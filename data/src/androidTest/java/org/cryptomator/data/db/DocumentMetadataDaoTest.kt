package org.cryptomator.data.db

import android.provider.DocumentsContract.Document
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.CachedFileEntity
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.data.db.entities.VaultEntity
import org.cryptomator.domain.CloudType
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class DocumentMetadataDaoTest {

	private val context = InstrumentationRegistry.getInstrumentation().context
	private lateinit var database: CryptomatorDatabase
	private lateinit var dao: DocumentMetadataDao
	private lateinit var cachedFileDao: CachedFileDao

	@Before
	fun setup() {
		database = Room.inMemoryDatabaseBuilder(context, CryptomatorDatabase::class.java) //
			.allowMainThreadQueries() //
			.build()
		database.vaultDao().insert(vault(VAULT_ID))
		database.vaultDao().insert(vault(OTHER_VAULT_ID))
		dao = database.documentMetadataDao()
		cachedFileDao = database.cachedFileDao()
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun storingChildrenOfTheRootCachesThemAndMarksTheRootEnumerated() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), file("b.txt")))

		Assert.assertThat(paths(dao.loadChildren(VAULT_ID, "")), CoreMatchers.`is`(setOf("a", "b.txt")))
		Assert.assertThat(dao.load(VAULT_ID, "")?.lastEnumeratedAt, CoreMatchers.`is`(1000L))
		Assert.assertThat(dao.load(VAULT_ID, "")?.parentPath, CoreMatchers.nullValue())
	}

	@Test
	fun storingChildrenAgainKeepsWhatIsKnownAboutRemainingFoldersAndDropsRemovedSubtrees() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("gone")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("gone", 1000), listOf(file("gone/y.txt")))

		dao.storeChildren(folder("", 2000), listOf(folder("a"), file("new.txt")))

		Assert.assertThat(paths(dao.loadChildren(VAULT_ID, "")), CoreMatchers.`is`(setOf("a", "new.txt")))
		Assert.assertThat(dao.load(VAULT_ID, "a")?.lastEnumeratedAt, CoreMatchers.`is`(1000L))
		Assert.assertThat(paths(dao.loadChildren(VAULT_ID, "a")), CoreMatchers.`is`(setOf("a/x.txt")))
		Assert.assertThat(dao.load(VAULT_ID, "gone"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "gone/y.txt"), CoreMatchers.nullValue())
	}

	@Test
	fun storingAFileWhereAFolderWasDropsTheFoldersDescendants() {
		dao.storeChildren(folder("", 1000), listOf(folder("a")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))

		dao.store(file("a"))

		Assert.assertThat(dao.load(VAULT_ID, "a")?.isFolder, CoreMatchers.`is`(false))
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt"), CoreMatchers.nullValue())
	}

	@Test
	fun movingAFolderMovesItsSubtreeAndReplacesWhatWasCachedAtTheTarget() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("b")))
		dao.storeChildren(folder("a", 1000), listOf(folder("a/sub")))
		dao.storeChildren(folder("a/sub", 1000), listOf(file("a/sub/x.txt")))
		dao.storeChildren(folder("b", 1000), listOf(folder("b/c")))
		dao.storeChildren(folder("b/c", 1000), listOf(file("b/c/stale.txt")))

		dao.move(VAULT_ID, "a", "b/c", "b", "c", Document.MIME_TYPE_DIR)

		Assert.assertThat(dao.load(VAULT_ID, "a"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "b/c/stale.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "b/c")?.name, CoreMatchers.`is`("c"))
		Assert.assertThat(dao.load(VAULT_ID, "b/c")?.parentPath, CoreMatchers.`is`("b"))
		Assert.assertThat(dao.load(VAULT_ID, "b/c")?.status, CoreMatchers.`is`(DocumentMetadataEntity.STATUS_SYNCED))
		Assert.assertThat(dao.load(VAULT_ID, "b/c")?.sourcePath, CoreMatchers.`is`("a"))
		Assert.assertThat(dao.load(VAULT_ID, "b/c/sub")?.status, CoreMatchers.`is`(DocumentMetadataEntity.STATUS_SYNCED))
		Assert.assertThat(dao.load(VAULT_ID, "b/c/sub")?.parentPath, CoreMatchers.`is`("b/c"))
		Assert.assertThat(dao.load(VAULT_ID, "b/c/sub/x.txt")?.parentPath, CoreMatchers.`is`("b/c/sub"))
		Assert.assertThat(dao.load(VAULT_ID, "b/c/sub/x.txt")?.name, CoreMatchers.`is`("x.txt"))
	}

	@Test
	fun aNodeKeepsItsIdWhenItIsListedAgainOrMoved() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), file("x.txt")))
		val folderId = dao.load(VAULT_ID, "a")?.id
		val fileId = dao.load(VAULT_ID, "x.txt")?.id

		dao.storeChildren(folder("", 2000), listOf(folder("a"), file("x.txt").apply { size = 7 }))
		dao.move(VAULT_ID, "x.txt", "a/y.txt", "a", "y.txt", "text/plain")

		Assert.assertThat(dao.load(VAULT_ID, "a")?.id, CoreMatchers.`is`(folderId))
		Assert.assertThat(dao.load(VAULT_ID, "a/y.txt")?.id, CoreMatchers.`is`(fileId))
		Assert.assertThat(dao.loadById(fileId!!)?.path, CoreMatchers.`is`("a/y.txt"))
	}

	@Test
	fun renamingAPendingNodeKeepsItsIdAndPendingChange() {
		val id = dao.insert(file("new.txt").apply { status = DocumentMetadataEntity.STATUS_CREATING })

		dao.renamePending(VAULT_ID, "new.txt", "new (1A2B3).txt", "new (1A2B3).txt", "text/plain")

		Assert.assertThat(dao.load(VAULT_ID, "new.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.loadById(id)?.path, CoreMatchers.`is`("new (1A2B3).txt"))
		Assert.assertThat(dao.loadById(id)?.name, CoreMatchers.`is`("new (1A2B3).txt"))
		Assert.assertThat(dao.loadById(id)?.status, CoreMatchers.`is`(DocumentMetadataEntity.STATUS_CREATING))
	}

	@Test
	fun markingCreatedEndsTheCreation() {
		val id = dao.insert(folder("new").apply { status = DocumentMetadataEntity.STATUS_CREATING })

		dao.markCreated(id, null, null)

		Assert.assertThat(dao.load(VAULT_ID, "new")?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(dao.loadInterruptedChanges(), CoreMatchers.`is`(emptyList()))
	}

	@Test
	fun markingCreatedStoresTheCreatedFileAndKeepsALaterUploadPending() {
		val id = dao.insert(file("new.txt").apply { status = DocumentMetadataEntity.STATUS_CREATING })
		dao.markUploading(VAULT_ID, "new.txt")

		dao.markCreated(id, 0, 5000)

		Assert.assertThat(dao.load(VAULT_ID, "new.txt")?.lastModified, CoreMatchers.`is`(5000L))
		Assert.assertThat(dao.load(VAULT_ID, "new.txt")?.status, CoreMatchers.`is`(DocumentMetadataEntity.STATUS_UPLOADING))
		Assert.assertThat(paths(dao.loadPendingUploads(VAULT_ID)), CoreMatchers.`is`(setOf("new.txt")))
		Assert.assertThat(dao.loadInterruptedChanges(), CoreMatchers.`is`(emptyList()))
	}

	@Test
	fun movingAFileUpdatesItsMimeType() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))

		dao.move(VAULT_ID, "a.txt", "a.jpg", "", "a.jpg", "image/jpeg")

		Assert.assertThat(dao.load(VAULT_ID, "a.jpg")?.mimeType, CoreMatchers.`is`("image/jpeg"))
	}

	@Test
	fun listingKeepsPendingChangesThatTheCloudDoesNotKnowYet() {
		dao.storeChildren(folder("", 1000), listOf(file("moved.txt"), file("deleted.txt")))
		dao.insert(folder("created").apply { status = DocumentMetadataEntity.STATUS_CREATING })
		dao.move(VAULT_ID, "moved.txt", "renamed.txt", "", "renamed.txt", "text/plain")
		dao.markDeletingWithDescendants(VAULT_ID, "deleted.txt")

		dao.storeChildren(folder("", 2000), listOf(file("moved.txt"), file("deleted.txt").apply { size = 7 }))

		Assert.assertThat(paths(dao.loadChildren(VAULT_ID, "")), CoreMatchers.`is`(setOf("created", "renamed.txt")))
		Assert.assertThat(dao.load(VAULT_ID, "moved.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "deleted.txt")?.isDeleting, CoreMatchers.`is`(true))
		Assert.assertThat(dao.load(VAULT_ID, "deleted.txt")?.size, CoreMatchers.`is`(42L))
	}

	@Test
	fun markingMovedEndsAPendingMove() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "text/plain")
		val id = dao.load(VAULT_ID, "b.txt")!!.id!!

		dao.markMoved(id, "b.txt")

		Assert.assertThat(dao.load(VAULT_ID, "b.txt")?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(dao.loadInterruptedChanges(), CoreMatchers.`is`(emptyList()))
	}

	@Test
	fun aNodeMovedAgainStaysPendingUntilTheLastMoveIsDone() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "text/plain")
		dao.move(VAULT_ID, "b.txt", "c.txt", "", "c.txt", "text/plain")
		val id = dao.load(VAULT_ID, "c.txt")!!.id!!
		Assert.assertThat(dao.loadById(id)?.sourcePath, CoreMatchers.`is`("a.txt"))

		dao.markMoved(id, "b.txt")
		Assert.assertThat(dao.loadById(id)?.sourcePath, CoreMatchers.`is`("b.txt"))

		dao.markMoved(id, "c.txt")
		Assert.assertThat(dao.loadById(id)?.sourcePath, CoreMatchers.nullValue())
	}

	@Test
	fun markingUploadedStoresTheUploadedMetadataAndEndsTheUpload() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.markUploading(VAULT_ID, "a.txt")
		val metadata = dao.load(VAULT_ID, "a.txt")!!

		dao.markUploaded(metadata.id!!, 7, 5000, metadata.uploadGeneration)

		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.size, CoreMatchers.`is`(7L))
		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.lastModified, CoreMatchers.`is`(5000L))
	}

	@Test
	fun anUploadOnlyEndsIfTheFileHasNotBeenWrittenAgain() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.markUploading(VAULT_ID, "a.txt")
		val firstWrite = dao.load(VAULT_ID, "a.txt")!!
		dao.markUploading(VAULT_ID, "a.txt")

		dao.markUploaded(firstWrite.id!!, 7, 5000, firstWrite.uploadGeneration)

		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.isUploading, CoreMatchers.`is`(true))
		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.lastModified, CoreMatchers.`is`(5000L))
		dao.markUploaded(firstWrite.id!!, 8, 6000, firstWrite.uploadGeneration + 1)
		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.hasPendingChange(), CoreMatchers.`is`(false))
	}

	@Test
	fun anUploadDoesNotRevealAFileBeingDeleted() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.markUploading(VAULT_ID, "a.txt")
		val metadata = dao.load(VAULT_ID, "a.txt")!!
		dao.markDeletingWithDescendants(VAULT_ID, "a.txt")

		dao.markUploaded(metadata.id!!, 7, 5000, metadata.uploadGeneration)

		Assert.assertThat(dao.load(VAULT_ID, "a.txt")?.isDeleting, CoreMatchers.`is`(true))
	}

	@Test
	fun discardingAPendingMoveKeepsTheNodeAndMarksBothFoldersForListing() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("b")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("b", 1000), listOf())
		dao.move(VAULT_ID, "a/x.txt", "b/x.txt", "b", "x.txt", "text/plain")

		dao.loadInterruptedChanges().forEach(dao::discardPendingChange)

		Assert.assertThat(dao.load(VAULT_ID, "b/x.txt")?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(dao.load(VAULT_ID, "a")?.lastEnumeratedAt, CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "b")?.lastEnumeratedAt, CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "")?.lastEnumeratedAt, CoreMatchers.`is`(1000L))
		Assert.assertThat(dao.loadInterruptedChanges(), CoreMatchers.`is`(emptyList()))
	}

	@Test
	fun movingAFileWithAPendingUploadKeepsTheUploadEvenIfTheMoveFails() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.markUploading(VAULT_ID, "a.txt")
		cachedFileDao.store(cachedFile("a.txt", "local-a"))

		dao.move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "text/plain")
		Assert.assertThat(dao.load(VAULT_ID, "b.txt")?.isUploading, CoreMatchers.`is`(true))
		dao.loadInterruptedChanges().forEach(dao::discardPendingChange)

		Assert.assertThat(dao.load(VAULT_ID, "b.txt")?.isUploading, CoreMatchers.`is`(true))
		Assert.assertThat(cachedFileDao.load(VAULT_ID, "b.txt")?.localName, CoreMatchers.`is`("local-a"))
	}

	@Test
	fun revertingAMoveBringsTheNodeBackWithItsLocalCopy() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("b")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("b", 1000), listOf())
		cachedFileDao.store(cachedFile("a/x.txt", "local-x"))
		dao.move(VAULT_ID, "a/x.txt", "b/y.jpg", "b", "y.jpg", "image/jpeg")

		dao.revertMove(dao.load(VAULT_ID, "b/y.jpg")!!.id!!, "text/plain")

		Assert.assertThat(dao.load(VAULT_ID, "b/y.jpg"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt")?.name, CoreMatchers.`is`("x.txt"))
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt")?.mimeType, CoreMatchers.`is`("text/plain"))
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt")?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(cachedFileDao.load(VAULT_ID, "a/x.txt")?.localName, CoreMatchers.`is`("local-x"))
		Assert.assertThat(dao.load(VAULT_ID, "a")?.lastEnumeratedAt, CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "b")?.lastEnumeratedAt, CoreMatchers.nullValue())
	}

	@Test
	fun revertingAMoveFollowsTheNodeIfItHasBeenMovedAgain() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "text/plain")
		val id = dao.load(VAULT_ID, "b.txt")!!.id!!
		dao.move(VAULT_ID, "b.txt", "c.txt", "", "c.txt", "text/plain")

		dao.revertMove(id, "text/plain")

		Assert.assertThat(dao.loadById(id)?.path, CoreMatchers.`is`("a.txt"))
		Assert.assertThat(dao.loadById(id)?.hasPendingChange(), CoreMatchers.`is`(false))
	}

	@Test
	fun revertingAMoveKeepsTheNodeIfItsSourceHasBeenTaken() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		dao.move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "text/plain")
		val id = dao.load(VAULT_ID, "b.txt")!!.id!!
		dao.insert(file("a.txt"))

		dao.revertMove(id, "text/plain")

		Assert.assertThat(dao.loadById(id)?.path, CoreMatchers.`is`("b.txt"))
		Assert.assertThat(dao.loadById(id)?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(dao.load(VAULT_ID, "")?.lastEnumeratedAt, CoreMatchers.nullValue())
	}

	@Test
	fun aFolderWithOnlyChildrenBeingDeletedHasNoChildren() {
		dao.storeChildren(folder("", 1000), listOf(file("a.txt")))
		Assert.assertThat(dao.hasChildren(VAULT_ID, ""), CoreMatchers.`is`(true))

		dao.markDeletingWithDescendants(VAULT_ID, "a.txt")

		Assert.assertThat(dao.hasChildren(VAULT_ID, ""), CoreMatchers.`is`(false))
	}

	@Test
	fun onlyLocalCopiesWithoutAPendingUploadCanBeEvicted() {
		dao.storeChildren(folder("", 1000), listOf(file("synced.txt"), file("pending.txt")))
		dao.markUploading(VAULT_ID, "pending.txt")
		cachedFileDao.store(cachedFile("synced.txt", "local-synced"))
		cachedFileDao.store(cachedFile("pending.txt", "local-pending"))

		Assert.assertThat(cachedFileDao.loadEvictable().map { it.localName }, CoreMatchers.`is`(listOf("local-synced")))
	}

	@Test
	fun discardingAFailedCreationDropsTheNode() {
		dao.storeChildren(folder("", 1000), listOf())
		dao.insert(folder("new").apply { status = DocumentMetadataEntity.STATUS_CREATING })

		dao.loadInterruptedChanges().forEach(dao::discardPendingChange)

		Assert.assertThat(dao.load(VAULT_ID, "new"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "")?.lastEnumeratedAt, CoreMatchers.nullValue())
	}

	@Test
	fun discardingAFailedCreationKeepsAFolderWithAPendingUpload() {
		dao.storeChildren(folder("", 1000), listOf())
		dao.insert(folder("new").apply { status = DocumentMetadataEntity.STATUS_CREATING })
		dao.insert(file("new/x.txt"))
		dao.markUploading(VAULT_ID, "new/x.txt")

		dao.loadInterruptedChanges().forEach(dao::discardPendingChange)

		Assert.assertThat(dao.load(VAULT_ID, "new")?.hasPendingChange(), CoreMatchers.`is`(false))
		Assert.assertThat(dao.load(VAULT_ID, "new/x.txt")?.isUploading, CoreMatchers.`is`(true))
		Assert.assertThat(dao.loadInterruptedChanges(), CoreMatchers.`is`(emptyList()))
	}

	@Test
	fun listingKeepsAFolderTheCloudDropsWhileItHasAPendingUpload() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("b")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("b", 1000), listOf(file("b/y.txt")))
		dao.markUploading(VAULT_ID, "a/x.txt")

		dao.storeChildren(folder("", 2000), listOf())

		Assert.assertThat(dao.load(VAULT_ID, "a"), CoreMatchers.notNullValue())
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt")?.isUploading, CoreMatchers.`is`(true))
		Assert.assertThat(dao.load(VAULT_ID, "b"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "b/y.txt"), CoreMatchers.nullValue())
	}

	@Test
	fun aFileInTheCloudDoesNotReplaceAFolderWithAPendingUpload() {
		dao.storeChildren(folder("", 1000), listOf(folder("a")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.markUploading(VAULT_ID, "a/x.txt")

		dao.store(file("a"))

		Assert.assertThat(dao.load(VAULT_ID, "a")?.isFolder, CoreMatchers.`is`(true))
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt")?.isUploading, CoreMatchers.`is`(true))
	}

	@Test
	fun aListingOfAFolderThatHasBeenMovedOrDeletedMeanwhileIsDropped() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("d")))
		dao.move(VAULT_ID, "a", "c", "", "c", Document.MIME_TYPE_DIR)
		dao.markDeletingWithDescendants(VAULT_ID, "d")

		dao.storeChildren(folder("a", 2000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("d", 2000), listOf(file("d/y.txt")))

		Assert.assertThat(dao.load(VAULT_ID, "a"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "c")?.lastEnumeratedAt, CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "d/y.txt"), CoreMatchers.nullValue())
	}

	@Test
	fun movingASubtreeLeavesSiblingsWithTheSamePrefixAndOtherVaults() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("a_b"), folder("ab")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("ab", 1000), listOf(file("ab/y.txt")))
		dao.storeChildren(folder("", 1000, OTHER_VAULT_ID), listOf(folder("a", vaultId = OTHER_VAULT_ID)))

		dao.move(VAULT_ID, "a", "z", "", "z", Document.MIME_TYPE_DIR)

		Assert.assertThat(paths(dao.loadChildren(VAULT_ID, "")), CoreMatchers.`is`(setOf("a_b", "ab", "z")))
		Assert.assertThat(dao.load(VAULT_ID, "z/x.txt"), CoreMatchers.notNullValue())
		Assert.assertThat(dao.load(VAULT_ID, "ab/y.txt"), CoreMatchers.notNullValue())
		Assert.assertThat(dao.load(OTHER_VAULT_ID, "a"), CoreMatchers.notNullValue())
	}

	@Test
	fun theDescendantsOfANodeAreFoundForRevokingTheirGrants() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("ab")))
		dao.storeChildren(folder("a", 1000), listOf(folder("a/sub"), file("a/x.txt")))
		dao.storeChildren(folder("a/sub", 1000), listOf(file("a/sub/y.txt")))
		dao.storeChildren(folder("ab", 1000), listOf(file("ab/z.txt")))

		val ids = dao.loadDescendantIds(VAULT_ID, "a").toSet()

		Assert.assertThat(ids, CoreMatchers.`is`(setOf("a/sub", "a/x.txt", "a/sub/y.txt").map { dao.load(VAULT_ID, it)!!.id!! }.toSet()))
	}

	@Test
	fun markingAFolderDeletingHidesItAndDropsItsDescendants() {
		dao.storeChildren(folder("", 1000), listOf(folder("a")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))

		dao.markDeletingWithDescendants(VAULT_ID, "a")

		Assert.assertThat(dao.loadChildren(VAULT_ID, ""), CoreMatchers.`is`(emptyList()))
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "a")?.isDeleting, CoreMatchers.`is`(true))
	}

	@Test
	fun deletingASubtreeLeavesSiblingsWithTheSamePrefixAndOtherVaults() {
		dao.storeChildren(folder("", 1000), listOf(folder("a"), folder("a_b"), file("ab")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		dao.storeChildren(folder("", 1000, OTHER_VAULT_ID), listOf(folder("a", vaultId = OTHER_VAULT_ID)))

		dao.deleteSubtree(VAULT_ID, "a")

		Assert.assertThat(paths(dao.loadChildren(VAULT_ID, "")), CoreMatchers.`is`(setOf("a_b", "ab")))
		Assert.assertThat(dao.load(VAULT_ID, "a/x.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(OTHER_VAULT_ID, "a"), CoreMatchers.notNullValue())
	}

	@Test
	fun aCachedFileFollowsItsDocumentWhenTheParentFolderIsMoved() {
		dao.storeChildren(folder("", 1000), listOf(folder("a")))
		dao.storeChildren(folder("a", 1000), listOf(file("a/x.txt")))
		cachedFileDao.store(cachedFile("a/x.txt", "local-x"))

		dao.move(VAULT_ID, "a", "b", "", "b", Document.MIME_TYPE_DIR)

		Assert.assertThat(cachedFileDao.load(VAULT_ID, "a/x.txt"), CoreMatchers.nullValue())
		Assert.assertThat(cachedFileDao.load(VAULT_ID, "b/x.txt")?.localName, CoreMatchers.`is`("local-x"))
	}

	@Test
	fun aCachedFileIsDroppedWithItsDocument() {
		dao.storeChildren(folder("", 1000), listOf(file("x.txt"), file("y.txt")))
		cachedFileDao.store(cachedFile("x.txt", "local-x"))
		cachedFileDao.store(cachedFile("y.txt", "local-y"))

		dao.storeChildren(folder("", 2000), listOf(file("y.txt")))

		Assert.assertThat(cachedFileDao.load(VAULT_ID, "x.txt"), CoreMatchers.nullValue())
		Assert.assertThat(cachedFileDao.loadLocalNames(), CoreMatchers.`is`(listOf("local-y")))
	}

	@Test
	fun deletingAVaultDropsItsEntriesButNotThoseOfOtherVaults() {
		dao.storeChildren(folder("", 1000), listOf(file("x.txt")))
		dao.storeChildren(folder("", 1000, OTHER_VAULT_ID), listOf(file("y.txt", OTHER_VAULT_ID)))

		database.vaultDao().delete(vault(VAULT_ID))

		Assert.assertThat(dao.load(VAULT_ID, ""), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(VAULT_ID, "x.txt"), CoreMatchers.nullValue())
		Assert.assertThat(dao.load(OTHER_VAULT_ID, "y.txt"), CoreMatchers.notNullValue())
	}

	private fun vault(id: Long): VaultEntity {
		return VaultEntity().apply {
			this.id = id
			folderPath = "vault$id"
			folderName = "vault$id"
			cloudType = CloudType.LOCAL.name
		}
	}

	private fun folder(path: String, lastEnumeratedAt: Long? = null, vaultId: Long = VAULT_ID): DocumentMetadataEntity {
		return entity(path, Document.MIME_TYPE_DIR, vaultId).apply { this.lastEnumeratedAt = lastEnumeratedAt }
	}

	private fun file(path: String, vaultId: Long = VAULT_ID): DocumentMetadataEntity {
		return entity(path, "text/plain", vaultId).apply { size = 42 }
	}

	private fun entity(path: String, mimeType: String, vaultId: Long): DocumentMetadataEntity {
		return DocumentMetadataEntity().apply {
			this.vaultId = vaultId
			this.path = path
			parentPath = if (path.isEmpty()) null else path.substringBeforeLast('/', "")
			name = path.substringAfterLast('/')
			this.mimeType = mimeType
		}
	}

	private fun cachedFile(path: String, localName: String): CachedFileEntity {
		return CachedFileEntity().apply {
			vaultId = VAULT_ID
			this.path = path
			this.localName = localName
			lastModified = 1000
		}
	}

	private fun paths(entities: List<DocumentMetadataEntity>): Set<String> {
		return entities.map { it.path }.toSet()
	}

	companion object {

		private const val VAULT_ID = 1L
		private const val OTHER_VAULT_ID = 2L
	}
}
