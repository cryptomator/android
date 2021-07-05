package org.cryptomator.presentation;

import androidx.test.rule.ActivityTestRule;

import org.cryptomator.data.cloud.local.file.RootLocalFolder;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource;
import org.cryptomator.presentation.di.component.ApplicationComponent;
import org.cryptomator.presentation.testCloud.CryptoTestCloud;
import org.cryptomator.presentation.testCloud.DropboxTestCloud;
import org.cryptomator.presentation.testCloud.GoogledriveTestCloud;
import org.cryptomator.presentation.testCloud.LocalStorageTestCloud;
import org.cryptomator.presentation.testCloud.LocalTestCloud;
import org.cryptomator.presentation.testCloud.OnedriveTestCloud;
import org.cryptomator.presentation.testCloud.TestCloud;
import org.cryptomator.presentation.testCloud.WebdavTestCloud;
import org.cryptomator.presentation.ui.activity.SplashActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static org.cryptomator.presentation.CloudNodeMatchers.aFile;
import static org.cryptomator.presentation.CloudNodeMatchers.folder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.collection.IsEmptyCollection.emptyCollectionOf;

@RunWith(Parameterized.class)
public class CloudContentRepositoryBlackboxTest {

	private static final byte[] DIGITS_ONE_TO_TEN_AS_BYTES = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
	private static final byte[] DIGITS_SEVEN_TO_ONE_AS_BYTES = new byte[] {7, 6, 5, 4, 3, 2, 1};

	private static Cloud cloud;
	private static TestCloud inTestCloud;
	private static boolean setupCloudCompleted = false;
	@Rule
	public final ActivityTestRule<SplashActivity> activityTestRule = new ActivityTestRule<>(SplashActivity.class);
	@Rule
	public ExpectedException thrown = ExpectedException.none();
	private CloudContentRepository inTest;
	private CloudFolder root;

	public CloudContentRepositoryBlackboxTest(TestCloud testCloud) {
		if (inTestCloud != null && inTestCloud != testCloud) {
			setupCloudCompleted = false;
		}

		inTestCloud = testCloud;
	}

	@Parameterized.Parameters(name = "{0}")
	public static TestCloud[] data() {
		return new TestCloud[] { //
				new LocalStorageTestCloud(), //
				new LocalTestCloud(), //
				new WebdavTestCloud(getTargetContext()), //
				new DropboxTestCloud(getTargetContext()), //
				new GoogledriveTestCloud(), //
				new OnedriveTestCloud(getTargetContext()), //
				new CryptoTestCloud()};
	}

	@Before
	public void setup() throws BackendException {

		ApplicationComponent appComponent = ((CryptomatorApp) activityTestRule //
				.getActivity() //
				.getApplication()) //
				.getComponent();

		if (!setupCloudCompleted) {
			if (inTestCloud instanceof CryptoTestCloud) {
				// FIXME 343 @julian just for testcase local cloud
				Cloud testCloud = appComponent.cloudRepository().clouds(CloudType.LOCAL).get(0);
				CloudFolder rootFolder = new RootLocalFolder((LocalStorageCloud) testCloud);
				cloud = ((CryptoTestCloud) inTestCloud).getInstance(appComponent, testCloud, rootFolder);
			} else {
				cloud = inTestCloud.getInstance(appComponent);
			}

			setupCloudCompleted = true;
		}

		inTest = appComponent.cloudContentRepository();
		root = inTest.create(inTest.resolve(cloud, UUID.randomUUID().toString()));
	}

	@Test
	public void testListEmptyDirectory() throws BackendException {
		assertThat(listingOf(root), is(emptyCollectionOf(CloudNode.class)));
	}

