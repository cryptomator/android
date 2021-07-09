package org.cryptomator.presentation.ui.activity;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.cryptomator.domain.CloudType;
import org.cryptomator.presentation.CryptomatorApp;
import org.cryptomator.presentation.R;
import org.cryptomator.presentation.di.component.ApplicationComponent;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static android.R.id.button1;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static java.lang.String.format;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.cryptomator.presentation.ui.TestUtil.DROPBOX;
import static org.cryptomator.presentation.ui.TestUtil.GOOGLE_DRIVE;
import static org.cryptomator.presentation.ui.TestUtil.LOCAL;
import static org.cryptomator.presentation.ui.TestUtil.ONEDRIVE;
import static org.cryptomator.presentation.ui.TestUtil.WEBDAV;
import static org.cryptomator.presentation.ui.TestUtil.addFolderInVaultsRoot;
import static org.cryptomator.presentation.ui.TestUtil.chooseSdCard;
import static org.cryptomator.presentation.ui.TestUtil.isToastDisplayed;
import static org.cryptomator.presentation.ui.TestUtil.removeFolderInVault;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.checkEmptyFolderHint;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.checkFileOrFolderAlreadyExistsErrorMessage;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.openSettings;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.withRecyclerView;
import static org.cryptomator.presentation.ui.activity.FolderOperationsTest.openFolder;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
public class FileOperationsTest {

	private final UiDevice device = UiDevice.getInstance(getInstrumentation());
	private final Context context = InstrumentationRegistry.getTargetContext();
	private final Integer cloudId;
	@Rule
	public ActivityTestRule<SplashActivity> activityTestRule //
			= new ActivityTestRule<>(SplashActivity.class);
	private String packageName;

	public FileOperationsTest(Integer cloudId, String cloudName) {
		this.cloudId = cloudId;
	}

