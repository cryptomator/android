package org.cryptomator.data.cloud.webdav.network

import java.io.IOException
import java.io.InputStream
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source


internal class InputStreamSourceBasedRequestBody private constructor(private val inputStream: InputStream) : RequestBody() {

	@Throws(IOException::class)
	override fun contentLength(): Long {
		val availableBytes = inputStream.available()
		/**
		 * inputStream.available() is an int and if the file to upload is > int.max it will overflow to 0.
		 * In this case we set contentLength to -1, which is fine, it just means the length is unknown.
		 * If inputStream.available() is actually 0, it does no harm either because we are not uploading a byte.
		 */
		return if (availableBytes != 0) {
			availableBytes.toLong()
		} else {
			-1
		}
	}

	override fun contentType(): MediaType? {
		return "application/octet-stream".toMediaTypeOrNull()
	}

	@Throws(IOException::class)
	override fun writeTo(sink: BufferedSink) {
		inputStream.source().use {
			sink.writeAll(it)
		}
	}

	companion object {

		fun from(data: InputStream): RequestBody {
			return InputStreamSourceBasedRequestBody(data)
		}

	}
}
