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

	/**
	 * Attempts before the copy is given up on as still being written.
	 */
	static final int MAX_ATTEMPTS = 3;

	private final Context context;
	private final File copy;
	private final byte[] hashWhenOpened;
	private final Uri document;
	long settleMillis = SETTLE_MILLIS;

	public ImportMicrosoftWorkaroundCopy(Context context, @Parameter File copy, @Parameter byte[] hashWhenOpened, @Parameter Uri document) {
		this.context = context;
		this.copy = copy;
		this.hashWhenOpened = hashWhenOpened;
		this.document = document;
	}

	/**
	 * @throws FileNotFoundException if the copy is gone before an attempt to save it. A copy that can't be opened once an
	 * attempt started fails with a {@link BackendException}, like a document that can't be written.
	 */
	public void execute() throws BackendException, FileNotFoundException {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			awaitWrites();
			if (!copy.exists()) {
				throw MicrosoftWorkaroundCopies.notFound(copy);
			}
			try {
				if (tryBringBack()) {
					MicrosoftWorkaroundCopies.delete(copy);
					return;
				}
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}
		throw new FatalBackendException(MicrosoftWorkaroundCopies.describe(copy) + " is still being written");
	}

	/**
	 * @return whether the copy is unchanged or saved. It is neither if the app wrote the copy while it was read. Then the
	 * comparison may have missed the change, or the save may hold only a part of it.
	 */
	private boolean tryBringBack() throws IOException {
		long lengthBefore = copy.length();
		long lastModifiedBefore = copy.lastModified();
		boolean unchanged = MessageDigest.isEqual(hashWhenOpened, MicrosoftWorkaroundCopies.hash(copy));
		if (wasWrittenSince(lengthBefore, lastModifiedBefore)) {
			return false;
		}
		return unchanged || save(lengthBefore, lastModifiedBefore);
	}

	private boolean wasWrittenSince(long lengthBefore, long lastModifiedBefore) {
		return copy.length() != lengthBefore || copy.lastModified() != lastModifiedBefore;
	}

	private void awaitWrites() {
		try {
			Thread.sleep(settleMillis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * The provider has what was written once the document is closed, and uploads it from there. If writing fails, or the
	 * app wrote the copy since its length and modification time were noted, the document is closed with an error instead,
	 * so that the provider drops what was written.
	 *
	 * @return false if the app wrote the copy since then
	 */
	private boolean save(long lengthBefore, long lastModifiedBefore) throws IOException {
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
		if (wasWrittenSince(lengthBefore, lastModifiedBefore)) {
			descriptor.closeWithError("The copy was written while it was saved");
			return false;
		}
		descriptor.close();
		return true;
	}

	private void closeWithError(ParcelFileDescriptor descriptor, Exception cause) {
		try {
			descriptor.closeWithError("Saving the copy failed");
		} catch (IOException e) {
			cause.addSuppressed(e);
		}
	}
}
