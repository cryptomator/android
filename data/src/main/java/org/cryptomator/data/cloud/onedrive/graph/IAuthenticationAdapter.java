package org.cryptomator.data.cloud.onedrive.graph;

import android.app.Activity;

import com.microsoft.graph.authentication.IAuthenticationProvider;

/**
 * An authentication adapter for signing requests, logging in, and logging out.
 */
public interface IAuthenticationAdapter extends IAuthenticationProvider {

	/**
	 * Logs out the user
	 *
	 * @param callback The callback when the logout is complete or an error occurs
	 */
	void logout(final ICallback<Void> callback);

	/**
	 * Login a user by popping UI
	 *
	 * @param activity The current activity
	 * @param callback The callback when the login is complete or an error occurs
	 */
	void login(final Activity activity, final ICallback<String> callback);

	/**
	 * Login a user with no ui
	 *
	 * @param callback The callback when the login is complete or an error occurs
	 */
	void loginSilent(final ICallback<Void> callback);

	/**
	 * Gets the access token for the session of a logged in user
	 *
	 * @return the access token
	 */
	String getAccessToken() throws ClientException;
}
