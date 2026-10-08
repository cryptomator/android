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
import com.microsoft.identity.common.java.exception.ClientException
import org.cryptomator.domain.MicrosoftGraphCloud
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.presentation.R
import org.cryptomator.util.crypto.CredentialCryptor
import timber.log.Timber

object MicrosoftGraphAuthentication {

	fun refreshOrCheckAuth(activity: Activity, cloud: MicrosoftGraphCloud, success: (cloud: MicrosoftGraphCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		val scopes = scopesFor(cloud)
		val onTokenObtained = { authenticationResult: IAuthenticationResult -> success(withAuthenticationResult(activity.applicationContext, cloud, authenticationResult)) }
		PublicClientApplication.createMultipleAccountPublicClientApplication(
			activity.applicationContext,
			R.raw.auth_config_onedrive,
			object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
				override fun onCreated(application: IMultipleAccountPublicClientApplication) {
					application.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
						override fun onTaskCompleted(accounts: List<IAccount>) {
							if (accounts.isEmpty()) {
								application.acquireToken(activity, scopes, getAuthInteractiveCallback(onTokenObtained, failed))
							} else {
								accounts.find { account -> account.username == cloud.username() }?.let {
									application.acquireTokenSilentAsync(
										scopes,
										it,
										"https://login.microsoftonline.com/common",
										getAuthSilentCallback(activity, scopes, onTokenObtained, failed, application)
									)
								} ?: application.acquireToken(activity, scopes, getAuthInteractiveCallback(onTokenObtained, failed))
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

	private fun scopesFor(cloud: MicrosoftGraphCloud): Array<String> {
		return when (cloud) {
			is SharepointCloud -> AuthenticateCloudPresenter.sharepointScopes()
			else -> AuthenticateCloudPresenter.onedriveScopes()
		}
	}

	private fun withAuthenticationResult(context: Context, cloud: MicrosoftGraphCloud, authenticationResult: IAuthenticationResult): MicrosoftGraphCloud {
		Timber.tag("AuthenticateCloudPresenter").i("Successfully authenticated")
		val accessToken = CredentialCryptor.getInstance(context).encrypt(authenticationResult.accessToken)
		val username = authenticationResult.account.username
		return when (cloud) {
			is SharepointCloud -> SharepointCloud.aCopyOf(cloud).withAccessToken(accessToken).withUsername(username).build()
			else -> OnedriveCloud.aCopyOf(cloud as OnedriveCloud).withAccessToken(accessToken).withUsername(username).build()
		}
	}

	private fun getAuthSilentCallback(
		activity: Activity,
		scopes: Array<String>,
		onTokenObtained: (authenticationResult: IAuthenticationResult) -> Unit,
		failed: (e: FatalBackendException) -> Unit,
		application: IMultipleAccountPublicClientApplication
	): AuthenticationCallback {
		return object : AuthenticationCallback {

			override fun onSuccess(authenticationResult: IAuthenticationResult) {
				onTokenObtained(authenticationResult)
			}

			override fun onError(e: MsalException) {
				Timber.tag("AuthenticateCloudPresenter").e(e, "Failed to acquireToken")
				when (e) {
					is MsalUiRequiredException -> {
						/* Tokens expired or no session, retry with interactive */
						application.acquireToken(activity, scopes, getAuthInteractiveCallback(onTokenObtained, failed))
					}
					else -> failed(mapToNetworkExceptionIfRequired(e))
				}
			}

			override fun onCancel() {
				Timber.tag("AuthenticateCloudPresenter").i("User cancelled login")
			}
		}
	}

	fun getAuthenticatedOnedriveCloud(activity: Activity, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		acquireTokenInteractively(activity, OnedriveCloud.aOnedriveCloud().build(), { cloud -> success(cloud as OnedriveCloud) }, failed)
	}

	fun getAuthenticatedSharepointCloud(activity: Activity, siteUrl: String, success: (cloud: SharepointCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		acquireTokenInteractively(activity, SharepointCloud.aSharepointCloud().withSiteUrl(siteUrl).build(), { cloud -> success(cloud as SharepointCloud) }, failed)
	}

	private fun acquireTokenInteractively(activity: Activity, skeleton: MicrosoftGraphCloud, success: (cloud: MicrosoftGraphCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		val onTokenObtained = { authenticationResult: IAuthenticationResult -> success(withAuthenticationResult(activity.applicationContext, skeleton, authenticationResult)) }
		PublicClientApplication.createMultipleAccountPublicClientApplication(
			activity.applicationContext,
			R.raw.auth_config_onedrive,
			object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
				override fun onCreated(application: IMultipleAccountPublicClientApplication) {
					application.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
						override fun onTaskCompleted(accounts: List<IAccount>) {
							application.acquireToken(activity, scopesFor(skeleton), getAuthInteractiveCallback(onTokenObtained, failed))
						}

						override fun onError(e: MsalException) {
							Timber.tag("AuthenticateCloudPresenter").e(e, "Error to get accounts")
							failed(mapToNetworkExceptionIfRequired(e))
						}
					})
				}

				override fun onError(e: MsalException) {
					Timber.tag("AuthenticateCloudPresenter").i(e, "Error in configuration")
					failed(mapToNetworkExceptionIfRequired(e))
				}
			})
	}

	private fun getAuthInteractiveCallback(onTokenObtained: (authenticationResult: IAuthenticationResult) -> Unit, failed: (e: FatalBackendException) -> Unit): AuthenticationCallback {
		return object : AuthenticationCallback {

			override fun onSuccess(authenticationResult: IAuthenticationResult) {
				onTokenObtained(authenticationResult)
			}

			override fun onError(e: MsalException) {
				Timber.tag("AuthenticateCloudPresenter").e(e, "Authentication failed")
				failed(mapToNetworkExceptionIfRequired(e))
			}

			override fun onCancel() {
				Timber.tag("AuthenticateCloudPresenter").i("User cancelled login")
			}
		}
	}

	private fun mapToNetworkExceptionIfRequired(e: MsalException): FatalBackendException {
		return if (e.errorCode == ClientException.DEVICE_NETWORK_NOT_AVAILABLE) {
			FatalBackendException(NetworkConnectionException(e))
		} else {
			FatalBackendException(e)
		}
	}
}
