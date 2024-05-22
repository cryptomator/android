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
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

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
	private final UpdateCheckRepository updateCheckRepository;
	private String license;

	DoLicenseCheck(final UpdateCheckRepository updateCheckRepository, @Parameter final String license) {
		this.updateCheckRepository = updateCheckRepository;
		this.license = license;
	}

	public LicenseCheck execute() throws BackendException {
		license = useLicenseOrRetrieveFromDb(license);
		try {
			Algorithm algorithm = Algorithm.ECDSA512(getPublicKey(ANDROID_PUB_KEY), null);
			JWTVerifier verifier = JWT.require(algorithm).build();
			DecodedJWT jwt = verifier.verify(license);
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

	private String useLicenseOrRetrieveFromDb(String license) throws NoLicenseAvailableException {
		if (!license.isEmpty()) {
			updateCheckRepository.setLicense(license);
		} else {
			license = updateCheckRepository.getLicense();
			if (license == null) {
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
