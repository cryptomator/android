package org.cryptomator.presentation.util

import android.content.Intent
import android.net.Uri
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.ActivityHolder
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import javax.inject.Inject

class ShareFileHelper @Inject constructor(private val mimeTypes: MimeTypes) {

	fun shareFile(activityHolder: ActivityHolder, fileUri: Uri, fileName: String) {
		val shareFileIntent = Intent(Intent.ACTION_SEND)
		shareFileIntent.type = (mimeTypes.fromFilename(fileName) ?: MimeType.WILDCARD_MIME_TYPE).toString()
		shareFileIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
		shareFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		activityHolder.activity().startActivity(Intent.createChooser(shareFileIntent, activityHolder.activity().getString(R.string.screen_file_browser_share_intent_chooser_title)))
	}
}
