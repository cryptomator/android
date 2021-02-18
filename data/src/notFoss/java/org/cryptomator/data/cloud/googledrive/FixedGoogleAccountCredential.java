package org.cryptomator.data.cloud.googledrive;

import android.accounts.Account;
import android.content.Context;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.util.BackOffUtils;
import com.google.api.client.util.Beta;
import com.google.api.client.util.Joiner;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.Sleeper;

import org.cryptomator.data.util.NetworkTimeout;

import java.io.IOException;
import java.util.Collection;

import static com.google.android.gms.auth.GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE;

class FixedGoogleAccountCredential extends GoogleAccountCredential {

	private String accountName;

	private FixedGoogleAccountCredential(Context context, String scopesStr) {
		super(context, scopesStr);
	}

	public static FixedGoogleAccountCredential usingOAuth2(Context context, Collection<String> scopes) {
		Preconditions.checkArgument(scopes != null && scopes.iterator().hasNext());
		String scopesStr = "oauth2:" + Joiner.on(' ').join(scopes);
		return new FixedGoogleAccountCredential(context, scopesStr);
	}

	@Override
	public void initialize(HttpRequest request) {
		FixedRequestHandler handler = new FixedRequestHandler();
		request.setInterceptor(handler);
		request.setUnsuccessfulResponseHandler(handler);
		request.setConnectTimeout((int) NetworkTimeout.CONNECTION.asMilliseconds());
		request.setReadTimeout((int) NetworkTimeout.READ.asMilliseconds());
	}

	void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	@Override
	public String getToken() throws IOException, GoogleAuthException {
		if (getBackOff() != null) {
			getBackOff().reset();
		}

		while (true) {
			try {
				Account accountDetails = new Account(accountName, GOOGLE_ACCOUNT_TYPE);
				return GoogleAuthUtil.getToken(getContext(), accountDetails, getScope());
			} catch (IOException e) {
				// network or server error, so retry using back-off policy
				try {
					if (getBackOff() == null || !BackOffUtils.next(Sleeper.DEFAULT, getBackOff())) {
						throw e;
					}
				} catch (InterruptedException e2) {
					// ignore
				}
			}
		}
	}

	@Beta
	class FixedRequestHandler implements HttpExecuteInterceptor, HttpUnsuccessfulResponseHandler {

		/**
		 * Whether we've received a 401 error code indicating the token is invalid.
		 */
		boolean received401;
		String token;

		@Override
		public void intercept(HttpRequest request) throws IOException {
			try {
				token = getToken();
				request.getHeaders().setAuthorization("Bearer " + token);
			} catch (GooglePlayServicesAvailabilityException e) {
				throw new GooglePlayServicesAvailabilityIOException(e);
			} catch (UserRecoverableAuthException e) {
				throw new UserRecoverableAuthIOException(e);
			} catch (GoogleAuthException e) {
				throw new GoogleAuthIOException(e);
			}
		}

		@Override
		public boolean handleResponse(HttpRequest request, HttpResponse response, boolean supportsRetry) throws IOException {
			if (response.getStatusCode() == 401 && !received401) {
				received401 = true;
				try {
					GoogleAuthUtil.clearToken(getContext(), token);
				} catch (GoogleAuthException e) {
					throw new IOException(e);
				}
				return true;
			}
			return false;
		}
	}
}
