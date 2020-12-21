package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class OnedriveCloud implements Cloud {

	private final Long id;
	private final String accessToken;
	private final String username;

	private OnedriveCloud(Builder builder) {
		this.id = builder.id;
		this.accessToken = builder.accessToken;
		this.username = builder.username;
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

	@Override
	public CloudType type() {
		return CloudType.ONEDRIVE;
	}

	@Override
	public boolean predefined() {
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
	public boolean configurationMatches(Cloud cloud) {
		return true;
	}

	@NotNull
	@Override
	public String toString() {
		return "ONEDRIVE";
	}

	public static Builder aOnedriveCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(OnedriveCloud oneDriveCloud) {
		return new Builder() //
				.withId(oneDriveCloud.id()) //
				.withAccessToken(oneDriveCloud.accessToken()) //
				.withUsername(oneDriveCloud.username());
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

		public OnedriveCloud build() {
			return new OnedriveCloud(this);
		}

	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass())
			return false;
		if (obj == this)
			return true;
		return internalEquals((OnedriveCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(OnedriveCloud obj) {
		return id != null && id.equals(obj.id);
	}

}
