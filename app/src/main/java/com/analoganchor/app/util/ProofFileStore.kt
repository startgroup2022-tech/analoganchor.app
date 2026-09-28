package com.analoganchor.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ProofFileStore {
    const val FILE_PROVIDER_AUTHORITY = "com.analoganchor.fileprovider"

    fun createProofImageFile(context: Context, prefix: String = "proof"): File {
        val proofDir = File(context.cacheDir, "proof").apply { mkdirs() }
        return File(proofDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    fun createCardImageFile(context: Context, prefix: String = "review"): File {
        val cardDir = File(context.cacheDir, "cards").apply { mkdirs() }
        return File(cardDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    }

    fun uriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }

    fun copyUriToProofFile(
        context: Context,
        sourceUri: Uri,
        prefix: String = "proof_upload"
    ): String? {
        val target = createProofImageFile(context, prefix)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun saveBitmapToCardFile(context: Context, bitmap: Bitmap): File {
        val target = createCardImageFile(context)
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        return target
    }
}
