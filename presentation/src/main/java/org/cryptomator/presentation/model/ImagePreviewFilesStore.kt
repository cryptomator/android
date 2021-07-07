package org.cryptomator.presentation.model

import java.io.Serializable

data class ImagePreviewFilesStore(
	val cloudFileModels: ArrayList<CloudFileModel>,
	var index: Int
) : Serializable
