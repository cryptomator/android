package org.cryptomator.data.cloud.box

import android.content.Context
import android.util.LruCache
import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BoxCloudContentRepositoryTest {

	private val cloud = BoxCloud.aBoxCloud().withId(1L).withUserId("userId").withUsername("user@example.com").build()
	private val api = mock<BoxApi>()

	private lateinit var lruCaches: MockedConstruction<LruCache<*, *>>
	private lateinit var inTest: BoxCloudContentRepository
	private lateinit var root: BoxFolder

	@BeforeEach
	fun setup() {
		lruCaches = mapBackedLruCaches()
		inTest = BoxCloudContentRepository(cloud, api, mock<Context>())
		root = inTest.root(cloud)
	}

	@AfterEach
	fun tearDown() {
		lruCaches.close()
	}

	@Test
	fun testNameClashIsReportedWithTheNodesName() {
		whenever(api.createFolder("0", "folder")).thenThrow(BoxApiException(409, "item_name_in_use", "Item with the same name already exists"))

		val exception = assertThrows(CloudNodeAlreadyExistsException::class.java) { inTest.create(inTest.folder(root, "folder")) }

		assertThat(exception.message, `is`("folder"))
	}

	@Test
	fun testBlockedOperationIsNoNameClash() {
		whenever(api.createFolder("0", "folder")).thenThrow(BoxApiException(409, "operation_blocked_temporary", "The operation is blocked"))

		assertThrows(BoxApiException::class.java) { inTest.create(inTest.folder(root, "folder")) }
	}

	@Test
	fun testCreateInStaleParentRetriesWithTheCurrentParentId() {
		whenever(api.folderItems("0", null)).thenReturn(items(folder("staleId", "parent")), items(folder("currentId", "parent")))
		whenever(api.createFolder("staleId", "child")).thenThrow(BoxApiException(404, "not_found", "Not Found"))
		whenever(api.createFolder("currentId", "child")).thenReturn(folder("childId", "child"))
		val parent = inTest.folder(root, "parent")
		inTest.list(root)

		val created = inTest.create(inTest.folder(parent, "child"))

		assertThat(created.path, `is`("/parent/child"))
		verify(api).createFolder("currentId", "child")
	}

	@Test
	fun testCreateDoesNotRetryWhenLookingUpTheParentFails() {
		whenever(api.folderItems("0", null)).thenThrow(BoxApiException(404, "not_found", "Not Found"))
		val parent = inTest.folder(root, "parent")

		assertThrows(NoSuchCloudFileException::class.java) { inTest.create(inTest.folder(parent, "child")) }

		verify(api, times(1)).folderItems("0", null)
		verify(api, never()).createFolder(any(), any())
	}

	/**
	 * android.util.LruCache is only a stub in JVM tests, so the id cache gets one backed by a map.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun mapBackedLruCaches(): MockedConstruction<LruCache<*, *>> {
		return Mockito.mockConstruction(LruCache::class.java as Class<LruCache<*, *>>) { cache, _ ->
			val entries = LinkedHashMap<Any?, Any?>()
			cache as LruCache<Any?, Any?>
			doAnswer { entries[it.arguments[0]] }.whenever(cache).get(any())
			doAnswer { entries.put(it.arguments[0], it.arguments[1]) }.whenever(cache).put(any(), any())
			doAnswer { entries.remove(it.arguments[0]) }.whenever(cache).remove(any())
			doAnswer { LinkedHashMap(entries) }.whenever(cache).snapshot()
		}
	}

	private fun items(vararg entries: BoxItem): BoxItems {
		return BoxItems(entries.toList(), null)
	}

	private fun folder(id: String, name: String): BoxItem {
		return BoxItem("folder", id, name, null, null, null, null)
	}
}
