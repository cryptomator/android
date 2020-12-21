package org.cryptomator.data.cloud.dropbox;

import android.content.Context;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.http.OkHttp3Requestor;
import com.dropbox.core.v2.DbxClientV2;

import org.cryptomator.data.BuildConfig;
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor;

import java.util.Locale;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import timber.log.Timber;

import static org.cryptomator.data.util.NetworkTimeout.CONNECTION;
import static org.cryptomator.data.util.NetworkTimeout.READ;
import static org.cryptomator.data.util.NetworkTimeout.WRITE;

class DropboxClientFactory {

	private DbxClientV2 sDbxClient;

	public DbxClientV2 getClient(String accessToken, Context context) {
		if (sDbxClient == null) {
			sDbxClient = createDropboxClient(accessToken, context);
		}
		return sDbxClient;
	}

	private DbxClientV2 createDropboxClient(String accessToken, Context context) {
		String userLocale = Locale.getDefault().toString();

		OkHttpClient okHttpClient = new OkHttpClient() //
				.newBuilder() //
				.connectTimeout(CONNECTION.getTimeout(), CONNECTION.getUnit()) //
				.readTimeout(READ.getTimeout(), READ.getUnit()) //
				.writeTimeout(WRITE.getTimeout(), WRITE.getUnit()) //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.build();

		DbxRequestConfig requestConfig = DbxRequestConfig //
				.newBuilder("Cryptomator-Android/" + BuildConfig.VERSION_NAME) //
				.withUserLocale(userLocale) //
				.withHttpRequestor(new OkHttp3Requestor(okHttpClient)) //
				.build();

		return new DbxClientV2(requestConfig, accessToken);
	}

	private static Interceptor httpLoggingInterceptor(Context context) {
		return new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").d(message), context);
	}
}
