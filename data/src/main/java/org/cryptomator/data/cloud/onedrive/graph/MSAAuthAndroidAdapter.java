package org.cryptomator.data.cloud.onedrive.graph;

import android.app.Activity;
import android.content.Context;

import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.options.HeaderOption;
import com.microsoft.services.msa.LiveAuthClient;
import com.microsoft.services.msa.LiveAuthException;
import com.microsoft.services.msa.LiveAuthListener;
import com.microsoft.services.msa.LiveConnectSession;
import com.microsoft.services.msa.LiveStatus;

import org.cryptomator.util.crypto.CredentialCryptor;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import timber.log.Timber;

import static com.microsoft.graph.core.GraphErrorCodes.AUTHENTICATION_FAILURE;

/**
 * Supports login, logout, and signing requests with authorization information.
 */
public abstract class MSAAuthAndroidAdapter implements IAuthenticationAdapter {

	/**
	 * The authorization header name.
	 */
	private static final String AUTHORIZATION_HEADER_NAME = "Authorization";

	/**
	 * The bearer prefix.
	 */
	private static final String OAUTH_BEARER_PREFIX = "bearer ";

	/**
	 * The live auth client.
	 */
	private final LiveAuthClient mLiveAuthClient;
	private Context context;

	/**
	 * Create a new instance of the provider
	 *
	 * @param context      the application context instance
	 * @param refreshToken
	 */
	protected MSAAuthAndroidAdapter(final Context context, String refreshToken) {
		this.context = context;
		mLiveAuthClient = new LiveAuthClient(context, getClientId(), Arrays.asList(getScopes()), MicrosoftOAuth2Endpoint.getInstance(), refreshToken);
	}

	/**
	 * The client id for this authenticator.
	 * http://graph.microsoft.io/en-us/app-registration
	 *
	 * @return The client id.
	 */
	protected abstract String getClientId();

	/**
	 * The scopes for this application.
	 * http://graph.microsoft.io/en-us/docs/authorization/permission_scopes
	 *
	 * @return The scopes for this application.
	 */
	protected abstract String[] getScopes();

	@Override
	public void authenticateRequest(final IHttpRequest request) {
		Timber.tag("MSAAuthAndroidAdapter").d("Authenticating request, %s", request.getRequestUrl());

		// If the request already has an authorization header, do not intercept it.
		for (final HeaderOption option : request.getHeaders()) {
			if (option.getName().equals(AUTHORIZATION_HEADER_NAME)) {
				Timber.tag("MSAAuthAndroidAdapter").d("Found an existing authorization header!");
				return;
			}
		}

		try {
			final String accessToken = getAccessToken();
			request.addHeader(AUTHORIZATION_HEADER_NAME, OAUTH_BEARER_PREFIX + accessToken);
		} catch (ClientException e) {
			final String message = "Unable to authenticate request, No active account found";
			final ClientException exception = new ClientException(message, e, AUTHENTICATION_FAILURE);
			Timber.tag("MSAAuthAndroidAdapter").e(exception, message);
			throw exception;
		}
	}

	@Override
	public String getAccessToken() throws ClientException {
		if (hasValidSession()) {
			Timber.tag("MSAAuthAndroidAdapter").d("Found account information");
			if (mLiveAuthClient.getSession().isExpired()) {
				Timber.tag("MSAAuthAndroidAdapter").d("Account access token is expired, refreshing");
				loginSilentBlocking();
			}
			return mLiveAuthClient.getSession().getAccessToken();
		} else {
			final String message = "Unable to get access token, No active account found";
			final ClientException exception = new ClientException(message, null, AUTHENTICATION_FAILURE);
			Timber.tag("MSAAuthAndroidAdapter").e(exception, message);
			throw exception;
		}
	}

	@Override
	public void logout(final ICallback<Void> callback) {
		Timber.tag("MSAAuthAndroidAdapter").d("Logout started");

		if (callback == null) {
			throw new IllegalArgumentException("callback");
		}

		mLiveAuthClient.logout(new LiveAuthListener() {
			@Override
			public void onAuthComplete(final LiveStatus status, final LiveConnectSession session, final Object userState) {
				Timber.tag("MSAAuthAndroidAdapter").d("Logout complete");
				callback.success(null);
			}

			@Override
			public void onAuthError(final LiveAuthException exception, final Object userState) {
				final ClientException clientException = new ClientException("Logout failure", exception, AUTHENTICATION_FAILURE);
				Timber.tag("MSAAuthAndroidAdapter").e(clientException);
				callback.failure(clientException);
			}
		});
	}

