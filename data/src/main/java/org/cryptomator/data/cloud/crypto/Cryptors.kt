package org.cryptomator.data.cloud.crypto

import com.google.common.base.Optional
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.MissingCryptorException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier

abstract class Cryptors internal constructor() {

	abstract fun isEmpty(): Boolean

	abstract fun size(): Int

	abstract operator fun get(vault: Vault): Supplier<Cryptor>

	abstract fun remove(vault: Vault): Optional<Cryptor>

	abstract fun putIfAbsent(vault: Vault, cryptor: Cryptor): Boolean

	class Delegating : Cryptors() {

		private val fallback = Default()

		@Volatile
		private var delegate: Default? = null

		@Synchronized
		fun setDelegate(delegate: Default) {
			delegate.putAll(fallback.cryptors)
			this.delegate = delegate
		}

		@Synchronized
		fun removeDelegate() {
			delegate?.let {
				fallback.putAll(it.cryptors)
			}.also { delegate = null }
		}

		@Synchronized
		override fun isEmpty(): Boolean {
			return delegate().isEmpty()
		}

		@Synchronized
		override fun size(): Int {
			return delegate().size()
		}

		@Synchronized
		override fun get(vault: Vault): Supplier<Cryptor> {
			return delegate()[vault]
		}

		@Synchronized
		override fun remove(vault: Vault): Optional<Cryptor> {
			return delegate().remove(vault)
		}

		@Synchronized
		override fun putIfAbsent(vault: Vault, cryptor: Cryptor): Boolean {
			return delegate().putIfAbsent(vault, cryptor)
		}

		@Synchronized
		private fun delegate(): Cryptors {
			return delegate ?: fallback
		}
	}

	class Default : Cryptors() {

		val cryptors: ConcurrentMap<Vault, Cryptor> = ConcurrentHashMap()

		private var onChangeListener = Runnable {}

		override fun isEmpty(): Boolean {
			return cryptors.isEmpty()
		}

		override fun size(): Int {
			return cryptors.size
		}

		override fun get(vault: Vault): Supplier<Cryptor> {
			return Supplier {
				cryptors[vault] ?: throw MissingCryptorException()
			}
		}

		override fun remove(vault: Vault): Optional<Cryptor> {
			val result = Optional.fromNullable(cryptors.remove(vault))
			if (result.isPresent) {
				onChangeListener.run()
			}
			return result
		}

		override fun putIfAbsent(vault: Vault, cryptor: Cryptor): Boolean {
			return if (cryptors.putIfAbsent(vault, cryptor) == null) {
				onChangeListener.run()
				true
			} else {
				false
			}
		}

		fun setOnChangeListener(onChangeListener: Runnable) {
			this.onChangeListener = onChangeListener
		}

		fun putAll(cryptors: Map<Vault, Cryptor>) {
			this.cryptors.putAll(cryptors)
			onChangeListener.run()
		}

		fun destroyAll() {
			while (!isEmpty()) {
				val cryptorIterator = cryptors.values.iterator()
				while (cryptorIterator.hasNext()) {
					cryptorIterator.next().destroy()
					cryptorIterator.remove()
				}
			}
			onChangeListener.run()
		}
	}
}
