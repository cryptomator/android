package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.data.cloud.webdav.WebDavFolder
import org.cryptomator.data.cloud.webdav.WebDavNode
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.exception.BackendException
import java.io.InputStream
import javax.inject.Inject

class ConnectionHandlerHandlerImpl @Inject internal constructor(httpClient: WebDavCompatibleHttpClient) {

	private val webDavClient: WebDavClient = WebDavClient(httpClient)

	@Throws(BackendException::class)
	fun dirList(url: String, listedFolder: WebDavFolder): List<WebDavNode> {
		return webDavClient.dirList(url, listedFolder)
	}

	@Throws(BackendException::class)
	fun move(from: String, to: String) {
		webDavClient.move(from, to)
	}

	@Throws(BackendException::class)
	fun get(url: String, parent: CloudFolder): WebDavNode? {
		return webDavClient[url, parent]
	}

	@Throws(BackendException::class)
	fun writeFile(url: String, inputStream: InputStream) {
		webDavClient.writeFile(url, inputStream)
	}

	@Throws(BackendException::class)
	fun delete(url: String) {
		webDavClient.delete(url)
	}

	@Throws(BackendException::class)
	fun createFolder(path: String, folder: WebDavFolder): WebDavFolder {
		return webDavClient.createFolder(path, folder)
	}

	@Throws(BackendException::class)
	fun readFile(url: String): InputStream {
		return webDavClient.readFile(url)
	}

	@Throws(BackendException::class)
	fun checkAuthenticationAndServerCompatibility(url: String) {
		webDavClient.checkAuthenticationAndServerCompatibility(url)
	}

}
