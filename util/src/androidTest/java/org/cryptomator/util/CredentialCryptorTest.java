package org.cryptomator.util;

import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.cryptomator.util.crypto.CredentialCryptor;
import org.cryptomator.util.crypto.CryptoMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
@SmallTest
public class CredentialCryptorTest {

	private final byte[] decrypted = "lalala".getBytes();
	private final CryptoMode cryptoMode;

	private Context context;

	public CredentialCryptorTest(CryptoMode cryptoMode) {
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
	public void testEncryptAndDecryptLeadsToSameDecryptedData() {
		CredentialCryptor credentialCryptor = CredentialCryptor.getInstance(context, cryptoMode);
		byte[] encrypted = credentialCryptor.encrypt(decrypted);

		assertThat(decrypted, is(credentialCryptor.decrypt(encrypted)));

		encrypted = credentialCryptor.encrypt(decrypted);

		assertThat(decrypted, is(credentialCryptor.decrypt(encrypted)));
	}
}
