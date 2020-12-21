package org.cryptomator.data.cloud.webdav.network;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static org.cryptomator.data.util.NetworkTimeout.CONNECTION;
import static org.cryptomator.data.util.NetworkTimeout.READ;
import static org.cryptomator.data.util.NetworkTimeout.WRITE;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.WEBDAV;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.X509TrustManager;

import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor;
import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.domain.exception.UnableToDecryptWebdavPasswordException;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.crypto.CredentialCryptor;
import org.cryptomator.util.file.LruFileCacheUtil;

import com.burgstaller.okhttp.AuthenticationCacheInterceptor;
import com.burgstaller.okhttp.CachingAuthenticatorDecorator;
import com.burgstaller.okhttp.DispatchingAuthenticator;
import com.burgstaller.okhttp.basic.BasicAuthenticator;
import com.burgstaller.okhttp.digest.CachingAuthenticator;
import com.burgstaller.okhttp.digest.Credentials;
import com.burgstaller.okhttp.digest.DigestAuthenticator;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

class WebDavCompatibleHttpClient {

	private final WebDavRedirectHandler webDavRedirectHandler;

	WebDavCompatibleHttpClient(WebDavCloud cloud, Context context) {
		final SharedPreferencesHandler sharedPreferencesHandler = new SharedPreferencesHandler(context);
		this.webDavRedirectHandler = new WebDavRedirectHandler(httpClientFor(cloud, context, sharedPreferencesHandler.useLruCache(), sharedPreferencesHandler.lruCacheSize()));
	}

	Response execute(Request.Builder requestBuilder) throws IOException {
		return execute(requestBuilder.build());
	}

	private Response execute(Request request) throws IOException {
		return webDavRedirectHandler.executeFollowingRedirects(request);
	}

	private static OkHttpClient httpClientFor(WebDavCloud webDavCloud, Context context, boolean useLruCache, int lruCacheSize) {
		final Map<String, CachingAuthenticator> authCache = new ConcurrentHashMap<>();

		OkHttpClient.Builder builder = new OkHttpClient() //
				.newBuilder() //
				.connectTimeout(CONNECTION.getTimeout(), CONNECTION.getUnit()) //
				.readTimeout(READ.getTimeout(), READ.getUnit()) //
				.writeTimeout(WRITE.getTimeout(), WRITE.getUnit()) //
				.followRedirects(false) //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.authenticator(httpAuthenticator(context, webDavCloud, authCache)) //
				.addInterceptor(new AuthenticationCacheInterceptor(authCache));

		if (useLruCache) {
			final Cache cache = new Cache(new LruFileCacheUtil(context).resolve(WEBDAV), lruCacheSize);
			builder.cache(cache) //
					.addNetworkInterceptor(provideCacheInterceptor()) //
					.addInterceptor(provideOfflineCacheInterceptor(context));
		}

		X509TrustManager trustManager;
		if (usingWebDavWithSelfSignedCertificate(webDavCloud)) {
			PinningTrustManager pinningTrustManager = new PinningTrustManager(webDavCloud.certificate());
			trustManager = pinningTrustManager;
			builder.hostnameVerifier(pinningTrustManager.hostnameVerifier());
		} else {
			trustManager = new DefaultTrustManager();
		}
		builder.sslSocketFactory(SSLSocketFactories.from(trustManager), trustManager);

		return builder.build();
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

	private static Interceptor provideCacheInterceptor() {
		return chain -> {
			final Response response = chain.proceed(chain.request());
			final CacheControl cacheControl = new CacheControl.Builder() //
					.maxAge(0, TimeUnit.DAYS) //
					.build();

			return response.newBuilder() //
					.removeHeader("Pragma") //
					.removeHeader("Cache-Control") //
					.header(CACHE_CONTROL, cacheControl.toString()) //
					.build();
		};
	}

	private static Authenticator httpAuthenticator(Context context, WebDavCloud webDavCloud, Map<String, CachingAuthenticator> authCache) {
		Credentials credentials = new Credentials(webDavCloud.username(), decryptPassword(context, webDavCloud.password()));
		final BasicAuthenticator basicAuthenticator = new BasicAuthenticator(credentials);
		final DigestAuthenticator digestAuthenticator = new DigestAuthenticator(credentials);

		Authenticator result = new DispatchingAuthenticator //
				.Builder() //
						.with("digest", digestAuthenticator) //
						.with("basic", basicAuthenticator) //
						.build();
		result = new CachingAuthenticatorDecorator(result, authCache);

		return result;
	}

	private static String decryptPassword(Context context, String password) throws UnableToDecryptWebdavPasswordException {
		try {
			return CredentialCryptor //
					.getInstance(context) //
					.decrypt(password);
		} catch (RuntimeException e) {
			throw new UnableToDecryptWebdavPasswordException(e);
		}
	}

	private static Interceptor httpLoggingInterceptor(Context context) {
		return new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").d(message), context);
	}

	private static boolean usingWebDavWithSelfSignedCertificate(WebDavCloud webDavCloud) {
		return webDavCloud.certificate() != null;
	}

	private static boolean isNetworkAvailable(final Context context) {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
}
