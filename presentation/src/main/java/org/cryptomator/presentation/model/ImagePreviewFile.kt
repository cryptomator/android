package org.cryptomator.presentation.model

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@SuppressLint("ParcelCreator")
data class ImagePreviewFile(
	val cloudFileModel: CloudFileModel,
	var uri: Uri?
) : Parcelable
