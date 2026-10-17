package org.cryptomator.data.documents;

import android.content.Context;

import org.cryptomator.data.db.dao.CachedFileDao;
import org.cryptomator.data.db.dao.DocumentMetadataDao;
import org.cryptomator.data.repository.DispatchingCloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.util.file.MimeTypes;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * The only place that creates the caches, so that the documents provider and the app share them.
 */
@Module
public class DocumentsModule {

	@Singleton
	@Provides
	public CloudTaskScheduler provideCloudTaskScheduler() {
		return new CloudTaskScheduler();
	}

	@Singleton
	@Provides
	public DocumentMetadataCache provideDocumentMetadataCache(DocumentMetadataDao dao, CloudTaskScheduler scheduler, VaultDocumentEvents events, DispatchingCloudContentRepository contentRepository, CloudRepository cloudRepository, VaultRepository vaultRepository, MimeTypes mimeTypes) {
		return new DocumentMetadataCache(dao, scheduler, events, contentRepository, cloudRepository, vaultRepository, mimeTypes);
	}

	@Singleton
	@Provides
	public DocumentContentCache provideDocumentContentCache(CachedFileDao dao, DocumentMetadataCache metadataCache, CloudTaskScheduler scheduler, DispatchingCloudContentRepository contentRepository, ThumbnailStore thumbnailStore, Context context) {
		return new DocumentContentCache(dao, metadataCache, scheduler, contentRepository, copyDirectory(context), thumbnailStore);
	}

	@Singleton
	@Provides
	public ThumbnailStore provideThumbnailStore(DocumentMetadataDao metadataDao, CachedFileDao cachedFileDao, Context context) {
		return new ThumbnailStore(new File(context.getNoBackupFilesDir(), ThumbnailStore.DIRECTORY_NAME), metadataDao, cachedFileDao, copyDirectory(context));
	}

	@Singleton
	@Provides
	public ThumbnailGenerator provideThumbnailGenerator(ThumbnailStore store, DocumentMetadataCache metadataCache, DocumentContentCache contentCache, VaultDocumentEvents events, Context context) {
		return new ThumbnailGenerator(store, metadataCache, contentCache, events, new File(context.getCacheDir(), "thumbnails"));
	}

	private static File copyDirectory(Context context) {
		return new File(context.getNoBackupFilesDir(), DocumentContentCache.DIRECTORY_NAME);
	}
}
