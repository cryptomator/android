// ------------------------------------------------------------------------------
// Copyright (c) 2015 Microsoft Corporation
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF WILDCARD_MIME_TYPE KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR WILDCARD_MIME_TYPE CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
// ------------------------------------------------------------------------------
package org.cryptomator.data.cloud.onedrive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.concurrency.ICallback;
import com.microsoft.graph.concurrency.IExecutors;
import com.microsoft.graph.concurrency.IProgressCallback;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.core.Constants;
import com.microsoft.graph.core.DefaultConnectionConfig;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.core.IConnectionConfig;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.http.HttpMethod;
import com.microsoft.graph.http.HttpResponseCode;
import com.microsoft.graph.http.HttpResponseHeadersHelper;
import com.microsoft.graph.http.IHttpProvider;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.http.IStatefulResponseHandler;
import com.microsoft.graph.httpcore.HttpClients;
import com.microsoft.graph.httpcore.ICoreAuthenticationProvider;
import com.microsoft.graph.httpcore.middlewareoption.RedirectOptions;
import com.microsoft.graph.httpcore.middlewareoption.RetryOptions;
import com.microsoft.graph.logger.ILogger;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.graph.options.HeaderOption;
import com.microsoft.graph.serializer.ISerializer;

import org.jetbrains.annotations.NotNull;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * Http provider based off of URLConnection.
 */
public class OnedriveHttpProvider implements IHttpProvider {

	private final HttpResponseHeadersHelper responseHeadersHelper = new HttpResponseHeadersHelper();

	/**
	 * The serializer
	 */
	private final ISerializer serializer;

	/**
	 * The authentication provider
	 */
	private final IAuthenticationProvider authenticationProvider;

	/**
	 * The executors
	 */
	private final IExecutors executors;

	/**
	 * The logger
	 */
	private final ILogger logger;

	/**
	 * The connection config
	 */
	private IConnectionConfig connectionConfig;

	/**
	 * The OkHttpClient that handles all requests
	 */
	private OkHttpClient corehttpClient;

	/**
	 * Creates the DefaultHttpProvider
	 *
	 * @param serializer the serializer
	 * @param authenticationProvider the authentication provider
	 * @param executors the executors
	 * @param logger the logger for diagnostic information
	 */
	public OnedriveHttpProvider(final ISerializer serializer, final IAuthenticationProvider authenticationProvider, final IExecutors executors, final ILogger logger) {
		this.serializer = serializer;
		this.authenticationProvider = authenticationProvider;
		this.executors = executors;
		this.logger = logger;
	}

	/**
	 * Creates the DefaultHttpProvider
	 *
	 * @param clientConfig the client configuration to use for the provider
	 * @param httpClient the http client to execute the requests with
	 */
	public OnedriveHttpProvider(final IClientConfig clientConfig, final OkHttpClient httpClient) {
		this(clientConfig.getSerializer(), clientConfig.getAuthenticationProvider(), clientConfig.getExecutors(), clientConfig.getLogger());
		this.corehttpClient = httpClient;
	}

	/**
	 * Gets the serializer for this HTTP provider
	 *
	 * @return the serializer for this provider
	 */
	@Override
	public ISerializer getSerializer() {
		return serializer;
	}

	/**
	 * Sends the HTTP request asynchronously
	 *
	 * @param request the request description
	 * @param callback the callback to be called after success or failure
	 * @param resultClass the class of the response from the service
	 * @param serializable the object to send to the service in the body of the request
	 * @param <Result> the type of the response object
	 * @param <Body> the type of the object to send to the service in the body of the request
	 */
	@Override
	public <Result, Body> void send(final IHttpRequest request, final ICallback<? super Result> callback, final Class<Result> resultClass, final Body serializable) {
		final IProgressCallback<? super Result> progressCallback;
		if (callback instanceof IProgressCallback) {
			progressCallback = (IProgressCallback<? super Result>) callback;
		} else {
			progressCallback = null;
		}

		executors.performOnBackground(() -> {
			try {
				executors.performOnForeground(sendRequestInternal(request, resultClass, serializable, progressCallback, null), callback);
			} catch (final ClientException e) {
				executors.performOnForeground(e, callback);
			}
		});
	}

