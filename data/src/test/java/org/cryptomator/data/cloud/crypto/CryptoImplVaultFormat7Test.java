package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileContentCryptor;
import org.cryptomator.cryptolib.api.FileHeader;
import org.cryptomator.cryptolib.api.FileHeaderCryptor;
import org.cryptomator.cryptolib.api.FileNameCryptor;
import org.cryptomator.cryptolib.common.MessageDigestSupplier;
import org.cryptomator.data.util.CopyStream;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.util.Encodings;
import org.cryptomator.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * <code>
 * path/to/vault/d/00
 * ├─ Directory 1
 * │  ├─ Directory 2
 * │  ├─ Directory 3x250
 * │  │  ├─ Directory 4x250
 * │  │  └─ File 5x250
 * │  └─ File 3
 * ├─ File 1
 * ├─ File 2
 * ├─ File 4
 * </code>
 */
public class CryptoImplVaultFormat7Test {

	private final String dirIdRoot = "";
	private final String dirId1 = "dir1-id";
	private final String dirId2 = "dir2-id";

	private Cloud cloud;
	private CryptoCloud cryptoCloud;
	private Context context;
	private Cryptor cryptor;
	private CloudContentRepository cloudContentRepository;
	private DirIdCache dirIdCache;
	private FileNameCryptor fileNameCryptor;
	private FileContentCryptor fileContentCryptor;
	private FileHeaderCryptor fileHeaderCryptor;

	private CryptoImplVaultFormat7 inTest;

