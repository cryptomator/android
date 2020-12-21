package org.cryptomator.presentation.testCloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.presentation.di.component.ApplicationComponent;

public abstract class TestCloud {
	public abstract Cloud getInstance(ApplicationComponent appComponent);
}
