package org.cryptomator.data.cloud.webdav.network;

import org.cryptomator.data.cloud.webdav.WebDavFolder;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.util.Optional;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

class PropfindResponseParser {

	private static final String TAG_RESPONSE = "response";
	private static final String TAG_HREF = "href";
	private static final String TAG_COLLECTION = "collection";
	private static final String TAG_LAST_MODIFIED = "getlastmodified";
	private static final String TAG_CONTENT_LENGTH = "getcontentlength";
	private static final String TAG_PROPSTAT = "propstat";
	private static final String TAG_STATUS = "status";
	private static final String STATUS_OK = "200";

	private final WebDavFolder requestedFolder;
	private final XmlPullParser xmlPullParser;

	PropfindResponseParser(WebDavFolder requestedFolder) {
		this.requestedFolder = requestedFolder;
		try {
			this.xmlPullParser = XmlPullParserFactory.newInstance().newPullParser();
		} catch (XmlPullParserException e) {
			throw new FatalBackendException(e);
		}
	}

	List<PropfindEntryData> parse(InputStream responseBody) throws XmlPullParserException, IOException {
		List<PropfindEntryData> entryData = new ArrayList<>();
		xmlPullParser.setInput(responseBody, "UTF-8");

		while (skipToStartOf(TAG_RESPONSE)) {
			PropfindEntryData entry = parseResponse();
			if (entry != null) {
				entryData.add(entry);
			}
		}

		return entryData;
	}

	private boolean skipToStartOf(String tag) throws XmlPullParserException, IOException {
		do {
			xmlPullParser.next();
		} while (!endOfDocument() && !startOf(tag));
		return startOf(tag);
	}

	private PropfindEntryData parseResponse() throws XmlPullParserException, IOException {
		PropfindEntryData entry = null;
		String path = null;

		while (nextTagUntilEndOf(TAG_RESPONSE)) {
			if (tagIs(TAG_PROPSTAT)) {
				entry = defaultIfNull(parsePropstatWith200Status(), entry);
			} else if (tagIs(TAG_HREF)) {
				path = textInCurrentTag().trim();
			}
		}

		if (entry == null) {
			Timber.tag("WebDAV").w("No propstat element with 200 status in response element. Entry ignored.");
			Timber.tag("WebDAV").v("No propstat element with 200 status in response element. Entry ignored. Dir: %s, Path: %s", requestedFolder.getPath(), path);
			return null;
		}
		if (path == null) {
			Timber.tag("WebDAV").w("Missing href in response element. Entry ignored.");
			Timber.tag("WebDAV").v("Missing href in response element. Entry ignored. Dir: %s", requestedFolder.getPath());
			return null;
		}

		entry.setPath(path);
		return entry;
	}

	private PropfindEntryData parsePropstatWith200Status() throws IOException, XmlPullParserException {
		PropfindEntryData result = new PropfindEntryData();
		boolean statusOk = false;
		while (nextTagUntilEndOf(TAG_PROPSTAT)) {
			if (tagIs(TAG_STATUS)) {
				String text = textInCurrentTag().trim();
				String[] statusSegments = text.split(" ");
				String code = statusSegments.length > 0 ? statusSegments[1] : "";
				statusOk = STATUS_OK.equals(code);
			} else if (tagIs(TAG_COLLECTION)) {
				result.setFile(false);
			} else if (tagIs(TAG_LAST_MODIFIED)) {
				result.setLastModified(parseDate(textInCurrentTag()));
			} else if (tagIs(TAG_CONTENT_LENGTH)) {
				result.setSize(parseLong(textInCurrentTag()));
			}
		}
		if (statusOk) {
			return result;
		} else {
			return null;
		}
	}

	private boolean nextTagUntilEndOf(String tag) throws XmlPullParserException, IOException {
		do {
			xmlPullParser.next();
		} while (!endOfDocument() && !startOfATag() && !endOf(tag));
		return startOfATag();
	}

	private boolean startOf(String tag) throws XmlPullParserException {
		return startOfATag() && tagIs(tag);
	}

	private boolean tagIs(String tag) {
		return tag.equalsIgnoreCase(localName());
	}

	private boolean startOfATag() throws XmlPullParserException {
		return xmlPullParser.getEventType() == XmlPullParser.START_TAG;
	}

	private boolean endOf(String tag) throws XmlPullParserException {
		return xmlPullParser.getEventType() == XmlPullParser.END_TAG && tag.equalsIgnoreCase(localName());
	}

	private String localName() {
		String rawName = xmlPullParser.getName();
		String[] namespaceAndLocalName = rawName.split(":", 2);
		return namespaceAndLocalName[namespaceAndLocalName.length - 1];
	}

	private boolean endOfDocument() throws XmlPullParserException {
		return xmlPullParser.getEventType() == XmlPullParser.END_DOCUMENT;
	}

	private String textInCurrentTag() throws IOException, XmlPullParserException {
		if (!startOfATag()) {
			throw new IllegalStateException("textInCurrentTag may only be called at start of a tag");
		}
		StringBuilder result = new StringBuilder();
		int ident = 0;
		do {
			switch (xmlPullParser.next()) {
				case XmlPullParser.TEXT:
					result.append(xmlPullParser.getText());
					break;
				case XmlPullParser.START_TAG:
					ident++;
					break;
				case XmlPullParser.END_TAG:
					ident--;
					break;
			}
		} while (!endOfDocument() && ident >= 0);
		return result.toString();
	}

	private PropfindEntryData defaultIfNull(PropfindEntryData value, PropfindEntryData defaultValue) {
		return value == null ? defaultValue : value;
	}

	private Optional<Date> parseDate(String text) {
		try {
			return Optional.of(new Date(text));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private Optional<Long> parseLong(String text) {
		try {
			return Optional.of(Long.parseLong(text));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

}
