package org.cryptomator.data.cloud.smb;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.SmbCloud;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.SMB;

/**
 * SMB Cloud content repository factory.
 * Skeleton for the first step of SMB support.
 */
@Singleton
public class SmbCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;

	@Inject
	public SmbCloudContentRepositoryFactory(Context context) {
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == SMB;
	}

	@Override
	public CloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile> cloudContentRepositoryFor(Cloud cloud) {
		SmbCloud smbCloud = (SmbCloud) cloud;
		// Authentication check will be added when SMB implementation is ready
		return new SmbCloudContentRepository(smbCloud, context);
	}
}
