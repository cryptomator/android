package org.cryptomator.domain.usecases.cloud;

import android.content.Context;
import android.net.Uri;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies a document of the documents provider to a file in shared storage, where a Microsoft app can save it, and hashes
 * it on the way, so that {@link ImportMicrosoftWorkaroundCopy} can tell whether the app changed it.
 */
@UseCase
class PrepareMicrosoftWorkaroundCopy {

	private final Context context;
	private final Uri document;
	private final File target;

	public PrepareMicrosoftWorkaroundCopy(Context context, @Parameter Uri document, @Parameter File target) {
		this.context = context;
		this.document = document;
		this.target = target;
	}

	/**
	 * @return the hash of the copy
	 */
	public byte[] execute() throws BackendException {
		boolean copied = false;
		try {
			byte[] hash = copy();
			copied = true;
			return hash;
		} catch (IOException e) {
			throw new FatalBackendException(e);
		} finally {
			if (!copied) {
				// the document is plaintext, so no part of it stays behind
				MicrosoftWorkaroundCopies.delete(target);
			}
		}
	}

	private byte[] copy() throws IOException {
		try (InputStream in = context.getContentResolver().openInputStream(document)) {
			if (in == null) {
				throw new FileNotFoundException(document.toString());
			}
			try (OutputStream out = MicrosoftWorkaroundCopies.create(target)) {
				return MicrosoftWorkaroundCopies.copy(in, out);
			}
		}
	}
}
