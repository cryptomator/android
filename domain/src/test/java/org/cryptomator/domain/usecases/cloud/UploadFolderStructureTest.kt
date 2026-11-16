package org.cryptomator.domain.usecases.cloud

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UploadFolderStructureTest {

	private val dataSource: DataSource = mock()

	@Test
	@DisplayName("Empty folder has totalFileCount of 0")
	fun totalFileCountWithNoFiles() {
		val structure = UploadFolderStructure("empty")

		assertThat(structure.totalFileCount(), `is`(0))
	}

	@Test
	@DisplayName("Flat folder counts its own files")
	fun totalFileCountWithFilesOnly() {
		val structure = UploadFolderStructure("flat")
		structure.addFile(uploadFile("a.txt"))
		structure.addFile(uploadFile("b.txt"))
		structure.addFile(uploadFile("c.txt"))

		assertThat(structure.totalFileCount(), `is`(3))
	}

	@Test
	@DisplayName("Nested subfolders sum file counts recursively")
	fun totalFileCountWithNestedSubfolders() {
		val deep = UploadFolderStructure("deep")
		deep.addFile(uploadFile("deep.txt"))

		val mid = UploadFolderStructure("mid")
		mid.addFile(uploadFile("mid1.txt"))
		mid.addFile(uploadFile("mid2.txt"))
		mid.addSubfolder(deep)

		val root = UploadFolderStructure("root")
		root.addFile(uploadFile("root.txt"))
		root.addSubfolder(mid)

		assertThat(root.totalFileCount(), `is`(4))
		assertThat(mid.totalFileCount(), `is`(3))
		assertThat(deep.totalFileCount(), `is`(1))
	}

	@Test
	@DisplayName("Empty subfolders contribute 0 to totalFileCount")
	fun totalFileCountWithEmptySubfolders() {
		val empty1 = UploadFolderStructure("empty1")
		val empty2 = UploadFolderStructure("empty2")

		val root = UploadFolderStructure("root")
		root.addFile(uploadFile("file.txt"))
		root.addSubfolder(empty1)
		root.addSubfolder(empty2)

		assertThat(root.totalFileCount(), `is`(1))
	}

	@Test
	@DisplayName("Folder name is preserved from constructor")
	fun folderNameIsPreserved() {
		val structure = UploadFolderStructure("my-folder")

		assertThat(structure.folderName, `is`("my-folder"))
	}

	@Test
	@DisplayName("Added files and subfolders are retrievable via getters")
	fun addFileAndAddSubfolder() {
		val file = uploadFile("file.txt")
		val subfolder = UploadFolderStructure("sub")

		val structure = UploadFolderStructure("root")
		structure.addFile(file)
		structure.addSubfolder(subfolder)

		assertThat(structure.files.size, `is`(1))
		assertThat(structure.files[0], `is`(file))
		assertThat(structure.subfolders.size, `is`(1))
		assertThat(structure.subfolders[0], `is`(subfolder))
	}

	private fun uploadFile(name: String): UploadFile {
		return UploadFile.Builder()
			.withFileName(name)
			.withDataSource(dataSource)
			.thatIsReplacing(false)
			.build()
	}
}
