package org.cryptomator.presentation.docprovider

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.util.file.MimeTypes
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock

/**
 * Stubs the cloud access, MIME types and vault lookup of Helpers.kt and VaultPath.kt, and makes the app component
 * unavailable. Every other helper runs as usual.
 */
class HelpersMock(
	repository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	cloud: Cloud,
	root: CloudFolder,
	vaults: List<Vault>,
	mimeTypes: MimeTypes = mock(),
	folders: (VaultPath) -> CloudFolder = { root }
) : AutoCloseable {

	private val helpers: MockedStatic<*> = Mockito.mockStatic(Class.forName("org.cryptomator.presentation.docprovider.HelpersKt")) { invocation ->
		when (invocation.method.name) {
			"getContentRepository" -> repository
			"getMimeTypes" -> mimeTypes
			"decryptedView" -> cloud
			"safeResolve" -> folders(invocation.arguments[0] as VaultPath)
			"getAppComponent" -> throw IllegalStateException("The app component is not available in unit tests")
			else -> invocation.callRealMethod()
		}
	}

	private val vaultPaths: MockedStatic<*> = Mockito.mockStatic(Class.forName("org.cryptomator.presentation.docprovider.VaultPathKt")) { invocation ->
		when (invocation.method.name) {
			"vaultById" -> vaults.find { it.id == invocation.arguments[0] }
			else -> invocation.callRealMethod()
		}
	}

	override fun close() {
		vaultPaths.close()
		helpers.close()
	}
}