	private TestFolder rootFolder = new RootTestFolder(cloud);
	private TestFolder d = new TestFolder(rootFolder, "d", "/d");
	private TestFolder lvl2Dir = new TestFolder(d, "00", "/d/00");
	private TestFolder aaFolder = new TestFolder(lvl2Dir, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

	private RootCryptoFolder root;
	private CryptoFile cryptoFile1;
	private CryptoFile cryptoFile2;
	private CryptoFile cryptoFile4;
	private CryptoFolder cryptoFolder1;

	@BeforeEach
	public void setup() throws BackendException {
		cloud = Mockito.mock(Cloud.class);
		cryptoCloud = Mockito.mock(CryptoCloud.class);
		context = Mockito.mock(Context.class);
		cryptor = Mockito.mock(Cryptor.class);
		cloudContentRepository = Mockito.mock(CloudContentRepository.class, Answers.RETURNS_DEEP_STUBS);
		dirIdCache = Mockito.mock(DirIdCache.class);
		fileNameCryptor = Mockito.mock(FileNameCryptor.class);
		fileContentCryptor = Mockito.mock(FileContentCryptor.class);
		fileHeaderCryptor = Mockito.mock(FileHeaderCryptor.class);

		Mockito.when(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor);
		Mockito.when(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor);
		Mockito.when(cryptor.fileContentCryptor()).thenReturn(fileContentCryptor);
		Mockito.when(cryptor.fileHeaderCryptor()).thenReturn(fileHeaderCryptor);

		root = new RootCryptoFolder(cryptoCloud);
		inTest = new CryptoImplVaultFormat7(context, () -> cryptor, cloudContentRepository, rootFolder, dirIdCache);

		Mockito.when(fileNameCryptor.hashDirectoryId(dirIdRoot)).thenReturn("00AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		Mockito.when(fileNameCryptor.hashDirectoryId(dirId1)).thenReturn("11BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
		Mockito.when(fileNameCryptor.hashDirectoryId(dirId2)).thenReturn("22CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir1", dirIdRoot.getBytes())).thenReturn("Directory 1");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file1", dirIdRoot.getBytes())).thenReturn("File 1");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file2", dirIdRoot.getBytes())).thenReturn("File 2");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir2", dirId1.getBytes())).thenReturn("Directory 2");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file3", dirId1.getBytes())).thenReturn("File 3");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file4", dirIdRoot.getBytes())).thenReturn("File 4");

		TestFile testFile1 = new TestFile(aaFolder, "file1.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file1.c9r", Optional.empty(), Optional.empty());
		TestFile testFile2 = new TestFile(aaFolder, "file2.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file2.c9r", Optional.empty(), Optional.empty());
		TestFile testFile4 = new TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", Optional.empty(), Optional.empty());
		TestFolder testDir1 = new TestFolder(aaFolder, "dir1.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r");
		TestFile testDir1DirFile = new TestFile(testDir1, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r/dir.c9r", Optional.empty(), Optional.empty());

		ArrayList<CloudNode> rootItems = new ArrayList<CloudNode>() {
			{
				add(testFile1);
				add(testFile2);
				add(testFile4);
				add(testDir1);
			}
		};

		cryptoFile1 = new CryptoFile(root, "File 1", "/File 1", Optional.of(15l), testFile1);
		cryptoFile2 = new CryptoFile(root, "File 2", "/File 2", Optional.empty(), testFile2);
		cryptoFile4 = new CryptoFile(root, "File 4", "/File 4", Optional.empty(), testFile4);
		cryptoFolder1 = new CryptoFolder(root, "Directory 1", "/Directory 1", testDir1DirFile);

		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "00")).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.folder(lvl2Dir, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).thenReturn(aaFolder);
		Mockito.when(cloudContentRepository.file(testDir1, "dir.c9r")).thenReturn(testDir1DirFile);
		Mockito.when(cloudContentRepository.exists(testDir1DirFile)).thenReturn(true);
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(dirId1.getBytes()), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(cryptoFolder1.getDirFile()), Mockito.any(), Mockito.any(), Mockito.any());
		Mockito.when(cloudContentRepository.list(aaFolder)).thenReturn(rootItems);
		Mockito.when(dirIdCache.put(Mockito.eq(root), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo("", aaFolder));
	}

	@Test
	@DisplayName("list(\"/\")")
	public void testListRoot() throws BackendException {
		List<CryptoNode> rootDirContent = inTest.list(root);

		Matchers.contains(rootDirContent, cryptoFile1);
		Matchers.contains(rootDirContent, cryptoFile2);
		Matchers.contains(rootDirContent, cryptoFile4);
		Matchers.contains(rootDirContent, cryptoFolder1);
	}

	@Test
	@DisplayName("list(\"/Directory 1/Directory 3x250\")")
	public void testListDirectory3x250() throws BackendException {
		String dir3Name = "Directory " + Strings.repeat("3", 250);
		String dir3Cipher = "dir" + Strings.repeat("3", 250);

		byte[] longFilenameBytes = (dir3Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
		TestFolder ddLvl2Dir = new TestFolder(d, "33", "/d/33");
		TestFolder ddFolder = new TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
		TestFolder testDir3 = new TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName);
		TestFile testDir3DirFile = new TestFile(testDir3, "dir.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/dir.c9r", Optional.empty(), Optional.empty());
		TestFile testDir3NameFile = new TestFile(testDir3, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/name.c9s", Optional.empty(), Optional.empty());

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir3Name, dirId1.getBytes())).thenReturn(dir3Cipher);
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir3Cipher, dirId1.getBytes())).thenReturn(dir3Name);
		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir);
		Mockito.when(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile);
		Mockito.when(cloudContentRepository.file(testDir3, "name.c9s")).thenReturn(testDir3NameFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 2", dirId1.getBytes())).thenReturn("dir2");

		CryptoFolder cryptoFolder3 = new CryptoFolder(cryptoFolder1, dir3Name, "/Directory 1/" + dir3Name, testDir3DirFile);
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream("dir3-id".getBytes()), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(cryptoFolder3.getDirFile()), Mockito.any(), Mockito.any(), Mockito.any());

		/*
		 * │ ├─ Directory 3x250
		 * │ │ ├─ Directory 4x250
		 * │ │ └─ File 5x250
		 */

		String dir4Name = "Directory " + Strings.repeat("4", 250);
		String dir4Cipher = "dir" + Strings.repeat("4", 250);

		byte[] longFilenameBytes4 = (dir4Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash4 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes4);
		String shortenedFileName4 = BaseEncoding.base64Url().encode(hash4) + ".c9s";

		TestFolder directory4x250 = new TestFolder(ddFolder, shortenedFileName4, "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD" + shortenedFileName4);
		TestFile testDir4DirFile = new TestFile(directory4x250, "dir.c9r", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/" + shortenedFileName4 + "/dir.c9r", Optional.empty(), Optional.empty());
		TestFile testDir4NameFile = new TestFile(directory4x250, "name.c9s", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/" + shortenedFileName4 + "/name.c9s", Optional.empty(), Optional.empty());

		Mockito.when(cloudContentRepository.file(directory4x250, "dir.c9r")).thenReturn(testDir4DirFile);
		Mockito.when(cloudContentRepository.file(directory4x250, "name.c9s")).thenReturn(testDir4NameFile);
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir4Name, "dir3-id".getBytes())).thenReturn(dir4Cipher);
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir4Cipher, "dir3-id".getBytes())).thenReturn(dir4Name);
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(dir4Cipher.getBytes("UTF-8")), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(testDir4NameFile), Mockito.any(), Mockito.any(), Mockito.any());

		ArrayList<CloudNode> dir4Files = new ArrayList<CloudNode>() {
			{
				add(testDir4DirFile);
				add(testDir4NameFile);
			}
		};

		String file5Name = "File " + Strings.repeat("5", 250);
		String file5Cipher = "file" + Strings.repeat("5", 250);

		byte[] longFilenameBytes5 = (file5Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash5 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes5);
		String shortenedFileName5 = BaseEncoding.base64Url().encode(hash5) + ".c9s";

		TestFolder directory5x250 = new TestFolder(ddFolder, shortenedFileName5, "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD" + shortenedFileName5);
		TestFile testFile5ContentFile = new TestFile(directory5x250, "contents.c9r", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/" + shortenedFileName5 + "/contents.c9r", Optional.empty(), Optional.empty());
		TestFile testFile5NameFile = new TestFile(directory5x250, "name.c9s", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/" + shortenedFileName5 + "/name.c9s", Optional.empty(), Optional.empty());

		Mockito.when(cloudContentRepository.file(directory5x250, "contents.c9r")).thenReturn(testFile5ContentFile);
		Mockito.when(cloudContentRepository.file(directory5x250, "name.c9s")).thenReturn(testFile5NameFile);
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file5Name, "dir3-id".getBytes())).thenReturn(file5Cipher);
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), file5Cipher, "dir3-id".getBytes())).thenReturn(file5Name);
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(file5Cipher.getBytes("UTF-8")), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(testFile5NameFile), Mockito.any(), Mockito.any(), Mockito.any());

		ArrayList<CloudNode> dir5Files = new ArrayList<CloudNode>() {
			{
				add(testFile5ContentFile);
				add(testFile5NameFile);
			}
		};

		ArrayList<CloudNode> dir3Items = new ArrayList<CloudNode>() {
			{
				add(directory4x250);
				add(directory5x250);
			}
		};

		Mockito.when(cloudContentRepository.exists(testDir3DirFile)).thenReturn(true);
		Mockito.when(cloudContentRepository.list(ddFolder)).thenReturn(dir3Items);
		Mockito.when(cloudContentRepository.list(directory4x250)).thenReturn(dir4Files);
		Mockito.when(cloudContentRepository.list(directory5x250)).thenReturn(dir5Files);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder3), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo("dir3-id", ddFolder));

		List<CryptoNode> folder3Content = inTest.list(cryptoFolder3);

		Matchers.contains(folder3Content, new CryptoFolder(cryptoFolder3, dir4Name, "/Directory 1/" + dir3Name + "/" + dir4Name, testDir4DirFile));
		Matchers.contains(folder3Content, new CryptoFile(cryptoFolder3, file5Name, "/Directory 1/" + dir3Name + "/" + file5Name, Optional.empty(), testFile5ContentFile));
	}

	@Test
	@DisplayName("read(\"/File 1\", NO_PROGRESS_AWARE)")
	public void testReadFromShortFile() throws BackendException {
		byte[] file1Content = "hhhhhTOPSECRET!TOPSECRET!TOPSECRET!TOPSECRET!".getBytes();
		FileHeader header = Mockito.mock(FileHeader.class);

		Mockito.when(fileContentCryptor.cleartextChunkSize()).thenReturn(8);
		Mockito.when(fileContentCryptor.ciphertextChunkSize()).thenReturn(10);
		Mockito.when(fileHeaderCryptor.headerSize()).thenReturn(5);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.getBytes())).thenReturn("file1");
		Mockito.when(fileHeaderCryptor.decryptHeader(UTF_8.encode("hhhhh"))).thenReturn(header);
		Mockito.when(fileContentCryptor.decryptChunk(Mockito.eq(UTF_8.encode("TOPSECRET!")), Mockito.anyLong(), Mockito.eq(header), Mockito.anyBoolean())).then(invocation -> UTF_8.encode("geheim!!"));

		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(file1Content), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(cryptoFile1.getCloudFile()), Mockito.any(), Mockito.any(), Mockito.any());

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1000);

		inTest.read(cryptoFile1, outputStream, ProgressAware.NO_OP_PROGRESS_AWARE);

		assertThat(outputStream.toString(), is("geheim!!geheim!!geheim!!geheim!!"));
	}

	@Test
	@DisplayName("read(\"/File 15x250\", NO_PROGRESS_AWARE)")
	public void testReadFromLongFile() throws BackendException {
		String file3Name = "File " + Strings.repeat("15", 250);

		byte[] longFilenameBytes = file3Name.getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder testFile3Folder = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testFile3ContentFile = new TestFile(testFile3Folder, "content.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/content.c9r", Optional.empty(), Optional.empty());

		byte[] file1Content = "hhhhhTOPSECRET!TOPSECRET!TOPSECRET!TOPSECRET!".getBytes();
		FileHeader header = Mockito.mock(FileHeader.class);

		Mockito.when(fileContentCryptor.cleartextChunkSize()).thenReturn(8);
		Mockito.when(fileContentCryptor.ciphertextChunkSize()).thenReturn(10);
		Mockito.when(fileHeaderCryptor.headerSize()).thenReturn(5);

		Mockito.when(fileHeaderCryptor.decryptHeader(UTF_8.encode("hhhhh"))).thenReturn(header);
		Mockito.when(fileContentCryptor.decryptChunk(Mockito.eq(UTF_8.encode("TOPSECRET!")), Mockito.anyLong(), Mockito.eq(header), Mockito.anyBoolean())).then(invocation -> UTF_8.encode("geheim!!"));

		CryptoFile cryptoFile15 = new CryptoFile(root, file3Name, "/" + file3Name, Optional.empty(), testFile3ContentFile);

		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(file1Content), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(cryptoFile15.getCloudFile()), Mockito.any(), Mockito.any(), Mockito.any());

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(1000);

		inTest.read(cryptoFile15, outputStream, ProgressAware.NO_OP_PROGRESS_AWARE);

		assertThat(outputStream.toString(), is("geheim!!geheim!!geheim!!geheim!!"));

	}

	@Test
	@DisplayName("write(\"/File 1\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	public void testWriteToShortFile() throws BackendException {
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.getBytes())).thenReturn("file1");

		FileHeader header = Mockito.mock(FileHeader.class);
		Mockito.when(fileHeaderCryptor.create()).thenReturn(header);
		Mockito.when(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".getBytes()));
		Mockito.when(fileHeaderCryptor.headerSize()).thenReturn(5);
		Mockito.when(fileContentCryptor.cleartextChunkSize()).thenReturn(10);
		Mockito.when(fileContentCryptor.ciphertextChunkSize()).thenReturn(10);
		Mockito.when(fileContentCryptor.encryptChunk(Mockito.any(ByteBuffer.class), Mockito.anyLong(), Mockito.any(FileHeader.class))).thenAnswer(invocation -> {
			ByteBuffer input = invocation.getArgument(0);
			String inStr = UTF_8.decode(input).toString();
			return ByteBuffer.wrap(inStr.toLowerCase().getBytes(UTF_8));
		});

		Mockito.when(cloudContentRepository.write(Mockito.eq(cryptoFile1.getCloudFile()), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(false), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String encrypted = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(encrypted, is("hhhhhtopsecret!"));
			return invocationOnMock.getArgument(0);
		});

		CryptoFile cryptoFile = inTest.write(cryptoFile1, ByteArrayDataSource.from("TOPSECRET!".getBytes(UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE, false, 10l);
		assertThat(cryptoFile, is(cryptoFile1));
	}

	@Test
	@DisplayName("write(\"/File 15x250\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	public void testWriteToLongFile() throws BackendException {
		String file15Name = "File " + Strings.repeat("15", 250);
		String file15Cipher = "file" + Strings.repeat("15", 250);

		byte[] longFilenameBytes = (file15Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder testFile3Folder = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testFile3WhatTheHellCLoudFile = new TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName, Optional.empty(), Optional.empty());
		TestFile testFile15ContentFile = new TestFile(testFile3Folder, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/contents.c9r", Optional.of(10l), Optional.empty());

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name, dirIdRoot.getBytes())).thenReturn(file15Cipher);

		CryptoFile cryptoFile15 = new CryptoFile(root, file15Name, "/" + file15Name, Optional.of(15l), testFile3WhatTheHellCLoudFile);

		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testFile3Folder);
		Mockito.when(cloudContentRepository.file(testFile3Folder, "contents.c9r", Optional.of(10l))).thenReturn(testFile15ContentFile);

		Mockito.when(cloudContentRepository.exists(testFile3Folder)).thenReturn(true);

		FileHeader header = Mockito.mock(FileHeader.class);
		Mockito.when(fileHeaderCryptor.create()).thenReturn(header);
		Mockito.when(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".getBytes()));
		Mockito.when(fileHeaderCryptor.headerSize()).thenReturn(5);
		Mockito.when(fileContentCryptor.cleartextChunkSize()).thenReturn(10);
		Mockito.when(fileContentCryptor.ciphertextChunkSize()).thenReturn(10);
		Mockito.when(fileContentCryptor.encryptChunk(Mockito.any(ByteBuffer.class), Mockito.anyLong(), Mockito.any(FileHeader.class))).thenAnswer(invocation -> {
			ByteBuffer input = invocation.getArgument(0);
			String inStr = UTF_8.decode(input).toString();
			return ByteBuffer.wrap(inStr.toLowerCase().getBytes(UTF_8));
		});

		Mockito.when(cloudContentRepository.write(Mockito.eq(testFile15ContentFile), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(false), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String encrypted = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(encrypted, is("hhhhhtopsecret!"));
			return invocationOnMock.getArgument(0);
		});

		CryptoFile cryptoFile = inTest.write(cryptoFile15, ByteArrayDataSource.from("TOPSECRET!".getBytes(UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE, false, 10l);
		assertThat(cryptoFile, is(cryptoFile15));

		Mockito.verify(cloudContentRepository).write(Mockito.eq(testFile15ContentFile), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(false), Mockito.anyLong());
	}

	@Test
	@DisplayName("write(\"/File 15x250\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	public void testWriteToLongFileUsingAutoRename() throws BackendException {
		String file15Name = "File " + Strings.repeat("15", 250);
		String file15Cipher = "file" + Strings.repeat("15", 250);

		byte[] longFilenameBytes = (file15Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder testFile15Folder = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testFile15WhatTheHellCLoudFile = new TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName, Optional.empty(), Optional.empty());
		TestFile testFile15ContentFile = new TestFile(testFile15Folder, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/contents.c9r", Optional.of(10l), Optional.empty());

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name, dirIdRoot.getBytes())).thenReturn(file15Cipher);

		String file15CipherRename = file15Cipher + "(1)";
		byte[] hashRename = MessageDigestSupplier.SHA1.get().digest((file15CipherRename + ".c9r").getBytes(Encodings.UTF_8));
		String shortenedFileNameRename = BaseEncoding.base64Url().encode(hashRename) + ".c9s";

		TestFolder testFile15FolderRename = new TestFolder(aaFolder, shortenedFileNameRename, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileNameRename);
		TestFile testFile15WhatTheHellCloudFileRename = new TestFile(aaFolder, shortenedFileNameRename, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileNameRename, Optional.of(20l), Optional.empty());

		TestFile testFile15ContentFileRename = new TestFile(testFile15FolderRename, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileNameRename + "/contents.c9r", Optional.of(10l), Optional.empty());
		TestFile testFile15NameFileRename = new TestFile(testFile15FolderRename, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileNameRename + "/name.c9s", Optional.of(511l), Optional.empty());

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name + " (1)", dirIdRoot.getBytes())).thenReturn(file15Cipher + "(1)");

		CryptoFile cryptoFile15 = new CryptoFile(root, file15Name, "/" + file15Name, Optional.of(15l), testFile15WhatTheHellCLoudFile);

		Mockito.when(cloudContentRepository.file(testFile15Folder, "contents.c9r", Optional.of(10l))).thenReturn(testFile15ContentFile);
		Mockito.when(cloudContentRepository.exists(testFile15ContentFile)).thenReturn(true);
		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testFile15Folder);
		Mockito.when(cloudContentRepository.exists(testFile15Folder)).thenReturn(true);
		Mockito.when(cloudContentRepository.file(testFile15Folder, "contents.c9r", Optional.of(10l))).thenReturn(testFile15ContentFile);
		Mockito.when(cloudContentRepository.file(testFile15FolderRename, "contents.c9r", Optional.of(15l))).thenReturn(testFile15ContentFileRename);

		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileNameRename)).thenReturn(testFile15FolderRename);
		Mockito.when(cloudContentRepository.exists(testFile15FolderRename)).thenReturn(false);
		Mockito.when(cloudContentRepository.create(testFile15FolderRename)).thenReturn(testFile15FolderRename);
		Mockito.when(cloudContentRepository.file(testFile15FolderRename, "name.c9s", Optional.of(511l))).thenReturn(testFile15NameFileRename);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testFile15NameFileRename), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String encrypted = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(encrypted, is(file15CipherRename + ".c9r"));
			return invocationOnMock.getArgument(0);
		});
		Mockito.when(cloudContentRepository.file(aaFolder, shortenedFileNameRename, Optional.of(20l))).thenReturn(testFile15WhatTheHellCloudFileRename);

		FileHeader header = Mockito.mock(FileHeader.class);
		Mockito.when(fileHeaderCryptor.create()).thenReturn(header);
		Mockito.when(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".getBytes()));
		Mockito.when(fileHeaderCryptor.headerSize()).thenReturn(5);
		Mockito.when(fileContentCryptor.cleartextChunkSize()).thenReturn(10);
		Mockito.when(fileContentCryptor.ciphertextChunkSize()).thenReturn(10);
		Mockito.when(fileContentCryptor.encryptChunk(Mockito.any(ByteBuffer.class), Mockito.anyLong(), Mockito.any(FileHeader.class))).thenAnswer(invocation -> {
			ByteBuffer input = invocation.getArgument(0);
			String inStr = UTF_8.decode(input).toString();
			return ByteBuffer.wrap(inStr.toLowerCase().getBytes(UTF_8));
		});

		Mockito.when(cloudContentRepository.write(Mockito.eq(testFile15ContentFileRename), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(false), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String encrypted = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(encrypted, is("hhhhhtopsecret!"));
			return invocationOnMock.getArgument(0);
		});

		CryptoFile cryptoFile = inTest.write(cryptoFile15, ByteArrayDataSource.from("TOPSECRET!".getBytes(UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE, false, 10l);
		assertThat(cryptoFile, is(cryptoFile15));

		Mockito.verify(cloudContentRepository).create(testFile15FolderRename);
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testFile15NameFileRename), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(true), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testFile15ContentFileRename), Mockito.any(DataSource.class), Mockito.any(), Mockito.eq(false), Mockito.anyLong());
	}

	@Test
	@DisplayName("create(\"/Directory 3/\")")
	public void testCreateShortFolder() throws BackendException {
		/*
		 * <code>
		 * path/to/vault/d
		 * ├─ Directory 1
		 * │ ├─ ...
		 * ├─ Directory 3
		 * ├─ ...
		 * </code>
		 */

		lvl2Dir = new TestFolder(d, "33", "/d/33");
		TestFolder ddFolder = new TestFolder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
		TestFolder testDir3 = new TestFolder(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r");
		TestFile testDir3DirFile = new TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r/dir.c9r", Optional.empty(), Optional.empty());
		CryptoFolder cryptoFolder3 = new CryptoFolder(root, "Directory 3", "/Directory 3", testDir3DirFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 3", dirIdRoot.getBytes())).thenReturn("dir3");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir3", dirIdRoot.getBytes())).thenReturn("Directory 3");
		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(cloudContentRepository.folder(d, "33")).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder3), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo("dir3-id", ddFolder));

		Mockito.when(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.create(testDir3)).thenReturn(testDir3);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testDir3DirFile), Mockito.any(), Mockito.any(), Mockito.eq(false), Mockito.anyLong())).thenReturn(testDir3DirFile);

		Mockito.when(cloudContentRepository.file(aaFolder, "dir3.c9r")).thenReturn(null);

		CloudFolder cloudFolder = inTest.create(cryptoFolder3);
		assertThat(cloudFolder, is(cryptoFolder3));

		Mockito.verify(cloudContentRepository).create(ddFolder);
		Mockito.verify(cloudContentRepository).create(testDir3);
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testDir3DirFile), Mockito.any(), Mockito.any(), Mockito.eq(false), Mockito.anyLong());
	}

	@Test
	@DisplayName("create(\"/Directory 3x250/\")")
	public void testCreateLongFolder() throws BackendException {
		/*
		 * <code>
		 * path/to/vault/d
		 * ├─ Directory 1
		 * │ ├─ ...
		 * ├─ Directory 3x250
		 * ├─ ...
		 * </code>
		 */
		String dir3Name = "Directory " + Strings.repeat("3", 250);
		String dir3Cipher = "dir" + Strings.repeat("3", 250);
		byte[] longFilenameBytes = (dir3Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder ddLvl2Dir = new TestFolder(d, "33", "/d/33");
		TestFolder ddFolder = new TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		TestFolder testDir3 = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testDir3DirFile = new TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/dir.c9r", Optional.empty(), Optional.empty());
		TestFile testDir3NameFile = new TestFile(testDir3, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/name.c9s", Optional.of(257L), Optional.empty());

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir3Name, dirIdRoot.getBytes())).thenReturn(dir3Cipher);
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir3Cipher, dirIdRoot.getBytes())).thenReturn(dir3Name);
		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir);
		Mockito.when(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir3);
		Mockito.when(cloudContentRepository.exists(testDir3)).thenReturn(false);
		Mockito.when(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile);
		Mockito.when(cloudContentRepository.file(testDir3, "name.c9s", Optional.of(257L))).thenReturn(testDir3NameFile);

		CryptoFolder cryptoFolder3 = new CryptoFolder(root, dir3Name, "/" + dir3Name, testDir3DirFile);

		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir);
		Mockito.when(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder3), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo("dir3-id", ddFolder));

		Mockito.when(cloudContentRepository.create(ddLvl2Dir)).thenReturn(ddLvl2Dir);
		Mockito.when(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.create(testDir3)).thenReturn(testDir3);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testDir3DirFile), Mockito.any(), Mockito.any(), Mockito.eq(false), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String dirContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(dirContent, is("dir3-id"));
			return testDir3DirFile;
		});
		Mockito.when(cloudContentRepository.write(Mockito.eq(testDir3NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String nameContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(nameContent, is(dir3Cipher + ".c9r"));
			return testDir3NameFile;
		});

		Mockito.when(cloudContentRepository.file(aaFolder, "dir3.c9r")).thenReturn(null);

		CloudFolder cloudFolder = inTest.folder(root, dir3Name);
		cloudFolder = inTest.create(cryptoFolder3);
		assertThat(cloudFolder, is(cryptoFolder3));

		Mockito.verify(cloudContentRepository).create(ddFolder);
		Mockito.verify(cloudContentRepository).create(testDir3);
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testDir3DirFile), Mockito.any(), Mockito.any(), Mockito.eq(false), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testDir3NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong());
	}

	@Test
	@DisplayName("delete(\"/File 4\")")
	public void testDeleteShortFile() throws BackendException {
		inTest.delete(cryptoFile4);
		Mockito.verify(cloudContentRepository).delete(cryptoFile4.getCloudFile());
	}

	@Test
	@DisplayName("delete(\"/File 15x250\")")
	public void testDeleteLongFile() throws BackendException {
		String file15Name = "File " + Strings.repeat("15", 250);
		String file15Cipher = "file" + Strings.repeat("15", 250);

		byte[] longFilenameBytes = (file15Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder testFile3Folder = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testFile3ContentFile = new TestFile(testFile3Folder, "content.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/content.c9r", Optional.empty(), Optional.empty());

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name, dirIdRoot.getBytes())).thenReturn(file15Cipher);

		CryptoFile cryptoFile15 = new CryptoFile(root, file15Name, "/" + file15Name, Optional.of(15l), testFile3ContentFile);

		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testFile3Folder);

		inTest.delete(cryptoFile15);

		Mockito.verify(cloudContentRepository).delete(testFile3Folder);
	}

	@Test
	@DisplayName("delete(\"/Directory 1/Directory 2/\")")
	public void testDeleteSingleShortFolder() throws BackendException {
		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
		TestFolder ccLvl2Dir = new TestFolder(d, "22", "/d/22");
		TestFolder ccFolder = new TestFolder(ccLvl2Dir, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", "/d/22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC");

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.getBytes())).thenReturn("dir1");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 2", dirId1.getBytes())).thenReturn("dir2");

		TestFolder testDir2 = new TestFolder(bbFolder, "dir2.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r");
		TestFile testDir2DirFile = new TestFile(testDir2, "dir.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r/dir.c9r", Optional.empty(), Optional.empty());

		CryptoFolder cryptoFolder2 = new CryptoFolder(cryptoFolder1, "Directory 2", "/Directory 1/Directory 2", testDir2DirFile);
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(dirId2.getBytes()), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(cryptoFolder2.getDirFile()), Mockito.any(), Mockito.any(), Mockito.any());

		ArrayList<CloudNode> dir1Items = new ArrayList<CloudNode>() {
			{
				add(testDir2);
			}
		};

		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "22")).thenReturn(ccLvl2Dir);
		Mockito.when(cloudContentRepository.folder(ccLvl2Dir, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")).thenReturn(ccFolder);
		Mockito.when(cloudContentRepository.file(testDir2, "dir.c9r")).thenReturn(testDir2DirFile);
		Mockito.when(cloudContentRepository.list(bbFolder)).thenReturn(dir1Items);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder2), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId2, ccFolder));
		Mockito.when(cloudContentRepository.exists(testDir2DirFile)).thenReturn(true);
		Mockito.when(cloudContentRepository.list(ccFolder)).thenReturn(new ArrayList<CloudNode>());

		inTest.delete(cryptoFolder2);

		Mockito.verify(cloudContentRepository).delete(ccFolder);
		Mockito.verify(cloudContentRepository).delete(testDir2);
		Mockito.verify(dirIdCache).evict(cryptoFolder2);
	}

	@Test
	@DisplayName("delete(\"/Directory 3x250\")")
	public void testDeleteSingleLongFolder() throws BackendException {
		String dir3Name = "Directory " + Strings.repeat("3", 250);
		String dir3Cipher = "dir" + Strings.repeat("3", 250);
		byte[] longFilenameBytes = (dir3Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder ddLvl2Dir = new TestFolder(d, "33", "/d/33");
		TestFolder ddFolder = new TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		TestFolder testDir3 = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testDir3DirFile = new TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/dir.c9r", Optional.empty(), Optional.empty());
		TestFile testDir3NameFile = new TestFile(testDir3, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/name.c9s", Optional.of(257L), Optional.empty());

		CryptoFolder cryptoFolder3 = new CryptoFolder(root, dir3Name, "/" + dir3Name, testDir3DirFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir3Name, dirIdRoot.getBytes())).thenReturn(dir3Cipher);
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir3Cipher, dirIdRoot.getBytes())).thenReturn(dir3Name);
		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir);
		Mockito.when(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir3);
		Mockito.when(cloudContentRepository.exists(testDir3)).thenReturn(false);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder3), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo("dir3-id", ddFolder));
		Mockito.when(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile);
		Mockito.when(cloudContentRepository.file(testDir3, "name.c9s", Optional.of(257L))).thenReturn(testDir3NameFile);
		Mockito.when(cloudContentRepository.list(ddFolder)).thenReturn(new ArrayList<CloudNode>());

		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		inTest.delete(cryptoFolder3);

		Mockito.verify(cloudContentRepository).delete(ddFolder);
		Mockito.verify(cloudContentRepository).delete(testDir3);
		Mockito.verify(dirIdCache).evict(cryptoFolder3);
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\")")
	public void testMoveShortFileToNewShortFile() throws BackendException {
		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFile testFile4 = new TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", Optional.empty(), Optional.empty());
		TestFile testMovedFile4 = new TestFile(bbFolder, "file4.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file4.c9r", Optional.empty(), Optional.empty());
		CryptoFile cryptoFile4 = new CryptoFile(root, "File 4", "/File 4", Optional.empty(), testFile4);
		CryptoFile cryptoMovedFile4 = new CryptoFile(cryptoFolder1, "File 4", "/Directory 1/File 4", Optional.empty(), testMovedFile4);

		Mockito.when(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4);
		Mockito.when(cloudContentRepository.file(bbFolder, "file4.c9r")).thenReturn(testMovedFile4);
		Mockito.when(cloudContentRepository.move(testFile4, testMovedFile4)).thenReturn(testMovedFile4);
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirId1.getBytes())).thenReturn("file4");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.getBytes())).thenReturn("file4");

		CryptoFile result = inTest.move(cryptoFile4, cryptoMovedFile4);

		Assertions.assertEquals("File 4", result.getName());

		Mockito.verify(cloudContentRepository).move(testFile4, testMovedFile4);
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4x250\")")
	public void testMoveShortFileToNewLongFile() throws BackendException {
		String file4Name = "File " + Strings.repeat("4", 250);
		String file4Cipher = "file" + Strings.repeat("4", 250);
		byte[] longFilenameBytes = (file4Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFile testFile4 = new TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", Optional.empty(), Optional.empty());
		CryptoFile cryptoFile4 = new CryptoFile(root, "File 4", "/File 4", Optional.empty(), testFile4);

		TestFolder testDir4 = new TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName);
		TestFile testFile4ContentFile = new TestFile(testDir4, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/contents.c9r", Optional.empty(), Optional.empty());
		TestFile testFile4NameFile = new TestFile(testDir4, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/name.c9s", Optional.of(258L), Optional.empty());

		TestFile testFile4WhatTheHellCLoudFile = new TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName, Optional.empty(), Optional.empty()); // ugly hack
		CryptoFile cryptoMovedFile4 = new CryptoFile(cryptoFolder1, file4Name, "/Directory 1/" + file4Name, Optional.empty(), testFile4WhatTheHellCLoudFile);

		Mockito.when(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4);
		Mockito.when(cloudContentRepository.file(testDir4, "contents.c9r")).thenReturn(testFile4ContentFile);
		Mockito.when(cloudContentRepository.file(testDir4, "name.c9s")).thenReturn(testFile4NameFile);
		Mockito.when(cloudContentRepository.file(testDir4, "name.c9s", Optional.of(258L))).thenReturn(testFile4NameFile);
		Mockito.when(cloudContentRepository.file(bbFolder, shortenedFileName, Optional.ofNullable(null))).thenReturn(testFile4WhatTheHellCLoudFile);
		Mockito.when(cloudContentRepository.move(testFile4, testFile4ContentFile)).thenReturn(testFile4ContentFile);
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null);
		Mockito.when(cloudContentRepository.folder(bbFolder, shortenedFileName)).thenReturn(testDir4);
		Mockito.when(cloudContentRepository.create(testDir4)).thenReturn(testDir4);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testFile4NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String dirContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(dirContent, is(file4Cipher + ".c9r"));
			return testFile4NameFile;
		});

		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.getBytes())).thenReturn("file4");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirId1.getBytes())).thenReturn(file4Cipher);

		CloudFile targetFile = inTest.file(cryptoFolder1, file4Name); // needed due to ugly side effect
		CryptoFile result = inTest.move(cryptoFile4, cryptoMovedFile4);

		Assertions.assertEquals(file4Name, result.getName());

		Mockito.verify(cloudContentRepository).create(testDir4);
		Mockito.verify(cloudContentRepository).move(testFile4, testFile4ContentFile);
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testFile4NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong());
	}

	@Test
	@DisplayName("move(\"/File 4x250\", \"/Directory 1/File 4x250\")")
	public void testMoveLongFileToNewLongFile() throws BackendException {
		String file4Name = "File " + Strings.repeat("4", 250);
		String file4Cipher = "file" + Strings.repeat("4", 250);
		byte[] longFilenameBytes = (file4Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFolder testDir4Old = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testFile4ContentFileOld = new TestFile(testDir4Old, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/contents.c9r", Optional.empty(), Optional.empty());

		CryptoFile cryptoFile4Old = new CryptoFile(root, file4Name, "/" + file4Name, Optional.empty(), testFile4ContentFileOld);

		TestFolder testDir4 = new TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName);
		TestFile testFile4ContentFile = new TestFile(testDir4, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/contents.c9r", Optional.empty(), Optional.empty());
		TestFile testFile4NameFile = new TestFile(testDir4, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/name.c9s", Optional.of(258L), Optional.empty());

		TestFile testFile4WhatTheHellCLoudFile = new TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName, Optional.empty(), Optional.empty()); // ugly hack
		CryptoFile cryptoMovedFile4 = new CryptoFile(cryptoFolder1, file4Name, "/Directory 1/" + file4Name, Optional.empty(), testFile4WhatTheHellCLoudFile);

		Mockito.when(cloudContentRepository.file(testDir4, "contents.c9r")).thenReturn(testFile4ContentFile);
		Mockito.when(cloudContentRepository.file(testDir4, "name.c9s")).thenReturn(testFile4NameFile);
		Mockito.when(cloudContentRepository.file(testDir4, "name.c9s", Optional.of(258L))).thenReturn(testFile4NameFile);
		Mockito.when(cloudContentRepository.file(bbFolder, shortenedFileName, Optional.ofNullable(null))).thenReturn(testFile4WhatTheHellCLoudFile);
		Mockito.when(cloudContentRepository.file(testDir4Old, "contents.c9r")).thenReturn(testFile4ContentFileOld);
		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir4Old);
		Mockito.when(cloudContentRepository.move(testFile4ContentFileOld, testFile4ContentFile)).thenReturn(testFile4ContentFile);
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null);
		Mockito.when(cloudContentRepository.folder(bbFolder, shortenedFileName)).thenReturn(testDir4);
		Mockito.when(cloudContentRepository.create(testDir4)).thenReturn(testDir4);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testFile4NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String dirContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(dirContent, is(file4Cipher + ".c9r"));
			return testFile4NameFile;
		});

		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirIdRoot.getBytes())).thenReturn(file4Cipher);
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirId1.getBytes())).thenReturn(file4Cipher);

		CloudFile targetFile = inTest.file(cryptoFolder1, file4Name); // needed due to ugly side effect
		CryptoFile result = inTest.move(cryptoFile4Old, cryptoMovedFile4);

		Assertions.assertEquals(file4Name, result.getName());

		Mockito.verify(cloudContentRepository).create(testDir4);
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testFile4NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).move(testFile4ContentFileOld, testFile4ContentFile);
		Mockito.verify(cloudContentRepository).delete(testDir4Old);
	}

	@Test
	@DisplayName("move(\"/Directory 1/File 4x250\", \"/File 4\")")
	public void testMoveLongFileToNewShortFile() throws BackendException {
		String file4Name = "File " + Strings.repeat("4", 250);
		String file4Cipher = "file" + Strings.repeat("4", 250);
		byte[] longFilenameBytes = (file4Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFile testFile4 = new TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", Optional.empty(), Optional.empty());
		CryptoFile cryptoFile4 = new CryptoFile(root, "File 4", "/File 4", Optional.empty(), testFile4);

		TestFolder testDir4 = new TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName);
		TestFile testFile4ContentFile = new TestFile(testDir4, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/contents.c9r", Optional.empty(), Optional.empty());
		TestFile testFile4NameFile = new TestFile(testDir4, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName + "/name.c9s", Optional.of(258L), Optional.empty());

		TestFile testFile4WhatTheHellCLoudFile = new TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/" + shortenedFileName, Optional.empty(), Optional.empty()); // ugly hack
		CryptoFile cryptoMovedFile4 = new CryptoFile(cryptoFolder1, file4Name, "/Directory 1/" + file4Name, Optional.empty(), testFile4ContentFile);

		Mockito.when(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4);
		Mockito.when(cloudContentRepository.file(testDir4, "contents.c9r")).thenReturn(testFile4ContentFile);
		Mockito.when(cloudContentRepository.file(testDir4, "name.c9s")).thenReturn(testFile4NameFile);
		Mockito.when(cloudContentRepository.file(testDir4, "name.c9s", Optional.of(258L))).thenReturn(testFile4NameFile);
		Mockito.when(cloudContentRepository.file(bbFolder, shortenedFileName, Optional.ofNullable(null))).thenReturn(testFile4WhatTheHellCLoudFile); // bad
		Mockito.when(cloudContentRepository.move(testFile4, testFile4ContentFile)).thenReturn(testFile4ContentFile);
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null);
		Mockito.when(cloudContentRepository.folder(bbFolder, shortenedFileName)).thenReturn(testDir4);
		Mockito.when(cloudContentRepository.create(testDir4)).thenReturn(testDir4);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testFile4NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String dirContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(dirContent, is(file4Cipher + ".c9r"));
			return testFile4NameFile;
		});

		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.getBytes())).thenReturn("file4");
		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirId1.getBytes())).thenReturn(file4Cipher);

		CryptoFile result = inTest.move(cryptoMovedFile4, cryptoFile4);

		Mockito.verify(cloudContentRepository).delete(testDir4);
		Mockito.verify(cloudContentRepository).move(testFile4ContentFile, testFile4);
	}

	@Test
	@DisplayName("move(\"/Directory 1\", \"/Directory 15\")")
	public void testMoveShortFolderToNewShortFolder() throws BackendException {
		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFolder testDir15 = new TestFolder(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r");
		TestFile testDir15DirFile = new TestFile(testDir15, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r/dir.c9r", Optional.empty(), Optional.empty());

		CryptoFolder cryptoFolder15 = new CryptoFolder(root, "Directory 15", "/Directory 15/", testDir15DirFile);

		Mockito.when(cloudContentRepository.file(aaFolder, "dir15.c9r", Optional.ofNullable(null))).thenReturn(new TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", Optional.empty(), Optional.empty()));
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder15), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(cloudContentRepository.create(testDir15)).thenReturn(testDir15);
		Mockito.when(cloudContentRepository.file(testDir15, "dir.c9r")).thenReturn(testDir15DirFile);
		Mockito.when(cloudContentRepository.move(cryptoFolder1.getDirFile(), testDir15DirFile)).thenReturn(testDir15DirFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 15", dirIdRoot.getBytes())).thenReturn(dirId1);

		CryptoFolder result = inTest.move(cryptoFolder1, cryptoFolder15);

		Mockito.verify(cloudContentRepository).create(testDir15);
		Mockito.verify(cloudContentRepository).move(cryptoFolder1.getDirFile(), testDir15DirFile);
		Mockito.verify(cloudContentRepository).delete(cryptoFolder1.getDirFile().getParent());
	}

	@Test
	@DisplayName("move(\"/Directory 1\", \"/Directory 15x200\")")
	public void testMoveShortFolderToNewLongFolder() throws BackendException {
		String dir15Name = "Dir " + Strings.repeat("15", 250);
		String dir15Cipher = "dir" + Strings.repeat("15", 250);
		byte[] longFilenameBytes = (dir15Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFolder testDir15 = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testDir15DirFile = new TestFile(testDir15, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/dir.c9r", Optional.empty(), Optional.empty());
		TestFile testDir15NameFile = new TestFile(testDir15, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/name.c9s", Optional.of(507L), Optional.empty());

		CryptoFolder cryptoFolder15 = new CryptoFolder(root, dir15Name, "/" + dir15Name, testDir15DirFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirId1.getBytes())).thenReturn(dir15Cipher);

		Mockito.when(cloudContentRepository.file(aaFolder, "dir15.c9r", Optional.ofNullable(null))).thenReturn(new TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", Optional.empty(), Optional.empty()));
		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir15);
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder15), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(cloudContentRepository.create(testDir15)).thenReturn(testDir15);
		Mockito.when(cloudContentRepository.file(testDir15, "dir.c9r")).thenReturn(testDir15DirFile);
		Mockito.when(cloudContentRepository.file(testDir15, "name.c9s", Optional.of(507L))).thenReturn(testDir15NameFile);
		Mockito.when(cloudContentRepository.move(cryptoFolder1.getDirFile(), testDir15DirFile)).thenReturn(testDir15DirFile);
		Mockito.when(cloudContentRepository.create(testDir15)).thenReturn(testDir15);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirIdRoot.getBytes())).thenReturn(dir15Cipher);

		Mockito.when(cloudContentRepository.write(Mockito.eq(testDir15NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String dirContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(dirContent, is(dir15Cipher + ".c9r"));
			return testDir15NameFile;
		});

		CryptoFolder targetFile = inTest.folder(root, dir15Name); // needed due to ugly side effect
		CryptoFolder result = inTest.move(cryptoFolder1, cryptoFolder15);

		Mockito.verify(cloudContentRepository).create(testDir15);
		Mockito.verify(cloudContentRepository).move(cryptoFolder1.getDirFile(), testDir15DirFile);
		Mockito.verify(cloudContentRepository).write(Mockito.eq(testDir15NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).delete(cryptoFolder1.getDirFile().getParent());
	}

	@Test
	@DisplayName("move(\"/Directory 15x200\", \"/Directory 3000\")")
	public void testMoveLongFolderToNewShortFolder() throws BackendException {
		String dir15Name = "Dir " + Strings.repeat("15", 250);
		String dir15Cipher = "dir" + Strings.repeat("15", 250);
		byte[] longFilenameBytes = (dir15Cipher + ".c9r").getBytes(Encodings.UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s";

		TestFolder bbLvl2Dir = new TestFolder(d, "11", "/d/11");
		TestFolder bbFolder = new TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

		TestFolder testDir15 = new TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName);
		TestFile testDir15DirFile = new TestFile(testDir15, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/dir.c9r", Optional.empty(), Optional.empty());
		TestFile testDir15NameFile = new TestFile(testDir15, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/" + shortenedFileName + "/name.c9s", Optional.of(507L), Optional.empty());

		CryptoFolder cryptoFolder15 = new CryptoFolder(root, dir15Name, "/" + dir15Name, testDir15DirFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirId1.getBytes())).thenReturn(dir15Cipher);

		Mockito.when(cloudContentRepository.file(aaFolder, "dir15.c9r", Optional.ofNullable(null))).thenReturn(new TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", Optional.empty(), Optional.empty()));
		Mockito.when(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir15);
		Mockito.when(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d);
		Mockito.when(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir);
		Mockito.when(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder1), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder15), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo(dirId1, bbFolder));

		Mockito.when(cloudContentRepository.create(testDir15)).thenReturn(testDir15);
		Mockito.when(cloudContentRepository.file(testDir15, "dir.c9r")).thenReturn(testDir15DirFile);
		Mockito.when(cloudContentRepository.file(testDir15, "name.c9s", Optional.of(507L))).thenReturn(testDir15NameFile);
		Mockito.when(cloudContentRepository.move(cryptoFolder1.getDirFile(), testDir15DirFile)).thenReturn(testDir15DirFile);
		Mockito.when(cloudContentRepository.create(testDir15)).thenReturn(testDir15);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirIdRoot.getBytes())).thenReturn(dir15Cipher);

		Mockito.when(cloudContentRepository.write(Mockito.eq(testDir15NameFile), Mockito.any(), Mockito.any(), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String dirContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).readLine();
			assertThat(dirContent, is(dir15Cipher + ".c9r"));
			return testDir15NameFile;
		});

		lvl2Dir = new TestFolder(d, "33", "/d/33");
		TestFolder ddFolder = new TestFolder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
		TestFolder testDir3 = new TestFolder(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r");
		TestFile testDir3DirFile = new TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r/dir.c9r", Optional.empty(), Optional.empty());
		CryptoFolder cryptoFolder3 = new CryptoFolder(root, "Directory 3", "/Directory 3", testDir3DirFile);

		Mockito.when(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 3", dirIdRoot.getBytes())).thenReturn("dir3");
		Mockito.when(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir3", dirIdRoot.getBytes())).thenReturn("Directory 3");
		Mockito.when(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(Mockito.eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

		Mockito.when(cloudContentRepository.folder(d, "33")).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.folder(aaFolder, "dir3.c9r")).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile);
		Mockito.when(dirIdCache.put(Mockito.eq(cryptoFolder3), Mockito.any())).thenReturn(new DirIdCache.DirIdInfo("dir3-id", ddFolder));

		Mockito.when(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder);
		Mockito.when(cloudContentRepository.create(testDir3)).thenReturn(testDir3);
		Mockito.when(cloudContentRepository.write(Mockito.eq(testDir3DirFile), Mockito.any(), Mockito.any(), Mockito.eq(false), Mockito.anyLong())).thenReturn(testDir3DirFile);

		Mockito.when(cloudContentRepository.file(aaFolder, "dir3.c9r")).thenReturn(null);

		CryptoFolder targetFile = inTest.folder(root, cryptoFolder3.getName()); // needed due to ugly side effect
		CryptoFolder result = inTest.move(cryptoFolder15, cryptoFolder3);

		Mockito.verify(cloudContentRepository).create(testDir3);
		Mockito.verify(cloudContentRepository).move(testDir15DirFile, cryptoFolder3.getDirFile());
		Mockito.verify(cloudContentRepository).delete(testDir15);
	}

}
