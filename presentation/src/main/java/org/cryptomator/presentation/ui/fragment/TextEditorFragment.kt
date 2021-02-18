package org.cryptomator.presentation.ui.fragment

import android.text.Spannable
import android.text.style.BackgroundColorSpan
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.TextEditorPresenter
import java.util.Locale
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_text_editor.textEditor
import kotlinx.android.synthetic.main.fragment_text_editor.textViewWrapper

@Fragment(R.layout.fragment_text_editor)
class TextEditorFragment : BaseFragment() {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	val textFileContent: String
		get() = textEditor.text.toString()

	override fun setupView() {
		// no-op
	}

	override fun loadContent() {
		textEditorPresenter.loadFileContent()
	}

	fun displayTextFileContent(textFileContent: String?) {
		textEditor.setText(textFileContent)
	}

	fun onQueryText(query: String) {
		textEditorPresenter.query = query

		clearSpans(textEditor)

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

		clearSpans(textEditor)

		val fulltext = textEditor.text.toString().toLowerCase(Locale.getDefault())

		textEditorPresenter.query?.toLowerCase(Locale.getDefault())?.let {
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

			textEditor.text?.setSpan(
					BackgroundColorSpan(ContextCompat.getColor(context(), R.color.colorPrimaryTransparent)),
					index,
					index + it.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

			textEditorPresenter.lastFilterLocation = index

			textViewWrapper.scrollTo(0, textEditor.layout.getLineTop(textEditor.layout.getLineForOffset(index)))
		}
	}

	private fun clearSpans(@NonNull editable: TextInputEditText) {
		editable.text
				?.getSpans(0, editable.length(), BackgroundColorSpan::class.java)
				?.forEach { span ->
					editable.text?.removeSpan(span)
				}
	}

	enum class Direction { PREVIOUS, NEXT }
}
