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
		return inputStream.available().toLong()
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
