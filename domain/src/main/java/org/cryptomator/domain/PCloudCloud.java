package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class PCloudCloud implements Cloud {

	private final Long id;
	private final String accessToken;
	private final String url;
	private final String username;

	private PCloudCloud(Builder builder) {
		this.id = builder.id;
		this.accessToken = builder.accessToken;
		this.url = builder.url;
		this.username = builder.username;
	}

	public static Builder aPCloudCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(PCloudCloud pCloudCloud) {
		return new Builder() //
				.withId(pCloudCloud.id()) //
				.withAccessToken(pCloudCloud.accessToken()) //
				.withUrl(pCloudCloud.url()) //
				.withUsername(pCloudCloud.username());
	}

	@Override
	public Long id() {
		return id;
	}

	public String accessToken() {
		return accessToken;
	}

	public String url() {
		return url;
	}

	public String username() {
		return username;
	}

	@Override
	public CloudType type() {
		return CloudType.PCLOUD;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return true;
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

	@NotNull
	@Override
	public String toString() {
		return "PCLOUD";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((PCloudCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(PCloudCloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String accessToken;
		private String url;
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

		public Builder withUrl(String url) {
			this.url = url;
			return this;
		}

		public Builder withUsername(String username) {
			this.username = username;
			return this;
		}

		public PCloudCloud build() {
			return new PCloudCloud(this);
		}

	}

}
