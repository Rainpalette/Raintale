package io.github.rainpalette.raintale.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import io.github.rainpalette.raintale.debug.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 取**粗略**位置并反向地理编码成区域级地名（城市/地区，不含街道），供 AI 情境用。
 *
 * 用系统自带 [LocationManager] + [Geocoder]，**不引入依赖**。
 * 可靠性升级方案（FusedLocationProviderClient）见 README——届时只改本文件，外部不变。
 */
object LocationProvider {

    private val _area = MutableStateFlow<String?>(null)
    /** 当前区域级地名（粗略）；供 Debug 显示与情境拼接。 */
    val area: StateFlow<String?> = _area.asStateFlow()

    private const val FETCH_TIMEOUT_MS = 2_000L // 主动定位超时
    private const val GEOCODE_TIMEOUT_MS = 4_000L // 反向地理编码超时（Geocoder 可能很慢）
    private const val AREA_REFRESH_DISTANCE_M = 10_000.0 // 移动超过 10 公里才重新反向地理编码

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 最近一次经纬度（坐标消歧义用）；无则 null。 */
    @Volatile
    private var lastLatLon: Pair<Double, Double>? = null

    /** 缓存地名对应的坐标，用于"按距离失效"。 */
    @Volatile
    private var areaCoords: Pair<Double, Double>? = null

    fun lastCoords(): Pair<Double, Double>? = lastLatLon

    /**
     * 给 AI prompt 用：**不阻塞**——立刻返回缓存的地名，同时后台刷新供下次用。
     */
    fun areaForPrompt(context: Context): String? {
        prewarm(context)
        return _area.value
    }

    /** 后台预热/刷新（授权或进相机时调，提前把地名备好，第一张照片就有地点）。 */
    fun prewarm(context: Context) {
        bgScope.launch { runCatching { refresh(context) } }
    }

    /** 刷新并返回当前区域级地名；无权限/取不到时返回 null（保留上一次值）。 */
    suspend fun refresh(context: Context): String? {
        if (!hasPermission(context)) {
            _area.value = null
            return null
        }
        val location = fetchLocation(context) ?: return _area.value
        lastLatLon = location.latitude to location.longitude

        // 按距离失效：已有缓存地名且移动不足 10 公里 → 直接复用，不调 Geocoder。
        val cached = areaCoords
        if (_area.value != null && cached != null &&
            haversine(location.latitude, location.longitude, cached.first, cached.second) < AREA_REFRESH_DISTANCE_M
        ) {
            return _area.value
        }

        val area = reverseGeocode(context, location.latitude, location.longitude)
        if (area != null) {
            _area.value = area
            areaCoords = location.latitude to location.longitude
        }
        return _area.value
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // 调用前已在 refresh() 校验权限
    private suspend fun fetchLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)

        // 先用 last-known（瞬时），最快、最常见，避免阻塞 AI 调用。
        providers.filter { lm.isProviderEnabled(it) }
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { return it }

        // 没有缓存定位才主动取一次，且加超时。
        val provider = providers.firstOrNull { lm.isProviderEnabled(it) } ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                lm.getCurrentLocation(
                    provider, signal, ContextCompat.getMainExecutor(context)
                ) { loc -> cont.resume(loc) }
                cont.invokeOnCancellation { signal.cancel() }
            }
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? =
        withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses: List<Address>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // API 33+：异步接口，不阻塞线程。
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(results: MutableList<Address>) {
                                    if (cont.isActive) cont.resume(results)
                                }
                                override fun onError(message: String?) {
                                    if (cont.isActive) cont.resume(emptyList())
                                }
                            })
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        withContext(Dispatchers.IO) { geocoder.getFromLocation(lat, lon, 1) }
                    }
                val a = addresses?.firstOrNull() ?: return@withTimeoutOrNull null
                // 只取区域级（城市/地区 + 国家），不取街道，保证"粗略"。
                val place = a.locality ?: a.subAdminArea ?: a.adminArea
                listOfNotNull(place, a.countryName).joinToString("，").ifBlank { null }
            } catch (e: Exception) {
                DebugLog.e("LocationProvider", "反向地理编码失败", e)
                null
            }
        }

    /** 两点球面距离（米）。 */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
