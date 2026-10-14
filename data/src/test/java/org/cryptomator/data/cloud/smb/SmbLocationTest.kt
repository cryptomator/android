package org.cryptomator.data.cloud.smb

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SmbLocationTest {

	@ParameterizedTest
	@CsvSource(
		"smb://nas/share, smb://nas/share",
		"smb://nas/share/, smb://nas/share",
		"'  smb://nas/share/folder/  ', smb://nas/share/folder",
		"SMB://nas/share, smb://nas/share",
		"\\\\nas\\share\\folder, smb://nas/share/folder",
		"nas/share, smb://nas/share",
		"'', smb://"
	)
	fun testNormalize(input: String, expected: String) {
		assertThat(SmbLocation.normalize(input), `is`(expected))
	}

	@Test
	fun testParseShareRoot() {
		val location = SmbLocation.parse("smb://nas/share")!!

		assertThat(location.host, `is`("nas"))
		assertThat(location.port, `is`(445))
		assertThat(location.share, `is`("share"))
		assertThat(location.pathOf(""), `is`(""))
		assertThat(location.pathOf("/vault/d"), `is`("vault\\d"))
	}

	@Test
	fun testParseWithPortAndBasePath() {
		val location = SmbLocation.parse("smb://192.168.1.2:1445/share/my folder/sub")!!

		assertThat(location.host, `is`("192.168.1.2"))
		assertThat(location.port, `is`(1445))
		assertThat(location.share, `is`("share"))
		assertThat(location.pathOf(""), `is`("my folder\\sub"))
		assertThat(location.pathOf("/vault/masterkey.cryptomator"), `is`("my folder\\sub\\vault\\masterkey.cryptomator"))
	}

	@Test
	fun testParseIpv6() {
		val location = SmbLocation.parse("smb://[fe80::1]:446/share")!!

		assertThat(location.host, `is`("fe80::1"))
		assertThat(location.port, `is`(446))
	}

	@ParameterizedTest
	@ValueSource(strings = ["smb://", "smb://nas", "smb://nas/", "smb://:445/share", "smb://nas:0/share", "smb://nas:abc/share", "smb://user@nas/share", "smb://nas//share", "https://nas/share", "smb://[fe80::1/share"])
	fun testParseInvalid(url: String) {
		assertThat(SmbLocation.parse(url), `is`(nullValue()))
	}
}
