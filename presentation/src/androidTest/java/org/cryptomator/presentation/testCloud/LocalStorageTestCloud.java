package org.cryptomator.presentation.testCloud;

import androidx.test.uiautomator.UiDevice;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.presentation.R;
import org.cryptomator.presentation.di.component.ApplicationComponent;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.cryptomator.presentation.ui.TestUtil.LOCAL;
import static org.cryptomator.presentation.ui.TestUtil.chooseSdCard;
import static org.cryptomator.presentation.ui.activity.CloudsOperationsTest.openCloudServices;
import static org.cryptomator.presentation.ui.activity.LoginLocalClouds.chooseFolder;

public class LocalStorageTestCloud extends TestCloud {
	@Override
	public Cloud getInstance(ApplicationComponent appComponent) {
		login();
		try {
			return appComponent.cloudRepository() //
					.clouds(CloudType.LOCAL).stream() //
					.map(LocalStorageCloud.class::cast) //
					.filter(cloud -> cloud.rootUri() != null) //
					.findFirst() //
					.get();
		} catch (BackendException e) {
			throw new RuntimeException(e);
		}
	}

	private void login() {
		UiDevice device = UiDevice.getInstance(getInstrumentation());

		openCloudServices(device);

		onView(withId(R.id.recyclerView)) //
				.perform(actionOnItemAtPosition(LOCAL, click()));
		awaitCompleted();

		onView(withId(R.id.floating_action_button)) //
				.perform(click());
		awaitCompleted();

		chooseSdCard(device);
		awaitCompleted();

		chooseFolder(device);
	}

	@Override
	public String toString() {
		return "LocalStorageTestCloud";
	}
}