	/**
	 * Sends the HTTP request
	 *
	 * @param request the request description
	 * @param resultClass the class of the response from the service
	 * @param serializable the object to send to the service in the body of the request
	 * @param <Result> the type of the response object
	 * @param <Body> the type of the object to send to the service in the body of the request
	 * @return the result from the request
	 * @throws ClientException an exception occurs if the request was unable to complete for any reason
	 */
	@Override
	public <Result, Body> Result send(final IHttpRequest request, final Class<Result> resultClass, final Body serializable) throws ClientException {
		return send(request, resultClass, serializable, null);
	}

	/**
	 * Sends the HTTP request
	 *
	 * @param request the request description
	 * @param resultClass the class of the response from the service
	 * @param serializable the object to send to the service in the body of the request
	 * @param handler the handler for stateful response
	 * @param <Result> the type of the response object
	 * @param <Body> the type of the object to send to the service in the body of the request
	 * @param <DeserializeType> the response handler for stateful response
	 * @return the result from the request
	 * @throws ClientException this exception occurs if the request was unable to complete for any reason
	 */
	public <Result, Body, DeserializeType> Result send(final IHttpRequest request, final Class<Result> resultClass, final Body serializable, final IStatefulResponseHandler<Result, DeserializeType> handler)
			throws ClientException {
		return sendRequestInternal(request, resultClass, serializable, null, handler);
	}

