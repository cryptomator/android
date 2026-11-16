package org.cryptomator.domain.usecases;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import org.cryptomator.domain.exception.license.DesktopSupporterCertificateException;
import org.cryptomator.domain.exception.license.LicenseNotValidException;
import org.cryptomator.domain.exception.license.NoLicenseAvailableException;
import org.cryptomator.util.SharedPreferencesHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DoLicenseCheckTest {

	private final SharedPreferencesHandler sharedPreferencesHandler = mock(SharedPreferencesHandler.class);

	@Nested
	@DisplayName("License retrieval from preferences")
	class LicenseRetrieval {

		@Test
		@DisplayName("Empty license + empty preference throws NoLicenseAvailableException")
		void emptyLicenseAndEmptyPreference() {
			when(sharedPreferencesHandler.licenseToken()).thenReturn("");

			DoLicenseCheck inTest = testCandidate("");

			assertThrows(NoLicenseAvailableException.class, inTest::execute);
		}

		@Test
		@DisplayName("Empty license + stored preference attempts verification with stored token")
		void emptyLicenseWithStoredPreference() {
			when(sharedPreferencesHandler.licenseToken()).thenReturn("some-stored-token");

			DoLicenseCheck inTest = testCandidate("");

			assertThrows(LicenseNotValidException.class, inTest::execute);
			verify(sharedPreferencesHandler, never()).setLicenseToken(any());
		}

		@Test
		@DisplayName("Non-empty license stores token in preferences")
		void nonEmptyLicenseSetsPreference() throws Exception {
			KeyPair keyPair = generateEcKeyPair();
			String token = signJwt(keyPair, "user@example.com");

			try (MockedStatic<Algorithm> algorithmMock = mockAlgorithmWithKey(keyPair)) {
				DoLicenseCheck inTest = testCandidate(token);
				inTest.execute();
			}

			verify(sharedPreferencesHandler).setLicenseToken(token);
		}

		@Test
		@DisplayName("Non-empty license does not read from preferences")
		void nonEmptyLicenseSkipsPreferenceLookup() throws Exception {
			KeyPair keyPair = generateEcKeyPair();
			String token = signJwt(keyPair, "user@example.com");

			try (MockedStatic<Algorithm> algorithmMock = mockAlgorithmWithKey(keyPair)) {
				DoLicenseCheck inTest = testCandidate(token);
				inTest.execute();
			}

			verify(sharedPreferencesHandler, never()).licenseToken();
		}
	}

	@Nested
	@DisplayName("Invalid token rejection")
	class InvalidTokenRejection {

		@Test
		@DisplayName("Random garbage string throws LicenseNotValidException")
		void randomGarbageString() {
			DoLicenseCheck inTest = testCandidate("this-is-not-a-jwt");

			assertThrows(LicenseNotValidException.class, inTest::execute);
			verify(sharedPreferencesHandler, never()).setLicenseToken(any());
		}

		@Test
		@DisplayName("Well-formed JWT signed with wrong key throws LicenseNotValidException")
		void jwtSignedWithWrongKey() throws Exception {
			KeyPair wrongKeyPair = generateEcKeyPair();
			String token = signJwt(wrongKeyPair, "attacker@example.com");

			DoLicenseCheck inTest = testCandidate(token);

			assertThrows(LicenseNotValidException.class, inTest::execute);
			verify(sharedPreferencesHandler, never()).setLicenseToken(any());
		}

		@Test
		@DisplayName("Corrupted base64 in JWT body throws LicenseNotValidException")
		void corruptedBase64InJwtBody() throws Exception {
			KeyPair keyPair = generateEcKeyPair();
			String validToken = signJwt(keyPair, "user@example.com");
			// Corrupt the payload section (second segment)
			String[] parts = validToken.split("\\.");
			parts[1] = "!!!invalid-base64!!!";
			String corruptedToken = parts[0] + "." + parts[1] + "." + parts[2];

			DoLicenseCheck inTest = testCandidate(corruptedToken);

			assertThrows(LicenseNotValidException.class, inTest::execute);
			verify(sharedPreferencesHandler, never()).setLicenseToken(any());
		}

		@Test
		@DisplayName("Empty JWT segments throw LicenseNotValidException")
		void emptyJwtSegments() {
			DoLicenseCheck inTest = testCandidate("a.b.c");

			assertThrows(LicenseNotValidException.class, inTest::execute);
			verify(sharedPreferencesHandler, never()).setLicenseToken(any());
		}
	}

	@Nested
	@DisplayName("Valid token acceptance")
	class ValidTokenAcceptance {

		@Test
		@DisplayName("JWT signed with matching key returns LicenseCheck with correct mail")
		void validJwtReturnsLicenseCheckWithMail() throws Exception {
			KeyPair keyPair = generateEcKeyPair();
			String token = signJwt(keyPair, "user@example.com");

			try (MockedStatic<Algorithm> algorithmMock = mockAlgorithmWithKey(keyPair)) {
				DoLicenseCheck inTest = testCandidate(token);
				LicenseCheck result = inTest.execute();

				assertThat(result.mail(), is("user@example.com"));
			}
		}

		@Test
		@DisplayName("JWT subject with special characters is returned correctly")
		void validJwtWithSpecialCharSubject() throws Exception {
			KeyPair keyPair = generateEcKeyPair();
			String token = signJwt(keyPair, "user+tag@example.co.uk");

			try (MockedStatic<Algorithm> algorithmMock = mockAlgorithmWithKey(keyPair)) {
				DoLicenseCheck inTest = testCandidate(token);
				LicenseCheck result = inTest.execute();

				assertThat(result.mail(), is("user+tag@example.co.uk"));
			}
		}

		@Test
		@DisplayName("Valid token retrieved from preferences returns LicenseCheck")
		void validTokenFromPreferencesReturnsLicenseCheck() throws Exception {
			KeyPair keyPair = generateEcKeyPair();
			String token = signJwt(keyPair, "stored@example.com");
			when(sharedPreferencesHandler.licenseToken()).thenReturn(token);

			try (MockedStatic<Algorithm> algorithmMock = mockAlgorithmWithKey(keyPair)) {
				DoLicenseCheck inTest = testCandidate("");
				LicenseCheck result = inTest.execute();

				assertThat(result.mail(), is("stored@example.com"));
			}
		}
	}

	@Nested
	@DisplayName("Desktop supporter certificate detection")
	class DesktopSupporterCertificate {

		@Test
		@DisplayName("JWT signed with desktop supporter key throws DesktopSupporterCertificateException")
		void desktopSupporterCertificateDetected() throws Exception {
			KeyPair desktopKeyPair = generateEcKeyPair();
			String token = signJwt(desktopKeyPair, "supporter@example.com");

			// execute() calls ECDSA512 twice: first for android key (fails verification),
			// then isDesktopSupporterCertificate calls it again for the desktop key (succeeds).
			// We return the real desktop algorithm for both calls -- the first will fail
			// signature verification (token was signed with the desktop key, not the android key)
			// triggering the desktop supporter certificate check, which then succeeds.
			try (MockedStatic<Algorithm> algorithmMock = mockAlgorithmForDesktopCert(desktopKeyPair)) {
				DoLicenseCheck inTest = testCandidate(token);

				DesktopSupporterCertificateException exception = assertThrows(
						DesktopSupporterCertificateException.class, inTest::execute);
				assertThat(exception.getLicense(), is(token));
			}
		}

		@Test
		@DisplayName("JWT signed with unknown key does not trigger desktop supporter detection")
		void unknownKeyDoesNotTriggerDesktopSupporterDetection() {
			DoLicenseCheck inTest = testCandidate("eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJub2JvZHlAZXhhbXBsZS5jb20ifQ.fakeSignature");

			LicenseNotValidException exception = assertThrows(LicenseNotValidException.class, inTest::execute);
			assertThat(exception.getClass().getSimpleName(), is("LicenseNotValidException"));
		}
	}

	private DoLicenseCheck testCandidate(String license) {
		return new DoLicenseCheck(sharedPreferencesHandler, license);
	}

	private static KeyPair generateEcKeyPair() throws Exception {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
		keyGen.initialize(new ECGenParameterSpec("secp521r1"));
		return keyGen.generateKeyPair();
	}

	private static String signJwt(KeyPair keyPair, String subject) {
		Algorithm algorithm = Algorithm.ECDSA512(
				(ECPublicKey) keyPair.getPublic(),
				(ECPrivateKey) keyPair.getPrivate());
		return JWT.create().withSubject(subject).sign(algorithm);
	}

	/**
	 * Mocks {@code Algorithm.ECDSA512(ECPublicKey, ECPrivateKey)} to always return
	 * an algorithm configured with the given key pair's public key. This bypasses
	 * the compile-time-inlined production public key constants.
	 */
	private static MockedStatic<Algorithm> mockAlgorithmWithKey(KeyPair keyPair) {
		Algorithm testAlgorithm = Algorithm.ECDSA512((ECPublicKey) keyPair.getPublic(), null);
		MockedStatic<Algorithm> algorithmMock = mockStatic(Algorithm.class);
		algorithmMock.when(() -> Algorithm.ECDSA512(any(ECPublicKey.class), any()))
				.thenReturn(testAlgorithm);
		return algorithmMock;
	}

	/**
	 * Mocks {@code Algorithm.ECDSA512(ECPublicKey, ECPrivateKey)} to simulate the
	 * desktop supporter certificate flow:
	 * <ol>
	 *   <li>First call (android key check): returns an algorithm with a wrong key
	 *       so signature verification fails</li>
	 *   <li>Second call (desktop key check in {@code isDesktopSupporterCertificate}):
	 *       returns an algorithm using the test desktop public key which succeeds</li>
	 * </ol>
	 */
	private static MockedStatic<Algorithm> mockAlgorithmForDesktopCert(KeyPair desktopKeyPair) throws Exception {
		// Create both algorithms before mocking to avoid recursive mock interception
		KeyPair wrongKeyPair = generateEcKeyPair();
		Algorithm wrongAlgorithm = Algorithm.ECDSA512((ECPublicKey) wrongKeyPair.getPublic(), null);
		Algorithm desktopAlgorithm = Algorithm.ECDSA512((ECPublicKey) desktopKeyPair.getPublic(), null);
		AtomicInteger callCount = new AtomicInteger(0);
		MockedStatic<Algorithm> algorithmMock = mockStatic(Algorithm.class);
		algorithmMock.when(() -> Algorithm.ECDSA512(any(ECPublicKey.class), any()))
				.thenAnswer(invocation -> {
					if (callCount.incrementAndGet() == 1) {
						return wrongAlgorithm;
					}
					return desktopAlgorithm;
				});
		return algorithmMock;
	}
}
