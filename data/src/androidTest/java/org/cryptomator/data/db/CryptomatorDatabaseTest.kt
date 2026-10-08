package org.cryptomator.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.entities.VaultEntity
import org.cryptomator.data.db.entities.VaultWithCloud
import org.cryptomator.domain.CloudType
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class CryptomatorDatabaseTest {

	private val context = InstrumentationRegistry.getInstrumentation().context
	private lateinit var database: CryptomatorDatabase

	@Before
	fun setup() {
		database = Room.inMemoryDatabaseBuilder(context, CryptomatorDatabase::class.java) //
			.allowMainThreadQueries() //
			.build()
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun storingACloudWithoutIdInsertsItAndStoringItAgainUpdatesIt() {
		val stored = database.cloudDao().store(webDavCloud())
		Assert.assertThat(stored.id, CoreMatchers.notNullValue())
		Assert.assertThat(stored.url, CoreMatchers.`is`("https://example.org/webdav"))

		stored.url = "https://example.com/webdav"
		val updated = database.cloudDao().store(stored)

		Assert.assertThat(updated.id, CoreMatchers.`is`(stored.id))
		Assert.assertThat(updated.url, CoreMatchers.`is`("https://example.com/webdav"))
		Assert.assertThat(database.cloudDao().loadAll().size, CoreMatchers.`is`(1))
	}

	@Test
	fun storingAVaultResolvesItsCloudOnLoad() {
		val cloud = database.cloudDao().store(webDavCloud())

		val stored = database.vaultDao().store(vaultIn(cloud))

		Assert.assertThat(stored.vault.id, CoreMatchers.notNullValue())
		Assert.assertThat(stored.vault.folderName, CoreMatchers.`is`("name"))
		Assert.assertThat(stored.folderCloud.id, CoreMatchers.`is`(cloud.id))
		Assert.assertThat(stored.folderCloud.type, CoreMatchers.`is`(CloudType.WEBDAV.name))

		database.vaultDao().delete(stored)

		Assert.assertThat(database.vaultDao().loadAll().isEmpty(), CoreMatchers.`is`(true))
	}

	@Test(expected = SQLiteConstraintException::class)
	fun storingTwoVaultsForTheSamePathInTheSameCloudIsRejected() {
		val cloud = database.cloudDao().store(webDavCloud())

		database.vaultDao().store(vaultIn(cloud))
		database.vaultDao().store(vaultIn(cloud))
	}

	@Test
	fun deletingACloudDetachesItsVaults() {
		val cloud = database.cloudDao().store(webDavCloud())
		database.vaultDao().store(vaultIn(cloud))

		database.cloudDao().delete(cloud)

		val vault = database.vaultDao().loadAll().single()
		Assert.assertThat(vault.vault.folderCloudId, CoreMatchers.nullValue())
		Assert.assertThat(vault.folderCloud, CoreMatchers.nullValue())
	}

	private fun webDavCloud() = CloudEntity().apply {
		type = CloudType.WEBDAV.name
		url = "https://example.org/webdav"
		username = "username"
	}

	private fun vaultIn(cloud: CloudEntity) = VaultWithCloud().apply {
		vault = VaultEntity().apply {
			folderCloudId = cloud.id
			folderPath = "path"
			folderName = "name"
			cloudType = CloudType.WEBDAV.name
		}
		folderCloud = cloud
	}
}
