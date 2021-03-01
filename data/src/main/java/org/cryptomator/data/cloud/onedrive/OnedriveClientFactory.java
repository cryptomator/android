package org.cryptomator.data.cloud.onedrive;

import android.content.Context;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;

import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor;
import org.cryptomator.data.cloud.onedrive.graph.IAuthenticationAdapter;

import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import timber.log.Timber;

import static org.cryptomator.data.util.NetworkTimeout.CONNECTION;
import static org.cryptomator.data.util.NetworkTimeout.READ;
import static org.cryptomator.data.util.NetworkTimeout.WRITE;

public class OnedriveClientFactory {

	private static OnedriveClientFactory instance;
	private final AtomicReference<IGraphServiceClient> graphServiceClient = new AtomicReference<>();
	private final IAuthenticationAdapter authenticationAdapter;
	private final Context context;

	private OnedriveClientFactory(Context context, String refreshToken) {
		this.context = context;
		this.authenticationAdapter = new MSAAuthAndroidAdapterImpl(context, refreshToken);
	}

	public static OnedriveClientFactory instance(Context context, String accessToken) {
		if (instance == null) {
			instance = new OnedriveClientFactory(context, accessToken);
		}
		return instance;
	}

	private static Interceptor httpLoggingInterceptor(Context context) {
		return new HttpLoggingInterceptor(message -> Timber.tag("OkHttp").d(message), context);
	}

	public IGraphServiceClient client() {
		if (graphServiceClient.get() == null) {

			OkHttpClient.Builder builder = new OkHttpClient() //
					.newBuilder() //
					.connectTimeout(CONNECTION.getTimeout(), CONNECTION.getUnit()) //
					.readTimeout(READ.getTimeout(), READ.getUnit()) //
					.writeTimeout(WRITE.getTimeout(), WRITE.getUnit()) //
					.addInterceptor(httpLoggingInterceptor(context));

			OnedriveHttpProvider onedriveHttpProvider = new OnedriveHttpProvider(new DefaultClientConfig() {
				@Override
				public IAuthenticationProvider getAuthenticationProvider() {
					return getAuthenticationAdapter();
				}
			}, builder.build());

			graphServiceClient.set(GraphServiceClient //
					.builder() //
					.authenticationProvider(getAuthenticationAdapter()) //
					.httpProvider(onedriveHttpProvider) //
					.buildClient());
		}
		return graphServiceClient.get();
	}

	public synchronized IAuthenticationAdapter getAuthenticationAdapter() {
		return authenticationAdapter;
	}

}
