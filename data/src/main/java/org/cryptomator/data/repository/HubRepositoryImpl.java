package org.cryptomator.data.repository;

import android.content.Context;

import com.google.common.io.BaseEncoding;
import com.nimbusds.jose.JWEObject;

import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor;
import org.cryptomator.data.util.NetworkTimeout;
import org.cryptomator.domain.UnverifiedHubVaultConfig;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.hub.HubDeviceAlreadyRegisteredForOtherUserException;
import org.cryptomator.domain.exception.hub.HubDeviceSetupRequiredException;
import org.cryptomator.domain.exception.hub.HubInvalidSetupCodeException;
import org.cryptomator.domain.exception.hub.HubLicenseUpgradeRequiredException;
import org.cryptomator.domain.exception.hub.HubUserSetupRequiredException;
import org.cryptomator.domain.exception.hub.HubVaultAccessForbiddenException;
import org.cryptomator.domain.exception.hub.HubVaultIsArchivedException;
import org.cryptomator.domain.repository.HubRepository;
import org.cryptomator.util.crypto.HubDeviceCryptor;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import timber.log.Timber;

@Singleton
public class HubRepositoryImpl implements HubRepository {

	private final OkHttpClient httpClient;
	private final HubDeviceCryptor hubDeviceCryptor;

	@Inject
	public HubRepositoryImpl(Context context) {
		this.httpClient = new OkHttpClient.Builder() //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.connectTimeout(NetworkTimeout.CONNECTION.getTimeout(), NetworkTimeout.CONNECTION.getUnit()) //
				.readTimeout(NetworkTimeout.READ.getTimeout(), NetworkTimeout.READ.getUnit()) //
				.writeTimeout(NetworkTimeout.WRITE.getTimeout(), NetworkTimeout.WRITE.getUnit()) //
				.build();
		this.hubDeviceCryptor = HubDeviceCryptor.getInstance();
	}

	private Interceptor httpLoggingInterceptor(Context context) {
		HttpLoggingInterceptor.Logger logger = message -> Timber.tag("OkHttp").d(message);
		return new HttpLoggingInterceptor(logger, context);
	}

