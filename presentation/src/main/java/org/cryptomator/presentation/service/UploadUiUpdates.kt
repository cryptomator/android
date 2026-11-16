package org.cryptomator.presentation.service

import io.reactivex.BackpressureOverflowStrategy
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class VaultUploadEvent {
	abstract val vaultId: Long

	data class Started(override val vaultId: Long) : VaultUploadEvent()
	data class Finished(override val vaultId: Long) : VaultUploadEvent()
}

@Singleton
class UploadUiUpdates @Inject constructor() {

	private val vaultProcessor = PublishProcessor.create<VaultUploadEvent>().toSerialized()
	private val activeVaultIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

	fun markVaultActive(vaultId: Long) {
		activeVaultIds.add(vaultId)
		vaultProcessor.onNext(VaultUploadEvent.Started(vaultId))
	}

	fun markVaultFinished(vaultId: Long) {
		activeVaultIds.remove(vaultId)
		vaultProcessor.onNext(VaultUploadEvent.Finished(vaultId))
	}

	fun activeVaultIds(): Set<Long> = HashSet(activeVaultIds)

	fun isVaultActive(vaultId: Long): Boolean = vaultId in activeVaultIds

	fun vaultEvents(): Flowable<VaultUploadEvent> {
		return vaultProcessor
			.onBackpressureBuffer(64, {
				Timber.tag("UploadUiUpdates").w("Vault event buffer overflow, dropping oldest")
			}, BackpressureOverflowStrategy.DROP_OLDEST)
	}
}
