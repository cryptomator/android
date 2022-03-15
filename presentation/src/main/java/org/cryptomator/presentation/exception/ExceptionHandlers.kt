package org.cryptomator.presentation.exception

import android.content.ActivityNotFoundException
import android.content.Context
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.CloudAlreadyExistsException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchBucketException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.UnableToDecryptWebdavPasswordException
import org.cryptomator.domain.exception.VaultAlreadyExistException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.exception.license.DesktopSupporterCertificateException
import org.cryptomator.domain.exception.license.LicenseNotValidException
import org.cryptomator.domain.exception.license.NoLicenseAvailableException
import org.cryptomator.domain.exception.update.GeneralUpdateErrorException
import org.cryptomator.domain.exception.update.HashMismatchUpdateCheckException
import org.cryptomator.domain.exception.update.SSLHandshakePreAndroid5UpdateCheckException
import org.cryptomator.domain.exception.vaultconfig.MissingVaultConfigFileException
import org.cryptomator.domain.exception.vaultconfig.UnsupportedMasterkeyLocationException
import org.cryptomator.domain.exception.vaultconfig.VaultConfigLoadException
import org.cryptomator.domain.exception.vaultconfig.VaultKeyInvalidException
import org.cryptomator.domain.exception.vaultconfig.VaultVersionMismatchException
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.presentation.util.ResourceHelper
import java.util.Collections
import javax.inject.Inject
import timber.log.Timber

@PerView
class ExceptionHandlers @Inject constructor(private val context: Context, defaultExceptionHandler: DefaultExceptionHandler) : Iterable<ExceptionHandler?> {

	private val exceptionHandlers: MutableList<ExceptionHandler> = ArrayList()
	private val defaultExceptionHandler: ExceptionHandler

	private fun setupHandlers() {
		staticHandler(AuthenticationException::class.java, R.string.error_authentication_failed)
		staticHandler(NetworkConnectionException::class.java, R.string.error_no_network_connection)
		staticHandler(InvalidPassphraseException::class.java, R.string.error_invalid_passphrase)
		staticHandler(CloudNodeAlreadyExistsException::class.java, R.string.error_file_or_folder_exists)
		staticHandler(VaultAlreadyExistException::class.java, R.string.error_vault_already_exists)
		staticHandler(ActivityNotFoundException::class.java, R.string.error_activity_not_found)
		staticHandler(CloudAlreadyExistsException::class.java, R.string.error_cloud_already_exists)
		staticHandler(NoSuchCloudFileException::class.java, R.string.error_no_such_file)
		staticHandler(IllegalFileNameException::class.java, R.string.error_export_illegal_file_name)
		staticHandler(UnableToDecryptWebdavPasswordException::class.java, R.string.error_failed_to_decrypt_webdav_password)
		staticHandler(DesktopSupporterCertificateException::class.java, R.string.dialog_enter_license_not_valid_content_desktop_supporter_certificate)
		staticHandler(LicenseNotValidException::class.java, R.string.dialog_enter_license_not_valid_content)
		staticHandler(NoLicenseAvailableException::class.java, R.string.dialog_enter_license_no_content)
		staticHandler(HashMismatchUpdateCheckException::class.java, R.string.error_hash_mismatch_update)
		staticHandler(GeneralUpdateErrorException::class.java, R.string.error_general_update)
		staticHandler(SSLHandshakePreAndroid5UpdateCheckException::class.java, R.string.error_general_update)
		staticHandler(UnsupportedVaultFormatException::class.java, R.string.error_vault_version_not_supported)
		staticHandler(
			MissingVaultConfigFileException::class.java, String.format(
				ResourceHelper.getString(R.string.error_vault_config_file_missing_due_to_format_999),
				ResourceHelper.getString(R.string.vault_cryptomator)
			)
		)
		staticHandler(
			VaultVersionMismatchException::class.java, String.format(
				ResourceHelper.getString(R.string.error_vault_version_mismatch),
				ResourceHelper.getString(R.string.vault_cryptomator),
				ResourceHelper.getString(R.string.masterkey_cryptomator)
			)
		)
		staticHandler(
			VaultKeyInvalidException::class.java, String.format(
				ResourceHelper.getString(R.string.error_vault_key_invalid),
				ResourceHelper.getString(R.string.vault_cryptomator),
				ResourceHelper.getString(R.string.masterkey_cryptomator)
			)
		)
		staticHandler(VaultConfigLoadException::class.java, R.string.error_vault_config_loading)
		staticHandler(UnsupportedMasterkeyLocationException::class.java, R.string.error_masterkey_location_not_supported)
		staticHandler(NoSuchBucketException::class.java, R.string.error_no_such_bucket)
		exceptionHandlers.add(MissingCryptorExceptionHandler())
		exceptionHandlers.add(CancellationExceptionHandler())
		exceptionHandlers.add(NoSuchVaultExceptionHandler())
		exceptionHandlers.add(PermissionNotGrantedExceptionHandler())
	}

	fun handle(view: View, e: Throwable) {
		Timber.tag("ExceptionHandler").d(e, "Unexpected error")
		for (mapping in this) {
			if (mapping.handle(view, e)) {
				return
			}
		}
		defaultExceptionHandler.handle(view, e)
	}

	private fun <T : Throwable> staticHandler(type: Class<T>, messageId: Int) {
		staticHandler(type, context.getString(messageId))
	}

	private fun <T : Throwable> staticHandler(type: Class<T>, message: String) {
		exceptionHandlers.add(object : MessageExceptionHandler<T>(type) {
			override fun toMessage(e: T?): String {
				return if (e?.message?.isNotEmpty() == true) {
					String.format(message, e.message)
				} else message
			}
		})
	}

	override fun iterator(): MutableIterator<ExceptionHandler> {
		return Collections.unmodifiableCollection(exceptionHandlers).iterator()
	}

	init {
		this.defaultExceptionHandler = defaultExceptionHandler
		setupHandlers()
	}
}