	@Override
	public void login(final Activity activity, final ICallback<String> callback) {
		Timber.tag("MSAAuthAndroidAdapter").d("Login started");

		if (callback == null) {
			throw new IllegalArgumentException("callback");
		}

		if (hasValidSession()) {
			Timber.tag("MSAAuthAndroidAdapter").d("Already logged in");
			callback.success(null);
			return;
		}

		final LiveAuthListener listener = new LiveAuthListener() {
			@Override
			public void onAuthComplete(final LiveStatus status, final LiveConnectSession session, final Object userState) {
				Timber.tag("MSAAuthAndroidAdapter").d(String.format("LiveStatus: %s, LiveConnectSession good?: %s, UserState %s", status, session != null, userState));

				if (status == LiveStatus.NOT_CONNECTED && session.getRefreshToken() == null) {
					Timber.tag("MSAAuthAndroidAdapter").d("Received invalid login failure from silent authentication, ignoring.");
					return;
				}

				if (status == LiveStatus.CONNECTED) {
					Timber.tag("MSAAuthAndroidAdapter").d("Login completed");
					callback.success(encrypt(session.getRefreshToken()));
					return;
				}

				final ClientException clientException = new ClientException("Unable to login successfully", null, AUTHENTICATION_FAILURE);
				Timber.tag("MSAAuthAndroidAdapter").e(clientException);
				callback.failure(clientException);
			}

			@Override
			public void onAuthError(final LiveAuthException exception, final Object userState) {
				final ClientException clientException = new ClientException("Login failure", exception, AUTHENTICATION_FAILURE);
				Timber.tag("MSAAuthAndroidAdapter").e(clientException);
				callback.failure(clientException);
			}
		};

		// Make sure the login process is started with the current activity information
		activity.runOnUiThread(() -> mLiveAuthClient.login(activity, listener));
	}

	private String encrypt(String refreshToken) {
		if (refreshToken == null) {
			return null;
		}
		return CredentialCryptor //
				.getInstance(context) //
				.encrypt(refreshToken);
	}

	/**
	 * Login a user with no ui
	 *
	 * @param callback The callback when the login is complete or an error occurs
	 */
	@Override
	public void loginSilent(final ICallback<Void> callback) {
		Timber.tag("MSAAuthAndroidAdapter").d("Login silent started");

		if (callback == null) {
			throw new IllegalArgumentException("callback");
		}

		final LiveAuthListener listener = new LiveAuthListener() {
			@Override
			public void onAuthComplete(final LiveStatus status, final LiveConnectSession session, final Object userState) {
				Timber.tag("MSAAuthAndroidAdapter").d(String.format("LiveStatus: %s, LiveConnectSession good?: %s, UserState %s", status, session != null, userState));

				if (status == LiveStatus.CONNECTED) {
					Timber.tag("MSAAuthAndroidAdapter").d("Login completed");
					callback.success(null);
					return;
				}

				final ClientException clientException = new ClientException("Unable to login silently", null, AUTHENTICATION_FAILURE);
				Timber.tag("MSAAuthAndroidAdapter").e(clientException);
				callback.failure(clientException);
			}

			@Override
			public void onAuthError(final LiveAuthException exception, final Object userState) {
				final ClientException clientException = new ClientException("Unable to login silently", null, AUTHENTICATION_FAILURE);
				Timber.tag("MSAAuthAndroidAdapter").e(clientException);
				callback.failure(clientException);
			}
		};

		mLiveAuthClient.loginSilent(listener);
	}

	/**
	 * Login silently while blocking for the call to return
	 *
	 * @return the result of the login attempt
	 * @throws ClientException The exception if there was an issue during the login attempt
	 */
	private Void loginSilentBlocking() throws ClientException {
		Timber.tag("MSAAuthAndroidAdapter").d("Login silent blocking started");
		final SimpleWaiter waiter = new SimpleWaiter();
		final AtomicReference<Void> returnValue = new AtomicReference<>();
		final AtomicReference<ClientException> exceptionValue = new AtomicReference<>();

		loginSilent(new ICallback<Void>() {
			@Override
			public void success(final Void aVoid) {
				returnValue.set(aVoid);
				waiter.signal();
			}

			@Override
			public void failure(ClientException ex) {
				exceptionValue.set(ex);
				waiter.signal();
			}
		});

		waiter.waitForSignal();

		// noinspection ThrowableResultOfMethodCallIgnored
		if (exceptionValue.get() != null) {
			throw exceptionValue.get();
		}

		return returnValue.get();
	}

	/**
	 * Is the session object valid
	 *
	 * @return true, if the session is valid (but not necessary unexpired)
	 */
	private boolean hasValidSession() {
		return mLiveAuthClient.getSession() != null && mLiveAuthClient.getSession().getAccessToken() != null;
	}
}
