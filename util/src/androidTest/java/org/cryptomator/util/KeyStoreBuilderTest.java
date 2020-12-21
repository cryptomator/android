package org.cryptomator.util;

import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import org.cryptomator.util.crypto.KeyStoreBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStore;
import java.security.KeyStoreException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4ClassRunner.class)
@SmallTest
public class KeyStoreBuilderTest {

	private Context context;

	@Before
	public void setup() {
		context = InstrumentationRegistry.getInstrumentation().getContext();
	}

	@Test
	public void testAKeyStoreWithKeyLeadsToKeyInKeyStore() throws KeyStoreException {
		String webdavKey = "webdavKey";
		KeyStore inTestKeyStore = KeyStoreBuilder //
				.defaultKeyStore() //
				.withKey(webdavKey, false, context) //
				.build();

		assertThat(inTestKeyStore.containsAlias(webdavKey), is(true));

		inTestKeyStore.deleteEntry(webdavKey);

		assertThat(inTestKeyStore.containsAlias(webdavKey), is(false));
	}
}
