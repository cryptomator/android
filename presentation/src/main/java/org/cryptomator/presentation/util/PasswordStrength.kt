package org.cryptomator.presentation.util

import com.nulabinc.zxcvbn.Zxcvbn
import org.cryptomator.presentation.R

enum class PasswordStrength(val score: Int, val description: Int, val color: Int) {
	EMPTY(-1, R.string.empty, R.color.password_strength_empty),  //
	EXTREMELY_WEAK(0, R.string.screen_set_password_strength_indicator_0, R.color.password_strength_0),  //
	VERY_WEAK(1, R.string.screen_set_password_strength_indicator_1, R.color.password_strength_1),  //
	WEAK(2, R.string.screen_set_password_strength_indicator_2, R.color.password_strength_2),  //
	MODERATE(3, R.string.screen_set_password_strength_indicator_3, R.color.password_strength_3),  //
	GOOD(4, R.string.screen_set_password_strength_indicator_4, R.color.password_strength_4);

	companion object {

		private const val MIN_PASSWORD_LENGTH = 8

		private val zxcvbn = Zxcvbn()

		fun forPassword(password: String, sanitizedInputs: List<String>): PasswordStrength {
			return when {
				password.isEmpty() -> {
					EMPTY
				}
				password.length < MIN_PASSWORD_LENGTH -> {
					EXTREMELY_WEAK
				}
				else -> {
					forScore(zxcvbn.measure(password, sanitizedInputs).score) ?: EMPTY
				}
			}
		}

		private fun forScore(score: Int): PasswordStrength? {
			return values().firstOrNull { score == it.score }
		}
	}
}
