package org.cryptomator.presentation.ui.activity.suite;

import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

class FailureListener extends RunListener {

	private final RunNotifier runNotifier;

	FailureListener(RunNotifier runNotifier) {
		super();
		this.runNotifier = runNotifier;
	}

	@Override
	public void testFailure(Failure failure) throws Exception {
		super.testFailure(failure);
		this.runNotifier.pleaseStop();
	}
}
