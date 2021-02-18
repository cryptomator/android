package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;

import java.util.Objects;

class RootTestFolder extends TestFolder {

	private final Cloud cloud;

	public RootTestFolder(Cloud cloud) {
		super(null, "", "");
		this.cloud = cloud;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}

		RootTestFolder that = (RootTestFolder) o;

		return Objects.equals(cloud, that.cloud);
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (cloud != null ? cloud.hashCode() : 0);
		return result;
	}

	@Override
	public Cloud getCloud() {
		return cloud;
	}

	@Override
	public TestFolder withCloud(Cloud cloud) {
		return new RootTestFolder(cloud);
	}
}
