package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.CloudFolderModel;
import org.cryptomator.presentation.ui.activity.BrowseFilesActivity;

@Intent(BrowseFilesActivity.class)
public interface BrowseFilesIntent {

	CloudFolderModel folder();

	@Optional
	String title();
	@Optional
	Long vaultId();

	@Optional
	ChooseCloudNodeSettings chooseCloudNodeSettings();

}
