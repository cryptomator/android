package org.cryptomator.data.cloud.crypto

import com.google.common.io.BaseEncoding
import org.cryptomator.cryptolib.api.Masterkey
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

internal class RecoveryKeyFactoryTest {

	@Test
	@DisplayName("createRecoveryKey() matches the recovery key of Cryptomator for desktop")
	fun testCreateRecoveryKey() {
		val rawKey = BaseEncoding.base16().lowerCase().decode("bb01e256b7836e6bf8ecaee867397065b1dc12a976676f5badb805c4a03b8b9e295d53e81e8c4a2b9af3652dd51213f11de810282c1ae4c9387a9eceb18bad03")

		val recoveryKey = RecoveryKeyFactory.createRecoveryKey(Masterkey(rawKey))

		MatcherAssert.assertThat(
			recoveryKey, CoreMatchers.`is`(
				"pathway lift abuse plenty export texture gentleman landscape beyond ceiling around leaf cafe charity border breakdown victory surely computer cat linger restrict " //
						+ "infer crowd live computer true written amazed investor boot depth left theory snow whereby terminal weekly reject happiness circuit partial cup ad"
			)
		)
	}

	@Test
	@DisplayName("createRecoveryKey() of an all-zero key")
	fun testCreateRecoveryKeyOfZeroKey() {
		val recoveryKey = RecoveryKeyFactory.createRecoveryKey(Masterkey(ByteArray(64)))

		MatcherAssert.assertThat(recoveryKey, CoreMatchers.`is`("ad ".repeat(42) + "at assume"))
	}

	@Test
	@DisplayName("createRecoveryKey() leaves the masterkey intact")
	fun testCreateRecoveryKeyLeavesMasterkeyIntact() {
		val rawKey = ByteArray(64) { it.toByte() }
		val masterkey = Masterkey(rawKey.clone())

		RecoveryKeyFactory.createRecoveryKey(masterkey)

		Assertions.assertArrayEquals(rawKey, masterkey.encoded)
	}
}
