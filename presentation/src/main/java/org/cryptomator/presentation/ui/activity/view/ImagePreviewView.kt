package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.ImagePreviewFile

interface ImagePreviewView : View {

	fun hideSystemUi()
	fun showSystemUi()
	fun showImagePreview(imagePreviewFile: ImagePreviewFile)
	fun hideProgressBar(imagePreviewFile: ImagePreviewFile)
	fun onImageDeleted(index: Int)

}
