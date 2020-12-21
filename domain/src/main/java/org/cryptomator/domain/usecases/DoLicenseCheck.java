package org.cryptomator.domain.usecases;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.license.LicenseNotValidException;
import org.cryptomator.domain.exception.license.NoLicenseAvailableException;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import com.google.common.io.BaseEncoding;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@UseCase
public class DoLicenseCheck {

	private final UpdateCheckRepository updateCheckRepository;
	private String license;

	DoLicenseCheck(final UpdateCheckRepository updateCheckRepository, @Parameter final String license) {
		this.updateCheckRepository = updateCheckRepository;
		this.license = license;
	}

	public LicenseCheck execute() throws BackendException {
		license = useLicenseOrRetrieveFromDb(license);

		try {
			final Claims claims = Jwts //
					.parserBuilder().setSigningKey(getPublicKey()) //
					.build().parseClaimsJws(license) //
					.getBody();

			return claims::getSubject;
		} catch (JwtException | FatalBackendException e) {
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

	private ECPublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] publicKey = BaseEncoding //
				.base64() //
				.decode("MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBcnb81CfNeL3qBVFMx/yRfm1Y1yib" + //
						"ajIJkV1s82AQt+mOl4+Kub64wq1OCgBVwWUlKwqgnyF39nmkoXEjakRPFngBzg2J" + //
						"zo4UR0B7OYmn0uGf3K+zQfxKnNMxGVPtlzE8j9Nqz/dm2YvYLLVwvTSDQX/GaxoP" + //
						"/EH84Hupw2wuU7qAaFU=");

		Key key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKey));
		if (key instanceof ECPublicKey) {
			return (ECPublicKey) key;
		} else {
			throw new FatalBackendException("Key not an EC public key.");
		}
	}
}
