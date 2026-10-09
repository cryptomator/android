package org.cryptomator.data.cloud.box

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

internal class BoxItem(
	@SerializedName("type") val type: String?,
	@SerializedName("id") val id: String,
	@SerializedName("name") val name: String?,
	@SerializedName("size") val size: Long?,
	@SerializedName("modified_at") val modifiedAt: String?,
	@SerializedName("content_modified_at") val contentModifiedAt: String?,
	@SerializedName("sha1") val sha1: String?
) {

	val isFolder: Boolean
		get() = type == "folder"

	val isFile: Boolean
		get() = type == "file"
}

internal class BoxItems(
	@SerializedName("entries") val entries: List<BoxItem>?,
	@SerializedName("next_marker") val nextMarker: String?
)

internal class BoxUser(
	@SerializedName("id") val id: String?,
	@SerializedName("login") val login: String?,
	@SerializedName("name") val name: String?
)

internal class BoxError(
	@SerializedName("code") val code: String?,
	@SerializedName("message") val message: String?,
	@SerializedName("error") val error: String?,
	@SerializedName("error_description") val errorDescription: String?,
	@SerializedName("context_info") val contextInfo: BoxErrorContext?
)

internal class BoxErrorContext(
	@SerializedName("conflicts") val conflicts: JsonElement?
)

internal class BoxTokenResponse(
	@SerializedName("access_token") val accessToken: String,
	@SerializedName("refresh_token") val refreshToken: String,
	@SerializedName("expires_in") val expiresIn: Long
)

internal class BoxUploadSession(
	@SerializedName("id") val id: String,
	@SerializedName("part_size") val partSize: Long,
	@SerializedName("session_endpoints") val sessionEndpoints: BoxUploadSessionEndpoints?
)

internal class BoxUploadSessionEndpoints(
	@SerializedName("upload_part") val uploadPart: String?,
	@SerializedName("commit") val commit: String?,
	@SerializedName("abort") val abort: String?
)

internal class BoxUploadedPart(
	@SerializedName("part") val part: BoxUploadPart
)

internal class BoxUploadPart(
	@SerializedName("part_id") val partId: String,
	@SerializedName("offset") val offset: Long,
	@SerializedName("size") val size: Long,
	@SerializedName("sha1") val sha1: String?
)
