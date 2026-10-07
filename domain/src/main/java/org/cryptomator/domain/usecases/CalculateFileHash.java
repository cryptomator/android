package org.cryptomator.domain.usecases;

import android.content.Context;
import android.net.Uri;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@UseCase
public class CalculateFileHash {

	private final Context context;
	private final Uri uri;

	CalculateFileHash(final Context context, @Parameter Uri uri) {
		this.context = context;
		this.uri = uri;
	}

	public byte[] execute() throws BackendException, FileNotFoundException {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			try (InputStream inputStream = context.getContentResolver().openInputStream(uri); //
				 DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
				byte[] buffer = new byte[4096];
				while (dis.read(buffer) != -1) {
				}
				return digest.digest();
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

}
