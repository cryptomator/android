package org.cryptomator.data.db

import android.content.Context
import android.database.Cursor
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.room.util.copyAndClose
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.base.Optional
import org.cryptomator.data.db.CryptomatorAssert.assertCursorEquals
import org.cryptomator.data.db.CryptomatorAssert.assertIsUUID
import org.cryptomator.data.db.SQLiteCacheControl.asCacheControlled
import org.cryptomator.data.db.migrations.MigrationContainer
import org.cryptomator.data.db.migrations.Sql
import org.cryptomator.data.db.migrations.legacy.Upgrade10To11
import org.cryptomator.data.db.migrations.legacy.Upgrade11To12
import org.cryptomator.data.db.migrations.legacy.Upgrade12To13
import org.cryptomator.data.db.migrations.legacy.Upgrade1To2
import org.cryptomator.data.db.migrations.legacy.Upgrade2To3
import org.cryptomator.data.db.migrations.legacy.Upgrade3To4
import org.cryptomator.data.db.migrations.legacy.Upgrade4To5
import org.cryptomator.data.db.migrations.legacy.Upgrade5To6
import org.cryptomator.data.db.migrations.legacy.Upgrade6To7
import org.cryptomator.data.db.migrations.legacy.Upgrade7To8
import org.cryptomator.data.db.migrations.legacy.Upgrade8To9
import org.cryptomator.data.db.migrations.legacy.Upgrade9To10
import org.cryptomator.data.db.migrations.manual.Migration13To14
import org.cryptomator.data.db.templating.DbTemplateModule
import org.cryptomator.data.db.templating.TemplateDatabaseContext
import org.cryptomator.domain.CloudType
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.CryptoMode
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.nio.file.Files

private const val TEST_DB = "migration-test"
private const val LATEST_LEGACY_MIGRATION = 13

private const val UUID_LENGTH = 36

@RunWith(AndroidJUnit4::class)
@SmallTest
class UpgradeDatabaseTest {

	private val instrumentation = InstrumentationRegistry.getInstrumentation()
	private val context = instrumentation.context
	private val sharedPreferencesHandler = SharedPreferencesHandler(context)

	private val templateDbStream = DbTemplateModule().let {
		it.provideDbTemplateStream(it.provideConfiguration(TemplateDatabaseContext(context)))
	}.also {
		require(it.markSupported())
		it.mark(it.available())
	}

	private lateinit var migrationContainer: MigrationContainer
	private lateinit var openHelper: SupportSQLiteOpenHelper
	private lateinit var db: SupportSQLiteDatabase

	@get:Rule
	val helper: MigrationTestHelper = MigrationTestHelper( //
		instrumentation, //
		CryptomatorDatabase::class.java, //
		listOf(), //TODO AutoSpecs
		DatabaseOpenHelperFactory { throw IllegalStateException() }
	)

	@Before
	fun setup() {
		context.getDatabasePath(TEST_DB).also { dbFile ->
			if (dbFile.exists()) {
				//This may happen when killing the process while using the debugger
				println("Test database \"${dbFile.absolutePath}\" not cleaned up. Deleting...")
				dbFile.delete()
			}
			Files.copy(templateDbStream, dbFile.toPath())
		}

		migrationContainer = MigrationContainer(
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
			//
			Migration13To14(), //
			//Auto: 14 -> 15
		)

		val config = SupportSQLiteOpenHelper.Configuration.builder(context) //
			.name(TEST_DB) //
			.callback(object : SupportSQLiteOpenHelper.Callback(LATEST_LEGACY_MIGRATION) {
				override fun onConfigure(db: SupportSQLiteDatabase) = db.applyDefaultConfiguration( //
					assertedWalEnabledStatus = false //
				)

				override fun onCreate(db: SupportSQLiteDatabase) {
					fail("Database should not be created, but copied from template")
				}

				override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
					assertEquals(1, oldVersion)
					assertEquals(LATEST_LEGACY_MIGRATION, newVersion)
				}
			}).build()

