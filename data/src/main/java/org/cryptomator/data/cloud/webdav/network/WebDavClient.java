package org.cryptomator.data.cloud.webdav.network;

import static java.util.Collections.sort;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.cryptomator.data.cloud.webdav.WebDavFolder;
import org.cryptomator.data.cloud.webdav.WebDavNode;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.AlreadyExistException;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.ForbiddenException;
import org.cryptomator.domain.exception.NotFoundException;
import org.cryptomator.domain.exception.ParentFolderDoesNotExistException;
import org.cryptomator.domain.exception.ServerNotWebdavCompatibleException;
import org.cryptomator.domain.exception.TypeMismatchException;
import org.cryptomator.domain.exception.UnauthorizedException;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

class WebDavClient {

	private final Context context;
	private final WebDavCompatibleHttpClient httpClient;

	WebDavClient(Context context, WebDavCompatibleHttpClient httpClient) {
		this.context = context;
		this.httpClient = httpClient;
	}

	List<CloudNode> dirList(String url, WebDavFolder listedFolder) throws BackendException {
		try (Response response = executePropfindRequest(url, PropfindDepth.ONE)) {
			checkPropfindExecutionSucceeded(response.code());

			List<PropfindEntryData> nodes = getEntriesFromResponse(listedFolder, response);

			return processDirList(nodes, listedFolder);
		} catch (IOException | XmlPullParserException e) {
			throw new FatalBackendException(e);
		}
	}

	public WebDavNode get(String url, CloudFolder parent) throws BackendException {
		try (Response response = executePropfindRequest(url, PropfindDepth.ZERO)) {
			checkPropfindExecutionSucceeded(response.code());

			List<PropfindEntryData> nodes = getEntriesFromResponse((WebDavFolder) parent, response);

			return processGet(nodes, (WebDavFolder) parent);
		} catch (IOException | XmlPullParserException e) {
			throw new FatalBackendException(e);
		}
	}

	private Response executePropfindRequest(String url, PropfindDepth depth) throws IOException {
		String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" //
				+ "<d:propfind xmlns:d=\"DAV:\">\n" //
				+ "<d:prop>\n" //
				+ "<d:resourcetype />\n" //
				+ "<d:getcontentlength />\n" //
				+ "<d:getlastmodified />\n" //
				+ "</d:prop>\n" //
				+ "</d:propfind>";

		Request.Builder builder = new Request.Builder() //
				.method("PROPFIND", RequestBody.create(MediaType.parse(body), body)) //
				.url(url) //
				.header("DEPTH", depth.value) //
				.header("Content-Type", "text/xml");

		return httpClient.execute(builder);
	}

	private void checkPropfindExecutionSucceeded(int responseCode) throws BackendException {
		switch (responseCode) {
		case HttpURLConnection.HTTP_UNAUTHORIZED:
			throw new UnauthorizedException();
		case HttpURLConnection.HTTP_FORBIDDEN:
			throw new ForbiddenException();
		case HttpURLConnection.HTTP_NOT_FOUND:
			throw new NotFoundException();
		}

		if (responseCode < 199 || responseCode > 300) {
			throw new FatalBackendException("Response code isn't between 200 and 300: " + responseCode);
		}
	}

	private List<PropfindEntryData> getEntriesFromResponse(WebDavFolder listedFolder, Response response) throws IOException, XmlPullParserException {
		try (final ResponseBody responseBody = response.body()) {
			return new PropfindResponseParser(listedFolder).parse(responseBody.byteStream());
		}
	}

