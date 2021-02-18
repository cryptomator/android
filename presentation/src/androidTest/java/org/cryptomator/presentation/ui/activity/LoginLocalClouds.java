package org.cryptomator.presentation.ui.activity;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.cryptomator.presentation.R;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.cryptomator.presentation.ui.TestUtil.chooseSdCard;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.withRecyclerView;

public class LoginLocalClouds {

	private static UiDevice device;

	public static void loginLocalClouds(UiDevice uiDevice) {
		device = uiDevice;
		String folderName = "0";
		createNewStorageAccessCloudCloud(folderName);
		awaitCompleted();
		checkResult(folderName);
	}

	private static void createNewStorageAccessCloudCloud(String folderName) {
		onView(withId(R.id.floating_action_button)) //
				.perform(click());

		awaitCompleted();

		chooseSdCard(device);

		awaitCompleted();

		openFolder0();

		awaitCompleted();

		chooseFolder(device);
	}

	private static void openFolder0() {
		try {
			final UiSelector docList = new UiSelector() //
					.resourceId("com.android.documentsui:id/container_directory") //
					.childSelector( //
							new UiSelector() //
									.resourceId("com.android.documentsui:id/dir_list"));

			device //
					.findObject(docList.childSelector(new UiSelector().text("0"))) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Folder 0 not found");
		}
	}

	public static void chooseFolder(UiDevice device) {
		try {

			final UiSelector docList = new UiSelector() //
					.resourceId("com.android.documentsui:id/container_save");

			device //
					.findObject(docList.childSelector(new UiSelector().resourceId("android:id/button1"))) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Folder 0 not found");
		}
	}

	private static void checkResult(String folderName) {
		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(0, R.id.cloudText)) //
				.perform(click());
	}
}
