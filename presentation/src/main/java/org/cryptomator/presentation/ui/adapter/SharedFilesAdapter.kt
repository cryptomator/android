package org.cryptomator.presentation.ui.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import org.cryptomator.presentation.databinding.ItemSharedFilesBinding
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.ui.adapter.SharedFilesAdapter.FileViewHolder
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.Comparators
import javax.inject.Inject

class SharedFilesAdapter @Inject
constructor(private val fileUtil: FileUtil, private val context: Context) : RecyclerViewBaseAdapter<SharedFileModel, SharedFilesAdapter.Callback, FileViewHolder, ItemSharedFilesBinding>(Comparators.naturalOrder()) {

	interface Callback {

		fun onFileNameConflict(hasFileNameConflict: Boolean)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemSharedFilesBinding {
		return ItemSharedFilesBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemSharedFilesBinding, viewType: Int): FileViewHolder {
		return FileViewHolder(binding)
	}

	fun show(files: List<SharedFileModel>?) {
		clear()
		addAll(files)
		callback.onFileNameConflict(hasFileNameConflict())
	}

	private fun hasFileNameConflict(): Boolean {
		val files = HashSet<String>()
		itemCollection.forEach { file ->
			if (!files.add(file.fileName)) {
				return true
			}
		}
		return false
	}

	inner class FileViewHolder(private val binding: ItemSharedFilesBinding) : RecyclerViewBaseAdapter<SharedFileModel, SharedFilesAdapter.Callback, FileViewHolder, ItemSharedFilesBinding>.ItemViewHolder(binding.root) {

		private var etFileNameWatcher: TextWatcher? = null

		override fun bind(position: Int) {
			if (etFileNameWatcher != null) {
				binding.fileName.removeTextChangedListener(etFileNameWatcher)
			}
			val file = getItem(position)
			binding.tilFileName.startIconDrawable = AppCompatResources.getDrawable(context, FileIcon.fileIconFor(file.fileName, fileUtil).iconResource)
			binding.fileName.setText(file.fileName)
			etFileNameWatcher = object : TextWatcher {
				override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

				}

				override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

				}

				override fun afterTextChanged(newFileName: Editable) {
					file.setNewFileName(newFileName.toString())
					callback.onFileNameConflict(hasFileNameConflict())
				}
			}
			binding.fileName.addTextChangedListener(etFileNameWatcher)
		}
	}

}
