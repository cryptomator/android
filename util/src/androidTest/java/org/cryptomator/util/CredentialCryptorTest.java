package org.cryptomator.util;

import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;

import org.cryptomator.util.crypto.CredentialCryptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4ClassRunner.class)
@SmallTest
public class CredentialCryptorTest {

	private final byte[] decrypted = "lalala".getBytes();

	private Context context;

	@Before
	public void setup() {
		context = InstrumentationRegistry.getInstrumentation().getContext();
	}

	@Test
	public void testEncryptAndDecryptLeadsToSameDecryptedData() {
		CredentialCryptor credentialCryptor = CredentialCryptor.getInstance(context);
		byte[] encrypted = credentialCryptor.encrypt(decrypted);

		assertThat(decrypted, is(credentialCryptor.decrypt(encrypted)));

		encrypted = credentialCryptor.encrypt(decrypted);

		assertThat(decrypted, is(credentialCryptor.decrypt(encrypted)));
	}
}
