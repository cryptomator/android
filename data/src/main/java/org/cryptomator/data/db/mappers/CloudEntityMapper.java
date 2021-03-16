package org.cryptomator.data.db.mappers;

import org.cryptomator.data.db.entities.CloudEntity;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.DropboxCloud;
import org.cryptomator.domain.PCloudCloud;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.WebDavCloud;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.DropboxCloud.aDropboxCloud;
import static org.cryptomator.domain.GoogleDriveCloud.aGoogleDriveCloud;
import static org.cryptomator.domain.LocalStorageCloud.aLocalStorage;
import static org.cryptomator.domain.OnedriveCloud.aOnedriveCloud;
import static org.cryptomator.domain.PCloudCloud.aPCloudCloud;
import static org.cryptomator.domain.WebDavCloud.aWebDavCloudCloud;

@Singleton
public class CloudEntityMapper extends EntityMapper<CloudEntity, Cloud> {

	@Inject
	public CloudEntityMapper() {
	}

	@Override
	public Cloud fromEntity(CloudEntity entity) {
		CloudType type = CloudType.valueOf(entity.getType());
		switch (type) {
			case DROPBOX:
				return aDropboxCloud() //
						.withId(entity.getId()) //
						.withAccessToken(entity.getAccessToken()) //
						.withUsername(entity.getUsername()) //
						.build();
			case GOOGLE_DRIVE:
				return aGoogleDriveCloud() //
						.withId(entity.getId()) //
						.withAccessToken(entity.getAccessToken()) //
						.withUsername(entity.getUsername()) //
						.build();
			case ONEDRIVE:
				return aOnedriveCloud() //
						.withId(entity.getId()) //
						.withAccessToken(entity.getAccessToken()) //
						.withUsername(entity.getUsername()) //
						.build();
			case PCLOUD:
				return aPCloudCloud() //
						.withId(entity.getId()) //
						.withUrl(entity.getWebdavUrl()) //
						.withAccessToken(entity.getAccessToken()) //
						.withUsername(entity.getUsername()) //
						.build();
			case LOCAL:
				return aLocalStorage() //
						.withId(entity.getId()) //
						.withRootUri(entity.getAccessToken()).build();
			case WEBDAV:
				return aWebDavCloudCloud() //
						.withId(entity.getId()) //
						.withUrl(entity.getUrl()) //
						.withUsername(entity.getUsername()) //
						.withPassword(entity.getAccessToken()) //
						.withCertificate(entity.getWebdavCertificate()) //
						.build();
			default:
				throw new IllegalStateException("Unhandled enum constant " + type);
		}
	}

	@Override
	public CloudEntity toEntity(Cloud domainObject) {
		CloudEntity result = new CloudEntity();
		result.setId(domainObject.id());
		result.setType(domainObject.type().name());
		switch (domainObject.type()) {
			case DROPBOX:
				result.setAccessToken(((DropboxCloud) domainObject).accessToken());
				result.setUsername(((DropboxCloud) domainObject).username());
				break;
			case GOOGLE_DRIVE:
				result.setAccessToken(((GoogleDriveCloud) domainObject).accessToken());
				result.setUsername(((GoogleDriveCloud) domainObject).username());
				break;
			case ONEDRIVE:
				result.setAccessToken(((OnedriveCloud) domainObject).accessToken());
				result.setUsername(((OnedriveCloud) domainObject).username());
				break;
			case PCLOUD:
				result.setAccessToken(((PCloudCloud) domainObject).accessToken());
				result.setWebdavUrl(((PCloudCloud) domainObject).url());
				result.setUsername(((PCloudCloud) domainObject).username());
				break;
			case LOCAL:
				result.setAccessToken(((LocalStorageCloud) domainObject).rootUri());
				break;
			case WEBDAV:
				result.setAccessToken(((WebDavCloud) domainObject).password());
				result.setUrl(((WebDavCloud) domainObject).url());
				result.setUsername(((WebDavCloud) domainObject).username());
				result.setWebdavCertificate(((WebDavCloud) domainObject).certificate());
				break;
			default:
				throw new IllegalStateException("Unhandled enum constant " + domainObject.type());
		}
		return result;
	}

}
