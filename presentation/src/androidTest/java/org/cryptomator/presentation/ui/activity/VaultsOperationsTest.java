package org.cryptomator.presentation.ui.activity;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
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
import static org.cryptomator.presentation.ui.TestUtil.removeFolderInCloud;
import static org.cryptomator.presentation.ui.TestUtil.waitForIdle;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.checkEmptyFolderHint;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.checkFileOrFolderAlreadyExistsErrorMessage;
import static org.cryptomator.presentation.ui.activity.BasicNodeOperationsUtil.withRecyclerView;
import static org.cryptomator.presentation.ui.activity.FileOperationsTest.grantPermission;
import static org.cryptomator.presentation.ui.activity.FileOperationsTest.isPermissionShown;
import static org.cryptomator.presentation.ui.activity.FileOperationsTest.openFile;
import static org.cryptomator.presentation.ui.activity.FolderOperationsTest.openFolder;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
public class VaultsOperationsTest {

	@Rule
	public final ActivityTestRule<SplashActivity> activityTestRule = new ActivityTestRule<>(SplashActivity.class);

	private final UiDevice device = UiDevice.getInstance(getInstrumentation());
	private final Context context = InstrumentationRegistry.getTargetContext();

	private final Integer cloudId;

	@Parameterized.Parameters(name = "{1}")
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][] {{DROPBOX, "DROPBOX"}, {GOOGLE_DRIVE, "GOOGLE_DRIVE"}, {ONEDRIVE, "ONEDRIVE"}, {WEBDAV, "WEBDAV"}, {LOCAL, "LOCAL"}});
	}

	public VaultsOperationsTest(Integer cloudId, String cloudName) {
		this.cloudId = cloudId;
	}

	@Test
	public void test00CreateNewVaultsLeadsToNewVaults() {
		ApplicationComponent appComponent = ((CryptomatorApp) activityTestRule.getActivity().getApplication()).getComponent();
		String path = "0/tempVault/";

		awaitCompleted();

		// Permission problem
		if (cloudId != LOCAL) {
			removeFolderInCloud(appComponent, path, CloudType.values()[cloudId]);
			removeFolderInCloud(appComponent, "0/testVault/", CloudType.values()[cloudId]);
			removeFolderInCloud(appComponent, "0/testLoggedInVault/", CloudType.values()[cloudId]);
		}

		onView(withId(R.id.floating_action_button)) //
				.perform(click());

		awaitCompleted();

		onView(withId(R.id.create_new_vault)) //
				.perform(click());

		createVault(cloudId, appComponent, path);

		awaitCompleted();

		unlockVault("tempVault", cloudId);

		awaitCompleted();

		checkEmptyFolderHint();

		pressBack();

	}

	@Test
	public void test01RenameLoggedInVaultToAlreadyExistingNameLeadsToNothingChanged() {
		String vaultName = "tempVault";

		renameVault(cloudId, vaultName);

		checkFileOrFolderAlreadyExistsErrorMessage(vaultName);

		onView(withId(android.R.id.button2)) //
				.perform(click());

		checkVault(cloudId, vaultName);
	}

	@Test
	public void test02ChangeLoggedInVaultPasswordLeadsToNewPassword() {
		String oldPassword = "tempVault";
		String newPassword = "foo";

		changePassword(cloudId, oldPassword, newPassword);

		isToastDisplayed(context.getString(R.string.screen_vault_list_change_password_successful), activityTestRule);

	}

	@Test
	public void test03RenameLoggedInVaultToNewNameLeadsToNewName() {
		String vaultName = "testLoggedInVault";

		renameVault(cloudId, vaultName);

		checkVault(cloudId, vaultName);

	}

	@Test
	public void test04RenameLoggedOutVaultToAlreadyExistingNameLeadsToNothingChanged() {
		String vaultName = "testLoggedInVault";

		renameVault(cloudId, vaultName);

		checkFileOrFolderAlreadyExistsErrorMessage(vaultName);

		onView(withId(android.R.id.button2)) //
				.perform(click());
	}

	@Test
	public void test05RenameLoggedOutVaultToNewNameLeadsToNewName() {
		String vaultName = "testVault";

		renameVault(cloudId, vaultName);

		checkVault(cloudId, vaultName);
	}

	@Test
	public void test06ChangeLoggedOutVaultPasswordLeadsToNewPassword() {
		String oldPassword = "foo";
		String newPassword = "testVault";

		changePassword(cloudId, oldPassword, newPassword);

		isToastDisplayed(context.getString(R.string.screen_vault_list_change_password_successful), activityTestRule);
	}

	@Test
	public void test07DeleteVaultsLeadsToDeletedVaults() {
		waitForIdle(device);

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(cloudId, R.id.settings)) //
						.perform(click());

		waitForIdle(device);

		onView(withId(R.id.delete_vault)) //
				.perform(click());

		awaitCompleted();

		onView(withId(android.R.id.button1)) //
				.perform(click());

		onView(withId(R.id.tv_creation_hint));
	}

	@Test
	public void test08addExistingVaultsLeadsToAddedVaults() {
		waitForIdle(device);

		onView(withId(R.id.floating_action_button)) //
				.perform(click());

		waitForIdle(device);

		onView(withId(R.id.add_existing_vault)) //
				.perform(click());

		openVault(cloudId);

		awaitCompleted();

		unlockVault("testVault", cloudId);

		awaitCompleted();

		checkEmptyFolderHint();

		pressBack();
	}

	private void renameVault(int position, String vaultName) {
		awaitCompleted();

		waitForIdle(device);

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(position, R.id.settings)) //
						.perform(click());

		waitForIdle(device);

		onView(withId(R.id.et_rename)) //
				.perform(click());

		awaitCompleted();

		waitForIdle(device);

		onView(withId(R.id.et_rename)) //
				.perform(replaceText("tempVault"), closeSoftKeyboard());

		onView(withId(R.id.et_rename)) //
				.perform(replaceText(vaultName), closeSoftKeyboard());

		onView(withId(android.R.id.button1)) //
				.perform(click());

		awaitCompleted();
	}

	private void createVault(int vaultPosition, ApplicationComponent appComponent, String path) {
		awaitCompleted();

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(vaultPosition, R.id.cloud)) //
						.perform(click());

		String vaultnameAndPassword = "tempVault";

		awaitCompleted();

		switch (vaultPosition) {
		case WEBDAV:
			awaitCompleted();

			onView(withRecyclerView(R.id.recyclerView) //
					.atPositionOnView(0, R.id.cloudText)) //
							.perform(click());
			break;
		case LOCAL:
			awaitCompleted();

			onView(withRecyclerView(R.id.recyclerView) //
					.atPositionOnView(0, R.id.cloudText)) //
							.perform(click());

			awaitCompleted();

			isPermissionShown(device);
			grantPermission(device);

			removeFolderInCloud(appComponent, path, CloudType.LOCAL);
			removeFolderInCloud(appComponent, "0/testVault/", CloudType.LOCAL);
			removeFolderInCloud(appComponent, "0/testLoggedInVault/", CloudType.LOCAL);

			break;
		}

		awaitCompleted();

		onView(withId(R.id.vaultNameEditText)) //
				.perform(replaceText(vaultnameAndPassword), closeSoftKeyboard());

		awaitCompleted();

		onView(withId(R.id.createVaultButton)) //
				.perform(click());

		awaitCompleted();

		openFolder(0);

		onView(withId(R.id.chooseLocationButton)) //
				.perform(click());

		awaitCompleted();

		onView(withId(R.id.passwordEditText)) //
				.perform(replaceText(vaultnameAndPassword));

		onView(withId(R.id.passwordRetypedEditText)) //
				.perform(replaceText(vaultnameAndPassword), closeSoftKeyboard());

		awaitCompleted();

		onView(withId(R.id.createVaultButton)) //
				.perform(click());
	}

	private void openVault(int vaultPosition) {
		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(vaultPosition, R.id.cloud)) //
						.perform(click());

		awaitCompleted();

		switch (vaultPosition) {
		case WEBDAV:
			awaitCompleted();

			onView(withRecyclerView(R.id.recyclerView) //
					.atPositionOnView(0, R.id.cloudText)) //
							.perform(click());
			break;
		case LOCAL:
			awaitCompleted();

			onView(withRecyclerView(R.id.recyclerView) //
					.atPositionOnView(0, R.id.cloudText)) //
							.perform(click());

			break;
		}

		awaitCompleted();

		openFolder(0);
		awaitCompleted();

		openFolder(0);
		awaitCompleted();

		openFile(1);
	}

	private void unlockVault(String vaultPassword, int vaultPosition) {
		waitForIdle(device);

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(vaultPosition, R.id.vaultName)) //
						.perform(click());

		device //
				.findObject(new UiSelector().text("Password")) //
				.waitForExists(30000L);

		onView(withId(R.id.et_password)) //
				.perform(replaceText(vaultPassword), closeSoftKeyboard());
		onView(withId(android.R.id.button1)) //
				.perform(click());

		awaitCompleted();
	}

	private void changePassword(int position, String oldPassword, String newPassword) {
		awaitCompleted();

		waitForIdle(device);

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(position, R.id.settings)) //
						.perform(click());

		waitForIdle(device);

		onView(withId(R.id.change_password)) //
				.perform(click());

		awaitCompleted();

		onView(withId(R.id.et_old_password)) //
				.perform(replaceText(oldPassword));

		onView(withId(R.id.et_new_password)) //
				.perform(replaceText(newPassword));

		onView(withId(R.id.et_new_retype_password)) //
				.perform(replaceText(newPassword), closeSoftKeyboard());

		onView(withId(android.R.id.button1)) //
				.perform(click());

		awaitCompleted();
	}

	private void checkVault(int position, String name) {
		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(position, R.id.vaultName)) //
						.check(matches(withText(name)));

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(position, R.id.vaultPath)) //
						.check(matches(withText("/0/" + name)));
	}
}
