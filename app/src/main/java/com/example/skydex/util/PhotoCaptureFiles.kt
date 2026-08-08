package com.example.skydex.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object PhotoCaptureFiles {

    /** Pure part: allocates a unique JPEG path under `<baseDir>/captures/`. */
    fun newCaptureFileIn(baseDir: File): File {
        val directory = File(baseDir, "captures").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.jpg")
    }

    fun newCaptureFile(context: Context): File = newCaptureFileIn(context.cacheDir)

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
