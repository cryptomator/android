package org.cryptomator.domain.usecases.cloud;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The copies of documents in shared storage that Microsoft apps edit. Cryptomator puts each in a folder of its own,
 * so that a copy whose changes aren't in the vault yet isn't overwritten by the next copy of a file with that name.
 */
public class MicrosoftWorkaroundCopies {

	private MicrosoftWorkaroundCopies() {
	}

	/**
	 * Deletes the copy, and its folder if nothing else is left in it.
	 */
	public static void delete(File copy) {
		copy.delete();
		File folder = copy.getParentFile();
		if (folder != null) {
			folder.delete();
		}
	}

	static byte[] hash(File copy) throws IOException {
		try (InputStream in = open(copy)) {
			return copy(in, null);
		}
	}

	static InputStream open(File copy) throws FileNotFoundException {
		try {
			return new FileInputStream(copy);
		} catch (FileNotFoundException e) {
			throw notFound(copy);
		}
	}

	static OutputStream create(File copy) throws FileNotFoundException {
		try {
			return new FileOutputStream(copy);
		} catch (FileNotFoundException e) {
			throw notFound(copy);
		}
	}

	/**
	 * Names the copy by its folder, since the message ends up in the log, which must not reveal the name of a file in a
	 * vault.
	 */
	static FileNotFoundException notFound(File copy) {
		File folder = copy.getParentFile();
		return new FileNotFoundException("Copy " + (folder != null ? folder.getName() : "") + " not found");
	}

	/**
	 * @param out where to write what is read, or null to only hash it
	 * @return the hash of what was read from in
	 */
	static byte[] copy(InputStream in, OutputStream out) throws IOException {
		MessageDigest digest = digest();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			digest.update(buffer, 0, read);
			if (out != null) {
				out.write(buffer, 0, read);
			}
		}
		return digest.digest();
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
