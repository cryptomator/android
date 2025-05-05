package org.cryptomator.domain.usecases;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.IllegalFileNameException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.file.MimeType;
import org.cryptomator.util.file.MimeTypes;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

@UseCase
public class PrepareDownloadFiles {

	private final Context context;
	private final MimeTypes mimeTypes;
	private final List<CloudFile> filesToExport;
	private final Uri parentUri;
	private final ContentResolver contentResolver;
	private final CloudNodeRecursiveListing cloudNodeRecursiveListing;

	private List<DownloadFile> downloadFiles = new ArrayList<>();

	PrepareDownloadFiles(Context context, MimeTypes mimeTypes, @Parameter List<CloudFile> filesToExport, @Parameter Uri parentUri, @Parameter CloudNodeRecursiveListing cloudNodeRecursiveListing) {
		this.context = context;
		this.mimeTypes = mimeTypes;
		this.filesToExport = filesToExport;
		this.parentUri = parentUri;
		this.contentResolver = context.getContentResolver();
		this.cloudNodeRecursiveListing = cloudNodeRecursiveListing;
	}

	List<DownloadFile> execute() throws BackendException, FileNotFoundException {
		downloadFiles = prepareFilesForExport(filesToExport, parentUri);
		for (CloudFolderRecursiveListing folderRecursiveListing : cloudNodeRecursiveListing.getFoldersContent()) {
			prepareFolderContentForExport(folderRecursiveListing, parentUri);
		}
		return downloadFiles;
	}

	private List<DownloadFile> prepareFilesForExport(List<CloudFile> filesToExport, Uri parentUri) throws NoSuchCloudFileException, FileNotFoundException, IllegalFileNameException {
		List<DownloadFile> downloadFiles = new ArrayList<>();
		for (CloudFile cloudFile : filesToExport) {
			DownloadFile downloadFile = createDownloadFile(cloudFile, parentUri);
			downloadFiles.add(downloadFile);
		}
		return downloadFiles;
	}

	private void prepareFolderContentForExport(CloudFolderRecursiveListing folderRecursiveListing, Uri parentUri) throws FileNotFoundException, NoSuchCloudFileException, IllegalFileNameException {
		Uri createdFolder = createFolder(parentUri, folderRecursiveListing.getParent().getName());
		if (createdFolder != null) {
			downloadFiles.addAll(prepareFilesForExport(folderRecursiveListing.getFiles(), createdFolder));
			for (CloudFolderRecursiveListing childFolder : folderRecursiveListing.getFolders()) {
				prepareFolderContentForExport(childFolder, createdFolder);
			}
		} else {
			throw new FatalBackendException("Failed to create parent folder for export");
		}
	}

	private Uri createFolder(Uri parentUri, String folderName) throws NoSuchCloudFileException {
		try {
			return DocumentsContract.createDocument(contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, folderName);
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException("Creating folder failed");
		}
	}

	private DownloadFile createDownloadFile(CloudFile file, Uri documentUri) throws NoSuchCloudFileException, IllegalFileNameException {
		try {
			return new DownloadFile.Builder().setDownloadFile(file).setDataSink(contentResolver.openOutputStream(createNewDocumentUri(documentUri, file.getName()))).build();
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException(file.getName());
		}
	}

	private Uri createNewDocumentUri(Uri parentUri, String fileName) throws IllegalFileNameException, NoSuchCloudFileException {
		MimeType mimeType = mimeTypes.fromFilename(fileName);
		if (mimeType == null) {
			mimeType = MimeType.APPLICATION_OCTET_STREAM;
		}
		try {
			Uri documentUri = DocumentsContract.createDocument(context.getContentResolver(), parentUri, mimeType.toString(), fileName);
			if (documentUri == null) {
				throw new IllegalFileNameException();
			}
			return documentUri;
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException(fileName);
		}
	}

}
