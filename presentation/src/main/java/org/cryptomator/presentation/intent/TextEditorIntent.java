package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.ui.activity.TextEditorActivity;

@Intent(TextEditorActivity.class)
public interface TextEditorIntent {

	CloudFileModel textFile();

}
