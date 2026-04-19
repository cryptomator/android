package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.view.View
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentTextEditorBinding
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import javax.inject.Inject

@Fragment
class TextEditorFragment : BaseFragment<FragmentTextEditorBinding>(FragmentTextEditorBinding::inflate) {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	val textFileContent: String
		get() = binding.textEditor.text.toString()

	override fun setupView() {
		// no-op
	}

	override fun loadContent() {
		textEditorPresenter.loadFileContent()
	}

	/**
	 * Sets the editor's text to the provided content.
	 *
	 * @param textFileContent The text to display in the editor; passing `null` clears the editor.
	 */
	fun displayTextFileContent(textFileContent: String?) {
		binding.textEditor.setText(textFileContent)
	}

	/**
	 * Sets the text editor to read-only mode.
	 *
	 * Disables focus and cursor visibility so the user cannot edit or place the caret in the editor.
	 */
	fun setReadOnly() {
		binding.textEditor.isFocusable = false
		binding.textEditor.isFocusableInTouchMode = false
		binding.textEditor.isCursorVisible = false
	}

	/**
	 * Initiates a new search for the given query, clears existing highlights, and jumps to the first match.
	 *
	 * Clears current highlight spans; if `query` is empty the method returns after clearing highlights.
	 * Otherwise resets the search position and advances to the next match so the first occurrence is selected and brought into view.
	 *
	 * @param query The search text to locate and highlight in the editor.
	 */
	fun onQueryText(query: String) {
		textEditorPresenter.query = query

		clearSpans(binding.textEditor)

		if (query.isEmpty()) {
			return
		}

		textEditorPresenter.lastFilterLocation = -1

		onNextQuery()
	}

	fun onPreviousQuery() {
		onQuery(Direction.PREVIOUS)
	}

	fun onNextQuery() {
		onQuery(Direction.NEXT)
	}

	private fun onQuery(direction: Direction) {
		if (textEditorPresenter.query == null) {
			return
		}

		clearSpans(binding.textEditor)

		val fulltext = binding.textEditor.text.toString().lowercase()

		textEditorPresenter.query?.lowercase()?.let {
			val index: Int = when (direction) {
				Direction.PREVIOUS -> {
					textEditorPresenter.lastFilterLocation -= 1

					if (textEditorPresenter.lastFilterLocation < 0) {
						return
					}

					fulltext.lastIndexOf(it, textEditorPresenter.lastFilterLocation)
				}
				Direction.NEXT -> {
					textEditorPresenter.lastFilterLocation += 1
					fulltext.indexOf(it, textEditorPresenter.lastFilterLocation)
				}
			}

			if (index < 0) {
				return
			}

			binding.textEditor.text?.setSpan(
				BackgroundColorSpan(ContextCompat.getColor(context(), R.color.colorPrimaryTransparent)),
				index,
				index + it.length,
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)

			textEditorPresenter.lastFilterLocation = index

			binding.textEditor.setSelection(index, index + it.length)
			binding.textEditor.post { binding.textEditor.bringPointIntoView(index) }
		}
	}

	private fun clearSpans(@NonNull editable: TextInputEditText) {
		editable.text
			?.getSpans(0, editable.length(), BackgroundColorSpan::class.java)
			?.forEach { span ->
				editable.text?.removeSpan(span)
			}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.textEditor.applySystemBarsPadding(left = true, right = true, bottom = true)
	}

	enum class Direction { PREVIOUS, NEXT }
}
