package org.cryptomator.presentation.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContentResolverUtilTest {

	private val resolver: ContentResolver = mock()
	private val context: Context = mock { on { contentResolver } doReturn resolver }
	private val uri: Uri = mock { on { scheme } doReturn "content" }

	private val inTest = ContentResolverUtil(context)

	@Test
	fun `a provider that refuses to tell about a document, e g the app's own one of a locked vault, gives no name, size or date`() {
		whenever(resolver.query(uri, null, null, null, null)).thenThrow(SecurityException("locked"))

		assertNull(inTest.fileName(uri))
		assertNull(inTest.fileSize(uri))
		assertNull(inTest.fileModifiedDate(uri))
	}
}
