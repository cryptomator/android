package org.cryptomator.data.cloud.s3;

import android.content.Context;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;

import org.cryptomator.domain.S3Cloud;
import org.cryptomator.util.crypto.CredentialCryptor;

class S3ClientFactory {

	private AmazonS3 apiClient;

	public AmazonS3 getClient(S3Cloud cloud, Context context) {
		if (apiClient == null) {
			apiClient = createApiClient(cloud, context);
		}
		return apiClient;
	}

	private AmazonS3 createApiClient(S3Cloud cloud, Context context) {
		Region region = Region.getRegion(cloud.s3Region());

		S3ClientOptions.Builder s3ClientOptionsBuilder = S3ClientOptions.builder();
		if (region == null) {
			region = Region.getRegion(Regions.DEFAULT_REGION);
			s3ClientOptionsBuilder.setPayloadSigningEnabled(false);
		}

		AmazonS3Client client = new AmazonS3Client(new BasicAWSCredentials(decrypt(cloud.accessKey(), context), decrypt(cloud.secretKey(), context)), region);
		client.setEndpoint(cloud.s3Endpoint());
		client.setS3ClientOptions(s3ClientOptionsBuilder.build());
		return client;
	}


	private String decrypt(String password, Context context) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
	}
}
