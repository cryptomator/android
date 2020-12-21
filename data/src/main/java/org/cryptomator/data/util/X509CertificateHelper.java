package org.cryptomator.data.util;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

public class X509CertificateHelper {

	private static final String CERT_BEGIN = "-----BEGIN CERTIFICATE-----\n";
	private static final String CERT_END = "-----END CERTIFICATE-----";

	public static String convertToPem(X509Certificate cert) throws CertificateEncodingException {
		String pemCertPre = Base64.encodeToString(cert.getEncoded(), Base64.DEFAULT);
		return CERT_BEGIN + pemCertPre + CERT_END;
	}

	public static X509Certificate convertFromPem(String pem) throws CertificateException {
		byte[] decoded = Base64 //
				.decode(pem.replaceAll(CERT_BEGIN, "").replaceAll(CERT_END, ""), Base64.DEFAULT);

		return (X509Certificate) CertificateFactory //
				.getInstance("X.509") //
				.generateCertificate(new ByteArrayInputStream(decoded));
	}

	public static String getFingerprintFormatted(X509Certificate certificate) throws CertificateEncodingException {
		String hash = new String(Hex.encodeHex(DigestUtils.sha1(certificate.getEncoded()))) //
				.toUpperCase() //
				.replaceAll("(.{2})", "$1:");
		hash = hash.substring(0, hash.length() - 1);
		return "SHA-256 " + hash;
	}

}
