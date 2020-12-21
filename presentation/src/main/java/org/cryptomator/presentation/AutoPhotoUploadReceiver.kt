package org.cryptomator.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.file.MimeTypeMap_Factory
import org.cryptomator.util.file.MimeTypes
import timber.log.Timber

class AutoPhotoUploadReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (!SharedPreferencesHandler(context).usePhotoUpload()) {
			return
		}

		intent.data?.let { uri ->
			val cursor = context.contentResolver.query(uri, null, null, null, null)
			cursor?.moveToFirst()
			val imagePath = cursor?.getString(cursor.getColumnIndex("_data"))
			cursor?.close()
			imagePath?.let {
				val fileUtil = FileUtil(context, MimeTypes(MimeTypeMap_Factory.newInstance()))
				fileUtil.addImageToAutoUploads(it)
				Timber.tag("AutoPhotoUploadReceiver").i(String.format("Added file to UploadList %s", it))
			}
		} ?: Timber.tag("AutoPhotoUploadReceiver").i("No data in receiving intent")
	}
}
