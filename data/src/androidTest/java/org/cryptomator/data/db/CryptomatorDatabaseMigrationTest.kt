package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.domain.CloudType
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.CryptoMode
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens databases as greenDAO left them behind with Room, which is what happens on every device that
 * updates to the first release built on Room.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class CryptomatorDatabaseMigrationTest {

	private val context = InstrumentationRegistry.getInstrumentation().context
	private lateinit var sharedPreferencesHandler: SharedPreferencesHandler
	private lateinit var database: CryptomatorDatabase

	@Before
	fun setup() {
		context.deleteDatabase(CryptomatorDatabase.NAME)
		sharedPreferencesHandler = SharedPreferencesHandler(context)
	}

	@After
	fun tearDown() {
		if (this::database.isInitialized) {
			database.close()
		}
		context.deleteDatabase(CryptomatorDatabase.NAME)
		sharedPreferencesHandler.removeAllEntries()
	}

	@Test
	fun upgradesADatabaseFromTheOldestVersionStillInTheField() {
		val accessToken = "accessToken"

		createGreenDaoDatabase(1) { db ->
			LegacyDatabaseV1.createOn(db)
			addWebDavVaultTo(db, accessToken = CredentialCryptor.getInstance(context, CryptoMode.CBC).encrypt(accessToken))
		}

		database = openWithRoom()

		assertWebDavVaultSurvived(accessToken)
	}

	@Test
	fun adoptsADatabaseLeftBehindAtTheLastGreenDaoVersion() {
		val accessToken = "accessToken"

		createGreenDaoDatabase(CryptomatorDatabase.VERSION) { db ->
			LegacyDatabaseV1.createOn(db)
			addWebDavVaultTo(db, accessToken = CredentialCryptor.getInstance(context, CryptoMode.CBC).encrypt(accessToken))
			upgrades().forEach { it.migrate(db) }
		}

		database = openWithRoom()

		assertWebDavVaultSurvived(accessToken)
	}

	@Test
	fun createsTheCloudsAndUpdateCheckRowAFreshInstallStartsOutWith() {
		database = openWithRoom()

		val clouds = database.cloudDao().loadAll().sortedBy { it.id }
		Assert.assertThat(clouds.map { it.id }, CoreMatchers.`is`(listOf(1L, 2L)))
		Assert.assertThat(clouds.map { it.type }, CoreMatchers.`is`(listOf(CloudType.DROPBOX.name, CloudType.GOOGLE_DRIVE.name)))

		Assert.assertThat(database.vaultDao().loadAll().isEmpty(), CoreMatchers.`is`(true))
		Assert.assertThat(database.updateCheckDao().load(1L), CoreMatchers.notNullValue())
	}

	private fun assertWebDavVaultSurvived(accessToken: String) {
		val vaults = database.vaultDao().loadAll()
		Assert.assertThat(vaults.size, CoreMatchers.`is`(1))

		val vault = vaults[0].vault
		Assert.assertThat(vault.id, CoreMatchers.`is`(25L))
		Assert.assertThat(vault.folderPath, CoreMatchers.`is`("path"))
		Assert.assertThat(vault.folderName, CoreMatchers.`is`("name"))
		Assert.assertThat(vault.cloudType, CoreMatchers.`is`(CloudType.WEBDAV.name))
		Assert.assertThat(vault.password, CoreMatchers.`is`("password"))

		val cloud = vaults[0].folderCloud
		Assert.assertThat(cloud.id, CoreMatchers.`is`(15L))
		Assert.assertThat(cloud.type, CoreMatchers.`is`(CloudType.WEBDAV.name))
		Assert.assertThat(cloud.username, CoreMatchers.`is`("username"))
		Assert.assertThat(cloud.url, CoreMatchers.`is`("https://example.org/webdav"))
		Assert.assertThat(cloud.accessTokenCryptoMode, CoreMatchers.`is`(CryptoMode.GCM.name))
		Assert.assertThat(CredentialCryptor.getInstance(context, CryptoMode.GCM).decrypt(cloud.accessToken), CoreMatchers.`is`(accessToken))

		Assert.assertThat(database.updateCheckDao().load(1L), CoreMatchers.notNullValue())
	}

	private fun addWebDavVaultTo(db: SupportSQLiteDatabase, accessToken: String) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.WEBDAV.name) //
			.text("USERNAME", "username") //
			.text("ACCESS_TOKEN", accessToken) //
			.text("WEBDAV_URL", "https://example.org/webdav") //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 25) //
			.integer("FOLDER_CLOUD_ID", 15) //
			.text("FOLDER_PATH", "path") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.WEBDAV.name) //
			.text("PASSWORD", "password") //
			.executeOn(db)
	}

	private fun createGreenDaoDatabase(version: Int, create: (SupportSQLiteDatabase) -> Unit) {
		val helper = FrameworkSQLiteOpenHelperFactory().create(
			SupportSQLiteOpenHelper.Configuration.builder(context) //
				.name(CryptomatorDatabase.NAME) //
				.callback(object : SupportSQLiteOpenHelper.Callback(version) {
					override fun onCreate(db: SupportSQLiteDatabase) = create(db)
					override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
				}) //
				.build()
		)
		helper.writableDatabase
		helper.close()
	}

	private fun openWithRoom(): CryptomatorDatabase {
		return DatabaseModule().provideCryptomatorDatabase(context, databaseUpgrades())
	}

	private fun upgrades() = databaseUpgrades().all().toList()

	private fun databaseUpgrades() = DatabaseUpgrades( //
		Upgrade1To2(), //
		Upgrade2To3(context), //
		Upgrade3To4(), //
		Upgrade4To5(), //
		Upgrade5To6(), //
		Upgrade6To7(), //
		Upgrade7To8(), //
		Upgrade8To9(sharedPreferencesHandler), //
		Upgrade9To10(sharedPreferencesHandler), //
		Upgrade10To11(), //
		Upgrade11To12(sharedPreferencesHandler), //
		Upgrade12To13(context), //
		Upgrade13To14(sharedPreferencesHandler)
	)
}
