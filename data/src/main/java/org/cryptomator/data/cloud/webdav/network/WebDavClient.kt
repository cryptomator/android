package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.data.cloud.webdav.WebDavFolder
import org.cryptomator.data.cloud.webdav.WebDavNode
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.exception.AlreadyExistException
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.ForbiddenException
import org.cryptomator.domain.exception.NotFoundException
import org.cryptomator.domain.exception.ParentFolderDoesNotExistException
import org.cryptomator.domain.exception.TypeMismatchException
import org.cryptomator.domain.exception.UnauthorizedException
import org.cryptomator.util.Optional
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.Collections
import java.util.Date
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class WebDavClient(private val httpClient: WebDavCompatibleHttpClient) {

	private val ASCENDING_BY_DEPTH = Comparator { o1: PropfindEntryData, o2: PropfindEntryData -> o1.getDepth() - o2.getDepth() }

	@Throws(BackendException::class)
	fun dirList(url: String, listedFolder: WebDavFolder): List<WebDavNode> {
		try {
			executePropfindRequest(url, PropfindDepth.ONE).use { response ->
				checkPropfindExecutionSucceeded(response.code)
				val nodes = getEntriesFromResponse(listedFolder, response)
				return processDirList(nodes, listedFolder)
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		} catch (e: XmlPullParserException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	operator fun get(url: String, parent: CloudFolder): WebDavNode? {
		try {
			executePropfindRequest(url, PropfindDepth.ZERO).use { response ->
				checkPropfindExecutionSucceeded(response.code)
				val nodes = getEntriesFromResponse(parent as WebDavFolder, response)
				return processGet(nodes, parent)
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		} catch (e: XmlPullParserException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(IOException::class)
	private fun executePropfindRequest(url: String, depth: PropfindDepth): Response {
		val body = """<?xml version="1.0" encoding="utf-8"?>
						<d:propfind xmlns:d="DAV:">
						<d:prop>
						<d:resourcetype />
						<d:getcontentlength />
						<d:getlastmodified />
						</d:prop>
						</d:propfind>"""
		val builder = Request.Builder() //
			.method("PROPFIND", body.toRequestBody(body.toMediaTypeOrNull())) //
			.url(url) //
			.header("DEPTH", depth.value) //
			.header("Content-Type", "text/xml")
		return httpClient.execute(builder)
	}

	@Throws(BackendException::class)
	private fun checkPropfindExecutionSucceeded(responseCode: Int) {
		when (responseCode) {
			HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
			HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
			HttpURLConnection.HTTP_NOT_FOUND -> throw NotFoundException()
		}
		if (responseCode < 199 || responseCode > 300) {
			throw FatalBackendException("Response code isn't between 200 and 300: $responseCode")
		}
	}

	@Throws(IOException::class, XmlPullParserException::class)
	private fun getEntriesFromResponse(listedFolder: WebDavFolder, response: Response): List<PropfindEntryData> {
		return response.body?.use { responseBody -> return PropfindResponseParser(listedFolder).parse(responseBody.byteStream()) } ?: emptyList()
	}

	@Throws(BackendException::class)
	fun move(from: String, to: String) {
		val builder = Request.Builder() //
			.method("MOVE", null) //
			.url(from) //
			.header("Content-Type", "text/xml") //
			.header("Destination", to) //
			.header("Depth", "infinity") //
			.header("Overwrite", "F")
		try {
			httpClient.execute(builder).use { response ->
				if (!response.isSuccessful) {
					when (response.code) {
						HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
						HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
						HttpURLConnection.HTTP_NOT_FOUND -> throw NotFoundException()
						HttpURLConnection.HTTP_CONFLICT -> throw ParentFolderDoesNotExistException()
						HttpURLConnection.HTTP_PRECON_FAILED -> throw CloudNodeAlreadyExistsException(to)
						else -> throw FatalBackendException("Response code isn't between 200 and 300: " + response.code)
					}
				}
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	fun readFile(url: String): InputStream {
		val builder = Request.Builder() //
			.get() //
			.url(url)
		var response: Response? = null
		var success = false
		return try {
			response = httpClient.execute(builder)
			if (response.isSuccessful) {
				success = true
				response.body?.byteStream() ?: throw FatalBackendException("Response body is null")
			} else {
				when (response.code) {
					HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
					HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
					HttpURLConnection.HTTP_NOT_FOUND -> throw NotFoundException()
					416 -> ByteArrayInputStream(ByteArray(0))
					else -> throw FatalBackendException("Response code isn't between 200 and 300: " + response.code)
				}
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		} finally {
			if (response != null && !success) {
				response.close()
			}
		}
	}

	@Throws(BackendException::class)
	fun writeFile(url: String, inputStream: InputStream, modifiedDate: Date) {
		val builder = Request.Builder() //
			.addHeader("X-OC-Mtime", modifiedDate.toInstant().toEpochMilli().div(1000).toString()) //
			.put(InputStreamSourceBasedRequestBody.from(inputStream)) //
			.url(url)
		try {
			httpClient.execute(builder).use { response ->
				if (!response.isSuccessful) {
					when (response.code) {
						HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
						HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
						HttpURLConnection.HTTP_BAD_METHOD -> throw TypeMismatchException()
						HttpURLConnection.HTTP_CONFLICT, HttpURLConnection.HTTP_NOT_FOUND -> throw ParentFolderDoesNotExistException()
						else -> throw FatalBackendException("Response code isn't between 200 and 300: " + response.code)
					}
				}
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	fun createFolder(path: String, folder: WebDavFolder): WebDavFolder {
		val builder = Request.Builder() //
			.method("MKCOL", null) //
			.url(path)
		try {
			httpClient.execute(builder).use { response ->
				return if (response.isSuccessful) {
					folder
				} else {
					when (response.code) {
						HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
						HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
						HttpURLConnection.HTTP_BAD_METHOD -> throw AlreadyExistException()
						HttpURLConnection.HTTP_CONFLICT -> throw ParentFolderDoesNotExistException()
						else -> throw FatalBackendException("Response code isn't between 200 and 300: " + response.code)
					}
				}
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	fun delete(url: String) {
		val builder = Request.Builder() //
			.delete() //
			.url(url)
		try {
			httpClient.execute(builder).use { response ->
				if (!response.isSuccessful) {
					when (response.code) {
						HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
						HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
						HttpURLConnection.HTTP_NOT_FOUND -> throw NotFoundException(String.format("Node %s doesn't exists", url))
						else -> throw FatalBackendException("Response code isn't between 200 and 300: " + response.code)
					}
				}
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	fun checkAuthenticationAndServerCompatibility(url: String) {
		val optionsRequest = Request.Builder() //
			.method("OPTIONS", null) //
			.url(url)
		try {
			httpClient.execute(optionsRequest).use { response ->
				if (response.isSuccessful) {
					val containsDavHeader = response.headers.names().contains("DAV")
					if (!containsDavHeader) {
						throw ServerNotWebdavCompatibleException()
					}
				} else {
					when (response.code) {
						HttpURLConnection.HTTP_UNAUTHORIZED -> throw UnauthorizedException()
						HttpURLConnection.HTTP_FORBIDDEN -> throw ForbiddenException()
						else -> throw FatalBackendException("Response code isn't between 200 and 300: " + response.code)
					}
				}
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
		try {
			executePropfindRequest(url, PropfindDepth.ZERO).use { response -> checkPropfindExecutionSucceeded(response.code) }
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	private fun processDirList(entryData: List<PropfindEntryData>, requestedFolder: WebDavFolder): List<WebDavNode> {
		Collections.sort(entryData, ASCENDING_BY_DEPTH)
		// after sorting the first entry is the parent
		// because it's depth is 1 smaller than the depth
		// ot the other entries, thus we skip the first entry
		return entryData.subList(1, entryData.size).mapTo(ArrayList()) { it.toCloudNode(requestedFolder) }
	}

	private fun processGet(entryData: List<PropfindEntryData>, requestedFolder: WebDavFolder): WebDavNode? {
		Collections.sort(entryData, ASCENDING_BY_DEPTH)
		return if (entryData.isNotEmpty()) entryData[0].toCloudNode(requestedFolder) else null
	}

	private enum class PropfindDepth(val value: String) {
		ZERO("0"),  //
		ONE("1"),  //
		INFINITY("infinity");
	}
}
