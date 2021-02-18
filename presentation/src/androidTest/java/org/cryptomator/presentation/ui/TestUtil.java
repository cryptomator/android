package org.cryptomator.presentation.ui;

import androidx.test.espresso.ViewInteraction;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.presentation.R;
import org.cryptomator.presentation.di.component.ApplicationComponent;
import org.hamcrest.Matchers;

import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

public class TestUtil {

	public static final int DROPBOX = 0;
	public static final int GOOGLE_DRIVE = 1;
	public static final int ONEDRIVE = 2;
	public static final int WEBDAV = 3;
	public static final int LOCAL = 4;

	private static final String SD_CARD_REGEX = "(?i)SD[- ]*(CARD|KARTE)";

	public static void isToastDisplayed(String message, ActivityTestRule activityTestRule) {
		onView(withText(message)) //
				.inRoot(withDecorView(not(is(activityTestRule.getActivity().getWindow().getDecorView())))) //
				.check(matches(isDisplayed()));
	}

	public static void openMenu(UiDevice device) {
		try {
			final UiSelector toolbar = new UiSelector() //
					.resourceId("com.android.documentsui:id/toolbar");

			device //
					.findObject(toolbar.childSelector(new UiSelector().index(0))) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Menu not found");
		}
	}

	public static void chooseSdCard(UiDevice device) {
		try {
			if (!sdCardAlreadySelected()) {
				openMenu(device);

				device //
						.findObject(new UiSelector().textMatches(SD_CARD_REGEX)) //
						.click();
			}
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Menu not found");
		}
	}

	private static boolean sdCardAlreadySelected() {
		ViewInteraction textView = onView(allOf(withText("SDCARD"), withParent(withId(R.id.toolbar)), isDisplayed()));
		return textView.check(matches(withText("SDCARD"))) != null;
	}

	public static void openSettings(UiDevice device) {
		awaitCompleted();

		waitForIdle(device);

		openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());

		waitForIdle(device);

		onView(allOf( //
				withId(R.id.title), //
				withText(R.string.snack_bar_action_title_settings))) //
				.perform(click());

		awaitCompleted();

	}

	public static void waitForIdle(UiDevice uiDevice) {
		uiDevice.waitForIdle();
	}

	public static void addFolderInCloud(ApplicationComponent appComponent, String path, CloudType cloudType) {
		try {
			CloudFolder vaultFolder = (CloudFolder) getNode(appComponent, getEncryptedCloud(appComponent, cloudType), path);

			if (!appComponent.cloudContentRepository().exists(vaultFolder)) {
				assertThat(appComponent.cloudContentRepository().create(vaultFolder), Matchers.is(notNullValue()));
			}
		} catch (BackendException e) {
			throw new AssertionError("Error while adding testVault");
		}
	}

	public static void removeFolderInCloud(ApplicationComponent appComponent, String path, CloudType cloudType) {
		try {
			CloudFolder vaultFolder = (CloudFolder) getNode(appComponent, getEncryptedCloud(appComponent, cloudType), path);

			if (appComponent.cloudContentRepository().exists(vaultFolder)) {
				appComponent.cloudContentRepository().delete(vaultFolder);
			}
		} catch (BackendException e) {
			throw new AssertionError("Error while removing testVault");
		}
	}

	private static Cloud getEncryptedCloud(ApplicationComponent appComponent, CloudType cloudType) throws BackendException {
		Cloud cloud;
		if (cloudType.equals(CloudType.LOCAL)) {
			cloud = appComponent.cloudRepository().clouds(cloudType).get(1);
		} else {
			cloud = appComponent.cloudRepository().clouds(cloudType).get(0);
		}

		return cloud;
	}

	private static CloudNode getNode(ApplicationComponent appComponent, Cloud cloud, String path) throws BackendException {
		return appComponent.cloudContentRepository().resolve(cloud, path);
	}

	public static void removeFolderInVault(ApplicationComponent appComponent, String name, CloudType cloudType) {
		try {
			Cloud decryptedCloud = getDecryptedCloud(appComponent, cloudType);
			CloudFolder root = appComponent.cloudContentRepository().root(decryptedCloud);
			CloudFolder folder = appComponent.cloudContentRepository().folder(root, name);
			appComponent.cloudContentRepository().delete(folder);
		} catch (BackendException e) {
			throw new AssertionError(e);
		}
	}

	public static void addFolderInVaultsRoot(ApplicationComponent appComponent, String name, CloudType cloudType) {
		try {
			Cloud decryptedCloud = getDecryptedCloud(appComponent, cloudType);
			CloudFolder root = appComponent.cloudContentRepository().root(decryptedCloud);
			CloudFolder folder = appComponent.cloudContentRepository().folder(root, name);
			assertThat(appComponent.cloudContentRepository().create(folder), is(notNullValue()));
		} catch (BackendException e) {
			throw new AssertionError(e);
		}
	}

	private static Cloud getDecryptedCloud(ApplicationComponent appComponent, CloudType cloudType) throws BackendException {
		List<Vault> vaults = appComponent.vaultRepository().vaults();
		for (Vault vault : vaults) {
			if (vault.getCloudType().equals(cloudType)) {
				return appComponent.cloudRepository().decryptedViewOf(vault);
			}
		}

		throw new AssertionError("Cloud for vault not found");
	}
}
