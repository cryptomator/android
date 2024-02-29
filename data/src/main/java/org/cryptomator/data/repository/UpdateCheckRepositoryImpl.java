package org.cryptomator.data.repository;

import android.content.Context;
import android.net.Uri;

import com.google.common.base.Optional;
import com.google.common.io.BaseEncoding;

import org.apache.commons.codec.binary.Hex;
import org.cryptomator.data.db.UpdateCheckDao;
import org.cryptomator.data.db.entities.UpdateCheckEntity;
import org.cryptomator.data.util.UserAgentInterceptor;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.update.GeneralUpdateErrorException;
import org.cryptomator.domain.exception.update.HashMismatchUpdateCheckException;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.domain.usecases.UpdateCheck;

import java.io.File;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

@Singleton
public class UpdateCheckRepositoryImpl implements UpdateCheckRepository {

	private static final String HOSTNAME_LATEST_VERSION = "https://api.cryptomator.org/android/latest-version.json";

	private final Provider<UpdateCheckDao> updateCheckDao;
	private final OkHttpClient httpClient;
	private final Context context;

	@Inject
	UpdateCheckRepositoryImpl(Provider<UpdateCheckDao> updateCheckDao, Context context) {
		this.httpClient = httpClient();
		this.updateCheckDao = updateCheckDao;
		this.context = context;
	}

	private OkHttpClient httpClient() {
		return new OkHttpClient //
				.Builder().addInterceptor(new UserAgentInterceptor()) //
				.build();
	}

	@Override
	public Optional<UpdateCheck> getUpdateCheck(final String appVersion) throws BackendException {
		LatestVersion latestVersion = loadLatestVersion();

		if (appVersion.equals(latestVersion.version)) {
			return Optional.absent();
		}

		final UpdateCheckEntity entity = updateCheckDao.get().load(1L);

		if (entity.getVersion() != null && entity.getVersion().equals(latestVersion.version) && entity.getApkSha256() != null) {
			return Optional.of(new UpdateCheckImpl("", entity));
		}

		UpdateCheck updateCheck = loadUpdateStatus(latestVersion);
		entity.setUrlToApk(updateCheck.getUrlApk());
		entity.setVersion(updateCheck.getVersion());
		entity.setApkSha256(updateCheck.getApkSha256());

		updateCheckDao.get().storeReplacing(entity);

		return Optional.of(updateCheck);
	}

	@Nullable
	@Override
	public String getLicense() {
		return updateCheckDao.get().load(1L).getLicenseToken();
	}

	@Override
	public void setLicense(String license) {
		final UpdateCheckEntity entity = updateCheckDao.get().load(1L);

		entity.setLicenseToken(license);

		updateCheckDao.get().storeReplacing(entity);
	}

	@Override
	public void update(File file) throws GeneralUpdateErrorException {
		try {
			final UpdateCheckEntity entity = updateCheckDao.get().load(1L);

			final Request request = new Request //
					.Builder() //
					.url(entity.getUrlToApk()).build();

			final Response response = httpClient.newCall(request).execute();

			if (response.isSuccessful() && response.body() != null) {
				try (BufferedSource source = response.body().source(); BufferedSink sink = Okio.buffer(Okio.sink(file))) {
					sink.writeAll(source);
					sink.flush();

					String apkSha256 = calculateSha256(file);

					if (!apkSha256.equals(entity.getApkSha256())) {
						file.delete();
						throw new HashMismatchUpdateCheckException(String.format( //
								"Sha of calculated hash (%s) doesn't match the specified one (%s)", //
								apkSha256, //
								entity.getApkSha256()));
					}
				}
			} else {
				throw new GeneralUpdateErrorException("Failed to load update file, status code is not correct: " + response.code());
			}
		} catch (IOException e) {
			throw new GeneralUpdateErrorException("Failed to load update. General error occurred.", e);
		}
	}

