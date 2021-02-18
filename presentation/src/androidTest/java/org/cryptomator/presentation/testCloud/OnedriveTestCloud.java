package org.cryptomator.presentation.testCloud;

import android.content.Context;
import android.content.SharedPreferences;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.presentation.di.component.ApplicationComponent;

public class OnedriveTestCloud extends TestCloud {

	private final Context context;

	public OnedriveTestCloud(Context context) {
		this.context = context;
	}

	@Override
	public Cloud getInstance(ApplicationComponent appComponent) {
		SharedPreferences sharedPreferences = context.getSharedPreferences("com.microsoft.live", 0);
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putString("refresh_token", "OAQABAAAAAABHh4kmS_aKT5XrjzxRAtHzMkxzFfIzutF8Q04IwuU77Vmp5-ErO6muS0-watCiLjvPG" + //
				"-QICK6PwzSsJZApKZPIyTgDxJuElkhM9j9Caa-NGXKx3JPZx4Tk5X3zhmSWebZdQu2TM1N" + //
				"RrKWLl2k2m3Zj7xr1zEsjcr4tUjjrZl_W6EIglAIoi-DPOwznIEDWAzWCJeUY76CIpywax" + //
				"RTsbcZXD3u2321kIGaL-bnGLa_z5IzUbal0PcYfPNYPXXTuv8eMyl4L9Tls1tSEseTHgCu" + //
				"X3OZU2owiGQk6ycDAeyrbRaPoUOA78GYuaJGUXRmhAeH1WBccUzABdrZmAY1dAt4yu2eV7" + //
				"70RzrDQhbLCPV3u3x-7xEtvsM8w0W7096VBuu2-MXvaWuDccnCHo_PK8ketpow19_llBI9" + //
				"fx7yBnIU-HCkKuvOCKcvVq3Bv9r312bwAoWHdOrxsKrNK8aLoR317O9Cxjpr7q-YI3NSJJ" + //
				"veTK_vn2uE0e6gppGOYSmpJIvoW82ZVngpptW0jsp6rOsnkSg2yfKKpIPN-n0U1njVlYf8" + //
				"cwsS99tx8NDdCPS6MTkVmcdKRJ4dhMuuWdEm4E_hZRnnj2Pya3APxjL2eqyAR1-54TDLF-" + //
				"-L-jIJUS4bVpC_RBQn4fxcrX4s-ddo6I0ejfdC07mU_Np9p66VJ_3_Yokt64fFw-zzaGpn" + //
				"QEzRMxtJp5G40MAelYwxLhDIc-syw91JEoSSqfGJYHETKExPlnQOw-rqLGPFbIF6OKFNqO" + //
				"XtVLWKZFxIEf-EFQbhq5igZz8DZ2n-cRxC3HWi_x18tVSAA");
		editor.commit();

		return OnedriveCloud.aOnedriveCloud() //
				.withUsername("info@cryptomator.org") //
				.withAccessToken("authenticated") //
				.build();
	}

	@Override
	public String toString() {
		return "OnedriveTestCloud";
	}
}
