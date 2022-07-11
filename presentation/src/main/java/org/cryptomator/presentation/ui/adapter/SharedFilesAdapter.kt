package org.cryptomator.presentation.ui.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.ui.adapter.SharedFilesAdapter.FileViewHolder
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.Comparators
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_shared_files.view.fileName
import kotlinx.android.synthetic.main.item_shared_files.view.til_file_name

class SharedFilesAdapter @Inject
constructor(private val fileUtil: FileUtil, private val context: Context) : RecyclerViewBaseAdapter<SharedFileModel, SharedFilesAdapter.Callback, FileViewHolder>(Comparators.naturalOrder()) {

	interface Callback {

		fun onFileNameConflict(hasFileNameConflict: Boolean)
	}

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_shared_files
	}

	override fun createViewHolder(view: View, viewType: Int): FileViewHolder {
		return FileViewHolder(view)
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

	inner class FileViewHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		private var et_file_name_watcher: TextWatcher? = null

		override fun bind(position: Int) {
			if (et_file_name_watcher != null) {
				itemView.fileName.removeTextChangedListener(et_file_name_watcher)
			}
			val file = getItem(position)
			itemView.til_file_name.startIconDrawable = AppCompatResources.getDrawable(context, FileIcon.fileIconFor(file.fileName, fileUtil).iconResource)
			itemView.fileName.setText(file.fileName)
			et_file_name_watcher = object : TextWatcher {
				override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

				}

				override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

				}

				override fun afterTextChanged(newFileName: Editable) {
					file.setNewFileName(newFileName.toString())
					callback.onFileNameConflict(hasFileNameConflict())
				}
			}
			itemView.fileName.addTextChangedListener(et_file_name_watcher)
		}
	}

}
