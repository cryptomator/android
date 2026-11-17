package org.cryptomator.presentation.ui.adapter

import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.FileProgressStateModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel

/**
 * The node to show in the row of [old] once a new listing has [node] at its path: [old] itself if it is the same node and
 * nothing shown changed, so that its selection and progress stay with what is bound, otherwise [node], which takes them
 * over. Another node at the path, e.g. one created after another app renamed the shown one away, doesn't take over the
 * selection, so that an action the user chose doesn't go to it. The progress of an upload that has ended, successfully or
 * not, isn't taken over.
 */
internal fun rowNode(old: CloudNodeModel<*>?, node: CloudNodeModel<*>): CloudNodeModel<*> {
	if (old == null) {
		return node
	}
	if (showsSameNode(old, node)) {
		return old
	}
	node.isSelected = old.isSelected && documentId(old) == documentId(node)
	val endedUpload = node is CloudFileModel && node.upload != CachedVaultFile.Upload.PENDING && old.progress?.let(::isUploadProgress) == true
	node.progress = old.progress.takeUnless { endedUpload }
	return node
}

/**
 * Compares the documentIds, too, since actions on a row follow its node by its documentId.
 */
private fun showsSameNode(old: CloudNodeModel<*>, node: CloudNodeModel<*>): Boolean {
	if (old.name != node.name || documentId(old) != documentId(node)) {
		return false
	}
	return old !is CloudFileModel || (node is CloudFileModel && old.size == node.size && old.modified == node.modified && old.upload == node.upload && old.thumbnail == node.thumbnail)
}

/**
 * Whether [progress] is that of an upload, which, unlike other progress, leaves the row usable, so that the file can be
 * deleted meanwhile.
 */
internal fun isUploadProgress(progress: ProgressModel): Boolean {
	val state = progress.state()
	return state === ProgressStateModel.UPLOAD_PENDING || (state is FileProgressStateModel && (state.`is`(FileProgressStateModel.ENCRYPTION) || state.`is`(FileProgressStateModel.UPLOAD)))
}

private fun documentId(node: CloudNodeModel<*>): String? {
	return when (val cloudNode = node.toCloudNode()) {
		is CachedVaultFolder -> cloudNode.documentId
		is CachedVaultFile -> cloudNode.documentId
		else -> null
	}
}