	@Test
	public void testListDirectory() throws BackendException {
		createParentsAndWrite("a", DIGITS_SEVEN_TO_ONE_AS_BYTES);
		createParentsAndWrite("b.dat", DIGITS_ONE_TO_TEN_AS_BYTES);
		createParentsAndWrite("empty.txt", new byte[0]);
		inTest.create(inTest.folder(root, "b"));
		inTest.create(inTest.folder(root, "c"));

		assertThat(listingOf(root), containsInAnyOrder( //
				aFile().withName("a").withSize(DIGITS_SEVEN_TO_ONE_AS_BYTES.length), //
				aFile().withName("b.dat").withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length), //
				aFile().withName("empty.txt").withSize(0L), //
				folder("b"), //
				folder("c")));
	}

	@Test
	public void testCreateDirectory() throws BackendException {
		CloudFolder created = inTest.folder(root, "created");
		created = inTest.create(created);

		assertThat(listingOf(created), is(emptyCollectionOf(CloudNode.class)));
		assertThat(listingOf(root), containsInAnyOrder(folder("created")));
	}

	@Test
	public void testDeleteDirectory() throws BackendException {
		inTest.create(inTest.folder(root, "created"));
		inTest.delete(inTest.folder(root, "created"));

		assertThat(inTest.exists(inTest.folder(root, "created")), is(false));
		assertThat(listingOf(root), is(emptyCollectionOf(CloudNode.class)));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testUploadFile() throws BackendException {
		Date start = new Date();
		CloudFile file = createParentsAndWrite("file", DIGITS_ONE_TO_TEN_AS_BYTES);

		assertThat(file, is(aFile() //
				.withName("file") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length) //
				.withModifiedIn(start, new Date())));
		assertThat(listingOf(root), //
				containsInAnyOrder(aFile() //
						.withName("file") //
						.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testReplaceFile() throws BackendException {
		createParentsAndWrite("file", DIGITS_ONE_TO_TEN_AS_BYTES);
		Date start = new Date();
		CloudFile file = createParentsAndWriteOrReplace("file", DIGITS_SEVEN_TO_ONE_AS_BYTES);

		assertThat(file, is(aFile() //
				.withName("file") //
				.withSize(DIGITS_SEVEN_TO_ONE_AS_BYTES.length) //
				.withModifiedIn(start, new Date())));
		assertThat(listingOf(root), //
				containsInAnyOrder(aFile() //
						.withName("file") //
						.withSize(DIGITS_SEVEN_TO_ONE_AS_BYTES.length)));
	}

	@Test
	public void testUploadExistingFileWithoutReplaceFlag() throws BackendException {
		createParentsAndWrite("file", DIGITS_ONE_TO_TEN_AS_BYTES);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("CloudNode already exists and replace is false");

		createParentsAndWrite("file", DIGITS_ONE_TO_TEN_AS_BYTES);
	}

	@Test
	public void testDownloadFile() throws BackendException {
		createParentsAndWrite("file", DIGITS_ONE_TO_TEN_AS_BYTES);

		assertThat(read(inTest.file(root, "file")), is(DIGITS_ONE_TO_TEN_AS_BYTES));
	}

	@Test
	public void testDeleteFile() throws BackendException {
		createParentsAndWrite("file", DIGITS_ONE_TO_TEN_AS_BYTES);
		inTest.delete(inTest.file(root, "file"));

		assertThat(inTest.exists(inTest.file(root, "file")), is(false));
		assertThat(listingOf(root), is(emptyCollectionOf(CloudNode.class)));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testRenameDirectory() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder target = inTest.folder(root, "newName");

		target = inTest.move(directory, target);

		assertThat(listingOf(target), containsInAnyOrder(aFile() //
				.withName("file") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
	}

	@Test
	public void testRenameDirectoryToExistingDirectory() throws BackendException {
		CloudFolder directory = inTest.folder(root, "directory");
		directory = inTest.create(directory);
		CloudFolder target = inTest.folder(root, "newName");
		target = inTest.create(target);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("newName");

		inTest.move(directory, target);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testRenameDirectoryToExistingFile() throws BackendException {
		CloudFolder directory = inTest.folder(root, "directory");
		directory = inTest.create(directory);
		CloudFolder target = inTest.folder(root, "newName");
		createParentsAndWrite("newName", new byte[0]);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("newName");

		inTest.move(directory, target);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveDirectory() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFolder target = inTest.folder(newParent, "directory");

		target = inTest.move(directory, target);

		assertThat(listingOf(target), containsInAnyOrder(aFile() //
				.withName("file") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveDirectoryToExistingDirectory() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFolder target = inTest.folder(newParent, "directory");
		target = inTest.create(target);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("directory");

		inTest.move(directory, target);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveDirectoryToExistingFile() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFolder target = inTest.folder(newParent, "directory");
		createParentsAndWrite("newParent/directory", new byte[0]);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("directory");

		inTest.move(directory, target);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveAndRenameDirectory() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFolder target = inTest.folder(newParent, "newName");

		target = inTest.move(directory, target);

		assertThat(listingOf(target), containsInAnyOrder(aFile() //
				.withName("file") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveAndRenameDirectoryToExistingDirectory() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFolder target = inTest.folder(newParent, "newName");
		target = inTest.create(target);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("newName");

		inTest.move(directory, target);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveAndRenameDirectoryToExistingFile() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder directory = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFolder target = inTest.folder(newParent, "newName");
		createParentsAndWrite("newParent/newName", new byte[0]);

		thrown.expect(CloudNodeAlreadyExistsException.class);
		thrown.expectMessage("newName");

		inTest.move(directory, target);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testRenameFile() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFile target = inTest.file(file.getParent(), "newName");

		target = inTest.move(file, target);

		assertThat(listingOf(file.getParent()), containsInAnyOrder(aFile() //
				.withName("newName") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
		assertThat(read(target), is(DIGITS_ONE_TO_TEN_AS_BYTES));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveFile() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder oldParent = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFile target = inTest.file(newParent, "file");

		target = inTest.move(file, target);

		assertThat(listingOf(oldParent), is(emptyCollectionOf(CloudNode.class)));
		assertThat(listingOf(newParent), containsInAnyOrder(aFile() //
				.withName("file") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
		assertThat(read(target), is(DIGITS_ONE_TO_TEN_AS_BYTES));
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testMoveAndRenameFile() throws BackendException {
		CloudFile file = createParentsAndWrite("directory/file", DIGITS_ONE_TO_TEN_AS_BYTES);
		CloudFolder oldParent = file.getParent();
		CloudFolder newParent = inTest.create(inTest.folder(root, "newParent"));
		CloudFile target = inTest.file(newParent, "newName");

		target = inTest.move(file, target);

		assertThat(listingOf(oldParent), is(emptyCollectionOf(CloudNode.class)));
		assertThat(listingOf(newParent), containsInAnyOrder(aFile() //
				.withName("newName") //
				.withSize(DIGITS_ONE_TO_TEN_AS_BYTES.length)));
		assertThat(read(target), is(DIGITS_ONE_TO_TEN_AS_BYTES));
	}

	@After
	public void teardown() throws BackendException {
		if (inTest != null && root != null) {
			inTest.delete(root);
		}
	}

	private List<CloudNode> listingOf(CloudFolder testRoot) throws BackendException {
		return inTest.list(testRoot);
	}

	private byte[] read(CloudFile file) throws BackendException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		inTest.read(file, null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD);
		return out.toByteArray();
	}

	private CloudFile createParentsAndWrite(String path, byte[] data) throws BackendException {
		return createParentsAndWriteOrReplaceImpl(path, data, false);
	}

	private CloudFile createParentsAndWriteOrReplace(String path, byte[] data) throws BackendException {
		return createParentsAndWriteOrReplaceImpl(path, data, true);
	}

	private CloudFile createParentsAndWriteOrReplaceImpl(String path, byte[] data, boolean repalce) throws BackendException {
		path = root.getName() + "/" + path;
		String pathToParent = path.substring(0, path.lastIndexOf('/') + 1);
		String name = path.substring(path.lastIndexOf('/') + 1);
		CloudFolder parent = inTest.resolve(cloud, pathToParent);
		if (!inTest.exists(parent)) {
			parent = inTest.create(parent);
		}
		CloudFile file = inTest.file(parent, name, new Long(data.length));
		return inTest.write(file, ByteArrayDataSource.from(data), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, repalce, data.length);
	}
}
