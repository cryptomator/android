package org.cryptomator.presentation.ui.activity;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;

import org.cryptomator.presentation.R;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.cryptomator.presentation.ui.TestUtil.DROPBOX;
import static org.cryptomator.presentation.ui.TestUtil.GOOGLE_DRIVE;
import static org.cryptomator.presentation.ui.TestUtil.LOCAL;
import static org.cryptomator.presentation.ui.TestUtil.ONEDRIVE;
import static org.cryptomator.presentation.ui.TestUtil.WEBDAV;
import static org.cryptomator.presentation.ui.TestUtil.isToastDisplayed;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.checkEmptyFolderHint;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.checkFileOrFolderAlreadyExistsErrorMessage;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.openSettings;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.withRecyclerView;
import static org.hamcrest.Matchers.allOf;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
public class FolderOperationsTest {
	@Rule
	public ActivityTestRule<SplashActivity> activityTestRule //
			= new ActivityTestRule<>(SplashActivity.class);

	private final UiDevice device = UiDevice.getInstance(getInstrumentation());
	private final Context context = InstrumentationRegistry.getTargetContext();

	private final Integer cloudId;

	@Parameterized.Parameters(name = "{1}")
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][] {{DROPBOX, "DROPBOX"}, {GOOGLE_DRIVE, "GOOGLE_DRIVE"}, {ONEDRIVE, "ONEDRIVE"}, {WEBDAV, "WEBDAV"}, {LOCAL, "LOCAL"}});
	}

	public FolderOperationsTest(Integer cloudId, String cloudName) {
		this.cloudId = cloudId;
	}

	@Test
	public void test00CreateFolderLeadsToFolderInVault() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		String folderName = "testFolder";
		createFolder(folderName);

		checkFolderCreationResult(folderName, "/0/testVault", 0);
		pressBack();

		folderName = "testFolder1";
		createFolder(folderName);

		checkFolderCreationResult(folderName, "/0/testVault", 1);

		pressBack();
		pressBack();
	}

	@Test
	public void test01CreateExistingFolderLeadsToNoNewFolderInVault() {
		String folderName = "testFolder";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		awaitCompleted();

		createFolder(folderName);

		checkFileOrFolderAlreadyExistsErrorMessage(folderName);

		onView(withId(android.R.id.button2)) //
				.perform(click());

		awaitCompleted();

		pressBack();
	}

	@Test
	public void test02OpenFolderLeadsToOpenFolder() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		checkEmptyFolderHint();

		pressBack();
		pressBack();
	}

	@Test
	public void test03RenameFolderLeadsToFolderWithNewName() {
		String newFolderName = "testFolder2";
		int nodePosition = 1;

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		renameFolderTo(newFolderName, nodePosition);

		checkFolderDisplayText(newFolderName, nodePosition);

		pressBack();
	}

	@Test
	public void test04RenameFolderToAlreadyExistFolderLeadsToSameFolderName() {
		String newFolderName = "testFolder";
		int nodePosition = 1;

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		renameFolderTo(newFolderName, nodePosition);

		checkFileOrFolderAlreadyExistsErrorMessage(newFolderName);

		onView(withId(android.R.id.button2)) //
				.perform(click());

		awaitCompleted();

		pressBack();
	}

	@Test
	public void test05MoveFolderLeadsToFolderWithNewLocation() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openSettings(device, 1);

		openMoveFolder();

		openFolder(0);

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		openFolder(0);

		checkFolderDisplayText("testFolder2", 0);

		openFolder(0);

		checkEmptyFolderHint();

		pressBack();
		pressBack();
		pressBack();
	}

	@Test
	public void test06MoveFolderToAlreadyExistingFolderLeadsToErrorMessage() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openSettings(device, 0);

		openMoveFolder();

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		awaitCompleted();

		isToastDisplayed( //
				context.getString(R.string.error_file_or_folder_exists), //
				activityTestRule);

		pressBack();
		pressBack();
	}

	@Test
	public void test07DeleteFolderLeadsToRemovedFolder() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		openSettings(device, 0);

		onView(withId(R.id.delete_folder)) //
				.perform(click());

		awaitCompleted();

		onView(withId(android.R.id.button1)) //
				.perform(click());

		awaitCompleted();

		checkEmptyFolderHint();

		pressBack();
		pressBack();
	}

	private void openMoveFolder() {
		onView(withId(R.id.move_folder)) //
				.perform(click());
	}

	private void createFolder(String name) {
		awaitCompleted();

		onView(withId(R.id.floatingActionButton)) //
				.perform(click());

		onView(withId(R.id.create_new_folder)) //
				.perform(click());

		onView(withId(R.id.et_folder_name)) //
				.perform(replaceText(name), closeSoftKeyboard());

		onView(allOf( //
				withId(android.R.id.button1), //
				withText(R.string.screen_enter_vault_name_button_text))) //
						.perform(click());

		awaitCompleted();
	}

	private void checkFolderCreationResult(String folderName, String path, int position) {
		checkFolderDisplayText(folderName, position);

		openSettings(device, position);

		onView(allOf( //
				withId(R.id.tv_folder_path), //
				withText(path))) //
						.check(matches(withText(path)));

		awaitCompleted();
	}

	private void renameFolderTo(String name, int nodePosition) {
		awaitCompleted();

		openSettings(device, nodePosition);

		onView(withId(R.id.change_cloud)) //
				.perform(click());

		onView(withId(R.id.et_rename)) //
				.perform(replaceText(name), closeSoftKeyboard());

		onView(allOf( //
				withId(android.R.id.button1), //
				withText(R.string.dialog_rename_node_positive_button))) //
						.perform(click());

		awaitCompleted();
	}

	private void checkFolderDisplayText(String assertNodeText, int nodePosition) {
		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(nodePosition, R.id.cloudFolderText)) //
						.check(matches(withText(assertNodeText)));
	}

	static void openFolder(int nodePosition) {
		awaitCompleted();

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(nodePosition, R.id.cloudFolderText)) //
						.perform(click());

		awaitCompleted();
	}
}
