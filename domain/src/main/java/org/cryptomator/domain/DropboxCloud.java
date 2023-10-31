package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class DropboxCloud implements Cloud {

	private final Long id;
	private final String accessToken;
	private final String username;

	private DropboxCloud(Builder builder) {
		this.id = builder.id;
		this.accessToken = builder.accessToken;
		this.username = builder.username;
	}

	public static Builder aDropboxCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(DropboxCloud dropboxCloud) {
		return new Builder() //
				.withId(dropboxCloud.id()) //
				.withAccessToken(dropboxCloud.accessToken()) //
				.withUsername(dropboxCloud.username());
	}

	@Override
	public Long id() {
		return id;
	}

	public String accessToken() {
		return accessToken;
	}

	public String username() {
		return username;
	}

	@NotNull
	@Override
	public CloudType type() {
		return CloudType.DROPBOX;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return true;
	}

	@Override
	public boolean persistent() {
		return true;
	}

	@Override
	public boolean requiresNetwork() {
		return true;
	}

	@Override
	public boolean isReadOnly() {
		return false; //TODO Implement read-only check
	}

	@NotNull
	@Override
	public String toString() {
		return "DROPBOX";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((DropboxCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(DropboxCloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String accessToken;
		private String username;

		private Builder() {
		}

		public Builder withId(Long id) {
			this.id = id;
			return this;
		}

		public Builder withAccessToken(String accessToken) {
			this.accessToken = accessToken;
			return this;
		}

		public Builder withUsername(String username) {
			this.username = username;
			return this;
		}

		public DropboxCloud build() {
			return new DropboxCloud(this);
		}

	}

}
