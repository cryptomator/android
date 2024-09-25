package org.cryptomator.util;

import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.cryptomator.util.crypto.CryptoMode;
import org.cryptomator.util.crypto.KeyStoreBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Arrays;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
@SmallTest
public class KeyStoreBuilderTest {

	private final CryptoMode cryptoMode;
	private Context context;

	public KeyStoreBuilderTest(CryptoMode cryptoMode) {
		this.cryptoMode = cryptoMode;
	}

	@Parameterized.Parameters
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][] {{CryptoMode.GCM}, {CryptoMode.CBC}});
	}

	@Before
	public void setup() {
		context = InstrumentationRegistry.getInstrumentation().getContext();
	}

	@Test
	public void testAKeyStoreWithKeyLeadsToKeyInKeyStore() throws KeyStoreException {
		String webdavKey = "webdavKey";
		KeyStore inTestKeyStore = KeyStoreBuilder //
				.defaultKeyStore() //
				.withKey(webdavKey, false, cryptoMode, context) //
				.build();

		assertThat(inTestKeyStore.containsAlias(webdavKey), is(true));

		inTestKeyStore.deleteEntry(webdavKey);

		assertThat(inTestKeyStore.containsAlias(webdavKey), is(false));
	}
}
