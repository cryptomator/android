package org.cryptomator.data.cloud.smb

import android.content.Context
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMB2Packet
import com.hierynomus.mssmb2.messages.SMB2Echo
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.cryptomator.data.util.LocalNetworkAccess
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.util.ExceptionUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Keeps one authenticated [DiskShare] per configuration so operations don't have to connect, authenticate and
 * connect the tree again. smbj multiplexes concurrent requests over the connection.
 */
@Singleton
class SmbConnectionPool internal constructor(
	private val newClient: () -> SMBClient,
	private val localNetworkAccess: LocalNetworkAccess,
	private val nanoTime: () -> Long = System::nanoTime
) {

	@Inject
	constructor(context: Context) : this({ SMBClient(CONFIG) }, LocalNetworkAccess(context))

	private val slots = HashMap<Key, Slot>()

	/**
	 * Runs [action] on the connected share. If the connection or session turns out to be broken, it is closed and,
	 * as far as [retry] allows, the action is run once more on a fresh connection.
	 */
	fun <T> withShare(config: Config, retry: Retry, action: (DiskShare) -> T): T {
		val slot = synchronized(slots) { slots.getOrPut(config.key) { Slot() } }
		val connection = acquire(slot, config)
		try {
			return action(connection.share)
		} catch (e: Exception) {
			if (isBroken(e)) {
				synchronized(slot) {
					if (slot.connection === connection) {
						slot.connection = null
					}
				}
				connection.close()
				if (retry.allows(e)) {
					return withShare(config, Retry.NEVER, action)
				}
			}
			throw e
		} finally {
			release(slot, connection)
		}
	}

	/**
	 * Logs off the configured user from the share.
	 */
	fun logout(config: Config) {
		val slot = synchronized(slots) { slots[config.key] } ?: return
		synchronized(slot) {
			slot.connection?.let { retire(slot, it) }
		}
	}

	private fun acquire(slot: Slot, config: Config): PooledConnection {
		// one lock per configuration, so a slow server doesn't block operations on other ones
		synchronized(slot) {
			val current = slot.connection
			val connection = if (current != null && current.authenticatedWith(config) && isUsable(current)) {
				current
			} else {
				current?.let { retire(slot, it) }
				connect(config).also { slot.connection = it }
			}
			connection.inFlight++
			return connection
		}
	}

	private fun release(slot: Slot, connection: PooledConnection) {
		synchronized(slot) {
			connection.inFlight--
			connection.lastUsed = nanoTime()
			if (connection.retired && connection.inFlight == 0) {
				connection.close()
			}
		}
	}

	/**
	 * Closes the connection as soon as no operation uses it anymore.
	 */
	private fun retire(slot: Slot, connection: PooledConnection) {
		if (slot.connection === connection) {
			slot.connection = null
		}
		connection.retired = true
		if (connection.inFlight == 0) {
			connection.close()
		}
	}

	/**
	 * Servers and NAT gateways silently drop idle connections, which would make the next request hang until it times
	 * out. So before reusing a connection that has been idle for a while, check that the server still answers.
	 */
	private fun isUsable(connection: PooledConnection): Boolean {
		if (!connection.connection.isConnected || !connection.share.isConnected) {
			return false
		}
		if (connection.inFlight > 0 || nanoTime() - connection.lastUsed < TimeUnit.SECONDS.toNanos(IDLE_SECONDS_BEFORE_ECHO)) {
			return true
		}
		return try {
			connection.connection //
				.send<SMB2Packet>(SMB2Echo(connection.connection.connectionContext.negotiatedProtocol.dialect)) //
				.get(ECHO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			true
		} catch (e: Exception) {
			Timber.tag("SmbConnectionPool").i(e, "Idle connection is stale, reconnecting")
			false
		}
	}

	private fun connect(config: Config): PooledConnection {
		val location = config.location
		localNetworkAccess.check(location.host)
		val password = config.password()
		val client = newClient()
		try {
			val connection = client.connect(location.host, location.port)
			val session = connection.authenticate(authenticationContext(config.username, password))
			val share = session.connectShare(location.share) as? DiskShare ?: throw FatalBackendException("${location.share} is not a disk share")
			return PooledConnection(client, share, nanoTime(), config.encryptedPassword)
		} catch (e: Exception) {
			client.close()
			throw e
		}
	}

	private fun isBroken(e: Exception): Boolean {
		return ExceptionUtil.contains(e, TransportException::class.java) //
				|| ExceptionUtil.contains(e, TimeoutException::class.java) //
				|| isRejected(e)
	}

	/**
	 * Where to connect to and as whom. The password is only decrypted when a new session is needed.
	 */
	class Config(val location: SmbLocation, val username: String, val encryptedPassword: String, val password: () -> String) {

		internal val key: Key
			get() = Key(location.host, location.port, location.share, username)
	}

	internal data class Key(val host: String, val port: Int, val share: String, val username: String)

	private class Slot {

		var connection: PooledConnection? = null
	}

	/**
	 * Owns its [SMBClient] and thereby its connection: smbj shares one connection per host between all sessions of a
	 * client, so closing it would otherwise affect other shares as well.
	 */
	private class PooledConnection(val client: SMBClient, val share: DiskShare, var lastUsed: Long, private val encryptedPassword: String) {

		val connection = share.treeConnect.session.connection
		var inFlight = 0
		var retired = false

		/**
		 * Changes when the user edits the password, which also re-encrypts an unchanged one: then a new session is opened.
		 */
		fun authenticatedWith(config: Config): Boolean {
			return config.encryptedPassword == encryptedPassword
		}

		fun close() {
			// a graceful close would wait for the server to answer each request until it times out
			closeQuietly { connection.close(true) }
			// a forced close doesn't fail requests that are still waiting for a response
			closeQuietly { connection.handleError(TransportException("Connection closed")) }
			closeQuietly { client.close() }
		}

		private fun closeQuietly(close: () -> Unit) {
			try {
				close()
			} catch (e: Exception) {
				Timber.tag("SmbConnectionPool").d(e, "Failed to close connection")
			}
		}
	}

	enum class Retry {

		/**
		 * For transfers, which can't be repeated once they started.
		 */
		NEVER,

		/**
		 * For modifications: only retry if the server didn't execute the request because the session or tree was gone.
		 */
		IF_REJECTED,

		/**
		 * For requests that don't modify anything: also retry after the connection broke, but not after a timeout.
		 */
		IF_FAILED;

		internal fun allows(e: Exception): Boolean {
			return when (this) {
				NEVER -> false
				// a rejected CLOSE comes after the modification was already executed
				IF_REJECTED -> isRejected(e) && failedCommand(e) != SMB2MessageCommandCode.SMB2_CLOSE
				IF_FAILED -> !ExceptionUtil.contains(e, TimeoutException::class.java)
			}
		}
	}

	companion object {

		private const val IDLE_SECONDS_BEFORE_ECHO = 30L
		private const val ECHO_TIMEOUT_SECONDS = 5L

		private val CONFIG = SmbConfig.builder() //
			// the leased directory cache could serve listings that don't contain our own writes yet
			.withDirectoryLeasingEnabled(false) //
			.build()

		private fun isRejected(e: Exception): Boolean {
			return hasStatus(e, NtStatus.STATUS_NETWORK_NAME_DELETED, NtStatus.STATUS_USER_SESSION_DELETED)
		}

		/**
		 * Accepts `DOMAIN\user` for domain accounts, everything else (including `user@domain`) is passed on as is.
		 */
		fun authenticationContext(username: String, password: String): AuthenticationContext {
			val domain = username.substringBefore('\\', "")
			val user = username.substringAfter('\\')
			return AuthenticationContext(user, password.toCharArray(), domain.ifEmpty { null })
		}
	}
}
