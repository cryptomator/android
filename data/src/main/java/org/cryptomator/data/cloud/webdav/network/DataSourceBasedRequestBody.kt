package org.cryptomator.data.cloud.webdav.network

import android.content.Context
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.usecases.cloud.DataSource
import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source


internal class DataSourceBasedRequestBody private constructor( //
	private val context: Context,  //
	private val data: DataSource,  //
	private val size: Long,  //
	private val decorate: (InputStream) -> InputStream
) : RequestBody() {

	override fun contentLength(): Long {
		return size
	}

	override fun contentType(): MediaType? {
		return "application/octet-stream".toMediaTypeOrNull()
	}

	/**
	 * Opens the data again on every invocation instead of consuming a single stream. Without that, OkHttp is unable to
	 * repeat the request after it used a pooled connection the server closed in the meantime, e.g. because of its
	 * keep-alive timeout, and the upload fails instead of being retried using a new connection.
	 * see https://github.com/cryptomator/android/issues/646
	 */
	@Throws(IOException::class)
	override fun writeTo(sink: BufferedSink) {
		data.open(context)?.use { inputStream ->
			decorate(inputStream).source().use {
				sink.writeAll(it)
			}
		} ?: throw FatalBackendException("InputStream shouldn't be null")
	}

	companion object {

		fun from(context: Context, data: DataSource, size: Long, decorate: (InputStream) -> InputStream): RequestBody {
			return DataSourceBasedRequestBody(context, data, size, decorate)
		}

	}
}
