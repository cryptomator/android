package org.cryptomator.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.cryptomator.data.db.entities.CloudEntityDao
import org.cryptomator.data.db.entities.UpdateCheckEntityDao
import org.cryptomator.data.db.entities.VaultEntityDao
import org.cryptomator.domain.CloudType
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.greenrobot.greendao.database.Database
import org.greenrobot.greendao.database.StandardDatabase
import org.greenrobot.greendao.internal.DaoConfig
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class UpgradeDatabaseTest {

	private val context = InstrumentationRegistry.getTargetContext()
	private val sharedPreferencesHandler = SharedPreferencesHandler(context)
	private lateinit var db: Database

	@Before
	fun setup() {
		db = StandardDatabase(SQLiteDatabase.create(null))
	}

	@After
	fun tearDown() {
		db.close()
	}

	@Test
	fun upgradeAll() {
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)
		Upgrade4To5().applyTo(db, 4)
		Upgrade5To6().applyTo(db, 5)
		Upgrade6To7().applyTo(db, 6)
		Upgrade7To8().applyTo(db, 7)
		Upgrade8To9(sharedPreferencesHandler).applyTo(db, 8)

		CloudEntityDao(DaoConfig(db, CloudEntityDao::class.java)).loadAll()
		VaultEntityDao(DaoConfig(db, VaultEntityDao::class.java)).loadAll()
		UpdateCheckEntityDao(DaoConfig(db, UpdateCheckEntityDao::class.java)).loadAll()
	}


	@Test
	fun upgrade2To3() {
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)

		val url = "url"
		val username = "username"
		val webdavCertificate = "webdavCertificate"
		val accessToken = "accessToken"

		Sql.update("CLOUD_ENTITY")
			.where("TYPE", Sql.eq("DROPBOX"))
			.set("ACCESS_TOKEN", Sql.toString(accessToken))
			.set("WEBDAV_URL", Sql.toString(url))
			.set("USERNAME", Sql.toString(username))
			.set("WEBDAV_CERTIFICATE", Sql.toString(webdavCertificate))
			.executeOn(db)

		Sql.update("CLOUD_ENTITY")
			.where("TYPE", Sql.eq("ONEDRIVE"))
			.set("ACCESS_TOKEN", Sql.toString("NOT USED"))
			.set("WEBDAV_URL", Sql.toString(url))
			.set("USERNAME", Sql.toString(username))
			.set("WEBDAV_CERTIFICATE", Sql.toString(webdavCertificate))
			.executeOn(db)

		context.getSharedPreferences("com.microsoft.live", Context.MODE_PRIVATE).edit().putString("refresh_token", accessToken).commit()

		Upgrade2To3(context).applyTo(db, 2)

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
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)

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

		Upgrade3To4().applyTo(db, 3)

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
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)

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

		Upgrade4To5().applyTo(db, 4)

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
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)
		Upgrade4To5().applyTo(db, 4)

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

		Upgrade5To6().applyTo(db, 5)

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
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)
		Upgrade4To5().applyTo(db, 4)
		Upgrade5To6().applyTo(db, 5)

		val licenseToken = "licenseToken"
		val releaseNote = "releaseNote"
		val version = "version"
		val urlApk = "urlApk"
		val urlReleaseNote = "urlReleaseNote"

		Sql.update("UPDATE_CHECK_ENTITY")
			.set("LICENSE_TOKEN", Sql.toString(licenseToken))
			.set("RELEASE_NOTE", Sql.toString(releaseNote))
			.set("VERSION", Sql.toString(version))
			.set("URL_TO_APK", Sql.toString(urlApk))
			.set("URL_TO_RELEASE_NOTE", Sql.toString(urlReleaseNote))
			.executeOn(db)

		Upgrade6To7().applyTo(db, 6)

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
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)
		Upgrade4To5().applyTo(db, 4)
		Upgrade5To6().applyTo(db, 5)

		val licenseToken = "licenseToken"

		Sql.update("UPDATE_CHECK_ENTITY")
			.set("LICENSE_TOKEN", Sql.toString(licenseToken))
			.set("RELEASE_NOTE", Sql.toString("releaseNote"))
			.set("VERSION", Sql.toString("version"))
			.set("URL_TO_APK", Sql.toString("urlApk"))
			.set("URL_TO_RELEASE_NOTE", Sql.toString("urlReleaseNote"))
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
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)
		Upgrade4To5().applyTo(db, 4)
		Upgrade5To6().applyTo(db, 5)
		Upgrade6To7().applyTo(db, 6)

		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 15) //
			.text("TYPE", CloudType.S3.name) //
			.text("URL", "url") //
			.text("USERNAME", "username") //
			.text("WEBDAV_CERTIFICATE", "certificate") //
			.text("ACCESS_TOKEN", "accessToken")
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

		Upgrade7To8().applyTo(db, 7)

		Sql.query("CLOUD_ENTITY").executeOn(db).use {
			Assert.assertThat(it.count, CoreMatchers.`is`(4))
		}

		Sql.query("VAULT_ENTITY").executeOn(db).use {
			Assert.assertThat(it.moveToFirst(), CoreMatchers.`is`(false))
		}
	}

	@Test
	fun upgrade8To9() {
		Upgrade0To1().applyTo(db, 0)
		Upgrade1To2().applyTo(db, 1)
		Upgrade2To3(context).applyTo(db, 2)
		Upgrade3To4().applyTo(db, 3)
		Upgrade4To5().applyTo(db, 4)
		Upgrade5To6().applyTo(db, 5)
		Upgrade6To7().applyTo(db, 6)
		Upgrade7To8().applyTo(db, 7)

		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown(true)

		Upgrade8To9(sharedPreferencesHandler).applyTo(db, 8)

		Assert.assertThat(sharedPreferencesHandler.isBetaModeAlreadyShown(), CoreMatchers.`is`(false))
	}
}
