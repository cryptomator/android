package org.cryptomator.data.cloud.webdav.network;

import org.cryptomator.data.cloud.webdav.RootWebDavFolder;
import org.cryptomator.data.cloud.webdav.WebDavFile;
import org.cryptomator.data.cloud.webdav.WebDavFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static java.util.Collections.sort;
import static org.cryptomator.data.cloud.CloudFileMatcher.cloudFile;
import static org.cryptomator.data.cloud.CloudFolderMatcher.cloudFolder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

@Disabled
public class PropfindResponseParserTest {

	private static final String PARENT_CLOUD_PATH = "https://webdavserver.com/User7de989b";
	private static final String PARENT_FOLDER_PATH = "/asdasdasd/d/OC";
	private static final WebDavFolder PARENT_FOLDER = new WebDavFolder(new RootWebDavFolder( //
			WebDavCloud //
					.aWebDavCloudCloud() //
					.withUrl(PARENT_CLOUD_PATH) //
					.withPassword("Bla") //
					.withUsername("Julian") //
					.build()), //
			"OC", //
			PARENT_FOLDER_PATH); //

	private static final String RESPONSE_EMPTY_DIRECTORY = "empty-directory";
	private static final String RESPONSE_ONE_DIRECTORY = "directory-one-folder";
	private static final String RESPONSE_ONE_FILE = "directory-one-file";
	private static final String RESPONSE_ONE_FILE_NO_SERVER = "directory-one-file-no-server";
	private static final String RESPONSE_ONE_FILE_AND_FOLDERS = "directory-and-file";
	private static final String RESPONSE_MAL_FORMATTED_XMLPULLPARSER_EXCEPTION = "malformatted-response-xmlpullparser";

	private PropfindResponseParser inTest;

	@BeforeEach
	public void setup() {
		inTest = new PropfindResponseParser(PARENT_FOLDER);
	}

	@Test
	public void testEmptyResponseLeadsToEmptyCloudNodeList() throws XmlPullParserException, IOException {
		List<PropfindEntryData> result = inTest.parse(load(RESPONSE_EMPTY_DIRECTORY));
		List<CloudNode> nodes = processDirList(result, PARENT_FOLDER);

		assertThat(nodes, is(emptyCollectionOf(CloudNode.class)));
	}

	@Test
	public void testFolderResponseLeadsToFolderInCloudNodeList() throws XmlPullParserException, IOException {
		List<PropfindEntryData> result = inTest.parse(load(RESPONSE_ONE_DIRECTORY));
		List<CloudNode> nodes = processDirList(result, PARENT_FOLDER);

		assertThat(nodes.size(), is(1));
		assertThat(nodes, contains(cloudFolder(new WebDavFolder(PARENT_FOLDER, //
				"DYNTZMMHWLW25RZHWYEDHLFWIUZZG2", //
				"/asdasdasd/d/OC/DYNTZMMHWLW25RZHWYEDHLFWIUZZG2"))));
	}

	@Test
	public void testFolderWithoutServerPartInHrefResponseLeadsToFolderInCloudNodeListWithCompleteUrl() throws XmlPullParserException, IOException {
		List<PropfindEntryData> result = inTest.parse(load(RESPONSE_ONE_FILE_NO_SERVER));
		List<CloudNode> nodes = processDirList(result, PARENT_FOLDER);

		assertThat(nodes.size(), is(1));
		assertThat(nodes, contains(cloudFolder(new WebDavFolder(PARENT_FOLDER, //
				"DYNTZMMHWLW25RZHWYEDHLFWIUZZG2", //
				"/asdasdasd/d/OC/DYNTZMMHWLW25RZHWYEDHLFWIUZZG2"))));
	}

	@Test
	public void testFileResponseLeadsToFileInCloudNodeList() throws XmlPullParserException, IOException {
		List<PropfindEntryData> result = inTest.parse(load(RESPONSE_ONE_FILE));
		List<CloudNode> nodes = processDirList(result, PARENT_FOLDER);

		assertThat(nodes.size(), is(1));
		assertThat(nodes.get(0), is(cloudFile(new WebDavFile(PARENT_FOLDER, //
				"0ZRGQYTW7FFHOJDJWIJYVR3M6MOME5EAR", //
				"/asdasdasd/d/OC/0ZRGQYTW7FFHOJDJWIJYVR3M6MOME5EAR", //
				Optional.of(36L), //
				Optional.of(new Date("Thu, 30 Mar 2017 10:14:39 GMT"))))));
	}

	@Test
	public void testFileResponseLeadsToFileAndFoldersInCloudNodeList() throws XmlPullParserException, IOException {
		WebDavFolder webDavFolder = new WebDavFolder(new RootWebDavFolder( //
				WebDavCloud //
						.aWebDavCloudCloud() //
						.withUrl("") //
						.withPassword("Bla") //
						.withUsername("Julian") //
						.build()), //
				"", //
				""); //

		inTest = new PropfindResponseParser(webDavFolder);

		List<PropfindEntryData> result = inTest.parse(load(RESPONSE_ONE_FILE_AND_FOLDERS));
		List<CloudNode> nodes = processDirList(result, webDavFolder);

		assertThat(nodes.size(), is(2));
		assertThat(nodes, //
				containsInAnyOrder( //
						cloudFolder(new WebDavFolder(webDavFolder, "Gelöschte Dateien")), //
						cloudFile(new WebDavFile(webDavFolder, "0.txt", Optional.of(54175L), Optional.of(new Date("Thu, 18 May 2017 9:49:41 GMT"))))));
	}

	@Test
	public void testMallFormattedResponseLeadsToXmlPullParserException() {
		Assertions.assertThrows(XmlPullParserException.class, () -> inTest.parse(load(RESPONSE_MAL_FORMATTED_XMLPULLPARSER_EXCEPTION)));
	}

	private InputStream load(String resourceName) {
		return getClass().getResourceAsStream("/propfind-test-request/" + resourceName + ".xml");
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

	private final Comparator<PropfindEntryData> ASCENDING_BY_DEPTH = (o1, o2) -> o1.getDepth() - o2.getDepth();
}