	@Parameterized.Parameters(name = "{1}")
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][] {{DROPBOX, "DROPBOX"}, {GOOGLE_DRIVE, "GOOGLE_DRIVE"}, {ONEDRIVE, "ONEDRIVE"}, {WEBDAV, "WEBDAV"}, {LOCAL, "LOCAL"}});
	}

	static void isPermissionShown(UiDevice device) {
		if (!device //
				.findObject(new UiSelector().text("ALLOW")) //
				.waitForExists(1000L)) {
			throw new AssertionError("View with text <???> not found!");
		}
	}

	static void grantPermission(UiDevice device) {
		try {
			device //
					.findObject(new UiSelector().text("ALLOW")) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Permission not found");
		}
	}

	static void openFile(int nodePosition) {
		awaitCompleted();

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(nodePosition, R.id.cloudFileText)) //
				.perform(click());
	}

	@Test
	public void test00UploadFileWithCancelPressedWhileUploadingLeadsToNoNewFileInVault() {
		assumeThat(cloudId, is(not(LOCAL)));

		packageName = activityTestRule.getActivity().getPackageName();

		String nodeName = "foo.pdf";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		uploadFile(nodeName);

		onView(withId(android.R.id.button3)) //
				.perform(click());

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(swipeDown());

		awaitCompleted();

		try {
			onView(withRecyclerView(R.id.recyclerView) //
					.atPositionOnView(1, R.id.cloudFileText)) //
					.check(matches(withText(nodeName)));

			throw new AssertionError("Canceling the upload should not lead to new cloud node");
		} catch (NullPointerException e) {
			// do nothing
		}

		pressBack();
	}

	@Test
	public void test01UploadFileLeadsToNewFileInVault() {
		packageName = activityTestRule.getActivity().getPackageName();
		String nodeName = "lala.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		uploadFile(nodeName);

		device.waitForWindowUpdate(packageName, 15000);

		checkFileDisplayText(nodeName, 1);

		pressBack();
	}

	@Test
	public void test02UploadAlreadyExistingFileAndCancelLeadsToNoNewFileInVault() {
		String nodeName = "lala.png";
		String subString;

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		// subString = subtextFromFile();

		uploadFile(nodeName);

		device.waitForWindowUpdate(packageName, 500);

		onView(withText(R.string.dialog_upload_file_cancel_button)) //
				.perform(click());

		awaitCompleted();

		// assertThat(subtextFromFile(), equalTo(subString));

		pressBack();
	}

	@Test
	public void test03UploadAlreadyExistingFileAndReplaceLeadsToUpdatedFileInVault() {
		String nodeName = "lala.png";
		String subString;

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		awaitCompleted();

		subString = subtextFromFile();

		uploadFile(nodeName);

		device.waitForWindowUpdate(packageName, 500);

		onView(withText(R.string.dialog_existing_file_positive_button)) //
				.perform(click());

		awaitCompleted();

		// assertThat(subtextFromFile(), not(equalTo(subString)));

		pressBack();
	}

	@Test
	public void test04OpenFileLeadsToOpenFile() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFile(1);

		device.waitForWindowUpdate(null, 25000);

		device.pressBack();

		awaitCompleted();

		pressBack();
	}

	@Test
	public void test05RenameFileLeadsToFileWithNewName() {
		String fileName = "foo.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		renameFileTo(fileName, 1);

		checkFileDisplayText(fileName, 1);

		pressBack();
	}

	@Test
	public void test06RenameFileToExistingFolderLeadsToNothingChanged() {
		String fileName = "testFolder";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		renameFileTo(fileName, 1);

		checkFileOrFolderAlreadyExistsErrorMessage(fileName);

		onView(withId(android.R.id.button2)) //
				.perform(click());

		checkFileDisplayText("foo.png", 1);

		pressBack();
	}

	@Test
	public void test07MoveFileLeadsToFileWithNewLocation() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openSettings(device, 1);

		openMoveFile();

		openFolder(0);

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		openFolder(0);

		checkFileDisplayText("foo.png", 0);

		pressBack();
		pressBack();
	}

	@Test
	public void test08MoveWithExistingFileLeadsToNoNewFile() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		openSettings(device, 0);

		openMoveFile();

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		isToastDisplayed( //
				format( //
						InstrumentationRegistry //
								.getTargetContext() //
								.getString(R.string.error_file_or_folder_exists), //
						"foo.png"), //
				activityTestRule);

		pressBack();
		pressBack();
		pressBack();
		pressBack();
	}

	@Test
	public void test09MoveWithExistingFolderLeadsToNoNewFile() {
		ApplicationComponent appComponent = ((CryptomatorApp) activityTestRule.getActivity().getApplication()).getComponent();

		String moveFileNameAndTmpFolder = "foo.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		addFolderInVaultsRoot(appComponent, moveFileNameAndTmpFolder, CloudType.values()[cloudId]);

		awaitCompleted();

		refreshCloudNodes();

		openFolder(1);

		openSettings(device, 0);

		openMoveFile();

		pressBack();

		awaitCompleted();

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		isToastDisplayed( //
				InstrumentationRegistry //
						.getTargetContext() //
						.getString(R.string.error_file_or_folder_exists), //
				activityTestRule);

		removeFolderInVault(appComponent, moveFileNameAndTmpFolder, CloudType.values()[cloudId]);

		pressBack();
		pressBack();
		pressBack();
	}

	@Test
	public void test10ShareFileWithCryptomatorLeadsToNewFileInVault() {
		String newFileName = "fooBar.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		shareFile(newFileName, cloudId, 0);

		isToastDisplayed(context.getString(R.string.screen_share_files_msg_success), activityTestRule);

		refreshCloudNodes();

		checkFileDisplayText(newFileName, 1);

		pressBack();
		pressBack();
	}

	@Test
	public void test11ShareExistingFileAndReplaceWithCryptomatorLeadsToUpdatedFileInVault() {
		String newFileName = "fooBar.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		shareFile(newFileName, cloudId, 0);

		onView(withId(android.R.id.button1)) //
				.perform(click());

		awaitCompleted();

		isToastDisplayed(context.getString(R.string.screen_share_files_msg_success), activityTestRule);

		refreshCloudNodes();

		checkFileDisplayText(newFileName, 1); // check before and compare with after upload

		pressBack();
		pressBack();
	}

	@Test
	public void test12ShareExistingFileWithoutReplaceWithCryptomatorLeadsToNotUpdatedFileInVault() {
		String newFileName = "fooBar.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		shareFile(newFileName, cloudId, 0);

		onView(withId(android.R.id.button3)) //
				.perform(click());

		pressBack();
		pressBack();
		pressBack();
	}

	@Test
	public void test13RenameFileToExistingFileLeadsToNothingChanged() {
		String fileName = "fooBar.png";

		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		openFolder(0);

		renameFileTo(fileName, 0);

		checkFileOrFolderAlreadyExistsErrorMessage(fileName);

		onView(withId(android.R.id.button2)) //
				.perform(click());

		awaitCompleted();

		checkFileDisplayText("foo.png", 0);

		pressBack();
		pressBack();
	}

	@Test
	public void test14DeleteFileLeadsToRemovedFile() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(cloudId, click()));

		awaitCompleted();

		openFolder(0);

		deleteNodeOnPosition(0);
		awaitCompleted();
		deleteNodeOnPosition(0);

		checkEmptyFolderHint();

		pressBack();

		deleteTestFolder();

		pressBack();
	}

	private void deleteNodeOnPosition(int position) {
		openSettings(device, position);

		onView(withId(R.id.delete_file)) //
				.perform(click());

		awaitCompleted();

		onView(withId(android.R.id.button1)) //
				.perform(click());
	}

	private void refreshCloudNodes() {
		awaitCompleted();

		onView(withId(R.id.recyclerView)) //
				.perform(swipeDown());

		awaitCompleted();
	}

	private void shareFile(String newFilename, int vaultPosition, int filePosition) {
		awaitCompleted();

		openSettings(device, filePosition);

		openEncryptWithCryptomator();

		chooseShareLocation(vaultPosition);

		checkPathInSharingScreen("/testFolder", vaultPosition);

		onView(withId(R.id.fileName)) //
				.perform(replaceText(newFilename), closeSoftKeyboard());

		onView(withId(R.id.saveFiles)) //
				.perform(click());

		awaitCompleted();
	}

	private void chooseShareLocation(int nodePosition) {
		onView(withRecyclerView(R.id.locationsRecyclerView) //
				.atPositionOnView(nodePosition, R.id.vaultName)) //
				.perform(click());

		device.waitForWindowUpdate(packageName, 300);

		onView(withRecyclerView(R.id.locationsRecyclerView) //
				.atPositionOnView(nodePosition, R.id.chooseFolderLocation)) //
				.perform(click());

		openFolder(0);

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		awaitCompleted();
	}

	private void checkPathInSharingScreen(String path, int nodePosition) {
		onView(withRecyclerView(R.id.locationsRecyclerView) //
				.atPositionOnView(nodePosition, R.id.chosenLocation)) //
				.check(matches(withText(path)));
	}

	private void deleteTestFolder() {
		awaitCompleted();

		openSettings(device, 0);

		onView(withId(R.id.delete_folder)) //
				.perform(click());

		awaitCompleted();

		onView(withId(android.R.id.button1)) //
				.perform(click());

		awaitCompleted();

		checkEmptyFolderHint();
	}

	private void uploadFile(String nodeName) {
		awaitCompleted();

		onView(withId(R.id.floatingActionButton)) //
				.perform(click());

		onView(withId(R.id.upload_files)) //
				.perform(click());

		chooseSdCard(device);

		try {
			device //
					.findObject(new UiSelector().text(nodeName)) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Image " + nodeName + " not available");
		}
	}

	private void renameFileTo(String name, int nodePosition) {
		awaitCompleted();

		openSettings(device, nodePosition);

		onView(withId(R.id.rename_file)) //
				.perform(click());

		onView(withId(R.id.et_rename)) //
				.perform(replaceText(name), closeSoftKeyboard());

		onView(allOf( //
				withId(button1), //
				withText(R.string.dialog_rename_node_positive_button))) //
				.perform(click());

		awaitCompleted();
	}

	private void checkFileDisplayText(String assertNodeText, int nodePosition) {
		awaitCompleted();

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(nodePosition, R.id.cloudFileText)) //
				.check(matches(withText(assertNodeText)));
	}

	private void openMoveFile() {
		awaitCompleted();

		onView(withId(R.id.move_file)) //
				.perform(click());

		awaitCompleted();
	}

	private void openEncryptWithCryptomator() {
		openShareFile();
		try {
			device //
					.findObject(new UiSelector().text(context.getString(R.string.screen_share_label))) //
					.waitForExists(30000L);
			device //
					.findObject(new UiSelector().text(context.getString(R.string.screen_share_label))) //
					.click();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Share with Cryptomator not available");
		}

		awaitCompleted();
	}

	private void openShareFile() {
		awaitCompleted();

		onView(withId(R.id.share_file)) //
				.perform(click());

		awaitCompleted();
	}

	private String subtextFromFile() {
		try {
			final UiSelector docList = new UiSelector() //
					.resourceId("org.cryptomator:id/cloudFileContent");

			return device //
					.findObject(docList.childSelector(new UiSelector(). //
							resourceId("org.cryptomator:id/cloudFileSubText") //
							.index(0))) //
					.getText();
		} catch (UiObjectNotFoundException e) {
			throw new AssertionError("Folder 0 not found");
		}
	}
}
