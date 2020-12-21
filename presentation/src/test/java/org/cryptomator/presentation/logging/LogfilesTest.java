package org.cryptomator.presentation.logging;

import org.junit.jupiter.api.Test;

import static org.cryptomator.presentation.logging.Logfiles.NUMBER_OF_LOGFILES;
import static org.cryptomator.presentation.logging.Logfiles.ROTATION_FILE_SIZE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class LogfilesTest {

	@Test
	public void testRotationFileSizeIsNotTooSmall() {
		assertThat(ROTATION_FILE_SIZE, is(greaterThanOrEqualTo(64L << 10)));
	}

	@Test
	public void testAtLeastTwoLogfilesAreUsed() {
		assertThat(NUMBER_OF_LOGFILES, is(greaterThanOrEqualTo(2)));
	}

}
