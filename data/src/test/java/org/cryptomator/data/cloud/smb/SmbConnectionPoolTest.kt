package org.cryptomator.data.cloud.smb

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMB2Packet
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.connection.ConnectionContext
import com.hierynomus.smbj.connection.NegotiatedProtocol
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.TreeConnect
import org.cryptomator.data.cloud.smb.SmbConnectionPool.Retry
import org.cryptomator.data.util.LocalNetworkAccess
import org.cryptomator.data.util.LocalNetworkAccessDeniedException
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SmbConnectionPoolTest {

	private val location = SmbLocation.parse("smb://nas/share")!!
	private val config = SmbConnectionPool.Config(location, "alice", "encryptedPassword") { "secret" }
	private val localNetworkAccess = mock<LocalNetworkAccess>()
	private val servers = ArrayDeque<Server>()
	private var now = 0L
	private val inTest = SmbConnectionPool({ servers.removeFirst().client }, localNetworkAccess, { now })

	@Test
	fun testConnectionIsReused() {
		val server = server()

		val first = withShare(Retry.NEVER) { it }
		val second = withShare(Retry.NEVER) { it }

		assertThat(first, sameInstance(server.share))
		assertThat(second, sameInstance(server.share))
		verify(server.client).connect("nas", 445)
	}

	@Test
	fun testDisconnectedTransportIsReplaced() {
		val first = server()
		val second = server()
		withShare(Retry.NEVER) { it }
		whenever(first.connection.isConnected).thenReturn(false)

		val share = withShare(Retry.NEVER) { it }

		assertThat(share, sameInstance(second.share))
		verify(first.connection).close(true)
	}

	@Test
	fun testChangedPasswordUsesNewSession() {
		val first = server()
		val second = server()
		withShare(Retry.NEVER) { it }

		val share = inTest.withShare(SmbConnectionPool.Config(location, "alice", "otherEncryptedPassword") { "other" }, Retry.NEVER) { it }

		assertThat(share, sameInstance(second.share))
		verify(first.connection).close(true)
	}

	@Test
	fun testOtherShareGetsOwnConnection() {
		val home = server()
		val backup = server("backup")
		withShare(Retry.NEVER) { it }

		val share = inTest.withShare(SmbConnectionPool.Config(SmbLocation.parse("smb://nas/backup")!!, "alice", "encryptedPassword") { "secret" }, Retry.NEVER) { it }

		assertThat(share, sameInstance(backup.share))
		verify(home.connection, never()).close(true)
	}

	@Test
	fun testOtherUserGetsOwnConnection() {
		val alice = server()
		val bob = server()
		withShare(Retry.NEVER) { it }

		val share = inTest.withShare(SmbConnectionPool.Config(location, "bob", "encryptedPassword") { "secret" }, Retry.NEVER) { it }

		assertThat(share, sameInstance(bob.share))
		verify(alice.connection, never()).close(true)
	}

	@Test
	fun testPasswordIsDecryptedOnlyToOpenTheSession() {
		server()
		var decryptions = 0
		val config = SmbConnectionPool.Config(location, "alice", "encryptedPassword") { decryptions++; "secret" }

		inTest.withShare(config, Retry.NEVER) { it }
		inTest.withShare(config, Retry.NEVER) { it }

		assertThat(decryptions, `is`(1))
	}

	@Test
	fun testIdleConnectionIsReusedIfServerAnswersEcho() {
		val server = server()
		withShare(Retry.NEVER) { it }
		val echo = mock<Future<SMB2Packet>>()
		whenever(server.connection.send<SMB2Packet>(any())).thenReturn(echo)
		now += TimeUnit.SECONDS.toNanos(31)

		val share = withShare(Retry.NEVER) { it }

		assertThat(share, sameInstance(server.share))
		verify(echo).get(5, TimeUnit.SECONDS)
	}

	@Test
	fun testIdleConnectionIsReplacedIfServerDoesntAnswerEcho() {
		val first = server()
		val second = server()
		withShare(Retry.NEVER) { it }
		val echo = mock<Future<SMB2Packet>>()
		whenever(echo.get(5, TimeUnit.SECONDS)).thenThrow(TimeoutException())
		whenever(first.connection.send<SMB2Packet>(any())).thenReturn(echo)
		now += TimeUnit.SECONDS.toNanos(31)

		val share = withShare(Retry.NEVER) { it }

		assertThat(share, sameInstance(second.share))
		verify(first.connection).close(true)
	}

	@Test
	fun testRecentlyUsedConnectionIsNotProbed() {
		val server = server()
		withShare(Retry.NEVER) { it }
		now += TimeUnit.SECONDS.toNanos(29)

		withShare(Retry.NEVER) { it }

		verify(server.connection, never()).send<SMB2Packet>(any())
	}

	@Test
	fun testUsedConnectionIsNotProbed() {
		val server = server()
		withShare(Retry.NEVER) { it }
		now += TimeUnit.SECONDS.toNanos(20)
		withShare(Retry.NEVER) { it }
		now += TimeUnit.SECONDS.toNanos(20)

		withShare(Retry.NEVER) { it }

		verify(server.connection, never()).send<SMB2Packet>(any())
	}

	@Test
	fun testBusyConnectionIsNotProbed() {
		val server = server()

		withShare(Retry.NEVER) {
			// e.g. a listing while a long upload is running
			now += TimeUnit.SECONDS.toNanos(31)
			withShare(Retry.NEVER) { it }
		}

		verify(server.connection, never()).send<SMB2Packet>(any())
	}

	@Test
	fun testReadOnlyRequestIsRetriedOnFreshConnectionAfterTransportError() {
		val first = server()
		val second = server()

		val share = withShare(Retry.IF_FAILED) {
			if (it === first.share) throw SMBRuntimeException(TransportException("connection reset"))
			it
		}

		assertThat(share, sameInstance(second.share))
		verify(first.connection).close(true)
		verify(first.connection).handleError(any())
	}

	@Test
	fun testRetryIsAttemptedOnlyOnce() {
		server()
		server()
		var attempts = 0

		assertThrows(SMBRuntimeException::class.java) {
			withShare(Retry.IF_FAILED) {
				attempts++
				throw SMBRuntimeException(TransportException("connection reset"))
			}
		}
		assertThat(attempts, `is`(2))
	}

	@Test
	fun testTransferIsNotRetried() {
		val first = server()
		var attempts = 0

		assertThrows(SMBRuntimeException::class.java) {
			withShare(Retry.NEVER) {
				attempts++
				throw SMBRuntimeException(TransportException("connection reset"))
			}
		}
		assertThat(attempts, `is`(1))
		verify(first.connection).close(true)
	}

	@Test
	fun testModificationIsNotRetriedAfterTransportError() {
		server()
		var attempts = 0

		assertThrows(SMBRuntimeException::class.java) {
			withShare(Retry.IF_REJECTED) {
				attempts++
				throw SMBRuntimeException(TransportException("connection reset"))
			}
		}
		assertThat(attempts, `is`(1))
	}

	@ParameterizedTest
	@EnumSource(value = NtStatus::class, names = ["STATUS_USER_SESSION_DELETED", "STATUS_NETWORK_NAME_DELETED"])
	fun testModificationIsRetriedIfServerRejectedIt(status: NtStatus) {
		val first = server()
		val second = server()

		val share = withShare(Retry.IF_REJECTED) {
			if (it === first.share) throw SMBApiException(status.value, SMB2MessageCommandCode.SMB2_SET_INFO, null)
			it
		}

		assertThat(share, sameInstance(second.share))
	}

	@Test
	fun testModificationIsNotRetriedIfOnlyClosingTheHandleWasRejected() {
		server()
		var attempts = 0

		assertThrows(SMBApiException::class.java) {
			withShare(Retry.IF_REJECTED) {
				attempts++
				throw SMBApiException(NtStatus.STATUS_USER_SESSION_DELETED.value, SMB2MessageCommandCode.SMB2_CLOSE, null)
			}
		}
		assertThat(attempts, `is`(1))
	}

	@Test
	fun testTimeoutClosesConnectionWithoutRetry() {
		val first = server()
		var attempts = 0

		assertThrows(SMBRuntimeException::class.java) {
			withShare(Retry.IF_FAILED) {
				attempts++
				throw SMBRuntimeException(TimeoutException())
			}
		}
		assertThat(attempts, `is`(1))
		verify(first.connection).close(true)
	}

	@Test
	fun testSlowConnectBlocksOnlyItsOwnConfiguration() {
		val slow = server()
		val backup = server("backup")
		val connecting = CountDownLatch(1)
		val connected = CountDownLatch(1)
		whenever(slow.client.connect("nas", 445)).thenAnswer {
			connecting.countDown()
			connected.await(5, TimeUnit.SECONDS)
			slow.connection
		}
		val slowOperation = CompletableFuture.supplyAsync { withShare(Retry.NEVER) { it } }
		assertThat(connecting.await(5, TimeUnit.SECONDS), `is`(true))

		val share = CompletableFuture.supplyAsync {
			inTest.withShare(SmbConnectionPool.Config(SmbLocation.parse("smb://nas/backup")!!, "alice", "encryptedPassword") { "secret" }, Retry.NEVER) { it }
		}.get(2, TimeUnit.SECONDS)

		assertThat(share, sameInstance(backup.share))
		connected.countDown()
		assertThat(slowOperation.get(5, TimeUnit.SECONDS), sameInstance(slow.share))
	}

	@Test
	fun testFailureOnReplacedConnectionKeepsTheNewOne() {
		server()
		val second = server()

		assertThrows(SMBRuntimeException::class.java) {
			withShare(Retry.NEVER) {
				// meanwhile, another operation replaced the connection
				inTest.logout(config)
				withShare(Retry.NEVER) { it }
				throw SMBRuntimeException(TransportException("connection reset"))
			}
		}
		val share = withShare(Retry.NEVER) { it }

		assertThat(share, sameInstance(second.share))
		verify(second.connection, never()).close(true)
	}

	@Test
	fun testWaitingRequestsFailEvenIfForcedCloseThrows() {
		val first = server()
		doAnswer { throw TransportException("broken pipe") }.whenever(first.connection).close(true)

		assertThrows(SMBRuntimeException::class.java) {
			withShare(Retry.NEVER) { throw SMBRuntimeException(TransportException("connection reset")) }
		}
		verify(first.connection).handleError(any())
		verify(first.client).close()
	}

	@Test
	fun testApplicationErrorKeepsConnection() {
		val server = server()

		assertThrows(SMBApiException::class.java) {
			withShare(Retry.IF_FAILED) { throw SMBApiException(NtStatus.STATUS_OBJECT_NAME_NOT_FOUND.value, SMB2MessageCommandCode.SMB2_CREATE, null) }
		}
		val share = withShare(Retry.IF_FAILED) { it }

		assertThat(share, sameInstance(server.share))
		verify(server.connection, never()).close(true)
	}

	@Test
	fun testGuestSessionIsAccepted() {
		val server = server()
		whenever(server.session.isGuest).thenReturn(true)

		val share = withShare(Retry.NEVER) { it }

		assertThat(share, sameInstance(server.share))
	}

	@Test
	fun testFailedAuthenticationClosesClient() {
		val server = server()
		whenever(server.connection.authenticate(any())).thenThrow(SMBApiException(NtStatus.STATUS_LOGON_FAILURE.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null))

		assertThrows(SMBApiException::class.java) { withShare(Retry.NEVER) { it } }
		verify(server.client).close()
	}

	@Test
	fun testLogoutClosesIdleConnection() {
		val server = server()
		withShare(Retry.NEVER) { it }

		inTest.logout(config)

		verify(server.connection).close(true)
	}

	@Test
	fun testLogoutWaitsForRunningOperation() {
		val server = server()

		withShare(Retry.NEVER) {
			inTest.logout(config)
			verify(server.connection, never()).close(true)
		}

		verify(server.connection).close(true)
	}

	@Test
	fun testMissingLocalNetworkPermissionPreventsConnecting() {
		doAnswer { throw LocalNetworkAccessDeniedException("nas") }.whenever(localNetworkAccess).check("nas")
		val server = server()

		assertThrows(LocalNetworkAccessDeniedException::class.java) { withShare(Retry.NEVER) { } }
		verify(server.client, never()).connect(any(), any())
	}

	@Test
	fun testAuthenticationContextWithDomain() {
		val authenticationContext = SmbConnectionPool.authenticationContext("CORP\\alice", "secret")

		assertThat(authenticationContext.domain, `is`("CORP"))
		assertThat(authenticationContext.username, `is`("alice"))
		assertThat(String(authenticationContext.password), `is`("secret"))
	}

	@ParameterizedTest
	@ValueSource(strings = ["alice", "alice@corp.example"])
	fun testAuthenticationContextWithoutDomain(username: String) {
		val authenticationContext = SmbConnectionPool.authenticationContext(username, "secret")

		assertThat(authenticationContext.domain, `is`(nullValue()))
		assertThat(authenticationContext.username, `is`(username))
	}

	private fun <T> withShare(retry: Retry, action: (DiskShare) -> T): T {
		return inTest.withShare(config, retry, action)
	}

	private fun server(shareName: String = "share"): Server {
		return Server(shareName).also { servers.addLast(it) }
	}

	private class Server(shareName: String) {

		val client = mock<SMBClient>()
		val connection = mock<Connection>()
		val session = mock<Session>()
		val share = mock<DiskShare>()

		init {
			val treeConnect = mock<TreeConnect>()
			val connectionContext = mock<ConnectionContext>()
			whenever(client.connect("nas", 445)).thenReturn(connection)
			whenever(connection.authenticate(any())).thenReturn(session)
			whenever(connection.isConnected).thenReturn(true)
			whenever(connection.connectionContext).thenReturn(connectionContext)
			whenever(connectionContext.negotiatedProtocol).thenReturn(NegotiatedProtocol(SMB2Dialect.SMB_3_1_1, 0, 0, 0, true))
			whenever(session.connectShare(shareName)).thenReturn(share)
			whenever(session.connection).thenReturn(connection)
			whenever(share.treeConnect).thenReturn(treeConnect)
			whenever(share.isConnected).thenReturn(true)
			whenever(treeConnect.session).thenReturn(session)
		}
	}
}
