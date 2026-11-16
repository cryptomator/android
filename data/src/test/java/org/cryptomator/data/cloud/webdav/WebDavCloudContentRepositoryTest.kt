package org.cryptomator.data.cloud.webdav

import android.content.Context
import org.cryptomator.data.cloud.webdav.network.ConnectionHandlerHandlerImpl
import org.cryptomator.data.util.LocalNetworkAccessDeniedException
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WebDavCloudContentRepositoryTest {

	private val cloud = WebDavCloud.aWebDavCloudCloud().withUrl("https://192.168.178.20:5006/webdav").withUsername("alice").withPassword("encrypted").build()
	private val connectionHandler = mock<ConnectionHandlerHandlerImpl>()
	private val inTest = WebDavCloudContentRepository(cloud, connectionHandler, mock<Context>())

	@Test
	fun testDeniedLocalNetworkAccessIsReportedAsMissingPermission() {
		whenever(connectionHandler.get(any(), any())).thenAnswer { throw FatalBackendException(LocalNetworkAccessDeniedException("192.168.178.20")) }

		assertThrows(LocalNetworkPermissionMissingException::class.java) { inTest.exists(inTest.resolve(cloud, "/vault")) }
	}
}
