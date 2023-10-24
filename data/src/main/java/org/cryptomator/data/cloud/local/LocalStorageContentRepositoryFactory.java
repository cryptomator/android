package org.cryptomator.data.cloud.local;

import static org.cryptomator.domain.CloudType.LOCAL;

import android.content.Context;
import android.content.UriPermission;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.util.file.MimeTypes;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LocalStorageContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;
	private final MimeTypes mimeTypes;

	@Inject
	public LocalStorageContentRepositoryFactory(Context context, MimeTypes mimeTypes) {
		this.context = context;
		this.mimeTypes = mimeTypes;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == LOCAL;
	}

	@Override
	public CloudContentRepository cloudContentRepositoryFor(Cloud cloud) {
		List<UriPermission> permissions = context.getContentResolver().getPersistedUriPermissions();
		for (UriPermission permission : permissions) {
			if (permission.getUri().toString().equals(((LocalStorageCloud) cloud).rootUri())) {
				return new LocalStorageAccessFrameworkContentRepository(context, mimeTypes, (LocalStorageCloud) cloud);
			}
		}

		throw new NoAuthenticationProvidedException(cloud);
	}

}
