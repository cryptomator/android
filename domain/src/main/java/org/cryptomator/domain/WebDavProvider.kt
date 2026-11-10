package org.cryptomator.domain

import java.net.URLEncoder

private const val SERVER = "{server}"
private const val SPACE_ID = "{space-id}"

enum class WebDavProvider(private val urlTemplate: String) {

	ONE_AND_ONE_DSL("https://sd2dav.1und1.de"), //
	ONE_AND_ONE_WEBHOSTING("https://webdav.office.1und1.de"), //
	DISROOT("https://cloud.disroot.org/remote.php/webdav"), //
	FREENETCLOUD("https://webmail.freenet.de/webdav"), //
	GMX("https://webdav.mc.gmx.net"), //
	HIDRIVE_IONOS("https://webdav.hidrive.ionos.com"), //
	HIDRIVE_STRATO("https://webdav.hidrive.strato.com"), //
	ICEDRIVE("https://webdav.icedrive.io"), //
	KDRIVE("https://connect.drive.infomaniak.com"), //
	KOOFR("https://app.koofr.net/dav/Koofr"), //
	MAGENTACLOUD("https://magentacloud.de/remote.php/webdav"), //
	MAILBOX_ORG("https://dav.mailbox.org/servlet/webdav.infostore"), //
	MAIL_RU("https://webdav.cloud.mail.ru"), //
	NEXTCLOUD("$SERVER/remote.php/webdav"), //
	OPENCLOUD("$SERVER/remote.php/dav/spaces/$SPACE_ID"), //
	OWNCLOUD("$SERVER/remote.php/webdav"), //
	PCLOUD_EU("https://ewebdav.pcloud.com"), //
	PCLOUD_US("https://webdav.pcloud.com"), //
	SEAFILE("$SERVER/seafdav"), //
	STACK("$SERVER/remote.php/webdav"), //
	SWITCHDRIVE("https://drive.switch.ch/remote.php/webdav"), //
	SYNCWERK("https://app.syncwerk.com/seafdav"), //
	WEB_DE("https://webdav.smartdrive.web.de"), //
	WOELKLI("https://cloud.woelkli.com/remote.php/webdav"), //
	YANDEX_DISK("https://webdav.yandex.com");

	val requiresServer: Boolean
		get() = urlTemplate.contains(SERVER)

	val requiresSpaceId: Boolean
		get() = urlTemplate.contains(SPACE_ID)

	fun url(serverUrl: String, spaceId: String): String {
		return urlTemplate //
			.replace(SERVER, serverUrl.trimEnd('/')) //
			.replace(SPACE_ID, encodePathSegment(spaceId))
	}

	private fun encodePathSegment(segment: String): String {
		return URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
	}
}
