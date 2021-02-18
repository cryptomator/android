package org.cryptomator.presentation.testCloud;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import junit.framework.AssertionFailedError;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.presentation.R;
import org.cryptomator.presentation.di.component.ApplicationComponent;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.cryptomator.presentation.ui.TestUtil.GOOGLE_DRIVE;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.withRecyclerView;
import static org.cryptomator.presentation.ui.activity.CloudsOperationsTest.checkLoginResult;
import static org.cryptomator.presentation.ui.activity.CloudsOperationsTest.openCloudServices;

public class GoogledriveTestCloud extends TestCloud {

	@Override
	public Cloud getInstance(ApplicationComponent appComponent) {
		login();
		return GoogleDriveCloud.aGoogleDriveCloud() //
				.withUsername("geselthyn@googlemail.com") //
				.withAccessToken("geselthyn@googlemail.com") //
				.build();
	}

	public void login() {
		UiDevice device = UiDevice.getInstance(getInstrumentation());

		openCloudServices(device);

		if (alreadyLoggedIn()) {
			return;
		}

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(GOOGLE_DRIVE, click()));

		try {
			device //
					.findObject(new UiSelector().resourceId("android:id/text1")) //
					.click();

			device //
					.findObject(new UiSelector().resourceId("android:id/button1")) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("GoogleDrive login failed");
		}

		device.waitForIdle();

		checkLoginResult("Google Drive", GOOGLE_DRIVE);
	}

	private boolean alreadyLoggedIn() {
		try {
			onView(withRecyclerView(R.id.recyclerView) //
					.atPositionOnView(GOOGLE_DRIVE, R.id.tv_cloud_name)) //
					.check(matches(withText(InstrumentationRegistry //
							.getTargetContext() //
							.getString(R.string.screen_cloud_settings_sign_out_from_cloud) + " Google Drive")));
		} catch (AssertionFailedError e) {
			return false;
		}

		return true;

	}

	@Override
	public String toString() {
		return "GoogledriveTestCloud";
	}
}
