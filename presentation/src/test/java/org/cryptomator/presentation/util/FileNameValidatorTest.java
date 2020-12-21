package org.cryptomator.presentation.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FileNameValidatorTest {

	private static final String VALID_NAME = "filename.txt";

	@Test
	public void testValidNameIsValid() {
		assertValid(VALID_NAME);
	}

	@Test
	public void testEmptyNameIsValid() {
		assertValid("");
	}

	@Test
	public void testNameEndingWithDotIsInvalid() {
		assertInvalid("filename.");
	}

	@Test
	public void testNameEndingWithSpaceIsInvalid() {
		assertInvalid("filename ");
	}

	@Test
	public void testNameContainingBackslashIsInvalid() {
		assertInvalid("file\\name.txt");
	}

	@Test
	public void testNameContainingSlashIsInvalid() {
		assertInvalid("file/name.txt");
	}

	@Test
	public void testNameContainingColonIsInvalid() {
		assertInvalid("file:name.txt");
	}

	@Test
	public void testNameContainingStarIsInvalid() {
		assertInvalid("file*name.txt");
	}

	@Test
	public void testNameContainingQuestionMarkIsInvalid() {
		assertInvalid("file?name.txt");
	}

	@Test
	public void testNameContainingSmallerThanSignIsInvalid() {
		assertInvalid("file<name.txt");
	}

	@Test
	public void testNameContainingGreaterThanSignIsInvalid() {
		assertInvalid("file>name.txt");
	}

	@Test
	public void testNameContainingPipeIsInvalid() {
		assertInvalid("file|name.txt");
	}

	@Test
	public void testNameContainingDoubleQuoteIsInvalid() {
		assertInvalid("file\"name.txt");
	}

	private void assertValid(String name) {
		boolean isValid = !FileNameValidator.Companion.isInvalidName(name);

		assertThat(isValid, is(true));
	}

	private void assertInvalid(String name) {
		boolean isInvalid = FileNameValidator.Companion.isInvalidName(name);

		assertThat(isInvalid, is(true));
	}

}
