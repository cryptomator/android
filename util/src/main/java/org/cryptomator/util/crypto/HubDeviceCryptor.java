package org.cryptomator.util.crypto;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.PasswordBasedDecrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import org.cryptomator.cryptolib.api.CryptoException;
import org.cryptomator.cryptolib.api.Masterkey;
import org.cryptomator.cryptolib.api.MasterkeyLoadingFailedException;
import org.cryptomator.cryptolib.common.MessageDigestSupplier;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;

import timber.log.Timber;

public class HubDeviceCryptor {

	static final String DEFAULT_KEYSTORE_NAME = "AndroidKeyStore";
	static final String DEFAULT_KEY_ALIAS = "hubDeviceKey";
	private static final String JWE_PAYLOAD_KEY_FIELD = "key";
	private static final String EC_ALG = "EC";
	private final KeyStore keyStore;

	HubDeviceCryptor(KeyStore keyStore) {
		try {
			this.keyStore = keyStore;
			this.keyStore.load(null);
			if (!this.keyStore.containsAlias(DEFAULT_KEY_ALIAS)) {
				var keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, DEFAULT_KEYSTORE_NAME);
				var parameterSpec = new KeyGenParameterSpec //
						.Builder(DEFAULT_KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_AGREE_KEY) //
						.setAlgorithmParameterSpec(new ECGenParameterSpec("secp384r1")) //
						.setDigests(KeyProperties.DIGEST_SHA256) //
						.setUserAuthenticationRequired(false) //
						.build();
				keyPairGenerator.initialize(parameterSpec);
				keyPairGenerator.generateKeyPair();
			}
		} catch (KeyStoreException | NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | CertificateException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static HubDeviceCryptor getInstance() {
		try {
			var keyStore = KeyStore.getInstance(DEFAULT_KEYSTORE_NAME);
			return new HubDeviceCryptor(keyStore);
		} catch (KeyStoreException e) {
			throw new RuntimeException(e);
		}
	}

	public static ECPrivateKey decryptUserKey(JWEObject jwe, String setupCode) throws InvalidJweKeyException {
		try {
			jwe.decrypt(new PasswordBasedDecrypter(setupCode));
			return readKey(jwe, JWE_PAYLOAD_KEY_FIELD, HubDeviceCryptor::decodeECPrivateKey);
		} catch (JOSEException e) {
			throw new InvalidJweKeyException(e);
		}
	}

	public static ECPrivateKey decryptUserKey(JWEObject jwe, PrivateKey deviceKey) {
		try {
			jwe.decrypt(new ECDHDecrypter(deviceKey, null, Curve.P_384));
			return readKey(jwe, JWE_PAYLOAD_KEY_FIELD, HubDeviceCryptor::decodeECPrivateKey);
		} catch (JOSEException e) {
			throw new InvalidJweKeyException(e);
		}
	}

	private static ECPrivateKey decodeECPrivateKey(byte[] encoded) throws KeyDecodeFailedException {
		try {
			var factory = KeyFactory.getInstance(EC_ALG);
			var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
			if (privateKey instanceof ECPrivateKey ecPrivateKey) {
				return ecPrivateKey;
			} else {
				throw new IllegalStateException(EC_ALG + " key factory not generating ECPrivateKeys");
			}
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(EC_ALG + " not supported");
		} catch (InvalidKeySpecException e) {
			throw new KeyDecodeFailedException(e);
		}
	}

	private static <T> T readKey(JWEObject jwe, String keyField, Function<byte[], T> rawKeyFactory) throws MasterkeyLoadingFailedException {
		Preconditions.checkArgument(jwe.getState() == JWEObject.State.DECRYPTED);
		var fields = jwe.getPayload().toJSONObject();
		if (fields == null) {
			Timber.tag("HubDeviceCryptor").e("Expected JWE payload to be JSON: " + jwe.getPayload());
			throw new MasterkeyLoadingFailedException("Expected JWE payload to be JSON");
		}
		var keyBytes = new byte[0];
		try {
			if (fields.get(keyField) instanceof String key) {
				keyBytes = Base64.getDecoder().decode(key);
				return rawKeyFactory.apply(keyBytes);
			} else {
				throw new IllegalArgumentException("JWE payload doesn't contain field " + keyField);
			}
		} catch (IllegalArgumentException | KeyDecodeFailedException e) {
			Timber.tag("HubDeviceCryptor").e("Unexpected JWE payload: " + jwe.getPayload());
			throw new MasterkeyLoadingFailedException("Unexpected JWE payload", e);
		} finally {
			Arrays.fill(keyBytes, (byte) 0x00);
		}
	}

	private static JWEObject encryptKey(Key key, ECPublicKey userKey) {
		try {
			var encodedVaultKey = Base64.getEncoder().encodeToString(key.getEncoded());
			var keyGen = new ECKeyGenerator(Curve.P_384);
			var ephemeralKeyPair = keyGen.generate();
			var header = new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM).ephemeralPublicKey(ephemeralKeyPair.toPublicJWK()).build();
			var payload = new Payload(Map.of(JWE_PAYLOAD_KEY_FIELD, encodedVaultKey));
			var jwe = new JWEObject(header, payload);
			jwe.encrypt(new ECDHEncrypter(userKey));
			return jwe;
		} catch (JOSEException e) {
			throw new RuntimeException(e);
		}
	}

	public static JWEObject encryptUserKey(ECPrivateKey userKey, ECPublicKey deviceKey) {
		return encryptKey(userKey, deviceKey);
	}

	public static Masterkey decryptVaultKey(JWEObject jwe, ECPrivateKey privateKey) throws InvalidJweKeyException {
		try {
			jwe.decrypt(new ECDHDecrypter(privateKey));
			return readKey(jwe, JWE_PAYLOAD_KEY_FIELD, Masterkey::new);
		} catch (JOSEException e) {
			throw new InvalidJweKeyException(e);
		}
	}

	public JWEObject encryptUserKey(JWEObject userKey, String setupCode) {
		var userPrivateKey = decryptUserKey(userKey, setupCode);
		var devicePublicKey = getDevicePublicKey();
		return encryptUserKey(userPrivateKey, devicePublicKey);
	}

	public Masterkey decryptVaultKey(JWEObject vaultKeyJwe, JWEObject userKeyJwe) {
		try {
			var privateKey = (PrivateKey) keyStore.getKey(DEFAULT_KEY_ALIAS, null);
			var userKey = decryptUserKey(userKeyJwe, privateKey);
			return decryptVaultKey(vaultKeyJwe, userKey);
		} catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
			throw new RuntimeException(e);
		}
	}

	public ECPublicKey getDevicePublicKey() {
		try {
			var certificate = keyStore.getCertificate(DEFAULT_KEY_ALIAS);
			return (ECPublicKey) certificate.getPublicKey();
		} catch (KeyStoreException e) {
			throw new RuntimeException(e);
		}
	}

	public String getDeviceId() {
		var devicePublicKey = getDevicePublicKey();
		try (var instance = MessageDigestSupplier.SHA256.instance()) {
			var hashedKey = instance.get().digest(devicePublicKey.getEncoded());
			return BaseEncoding.base16().encode(hashedKey);
		}
	}

	public static class KeyDecodeFailedException extends CryptoException {

		public KeyDecodeFailedException(Throwable cause) {
			super("Malformed key", cause);
		}
	}

	public static class InvalidJweKeyException extends CryptoException {

		public InvalidJweKeyException(Throwable cause) {
			super("Invalid key", cause);
		}

	}

}
