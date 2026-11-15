package com.novelnest.app.feature.epub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/** One system-picker policy for every EPUB entry point; URI/ClipData parsing stays with AndroidX. */
internal class EpubImportContract : ActivityResultContract<Unit, List<Uri>>() {
    private val documents = ActivityResultContracts.OpenMultipleDocuments()
    override fun createIntent(context: Context, input: Unit): Intent = documents.createIntent(context,
        arrayOf("application/epub+zip", "application/zip", "application/octet-stream")).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> = documents.parseResult(resultCode, intent)
}
