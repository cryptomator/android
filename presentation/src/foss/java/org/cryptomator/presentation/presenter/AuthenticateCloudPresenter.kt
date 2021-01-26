package org.cryptomator.presentation.presenter

import android.Manifest
import android.accounts.AccountManager
import com.dropbox.core.android.Auth
import org.cryptomator.data.cloud.onedrive.OnedriveClientFactory
import org.cryptomator.data.cloud.onedrive.graph.ClientException
import org.cryptomator.data.cloud.onedrive.graph.ICallback
import org.cryptomator.data.util.X509CertificateHelper
import org.cryptomator.domain.*
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.*
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.exception.PermissionNotGrantedException
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import org.cryptomator.presentation.model.*
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.AuthenticateCloudView
import org.cryptomator.presentation.workflow.*
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import timber.log.Timber
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject

@PerView
class AuthenticateCloudPresenter @Inject constructor( //
		exceptionHandlers: ExceptionHandlers,  //
		private val cloudModelMapper: CloudModelMapper,  //
		private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
		private val getUsernameUseCase: GetUsernameUseCase,  //
		private val addExistingVaultWorkflow: AddExistingVaultWorkflow,  //
		private val createNewVaultWorkflow: CreateNewVaultWorkflow) : Presenter<AuthenticateCloudView>(exceptionHandlers) {

	private val strategies = arrayOf( //
			DropboxAuthStrategy(),  //
			OnedriveAuthStrategy(),  //
			WebDAVAuthStrategy(),  //
			LocalStorageAuthStrategy() //
	)

	override fun workflows(): Iterable<Workflow<*>> {
		return listOf(createNewVaultWorkflow, addExistingVaultWorkflow)
	}

	override fun resumed() {
		val cloud = view?.intent()?.cloud()
		val error = view?.intent()?.error()
		handleNetworkConnectionExceptionIfRequired(error)
		view?.intent()?.let { cloud?.let { cloud -> authStrategyFor(cloud).resumed(it) } }
	}

	private fun handleNetworkConnectionExceptionIfRequired(error: AuthenticationException?) {
		if (error != null && ExceptionUtil.contains(error, NetworkConnectionException::class.java)) {
			view?.showMessage(R.string.error_no_network_connection)
			finish()
		}
	}

	private fun authStrategyFor(cloud: CloudModel): AuthStrategy {
		strategies.forEach { strategy ->
			if (strategy.supports(cloud)) {
				return strategy
			}
		}
		return FailingAuthStrategy()
	}

	private fun getUsernameAndSuceedAuthentication(cloud: Cloud) {
		getUsernameUseCase.withCloud(cloud).run(object : DefaultResultHandler<String>() {
			override fun onSuccess(username: String) {
				succeedAuthenticationWith(updateUsernameOf(cloud, username))
			}

			override fun onError(e: Throwable) {
				super.onError(e)
				finish()
			}
		})
	}

	private fun updateUsernameOf(cloud: Cloud, username: String): Cloud {
		when (cloud.type()) {
			CloudType.DROPBOX -> return DropboxCloud.aCopyOf(cloud as DropboxCloud).withUsername(username).build()
			CloudType.ONEDRIVE -> return OnedriveCloud.aCopyOf(cloud as OnedriveCloud).withUsername(username).build()
		}
		throw IllegalStateException("Cloud " + cloud.type() + " is not supported")
	}

	private fun succeedAuthenticationWith(cloud: Cloud) {
		addOrChangeCloudConnectionUseCase //
				.withCloud(cloud) //
				.run(object : DefaultResultHandler<Void?>() {
					override fun onSuccess(void: Void?) {
						finishWithResult(cloudModelMapper.toModel(cloud))
					}

					override fun onError(e: Throwable) {
						super.onError(e)
						finish()
					}
				})
	}

	private fun failAuthentication(cloudName: Int) {
		view?.showMessage(String.format(getString(R.string.screen_authenticate_auth_authentication_failed), getString(cloudName)))
		finish()
	}

	private fun failAuthentication(error: PermissionNotGrantedException) {
		finishWithResult(error)
	}

	private inner class DropboxAuthStrategy : AuthStrategy {
		private var authenticationStarted = false
		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.DROPBOX
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			if (authenticationStarted) {
				handleAuthenticationResult(intent.cloud())
			} else {
				startAuthentication()
			}
		}

		private fun startAuthentication() {
			showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
			authenticationStarted = true
			Auth.startOAuth2Authentication(context(), BuildConfig.DROPBOX_API_KEY)
			view?.skipTransition()
		}

		private fun handleAuthenticationResult(cloudModel: CloudModel) {
			val authToken = Auth.getOAuth2Token()
			if (authToken == null) {
				failAuthentication(cloudModel.name())
			} else {
				getUsernameAndSuceedAuthentication( //
						DropboxCloud.aCopyOf(cloudModel.toCloud() as DropboxCloud) //
								.withAccessToken(encrypt(authToken)) //
								.build())
			}
		}
	}

	@Callback(dispatchResultOkOnly = false)
	fun onUserRecoveryFinished(result: ActivityResult, cloud: CloudModel) {
		if (result.isResultOk) {
			succeedAuthenticationWith(cloud.toCloud())
		} else {
			failAuthentication(cloud.name())
		}
	}

	@Callback(dispatchResultOkOnly = false)
	fun onGoogleDriveAuthenticated(result: ActivityResult, cloud: CloudModel) {
		if (result.isResultOk) {
			val accountName = result.intent()?.extras?.getString(AccountManager.KEY_ACCOUNT_NAME)
			succeedAuthenticationWith(GoogleDriveCloud.aCopyOf(cloud.toCloud() as GoogleDriveCloud) //
					.withUsername(accountName) //
					.withAccessToken(accountName) //
					.build())
		} else {
			failAuthentication(cloud.name())
		}
	}

	private inner class OnedriveAuthStrategy : AuthStrategy {
		private var authenticationStarted = false
		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.ONEDRIVE
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			if (!authenticationStarted) {
				startAuthentication(intent.cloud())
			}
		}

		private fun startAuthentication(cloud: CloudModel) {
			authenticationStarted = true
			val authenticationAdapter = OnedriveClientFactory.instance(context(), (cloud.toCloud() as OnedriveCloud).accessToken()).authenticationAdapter
			authenticationAdapter.login(activity(), object : ICallback<String?> {
				override fun success(accessToken: String?) {
					if (accessToken == null) {
						Timber.tag("AuthicateCloudPrester").e("Onedrive access token is empty")
						failAuthentication(cloud.name())
					} else {
						showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
						handleAuthenticationResult(cloud, accessToken)
					}
				}

				override fun failure(ex: ClientException) {
					Timber.tag("AuthicateCloudPrester").e(ex)
					failAuthentication(cloud.name())
				}
			})
		}

		private fun handleAuthenticationResult(cloud: CloudModel, accessToken: String) {
			getUsernameAndSuceedAuthentication( //
					OnedriveCloud.aCopyOf(cloud.toCloud() as OnedriveCloud) //
							.withAccessToken(accessToken) //
							.build())
		}
	}

	private inner class WebDAVAuthStrategy : AuthStrategy {
		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.WEBDAV
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			handleWebDavAuthenticationExceptionIfRequired(intent.cloud() as WebDavCloudModel, intent.error())
		}

		private fun handleWebDavAuthenticationExceptionIfRequired(cloud: WebDavCloudModel, e: AuthenticationException) {
			Timber.tag("AuthicateCloudPrester").e(e)
			when {
				ExceptionUtil.contains(e, WrongCredentialsException::class.java) -> {
					failAuthentication(cloud.name())
				}
				ExceptionUtil.contains(e, WebDavCertificateUntrustedAuthenticationException::class.java) -> {
					handleCertificateUntrustedExceptionIfRequired(cloud, e)
				}
				ExceptionUtil.contains(e, WebDavServerNotFoundException::class.java) -> {
					view?.showMessage(R.string.error_server_not_found)
					finish()
				}
				ExceptionUtil.contains(e, WebDavNotSupportedException::class.java) -> {
					view?.showMessage(R.string.screen_cloud_error_webdav_not_supported)
					finish()
				}
			}
		}

		private fun handleCertificateUntrustedExceptionIfRequired(cloud: WebDavCloudModel, e: AuthenticationException) {
			val untrustedException = ExceptionUtil.extract(e, WebDavCertificateUntrustedAuthenticationException::class.java)
			try {
				val certificate = X509CertificateHelper.convertFromPem(untrustedException.get().certificate)
				view?.showUntrustedCertificateDialog(cloud.toCloud() as WebDavCloud, certificate)
			} catch (ex: CertificateException) {
				Timber.tag("AuthicateCloudPrester").e(ex)
				throw FatalBackendException(ex)
			}
		}
	}

	fun onAcceptWebDavCertificateClicked(cloud: WebDavCloud?, certificate: X509Certificate?) {
		try {
			val webDavCloudWithAcceptedCert = WebDavCloud.aCopyOf(cloud) //
					.withCertificate(X509CertificateHelper.convertToPem(certificate)) //
					.build()
			finishWithResultAndExtra(cloudModelMapper.toModel(webDavCloudWithAcceptedCert),  //
					WEBDAV_ACCEPTED_UNTRUSTED_CERTIFICATE,  //
					true)
		} catch (e: CertificateEncodingException) {
			Timber.tag("AuthicateCloudPrester").e(e)
			throw FatalBackendException(e)
		}
	}

	fun onAcceptWebDavCertificateDenied() {
		finish()
	}

	private inner class LocalStorageAuthStrategy : AuthStrategy {
		private var authenticationStarted = false
		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.LOCAL
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			if (!authenticationStarted) {
				startAuthentication(intent.cloud())
			}
		}

		private fun startAuthentication(cloud: CloudModel) {
			authenticationStarted = true
			requestPermissions(PermissionsResultCallbacks.onLocalStorageAuthenticated(cloud),  //
					R.string.permission_snackbar_auth_local_vault,  //
					Manifest.permission.READ_EXTERNAL_STORAGE,  //
					Manifest.permission.WRITE_EXTERNAL_STORAGE)
		}
	}

	@Callback
	fun onLocalStorageAuthenticated(result: PermissionsResult, cloud: CloudModel) {
		if (result.granted()) {
			succeedAuthenticationWith(cloud.toCloud())
		} else {
			failAuthentication(PermissionNotGrantedException(R.string.permission_snackbar_auth_local_vault))
		}
	}

	private fun encrypt(password: String): String {
		return CredentialCryptor //
				.getInstance(context()) //
				.encrypt(password)
	}

	private inner class FailingAuthStrategy : AuthStrategy {
		override fun supports(cloud: CloudModel): Boolean {
			return false
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			view?.showError(R.string.error_authentication_failed)
			finish()
		}
	}

	private interface AuthStrategy {
		fun supports(cloud: CloudModel): Boolean
		fun resumed(intent: AuthenticateCloudIntent)
	}

	companion object {
		const val WEBDAV_ACCEPTED_UNTRUSTED_CERTIFICATE = "acceptedUntrustedCertificate"
	}

	init {
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, getUsernameUseCase)
	}
}
