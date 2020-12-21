package org.cryptomator.util;

import androidx.test.filters.SmallTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4ClassRunner.class)
@SmallTest
public class SharedPreferencesHandlerTest {

	private org.cryptomator.util.SharedPreferencesHandler inTest;

	@Before
	public void setUp() {
		inTest = new org.cryptomator.util.SharedPreferencesHandler(InstrumentationRegistry.getInstrumentation().getContext());
		inTest.removeAllEntries();
	}

	@Test
	public void testIsScreenLockDialogAlreadyShownWithoutSetLeadsToFalse() {
		assertThat(inTest.isScreenLockDialogAlreadyShown(), is(false));
	}

	@Test
	public void testFingerprintDialogAlreadyShownWithSetLeadsToTrue() {
		inTest.setScreenLockDialogAlreadyShown();
		assertThat(inTest.isScreenLockDialogAlreadyShown(), is(true));
	}

	@Test
	public void testDefaultLockTimeout() {
		assertThat(inTest.getLockTimeout(), is(LockTimeout.ONE_MINUTE));
	}

	@Test
	public void testLockTimeoutOnChange() {
		inTest.addLockTimeoutChangedListener(value -> assertThat(value, is(LockTimeout.ONE_MINUTE)));
	}

}
