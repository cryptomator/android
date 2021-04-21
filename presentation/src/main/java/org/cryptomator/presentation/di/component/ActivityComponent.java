package org.cryptomator.presentation.di.component;

import android.app.Activity;

import org.cryptomator.domain.di.PerView;
import org.cryptomator.presentation.di.module.ActivityModule;
import org.cryptomator.presentation.ui.activity.AuthenticateCloudActivity;
import org.cryptomator.presentation.ui.activity.AutoUploadChooseVaultActivity;
import org.cryptomator.presentation.ui.activity.BiometricAuthSettingsActivity;
import org.cryptomator.presentation.ui.activity.BrowseFilesActivity;
import org.cryptomator.presentation.ui.activity.ChooseCloudServiceActivity;
import org.cryptomator.presentation.ui.activity.CloudConnectionListActivity;
import org.cryptomator.presentation.ui.activity.CloudSettingsActivity;
import org.cryptomator.presentation.ui.activity.CreateVaultActivity;
import org.cryptomator.presentation.ui.activity.EmptyDirIdFileInfoActivity;
import org.cryptomator.presentation.ui.activity.ImagePreviewActivity;
import org.cryptomator.presentation.ui.activity.LicenseCheckActivity;
import org.cryptomator.presentation.ui.activity.LicensesActivity;
import org.cryptomator.presentation.ui.activity.S3AddOrChangeActivity;
import org.cryptomator.presentation.ui.activity.SetPasswordActivity;
import org.cryptomator.presentation.ui.activity.SettingsActivity;
import org.cryptomator.presentation.ui.activity.SharedFilesActivity;
import org.cryptomator.presentation.ui.activity.SplashActivity;
import org.cryptomator.presentation.ui.activity.TextEditorActivity;
import org.cryptomator.presentation.ui.activity.VaultListActivity;
import org.cryptomator.presentation.ui.activity.WebDavAddOrChangeActivity;
import org.cryptomator.presentation.ui.fragment.AutoUploadChooseVaultFragment;
import org.cryptomator.presentation.ui.fragment.BiometricAuthSettingsFragment;
import org.cryptomator.presentation.ui.fragment.BrowseFilesFragment;
import org.cryptomator.presentation.ui.fragment.ChooseCloudServiceFragment;
import org.cryptomator.presentation.ui.fragment.CloudConnectionListFragment;
import org.cryptomator.presentation.ui.fragment.CloudSettingsFragment;
import org.cryptomator.presentation.ui.fragment.EmptyDirIdFileInfoFragment;
import org.cryptomator.presentation.ui.fragment.ImagePreviewFragment;
import org.cryptomator.presentation.ui.fragment.S3AddOrChangeFragment;
import org.cryptomator.presentation.ui.fragment.SetPasswordFragment;
import org.cryptomator.presentation.ui.fragment.SharedFilesFragment;
import org.cryptomator.presentation.ui.fragment.TextEditorFragment;
import org.cryptomator.presentation.ui.fragment.VaultListFragment;
import org.cryptomator.presentation.ui.fragment.WebDavAddOrChangeFragment;
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow;
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow;

import dagger.Component;

@PerView
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent {

	Activity activity();

	void inject(SplashActivity splashActivity);

	void inject(VaultListActivity vaultListActivity);

	void inject(SetPasswordActivity setPasswordActivity);

	void inject(CreateVaultActivity createVaultActivity);

	void inject(CloudSettingsActivity cloudSettingsActivity);

	void inject(BrowseFilesActivity browseFilesActivity);

	void inject(ChooseCloudServiceActivity chooseCloudServiceActivity);

	void inject(SettingsActivity settingsActivity);

	void inject(LicensesActivity licensesActivity);

	void inject(VaultListFragment vaultListFragment);

	void inject(SetPasswordFragment setPasswordFragment);

	void inject(CloudSettingsFragment cloudSettingsFragment);

	void inject(BrowseFilesFragment browseFilesFragment);

	void inject(ChooseCloudServiceFragment chooseCloudServiceFragment);

	void inject(SharedFilesActivity sharedFilesActivity);

	void inject(SharedFilesFragment sharedFilesFragment);

	void inject(AddExistingVaultWorkflow addExistingVaultWorkflow);

	void inject(CreateNewVaultWorkflow createNewVaultWorkflow);

	void inject(WebDavAddOrChangeActivity webDavAddOrChangeActivity);

	void inject(WebDavAddOrChangeFragment webdavAddOrChangeFragment);

	void inject(CloudConnectionListFragment webDavConnectionListFragment);

	void inject(CloudConnectionListActivity cloudConnectionListActivity);

	void inject(EmptyDirIdFileInfoActivity emptyDirIdFileInfoActivity);

	void inject(EmptyDirIdFileInfoFragment emptyDirIdFileInfoFragment);

	void inject(BiometricAuthSettingsActivity biometricAuthSettingsActivity);

	void inject(BiometricAuthSettingsFragment biometricAuthSettingsFragment);

	void inject(TextEditorActivity textEditorActivity);

	void inject(TextEditorFragment textEditorFragment);

	void inject(AuthenticateCloudActivity authenticateCloudActivity);

	void inject(ImagePreviewActivity imagePreviewActivity);

	void inject(ImagePreviewFragment imagePreviewFragment);

	void inject(AutoUploadChooseVaultActivity autoUploadChooseVaultActivity);

	void inject(AutoUploadChooseVaultFragment autoUploadChooseVaultFragment);

	void inject(LicenseCheckActivity licenseCheckActivity);

	void inject(S3AddOrChangeActivity s3AddOrChangeActivity);

	void inject(S3AddOrChangeFragment s3AddOrChangeFragment);
}
