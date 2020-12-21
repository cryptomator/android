package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.ui.activity.EmptyDirIdFileInfoActivity;

@Intent(EmptyDirIdFileInfoActivity.class)
public interface EmptyDirIdFileInfoIntent {

	String dirName();

	String dirFilePath();

}
