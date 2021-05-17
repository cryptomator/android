package org.cryptomator.data.cloud.s3;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor;
import org.cryptomator.domain.S3Cloud;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.crypto.CredentialCryptor;
import org.cryptomator.util.file.LruFileCacheUtil;

import java.util.concurrent.TimeUnit;

import io.minio.MinioClient;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import timber.log.Timber;

import static org.cryptomator.data.util.NetworkTimeout.CONNECTION;
import static org.cryptomator.data.util.NetworkTimeout.READ;
import static org.cryptomator.data.util.NetworkTimeout.WRITE;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.S3;

class S3ClientFactory {

	private MinioClient apiClient;

	private static Interceptor httpLoggingInterceptor(Context context) {
		return new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").d(message), context);
	}

	private static Interceptor provideOfflineCacheInterceptor(final Context context) {
		return chain -> {
			Request request = chain.request();

			if (isNetworkAvailable(context)) {
				final CacheControl cacheControl = new CacheControl.Builder() //
						.maxAge(0, TimeUnit.DAYS) //
						.build();

				request = request.newBuilder() //
						.cacheControl(cacheControl) //
						.build();
			}

			return chain.proceed(request);
		};
	}

	private static boolean isNetworkAvailable(final Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	public MinioClient getClient(S3Cloud cloud, Context context) {
		if (apiClient == null) {
			apiClient = createApiClient(cloud, context);
		}
		return apiClient;
	}

	private MinioClient createApiClient(S3Cloud cloud, Context context) {
		final SharedPreferencesHandler sharedPreferencesHandler = new SharedPreferencesHandler(context);

		MinioClient.Builder minioClientBuilder = MinioClient.builder();

		minioClientBuilder.endpoint(cloud.s3Endpoint());
		minioClientBuilder.region(cloud.s3Region());

		OkHttpClient.Builder httpClientBuilder = new OkHttpClient() //
				.newBuilder() //
				.connectTimeout(CONNECTION.getTimeout(), CONNECTION.getUnit()) //
				.readTimeout(READ.getTimeout(), READ.getUnit()) //
				.writeTimeout(WRITE.getTimeout(), WRITE.getUnit()) //
				.addInterceptor(httpLoggingInterceptor(context));

		if (sharedPreferencesHandler.useLruCache()) {
			final Cache cache = new Cache(new LruFileCacheUtil(context).resolve(S3), sharedPreferencesHandler.lruCacheSize());
			httpClientBuilder.cache(cache).addInterceptor(provideOfflineCacheInterceptor(context));
		}

		return minioClientBuilder //
				.credentials(decrypt(cloud.accessKey(), context), decrypt(cloud.secretKey(), context)) //
				.httpClient(httpClientBuilder.build()) //
				.build();
	}

	private String decrypt(String password, Context context) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
	}
}
