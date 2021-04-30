package org.cryptomator.data.cloud.s3;

import android.content.Context;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

import org.cryptomator.domain.S3Cloud;
import org.cryptomator.util.crypto.CredentialCryptor;

import timber.log.Timber;

class S3ClientFactory {

	private AmazonS3 apiClient;

	public AmazonS3 getClient(S3Cloud cloud, Context context) {
		if (apiClient == null) {
			apiClient = createApiClient(cloud, context);
		}
		return apiClient;
	}

	private AmazonS3 createApiClient(S3Cloud cloud, Context context) {
		Region region = Region.getRegion(Regions.DEFAULT_REGION);
		String endpoint = null;

		if (cloud.s3Region() != null) {
			region = Region.getRegion(cloud.s3Region());
		} else if (cloud.s3Endpoint() != null) {
			endpoint = cloud.s3Endpoint();
		}

		AmazonS3Client client = new AmazonS3Client(new BasicAWSCredentials(decrypt(cloud.accessKey(), context), decrypt(cloud.secretKey(), context)), region);

		if (endpoint != null) {
			client.setEndpoint(cloud.s3Endpoint());
		}

		client.addRequestHandler(new LoggingAwareRequestHandler());

		return client;
	}

	private String decrypt(String password, Context context) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
	}

	private static class LoggingAwareRequestHandler extends RequestHandler2 {

		@Override
		public void beforeRequest(Request<?> request) {
			Timber.tag("S3Client").d("Sending request (%s) %s", request.getAWSRequestMetrics().getTimingInfo().getStartTimeNano(), request.toString());
		}

		@Override
		public void afterResponse(Request<?> request, Response<?> response) {
			Timber.tag("S3Client").d( //
					"Response received (%s) with status %s (%s)", //
					request.getAWSRequestMetrics().getTimingInfo().getStartTimeNano(), //
					response.getHttpResponse().getStatusText(), //
					response.getHttpResponse().getStatusCode());
		}

		@Override
		public void afterError(Request<?> request, Response<?> response, Exception e) {
			if (response != null) {
				Timber.tag("S3Client").e( //
						e, //
						"Error occurred (%s) with status %s (%s)", //
						request.getAWSRequestMetrics().getTimingInfo().getStartTimeNano(), //
						response.getHttpResponse().getStatusText(), //
						response.getHttpResponse().getStatusCode());
			} else {
				Timber.tag("S3Client").e(e, "Error occurred (%s)", request.getAWSRequestMetrics().getTimingInfo().getStartTimeNano());
			}
		}
	}
}
