package org.cryptomator.data.cloud.pcloud;

import android.content.Context;

import com.pcloud.sdk.ApiClient;
import com.pcloud.sdk.Authenticators;
import com.pcloud.sdk.PCloudSdk;

import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor;
import org.cryptomator.util.crypto.CredentialCryptor;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import timber.log.Timber;

import static org.cryptomator.data.util.NetworkTimeout.CONNECTION;
import static org.cryptomator.data.util.NetworkTimeout.READ;
import static org.cryptomator.data.util.NetworkTimeout.WRITE;

class PCloudClientFactory {

	private ApiClient apiClient;

	private static Interceptor httpLoggingInterceptor(Context context) {
		return new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").d(message), context);
	}

	public ApiClient getClient(String accessToken, String url, Context context) {
		if (apiClient == null) {
			apiClient = createApiClient(accessToken, url, context);
		}
		return apiClient;
	}

	private ApiClient createApiClient(String accessToken, String url, Context context) {
		OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient() //
				.newBuilder() //
				.connectTimeout(CONNECTION.getTimeout(), CONNECTION.getUnit()) //
				.readTimeout(READ.getTimeout(), READ.getUnit()) //
				.writeTimeout(WRITE.getTimeout(), WRITE.getUnit()) //
				.addInterceptor(httpLoggingInterceptor(context)); //;

		OkHttpClient okHttpClient = okHttpClientBuilder.build();

		return PCloudSdk.newClientBuilder().authenticator(Authenticators.newOAuthAuthenticator(decrypt(accessToken, context))).withClient(okHttpClient).apiHost(url).create();
	}

	private String decrypt(String password, Context context) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
	}
}
