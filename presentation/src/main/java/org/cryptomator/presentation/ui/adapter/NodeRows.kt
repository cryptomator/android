package org.cryptomator.presentation.ui.adapter

import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.FileProgressStateModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel

/**
 * The node to show in the row of [old] once a new listing has [node] at its path: [old] itself if nothing shown changed,
 * so that its selection and progress stay with what is bound, otherwise [node], which takes them over. The progress of an
 * upload that has ended, successfully or not, isn't taken over.
 */
internal fun rowNode(old: CloudNodeModel<*>?, node: CloudNodeModel<*>): CloudNodeModel<*> {
	if (old == null) {
		return node
	}
	if (hasSameContent(old, node)) {
		return old
	}
	node.isSelected = old.isSelected
	val endedUpload = node is CloudFileModel && node.upload != CachedVaultFile.Upload.PENDING && old.progress?.let(::isUploadProgress) == true
	node.progress = old.progress.takeUnless { endedUpload }
	return node
}

private fun hasSameContent(old: CloudNodeModel<*>, node: CloudNodeModel<*>): Boolean {
	if (old.name != node.name) {
		return false
	}
	return old !is CloudFileModel || (node is CloudFileModel && old.size == node.size && old.modified == node.modified && old.upload == node.upload && old.thumbnail == node.thumbnail)
}

/**
 * Whether [progress] is that of an upload, which, unlike other progress, leaves the row usable, so that the upload can
 * be stopped by deleting the file.
 */
internal fun isUploadProgress(progress: ProgressModel): Boolean {
	val state = progress.state()
	return state === ProgressStateModel.UPLOAD_PENDING || (state is FileProgressStateModel && (state.`is`(FileProgressStateModel.ENCRYPTION) || state.`is`(FileProgressStateModel.UPLOAD)))
}
