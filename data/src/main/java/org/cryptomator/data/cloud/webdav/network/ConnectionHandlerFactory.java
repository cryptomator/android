package org.cryptomator.data.cloud.webdav.network;

import android.content.Context;

import org.cryptomator.domain.WebDavCloud;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ConnectionHandlerFactory {
	private final Context context;

	@Inject
	public ConnectionHandlerFactory(Context context) {
		this.context = context;
	}

	public ConnectionHandlerHandlerImpl createConnectionHandler(WebDavCloud cloud) {
		return new ConnectionHandlerHandlerImpl(new WebDavCompatibleHttpClient(cloud, context), context);
	}
}
