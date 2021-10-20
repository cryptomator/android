package org.cryptomator.data.cloud.crypto

import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.exception.vaultconfig.VaultConfigLoadException
import org.cryptomator.domain.exception.vaultconfig.VaultKeyInvalidException
import org.cryptomator.domain.exception.vaultconfig.VaultVersionMismatchException
import java.net.URI
import java.security.Key
import java.util.UUID
import io.jsonwebtoken.Claims
import io.jsonwebtoken.IncorrectClaimException
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MissingClaimException
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.SigningKeyResolverAdapter
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import kotlin.properties.Delegates

class VaultConfig private constructor(builder: VaultConfigBuilder) {

	val keyId: URI
	val id: String
	val vaultFormat: Int
	val cipherCombo: CryptorProvider.Scheme
	val shorteningThreshold: Int

	fun toToken(rawKey: ByteArray): String {
		return Jwts.builder()
			.setHeaderParam(JSON_KEY_ID, keyId.toASCIIString()) //
			.setId(id) //
			.claim(JSON_KEY_VAULTFORMAT, vaultFormat) //
			.claim(JSON_KEY_CIPHERCONFIG, cipherCombo.name) //
			.claim(JSON_KEY_SHORTENING_THRESHOLD, shorteningThreshold) //
			.signWith(Keys.hmacShaKeyFor(rawKey), SignatureAlgorithm.HS256) //
			.compact()
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
			val unverifiedSigningKeyResolver = UnverifiedSigningKeyResolver()

			// At this point we can't verify the signature because we don't have the masterkey yet.
			try {
				Jwts.parserBuilder().setSigningKeyResolver(unverifiedSigningKeyResolver).build().parse(token)
			} catch (e: IllegalArgumentException) {
				return UnverifiedVaultConfig(token, unverifiedSigningKeyResolver.keyId, unverifiedSigningKeyResolver.vaultFormat)
			}
			throw VaultConfigLoadException("Failed to load vaultconfig")
		}

		@JvmStatic
		@Throws(VaultKeyInvalidException::class, VaultVersionMismatchException::class, VaultConfigLoadException::class)
		fun verify(rawKey: ByteArray, unverifiedVaultConfig: UnverifiedVaultConfig): VaultConfig {
			return try {
				val parser = Jwts //
					.parserBuilder() //
					.setSigningKey(rawKey) //
					.require(JSON_KEY_VAULTFORMAT, unverifiedVaultConfig.vaultFormat) //
					.build() //
					.parseClaimsJws(unverifiedVaultConfig.jwt)

				val vaultConfigBuilder = createVaultConfig() //
					.keyId(unverifiedVaultConfig.keyId)
					.id(parser.header[JSON_KEY_ID] as String) //
					.cipherCombo(CryptorProvider.Scheme.valueOf(parser.body.get(JSON_KEY_CIPHERCONFIG, String::class.java))) //
					.vaultFormat(unverifiedVaultConfig.vaultFormat) //
					.shorteningThreshold(parser.body[JSON_KEY_SHORTENING_THRESHOLD] as Int)

				VaultConfig(vaultConfigBuilder)
			} catch (e: JwtException) {
				when (e) {
					is MissingClaimException, is IncorrectClaimException -> throw VaultVersionMismatchException("Vault config not for version " + unverifiedVaultConfig.vaultFormat)
					is SignatureException -> throw VaultKeyInvalidException()
					else -> throw VaultConfigLoadException(e)
				}
			}
		}

		@JvmStatic
		fun createVaultConfig(): VaultConfigBuilder {
			return VaultConfigBuilder()
		}
	}

	private class UnverifiedSigningKeyResolver : SigningKeyResolverAdapter() {

		lateinit var keyId: URI
		var vaultFormat: Int by Delegates.notNull()

		override fun resolveSigningKey(jwsHeader: JwsHeader<*>, claims: Claims): Key? {
			keyId = URI.create(jwsHeader.keyId)
			vaultFormat = claims[JSON_KEY_VAULTFORMAT] as Int
			return null
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