	/**
	 * Sends the HTTP request
	 *
	 * @param request the request description
	 * @param resultClass the class of the response from the service
	 * @param serializable the object to send to the service in the body of the request
	 * @param progress the progress callback for the request
	 * @param <Result> the type of the response object
	 * @param <Body> the type of the object to send to the service in the body of the request
	 * @return the result from the request
	 * @throws ClientException an exception occurs if the request was unable to complete for any reason
	 */
	public <Result, Body> Request getHttpRequest(final IHttpRequest request, final Class<Result> resultClass, final Body serializable, final IProgressCallback<? super Result> progress) throws ClientException {
		final int defaultBufferSize = 4096;

		final URL requestUrl = request.getRequestUrl();
		logger.logDebug("Starting to send request, URL " + requestUrl.toString());

		if (this.connectionConfig == null) {
			this.connectionConfig = new DefaultConnectionConfig();
		}

		// Request level middleware options
		RedirectOptions redirectOptions = new RedirectOptions(request.getMaxRedirects() > 0 ? request.getMaxRedirects() : this.connectionConfig.getMaxRedirects(),
				request.getShouldRedirect() != null ? request.getShouldRedirect() : this.connectionConfig.getShouldRedirect());
		RetryOptions retryOptions = new RetryOptions(request.getShouldRetry() != null ? request.getShouldRetry() : this.connectionConfig.getShouldRetry(),
				request.getMaxRetries() > 0 ? request.getMaxRetries() : this.connectionConfig.getMaxRetries(), request.getDelay() > 0 ? request.getDelay() : this.connectionConfig.getDelay());

		Request coreHttpRequest = convertIHttpRequestToOkHttpRequest(request);
		Request.Builder corehttpRequestBuilder = coreHttpRequest.newBuilder().tag(RedirectOptions.class, redirectOptions).tag(RetryOptions.class, retryOptions);

		String contenttype = null;

		logger.logDebug("Request Method " + request.getHttpMethod().toString());
		List<HeaderOption> requestHeaders = request.getHeaders();

		for (HeaderOption headerOption : requestHeaders) {
			if (headerOption.getName().equalsIgnoreCase(Constants.CONTENT_TYPE_HEADER_NAME)) {
				contenttype = headerOption.getValue().toString();
				break;
			}
		}

		final byte[] bytesToWrite;
		corehttpRequestBuilder.addHeader("Accept", "*/*");
		if (serializable == null) {
			// Send an empty body through with a POST request
			// This ensures that the Content-Length header is properly set
			if (request.getHttpMethod() == HttpMethod.POST) {
				bytesToWrite = new byte[0];
				if (contenttype == null) {
					contenttype = Constants.BINARY_CONTENT_TYPE;
				}
			} else {
				bytesToWrite = null;
			}
		} else if (serializable instanceof byte[]) {
			logger.logDebug("Sending byte[] as request body");
			bytesToWrite = (byte[]) serializable;

			// If the user hasn't specified a Content-Type for the request
			if (!hasHeader(requestHeaders, Constants.CONTENT_TYPE_HEADER_NAME)) {
				corehttpRequestBuilder.addHeader(Constants.CONTENT_TYPE_HEADER_NAME, Constants.BINARY_CONTENT_TYPE);
				contenttype = Constants.BINARY_CONTENT_TYPE;
			}
		} else {
			logger.logDebug("Sending " + serializable.getClass().getName() + " as request body");
			final String serializeObject = serializer.serializeObject(serializable);
			try {
				bytesToWrite = serializeObject.getBytes(Constants.JSON_ENCODING);
			} catch (final UnsupportedEncodingException ex) {
				final ClientException clientException = new ClientException("Unsupported encoding problem: ", ex);
				logger.logError("Unsupported encoding problem: " + ex.getMessage(), ex);
				throw clientException;
			}

			// If the user hasn't specified a Content-Type for the request
			if (!hasHeader(requestHeaders, Constants.CONTENT_TYPE_HEADER_NAME)) {
				corehttpRequestBuilder.addHeader(Constants.CONTENT_TYPE_HEADER_NAME, Constants.JSON_CONTENT_TYPE);
				contenttype = Constants.JSON_CONTENT_TYPE;
			}
		}

		RequestBody requestBody = null;
		// Handle cases where we've got a body to process.
		if (bytesToWrite != null) {
			final String mediaContentType = contenttype;
			requestBody = new RequestBody() {
				@Override
				public long contentLength() {
					return bytesToWrite.length;
				}

				@Override
				public void writeTo(@NotNull BufferedSink sink) throws IOException {
					OutputStream out = sink.outputStream();
					int writtenSoFar = 0;
					BufferedOutputStream bos = new BufferedOutputStream(out);
					int toWrite;
					do {
						toWrite = Math.min(defaultBufferSize, bytesToWrite.length - writtenSoFar);
						bos.write(bytesToWrite, writtenSoFar, toWrite);
						writtenSoFar = writtenSoFar + toWrite;
						if (progress != null) {
							executors.performOnForeground(writtenSoFar, bytesToWrite.length, progress);
						}
					} while (toWrite > 0);
					bos.close();
					out.close();
				}

				@Override
				public MediaType contentType() {
					return MediaType.parse(mediaContentType);
				}
			};
		}

		corehttpRequestBuilder.method(request.getHttpMethod().toString(), requestBody);
		return corehttpRequestBuilder.build();
	}

