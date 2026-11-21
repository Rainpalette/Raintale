package io.github.rainpalette.raintale.ui.gallery

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.rainpalette.raintale.data.gallery.GalleryPhoto
import io.github.rainpalette.raintale.data.gallery.GalleryRepository
import io.github.rainpalette.raintale.ui.camera.sharePhoto
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * 图库页：Coil 网格总览 + 全屏浏览（左右滑）+ 详情操作栏（最爱/分享/删除/批注/AI 评论）+ 最爱筛选。
 * 只显示 `Pictures/PhotoBuddy` 相册。开启"只看最爱"时网格与详情浏览都只在最爱集内。
 */
@Composable
fun GalleryScreen(
    geminiApiKey: String,
    aiEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repo = remember { GalleryRepository(context) }
    val photos = remember { mutableStateListOf<GalleryPhoto>() }
    var loading by remember { mutableStateOf(true) }
    var detailIndex by remember { mutableStateOf<Int?>(null) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    val favKeys by repo.favoriteKeys().collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) {
        val list = repo.listPhotos()
        photos.clear()
        photos.addAll(list)
        loading = false
    }

    // 当前展示的列表（按筛选）。删除从源 [photos] 移除 → 展示列表随之更新。
    val favSet = favKeys.toSet()
    val displayed = if (onlyFavorites) photos.filter { it.displayName in favSet } else photos.toList()

    if (detailIndex != null) {
        GalleryDetail(
            repo = repo,
            initial = displayed,
            onDelete = { photos.remove(it) },
            initialIndex = detailIndex!!,
            geminiApiKey = geminiApiKey,
            aiEnabled = aiEnabled,
            timeGreetingEnabled = timeGreetingEnabled,
            onBack = { detailIndex = null },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("相册", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onlyFavorites = !onlyFavorites }) {
                Icon(
                    imageVector = if (onlyFavorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "只看最爱",
                    tint = if (onlyFavorites) Color(0xFFFF6B81) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            displayed.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (onlyFavorites) "还没有最爱～点开照片戳个 ♥ 吧" else "还没有照片～拍一张吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(displayed, key = { it.id }) { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clickable { detailIndex = displayed.indexOf(photo) },
                    )
                }
            }
        }
    }
}

/** 全屏浏览（左右滑）+ 底部操作栏。[photos] 是当前展示列表；删除经 [onDelete] 回源列表。 */
@Composable
private fun GalleryDetail(
    repo: GalleryRepository,
    initial: List<GalleryPhoto>,
    onDelete: (GalleryPhoto) -> Unit,
    initialIndex: Int,
    geminiApiKey: String,
    aiEnabled: Boolean,
    timeGreetingEnabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { onBack() }
    // 进详情那刻**快照**当前展示列表：之后在详情里改最爱（会影响筛选集）不会把你正在看的照片抽走。
    // 只有删除才从这份列表移除（同时经 onDelete 回源列表，让相册网格也更新）。
    val list = remember { initial.toMutableStateList() }
    LaunchedEffect(list.size) { if (list.isEmpty()) onBack() }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (list.size - 1).coerceAtLeast(0)),
        pageCount = { list.size },
    )
    val currentPhoto = list.getOrNull(pagerState.currentPage)

    var sheetPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }
    var sheetAutoGen by remember { mutableStateOf(false) }

    // 当前照片的最爱状态（随翻页切换）。
    val meta by remember(currentPhoto?.uri) {
        currentPhoto?.let { repo.metaFlow(it.uri) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val isFavorite = meta?.favorite == true

    // 删除：走系统确认（API 30+ createDeleteRequest；API 29 直接删/走可恢复异常）。
    var pendingDelete by remember { mutableStateOf<GalleryPhoto?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDelete?.let { onDelete(it); list.remove(it) }
            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
        }
        pendingDelete = null
    }

    fun requestDelete(photo: GalleryPhoto) {
        pendingDelete = photo
        val cr = context.contentResolver
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = MediaStore.createDeleteRequest(cr, listOf(photo.uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            try {
                cr.delete(photo.uri, null, null)
                onDelete(photo)
                list.remove(photo)
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                pendingDelete = null
            } catch (e: SecurityException) {
                val sender = (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                if (sender != null) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    pendingDelete = null
                    Toast.makeText(context, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            list.getOrNull(page)?.let { photo ->
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionItem(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = "最爱",
                tint = if (isFavorite) Color(0xFFFF6B81) else Color.White,
            ) { currentPhoto?.let { p -> scope.launch { repo.toggleFavorite(p.uri) } } }

            ActionItem(Icons.Filled.Share, "分享") {
                currentPhoto?.let { sharePhoto(context, it.uri) }
            }
            ActionItem(Icons.Filled.ChatBubbleOutline, "批注") {
                currentPhoto?.let { sheetAutoGen = false; sheetPhoto = it }
            }
            // AI 评论已关闭时不显示此入口（避免绕过设置上传照片）。
            if (aiEnabled) {
                ActionItem(Icons.Filled.AutoAwesome, "AI 评论") {
                    currentPhoto?.let { sheetAutoGen = true; sheetPhoto = it }
                }
            }
            ActionItem(Icons.Filled.Delete, "删除") {
                currentPhoto?.let { requestDelete(it) }
            }
        }
    }

    sheetPhoto?.let { p ->
        AnnotationSheet(
            photo = p,
            repo = repo,
            apiKey = geminiApiKey,
            aiEnabled = aiEnabled,
            timeGreetingEnabled = timeGreetingEnabled,
            autoGenerate = sheetAutoGen,
            onDismiss = { sheetPhoto = null },
        )
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Text(label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
