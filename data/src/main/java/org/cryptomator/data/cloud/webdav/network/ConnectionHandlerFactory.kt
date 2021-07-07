package org.cryptomator.data.cloud.webdav.network

import android.content.Context
import org.cryptomator.domain.WebDavCloud
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionHandlerFactory @Inject constructor(private val context: Context) {

	fun createConnectionHandler(cloud: WebDavCloud): ConnectionHandlerHandlerImpl {
		return ConnectionHandlerHandlerImpl(WebDavCompatibleHttpClient(cloud, context), context)
	}
}
