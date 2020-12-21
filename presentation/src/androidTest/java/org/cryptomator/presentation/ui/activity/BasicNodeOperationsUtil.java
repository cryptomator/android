package org.cryptomator.presentation.ui.activity;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.cryptomator.presentation.R;
import org.cryptomator.presentation.ui.RecyclerViewMatcher;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static java.lang.String.format;
import static org.cryptomator.domain.executor.BackgroundTasks.awaitCompleted;
import static org.hamcrest.Matchers.allOf;

public class BasicNodeOperationsUtil {

	static void openSettings(UiDevice device, int nodePosition) {
		awaitCompleted();

		waitForIdle(device);

		onView(withRecyclerView(R.id.recyclerView) //
				.atPositionOnView(nodePosition, R.id.settings)) //
						.perform(click());

		awaitCompleted();
	}

	static void waitForIdle(UiDevice uiDevice) {
		uiDevice.waitForIdle();
	}

	static void checkEmptyFolderHint() {
		awaitCompleted();

		onView(allOf( //
				withId(R.id.tv_empty_folder_hint), //
				withText(R.string.screen_file_browser_msg_empty_folder))) //
						.check(matches(withText(R.string.screen_file_browser_msg_empty_folder)));
	}

	static void checkFileOrFolderAlreadyExistsErrorMessage(String nodeName) {
		onView(withId(R.id.tv_error)) //
				.check(matches(withText(format( //
						InstrumentationRegistry //
								.getTargetContext() //
								.getString(R.string.error_file_or_folder_exists), //
						nodeName))));
	}

	public static RecyclerViewMatcher withRecyclerView(final int recyclerViewId) {
		return new RecyclerViewMatcher(recyclerViewId);
	}
}
