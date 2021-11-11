package org.cryptomator.domain;

import android.text.TextUtils;

import org.jetbrains.annotations.NotNull;

public class LocalStorageCloud implements Cloud {

	private final Long id;
	private final String rootUri;

	private LocalStorageCloud(Builder builder) {
		this.id = builder.id;
		this.rootUri = builder.rootUri;
	}

	public static Builder aLocalStorage() {
		return new Builder();
	}

	public static Builder aCopyOf(LocalStorageCloud localStorageCloud) {
		return new Builder() //
				.withId(localStorageCloud.id());
	}

	@Override
	public Long id() {
		return id;
	}

	public String rootUri() {
		return rootUri;
	}

	@Override
	public CloudType type() {
		return CloudType.LOCAL;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof LocalStorageCloud && configurationMatches((LocalStorageCloud) cloud);
	}

	private boolean configurationMatches(LocalStorageCloud cloud) {
		return TextUtils.equals(rootUri, cloud.rootUri);

	}

	@Override
	public boolean persistent() {
		return true;
	}

	@Override
	public boolean requiresNetwork() {
		return false;
	}

	@NotNull
	@Override
	public String toString() {
		return "LOCAL";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((LocalStorageCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(LocalStorageCloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String rootUri;

		private Builder() {
		}

		public Builder withId(Long id) {
			this.id = id;
			return this;
		}

		public Builder withRootUri(String rootUri) {
			this.rootUri = rootUri;
			return this;
		}

		public LocalStorageCloud build() {
			return new LocalStorageCloud(this);
		}

		@NotNull
		@Override
		public String toString() {
			return "LOCAL";
		}
	}
}
