package com.puretone.player.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读专辑封面。
 *
 * 不引第三方图片库：封面尺寸固定很小，用 BitmapFactory 带 inSampleSize 解码足够，
 * 避免为了几个 48dp 的图拉进一个图片加载框架。
 */
@Composable
fun rememberAlbumArt(uriString: String?): ImageBitmap? {
    val context = LocalContext.current

    val state by produceState<ImageBitmap?>(initialValue = null, key1 = uriString) {
        value = if (uriString.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

                    // 封面可能来自 MediaStore（content://）也可能是私有目录里的文件路径
                    if (uriString.startsWith("content:")) {
                        val uri = Uri.parse(uriString)
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        }
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TARGET_PX)
                        }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                    } else {
                        val path = uriString.removePrefix("file://")
                        BitmapFactory.decodeFile(path, bounds)
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TARGET_PX)
                        }
                        BitmapFactory.decodeFile(path, options)
                    }
                }.getOrNull()?.asImageBitmap()
            }
        }
    }

    return state
}

/**
 * 方形封面位。没有封面时留空（不画字母、不画图标），只保留一个极淡的底色占位。
 */
@Composable
fun AlbumArtBox(
    coverUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    val art = rememberAlbumArt(coverUri)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun sampleSize(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / sample > target || height / sample > target) {
        sample *= 2
    }
    return sample
}

private const val TARGET_PX = 320
