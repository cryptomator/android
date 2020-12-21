package org.cryptomator.presentation.ui.activity.suite;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

public class StopOnFailureSuite extends Suite {

	public StopOnFailureSuite(Class<?> klass, Class<?>[] suiteClasses) throws InitializationError {
		super(klass, suiteClasses);
	}

	public StopOnFailureSuite(Class<?> klass) throws InitializationError {
		super(klass, klass.getAnnotation(Suite.SuiteClasses.class).value());
	}

	@Override
	public void run(RunNotifier runNotifier) {
		runNotifier.addListener(new FailureListener(runNotifier));
		super.run(runNotifier);
	}
}
