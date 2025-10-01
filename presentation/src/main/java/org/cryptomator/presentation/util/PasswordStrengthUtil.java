package org.cryptomator.presentation.util;

import android.graphics.PorterDuff;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jakewharton.rxbinding2.widget.RxTextView;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

import static java.util.Arrays.asList;

public class PasswordStrengthUtil {

	private static final List<String> SANITIZED_INPUTS = asList( //
			"cryptomator", //
			"crypto", //
			"mator", //
			"vault", //
			"tresor", //
			"dropbox", //
			"google", //
			"gdrive", //
			"drive", //
			"onedrive", //
			"webdav", //
			"local", //
			"storage", //
			"android", //
			"cloud" //
	);

	@Inject
	public PasswordStrengthUtil() {
	}

	public void startUpdatingPasswordStrengthMeter(EditText passwordInput, //
			final ProgressBar strengthMeter, //
			final TextView strengthLabel, //
			final Button button) {
		RxTextView.textChanges(passwordInput) //
				.observeOn(Schedulers.computation()) //
				.map(password -> PasswordStrength.Companion.forPassword(password.toString(), SANITIZED_INPUTS)) //
				.observeOn(AndroidSchedulers.mainThread()) //
				.subscribe(strength -> {
					strengthMeter.getProgressDrawable().setColorFilter(ResourceHelper.Companion.getColor(strength.getColor()), PorterDuff.Mode.SRC_IN);
					strengthLabel.setText(strength.getDescription());
					strengthMeter.setProgress(strength.getScore() + 1);
					button.setEnabled(strength.getScore() > PasswordStrength.EXTREMELY_WEAK.getScore());
				});
	}
}
