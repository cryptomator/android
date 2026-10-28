package org.cryptomator.presentation.ui.adapter

import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.FileProgressStateModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.util.FileIcon
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Date

class NodeRowsTest {

	private val folder = CachedVaultFolder(null, "/a")

	@Test
	fun `a new node has nothing to take over`() {
		val node = file()

		assertSame(node, rowNode(null, node))
	}

	@Test
	fun `a node whose row shows the same keeps the bound node with its selection and progress`() {
		val old = file().apply {
			isSelected = true
			progress = ProgressModel(ProgressStateModel.DELETION)
		}

		assertSame(old, rowNode(old, file()))
	}

	@Test
	fun `a changed node takes over the selection and progress of its row`() {
		val progress = progress(FileProgressStateModel.DOWNLOAD)
		val old = file().apply {
			isSelected = true
			this.progress = progress
		}
		val node = file(size = 5)

		assertSame(node, rowNode(old, node))
		assertTrue(node.isSelected)
		assertSame(progress, node.progress)
	}

	@Test
	fun `another node at the same path changes the row, so that actions on it don't follow the node that was there`() {
		val old = file()
		val replacement = file(documentId = "1:8")
		val replacedFolder = CloudFolderModel(CachedVaultFolder(null, "/a/b", "1:9"))

		assertSame(replacement, rowNode(old, replacement))
		assertSame(old, rowNode(old, file()))
		assertSame(replacedFolder, rowNode(CloudFolderModel(CachedVaultFolder(null, "/a/b", "1:10")), replacedFolder))
	}

	@Test
	fun `another node at the same path isn't selected in place of the node the user selected, a changed one stays selected`() {
		val selected = file().apply { isSelected = true }

		assertFalse(rowNode(selected, file(documentId = "1:8")).isSelected)
		assertTrue(rowNode(selected, file(size = 4)).isSelected)
	}

	@Test
	fun `a node without a documentId, e g outside a vault, keeps its row as long as nothing shown changed`() {
		val old = file(documentId = null)

		assertSame(old, rowNode(old, file(documentId = null)))
	}

	@Test
	fun `a changed upload state or thumbnail changes the row`() {
		val pending = file(upload = CachedVaultFile.Upload.PENDING)
		assertNotSame(pending, rowNode(pending, file(upload = CachedVaultFile.Upload.FAILED)))
		val old = file()
		assertNotSame(old, rowNode(old, file(thumbnail = File("thumbnail.jpg"))))
	}

	@Test
	fun `the progress of an upload that ended isn't taken over, in any of its phases`() {
		listOf(ProgressModel(ProgressStateModel.UPLOAD_PENDING), progress(FileProgressStateModel.ENCRYPTION), progress(FileProgressStateModel.UPLOAD)).forEach { progress ->
			val failed = file(upload = CachedVaultFile.Upload.FAILED)
			rowNode(file(upload = CachedVaultFile.Upload.PENDING).apply { this.progress = progress }, failed)
			assertNull(failed.progress)

			val uploading = file(size = 5, upload = CachedVaultFile.Upload.PENDING)
			rowNode(file(upload = CachedVaultFile.Upload.PENDING).apply { this.progress = progress }, uploading)
			assertSame(progress, uploading.progress)
		}
	}

	@Test
	fun `only upload progress leaves the row usable`() {
		assertTrue(isUploadProgress(ProgressModel(ProgressStateModel.UPLOAD_PENDING)))
		assertTrue(isUploadProgress(progress(FileProgressStateModel.ENCRYPTION)))
		assertTrue(isUploadProgress(progress(FileProgressStateModel.UPLOAD)))
		assertFalse(isUploadProgress(progress(FileProgressStateModel.DOWNLOAD)))
		assertFalse(isUploadProgress(progress(FileProgressStateModel.DECRYPTION)))
		assertFalse(isUploadProgress(ProgressModel(ProgressStateModel.DELETION)))
		assertFalse(isUploadProgress(ProgressModel.GENERIC))
	}

	private fun file(size: Long = 3, upload: CachedVaultFile.Upload = CachedVaultFile.Upload.NONE, thumbnail: File? = null, documentId: String? = "1:7"): CloudFileModel {
		return CloudFileModel(CachedVaultFile(folder, "x.jpg", size, Date(1_000), upload, thumbnail, documentId), FileIcon.IMAGE)
	}

	private fun progress(state: String): ProgressModel {
		val file = CachedVaultFile(folder, "x.jpg", 3, Date(1_000), CachedVaultFile.Upload.NONE, null)
		return ProgressModel(FileProgressStateModel(file, FileIcon.IMAGE, state, ProgressStateModel.noImage(), ProgressStateModel.noText()), 50)
	}
}
