package org.cryptomator.domain.usecases;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.io.BaseEncoding;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.license.DesktopSupporterCertificateException;
import org.cryptomator.domain.exception.license.LicenseNotValidException;
import org.cryptomator.domain.exception.license.NoLicenseAvailableException;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.SharedPreferencesHandler;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

@UseCase
public class DoLicenseCheck {

	private static final String ANDROID_PUB_KEY = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBcnb81CfNeL3qBVFMx/yRfm1Y1yib" + //
			"ajIJkV1s82AQt+mOl4+Kub64wq1OCgBVwWUlKwqgnyF39nmkoXEjakRPFngBzg2J" + //
			"zo4UR0B7OYmn0uGf3K+zQfxKnNMxGVPtlzE8j9Nqz/dm2YvYLLVwvTSDQX/GaxoP" + //
			"/EH84Hupw2wuU7qAaFU=";
	private static final String DESKTOP_SUPPORTER_CERTIFICATE_PUB_KEY = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQB7NfnqiZbg2KTmoflmZ71PbXru7oW" + //
			"fmnV2yv3eDjlDfGruBrqz9TtXBZV/eYWt31xu1osIqaT12lKBvZ511aaAkIBeOEV" + //
			"gwcBIlJr6kUw7NKzeJt7r2rrsOyQoOG2nWc/Of/NBqA3mIZRHk5Aq1YupFdD26QE" + //
			"r0DzRyj4ixPIt38CQB8=";
	private final SharedPreferencesHandler sharedPreferencesHandler;
	private String license;

	/**
	 * Creates a new DoLicenseCheck use case with the given shared-preferences handler and an initial license token.
	 *
	 * @param license the incoming license token to verify (may be empty, in which case a stored token will be retrieved)
	 */
	DoLicenseCheck(final SharedPreferencesHandler sharedPreferencesHandler, @Parameter final String license) {
		this.sharedPreferencesHandler = sharedPreferencesHandler;
		this.license = license;
	}

	/**
	 * Verify the provided or stored license JWT, persist it if valid, and expose its subject.
	 *
	 * @return a {@link LicenseCheck} that yields the JWT subject string when invoked
	 * @throws DesktopSupporterCertificateException if the token's signature is invalid but verifies with the desktop supporter certificate
	 * @throws LicenseNotValidException             if the token is invalid or its signature cannot be verified
	 * @throws FatalBackendException                if a cryptographic error occurs while obtaining the public key
	 */
	public LicenseCheck execute() throws BackendException {
		license = useLicenseOrRetrieveFromPreferences(license);
		try {
			Algorithm algorithm = Algorithm.ECDSA512(getPublicKey(ANDROID_PUB_KEY), null);
			JWTVerifier verifier = JWT.require(algorithm).build();
			DecodedJWT jwt = verifier.verify(license);
			sharedPreferencesHandler.setLicenseToken(license);
			return jwt::getSubject;
		} catch (SignatureVerificationException | JWTDecodeException | FatalBackendException e) {
			if (e instanceof SignatureVerificationException && isDesktopSupporterCertificate(license)) {
				throw new DesktopSupporterCertificateException(license);
			}
			throw new LicenseNotValidException(license);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new FatalBackendException(e);
		}
	}

	/**
	 * Ensure a non-empty license token is available by returning the provided license or, if empty,
	 * the token stored in shared preferences.
	 *
	 * @param license the incoming license token; if empty the method will attempt to read a stored token
	 * @return the non-empty license token
	 * @throws NoLicenseAvailableException if both the provided license and the stored token are empty
	 */
	private String useLicenseOrRetrieveFromPreferences(String license) throws NoLicenseAvailableException {
		if (!license.isEmpty()) {
			return license;
		} else {
			license = sharedPreferencesHandler.licenseToken();
			if (license.isEmpty()) {
				throw new NoLicenseAvailableException();
			}
		}
		return license;
	}

	private ECPublicKey getPublicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
		final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(BaseEncoding.base64().decode(publicKey));
		Key key = KeyFactory.getInstance("EC").generatePublic(keySpec);
		if (key instanceof ECPublicKey) {
			return (ECPublicKey) key;
		} else {
			throw new FatalBackendException("Key not an EC public key.");
		}
	}

	private boolean isDesktopSupporterCertificate(String license) {
		try {
			Algorithm algorithm = Algorithm.ECDSA512(getPublicKey(DESKTOP_SUPPORTER_CERTIFICATE_PUB_KEY), null);
			JWTVerifier verifier = JWT.require(algorithm).build();
			verifier.verify(license);
			return true;
		} catch (SignatureVerificationException | NoSuchAlgorithmException | InvalidKeySpecException e) {
			return false;
		}
	}
}
