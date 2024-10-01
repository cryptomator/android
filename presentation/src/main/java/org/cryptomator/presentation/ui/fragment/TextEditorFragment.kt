package org.cryptomator.presentation.ui.fragment

import android.text.Spannable
import android.text.style.BackgroundColorSpan
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentTextEditorBinding
import org.cryptomator.presentation.presenter.TextEditorPresenter
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

	fun displayTextFileContent(textFileContent: String?) {
		binding.textEditor.setText(textFileContent)
	}

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

			binding.textViewWrapper.scrollTo(0, binding.textEditor.layout.getLineTop(binding.textEditor.layout.getLineForOffset(index)))
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
