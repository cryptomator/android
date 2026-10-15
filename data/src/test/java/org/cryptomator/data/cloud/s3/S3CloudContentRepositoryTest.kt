package org.cryptomator.data.cloud.s3

import android.content.Context
import org.cryptomator.data.util.LocalNetworkAccessDeniedException
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import io.minio.MinioClient

class S3CloudContentRepositoryTest {

	private val cloud = S3Cloud.aS3Cloud().withS3Bucket("bucket").withS3Endpoint("http://10.0.0.5:9000").withAccessKey("accessKey").withSecretKey("secretKey").build()
	private val client = mock<MinioClient>()
	private val inTest = S3CloudContentRepository(cloud, client, mock<Context>())

	@Test
	fun testDeniedLocalNetworkAccessIsReportedAsMissingPermission() {
		// MinIO wraps transport errors; a missing permission must not end up as a generic network error
		whenever(client.bucketExists(any())).thenAnswer { throw IllegalStateException(LocalNetworkAccessDeniedException("10.0.0.5")) }

		assertThrows(LocalNetworkPermissionMissingException::class.java) { inTest.exists(inTest.root(cloud)) }
	}
}
