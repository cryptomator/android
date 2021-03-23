package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class PCloud implements Cloud {

	private final Long id;
	private final String accessToken;
	private final String url;
	private final String username;

	private PCloud(Builder builder) {
		this.id = builder.id;
		this.accessToken = builder.accessToken;
		this.url = builder.url;
		this.username = builder.username;
	}

	public static Builder aPCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(PCloud pCloud) {
		return new Builder() //
				.withId(pCloud.id()) //
				.withAccessToken(pCloud.accessToken()) //
				.withUrl(pCloud.url()) //
				.withUsername(pCloud.username());
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
		return cloud instanceof PCloud && configurationMatches((PCloud) cloud);
	}

	private boolean configurationMatches(PCloud cloud) {
		return url.equals(cloud.url) && username.equals(cloud.username);
	}


	@Override
	public boolean predefined() {
		return false;
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
		return internalEquals((PCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(PCloud obj) {
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

		public PCloud build() {
			return new PCloud(this);
		}

	}

}
