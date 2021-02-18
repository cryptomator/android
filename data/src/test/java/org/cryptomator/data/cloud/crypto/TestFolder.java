package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

import java.util.Objects;

class TestFolder implements CloudFolder {

	private final TestFolder parent;
	private final String name;
	private final String path;

	public TestFolder(TestFolder parent, String name, String path) {
		this.parent = parent;
		this.name = name;
		this.path = path;
	}

	@Override
	public Cloud getCloud() {
		return parent.getCloud();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		TestFolder that = (TestFolder) o;

		if (!Objects.equals(parent, that.parent)) {
			return false;
		}
		if (!Objects.equals(name, that.name)) {
			return false;
		}
		return Objects.equals(path, that.path);
	}

	@Override
	public int hashCode() {
		int result = parent != null ? parent.hashCode() : 0;
		result = 31 * result + (name != null ? name.hashCode() : 0);
		result = 31 * result + (path != null ? path.hashCode() : 0);
		return result;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public TestFolder getParent() {
		return parent;
	}

	@Override
	public TestFolder withCloud(Cloud cloud) {
		return new TestFolder(parent.withCloud(cloud), name, path);
	}
}