	@Override
	public String getVaultKeyJwe(UnverifiedHubVaultConfig unverifiedHubVaultConfig, String accessToken) throws BackendException {
		var request = new Request.Builder().get() //
				.header("Authorization", "Bearer " + accessToken) //
				.url(unverifiedHubVaultConfig.getApiBaseUrl() + "vaults/" + unverifiedHubVaultConfig.vaultId() + "/access-token") //
				.build();
		try (var response = httpClient.newCall(request).execute()) {
			switch (response.code()) {
				case HttpURLConnection.HTTP_OK:
					if (response.body() != null) {
						return response.body().string();
					} else {
						throw new FatalBackendException("Failed to load JWE, response code good but no body");
					}
				case HttpURLConnection.HTTP_PAYMENT_REQUIRED:
					throw new HubLicenseUpgradeRequiredException();
				case HttpURLConnection.HTTP_FORBIDDEN:
					throw new HubVaultAccessForbiddenException();
				case HttpURLConnection.HTTP_GONE:
					throw new HubVaultIsArchivedException();
				case 449:
					throw new HubUserSetupRequiredException();
				default:
					throw new FatalBackendException("Failed with response code " + response.code());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public UserDto getUser(UnverifiedHubVaultConfig unverifiedHubVaultConfig, String accessToken) throws FatalBackendException {
		var request = new Request.Builder().get() //
				.header("Authorization", "Bearer " + accessToken) //
				.url(unverifiedHubVaultConfig.getApiBaseUrl() + "users/me") //
				.build();
		try (var response = httpClient.newCall(request).execute()) {
			if (response.isSuccessful() && response.body() != null) {
				JSONObject jsonObject = new JSONObject(response.body().string());
				return new UserDto(jsonObject.getString("id"), jsonObject.getString("name"), jsonObject.getString("publicKey"), jsonObject.getString("privateKey"), jsonObject.getString("setupCode"));
			}
			throw new FatalBackendException("Failed to load user, bad response code " + response.code());
		} catch (IOException | JSONException e) {
			throw new FatalBackendException("Failed to load user", e);
		}
	}

	@Override
	public DeviceDto getDevice(UnverifiedHubVaultConfig unverifiedHubVaultConfig, String accessToken) throws BackendException {
		var request = new Request.Builder().get() //
				.header("Authorization", "Bearer " + accessToken) //
				.url(unverifiedHubVaultConfig.getApiBaseUrl() + "devices/" + hubDeviceCryptor.getDeviceId()).build();
		try (var response = httpClient.newCall(request).execute()) {
			switch (response.code()) {
				case HttpURLConnection.HTTP_OK:
					if (response.body() != null) {
						JSONObject jsonObject = new JSONObject(response.body().string());
						return new DeviceDto(jsonObject.getString("userPrivateKey"));
					} else {
						throw new FatalBackendException("Failed to load device, response code good but no body");
					}
				case HttpURLConnection.HTTP_NOT_FOUND:
					throw new HubDeviceSetupRequiredException();
				default:
					throw new FatalBackendException("Failed to load device with response code " + response.code());
			}
		} catch (IOException | JSONException e) {
			throw new FatalBackendException("Failed to load device", e);
		}
	}

	@Override
	public void createDevice(UnverifiedHubVaultConfig unverifiedHubVaultConfig, String accessToken, String deviceName, String setupCode, String userPrivateKey) throws BackendException {
		var deviceId = hubDeviceCryptor.getDeviceId();
		var publicKey = BaseEncoding.base64().encode(hubDeviceCryptor.getDevicePublicKey().getEncoded());

		JWEObject encryptedUserKey;
		try {
			encryptedUserKey = hubDeviceCryptor.encryptUserKey(JWEObject.parse(userPrivateKey), setupCode);
		} catch (HubDeviceCryptor.InvalidJweKeyException e) {
			throw new HubInvalidSetupCodeException(e);
		} catch (ParseException e) {
			throw new FatalBackendException("Failed to parse user private key", e);
		}
		var dto = new JSONObject();
		try {
			dto.put("id", deviceId);
			dto.put("name", deviceName);
			dto.put("publicKey", publicKey);
			dto.put("type", "MOBILE");
			dto.put("userPrivateKey", encryptedUserKey.serialize());
			dto.put("creationTime", Instant.now().toString());
		} catch (JSONException e) {
			throw new FatalBackendException("Failed to parse user private key", e);
		}

		var request = new Request.Builder() //
				.put(RequestBody.create(dto.toString(), MediaType.parse("application/json; charset=utf-8"))) //
				.header("Authorization", "Bearer " + accessToken) //
				.url(unverifiedHubVaultConfig.getApiBaseUrl() + "devices/" + deviceId) //
				.build();
		try (var response = httpClient.newCall(request).execute()) {
			switch (response.code()) {
				case HttpURLConnection.HTTP_CREATED:
					Timber.tag("HubRepositoryImpl").i("Device created");
					break;
				case HttpURLConnection.HTTP_CONFLICT:
					throw new HubDeviceAlreadyRegisteredForOtherUserException();
				default:
					throw new FatalBackendException("Failed to load device with response code " + response.code());
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	public ConfigDto getConfig(UnverifiedHubVaultConfig unverifiedHubVaultConfig, String accessToken) throws BackendException {
		var request = new Request.Builder().get() //
				.header("Authorization", "Bearer " + accessToken) //
				.url(unverifiedHubVaultConfig.getApiBaseUrl() + "config").build();
		try (var response = httpClient.newCall(request).execute()) {
			if (response.isSuccessful()) {
				if (response.body() != null) {
					JSONObject jsonObject = new JSONObject(response.body().string());
					return new ConfigDto(jsonObject.getInt("apiLevel"));
				} else {
					throw new FatalBackendException("Failed to load device, response code good but no body");
				}
			}
			throw new FatalBackendException("Failed to load device with response code " + response.code());
		} catch (IOException | JSONException e) {
			throw new FatalBackendException("Failed to load device", e);
		}
	}
}
