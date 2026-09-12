package com.inkora.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Decode PNG/JPEG bytes for drawing imported images and PDF page previews. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap
