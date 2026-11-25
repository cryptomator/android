package org.cryptomator.domain.usecases.cloud;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Brings what a Microsoft app saved into the copy of {@link PrepareMicrosoftWorkaroundCopy} back into the vault. It
 * saves the copy to the document the way an app that edits it through the documents provider does, so the save is
 * based on the version pinned by {@link StartExternalEditing} and becomes a conflict copy if the file changed in the
 * cloud meanwhile.
 * <p>
 * Until the copy is saved, it is the only place the changes are. So this deletes it only once it is unchanged or
 * saved. A copy that couldn't be saved stays for the user until it is cleaned up as an old file.
 */
@UseCase
class ImportMicrosoftWorkaroundCopy {

	/**
	 * Some apps are still writing the file when the user is back, see
	 * https://community.cryptomator.org/t/android-tabelle-nach-upload-unlesbar/6550
	 */
	static final long SETTLE_MILLIS = 500;

	private final Context context;
	private final File copy;
	private final byte[] hashWhenOpened;
	private final Uri document;

	public ImportMicrosoftWorkaroundCopy(Context context, @Parameter File copy, @Parameter byte[] hashWhenOpened, @Parameter Uri document) {
		this.context = context;
		this.copy = copy;
		this.hashWhenOpened = hashWhenOpened;
		this.document = document;
	}

	/**
	 * @throws FileNotFoundException if the copy is gone when the save starts. A copy that goes missing during the save
	 * fails with a {@link BackendException}, like a document that can't be written.
	 */
	public void execute() throws BackendException, FileNotFoundException {
		awaitWrites();
		if (!copy.exists()) {
			throw MicrosoftWorkaroundCopies.notFound(copy);
		}
		try {
			if (!MessageDigest.isEqual(hashWhenOpened, MicrosoftWorkaroundCopies.hash(copy))) {
				save();
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
		MicrosoftWorkaroundCopies.delete(copy);
	}

	private void awaitWrites() {
		try {
			Thread.sleep(SETTLE_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * The provider has what was written once the document is closed, and uploads it from there. If writing fails, the
	 * document is closed with an error instead, so that the provider drops the part that was written.
	 */
	private void save() throws IOException {
		ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(document, "wt");
		if (descriptor == null) {
			throw new FileNotFoundException(document.toString());
		}
		try (InputStream in = MicrosoftWorkaroundCopies.open(copy)) {
			// the stream doesn't own the descriptor, which is closed below
			MicrosoftWorkaroundCopies.copy(in, new FileOutputStream(descriptor.getFileDescriptor()));
		} catch (IOException | RuntimeException e) {
			closeWithError(descriptor, e);
			throw e;
		}
		descriptor.close();
	}

	private void closeWithError(ParcelFileDescriptor descriptor, Exception cause) {
		try {
			descriptor.closeWithError("Saving the copy failed");
		} catch (IOException e) {
			cause.addSuppressed(e);
		}
	}
}
