package org.cryptomator.data.repository

import android.content.Context
import com.microsoft.graph.core.ClientException
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.models.Drive
import org.cryptomator.data.cloud.microsoftgraph.MicrosoftGraphClient
import org.cryptomator.data.cloud.microsoftgraph.SharepointSiteId
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.SharepointRepository
import org.cryptomator.util.ExceptionUtil
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharepointRepositoryImpl @Inject constructor(private val context: Context) : SharepointRepository {

	@Throws(BackendException::class)
	override fun drives(cloud: SharepointCloud): List<SharepointCloud> {
		val drives = try {
			loadDrives(cloud)
		} catch (e: GraphServiceException) {
			throw if (e.responseCode == 404) NoSuchCloudFileException(cloud.siteUrl()) else FatalBackendException(e)
		} catch (e: ClientException) {
			throw if (ExceptionUtil.contains(e, IOException::class.java)) NetworkConnectionException(e) else FatalBackendException(e)
		}
		return drives.map { drive ->
			SharepointCloud.aCopyOf(cloud) //
				.withDriveId(drive.id) //
				.withDriveName(drive.name) //
				.build()
		}
	}

	private fun loadDrives(cloud: SharepointCloud): List<Drive> {
		val client = MicrosoftGraphClient.createInstance(context, cloud.accessToken())
		val siteId = client.sites(SharepointSiteId.of(cloud.siteUrl())).buildRequest().get()?.id ?: throw FatalBackendException("Failed to load site, site is null")
		val drives = mutableListOf<Drive>()
		var page = client.sites(siteId).drives().buildRequest().get()
		while (page != null) {
			drives += page.currentPage
			page = page.nextPage?.buildRequest()?.get()
		}
		return drives
	}
}
