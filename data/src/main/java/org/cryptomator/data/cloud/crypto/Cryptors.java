package org.cryptomator.data.cloud.crypto;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.MissingCryptorException;
import org.cryptomator.util.Optional;
import org.cryptomator.util.Supplier;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class Cryptors {

	Cryptors() {
	}

	public abstract boolean isEmpty();

	public abstract int size();

	public abstract Supplier<Cryptor> get(Vault vault);

	public abstract Optional<Cryptor> remove(Vault vault);

	public abstract boolean putIfAbsent(Vault vault, Cryptor cryptor);

	public static class Delegating extends Cryptors {

		private final Cryptors.Default fallback = new Cryptors.Default();

		private volatile Cryptors.Default delegate;

		public synchronized void setDelegate(Cryptors.Default delegate) {
			delegate.putAll(fallback.cryptors);
			this.delegate = delegate;
		}

		public synchronized void removeDelegate() {
			fallback.putAll(delegate.cryptors);
			this.delegate = null;
		}

		@Override
		public synchronized boolean isEmpty() {
			return delegate().isEmpty();
		}

		@Override
		public synchronized int size() {
			return delegate().size();
		}

		@Override
		public synchronized Supplier<Cryptor> get(Vault vault) {
			return delegate().get(vault);
		}

		@Override
		public synchronized Optional<Cryptor> remove(Vault vault) {
			return delegate().remove(vault);
		}

		@Override
		public synchronized boolean putIfAbsent(Vault vault, Cryptor cryptor) {
			return delegate().putIfAbsent(vault, cryptor);
		}

		private synchronized Cryptors delegate() {
			if (delegate == null) {
				return fallback;
			} else {
				return delegate;
			}
		}

	}

	public static class Default extends Cryptors {

		private final ConcurrentMap<Vault, Cryptor> cryptors = new ConcurrentHashMap<>();

		private Runnable onChangeListener = () -> {
		};

		public boolean isEmpty() {
			return cryptors.isEmpty();
		}

		public int size() {
			return cryptors.size();
		}

		public Supplier<Cryptor> get(final Vault vault) {
			return () -> {
				Cryptor cryptor = cryptors.get(vault);
				if (cryptor == null) {
					throw new MissingCryptorException();
				} else {
					return cryptor;
				}
			};
		}

		public Optional<Cryptor> remove(Vault vault) {
			Optional<Cryptor> result = Optional.ofNullable(cryptors.remove(vault));
			if (result.isPresent()) {
				onChangeListener.run();
			}
			return result;
		}

		public boolean putIfAbsent(Vault vault, Cryptor cryptor) {
			if (cryptors.putIfAbsent(vault, cryptor) == null) {
				onChangeListener.run();
				return true;
			} else {
				return false;
			}
		}

		public void setOnChangeListener(Runnable onChangeListener) {
			this.onChangeListener = onChangeListener;
		}

		public void putAll(Map<Vault, Cryptor> cryptors) {
			this.cryptors.putAll(cryptors);
			onChangeListener.run();
		}

		public void destroyAll() {
			while (!isEmpty()) {
				Iterator<Cryptor> cryptorIterator = cryptors.values().iterator();
				while (cryptorIterator.hasNext()) {
					cryptorIterator.next().destroy();
					cryptorIterator.remove();
				}
			}
			onChangeListener.run();
		}
	}

}
