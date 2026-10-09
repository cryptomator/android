package org.cryptomator.data.repository

import org.cryptomator.data.cloud.crypto.CryptoCloudFactory
import org.cryptomator.data.db.dao.CloudDao
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.mappers.CloudEntityMapper
import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.PCloud
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CloudRepositoryImplTest {

	private val cloudDao = mock<CloudDao>()

	private lateinit var inTest: CloudRepositoryImpl

	@BeforeEach
	fun setup() {
		whenever(cloudDao.store(any())).thenAnswer { it.arguments[0] }
		whenever(cloudDao.storeKeepingAccessToken(any())).thenCallRealMethod()
		whenever(cloudDao.load(7L)).thenReturn(storedBoxEntity())
		inTest = CloudRepositoryImpl(CloudEntityMapper(), mock<CryptoCloudFactory>(), cloudDao, mock<DispatchingCloudContentRepository>())
	}

	@Test
	fun testStoringBoxCloudKeepsStoredTokens() {
		inTest.store(BoxCloud.aBoxCloud().withId(7L).withUserId("userId").withUsername("new@example.com").build())

		val entity = storedEntity()
		assertThat(entity.accessToken, `is`("rotatedTokens"))
		assertThat(entity.username, `is`("new@example.com"))
	}

	@Test
	fun testStoringNewBoxCloudStoresNoTokens() {
		inTest.store(BoxCloud.aBoxCloud().withUserId("userId").withUsername("user@example.com").build())

		assertThat(storedEntity().accessToken, `is`(nullValue()))
	}

	@Test
	fun testStoringOtherCloudStoresItsToken() {
		inTest.store(PCloud.aPCloud().withId(8L).withAccessToken("pCloudToken").withUrl("api.pcloud.com").withUsername("user@example.com").build())

		assertThat(storedEntity().accessToken, `is`("pCloudToken"))
	}

	private fun storedEntity(): CloudEntity {
		val entity = argumentCaptor<CloudEntity>()
		verify(cloudDao).store(entity.capture())
		return entity.firstValue
	}

	private fun storedBoxEntity(): CloudEntity {
		val entity = CloudEntity()
		entity.id = 7L
		entity.type = CloudType.BOX.name
		entity.accessToken = "rotatedTokens"
		entity.url = "userId"
		entity.username = "user@example.com"
		return entity
	}
}
