package org.cryptomator.presentation.presenter

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.R
import org.cryptomator.util.crypto.CredentialCryptor
import timber.log.Timber

object OnedriveAuthentication {

	fun refreshOrCheckAuth(activity: Activity, cloud: OnedriveCloud, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		PublicClientApplication.createMultipleAccountPublicClientApplication(
			activity.applicationContext,
			R.raw.auth_config_onedrive,
			object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
				override fun onCreated(application: IMultipleAccountPublicClientApplication) {
					application.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
						override fun onTaskCompleted(accounts: List<IAccount>) {
							if (accounts.isEmpty()) {
								application.acquireToken(activity, AuthenticateCloudPresenter.onedriveScopes(), getAuthInteractiveCallback(activity.applicationContext, cloud, success, failed))
							} else {
								accounts.find { account -> account.username == cloud.username() }?.let {
									application.acquireTokenSilentAsync(
										AuthenticateCloudPresenter.onedriveScopes(),
										it,
										"https://login.microsoftonline.com/common",
										getAuthSilentCallback(activity, cloud, success, failed, application)
									)
								} ?: application.acquireToken(activity, AuthenticateCloudPresenter.onedriveScopes(), getAuthInteractiveCallback(activity.applicationContext, cloud, success, failed))
							}
						}

						override fun onError(e: MsalException) {
							Timber.tag("AuthenticateCloudPresenter").e(e, "Error to get accounts")
							failed(FatalBackendException(e))
						}
					})
				}

				override fun onError(e: MsalException) {
					Timber.tag("AuthenticateCloudPresenter").i(e, "Error in configuration")
					failed(FatalBackendException(e))
				}
			})
	}

	private fun getAuthSilentCallback(
		activity: Activity,
		cloud: OnedriveCloud,
		success: (cloud: OnedriveCloud) -> Unit,
		failed: (e: FatalBackendException) -> Unit,
		application: IMultipleAccountPublicClientApplication
	): AuthenticationCallback {
		return object : AuthenticationCallback {

			override fun onSuccess(authenticationResult: IAuthenticationResult) {
				onTokenObtained(activity.applicationContext, cloud, authenticationResult, success)
			}

			override fun onError(e: MsalException) {
				Timber.tag("AuthenticateCloudPresenter").e(e, "Failed to acquireToken")
				when (e) {
					is MsalUiRequiredException -> {
						/* Tokens expired or no session, retry with interactive */
						application.acquireToken(activity, AuthenticateCloudPresenter.onedriveScopes(), getAuthInteractiveCallback(activity.applicationContext, cloud, success, failed))
					}
					else -> failed(FatalBackendException(e))
				}
			}

			override fun onCancel() {
				Timber.tag("AuthenticateCloudPresenter").i("User cancelled login")
			}
		}
	}

	private fun onTokenObtained(context: Context, cloud: OnedriveCloud?, authenticationResult: IAuthenticationResult, success: (cloud: OnedriveCloud) -> Unit) {
		Timber.tag("AuthenticateCloudPresenter").i("Successfully authenticated")
		val accessToken = CredentialCryptor.getInstance(context).encrypt(authenticationResult.accessToken)
		val cloudBuilder = cloud?.let { OnedriveCloud.aCopyOf(it) } ?: OnedriveCloud.aOnedriveCloud()
		val onedriveSkeleton = cloudBuilder.withAccessToken(accessToken).withUsername(authenticationResult.account.username).build()
		success(onedriveSkeleton)
	}

	fun getAuthenticatedOnedriveCloud(activity: Activity, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		PublicClientApplication.createMultipleAccountPublicClientApplication(
			activity.applicationContext,
			R.raw.auth_config_onedrive,
			object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
				override fun onCreated(application: IMultipleAccountPublicClientApplication) {
					application.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
						override fun onTaskCompleted(accounts: List<IAccount>) {
							application.acquireToken(activity, AuthenticateCloudPresenter.onedriveScopes(), getAuthInteractiveCallback(activity.applicationContext, null, success, failed))
						}

						override fun onError(e: MsalException) {
							Timber.tag("AuthenticateCloudPresenter").e(e, "Error to get accounts")
							failed(FatalBackendException(e))
						}
					})
				}

				override fun onError(e: MsalException) {
					Timber.tag("AuthenticateCloudPresenter").i(e, "Error in configuration")
					failed(FatalBackendException(e))
				}
			})
	}

	private fun getAuthInteractiveCallback(context: Context, cloud: OnedriveCloud?, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit): AuthenticationCallback {
		return object : AuthenticationCallback {

			override fun onSuccess(authenticationResult: IAuthenticationResult) {
				onTokenObtained(context, cloud, authenticationResult, success)
			}

			override fun onError(e: MsalException) {
				Timber.tag("AuthenticateCloudPresenter").e(e, "Successfully authenticated")
				failed(FatalBackendException(e))
			}

			override fun onCancel() {
				Timber.tag("AuthenticateCloudPresenter").i("User cancelled login")
			}
		}
	}
}


