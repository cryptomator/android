package org.cryptomator.presentation.service

import io.reactivex.BackpressureOverflowStrategy
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import org.cryptomator.presentation.model.CloudNodeModel
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class FolderKey(val vaultId: Long, val parentPath: String)

sealed class UploadUiEvent {
	abstract val folderKey: FolderKey

	data class NodeCreated(override val folderKey: FolderKey, val node: CloudNodeModel<*>) : UploadUiEvent()
	data class UploadFinished(override val folderKey: FolderKey) : UploadUiEvent()
}

@Singleton
class UploadUiUpdates @Inject constructor() {

	private val processor = PublishProcessor.create<UploadUiEvent>().toSerialized()
	private val snapshots = ConcurrentHashMap<FolderKey, ConcurrentHashMap<String, CloudNodeModel<*>>>()

	fun emit(event: UploadUiEvent) {
		when (event) {
			is UploadUiEvent.NodeCreated -> {
				snapshots.computeIfAbsent(event.folderKey) { ConcurrentHashMap() }[event.node.name] = event.node
			}
			is UploadUiEvent.UploadFinished -> {
				snapshots.remove(event.folderKey)
			}
		}
		processor.onNext(event)
	}

	fun eventsFor(folderKey: FolderKey): Flowable<UploadUiEvent> {
		return processor
			.filter { it.folderKey == folderKey }
			.onBackpressureBuffer(512, {
				Timber.tag("UploadUiUpdates").w("Backpressure buffer overflow, dropping oldest event")
			}, BackpressureOverflowStrategy.DROP_OLDEST)
	}

	fun snapshot(folderKey: FolderKey): List<CloudNodeModel<*>> {
		return snapshots[folderKey]?.values?.toList() ?: emptyList()
	}

	fun clear(folderKey: FolderKey) {
		snapshots.remove(folderKey)
	}
}