		openHelper = FrameworkSQLiteOpenHelperFactory().asCacheControlled().create(config)
		openHelper.setWriteAheadLoggingEnabled(false)
		db = openHelper.writableDatabase
	}

	@After
	fun tearDown() {
		db.close()
		openHelper.close()
		//Room handles creating/deleting room-only databases correctly, but this falls apart when using the FrameworkSQLiteOpenHelper directly
		context.getDatabasePath(TEST_DB).delete()
		templateDbStream.reset()
	}

	@Test
	fun upgradeAll() {
		migrationContainer.applyPath(db, 1, 13)
		db.close()

		runMigrationsAndValidate(14, Migration13To14())
		runMigrationsAndValidate(15, CryptomatorDatabase_AutoMigration_14_15_Impl())
	}

	@Throws(IOException::class)
	private fun runMigrationsAndValidate(version: Int, vararg migrations: Migration): SupportSQLiteDatabase {
		return helper.runMigrationsAndValidate(TEST_DB, version, true, *migrations).also { db -> helper.closeWhenFinished(db) }
	}

	@Test
	fun upgrade2To3() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade2To3>(db, 1, 2)

		val url = "url"
		val username = "username"
		val webdavCertificate = "webdavCertificate"
		val accessToken = "accessToken"

		Sql.update("CLOUD_ENTITY") //
			.where("TYPE", Sql.eq("DROPBOX")) //
			.set("ACCESS_TOKEN", Sql.toString(accessToken)) //
			.set("WEBDAV_URL", Sql.toString(url)) //
			.set("USERNAME", Sql.toString(username)) //
			.set("WEBDAV_CERTIFICATE", Sql.toString(webdavCertificate)) //
			.executeOn(db)

		Sql.update("CLOUD_ENTITY") //
			.where("TYPE", Sql.eq("ONEDRIVE")) //
			.set("ACCESS_TOKEN", Sql.toString("NOT USED")) //
			.set("WEBDAV_URL", Sql.toString(url)) //
			.set("USERNAME", Sql.toString(username)) //
			.set("WEBDAV_CERTIFICATE", Sql.toString(webdavCertificate)) //
			.executeOn(db)

		context.getSharedPreferences("com.microsoft.live", Context.MODE_PRIVATE).edit().putString("refresh_token", accessToken).commit()

		testedUpgrade.migrate(db)

		checkUpgrade2to3ResultForCloud("DROPBOX", accessToken, url, username, webdavCertificate)
		checkUpgrade2to3ResultForCloud("ONEDRIVE", accessToken, url, username, webdavCertificate)

		Assert.assertThat(context.getSharedPreferences("com.microsoft.live", Context.MODE_PRIVATE).getString("refresh_token", null), CoreMatchers.nullValue())
	}

	private fun checkUpgrade2to3ResultForCloud(cloudName: String, accessToken: String, url: String, username: String, webdavCertificate: String) {
		Sql.query("CLOUD_ENTITY").where("TYPE", Sql.eq(cloudName)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(CredentialCryptor.getInstance(context).decrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN"))), CoreMatchers.`is`(accessToken))
			Assert.assertThat(it.getString(it.getColumnIndex("WEBDAV_URL")), CoreMatchers.`is`(url))
			Assert.assertThat(it.getString(it.getColumnIndex("USERNAME")), CoreMatchers.`is`(username))
			Assert.assertThat(it.getString(it.getColumnIndex("WEBDAV_CERTIFICATE")), CoreMatchers.`is`(webdavCertificate))
		}
	}

	@Test
	fun upgrade3To4() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade3To4>(db, 1, 3)

		val ids = arrayOf("10", "20", "31", "32", "51")

		ids.forEach {
			Sql.insertInto("VAULT_ENTITY") //
				.integer("_id", Integer.parseInt(it)) //
				.integer("FOLDER_CLOUD_ID", 1) //
				.text("FOLDER_PATH", "path${it}") //
				.text("FOLDER_NAME", "name${it}") //
				.text("CLOUD_TYPE", CloudType.DROPBOX.name) //
				.text("PASSWORD", "password${it}") //
				.executeOn(db)
		}

		testedUpgrade.migrate(db)

		Sql.query("VAULT_ENTITY").where("CLOUD_TYPE", Sql.eq(CloudType.DROPBOX.name)).executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(ids.size))
			while (it.moveToNext()) {
				Assert.assertThat(it.getString(it.getColumnIndex("_id")), CoreMatchers.`is`(ids[it.position]))
				Assert.assertThat(it.getInt(it.getColumnIndex("POSITION")), CoreMatchers.`is`(it.position))
				Assert.assertThat(it.getInt(it.getColumnIndex("FOLDER_CLOUD_ID")), CoreMatchers.`is`(1))
				Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`("path${ids[it.position]}"))
				Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`("name${ids[it.position]}"))
				Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.DROPBOX.name))
				Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD")), CoreMatchers.`is`("password${ids[it.position]}"))
			}
		}
	}

	@Test
	fun upgrade4To5() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade4To5>(db, 1, 4)

		val cloudId = 15
		val cloudUrl = "url"
		val username = "username"
		val webdavCertificate = "webdavCertificate"
		val accessToken = "accessToken"

		val vaultId = 25
		val folderPath = "path"
		val folderName = "name"
		val password = "password"
		val position = 10

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", cloudId) //
			.text("TYPE", CloudType.WEBDAV.name) //
			.text("ACCESS_TOKEN", accessToken) //
			.text("WEBDAV_URL", cloudUrl) //
			.text("USERNAME", username) //
			.text("WEBDAV_CERTIFICATE", webdavCertificate) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", vaultId) //
			.integer("FOLDER_CLOUD_ID", cloudId) //
			.text("FOLDER_PATH", folderPath) //
			.text("FOLDER_NAME", folderName) //
			.text("CLOUD_TYPE", CloudType.WEBDAV.name) //
			.text("PASSWORD", password) //
			.integer("POSITION", position) //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("TYPE", Sql.eq(CloudType.WEBDAV.name)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getInt(it.getColumnIndex("_id")), CoreMatchers.`is`(cloudId))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN")), CoreMatchers.`is`(accessToken))
			Assert.assertThat(it.getString(it.getColumnIndex("URL")), CoreMatchers.`is`(cloudUrl))
			Assert.assertThat(it.getString(it.getColumnIndex("USERNAME")), CoreMatchers.`is`(username))
			Assert.assertThat(it.getString(it.getColumnIndex("WEBDAV_CERTIFICATE")), CoreMatchers.`is`(webdavCertificate))
		}

		Sql.query("VAULT_ENTITY").where("CLOUD_TYPE", Sql.eq(CloudType.WEBDAV.name)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getInt(it.getColumnIndex("_id")), CoreMatchers.`is`(vaultId))
			Assert.assertThat(it.getInt(it.getColumnIndex("FOLDER_CLOUD_ID")), CoreMatchers.`is`(cloudId))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`(folderPath))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`(folderName))
			Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.WEBDAV.name))
			Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD")), CoreMatchers.`is`(password))
			Assert.assertThat(it.getInt(it.getColumnIndex("POSITION")), CoreMatchers.`is`(position))
		}
	}


	@Test
	fun upgrade5To6() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade5To6>(db, 1, 5)

		val cloudId = 15
		val cloudUrl = "url"
		val username = "username"
		val webdavCertificate = "webdavCertificate"
		val accessToken = "accessToken"

		val vaultId = 25
		val folderPath = "path"
		val folderName = "name"
		val password = "password"
		val position = 10

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", cloudId) //
			.text("TYPE", CloudType.WEBDAV.name) //
			.text("ACCESS_TOKEN", accessToken) //
			.text("URL", cloudUrl) //
			.text("USERNAME", username) //
			.text("WEBDAV_CERTIFICATE", webdavCertificate) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", vaultId) //
			.integer("FOLDER_CLOUD_ID", cloudId) //
			.text("FOLDER_PATH", folderPath) //
			.text("FOLDER_NAME", folderName) //
			.text("CLOUD_TYPE", CloudType.WEBDAV.name) //
			.text("PASSWORD", password) //
			.integer("POSITION", position) //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("TYPE", Sql.eq(CloudType.WEBDAV.name)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getInt(it.getColumnIndex("_id")), CoreMatchers.`is`(cloudId))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN")), CoreMatchers.`is`(accessToken))
			Assert.assertThat(it.getString(it.getColumnIndex("URL")), CoreMatchers.`is`(cloudUrl))
			Assert.assertThat(it.getString(it.getColumnIndex("USERNAME")), CoreMatchers.`is`(username))
			Assert.assertThat(it.getString(it.getColumnIndex("WEBDAV_CERTIFICATE")), CoreMatchers.`is`(webdavCertificate))
		}

		Sql.query("VAULT_ENTITY").where("CLOUD_TYPE", Sql.eq(CloudType.WEBDAV.name)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getInt(it.getColumnIndex("_id")), CoreMatchers.`is`(vaultId))
			Assert.assertThat(it.getInt(it.getColumnIndex("FOLDER_CLOUD_ID")), CoreMatchers.`is`(cloudId))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`(folderPath))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`(folderName))
			Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.WEBDAV.name))
			Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD")), CoreMatchers.`is`(password))
			Assert.assertThat(it.getInt(it.getColumnIndex("POSITION")), CoreMatchers.`is`(position))
		}

	}

	@Test
	fun upgrade6To7() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade6To7>(db, 1, 6)

		val licenseToken = "licenseToken"
		val releaseNote = "releaseNote"
		val version = "version"
		val urlApk = "urlApk"
		val urlReleaseNote = "urlReleaseNote"

		Sql.update("UPDATE_CHECK_ENTITY") //
			.set("LICENSE_TOKEN", Sql.toString(licenseToken)) //
			.set("RELEASE_NOTE", Sql.toString(releaseNote)) //
			.set("VERSION", Sql.toString(version)) //
			.set("URL_TO_APK", Sql.toString(urlApk)) //
			.set("URL_TO_RELEASE_NOTE", Sql.toString(urlReleaseNote)) //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("UPDATE_CHECK_ENTITY").executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getString(it.getColumnIndex("LICENSE_TOKEN")), CoreMatchers.`is`(licenseToken))
			Assert.assertThat(it.getString(it.getColumnIndex("RELEASE_NOTE")), CoreMatchers.`is`(releaseNote))
			Assert.assertThat(it.getString(it.getColumnIndex("VERSION")), CoreMatchers.`is`(version))
			Assert.assertThat(it.getString(it.getColumnIndex("URL_TO_APK")), CoreMatchers.`is`(urlApk))
			Assert.assertThat(it.getString(it.getColumnIndex("APK_SHA256")), CoreMatchers.nullValue())
			Assert.assertThat(it.getString(it.getColumnIndex("URL_TO_RELEASE_NOTE")), CoreMatchers.`is`(urlReleaseNote))
		}
	}

	@Test
	fun upgrade6To7DueToSQLiteExceptionThrown() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade6To7>(db, 1, 6)

		val licenseToken = "licenseToken"

		Sql.update("UPDATE_CHECK_ENTITY") //
			.set("LICENSE_TOKEN", Sql.toString(licenseToken)) //
			.set("RELEASE_NOTE", Sql.toString("releaseNote")) //
			.set("VERSION", Sql.toString("version")) //
			.set("URL_TO_APK", Sql.toString("urlApk")) //
			.set("URL_TO_RELEASE_NOTE", Sql.toString("urlReleaseNote")) //
			.executeOn(db)

		Sql.alterTable("UPDATE_CHECK_ENTITY").renameTo("UPDATE_CHECK_ENTITY_OLD").executeOn(db)

		Sql.createTable("UPDATE_CHECK_ENTITY") //
			.pre15Id() //
			.optionalText("LICENSE_TOKEN") //
			.optionalText("RELEASE_NOTE") //
			.optionalText("VERSION") //
			.optionalText("URL_TO_APK") //
			.optionalText("APK_SHA256") //
			.optionalText("URL_TO_RELEASE_NOTE") //
			.executeOn(db)

		testedUpgrade.tryToRecoverFromSQLiteException(db)

		Sql.query("UPDATE_CHECK_ENTITY").executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getString(it.getColumnIndex("LICENSE_TOKEN")), CoreMatchers.`is`(licenseToken))
			Assert.assertThat(it.getString(it.getColumnIndex("RELEASE_NOTE")), CoreMatchers.nullValue())
			Assert.assertThat(it.getString(it.getColumnIndex("VERSION")), CoreMatchers.nullValue())
			Assert.assertThat(it.getString(it.getColumnIndex("URL_TO_APK")), CoreMatchers.nullValue())
			Assert.assertThat(it.getString(it.getColumnIndex("APK_SHA256")), CoreMatchers.nullValue())
			Assert.assertThat(it.getString(it.getColumnIndex("URL_TO_RELEASE_NOTE")), CoreMatchers.nullValue())
		}
	}

	@Test
	fun upgrade7To8() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade7To8>(db, 1, 7)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.S3.name) //
			.text("URL", "url") //
			.text("USERNAME", "username") //
			.text("WEBDAV_CERTIFICATE", "certificate") //
			.text("ACCESS_TOKEN", "accessToken") //
			.text("S3_BUCKET", "s3Bucket") //
			.text("S3_REGION", "s3Region") //
			.text("S3_SECRET_KEY", "s3SecretKey") //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 25) //
			.integer("FOLDER_CLOUD_ID", 15) //
			.text("FOLDER_PATH", "path") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.S3.name) //
			.text("PASSWORD", "password") //
			.integer("POSITION", 10) //
			.executeOn(db)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(5))
		}

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(4))
		}

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			Assert.assertThat(it.moveToFirst(), CoreMatchers.`is`(false))
		}
	}

	@Test
	fun upgrade8To9() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade8To9>(db, 1, 8)

		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown(true)

		testedUpgrade.migrate(db)

		Assert.assertThat(sharedPreferencesHandler.isBetaModeAlreadyShown(), CoreMatchers.`is`(false))
	}

	@Test
	fun upgrade9To10() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade9To10>(db, 1, 9)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.LOCAL.name) //
			.text("URL", "url") //
			.text("USERNAME", "username") //
			.text("WEBDAV_CERTIFICATE", "certificate") //
			.text("ACCESS_TOKEN", "accessToken") //
			.text("S3_BUCKET", "s3Bucket") //
			.text("S3_REGION", "s3Region") //
			.text("S3_SECRET_KEY", "s3SecretKey") //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 25) //
			.integer("FOLDER_CLOUD_ID", 15) //
			.text("FOLDER_PATH", "path") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.LOCAL.name) //
			.text("PASSWORD", "password") //
			.integer("POSITION", 10) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 26) //
			.integer("FOLDER_CLOUD_ID", 4) //
			.text("FOLDER_PATH", "pathOfVault26") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.LOCAL.name) //
			.text("PASSWORD", "password") //
			.integer("POSITION", 11) //
			.executeOn(db)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(5))
		}

		testedUpgrade.migrate(db)

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(1))
		}

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(4))
		}

		Assert.assertThat(sharedPreferencesHandler.vaultsRemovedDuringMigration(), CoreMatchers.`is`(Pair("LOCAL", arrayListOf("pathOfVault26"))))
	}

	@Test
	fun upgrade10To11EmptyOnedriveCloudRemovesCloud() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade10To11>(db, 1, 10)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 25) //
			.integer("FOLDER_CLOUD_ID", 3) //
			.text("FOLDER_PATH", "path") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.ONEDRIVE.name) //
			.text("PASSWORD", "password") //
			.integer("POSITION", 10) //
			.executeOn(db)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(3))
		}

		testedUpgrade.migrate(db)

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(1))
		}

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_CLOUD_ID")), CoreMatchers.nullValue())
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`("path"))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`("name"))
			Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.ONEDRIVE.name))
			Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD")), CoreMatchers.`is`("password"))
			Assert.assertThat(it.getString(it.getColumnIndex("POSITION")), CoreMatchers.`is`("10"))
			Assert.assertThat(it.getString(it.getColumnIndex("FORMAT")), CoreMatchers.`is`("8"))
			Assert.assertThat(it.getString(it.getColumnIndex("SHORTENING_THRESHOLD")), CoreMatchers.`is`("220"))
		}

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(2))
		}
	}

	@Test
	fun upgrade10To11UsedOnedriveCloudPreservesCloud() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade10To11>(db, 1, 10)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 25) //
			.integer("FOLDER_CLOUD_ID", 3) //
			.text("FOLDER_PATH", "path") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.ONEDRIVE.name) //
			.text("PASSWORD", "password") //
			.integer("POSITION", 10) //
			.executeOn(db)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			while (it.moveToNext()) {
				Sql.update("CLOUD_ENTITY") //
					.where("_id", Sql.eq(3L)) //
					.set("ACCESS_TOKEN", Sql.toString("Access token 3000")) //
					.set("USERNAME", Sql.toString("foo@bar.baz")) //
					.executeOn(db)
			}
		}
		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(3))
		}

		testedUpgrade.migrate(db)

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(1))
		}

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_CLOUD_ID")), CoreMatchers.`is`("3"))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`("path"))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`("name"))
			Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.ONEDRIVE.name))
			Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD")), CoreMatchers.`is`("password"))
			Assert.assertThat(it.getString(it.getColumnIndex("POSITION")), CoreMatchers.`is`("10"))
			Assert.assertThat(it.getString(it.getColumnIndex("FORMAT")), CoreMatchers.`is`("8"))
			Assert.assertThat(it.getString(it.getColumnIndex("SHORTENING_THRESHOLD")), CoreMatchers.`is`("220"))
		}

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(3))
		}
	}

	@Test
	fun upgrade11To12IfOldDefaultSet() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade11To12>(db, 1, 11)

		sharedPreferencesHandler.setUpdateIntervalInDays(Optional.of(7))

		testedUpgrade.migrate(db)

		Assert.assertThat(sharedPreferencesHandler.updateIntervalInDays(), CoreMatchers.`is`(Optional.of(1)))
	}

	@Test
	fun upgrade11To12MonthlySet() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade11To12>(db, 1, 11)

		sharedPreferencesHandler.setUpdateIntervalInDays(Optional.of(30))

		testedUpgrade.migrate(db)

		Assert.assertThat(sharedPreferencesHandler.updateIntervalInDays(), CoreMatchers.`is`(Optional.of(1)))
	}

	@Test
	fun upgrade11To12MonthlyNever() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade11To12>(db, 1, 11)

		sharedPreferencesHandler.setUpdateIntervalInDays(Optional.absent())

		testedUpgrade.migrate(db)

		Assert.assertThat(sharedPreferencesHandler.updateIntervalInDays(), CoreMatchers.`is`(Optional.absent()))
	}

	@Test
	fun upgrade12To13BaseTests() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		val gcmCryptor = CredentialCryptor.getInstance(context, CryptoMode.GCM)
		val cbcCryptor = CredentialCryptor.getInstance(context, CryptoMode.CBC)

		val accessTokenPlain = "accessToken"
		val accessTokenCiphertext = cbcCryptor.encrypt(accessTokenPlain)
		val s3SecretPlain = "s3SecretKey"
		val s3SecretCiphertext = cbcCryptor.encrypt(s3SecretPlain)
		val vaultPasswordPlain = "password"

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.S3.name) //
			.text("URL", "url") //
			.text("ACCESS_TOKEN", accessTokenCiphertext)
			.text("S3_BUCKET", "s3Bucket") //
			.text("S3_REGION", "s3Region") //
			.text("S3_SECRET_KEY", s3SecretCiphertext) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 1) //
			.integer("FOLDER_CLOUD_ID", 15) //
			.text("FOLDER_PATH", "path1") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.S3.name) //
			.text("PASSWORD", vaultPasswordPlain) //
			.integer("POSITION", 1) //
			.integer("FORMAT", 8) //
			.integer("SHORTENING_THRESHOLD", 4)
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 2) //
			.integer("FOLDER_CLOUD_ID", 15) //
			.text("FOLDER_PATH", "path2") //
			.text("FOLDER_NAME", "name") //
			.text("CLOUD_TYPE", CloudType.S3.name) //
			.text("PASSWORD", null) //
			.integer("POSITION", 2) //
			.integer("FORMAT", 8) //
			.integer("SHORTENING_THRESHOLD", 4)
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(gcmCryptor.decrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN"))), CoreMatchers.`is`(accessTokenPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.GCM.name))
			Assert.assertThat(gcmCryptor.decrypt(it.getString(it.getColumnIndex("S3_SECRET_KEY"))), CoreMatchers.`is`(s3SecretPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("S3_SECRET_KEY_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.GCM.name))
		}

		Sql.query("VAULT_ENTITY").where("_id", Sql.eq(1)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD")), CoreMatchers.`is`(vaultPasswordPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("PASSWORD_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.CBC.name))

			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`("path1"))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`("name"))
			Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.S3.name))
			Assert.assertThat(it.getInt(it.getColumnIndex("POSITION")), CoreMatchers.`is`(1))
			Assert.assertThat(it.getInt(it.getColumnIndex("FORMAT")), CoreMatchers.`is`(8))
			Assert.assertThat(it.getInt(it.getColumnIndex("SHORTENING_THRESHOLD")), CoreMatchers.`is`(4))
		}

		Sql.query("VAULT_ENTITY").where("_id", Sql.eq(2)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertNull(it.getString(it.getColumnIndex("PASSWORD")))
			Assert.assertNull(it.getString(it.getColumnIndex("PASSWORD_CRYPTO_MODE")))

			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_PATH")), CoreMatchers.`is`("path2"))
			Assert.assertThat(it.getString(it.getColumnIndex("FOLDER_NAME")), CoreMatchers.`is`("name"))
			Assert.assertThat(it.getString(it.getColumnIndex("CLOUD_TYPE")), CoreMatchers.`is`(CloudType.S3.name))
			Assert.assertThat(it.getInt(it.getColumnIndex("POSITION")), CoreMatchers.`is`(2))
			Assert.assertThat(it.getInt(it.getColumnIndex("FORMAT")), CoreMatchers.`is`(8))
			Assert.assertThat(it.getInt(it.getColumnIndex("SHORTENING_THRESHOLD")), CoreMatchers.`is`(4))
		}
	}

	@Test
	fun upgrade12To13DropGoogleDriveUsernameInAccessToken() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.GOOGLE_DRIVE.name) //
			.text("USERNAME", "username") //
			.text("ACCESS_TOKEN", "username") //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertNull(it.getString(it.getColumnIndex("ACCESS_TOKEN")))
		}
	}

	@Test
	fun upgrade12To13MovingAccessTokenToUrlInLocalStorage() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.LOCAL.name) //
			.text("ACCESS_TOKEN", "testUrl3000") //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(it.getString(it.getColumnIndex("URL")), CoreMatchers.`is`("testUrl3000"))
			Assert.assertNull(it.getString(it.getColumnIndex("ACCESS_TOKEN")))
		}
	}

	@Test
	fun upgrade12To13Dropbox() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		val gcmCryptor = CredentialCryptor.getInstance(context, CryptoMode.GCM)
		val cbcCryptor = CredentialCryptor.getInstance(context, CryptoMode.CBC)

		val accessTokenPlain = "accessToken"
		val accessTokenCiphertext = cbcCryptor.encrypt(accessTokenPlain)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.DROPBOX.name) //
			.text("USERNAME", "username") //
			.text("ACCESS_TOKEN", accessTokenCiphertext) //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(gcmCryptor.decrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN"))), CoreMatchers.`is`(accessTokenPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.GCM.name))
		}
	}


	@Test
	fun upgrade12To13OneDrive() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		val gcmCryptor = CredentialCryptor.getInstance(context, CryptoMode.GCM)
		val cbcCryptor = CredentialCryptor.getInstance(context, CryptoMode.CBC)

		val accessTokenPlain = "accessToken"
		val accessTokenCiphertext = cbcCryptor.encrypt(accessTokenPlain)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.ONEDRIVE.name) //
			.text("USERNAME", "username") //
			.text("ACCESS_TOKEN", accessTokenCiphertext) //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(gcmCryptor.decrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN"))), CoreMatchers.`is`(accessTokenPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.GCM.name))
		}
	}

	@Test
	fun upgrade12To13PCloud() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		val gcmCryptor = CredentialCryptor.getInstance(context, CryptoMode.GCM)
		val cbcCryptor = CredentialCryptor.getInstance(context, CryptoMode.CBC)

		val accessTokenPlain = "accessToken"
		val accessTokenCiphertext = cbcCryptor.encrypt(accessTokenPlain)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.PCLOUD.name) //
			.text("USERNAME", "username") //
			.text("ACCESS_TOKEN", accessTokenCiphertext) //
			.text("URL", "url") //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(gcmCryptor.decrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN"))), CoreMatchers.`is`(accessTokenPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.GCM.name))
		}
	}

	@Test
	fun upgrade12To13Webdav() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Upgrade12To13>(db, 1, 12)

		val gcmCryptor = CredentialCryptor.getInstance(context, CryptoMode.GCM)
		val cbcCryptor = CredentialCryptor.getInstance(context, CryptoMode.CBC)

		val accessTokenPlain = "accessToken"
		val accessTokenCiphertext = cbcCryptor.encrypt(accessTokenPlain)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.WEBDAV.name) //
			.text("USERNAME", "username") //
			.text("ACCESS_TOKEN", accessTokenCiphertext) //
			.text("URL", "url") //
			.executeOn(db)

		testedUpgrade.migrate(db)

		Sql.query("CLOUD_ENTITY").where("_id", Sql.eq(15)).executeOn(db).use {
			it.moveToFirst()
			Assert.assertThat(gcmCryptor.decrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN"))), CoreMatchers.`is`(accessTokenPlain))
			Assert.assertThat(it.getString(it.getColumnIndex("ACCESS_TOKEN_CRYPTO_MODE")), CoreMatchers.`is`(CryptoMode.GCM.name))
		}
	}

	@Test
	fun migrate13To15ForeignKeySideEffects() { //See: Migration13To14
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Migration13To14>(db, 1, 13)

		val pre14Statement = referencesStatement(db)
		val pre14Expected = "CONSTRAINT FK_FOLDER_CLOUD_ID_CLOUD_ENTITY FOREIGN KEY (FOLDER_CLOUD_ID) REFERENCES CLOUD_ENTITY(_id) ON DELETE SET NULL"
		//This is a sanity check and may need to be updated if Sql.java is changed
		assertTrue("Expected \".*$pre14Expected.*\", got \"$pre14Statement\"", pre14Statement.contains(pre14Expected))
		db.close()

		runMigrationsAndValidate(14, testedUpgrade).also { migratedDb ->
			val statement = referencesStatement(migratedDb)
			assertEquals(pre14Statement, statement)
		}

		runMigrationsAndValidate(15, CryptomatorDatabase_AutoMigration_14_15_Impl()).also { migratedDb ->
			val statement = referencesStatement(migratedDb)
			val expected = "FOREIGN KEY(folderCloudId) REFERENCES CLOUD_ENTITY(id) ON"
			assertTrue("Expected \".*$expected.*\", got \"$statement\"", statement.contains(expected))
			assertFalse(statement.contains("CONSTRAINT"))
			assertFalse(statement.contains("FK_FOLDER_CLOUD_ID_CLOUD_ENTITY"))

			assertTrue(statement.contains("ON UPDATE NO ACTION"))
			assertTrue(statement.contains("ON DELETE RESTRICT"))
		}
	}

	private fun referencesStatement(db: SupportSQLiteDatabase): String {
		return Sql.SqlQueryBuilder("sqlite_master") //
			.columns(listOf("sql")) //
			.where("tbl_name", Sql.eq("VAULT_ENTITY")) //
			.where("sql", Sql.like("%REFERENCES%")) //
			.executeOn(db).use {
				assertEquals(it.count, 1)
				assertTrue(it.moveToNext())
				it.getString(0)
			}.filterNot { it in "\"'`" }
	}

	@Test
	fun migrate13To15IndexSideEffects() { //See: Migration13To14
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Migration13To14>(db, 1, 13)

		val pre14Statement = indexStatement(db)
		val pre14Expected = "CREATE UNIQUE INDEX \"IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID\" ON \"VAULT_ENTITY\" (\"FOLDER_PATH\" ASC,\"FOLDER_CLOUD_ID\" ASC) -- "
		//This is a sanity check and may need to be updated if Sql.java is changed
		assertEquals(pre14Expected, pre14Statement.substring(0, pre14Statement.length - UUID_LENGTH))
		assertIsUUID(pre14Statement.substring(pre14Statement.length - UUID_LENGTH))
		db.close()

		runMigrationsAndValidate(14, testedUpgrade).also { migratedDb ->
			val statement = indexStatement(migratedDb)
			assertEquals(pre14Statement, statement)
		}

		runMigrationsAndValidate(15, CryptomatorDatabase_AutoMigration_14_15_Impl()).also { migratedDb ->
			val statement = indexStatement(migratedDb)
			val expected = "CREATE UNIQUE INDEX `IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID` ON `VAULT_ENTITY` (`folderPath` ASC, `folderCloudId` ASC)"
			assertEquals(expected, statement)
		}
	}

	private fun indexStatement(db: SupportSQLiteDatabase): String {
		return Sql.SqlQueryBuilder("sqlite_master") //
			.columns(listOf("sql")) //
			.where("type", Sql.eq("index")) //
			.where("name", Sql.eq("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID")) //
			.where("tbl_name", Sql.eq("VAULT_ENTITY")) //
			.executeOn(db).use {
				assertEquals(it.count, 1)
				assertTrue(it.moveToNext())
				it.getString(0)
			}
	}

	@Test
	fun migrate13To14() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Migration13To14>(db, 1, 13)

		assertEquals(13, db.version)
		val pre14Tables: Map<String, Cursor> = listOf("CLOUD_ENTITY", "UPDATE_CHECK_ENTITY", "VAULT_ENTITY").associateWith { tableName ->
			val cursor = Sql.query(tableName).executeOn(db)
			copyAndClose(cursor)
		}
		db.close()

		runMigrationsAndValidate(14, testedUpgrade).also { migratedDb ->
			assertTrue(migratedDb.hasRoomMasterTable)
			assertEquals(14, migratedDb.version)

			for (preTable in pre14Tables) {
				preTable.value.use { preCursor ->
					Sql.query(preTable.key).executeOn(migratedDb).use { postCursor ->
						assertCursorEquals(preCursor, postCursor)
					}
				}
			}
		}
	}

	@Test
	fun migrate13To14WithData() {
		val testedUpgrade = migrationContainer.applyPathAndReturnNext<Migration13To14>(db, 1, 13)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 3) //
			.text("TYPE", CloudType.LOCAL.name) //
			.text("URL", "url1") //
			.text("USERNAME", "username1") //
			.text("WEBDAV_CERTIFICATE", "certificate1") //
			.text("ACCESS_TOKEN", "accessToken1") //
			.text("S3_BUCKET", "s3Bucket1") //
			.text("S3_REGION", "s3Region1") //
			.text("S3_SECRET_KEY", "s3SecretKey1") //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 10) //
			.integer("FOLDER_CLOUD_ID", 1) //
			.text("FOLDER_PATH", "path1") //
			.text("FOLDER_NAME", "name1") //
			.text("CLOUD_TYPE", CloudType.DROPBOX.name) //
			.text("PASSWORD", "password1") //
			.integer("POSITION", 10) //
			.integer("FORMAT", 42) //
			.integer("SHORTENING_THRESHOLD", 110) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.integer("_id", 20) //
			.integer("FOLDER_CLOUD_ID", 3) //
			.text("FOLDER_PATH", "path2") //
			.text("FOLDER_NAME", "name2") //
			.text("CLOUD_TYPE", CloudType.LOCAL.name) //
			.text("PASSWORD", "password2") //
			.integer("POSITION", 20) //
			.integer("FORMAT", 43) //
			.integer("SHORTENING_THRESHOLD", 120) //
			.executeOn(db)

		Sql.update("UPDATE_CHECK_ENTITY") //
			.set("LICENSE_TOKEN", Sql.toString("license1")) //
			.set("RELEASE_NOTE", Sql.toString("note1")) //
			.set("VERSION", Sql.toString("version1")) //
			.set("URL_TO_APK", Sql.toString("urlToApk1")) //
			.set("APK_SHA256", Sql.toString("sha1")) //
			.set("URL_TO_RELEASE_NOTE", Sql.toString("urlToNote1")) //
			.executeOn(db)

		assertEquals(13, db.version)
		val pre14Tables: Map<String, Cursor> = listOf("CLOUD_ENTITY", "UPDATE_CHECK_ENTITY", "VAULT_ENTITY").associateWith { tableName ->
			copyAndClose(Sql.query(tableName).executeOn(db))
		}
		db.close()

		runMigrationsAndValidate(14, testedUpgrade).also { migratedDb ->
			assertTrue(migratedDb.hasRoomMasterTable)
			assertEquals(14, migratedDb.version)

			for (preTable in pre14Tables) {
				preTable.value.use { preCursor ->
					Sql.query(preTable.key).executeOn(migratedDb).use { postCursor ->
						assertCursorEquals(preCursor, postCursor)
					}
				}
			}
		}
	}

	//TODO Test metadata of non-entity tables for v14, v15
	//TODO Test metadata and content of entity tables for v15

	@Test
	fun migrate1To14WithRoom() {
		db.version = 1
		db.close()
		runMigrationsAndValidate(
			14,
			*migrationContainer.getPath(1, 14).toTypedArray()
		)
	}
}