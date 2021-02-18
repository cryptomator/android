package org.cryptomator.data.cloud.webdav.network;

import android.content.Context;

import org.cryptomator.domain.usecases.cloud.DataSource;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;

class DataSourceBasedRequestBody extends RequestBody {

	private final Context context;
	private final DataSource data;

	private DataSourceBasedRequestBody(Context context, DataSource data) {
		this.context = context;
		this.data = data;
	}

	public static RequestBody from(Context context, DataSource data) {
		return new DataSourceBasedRequestBody(context, data);
	}

	@Override
	public long contentLength() {
		return data.size(context).get();
	}

	@Override
	public MediaType contentType() {
		return MediaType.parse("application/octet-stream");
	}

	@Override
	public void writeTo(BufferedSink sink) throws IOException {
		sink.writeAll(Okio.buffer(Okio.source(data.open(context))));
	}
}
