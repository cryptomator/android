package org.cryptomator.data.cloud.s3

import android.content.Context
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.exception.ForbiddenException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.Result
import io.minio.errors.ErrorResponseException
import io.minio.messages.ErrorResponse
import io.minio.messages.Item

class S3ImplTest {

	private val client: MinioClient = mock()
	private val cloud = S3Cloud.aS3Cloud().withS3Bucket("bucket").withAccessKey("access").withSecretKey("secret").build()
	private val root = RootS3Folder(cloud)
	private val inTest = S3Impl(cloud, client, mock<Context>())

	@Test
	fun `a folder without an object of its own is there by what is in it`() {
		givenStatFails(S3CloudApiErrorCodes.NO_SUCH_KEY)
		whenever(client.listObjects(argThat<ListObjectsArgs> { bucket() == "bucket" && prefix() == "a/" })).thenReturn(listOf(Result(mock<Item>())))

		assertTrue(inTest.exists(S3CloudNodeFactory.folder(root, "a")))
	}

	@Test
	fun `a folder without an object of its own and with nothing in it isn't there`() {
		givenStatFails(S3CloudApiErrorCodes.NO_SUCH_KEY)
		whenever(client.listObjects(argThat<ListObjectsArgs> { bucket() == "bucket" && prefix() == "a/" })).thenReturn(emptyList())

		assertFalse(inTest.exists(S3CloudNodeFactory.folder(root, "a")))
	}

	@Test
	fun `a file without its object isn't there, whatever is below its name`() {
		givenStatFails(S3CloudApiErrorCodes.NO_SUCH_KEY)
		whenever(client.listObjects(any())).thenReturn(listOf(Result(mock<Item>())))

		assertFalse(inTest.exists(S3CloudNodeFactory.file(root, "a")))
		verify(client, never()).listObjects(any())
	}

	@Test
	fun `a node that can't be looked up for lack of access fails like the other requests`() {
		givenStatFails(S3CloudApiErrorCodes.ACCESS_DENIED)

		assertThrows<ForbiddenException> { inTest.exists(S3CloudNodeFactory.folder(root, "a")) }
		assertThrows<ForbiddenException> { inTest.metadata(S3CloudNodeFactory.file(root, "a")) }
	}

	@Test
	fun `a folder without an object of its own whose content can't be listed for lack of access fails like the other requests`() {
		givenStatFails(S3CloudApiErrorCodes.NO_SUCH_KEY)
		whenever(client.listObjects(any())).thenReturn(listOf(Result<Item>(error(S3CloudApiErrorCodes.ACCESS_DENIED))))

		assertThrows<ForbiddenException> { inTest.exists(S3CloudNodeFactory.folder(root, "a")) }
	}

	private fun givenStatFails(code: S3CloudApiErrorCodes) {
		whenever(client.statObject(any())).thenThrow(error(code))
	}

	private fun error(code: S3CloudApiErrorCodes): ErrorResponseException {
		return ErrorResponseException(ErrorResponse(code.value, null, null, null, null, null, null), null, null)
	}
}
