package org.cryptomator.data.cloud.okhttplogging;

import android.content.Context;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Connection;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class HttpLoggingInterceptor implements Interceptor {

	private static final HeaderNames EXCLUDED_HEADERS = new HeaderNames(//
			// headers excluded because they are logged separately:
			"Content-Type", "Content-Length",
			// headers excluded because they contain sensitive information:
			"Authorization", //
			"WWW-Authenticate", //
			"Cookie", //
			"Set-Cookie" //
	);
	private final Logger logger;
	private final Context context;

	public HttpLoggingInterceptor(Logger logger, Context context) {
		this.logger = logger;
		this.context = context;
	}

	private static boolean debugModeEnabled(Context context) {
		return getDefaultSharedPreferences(context).getBoolean("debugMode", false);
	}

	@NotNull
	@Override
	public Response intercept(@NotNull Chain chain) throws IOException {
		if (debugModeEnabled(context)) {
			return proceedWithLogging(chain);
		} else {
			return chain.proceed(chain.request());
		}
	}

	private Response proceedWithLogging(Chain chain) throws IOException {
		Request request = chain.request();
		logRequest(request, chain);
		return getAndLogResponse(request, chain);
	}

	private void logRequest(Request request, Chain chain) throws IOException {
		logRequestStart(request, chain);
		logContentTypeAndLength(request);
		logHeaders(request.headers());
		logRequestEnd(request);
	}

	private Response getAndLogResponse(Request request, Chain chain) throws IOException {
		long startOfRequestMs = System.nanoTime();
		Response response = getResponseLoggingExceptions(request, chain);
		long requestDurationMs = NANOSECONDS.toMillis(System.nanoTime() - startOfRequestMs);
		logResponse(response, requestDurationMs);
		return response;
	}

	private Response getResponseLoggingExceptions(Request request, Chain chain) throws IOException {
		try {
			return chain.proceed(request);
		} catch (Exception e) {
			logger.log("<-- HTTP FAILED: " + e);
			throw e;
		}
	}

	private void logResponse(Response response, long requestDurationMs) {
		logResponseStart(response, requestDurationMs);
		logHeaders(response.headers());
		logger.log("<-- END HTTP");
	}

	private void logRequestStart(Request request, Chain chain) throws IOException {
		Connection connection = chain.connection();
		Protocol protocol = connection != null ? connection.protocol() : Protocol.HTTP_1_1;
		String bodyLength = hasBody(request) ? request.body().contentLength() + "-byte body" : "unknown length";

		logger.log(format("--> %s %s %s (%s)", //
				request.method(), //
				request.url(), //
				protocol, //
				bodyLength //
		));
	}

	private void logContentTypeAndLength(Request request) throws IOException {
		// Request body headers are only present when installed as a network interceptor. Force
		// them to be included (when available) so there values are known.
		if (hasBody(request)) {
			RequestBody body = request.body();
			if (body.contentType() != null) {
				logger.log("Content-Type: " + body.contentType());
			}
			if (body.contentLength() != -1) {
				logger.log("Content-Length: " + body.contentLength());
			}
		}
	}

	private void logRequestEnd(Request request) {
		logger.log("--> END " + request.method());
	}

	private void logResponseStart(Response response, long requestDurationMs) {
		logger.log("<-- " + response.code() + ' ' + response.message() + ' ' + response.request().url() + " (" + requestDurationMs + "ms" + ')');
	}

	private boolean hasBody(Request request) {
		return request.body() != null;
	}

	private void logHeaders(Headers headers) {
		for (int i = 0, count = headers.size(); i < count; i++) {
			String name = headers.name(i);
			if (isExcludedHeader(name)) {
				continue;
			}
			logger.log(name + ": " + headers.value(i));
		}
	}

	private boolean isExcludedHeader(String name) {
		return EXCLUDED_HEADERS.contains(name);
	}

	public interface Logger {

		void log(String message);
	}
}
