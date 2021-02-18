package org.cryptomator.presentation.ui.activity;

import org.cryptomator.presentation.R;

import java.util.Collections;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.pressBack;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.cryptomator.presentation.ui.activity.LoginWebdavClouds.WebdavCloudCredentials.notSpecialWebdavClouds;
import static org.hamcrest.Matchers.allOf;

class LoginWebdavClouds {

	private static final String localUrl = "192.168.0.108";

	static void loginWebdavClouds(SplashActivity activity) {
		loginStandardWebdavClouds();

		/*
		 * loginAuthenticationFailWebdavCloud(activity);
		 *
		 * loginSelfSignedWebdavCloud();
		 *
		 * loginRedirectToHttpsWebdavCloud();
		 *
		 * loginRedirectToUrlWebdavCloud();
		 */
	}

	private static void loginStandardWebdavClouds() {
		for (WebdavCloudCredentials webdavCloudCredential : notSpecialWebdavClouds()) {
			createNewCloud();

			enterCredentials(webdavCloudCredential);

			startLoginProcess();

			awaitCompleted();

			checkResult(webdavCloudCredential);
		}

		pressBack();
	}

	private static void createNewCloud() {
		onView(withId(R.id.floating_action_button)) //
				.perform(click());
	}

	private static void startLoginProcess() {
		onView(withId(R.id.createCloudButton)) //
				.perform(click());
	}

	/*
	 * private static void loginAuthenticationFailWebdavCloud(SplashActivity activity) {
	 * createNewCloud();
	 * enterCredentials(AUTHENTICATION_FAIL);
	 * startLoginProcess();
	 *
	 * onView(withText(R.string.error_authentication_failed)) //
	 * .inRoot(withDecorView(not(is(activity.getWindow().getDecorView())))) //
	 * .check(matches(isDisplayed()));
	 *
	 * onView(allOf( //
	 * withId(R.id.et_url_port), //
	 * withText(AUTHENTICATION_FAIL.url)));
	 *
	 * onView(allOf( //
	 * withId(R.id.et_user), //
	 * withText(AUTHENTICATION_FAIL.username)));
	 *
	 * onView(allOf( //
	 * withId(R.id.et_password), //
	 * withText(AUTHENTICATION_FAIL.password)));
	 *
	 * pressBack();
	 * }
	 *
	 * private static void loginSelfSignedWebdavCloud() {
	 * createNewCloud();
	 * enterCredentials(SELF_SIGNED_HTTPS);
	 * startLoginProcess();
	 * clickOk();
	 * startLoginProcess();
	 * checkResult(SELF_SIGNED_HTTPS);
	 * }
	 *
	 * private static void loginRedirectToHttpsWebdavCloud() {
	 * createNewCloud();
	 * enterCredentials(REDIRECT_TO_HTTPS);
	 * startLoginProcess();
	 * clickOk();
	 * clickOk();
	 * startLoginProcess();
	 * checkResult(REDIRECT_TO_HTTPS);
	 * }
	 *
	 * private static void loginRedirectToUrlWebdavCloud() {
	 * createNewCloud();
	 * enterCredentials(REDIRECT_TO_URL);
	 * startLoginProcess();
	 * clickOk();
	 * startLoginProcess();
	 * }
	 */

	private static void enterCredentials(WebdavCloudCredentials webdavCloudCredential) {
		onView(withId(R.id.urlPortEditText)) //
				.perform(replaceText(webdavCloudCredential.url));

		onView(withId(R.id.userNameEditText)) //
				.perform(replaceText(webdavCloudCredential.username));

		onView(withId(R.id.passwordEditText)) //
				.perform( //
						replaceText(webdavCloudCredential.password), //
						closeSoftKeyboard());
	}

	private static void checkResult(WebdavCloudCredentials webdavCloudCredential) {
		onView(allOf( //
				withId(R.id.cloudText), //
				withText(webdavCloudCredential.displayUrl)));

		onView(allOf( //
				withId(R.id.cloudSubText), //
				withText(webdavCloudCredential.username + " • ")));
	}

	enum WebdavCloudCredentials {
		GMX("https://webdav.mc.gmx.net/", "webdav.mc.gmx.net", "jraufelder@gmx.de", "mG7!3B3Mx"), //
		/*
		 * FREENET("https://webmail.freenet.de/webdav", "webmail.freenet.de", "milestone@freenet.de", "rF7!3B3Et")
		 *
		 * , //
		 * /*
		 * AUTHENTICATION_FAIL("https://webdav.mc.gmx.net/", "webdav.mc.gmx.net", "bla@bla.de", "bla"), //
		 * SELF_SIGNED_HTTPS("https://" + localUrl + "/webdav", localUrl + "/webdav", "bla@bla.de", "bla"), //
		 * REDIRECT_TO_HTTPS("http://" + localUrl + "/webdav", localUrl + "/webdav", "bla@bla.de", "bla"), //
		 * REDIRECT_TO_URL("https://" + localUrl + "/bar/baz", localUrl + "/bar/baz", "bla@bla.de", "bla")
		 */;

		private final String url;
		private final String displayUrl;
		private final String username;
		private final String password;

		WebdavCloudCredentials(String url, String displayUrl, String username, String password) {
			this.url = url;
			this.displayUrl = displayUrl;
			this.username = username;
			this.password = password;
		}

		static List<WebdavCloudCredentials> notSpecialWebdavClouds() {
			return Collections.singletonList(GMX/* , FREENET */);
		}
	}
}
