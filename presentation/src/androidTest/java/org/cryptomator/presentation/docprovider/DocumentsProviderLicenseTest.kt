package org.cryptomator.presentation.docprovider

import android.app.AuthenticationRequiredException
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.preference.PreferenceManager
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.util.FlavorConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Calls the provider of the app under test with a locked vault that the test stores, so that a change is refused for the
 * missing license before it gets to the unlock, and asks for the unlock with a license. Restores the license state and
 * removes what it stored afterwards.
 */
@RunWith(AndroidJUnit4::class)
class DocumentsProviderLicenseTest {

	private val context = InstrumentationRegistry.getTargetContext()
	private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
	private val originalLicense = LICENSE_KEYS.associateWith { preferences.all[it] }
	private val client = requireNotNull(context.contentResolver.acquireContentProviderClient(CryptomatorDocumentsProvider.AUTHORITY))
	private val provider = client.localContentProvider as CryptomatorDocumentsProvider
	private lateinit var cloud: Cloud
	private lateinit var vault: Vault
	private lateinit var root: String

	@Before
	fun storeLockedVault() {
		assumeFalse("Licensing logic is bypassed on this flavor", FlavorConfig.isPremiumFlavor)
		cloud = appComponent.cloudRepository().store(WebDavCloud.aWebDavCloudCloud().withUrl("https://example.org/webdav").withUsername("user").withPassword("password").build())
		vault = appComponent.vaultRepository().store(Vault.aVault().thatIsNew().withName("license-test").withPath("/license-test").withCloud(cloud).withPosition(9999).build())
		root = "${vault.id}"
	}

	@After
	fun restore() {
		if (::vault.isInitialized) {
			appComponent.vaultRepository().delete(vault)
		}
		if (::cloud.isInitialized) {
			appComponent.cloudRepository().delete(cloud)
		}
		preferences.edit().apply {
			originalLicense.forEach { (key, value) ->
				when (value) {
					null -> remove(key)
					is String -> putString(key, value)
					is Long -> putLong(key, value)
					is Boolean -> putBoolean(key, value)
					else -> error("Unexpected $key: $value")
				}
			}
		}.commit()
		client.close()
	}

	@Test
	fun withoutLicenseEveryChangeIsRefusedAndTheVaultOffersNoCreate() {
		givenTrialExpired()

		assertReadOnly { provider.createDocument(root, "text/plain", "x.txt") }
		assertReadOnly { provider.createDocument(root, DocumentsContract.Document.MIME_TYPE_DIR, "d") }
		assertReadOnly { provider.renameDocument(root, "y") }
		assertReadOnly { provider.moveDocument(root, root, root) }
		assertReadOnly { provider.deleteDocument(root) }
		listOf("w", "wt", "wa", "rw", "rwt").forEach { mode -> assertReadOnly { provider.openDocument(root, mode, null) } }
		assertEquals(0, rootFlags() and DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
	}

	@Test
	fun withLicenseEveryChangeGetsToTheUnlockAndTheVaultOffersCreate() {
		givenLicense()

		assertUnlockRequired { provider.createDocument(root, "text/plain", "x.txt") }
		assertUnlockRequired { provider.renameDocument(root, "y") }
		assertUnlockRequired { provider.moveDocument(root, root, root) }
		assertUnlockRequired { provider.deleteDocument(root) }
		assertUnlockRequired { provider.openDocument(root, "w", null) }
		assertTrue(rootFlags() and DocumentsContract.Root.FLAG_SUPPORTS_CREATE != 0)
	}

	@Test
	fun aModeWithoutWriteAccessIsAReadAlsoWithTruncate() {
		// Android 17 rejects "rt", before that it is a read that truncates, which a grant to read allows
		assumeTrue("This Android rejects the mode", runCatching { ParcelFileDescriptor.parseMode("rt") }.isSuccess)
		givenTrialExpired()

		assertUnlockRequired { provider.openDocument(root, "rt", null) }
		assertUnlockRequired { provider.openDocument(root, "r", null) }
	}

	@Test
	fun filesIsToldAboutEveryDocumentWhenTheLicenseChanges() {
		givenTrialExpired()
		rootFlags()
		val changed = CountDownLatch(1)
		val thread = HandlerThread("LicenseObserver").apply { start() }
		val observer = object : ContentObserver(Handler(thread.looper)) {
			override fun onChange(selfChange: Boolean, uri: Uri?) {
				changed.countDown()
			}
		}
		// a document, which unlike the roots isn't told about the vault the test stored
		context.contentResolver.registerContentObserver(DocumentsContract.buildDocumentUri(CryptomatorDocumentsProvider.AUTHORITY, root), false, observer)
		try {
			givenLicense()

			assertTrue("Files wasn't told", changed.await(5, TimeUnit.SECONDS))
		} finally {
			context.contentResolver.unregisterContentObserver(observer)
			thread.quitSafely()
		}
	}

	private fun givenTrialExpired() {
		preferences.edit()
			.putString(LICENSE_TOKEN, "")
			.putBoolean(HAS_RUNNING_SUBSCRIPTION, false)
			.putLong(TRIAL_EXPIRATION_DATE, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
			.putBoolean(TRIAL_EXPIRED, true)
			.commit()
	}

	private fun givenLicense() {
		preferences.edit().putString(LICENSE_TOKEN, "license-test").commit()
	}

	private fun rootFlags(): Int {
		provider.queryRoots(null).use { cursor ->
			while (cursor.moveToNext()) {
				if (cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_ROOT_ID)) == root) {
					return cursor.getInt(cursor.getColumnIndexOrThrow(DocumentsContract.Root.COLUMN_FLAGS))
				}
			}
		}
		throw AssertionError("No root for vault $root, is the documents provider turned off?")
	}

	private fun assertReadOnly(change: () -> Any?) {
		try {
			change()
			fail("Not refused")
		} catch (e: UnsupportedOperationException) {
			assertTrue(e.message, e.message?.contains("read-only") == true)
		}
	}

	private fun assertUnlockRequired(change: () -> Any?) {
		try {
			change()
			fail("Didn't ask for the unlock")
		} catch (e: AuthenticationRequiredException) {
			// the change got past the license check
		}
	}

	private companion object {

		const val LICENSE_TOKEN = "licenseToken"
		const val HAS_RUNNING_SUBSCRIPTION = "hasRunningSubscription"
		const val TRIAL_EXPIRATION_DATE = "trialExpirationDate"
		const val TRIAL_EXPIRED = "trialExpired"
		val LICENSE_KEYS = listOf(LICENSE_TOKEN, HAS_RUNNING_SUBSCRIPTION, TRIAL_EXPIRATION_DATE, TRIAL_EXPIRED)
	}
}
