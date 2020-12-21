package org.cryptomator.presentation;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

public class SvgValidationTest {

	private static final File DRAWABLE_DIR = new File("../presentation/src/main/res/drawable");
	private static final Pattern CONCATENATED_FLOATS_PATTERN = Pattern.compile("(\\.\\d+)(\\.\\d)");
	private static final int EOF = -1;
	private static final String FAIL_MESSAGE = //
			"Problems detected in xml files.\n" + //
					"\tSearch files for '(\\.\\d+)(\\.\\d)' and replace with '$1 $2'.\n" + //
					"\tSee https://stackoverflow.com/questions/27561170";

	@Test
	public void validateSvgPathsDoNotContainConcatenatedFloats() throws IOException {
		boolean success = true;
		for (File xmlFile : allXmlFilesInDrawableDir()) {
			success &= validateSvgPathsDoNotContainConcatenatedFloats(xmlFile);
		}
		if (!success) {
			fail(FAIL_MESSAGE);
		}
	}

	private boolean validateSvgPathsDoNotContainConcatenatedFloats(File file) throws IOException {
		String contents = toString(file);
		Matcher matcher = CONCATENATED_FLOATS_PATTERN.matcher(contents);
		if (matcher.find()) {
			System.out.println("SvgValidationTest: Problems in " + file.getName());
			return false;
		}
		return true;
	}

	private List<File> allXmlFilesInDrawableDir() {
		List<File> result = new ArrayList<>();
		addAllXmlFiles(result, DRAWABLE_DIR);
		return result;
	}

	private void addAllXmlFiles(List<File> result, File dir) {
		for (File child : dir.listFiles()) {
			if (child.isDirectory()) {
				addAllXmlFiles(result, child);
			} else if (child.getName().endsWith(".xml")) {
				result.add(child);
			}
		}
	}

	private String toString(File file) throws IOException {
		StringWriter out = new StringWriter();
		Reader in = new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8"));
		char[] buffer = new char[4096];
		int read = 0;
		while (read != EOF) {
			out.write(buffer, 0, read);
			read = in.read(buffer);
		}
		return out.toString();
	}

}
