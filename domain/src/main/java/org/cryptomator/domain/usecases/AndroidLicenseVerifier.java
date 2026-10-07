package org.cryptomator.domain.usecases;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.common.io.BaseEncoding;

import org.cryptomator.domain.exception.FatalBackendException;

import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public final class AndroidLicenseVerifier {

	public static final String ANDROID_PUB_KEY = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBcnb81CfNeL3qBVFMx/yRfm1Y1yib" + //
			"ajIJkV1s82AQt+mOl4+Kub64wq1OCgBVwWUlKwqgnyF39nmkoXEjakRPFngBzg2J" + //
			"zo4UR0B7OYmn0uGf3K+zQfxKnNMxGVPtlzE8j9Nqz/dm2YvYLLVwvTSDQX/GaxoP" + //
			"/EH84Hupw2wuU7qAaFU=";

	private AndroidLicenseVerifier() {
	}

	public static DecodedJWT verify(String license, String base64PublicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
		Algorithm algorithm = Algorithm.ECDSA512(getPublicKey(base64PublicKey), null);
		JWTVerifier verifier = JWT.require(algorithm).build();
		return verifier.verify(license);
	}

	private static ECPublicKey getPublicKey(String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
		final X509EncodedKeySpec keySpec = new X509EncodedKeySpec(BaseEncoding.base64().decode(publicKey));
		Key key = KeyFactory.getInstance("EC").generatePublic(keySpec);
		if (key instanceof ECPublicKey) {
			return (ECPublicKey) key;
		} else {
			throw new FatalBackendException("Key not an EC public key.");
		}
	}
}
