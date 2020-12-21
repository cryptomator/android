package org.cryptomator.presentation.logging;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class LogRotatorTest {

	private final LogRotator inTest;
	private final Context context;

	public LogRotatorTest() {
		this.context = InstrumentationRegistry.getTargetContext();
		this.inTest = new LogRotator(context);
	}

	@Test
	public void testRotationLeadsToChangedFile() throws InterruptedException {
		int indexBefore = indexOfNewestLogfile(Logfiles.logfiles(context));
		byte[] testData = new byte[(int) Logfiles.ROTATION_FILE_SIZE];

		inTest.log(Arrays.toString(testData));
		sleep(500);
		inTest.log("Hello World!");

		int indexAfter = indexOfNewestLogfile(Logfiles.logfiles(context));
		assertThat(indexBefore, not(indexAfter));
	}

	private int indexOfNewestLogfile(List<File> logfiles) {
		int index = 0;
		long newestLastModified = 0L;
		for (int i = 0; i < logfiles.size(); i++) {
			long lastModified = logfiles.get(i).lastModified();
			if (lastModified > newestLastModified) {
				index = i;
				newestLastModified = lastModified;
			}
		}
		return index;
	}
}
