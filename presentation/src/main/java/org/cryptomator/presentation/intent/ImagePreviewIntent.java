package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.ui.activity.ImagePreviewActivity;

@Intent(ImagePreviewActivity.class)
public interface ImagePreviewIntent {

	String withImagePreviewFiles();

}
