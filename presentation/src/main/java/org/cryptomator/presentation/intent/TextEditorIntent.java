package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.ui.activity.TextEditorActivity;

@Intent(TextEditorActivity.class)
public interface TextEditorIntent {

	/**
 * Provides the cloud file to be opened by the text editor.
 *
 * @return the CloudFileModel representing the text file to open
 */
CloudFileModel textFile();

	/**
	 * Indicates whether hub write access is allowed for the target text file.
	 *
	 * @return `true` if hub write access is allowed, `false` if not, or `null` if the value is absent from the intent
	 */
	@Optional
	Boolean hubWriteAllowed();

}
