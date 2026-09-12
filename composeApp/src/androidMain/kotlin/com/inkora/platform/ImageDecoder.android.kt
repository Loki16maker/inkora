package com.inkora.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImage(bytes: ByteArray): ImageBitmap =
    requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Unsupported or damaged image" }.asImageBitmap()