	private String calculateSha256(File file) throws GeneralUpdateErrorException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream digestInputStream = new DigestInputStream(context.getContentResolver().openInputStream(Uri.fromFile(file)), digest)) {
				byte[] buffer = new byte[8192];
				while (digestInputStream.read(buffer) > -1) {
				}
			}
			return new String(Hex.encodeHex(digest.digest()));
		} catch (NoSuchAlgorithmException | IOException e) {
			throw new GeneralUpdateErrorException(e);
		}
	}

	private LatestVersion loadLatestVersion() throws BackendException {
		try {
			final Request request = new Request //
					.Builder() //
					.url(HOSTNAME_LATEST_VERSION) //
					.build();
			return toLatestVersion(httpClient.newCall(request).execute());
		} catch (IOException e) {
			throw new GeneralUpdateErrorException("Failed to update. General error occurred.", e);
		}
	}

	private UpdateCheck loadUpdateStatus(LatestVersion latestVersion) throws BackendException {
		try {
			final Request request = new Request //
					.Builder() //
					.url(latestVersion.urlReleaseNote) //
					.build();
			return toUpdateCheck(httpClient.newCall(request).execute(), latestVersion);
		} catch (IOException e) {
			throw new GeneralUpdateErrorException("Failed to update.  General error occurred.", e);
		}
	}

	private LatestVersion toLatestVersion(Response response) throws IOException, GeneralUpdateErrorException {
		if (response.isSuccessful() && response.body() != null) {
			return new LatestVersion(response.body().string());
		} else {
			throw new GeneralUpdateErrorException("Failed to update. Wrong status code in response from server: " + response.code());
		}
	}

	private UpdateCheck toUpdateCheck(Response response, LatestVersion latestVersion) throws IOException, GeneralUpdateErrorException {
		if (response.isSuccessful() && response.body() != null) {
			final String releaseNote = response.body().string();
			return new UpdateCheckImpl(releaseNote, latestVersion);
		} else {
			throw new GeneralUpdateErrorException("Failed to update. Wrong status code in response from server: " + response.code());
		}
	}

	private ECPublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
		final byte[] publicKey = BaseEncoding //
				.base64() //
				.decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELOYa5ax7QZvS92HJYCBPBiR2wWfX" + "P9/Oq/yl2J1yg0Vovetp8i1A3yCtoqdHVdVytM1wNV0JXgRbWuNTAr9nlQ==");

		Key key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(publicKey));
		if (key instanceof ECPublicKey) {
			return (ECPublicKey) key;
		} else {
			throw new FatalBackendException("Key not an EC public key.");
		}
	}

	private static class UpdateCheckImpl implements UpdateCheck {

		private final String releaseNote;
		private final String version;
		private final String urlApk;
		private final String apkSha256;
		private final String urlReleaseNote;

		private UpdateCheckImpl(String releaseNote, LatestVersion latestVersion) {
			this.releaseNote = releaseNote;
			this.version = latestVersion.version;
			this.urlApk = latestVersion.urlApk;
			this.apkSha256 = latestVersion.apkSha256;
			this.urlReleaseNote = latestVersion.urlReleaseNote;
		}

		private UpdateCheckImpl(String releaseNote, UpdateCheckEntity updateCheckEntity) {
			this.releaseNote = releaseNote;
			this.version = updateCheckEntity.getVersion();
			this.urlApk = updateCheckEntity.getUrlToApk();
			this.apkSha256 = updateCheckEntity.getApkSha256();
			this.urlReleaseNote = updateCheckEntity.getUrlToReleaseNote();
		}

		@Override
		public String releaseNote() {
			return releaseNote;
		}

		@Override
		public String getVersion() {
			return version;
		}

		@Override
		public String getUrlApk() {
			return urlApk;
		}

		@Override
		public String getApkSha256() {
			return apkSha256;
		}

		@Override
		public String getUrlReleaseNote() {
			return urlReleaseNote;
		}
	}

	private class LatestVersion {

		private final String version;
		private final String urlApk;
		private final String apkSha256;
		private final String urlReleaseNote;

		LatestVersion(String json) throws GeneralUpdateErrorException {
			try {
				Claims jws = Jwts //
						.parserBuilder().setSigningKey(getPublicKey()) //
						.build() //
						.parseClaimsJws(json) //
						.getBody();

				version = jws.get("version", String.class);
				urlApk = jws.get("url", String.class);
				apkSha256 = jws.get("apk_sha_256", String.class);
				urlReleaseNote = jws.get("release_notes", String.class);
			} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
				throw new GeneralUpdateErrorException("Failed to parse latest version", e);
			}
		}
	}
}
