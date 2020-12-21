package org.cryptomator.presentation.testCloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.presentation.di.component.ApplicationComponent;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

public class LocalTestCloud extends TestCloud {
	@Override
	public Cloud getInstance(ApplicationComponent appComponent) {
		getInstrumentation() //
				.getUiAutomation() //
				.executeShellCommand("pm grant " + "org.cryptomator" + " android.permission.READ_EXTERNAL_STORAGE");

		getInstrumentation() //
				.getUiAutomation() //
				.executeShellCommand("pm grant " + "org.cryptomator" + " android.permission.WRITE_EXTERNAL_STORAGE");

		return LocalStorageCloud.aLocalStorage().build();
	}

	@Override
	public String toString() {
		return "LocalTestCloud";
	}
}
