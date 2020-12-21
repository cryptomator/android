package org.cryptomator.data.util;

import org.cryptomator.data.BuildConfig;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class UserAgentInterceptor implements Interceptor {

	@Override
	public Response intercept(Chain chain) throws IOException {
		Request originalRequest = chain.request();
		String userAgent = "Cryptomator-Android/" + BuildConfig.VERSION_NAME + " " + System.getProperty("http.agent");
		Request requestWithUserAgent = originalRequest.newBuilder().header("User-Agent", userAgent).build();
		return chain.proceed(requestWithUserAgent);
	}

}
