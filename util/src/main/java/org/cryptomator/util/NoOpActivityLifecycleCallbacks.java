package org.cryptomator.util;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import org.jetbrains.annotations.NotNull;

public class NoOpActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

	@Override
	public void onActivityCreated(@NotNull Activity activity, Bundle savedInstanceState) {

	}

	@Override
	public void onActivityStarted(@NotNull Activity activity) {

	}

	@Override
	public void onActivityResumed(@NotNull Activity activity) {

	}

	@Override
	public void onActivityPaused(@NotNull Activity activity) {

	}

	@Override
	public void onActivityStopped(@NotNull Activity activity) {

	}

	@Override
	public void onActivitySaveInstanceState(@NotNull Activity activity, @NotNull Bundle outState) {

	}

	@Override
	public void onActivityDestroyed(@NotNull Activity activity) {

	}
}
