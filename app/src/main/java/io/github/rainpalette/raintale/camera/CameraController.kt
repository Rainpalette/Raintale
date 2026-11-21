package io.github.rainpalette.raintale.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 封装 CameraX 的无状态工具类，按蓝图归在 camera 子系统。
 *
 * 对外只暴露两件事：[bindToLifecycle]（开预览）和 [capture]（拍照存盘）。
 * 由 UI 作用域创建并持有（它绑定的 CameraX 用例与生命周期相关，不放进 ViewModel）。
 */
class CameraController {

    private var previewUseCase: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    // 当前目标旋转（Surface.ROTATION_*）；设备朝向变化时更新，保证预览/拍照/抓帧方向正确。
    private var targetRotation: Int = Surface.ROTATION_0

    /**
     * 更新预览/拍照/抓帧的目标旋转（设备朝向变化时调，来源见 rememberDeviceRotation）。
     *
     * **Preview 也要设**：App 锁定竖屏后，PreviewView 不再随显示旋转，必须显式给 Preview 设
     * targetRotation，预览画面才会随手持方向转正；ImageCapture/ImageAnalysis 设了则成片方向正确。
     */
    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        previewUseCase?.targetRotation = rotation
        imageCapture?.targetRotation = rotation
        imageAnalysis?.targetRotation = rotation
    }

    // 实时点评抓帧用：分析器只在被 grabFrame() 请求时才转换一帧（节流，省性能）。
    private val analysisExecutor by lazy { Executors.newSingleThreadExecutor() }
    private val frameRequest = AtomicReference<CancellableContinuation<Bitmap>?>(null)
    private val frameAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        val cont = frameRequest.getAndSet(null)
        if (cont != null) {
            try {
                val bitmap = imageProxy.toBitmap()
                val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
                cont.resume(transformBitmap(bitmap, imageProxy.imageInfo.rotationDegrees, mirror))
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }
        imageProxy.close()
    }

    /**
     * 绑定预览与拍照用例到给定生命周期，并把预览喂给 [previewView]。
     * 必须在主线程调用（请在 Main 调度的协程里调）。切换前后置时用新的 [lensFacing] 重新调用即可。
     *
     * @param withAnalysis 是否绑定按需抓帧分析器（实时点评 [grabFrame] 用）。
     * @param analyzer 可选的**连续**分析器（如预览人像分割）。非空时优先于 [withAnalysis]，
     *   绑定一个持续运行的 ImageAnalysis；二者实际不同屏、互斥，最多绑定一个 ImageAnalysis。
     *
     * M4 预埋：Preview 与 ImageCapture 共用同一 [AspectRatioStrategy]，
     * 让"屏幕坐标 → 原图坐标"日后退化成等比缩放。
     */
    suspend fun bindToLifecycle(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int,
        withAnalysis: Boolean = false,
        analyzer: ImageAnalysis.Analyzer? = null,
        aspect: PreviewAspect = PreviewAspect.FULL,
    ) {
        this.lensFacing = lensFacing
        val cameraProvider = context.awaitCameraProvider()

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspect.aspectStrategy)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(targetRotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        previewUseCase = preview

        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(targetRotation)
            .build()
        imageCapture = capture

        val useCases = mutableListOf<UseCase>(preview, capture)
        // 外部连续分析器（如预览人像分割）优先；否则用按需抓帧分析器（实时点评）。
        val activeAnalyzer = analyzer ?: if (withAnalysis) frameAnalyzer else null
        if (activeAnalyzer != null) {
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(targetRotation)
                .build()
                .also { it.setAnalyzer(analysisExecutor, activeAnalyzer) }
            imageAnalysis = analysis
            useCases.add(analysis)
        }

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
    }

    /**
     * 抓取下一帧预览（已旋正的 Bitmap）。需先以 withAnalysis=true 绑定。
     * 实现为"按需节流"：只有调用时分析器才转换一帧。
     */
    suspend fun grabFrame(): Bitmap = suspendCancellableCoroutine { cont ->
        frameRequest.set(cont)
        cont.invokeOnCancellation { frameRequest.compareAndSet(cont, null) }
    }

    /**
     * 拍一张并返回**已旋正的内存 Bitmap**（不落盘）。失败抛异常。
     * M4 起改走内存 Bitmap 路径，便于与角色叠层合成（见 PhotoCompositor）。
     */
    suspend fun capture(context: Context): Bitmap = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
        if (capture == null) {
            cont.resumeWithException(IllegalStateException("相机尚未就绪"))
            return@suspendCancellableCoroutine
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toBitmap()
                        // 前置：预览是镜像的，拍下来的原图默认不镜像，这里水平翻转以保持所见即所得。
                        val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
                        val result = transformBitmap(bitmap, image.imageInfo.rotationDegrees, mirror)
                        cont.resume(result)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }
}

/**
 * 旋正并（可选）水平镜像位图。CameraX 给的是传感器方向，需转成正向；前置需镜像。
 */
private fun transformBitmap(bitmap: Bitmap, degrees: Int, mirror: Boolean): Bitmap {
    if (degrees == 0 && !mirror) return bitmap
    val matrix = Matrix().apply {
        postRotate(degrees.toFloat())
        if (mirror) postScale(-1f, 1f)
    }
    val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (out != bitmap) bitmap.recycle()
    return out
}

/** 把 ProcessCameraProvider 的 ListenableFuture 包成挂起函数，避免再引入额外依赖。 */
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(this)
        )
    }