	/**
	 * Sends the HTTP request
	 *
	 * @param request the request description
	 * @param resultClass the class of the response from the service
	 * @param serializable the object to send to the service in the body of the request
	 * @param progress the progress callback for the request
	 * @param handler the handler for stateful response
	 * @param <Result> the type of the response object
	 * @param <Body> the type of the object to send to the service in the body of the request
	 * @param <DeserializeType> the response handler for stateful response
	 * @return the result from the request
	 * @throws ClientException an exception occurs if the request was unable to complete for any reason
	 */
	@SuppressWarnings("unchecked")
	private <Result, Body, DeserializeType> Result sendRequestInternal(final IHttpRequest request, final Class<Result> resultClass, final Body serializable, final IProgressCallback<? super Result> progress,
			final IStatefulResponseHandler<Result, DeserializeType> handler) throws ClientException {

		try {
			if (this.connectionConfig == null) {
				this.connectionConfig = new DefaultConnectionConfig();
			}
			if (this.corehttpClient == null) {
				final ICoreAuthenticationProvider authProvider = request1 -> request1;
				this.corehttpClient = HttpClients.createDefault(authProvider).newBuilder().connectTimeout(connectionConfig.getConnectTimeout(), TimeUnit.MILLISECONDS)
						.readTimeout(connectionConfig.getReadTimeout(), TimeUnit.MILLISECONDS).followRedirects(false) // TODO https://github.com/microsoftgraph/msgraph-sdk-java/issues/516
						.protocols(Collections.singletonList(Protocol.HTTP_1_1)) // https://stackoverflow.com/questions/62031298/sockettimeout-on-java-11-but-not-on-java-8
						.build();
			}
			if (authenticationProvider != null) { // TODO https://github.com/microsoftgraph/msgraph-sdk-java/issues/517
				authenticationProvider.authenticateRequest(request);
			}
			Request coreHttpRequest = getHttpRequest(request, resultClass, serializable, progress);
			Response response = corehttpClient.newCall(coreHttpRequest).execute();
			InputStream in = null;
			boolean isBinaryStreamInput = false;
			try {

				// Call being executed

				if (handler != null) {
					handler.configConnection(response);
				}

				logger.logDebug(String.format("Response code %d, %s", response.code(), response.message()));

				if (handler != null) {
					logger.logDebug("StatefulResponse is handling the HTTP response.");
					return handler.generateResult(request, response, this.getSerializer(), this.logger);
				}

				if (response.code() >= HttpResponseCode.HTTP_CLIENT_ERROR) {
					logger.logDebug("Handling error response");
					in = response.body().byteStream();
					handleErrorResponse(request, serializable, response);
				}

				if (response.code() == HttpResponseCode.HTTP_NOBODY || response.code() == HttpResponseCode.HTTP_NOT_MODIFIED) {
					logger.logDebug("Handling response with no body");
					return handleEmptyResponse(responseHeadersHelper.getResponseHeadersAsMapOfStringList(response), resultClass);
				}

				if (response.code() == HttpResponseCode.HTTP_ACCEPTED) {
					logger.logDebug("Handling accepted response");
					return handleEmptyResponse(responseHeadersHelper.getResponseHeadersAsMapOfStringList(response), resultClass);
				}

				in = new BufferedInputStream(response.body().byteStream());

				final Map<String, String> headers = responseHeadersHelper.getResponseHeadersAsMapStringString(response);

				if (response.body() == null || response.body().contentLength() == 0)
					return (Result) null;

				final String contentType = headers.get(Constants.CONTENT_TYPE_HEADER_NAME);
				if (contentType != null && resultClass != InputStream.class && contentType.contains(Constants.JSON_CONTENT_TYPE)) {
					logger.logDebug("Response json");
					return handleJsonResponse(in, responseHeadersHelper.getResponseHeadersAsMapOfStringList(response), resultClass);
				} else if (resultClass == InputStream.class) {
					logger.logDebug("Response binary");
					isBinaryStreamInput = true;
					return (Result) handleBinaryStream(in);
				} else {
					return (Result) null;
				}
			} finally {
				if (!isBinaryStreamInput) {
					try {
						if (in != null)
							in.close();
					} catch (IOException e) {
						logger.logError(e.getMessage(), e);
					}
					if (response != null)
						response.close();
				}
			}
		} catch (final GraphServiceException ex) {
			final boolean shouldLogVerbosely = logger.getLoggingLevel() == LoggerLevel.DEBUG;
			logger.logError("Graph service exception " + ex.getMessage(shouldLogVerbosely), ex);
			throw ex;
		} catch (final Exception ex) {
			final ClientException clientException = new ClientException("Error during http request", ex);
			logger.logError("Error during http request", clientException);
			throw clientException;
		}
	}

