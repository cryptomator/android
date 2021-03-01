package org.cryptomator.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.cryptomator.util.ByteArrayUtils.join;
import static org.cryptomator.util.matchers.ByteArrayMatchers.emptyByteArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class ByteArrayUtilsTest {

	@Test
	public void testJoinWithEmptyCollection() {
		byte[] result = join();

		assertThat(result, is(emptyByteArray()));
	}

	@Test
	public void testJoinWithEmptyArray() {
		byte[] result = join(new byte[0]);

		assertThat(result, is(emptyByteArray()));
	}

	@Test
	public void testJoinWithSingleNonEmptyArray() {
		byte[] result = join(arrayWithSize(10).filledWith(1).build());

		assertThat(result, is(equalTo(arrayWithSize(10).filledWith(1).build())));
	}

	@Test
	public void testJoinWithTwoArrays() {
		byte[] result = join( //
				arrayWithSize(10).filledWith(1).build(), //
				arrayWithSize(9).filledWith(2).build());

		assertThat(result, //
				is(equalTo(arrayWithSize(19) //
						.append(1).times(10) //
						.append(2).times(9).build())));
	}

	@Test
	public void testJoinWithWithThreeArrays() {
		byte[] result = join( //
				arrayWithSize(2).filledWith(1).build(), //
				arrayWithSize(5).filledWith(2).build(), //
				arrayWithSize(1).filledWith(-23).build());

		assertThat(result, //
				is(equalTo(arrayWithSize(8) //
						.append(1).times(2) //
						.append(2).times(5) //
						.append(-23).times(1).build())));
	}

	private ByteArrayBuilder arrayWithSize(int size) {
		return new ByteArrayBuilder(size);
	}

	public interface ByteArrayBuilderAppendWithoutSize {

		ByteArrayBuilder times(int amount);

	}

	private static class ByteArrayBuilder {

		private final byte[] array;
		private int offset = 0;

		private ByteArrayBuilder(int size) {
			this.array = new byte[size];
		}

		public ByteArrayBuilder filledWith(int value) {
			assertValidByte(value);
			Arrays.fill(array, (byte) value);
			this.offset = array.length;
			return this;
		}

		public ByteArrayBuilderAppendWithoutSize append(final int value) {
			assertValidByte(value);
			return amount -> {
				if (offset + amount > array.length) {
					throw new IllegalArgumentException("Appending " + amount + " bytes would exceed array size");
				}
				Arrays.fill(array, offset, offset + amount, (byte) value);
				offset += amount;
				return ByteArrayBuilder.this;
			};
		}

		private void assertValidByte(int value) {
			if ((byte) value != value) {
				throw new IllegalArgumentException("Invalid byte value: " + value);
			}
		}

		public byte[] build() {
			return array;
		}

	}

}
