package org.cryptomator.domain;

import org.jetbrains.annotations.NotNull;

public class SharepointCloud implements MicrosoftGraphCloud {

	private final Long id;
	private final String accessToken;
	private final String username;
	private final String siteUrl;
	private final String driveId;
	private final String driveName;

	private SharepointCloud(Builder builder) {
		this.id = builder.id;
		this.accessToken = builder.accessToken;
		this.username = builder.username;
		this.siteUrl = builder.siteUrl;
		this.driveId = builder.driveId;
		this.driveName = builder.driveName;
	}

	public static Builder aSharepointCloud() {
		return new Builder();
	}

	public static Builder aCopyOf(SharepointCloud sharepointCloud) {
		return new Builder() //
				.withId(sharepointCloud.id()) //
				.withAccessToken(sharepointCloud.accessToken()) //
				.withUsername(sharepointCloud.username()) //
				.withSiteUrl(sharepointCloud.siteUrl()) //
				.withDriveId(sharepointCloud.driveId()) //
				.withDriveName(sharepointCloud.driveName());
	}

	@Override
	public Long id() {
		return id;
	}

	@Override
	public String accessToken() {
		return accessToken;
	}

	@Override
	public String username() {
		return username;
	}

	public String siteUrl() {
		return siteUrl;
	}

	@Override
	public String driveId() {
		return driveId;
	}

	public String driveName() {
		return driveName;
	}

	@Override
	public CloudType type() {
		return CloudType.SHAREPOINT;
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
		return false;
	}

	@Override
	public boolean configurationMatches(Cloud cloud) {
		return cloud instanceof SharepointCloud && configurationMatches((SharepointCloud) cloud);
	}

	private boolean configurationMatches(SharepointCloud cloud) {
		return username.equals(cloud.username) && driveId.equals(cloud.driveId);
	}

	@NotNull
	@Override
	public String toString() {
		return "SHAREPOINT";
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((SharepointCloud) obj);
	}

	@Override
	public int hashCode() {
		return id == null ? 0 : id.hashCode();
	}

	private boolean internalEquals(SharepointCloud obj) {
		return id != null && id.equals(obj.id);
	}

	public static class Builder {

		private Long id;
		private String accessToken;
		private String username;
		private String siteUrl;
		private String driveId;
		private String driveName;

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

		public Builder withSiteUrl(String siteUrl) {
			this.siteUrl = siteUrl;
			return this;
		}

		public Builder withDriveId(String driveId) {
			this.driveId = driveId;
			return this;
		}

		public Builder withDriveName(String driveName) {
			this.driveName = driveName;
			return this;
		}

		public SharepointCloud build() {
			return new SharepointCloud(this);
		}

	}

}
