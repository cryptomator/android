package org.cryptomator.presentation.ui.activity.suite;

import org.cryptomator.presentation.ui.activity.CloudsOperationsTest;
import org.cryptomator.presentation.ui.activity.FileOperationsTest;
import org.cryptomator.presentation.ui.activity.FolderOperationsTest;
import org.cryptomator.presentation.ui.activity.VaultsOperationsTest;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@Ignore
@RunWith(StopOnFailureSuite.class)
@Suite.SuiteClasses({ //
		CloudsOperationsTest.class, //
		VaultsOperationsTest.class, //
		FolderOperationsTest.class, //
		FileOperationsTest.class //
})
public class UiTestSuite {
}
