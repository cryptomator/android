package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class GoogleDriveCloud implements Cloud {

	private final Long id;
	private final String username;

	private GoogleDriveCloud(Builder builder) {
		this.id = builder.id;
		this.username = builder.username;
	}

	public static Builder aGoogleDriveCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(GoogleDriveCloud googleDriveCloud) {
		return new Builder() //
				.withId(googleDriveCloud.id()) //
				.withUsername(googleDriveCloud.username());
	}

	@Override
	public Long id() {
		return id;
	}

	@NotNull
	@Override
	public CloudType getType() {
		return CloudType.GOOGLE_DRIVE;
	}

	public String username() {
		return username;
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
		return "GOOGLE_DRIVE";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((GoogleDriveCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(GoogleDriveCloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String username;

		private Builder() {
		}

		public Builder withId(Long id) {
			this.id = id;
			return this;
		}

		public Builder withUsername(String username) {
			this.username = username;
			return this;
		}

		public GoogleDriveCloud build() {
			return new GoogleDriveCloud(this);
		}

	}

}