	private Request convertIHttpRequestToOkHttpRequest(IHttpRequest request) {
		if (request != null) {
			Request.Builder requestBuilder = new Request.Builder();
			requestBuilder.url(request.getRequestUrl());
			for (final HeaderOption header : request.getHeaders()) {
				requestBuilder.addHeader(header.getName(), header.getValue().toString());
			}
			return requestBuilder.build();
		}
		return null;
	}

	/**
	 * Handles the event of an error response
	 *
	 * @param request the request that caused the failed response
	 * @param serializable the body of the request
	 * @param connection the URL connection
	 * @param <Body> the type of the request body
	 * @throws IOException an exception occurs if there were any problems interacting with the connection object
	 */
	private <Body> void handleErrorResponse(final IHttpRequest request, final Body serializable, final Response response) throws IOException {
		throw GraphServiceException.createFromConnection(request, serializable, serializer, response, logger);
	}

	/**
	 * Handles the cause where the response is a binary stream
	 *
	 * @param in the input stream from the response
	 * @return the input stream to return to the caller
	 */
	private InputStream handleBinaryStream(final InputStream in) {
		return in;
	}

	/**
	 * Handles the cause where the response is a JSON object
	 *
	 * @param in the input stream from the response
	 * @param responseHeaders the response header
	 * @param clazz the class of the response object
	 * @param <Result> the type of the response object
	 * @return the JSON object
	 */
	private <Result> Result handleJsonResponse(final InputStream in, Map<String, List<String>> responseHeaders, final Class<Result> clazz) {
		if (clazz == null) {
			return null;
		}

		final String rawJson = streamToString(in);
		return getSerializer().deserializeObject(rawJson, clazz, responseHeaders);
	}

	/**
	 * Handles the case where the response body is empty
	 *
	 * @param responseHeaders the response headers
	 * @param clazz the type of the response object
	 * @return the JSON object
	 */
	private <Result> Result handleEmptyResponse(Map<String, List<String>> responseHeaders, final Class<Result> clazz) throws UnsupportedEncodingException {
		// Create an empty object to attach the response headers to
		InputStream in = new ByteArrayInputStream("{}".getBytes(Constants.JSON_ENCODING));
		return handleJsonResponse(in, responseHeaders, clazz);
	}

	/**
	 * Reads in a stream and converts it into a string
	 *
	 * @param input the response body stream
	 * @return the string result
	 */
	public static String streamToString(final InputStream input) {
		final String httpStreamEncoding = "UTF-8";
		final String endOfFile = "\\A";
		final Scanner scanner = new Scanner(input, httpStreamEncoding);
		String scannerString = "";
		try {
			scanner.useDelimiter(endOfFile);
			scannerString = scanner.next();
		} finally {
			scanner.close();
		}
		return scannerString;
	}

	/**
	 * Searches for the given header in a list of HeaderOptions
	 *
	 * @param headers the list of headers to search through
	 * @param header the header name to search for (case insensitive)
	 * @return true if the header has already been set
	 */
	@VisibleForTesting
	static boolean hasHeader(List<HeaderOption> headers, String header) {
		for (HeaderOption option : headers) {
			if (option.getName().equalsIgnoreCase(header)) {
				return true;
			}
		}
		return false;
	}

	@VisibleForTesting
	public ILogger getLogger() {
		return logger;
	}

	@VisibleForTesting
	public IExecutors getExecutors() {
		return executors;
	}

	@VisibleForTesting
	public IAuthenticationProvider getAuthenticationProvider() {
		return authenticationProvider;
	}

	/**
	 * Get connection config for read and connect timeout in requests
	 *
	 * @return Connection configuration to be used for timeout values
	 */
	public IConnectionConfig getConnectionConfig() {
		if (this.connectionConfig == null) {
			this.connectionConfig = new DefaultConnectionConfig();
		}
		return connectionConfig;
	}

	/**
	 * Set connection config for read and connect timeout in requests
	 *
	 * @param connectionConfig Connection configuration to be used for timeout values
	 */
	public void setConnectionConfig(IConnectionConfig connectionConfig) {
		this.connectionConfig = connectionConfig;
	}
}
