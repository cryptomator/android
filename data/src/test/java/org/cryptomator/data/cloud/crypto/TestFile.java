package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.util.Optional;

import java.util.Date;
import java.util.Objects;

class TestFile implements CloudFile {

	private final TestFolder parent;
	private final String name;
	private final String path;
	private final Optional<Long> size;
	private final Optional<Date> modified;

	public TestFile(TestFolder parent, String name, String path, Optional<Long> size, Optional<Date> modified) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.size = size;
		this.modified = modified;
	}

	@Override
	public Cloud getCloud() {
		return parent.getCloud();
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
	public Optional<Long> getSize() {
		return size;
	}

	@Override
	public Optional<Date> getModified() {
		return modified;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		TestFile testFile = (TestFile) o;

		if (!Objects.equals(parent, testFile.parent)) {
			return false;
		}
		if (!Objects.equals(name, testFile.name)) {
			return false;
		}
		if (!Objects.equals(path, testFile.path)) {
			return false;
		}
		if (!Objects.equals(size, testFile.size)) {
			return false;
		}
		return Objects.equals(modified, testFile.modified);
	}

	@Override
	public int hashCode() {
		int result = parent != null ? parent.hashCode() : 0;
		result = 31 * result + (name != null ? name.hashCode() : 0);
		result = 31 * result + (path != null ? path.hashCode() : 0);
		result = 31 * result + (size != null ? size.hashCode() : 0);
		result = 31 * result + (modified != null ? modified.hashCode() : 0);
		return result;
	}
}
