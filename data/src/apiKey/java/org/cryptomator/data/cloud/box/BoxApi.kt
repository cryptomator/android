package org.cryptomator.data.cloud.box

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.domain.exception.FatalBackendException
import java.io.IOException
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class BoxApi(private val cloud: BoxCloud, private val tokenStore: BoxTokenStore, private val httpClient: OkHttpClient) {

	private val oAuth = BoxOAuth(httpClient)
	private var tokens: BoxTokens? = null

	@Throws(IOException::class)
	fun currentUser(): BoxUser {
		return BoxHttpClient.parse(execute(Request.Builder().url(CURRENT_USER_URL)), BoxUser::class.java)
	}

	@Throws(IOException::class)
	fun folderItems(folderId: String, marker: String?): BoxItems {
		val url = "$API_URL/folders/$folderId/items".toHttpUrl().newBuilder() //
			.addQueryParameter("fields", ITEM_FIELDS) //
			.addQueryParameter("usemarker", "true") //
			.addQueryParameter("limit", PAGE_SIZE.toString()) //
			.apply { marker?.let { addQueryParameter("marker", it) } } //
			.build()
		return BoxHttpClient.parse(execute(Request.Builder().url(url)), BoxItems::class.java)
	}

	@Throws(IOException::class)
	fun fileInfo(fileId: String): BoxItem {
		return BoxHttpClient.parse(execute(Request.Builder().url("$API_URL/files/$fileId?fields=$ITEM_FIELDS")), BoxItem::class.java)
	}

	/**
	 * Uses the upload preflight check to find the item already using the name in the folder in a single request. Returns `null` if the name is free.
	 */
	@Throws(IOException::class)
	fun itemNamed(parentId: String, name: String): BoxItem? {
		val body = json(mapOf("name" to name, "parent" to mapOf("id" to parentId)))
		val response = execute(Request.Builder().url("$API_URL/files/content").method("OPTIONS", body))
		if (response.code != 409) {
			BoxHttpClient.requireSuccess(response)
			return null
		}
		val conflict = response.use { BoxHttpClient.apiException(it.code, it.body?.string() ?: "") }
		return conflict.conflict ?: throw conflict
	}

	@Throws(IOException::class)
	fun createFolder(parentId: String, name: String): BoxItem {
		val body = json(mapOf("name" to name, "parent" to mapOf("id" to parentId)))
		return BoxHttpClient.parse(execute(Request.Builder().url("$API_URL/folders?fields=$ITEM_FIELDS").post(body)), BoxItem::class.java)
	}

	@Throws(IOException::class)
	fun move(item: BoxIdCache.NodeInfo, targetParentId: String, targetName: String): BoxItem {
		val body = json(mapOf("name" to targetName, "parent" to mapOf("id" to targetParentId)))
		return BoxHttpClient.parse(execute(Request.Builder().url("$API_URL/${itemsPath(item)}/${item.id}?fields=$ITEM_FIELDS").put(body)), BoxItem::class.java)
	}

	@Throws(IOException::class)
	fun delete(item: BoxIdCache.NodeInfo) {
		val url = if (item.isFolder) "$API_URL/folders/${item.id}?recursive=true" else "$API_URL/files/${item.id}"
		BoxHttpClient.requireSuccess(execute(Request.Builder().url(url).delete()))
	}

	/**
	 * The caller has to close the returned response.
	 */
	@Throws(IOException::class)
	fun download(fileId: String): Response {
		val response = executeWaitingWhileProcessing(Request.Builder().url("$API_URL/files/$fileId/content"))
		if (!response.isSuccessful) {
			BoxHttpClient.requireSuccess(response)
		}
		return response
	}

	@Throws(IOException::class)
	fun upload(parentId: String, name: String, modified: Date?, content: RequestBody): BoxItem {
		val attributes = mapOf("name" to name, "parent" to mapOf("id" to parentId)) + contentModifiedAt(modified)
		return uploadContent("$UPLOAD_URL/files/content", attributes, name, content)
	}

	@Throws(IOException::class)
	fun uploadVersion(fileId: String, name: String, modified: Date?, content: RequestBody): BoxItem {
		val attributes = mapOf("name" to name) + contentModifiedAt(modified)
		return uploadContent("$UPLOAD_URL/files/$fileId/content", attributes, name, content)
	}

	private fun uploadContent(url: String, attributes: Map<String, Any>, name: String, content: RequestBody): BoxItem {
		val body = MultipartBody.Builder() //
			.setType(MultipartBody.FORM) //
			.addFormDataPart("attributes", BoxHttpClient.gson.toJson(attributes)) // Box requires the attributes before the file
			.addFormDataPart("file", name, content) //
			.build()
		return firstEntry(BoxHttpClient.parse(execute(Request.Builder().url(url).post(body)), BoxItems::class.java))
	}

	@Throws(IOException::class)
	fun createUploadSession(parentId: String, name: String, size: Long): BoxUploadSession {
		val body = json(mapOf("folder_id" to parentId, "file_name" to name, "file_size" to size))
		return BoxHttpClient.parse(execute(Request.Builder().url("$UPLOAD_URL/files/upload_sessions").post(body)), BoxUploadSession::class.java)
	}

	@Throws(IOException::class)
	fun createVersionUploadSession(fileId: String, name: String, size: Long): BoxUploadSession {
		val body = json(mapOf("file_name" to name, "file_size" to size))
		return BoxHttpClient.parse(execute(Request.Builder().url("$UPLOAD_URL/files/$fileId/upload_sessions").post(body)), BoxUploadSession::class.java)
	}

	@Throws(IOException::class)
	fun uploadPart(session: BoxUploadSession, part: ByteArray, length: Int, offset: Long, totalSize: Long): BoxUploadPart {
		val request = Request.Builder() //
			.url(session.sessionEndpoints?.uploadPart ?: "$UPLOAD_URL/files/upload_sessions/${session.id}") //
			.header("Digest", "sha=" + BoxDigest.sha1Base64(part, length)) //
			.header("Content-Range", "bytes $offset-${offset + length - 1}/$totalSize") //
			.put(part.toRequestBody(OCTET_STREAM, 0, length))
		return BoxHttpClient.parse(execute(request), BoxUploadedPart::class.java).part
	}

	@Throws(IOException::class)
	fun abortUploadSession(session: BoxUploadSession) {
		val url = session.sessionEndpoints?.abort ?: "$UPLOAD_URL/files/upload_sessions/${session.id}"
		BoxHttpClient.requireSuccess(execute(Request.Builder().url(url).delete()))
	}

	@Throws(IOException::class)
	fun commitUploadSession(session: BoxUploadSession, parts: List<BoxUploadPart>, fileSha1Base64: String, modified: Date?): BoxItem {
		val body = json(mapOf("parts" to parts, "attributes" to contentModifiedAt(modified)))
		val request = Request.Builder() //
			.url(session.sessionEndpoints?.commit ?: "$UPLOAD_URL/files/upload_sessions/${session.id}/commit") //
			.header("Digest", "sha=$fileSha1Base64") //
			.post(body)
		return firstEntry(BoxHttpClient.parse(executeWaitingWhileProcessing(request), BoxItems::class.java))
	}

	/**
	 * Also repeats the request as long as Box answers 202, which it does while a file isn't ready for download or the parts of an upload are still being processed.
	 */
	@Throws(IOException::class)
	private fun executeWaitingWhileProcessing(request: Request.Builder): Response {
		return executeRetrying(request) { it == 202 || it == 429 }
	}

	/**
	 * Repeats the request after the delay Box asks for if it rate limits the request.
	 */
	@Throws(IOException::class)
	private fun execute(request: Request.Builder): Response {
		return executeRetrying(request) { it == 429 }
	}

	private fun executeRetrying(request: Request.Builder, retryOn: (Int) -> Boolean): Response {
		repeat(MAX_RETRY_ATTEMPTS) {
			val response = executeAuthorized(request)
			if (!retryOn(response.code)) {
				return response
			}
			val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 1L
			response.close()
			try {
				Thread.sleep(retryAfterSeconds * 1000)
			} catch (e: InterruptedException) {
				Thread.currentThread().interrupt()
				throw CancellationException(e)
			}
		}
		throw FatalBackendException("Box still asks to retry after $MAX_RETRY_ATTEMPTS attempts")
	}

	private fun firstEntry(items: BoxItems): BoxItem {
		return items.entries?.firstOrNull() ?: throw FatalBackendException("Box returned no item")
	}

	private fun contentModifiedAt(modified: Date?): Map<String, String> {
		return modified?.let { mapOf("content_modified_at" to RFC_3339.format(it.toInstant().atOffset(ZoneOffset.UTC))) } ?: emptyMap()
	}

	private fun itemsPath(item: BoxIdCache.NodeInfo): String {
		return if (item.isFolder) "folders" else "files"
	}

	private fun json(value: Any): RequestBody {
		return BoxHttpClient.gson.toJson(value).toRequestBody(JSON)
	}

	/**
	 * Executes the request with a valid access token, refreshing it once if Box rejects it.
	 */
	@Throws(IOException::class)
	private fun executeAuthorized(request: Request.Builder): Response {
		val used = validTokens()
		val response = httpClient.newCall(request.header("Authorization", "Bearer ${used.accessToken}").build()).execute()
		if (response.code != 401) {
			return response
		}
		response.close()
		val refreshed = refresh(used)
		return httpClient.newCall(request.header("Authorization", "Bearer ${refreshed.accessToken}").build()).execute()
	}

	@Synchronized
	private fun validTokens(): BoxTokens {
		val current = tokens ?: tokenStore.load(cloud)
		return (if (current.expiresSoon()) tokenStore.refresh(cloud, current, oAuth) else current).also { tokens = it }
	}

	@Synchronized
	private fun refresh(rejected: BoxTokens): BoxTokens {
		return tokenStore.refresh(cloud, rejected, oAuth).also { tokens = it }
	}

	companion object {

		const val ROOT_FOLDER_ID = "0"

		/**
		 * Used while logging in, before the tokens are stored, so a rejected access token fails the login instead of being refreshed.
		 */
		@Throws(IOException::class)
		fun currentUser(httpClient: OkHttpClient, accessToken: String): BoxUser {
			val request = Request.Builder().url(CURRENT_USER_URL).header("Authorization", "Bearer $accessToken").build()
			return BoxHttpClient.parse(httpClient.newCall(request).execute(), BoxUser::class.java)
		}

		private const val API_URL = "https://api.box.com/2.0"
		private const val UPLOAD_URL = "https://upload.box.com/api/2.0"
		private const val CURRENT_USER_URL = "$API_URL/users/me?fields=id,login,name"
		private const val ITEM_FIELDS = "type,id,name,size,modified_at,content_modified_at,sha1"
		private const val PAGE_SIZE = 1000
		private const val MAX_RETRY_ATTEMPTS = 10
		private val JSON = "application/json; charset=utf-8".toMediaType()
		private val OCTET_STREAM = "application/octet-stream".toMediaType()
		private val RFC_3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
	}
}
