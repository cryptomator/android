package org.cryptomator.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.base.Optional
import org.cryptomator.data.db.migrations.Sql
import org.cryptomator.data.db.migrations.legacy.Upgrade10To11
import org.cryptomator.data.db.migrations.legacy.Upgrade11To12
import org.cryptomator.data.db.migrations.legacy.Upgrade1To2
import org.cryptomator.data.db.migrations.legacy.Upgrade2To3
import org.cryptomator.data.db.migrations.legacy.Upgrade3To4
import org.cryptomator.data.db.migrations.legacy.Upgrade4To5
import org.cryptomator.data.db.migrations.legacy.Upgrade5To6
import org.cryptomator.data.db.migrations.legacy.Upgrade6To7
import org.cryptomator.data.db.migrations.legacy.Upgrade7To8
import org.cryptomator.data.db.migrations.legacy.Upgrade8To9
import org.cryptomator.data.db.migrations.legacy.Upgrade9To10
import org.cryptomator.data.db.migrations.manual.Migration12To13
import org.cryptomator.domain.CloudType
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import okio.use

private const val TEST_DB = "migration-test"
private const val LATEST_LEGACY_MIGRATION = 12

@RunWith(AndroidJUnit4::class)
@SmallTest
class UpgradeDatabaseTest {

	private val instrumentation = InstrumentationRegistry.getInstrumentation()
	private val context = instrumentation.context
	private val sharedPreferencesHandler = SharedPreferencesHandler(context)

	private lateinit var db: SupportSQLiteDatabase

	@get:Rule
	val helper: MigrationTestHelper = MigrationTestHelper( //
		instrumentation, //
		CryptomatorDatabase::class.java, //
		listOf() //TODO AutoSpecs
	)

	@Before
	fun setup() {
		context.assets.open(DatabaseModule.BASE_DATABASE_ASSET).use { originStream ->
			context.getDatabasePath(TEST_DB).outputStream().use { targetStream ->
				originStream.copyTo(targetStream)
			}
		}
		db = SupportSQLiteOpenHelper.Configuration(context, TEST_DB, object : SupportSQLiteOpenHelper.Callback(LATEST_LEGACY_MIGRATION) {
			override fun onCreate(db: SupportSQLiteDatabase) {
				fail("Database should not be created, but copied from asset")
			}

			override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
				assertEquals(1, oldVersion)
				assertEquals(LATEST_LEGACY_MIGRATION, newVersion)
			}
		}).let { FrameworkSQLiteOpenHelperFactory().create(it).writableDatabase }
	}

	@After
	fun tearDown() {
		db.close()
		//Room handles creating/deleting room-only databases correctly, but this falls apart when using the FrameworkSQLiteOpenHelper directly
		context.getDatabasePath(TEST_DB).delete()
	}

	@Test
	fun upgradeAll() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)
		Upgrade9To10(sharedPreferencesHandler).migrate(db)
		Upgrade10To11().migrate(db)
		Upgrade11To12(sharedPreferencesHandler).migrate(db)
		db.close()

		helper.runMigrationsAndValidate(TEST_DB, 13, true, Migration12To13())
		helper.runMigrationsAndValidate(TEST_DB, 14, true, CryptomatorDatabase_AutoMigration_13_14_Impl()) //TODO Use correctly
		//TODO Verify content (e.g. by using "loadAll")
	}


	@Test
	fun upgrade2To3() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)

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

		Upgrade2To3(context).migrate(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)

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

		Upgrade3To4().migrate(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)

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

		Upgrade4To5().migrate(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)

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

		Upgrade5To6().migrate(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)

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

		Upgrade6To7().migrate(db)

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
	fun recoverUpgrade6to7DueToSQLiteExceptionThrown() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)

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
			.id() //
			.optionalText("LICENSE_TOKEN") //
			.optionalText("RELEASE_NOTE") //
			.optionalText("VERSION") //
			.optionalText("URL_TO_APK") //
			.optionalText("APK_SHA256") //
			.optionalText("URL_TO_RELEASE_NOTE") //
			.executeOn(db)

		Upgrade6To7().tryToRecoverFromSQLiteException(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)

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

		Upgrade7To8().migrate(db)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(4))
		}

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			Assert.assertThat(it.moveToFirst(), CoreMatchers.`is`(false))
		}
	}

	@Test
	fun upgrade8To9() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)

		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown(true)

		Upgrade8To9(sharedPreferencesHandler).migrate(db)

		Assert.assertThat(sharedPreferencesHandler.isBetaModeAlreadyShown(), CoreMatchers.`is`(false))
	}

	@Test
	fun upgrade9To10() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)

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

		Upgrade9To10(sharedPreferencesHandler).migrate(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)
		Upgrade9To10(sharedPreferencesHandler).migrate(db)

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

		Upgrade10To11().migrate(db)

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
			Assert.assertThat(it.count, CoreMatchers.`is`(2))
		}
	}

	@Test
	fun upgrade10To11UsedOnedriveCloudPreservesCloud() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)
		Upgrade9To10(sharedPreferencesHandler).migrate(db)

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

		Upgrade10To11().migrate(db)

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
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)
		Upgrade9To10(sharedPreferencesHandler).migrate(db)
		Upgrade10To11().migrate(db)

		sharedPreferencesHandler.setUpdateIntervalInDays(Optional.of(7))

		Upgrade11To12(sharedPreferencesHandler).migrate(db)

		Assert.assertThat(sharedPreferencesHandler.updateIntervalInDays(), CoreMatchers.`is`(Optional.of(1)))
	}

	@Test
	fun upgrade11To12MonthlySet() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)
		Upgrade9To10(sharedPreferencesHandler).migrate(db)
		Upgrade10To11().migrate(db)

		sharedPreferencesHandler.setUpdateIntervalInDays(Optional.of(30))

		Upgrade11To12(sharedPreferencesHandler).migrate(db)

		Assert.assertThat(sharedPreferencesHandler.updateIntervalInDays(), CoreMatchers.`is`(Optional.of(1)))
	}

	@Test
	fun upgrade11To12MonthlyNever() {
		//Upgrade0To1().migrate(db)
		Upgrade1To2().migrate(db)
		Upgrade2To3(context).migrate(db)
		Upgrade3To4().migrate(db)
		Upgrade4To5().migrate(db)
		Upgrade5To6().migrate(db)
		Upgrade6To7().migrate(db)
		Upgrade7To8().migrate(db)
		Upgrade8To9(sharedPreferencesHandler).migrate(db)
		Upgrade9To10(sharedPreferencesHandler).migrate(db)
		Upgrade10To11().migrate(db)

		sharedPreferencesHandler.setUpdateIntervalInDays(Optional.absent())

		Upgrade11To12(sharedPreferencesHandler).migrate(db)

		Assert.assertThat(sharedPreferencesHandler.updateIntervalInDays(), CoreMatchers.`is`(Optional.absent()))
	}
}