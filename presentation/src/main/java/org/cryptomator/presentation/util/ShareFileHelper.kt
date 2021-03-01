package org.cryptomator.presentation.util

import android.content.Intent
import android.net.Uri
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.presenter.ActivityHolder
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import javax.inject.Inject
import timber.log.Timber

class ShareFileHelper @Inject constructor( //
		private val fileUtil: FileUtil,  //
		private val mimeTypes: MimeTypes,  //
		private val contentResolverUtil: ContentResolverUtil) {

	fun shareFile(activityHolder: ActivityHolder, cloudFile: CloudFileModel) {
		shareFile(activityHolder, fileUtil.contentUriFor(cloudFile), mimeTypeFromFileName(cloudFile.name))
	}

	fun shareFile(activityHolder: ActivityHolder, uri: Uri) {
		contentResolverUtil.fileName(uri)?.let {
			shareFile(activityHolder, uri, mimeTypeFromFileName(it))
		} ?: Timber.tag("SharedFile").i("The file doesn't have a path in the URI")
	}

	private fun mimeTypeFromFileName(fileName: String): String {
		return mimeTypes.fromFilename(fileName).orElse(MimeType.WILDCARD_MIME_TYPE).toString()
	}

	private fun shareFile(activityHolder: ActivityHolder, fileUri: Uri, mimeType: String) {
		val shareFileIntent = Intent(Intent.ACTION_SEND)
		shareFileIntent.type = mimeType
		shareFileIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
		shareFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		activityHolder.activity().startActivity(Intent.createChooser(shareFileIntent, activityHolder.activity().getString(R.string.screen_file_browser_share_intent_chooser_title)))
	}
}
