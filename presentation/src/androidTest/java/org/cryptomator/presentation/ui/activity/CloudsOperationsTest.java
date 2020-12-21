package org.cryptomator.presentation.ui.activity;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import org.cryptomator.presentation.R;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withChild;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static java.lang.Thread.sleep;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.cryptomator.presentation.ui.TestUtil.DROPBOX;
import static org.cryptomator.presentation.ui.TestUtil.GOOGLE_DRIVE;
import static org.cryptomator.presentation.ui.TestUtil.LOCAL;
import static org.cryptomator.presentation.ui.TestUtil.ONEDRIVE;
import static org.cryptomator.presentation.ui.TestUtil.WEBDAV;
import static org.cryptomator.presentation.ui.TestUtil.openSettings;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.withRecyclerView;
import static org.hamcrest.core.AllOf.allOf;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CloudsOperationsTest {

	@Rule
	public ActivityTestRule<SplashActivity> activityTestRule //
			= new ActivityTestRule<>(SplashActivity.class);

	private final UiDevice device = UiDevice.getInstance(getInstrumentation());

	@Test
	public void test01EnableDebugModeLeadsToDebugMode() {
		openSettings(device);

		try {
			new UiScrollable(new UiSelector().scrollable(true)).scrollToEnd(10);

			awaitCompleted();

			onView(withChild(withText(R.string.screen_settings_debug_mode_label))) //
					.perform(click());

			awaitCompleted();

			onView(withId(android.R.id.button1)) //
					.perform(click());
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Scrolling down failed");
		}
	}

	@Test
	public void test02LoginDropboxCloudLeadsToLoggedInDropboxCloud() {
		openCloudServices(device);

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(DROPBOX, click()));

		try {
			device //
					.findObject(new UiSelector().resourceId("android:id/button_once")) //
					.click();

			device.waitForIdle();

			device //
					.findObject(new UiSelector().text("Email")) //
					.setText("");

			device //
					.findObject(new UiSelector().text("Password")) //
					.setText("");

			device //
					.findObject(new UiSelector().description("Sign in")) //
					.click();

			device.waitForIdle();

			device //
					.findObject(new UiSelector().description("Allow")) //
					.click();

		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Dropbox login failed");
		}

		device.waitForIdle();

		checkLoginResult("Dropbox", DROPBOX);
	}

	@Test
	public void test03LoginGoogleDriveCloudLeadsToLoggedInGoogleDriveCloud() {
		openCloudServices(device);

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

	@Test
	public void test04LoginOneDriveLeadsToLoggedInOneDriveCloud() {
		openCloudServices(device);

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(ONEDRIVE, click()));

		try {
			try {
				sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			device //
					.findObject(new UiSelector().resourceId("i0116")) //
					.setText("");

			device //
					.findObject(new UiSelector().resourceId("idSIButton9")) //
					.click();

			device.waitForWindowUpdate(null, 500);

			device //
					.findObject(new UiSelector().resourceId("i0118")) //
					.setText("");

			device.waitForWindowUpdate(null, 500);

			device //
					.findObject(new UiSelector().resourceId("idSIButton9")) //
					.click();

			try {
				device //
						.findObject(new UiSelector().resourceId("idSIButton9")) //
						.click();
			} catch (UiObjectNotFoundException e) {
				// Do nothing because second click is normaly not necessary
			}
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("OneDrive login failed");
		}

		awaitCompleted();

		checkLoginResult("OneDrive", ONEDRIVE);
	}

	@Test
	public void test05LoginWebdavCloudLeadsToLoggedInWebdavCloud() {
		openCloudServices(device);

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(WEBDAV, click()));

		LoginWebdavClouds.loginWebdavClouds(activityTestRule.getActivity());
	}

	@Test
	public void test06LoginLocalCloudLeadsToLoggedInLocalCloud() {
		openCloudServices(device);

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(LOCAL, click()));

		LoginLocalClouds.loginLocalClouds(device);
	}

	public static void openCloudServices(UiDevice device) {
		openSettings(device);

		ViewInteraction recyclerView = onView(allOf(withId(R.id.recycler_view), childAtPosition(withId(android.R.id.list_container), 0)));
		recyclerView.perform(RecyclerViewActions.actionOnItemAtPosition(3, click()));

		awaitCompleted();
	}

	public static void checkLoginResult(String cloudName, int cloudPosition) {
		String displayText = InstrumentationRegistry //
				.getTargetContext() //
				.getString(R.string.screen_cloud_settings_sign_out_from_cloud) + " " + cloudName;

		UiObject signOutText = UiDevice.getInstance(getInstrumentation()).findObject(new UiSelector().text(displayText));
		signOutText.waitForExists(15000);

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(cloudPosition, R.id.cloudName)) //
						.check(matches(withText(displayText)));
	}

	private static Matcher<View> childAtPosition(final Matcher<View> parentMatcher, final int position) {

		return new TypeSafeMatcher<View>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("Child at position " + position + " in parent ");
				parentMatcher.describeTo(description);
			}

			@Override
			public boolean matchesSafely(View view) {
				ViewParent parent = view.getParent();
				return parent instanceof ViewGroup && parentMatcher.matches(parent) //
						&& view.equals(((ViewGroup) parent).getChildAt(position));
			}
		};
	}
}
