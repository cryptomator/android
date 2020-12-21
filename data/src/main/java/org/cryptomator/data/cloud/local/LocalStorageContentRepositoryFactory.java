package org.cryptomator.data.cloud.local;

import android.content.Context;
import android.os.Build;

import org.cryptomator.data.cloud.local.file.LocalStorageContentRepository;
import org.cryptomator.data.cloud.local.storageaccessframework.LocalStorageAccessFrameworkContentRepository;
import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.util.file.MimeTypes;

import javax.inject.Inject;
import javax.inject.Singleton;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static androidx.core.content.ContextCompat.checkSelfPermission;
import static org.cryptomator.domain.CloudType.LOCAL;

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
		if (!hasPermissions(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)) {
			throw new NoAuthenticationProvidedException(cloud);
		}
		if (((LocalStorageCloud) cloud).rootUri() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			return new LocalStorageAccessFrameworkContentRepository(context, mimeTypes, (LocalStorageCloud) cloud);
		} else {
			return new LocalStorageContentRepository(context, (LocalStorageCloud) cloud);
		}
	}

	private boolean hasPermissions(String... permissions) {
		for (String permission : permissions) {
			if (checkSelfPermission(context, permission) != PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

}