	public void move(String from, String to) throws BackendException {
		Request.Builder builder = new Request.Builder() //
				.method("MOVE", null) //
				.url(from) //
				.header("Content-Type", "text/xml") //
				.header("Destination", to) //
				.header("Depth", "infinity") //
				.header("Overwrite", "F");

		try (Response response = httpClient.execute(builder)) {
			if (!response.isSuccessful()) {
				switch (response.code()) {
				case HttpURLConnection.HTTP_UNAUTHORIZED:
					throw new UnauthorizedException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new ForbiddenException();
				case HttpURLConnection.HTTP_NOT_FOUND:
					throw new NotFoundException();
				case HttpURLConnection.HTTP_CONFLICT:
					throw new ParentFolderDoesNotExistException();
				case HttpURLConnection.HTTP_PRECON_FAILED:
					throw new CloudNodeAlreadyExistsException(to);
				default:
					throw new FatalBackendException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	InputStream readFile(String url) throws BackendException {
		Request.Builder builder = new Request.Builder() //
				.get() //
				.url(url);

		Response response = null;
		boolean success = false;

		try {
			response = httpClient.execute(builder);

			if (response.isSuccessful()) {
				success = true;
				return response.body().byteStream();
			} else {
				switch (response.code()) {
				case HttpURLConnection.HTTP_UNAUTHORIZED:
					throw new UnauthorizedException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new ForbiddenException();
				case HttpURLConnection.HTTP_NOT_FOUND:
					throw new NotFoundException();
				case 416: // UNSATISFIABLE_RANGE
					return new ByteArrayInputStream(new byte[0]);
				default:
					throw new FatalBackendException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		} finally {
			if (response != null && !success) {
				response.close();
			}
		}
	}

	void writeFile(String url, DataSource data) throws BackendException {
		Request.Builder builder = new Request.Builder() //
				.put(DataSourceBasedRequestBody.from(context, data)) //
				.url(url);

		try (Response response = httpClient.execute(builder)) {
			if (!response.isSuccessful()) {
				switch (response.code()) {
				case HttpURLConnection.HTTP_UNAUTHORIZED:
					throw new UnauthorizedException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new ForbiddenException();
				case HttpURLConnection.HTTP_BAD_METHOD:
					throw new TypeMismatchException();
				case HttpURLConnection.HTTP_CONFLICT: // fall through
				case HttpURLConnection.HTTP_NOT_FOUND: // necessary due to a bug in Nextcloud, see https://github.com/nextcloud/server/issues/23519
					throw new ParentFolderDoesNotExistException();
				default:
					throw new FatalBackendException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	WebDavFolder createFolder(String path, WebDavFolder folder) throws BackendException {
		Request.Builder builder = new Request.Builder() //
				.method("MKCOL", null) //
				.url(path);

		try (Response response = httpClient.execute(builder)) {
			if (response.isSuccessful()) {
				return folder;
			} else {
				switch (response.code()) {
				case HttpURLConnection.HTTP_UNAUTHORIZED:
					throw new UnauthorizedException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new ForbiddenException();
				case HttpURLConnection.HTTP_BAD_METHOD:
					throw new AlreadyExistException();
				case HttpURLConnection.HTTP_CONFLICT:
					throw new ParentFolderDoesNotExistException();
				default:
					throw new FatalBackendException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	public void delete(String url) throws BackendException {
		Request.Builder builder = new Request.Builder() //
				.delete() //
				.url(url);

		try (Response response = httpClient.execute(builder)) {
			if (!response.isSuccessful()) {
				switch (response.code()) {
				case HttpURLConnection.HTTP_UNAUTHORIZED:
					throw new UnauthorizedException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new ForbiddenException();
				case HttpURLConnection.HTTP_NOT_FOUND:
					throw new NotFoundException(String.format("Node %s doesn't exists", url));
				default:
					throw new FatalBackendException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	void checkAuthenticationAndServerCompatibility(String url) throws BackendException {
		final Request.Builder optionsRequest = new Request.Builder() //
				.method("OPTIONS", null) //
				.url(url);

		try (Response response = httpClient.execute(optionsRequest)) {
			if (response.isSuccessful()) {
				final boolean containsDavHeader = response.headers().names().contains("DAV");
				if (!containsDavHeader) {
					throw new ServerNotWebdavCompatibleException();
				}
			} else {
				switch (response.code()) {
				case HttpURLConnection.HTTP_UNAUTHORIZED:
					throw new UnauthorizedException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new ForbiddenException();
				default:
					throw new FatalBackendException("Response code isn't between 200 and 300: " + response.code());
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}

		try (Response response = executePropfindRequest(url, PropfindDepth.ZERO)) {
			checkPropfindExecutionSucceeded(response.code());
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private List<CloudNode> processDirList(List<PropfindEntryData> entryData, WebDavFolder requestedFolder) {
		List<CloudNode> result = new ArrayList<>();
		sort(entryData, ASCENDING_BY_DEPTH);
		// after sorting the first entry is the parent
		// because it's depth is 1 smaller than the depth
		// ot the other entries, thus we skip the first entry
		for (PropfindEntryData childEntry : entryData.subList(1, entryData.size())) {
			result.add(childEntry.toCloudNode(requestedFolder));
		}
		return result;
	}

	private WebDavNode processGet(List<PropfindEntryData> entryData, WebDavFolder requestedFolder) {
		sort(entryData, ASCENDING_BY_DEPTH);
		return entryData.size() >= 1 ? entryData.get(0).toCloudNode(requestedFolder) : null;
	}

	private final Comparator<PropfindEntryData> ASCENDING_BY_DEPTH = (o1, o2) -> o1.getDepth() - o2.getDepth();

	private enum PropfindDepth {
		ZERO("0"), //
		ONE("1"), //
		INFINITY("infinity");

		private final String value;

		PropfindDepth(final String value) {
			this.value = value;
		}
	}
}
