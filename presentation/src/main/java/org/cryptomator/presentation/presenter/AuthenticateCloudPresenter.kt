package org.cryptomator.presentation.presenter

import android.accounts.AccountManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.ACTION_OPEN_DOCUMENT_TREE
import android.provider.DocumentsContract
import android.widget.Toast
import org.cryptomator.data.util.X509CertificateHelper
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.DropboxCloud
import org.cryptomator.domain.GoogleDriveCloud
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.PCloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.exception.authentication.WebDavCertificateUntrustedAuthenticationException
import org.cryptomator.domain.exception.authentication.WebDavNotSupportedException
import org.cryptomator.domain.exception.authentication.WebDavServerNotFoundException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.AuthenticateCloudView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.Workflow
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import timber.log.Timber


@PerView
class AuthenticateCloudPresenter @Inject constructor( //
	exceptionHandlers: ExceptionHandlers,  //
	private val cloudModelMapper: CloudModelMapper,  //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val getCloudsUseCase: GetCloudsUseCase, //
	private val getUsernameUseCase: GetUsernameUseCase,  //
	private val addExistingVaultWorkflow: AddExistingVaultWorkflow,  //
	private val createNewVaultWorkflow: CreateNewVaultWorkflow
) : Presenter<AuthenticateCloudView>(exceptionHandlers) {

	private val strategies = arrayOf( //
		DropboxAuthStrategy(),  //
		GoogleDriveAuthStrategy(),  //
		OnedriveAuthStrategy(),  //
		PCloudAuthStrategy(), //
		WebDAVAuthStrategy(),  //
		S3AuthStrategy(), //
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
		return when (cloud.type) {
			CloudType.DROPBOX -> DropboxCloud.aCopyOf(cloud as DropboxCloud).withUsername(username).build()
			CloudType.ONEDRIVE -> OnedriveCloud.aCopyOf(cloud as OnedriveCloud).withUsername(username).build()
			else -> throw IllegalStateException("Cloud " + cloud.type + " is not supported")
		}
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
		activity().runOnUiThread {
			view?.showMessage(String.format(getString(R.string.screen_authenticate_auth_authentication_failed), getString(cloudName)))
		}
		finish()
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
			DropboxAuthHelper.startOAuth2Authentication(context())
			view?.skipTransition()
		}

		private fun handleAuthenticationResult(cloudModel: CloudModel) {
			val authToken = DropboxAuthHelper.getOAuth2Token()
			if (authToken == null) {
				failAuthentication(cloudModel.name())
			} else {
				getUsernameAndSuceedAuthentication( //
					DropboxCloud.aCopyOf(cloudModel.toCloud() as DropboxCloud) //
						.withAccessToken(encrypt(authToken)) //
						.build()
				)
			}
		}
	}

	private inner class GoogleDriveAuthStrategy : AuthStrategy {

		private var authenticationStarted = false
		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.GOOGLE_DRIVE
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			if (!authenticationStarted) {
				startAuthentication(intent)
			}
		}

		private fun startAuthentication(intent: AuthenticateCloudIntent) {
			showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
			authenticationStarted = true
			if (intent.recoveryAction() != null) {
				handleUserRecoverableAuthenticationException(intent)
			} else {
				chooseAccount(intent.cloud())
			}
		}

		private fun handleUserRecoverableAuthenticationException(intent: AuthenticateCloudIntent) {
			requestActivityResult(ActivityResultCallbacks.onUserRecoveryFinished(intent.cloud()), intent.recoveryAction())
		}

		private fun chooseAccount(cloud: CloudModel) {
			try {
				requestActivityResult( //
					ActivityResultCallbacks.onGoogleDriveAuthenticated(cloud),  //
					GoogleAuthHelper.getChooseAccountIntent(context())
				)
			} catch (e: ActivityNotFoundException) {
				view?.showMessage(R.string.error_play_services_not_available)
				finish()
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
			succeedAuthenticationWith(
				GoogleDriveCloud.aCopyOf(cloud.toCloud() as GoogleDriveCloud) //
					.withUsername(accountName) //
					.withAccessToken(accountName) //
					.build()
			)
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

			Toast.makeText(context(), R.string.notification_authenticating, Toast.LENGTH_SHORT).show()

			OnedriveAuthentication.refreshOrCheckAuth(activity(), cloud.toCloud() as OnedriveCloud, { authenticatedCloud ->
				getUsernameAndSuceedAuthentication(authenticatedCloud)
			}, {
				failAuthentication(cloud.name())
			})
		}
	}

	private inner class PCloudAuthStrategy : AuthStrategy {

		private var authenticationStarted = false

		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.PCLOUD
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			if (authenticationStarted) {
				finish()
			} else {
				startAuthentication(intent.cloud())
				Toast.makeText(
					context(),
					String.format(getString(R.string.error_authentication_failed_re_authenticate), intent.cloud().username()),
					Toast.LENGTH_LONG
				).show()
			}
		}

		private fun startAuthentication(cloud: CloudModel) {
			authenticationStarted = true
			showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
			view?.skipTransition()
			requestActivityResult(
				ActivityResultCallbacks.pCloudReAuthenticationFinished(cloud),  //
				Intents.cloudConnectionListIntent() //
					.withCloudType(CloudTypeModel.PCLOUD) //
					.withDialogTitle(context().getString(R.string.screen_update_pcloud_connections_title)) //
					.withFinishOnCloudItemClick(false) //
			)
		}
	}

	@Callback
	fun pCloudReAuthenticationFinished(activityResult: ActivityResult, cloud: CloudModel) {
		val code = activityResult.intent().extras?.getString(CloudConnectionListPresenter.PCLOUD_OAUTH_AUTH_CODE, "")
		val hostname = activityResult.intent().extras?.getString(CloudConnectionListPresenter.PCLOUD_HOSTNAME, "")

		if (!code.isNullOrEmpty() && !hostname.isNullOrEmpty()) {
			Timber.tag("CloudConnectionListPresenter").i("PCloud OAuth code successfully retrieved")

			val accessToken = CredentialCryptor //
				.getInstance(this.context()) //
				.encrypt(code)
			val pCloudSkeleton = PCloud.aPCloud() //
				.withAccessToken(accessToken)
				.withUrl(hostname)
				.build();
			getUsernameUseCase //
				.withCloud(pCloudSkeleton) //
				.run(object : DefaultResultHandler<String>() {
					override fun onSuccess(username: String) {
						Timber.tag("CloudConnectionListPresenter").i("PCloud Authentication successfully")
						prepareForSavingPCloud(PCloud.aCopyOf(pCloudSkeleton).withUsername(username).build())
					}
				})
		} else {
			Timber.tag("CloudConnectionListPresenter").i("PCloud Authentication not successful")
			failAuthentication(cloud.name())
		}
	}

	fun prepareForSavingPCloud(cloud: PCloud) {
		getCloudsUseCase //
			.withCloudType(cloud.type) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					clouds.firstOrNull {
						(it as PCloud).username() == cloud.username()
					}?.let {
						it as PCloud
						succeedAuthenticationWith(
							PCloud.aCopyOf(it) //
								.withUrl(cloud.url())
								.withAccessToken(cloud.accessToken())
								.build()
						)
					} ?: succeedAuthenticationWith(cloud)
				}
			})
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

	fun onAcceptWebDavCertificateClicked(cloud: WebDavCloud, certificate: X509Certificate) {
		try {
			val webDavCloudWithAcceptedCert = WebDavCloud.aCopyOf(cloud) //
				.withCertificate(X509CertificateHelper.convertToPem(certificate)) //
				.build()
			finishWithResultAndExtra(
				cloudModelMapper.toModel(webDavCloudWithAcceptedCert),  //
				WEBDAV_ACCEPTED_UNTRUSTED_CERTIFICATE,  //
				true
			)
		} catch (e: CertificateEncodingException) {
			Timber.tag("AuthicateCloudPrester").e(e)
			throw FatalBackendException(e)
		}
	}

	fun onAcceptWebDavCertificateDenied() {
		finish()
	}

	private inner class S3AuthStrategy : AuthStrategy {

		private var authenticationStarted = false

		override fun supports(cloud: CloudModel): Boolean {
			return cloud.cloudType() == CloudTypeModel.S3
		}

		override fun resumed(intent: AuthenticateCloudIntent) {
			when {
				ExceptionUtil.contains(intent.error(), WrongCredentialsException::class.java) -> {
					if (!authenticationStarted) {
						startAuthentication(intent.cloud())
						Toast.makeText(
							context(),
							String.format(getString(R.string.error_authentication_failed), intent.cloud().username()),
							Toast.LENGTH_LONG
						).show()
					}
				}
				else -> {
					Timber.tag("AuthicateCloudPrester").e(intent.error())
					failAuthentication(intent.cloud().name())
				}
			}
		}

		private fun startAuthentication(cloud: CloudModel) {
			authenticationStarted = true
			startIntent(Intents.s3AddOrChangeIntent().withS3Cloud(cloud as S3CloudModel))
		}
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

			val uri = (cloud as LocalStorageModel).uri()

			val permissions = context().contentResolver.persistedUriPermissions
			for (permission in permissions) {
				if (permission.uri.toString() == uri) {
					succeedAuthenticationWith(cloud.toCloud())
				}
			}

			Timber.tag("AuthicateCloudPrester").e("Permission revoked, ask to re-pick location")

			Toast.makeText(context(), getString(R.string.permission_revoked_re_request_permission), Toast.LENGTH_LONG).show()

			val openDocumentTree = Intent(ACTION_OPEN_DOCUMENT_TREE).apply {
				putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
			}

			requestActivityResult(ActivityResultCallbacks.rePickedLocalStorageLocation(cloud), openDocumentTree)
		}
	}

	@Callback
	fun rePickedLocalStorageLocation(result: ActivityResult, cloud: LocalStorageModel) {
		val rootTreeUriOfLocalStorage = result.intent().data
		rootTreeUriOfLocalStorage?.let {
			context() //
				.contentResolver //
				.takePersistableUriPermission( //
					it,  //
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				)
		}
		Timber.tag("AuthicateCloudPrester").e("Permission granted again")
		succeedAuthenticationWith(cloud.toCloud())
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

		fun onedriveScopes(): Array<String> {
			return arrayOf("User.Read", "Files.ReadWrite")
		}
	}

	init {
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, getCloudsUseCase, getUsernameUseCase)
	}
}
