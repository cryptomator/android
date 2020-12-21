package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.ui.activity.ChooseCloudServiceActivity;

@Intent(ChooseCloudServiceActivity.class)
public interface ChooseCloudServiceIntent {

	String CHOSEN_CLOUD_SERVICE = "chosenCloudService";

	String subtitle();

}
