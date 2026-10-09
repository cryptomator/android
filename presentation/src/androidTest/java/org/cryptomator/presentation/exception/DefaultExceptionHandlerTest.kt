package org.cryptomator.presentation.exception

import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

@RunWith(androidx.test.runner.AndroidJUnit4::class)
@SmallTest
class DefaultExceptionHandlerTest {

	private lateinit var handler: DefaultExceptionHandler
	private lateinit var deviceStorageMessage: String
	private lateinit var genericMessage: String

	@Before
	fun setUp() {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		handler = DefaultExceptionHandler(context)
		deviceStorageMessage = context.getString(R.string.error_device_storage_full)
		genericMessage = context.getString(R.string.error_generic)
		assertNotEquals(genericMessage, deviceStorageMessage)
	}

	@Test
	fun fatalBackendExceptionWrappingWriteEnospcShowsDeviceStorageMessage() {
		val failure = FatalBackendException(IOException(ErrnoException("write", OsConstants.ENOSPC)))

		assertShows(deviceStorageMessage, failure)
	}

	@Test
	fun directAndWrappedEnospcShowTheSameMessageRegardlessOfOperationOrWrapperText() {
		assertShows(deviceStorageMessage, ErrnoException("read", OsConstants.ENOSPC))
		assertShows(
			deviceStorageMessage,
			FatalBackendException(
				"cloud rejected the upload",
				RuntimeException("intermediate", IOException("inner", ErrnoException("fsync", OsConstants.ENOSPC)))
			)
		)
	}

	@Test
	fun otherErrnoValuesPlainIoAndExceptionsWithoutACauseStayGeneric() {
		assertShows(genericMessage, ErrnoException("open", OsConstants.EACCES))
		assertShows(genericMessage, ErrnoException("write", OsConstants.EIO))
		assertShows(genericMessage, IOException("write failed"))
		assertShows(genericMessage, IllegalStateException("no cause"))
	}

	@Test
	fun ioExceptionTextWithoutAnEnospcCauseStaysGeneric() {
		assertShows(genericMessage, IOException("No space left on device"))
	}

	private fun assertShows(expected: String, failure: Throwable) {
		val shown = mutableListOf<String>()
		var showErrorCalls = 0
		val view = Proxy.newProxyInstance(
			View::class.java.classLoader,
			arrayOf(View::class.java),
			InvocationHandler { _, method, args ->
				if (method.name == "showError") {
					showErrorCalls++
					if (args != null && args.size == 1 && args[0] is String) {
						shown.add(args[0] as String)
					}
				}
				null
			}
		) as View

		assertTrue(handler.handle(view, failure))
		assertEquals(1, showErrorCalls)
		assertEquals(listOf(expected), shown)
	}

}
