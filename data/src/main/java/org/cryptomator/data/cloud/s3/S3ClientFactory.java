package org.cryptomator.data.cloud.s3;

import android.content.Context;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

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
		AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(cloud.s3Endpoint(), cloud.s3Region());

		AWSCredentials credentials = new BasicAWSCredentials(cloud.accessKey(), decrypt(cloud.secretKey(), context));

		return AmazonS3ClientBuilder.standard().withEndpointConfiguration(endpointConfiguration).withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
	}

	private String decrypt(String password, Context context) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
	}
}
