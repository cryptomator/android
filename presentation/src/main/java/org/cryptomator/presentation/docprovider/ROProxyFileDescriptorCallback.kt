package org.cryptomator.presentation.docprovider

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.usecases.ProgressAware
import java.io.ByteArrayOutputStream
import kotlin.math.min

//TODO: Throw correct exceptions instead of required
class ROProxyFileDescriptorCallback(private val documentPath: VaultPath) : ProxyFileDescriptorCallback() {

	override fun onRelease() {
		println()
		//NO-OP
	}

	@Throws(ErrnoException::class)
	override fun onGetSize(): Long {
		return fileHandle().size!!
	}

	@Throws(ErrnoException::class)
	override fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
		requireNotNull(data)
		//TODO Handle overflow
		val handle = fileHandle()

		val read = ByteArrayOutputStream(handle.size!!.toInt()).use {
			appComponent.cloudContentRepository().read(handle, null, it, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD) //TODO ProgressAware
			it.toByteArray()
		}

		val end = min(offset.toInt() + size, read.size)
		read.copyInto(data, destinationOffset = 0, startIndex = offset.toInt(), endIndex = end)

		return end - offset.toInt()
	}

	@Throws(ErrnoException::class)
	override fun onWrite(offset: Long, size: Int, data: ByteArray?): Int {
		throw ErrnoException("onWrite", OsConstants.EBADF)
	}

	@Throws(ErrnoException::class)
	override fun onFsync() {
		throw ErrnoException("onFsync", OsConstants.EINVAL)
	}

	private fun fileHandle(): CloudFile {
		val view = appComponent.cloudRepository().decryptedViewOf(documentPath.vault)
		val node = resolveNode(view, documentPath)!!

		require(requireNotNull(node.cloud?.type()) == CloudType.CRYPTO)
		require(node is CloudFile) //TODO Use #file instead if safe

		return node
	}
}