package org.cryptomator.data.cloud.crypto

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.InvalidClaimException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.exception.vaultconfig.VaultConfigLoadException
import org.cryptomator.domain.exception.vaultconfig.VaultKeyInvalidException
import org.cryptomator.domain.exception.vaultconfig.VaultVersionMismatchException
import java.net.URI
import java.util.UUID

class VaultConfig private constructor(builder: VaultConfigBuilder) {

	val keyId: URI
	val id: String
	val vaultFormat: Int
	val cipherCombo: CryptorProvider.Scheme
	val shorteningThreshold: Int

	fun toToken(rawKey: ByteArray): String {
		return JWT.create() //
			.withKeyId(keyId.toString()) //
			.withJWTId(id) //
			.withClaim(JSON_KEY_VAULTFORMAT, vaultFormat) //
			.withClaim(JSON_KEY_CIPHERCONFIG, cipherCombo.name) //
			.withClaim(JSON_KEY_SHORTENING_THRESHOLD, shorteningThreshold) //
			.sign(Algorithm.HMAC256(rawKey))
	}

	class VaultConfigBuilder {

		internal var id: String = UUID.randomUUID().toString()
		internal var vaultFormat = CryptoConstants.MAX_VAULT_VERSION;
		internal var cipherCombo = CryptoConstants.DEFAULT_CIPHER_COMBO
		internal var shorteningThreshold = CryptoConstants.DEFAULT_MAX_FILE_NAME;
		lateinit var keyId: URI

		fun keyId(keyId: URI): VaultConfigBuilder {
			this.keyId = keyId
			return this
		}

		fun cipherCombo(cipherCombo: CryptorProvider.Scheme): VaultConfigBuilder {
			this.cipherCombo = cipherCombo
			return this
		}

		fun shorteningThreshold(shorteningThreshold: Int): VaultConfigBuilder {
			this.shorteningThreshold = shorteningThreshold
			return this
		}

		fun id(id: String): VaultConfigBuilder {
			this.id = id
			return this
		}

		fun vaultFormat(vaultFormat: Int): VaultConfigBuilder {
			this.vaultFormat = vaultFormat
			return this
		}

		fun build(): VaultConfig {
			return VaultConfig(this)
		}
	}

	companion object {

		private const val JSON_KEY_VAULTFORMAT = "format"
		private const val JSON_KEY_CIPHERCONFIG = "cipherCombo"
		private const val JSON_KEY_SHORTENING_THRESHOLD = "shorteningThreshold"
		private const val JSON_KEY_ID = "kid"

		@JvmStatic
		@Throws(VaultConfigLoadException::class)
		fun decode(token: String): UnverifiedVaultConfig {
			val unverifiedJwt = JWT.decode(token)
			val vaultFormat = unverifiedJwt.getClaim(JSON_KEY_VAULTFORMAT).asInt()
			val keyId = URI.create(unverifiedJwt.keyId)
			if (keyId.scheme.startsWith(CryptoConstants.HUB_SCHEME)) {
				val hubClaim = unverifiedJwt.getHeaderClaim("hub").asMap()
				val clientId = hubClaim["clientId"] as String
				val authEndpoint = hubClaim["authEndpoint"] as String
				val tokenEndpoint = hubClaim["tokenEndpoint"] as String
				val authSuccessUrl = hubClaim["authSuccessUrl"] as String
				val authErrorUrl = hubClaim["authErrorUrl"] as String
				val apiBaseUrl = hubClaim["apiBaseUrl"] as String
				val devicesResourceUrl = hubClaim["devicesResourceUrl"] as String
				return UnverifiedHubVaultConfig(token, keyId, vaultFormat, clientId, authEndpoint, tokenEndpoint, authSuccessUrl, authErrorUrl, apiBaseUrl, devicesResourceUrl)
			} else {
				return UnverifiedVaultConfig(token, keyId, vaultFormat)
			}
		}

		@JvmStatic
		@Throws(VaultKeyInvalidException::class, VaultVersionMismatchException::class, VaultConfigLoadException::class)
		fun verify(rawKey: ByteArray, unverifiedVaultConfig: UnverifiedVaultConfig): VaultConfig {
			return try {
				val unverifiedJwt = JWT.decode(unverifiedVaultConfig.jwt)
				val verifier = JWT.require(initAlgorithm(rawKey, unverifiedJwt)) //
					.withClaim(JSON_KEY_VAULTFORMAT, unverifiedVaultConfig.vaultFormat) //
					.build()
				val verifiedJwt = verifier.verify(unverifiedJwt)

				val vaultConfigBuilder = createVaultConfig() //
					.keyId(URI.create(verifiedJwt.keyId)) //
					.id(verifiedJwt.getHeaderClaim(JSON_KEY_ID).asString()) //
					.cipherCombo(CryptorProvider.Scheme.valueOf(verifiedJwt.getClaim(JSON_KEY_CIPHERCONFIG).asString())) //
					.vaultFormat(verifiedJwt.getClaim(JSON_KEY_VAULTFORMAT).asInt()) //
					.shorteningThreshold(verifiedJwt.getClaim(JSON_KEY_SHORTENING_THRESHOLD).asInt()) //

				VaultConfig(vaultConfigBuilder)
			} catch (e: SignatureVerificationException) {
				throw VaultKeyInvalidException()
			} catch (e: InvalidClaimException) {
				throw VaultVersionMismatchException("Vault config not for version $unverifiedVaultConfig.vaultFormat")
			} catch (e: JWTVerificationException) {
				throw VaultConfigLoadException("Failed to verify vault config")
			}
		}

		@Throws(VaultConfigLoadException::class)
		private fun initAlgorithm(rawKey: ByteArray, jwt: DecodedJWT): Algorithm {
			return when (val algo = jwt.algorithm) {
				"HS256" -> Algorithm.HMAC256(rawKey)
				"HS384" -> Algorithm.HMAC384(rawKey)
				"HS512" -> Algorithm.HMAC512(rawKey)
				else -> throw VaultConfigLoadException("Unsupported signature algorithm: $algo")
			}
		}

		@JvmStatic
		fun createVaultConfig(): VaultConfigBuilder {
			return VaultConfigBuilder()
		}
	}

	init {
		id = builder.id
		keyId = builder.keyId
		vaultFormat = builder.vaultFormat
		cipherCombo = builder.cipherCombo
		shorteningThreshold = builder.shorteningThreshold
	}
}
