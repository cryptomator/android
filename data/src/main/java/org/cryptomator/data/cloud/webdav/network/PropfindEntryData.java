package org.cryptomator.data.cloud.webdav.network;

import org.cryptomator.data.cloud.webdav.WebDavFile;
import org.cryptomator.data.cloud.webdav.WebDavFolder;
import org.cryptomator.data.cloud.webdav.WebDavNode;
import org.cryptomator.util.Optional;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PropfindEntryData {
	private static final Pattern URI_PATTERN = Pattern.compile("^[a-z]+://[^/]+/(.*)$");

	private String path;
	private String[] pathSegments;

	private boolean file = true;
	private Optional<Date> lastModified = Optional.empty();
	private Optional<Long> size = Optional.empty();

	public void setPath(String pathOrUri) {
		this.path = extractPath(pathOrUri);
		this.pathSegments = path.split("/");
	}

	private String extractPath(String pathOrUri) {
		Matcher matcher = URI_PATTERN.matcher(pathOrUri);
		if (matcher.matches()) {
			return urlDecode(matcher.group(1));
		} else if (!pathOrUri.startsWith("/")) {
			return urlDecode("/" + pathOrUri);
		} else {
			return urlDecode(pathOrUri);
		}
	}

	void setLastModified(Optional<Date> lastModified) {
		this.lastModified = lastModified;
	}

	public void setSize(Optional<Long> size) {
		this.size = size;
	}

	public void setFile(boolean file) {
		this.file = file;
	}

	public String getPath() {
		return path;
	}

	public Optional<Long> getSize() {
		return size;
	}

	private boolean isFile() {
		return file;
	}

	public WebDavNode toCloudNode(WebDavFolder parent) {
		if (isFile()) {
			return new WebDavFile(parent, getName(), size, lastModified);
		} else {
			return new WebDavFolder(parent, getName());
		}
	}

	private String urlDecode(String value) {
		try {
			return URLDecoder.decode(value, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("UTF-8 must be supported by every JVM", e);
		}
	}

	int getDepth() {
		return pathSegments.length;
	}

	private String getName() {
		return pathSegments[pathSegments.length - 1];
	}

}
