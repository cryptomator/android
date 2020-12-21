package org.cryptomator.data.cloud.webdav.network;

import android.content.Context;

import org.cryptomator.data.cloud.webdav.WebDavFolder;
import org.cryptomator.data.cloud.webdav.WebDavNode;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.usecases.cloud.DataSource;

import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;

public class ConnectionHandlerHandlerImpl {

	private final WebDavClient webDavClient;

	@Inject
	ConnectionHandlerHandlerImpl(WebDavCompatibleHttpClient httpClient, Context context) {
		this.webDavClient = new WebDavClient(context, httpClient);
	}

	public List<CloudNode> dirList(String url, WebDavFolder listedFolder) throws BackendException {
		return webDavClient.dirList(url, listedFolder);
	}

	public void move(String from, String to) throws BackendException {
		webDavClient.move(from, to);
	}

	public WebDavNode get(String url, CloudFolder parent) throws BackendException {
		return webDavClient.get(url, parent);
	}

	public void writeFile(String url, DataSource data) throws BackendException {
		webDavClient.writeFile(url, data);
	}

	public void delete(String url) throws BackendException {
		webDavClient.delete(url);
	}

	public WebDavFolder createFolder(String path, WebDavFolder folder) throws BackendException {
		return webDavClient.createFolder(path, folder);
	}

	public InputStream readFile(String url) throws BackendException {
		return webDavClient.readFile(url);
	}

	public void checkAuthenticationAndServerCompatibility(String url) throws BackendException {
		webDavClient.checkAuthenticationAndServerCompatibility(url);
	}
}
