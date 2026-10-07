package org.cryptomator.data.db.mappers;

import org.cryptomator.data.db.entities.CloudEntity;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.DropboxCloud;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.PCloud;
import org.cryptomator.domain.S3Cloud;
import org.cryptomator.domain.WebDavCloud;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.DropboxCloud.aDropboxCloud;
import static org.cryptomator.domain.GoogleDriveCloud.aGoogleDriveCloud;
import static org.cryptomator.domain.LocalStorageCloud.aLocalStorage;
import static org.cryptomator.domain.OnedriveCloud.aOnedriveCloud;
import static org.cryptomator.domain.PCloud.aPCloud;
import static org.cryptomator.domain.S3Cloud.aS3Cloud;
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
						.withUsername(entity.getUsername()) //
						.build();
			case LOCAL:
				return aLocalStorage() //
						.withId(entity.getId()) //
						.withRootUri(entity.getUrl()).build();
			case ONEDRIVE:
				return aOnedriveCloud() //
						.withId(entity.getId()) //
						.withAccessToken(entity.getAccessToken()) //
						.withUsername(entity.getUsername()) //
						.build();
			case PCLOUD:
				return aPCloud() //
						.withId(entity.getId()) //
						.withUrl(entity.getUrl()) //
						.withAccessToken(entity.getAccessToken()) //
						.withUsername(entity.getUsername()) //
						.build();
			case S3:
				return aS3Cloud() //
						.withId(entity.getId()) //
						.withS3Endpoint(entity.getUrl()) //
						.withS3Region(entity.getS3Region()) //
						.withAccessKey(entity.getAccessToken()) //
						.withSecretKey(entity.getS3SecretKey()) //
						.withS3Bucket(entity.getS3Bucket()) //
						.withDisplayName(entity.getUsername()) //
						.build();
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
				result.setUsername(((GoogleDriveCloud) domainObject).username());
				break;
			case LOCAL:
				result.setUrl(((LocalStorageCloud) domainObject).rootUri());
				break;
			case ONEDRIVE:
				result.setAccessToken(((OnedriveCloud) domainObject).accessToken());
				result.setUsername(((OnedriveCloud) domainObject).username());
				break;
			case PCLOUD:
				result.setAccessToken(((PCloud) domainObject).accessToken());
				result.setUrl(((PCloud) domainObject).url());
				result.setUsername(((PCloud) domainObject).username());
				break;
			case S3:
				result.setUrl(((S3Cloud) domainObject).s3Endpoint());
				result.setS3Region(((S3Cloud) domainObject).s3Region());
				result.setAccessToken(((S3Cloud) domainObject).accessKey());
				result.setS3SecretKey(((S3Cloud) domainObject).secretKey());
				result.setS3Bucket(((S3Cloud) domainObject).s3Bucket());
				result.setUsername(((S3Cloud) domainObject).displayName());
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
