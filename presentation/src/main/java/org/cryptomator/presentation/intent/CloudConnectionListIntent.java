package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.CloudTypeModel;
import org.cryptomator.presentation.ui.activity.CloudConnectionListActivity;

@Intent(CloudConnectionListActivity.class)
public interface CloudConnectionListIntent {

	CloudTypeModel cloudType();

	String dialogTitle();

	Boolean finishOnCloudItemClick();

	@Optional
	Boolean includeWebDavProviderClouds();

}
