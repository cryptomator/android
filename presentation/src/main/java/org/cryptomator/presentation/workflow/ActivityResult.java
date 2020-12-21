package org.cryptomator.presentation.workflow;

import android.content.Intent;

import org.cryptomator.generator.BoundCallback;

import java.io.Serializable;

import static org.cryptomator.presentation.presenter.Presenter.SINGLE_RESULT;

public class ActivityResult extends AsyncResult {

	private final Intent intent;
	private final boolean resultOk;

	public ActivityResult(BoundCallback callback, Intent intent, boolean resultOk) {
		super(callback);
		this.intent = intent;
		this.resultOk = resultOk;
	}

	public boolean isResultOk() {
		return resultOk;
	}

	public <T extends Serializable> T getSingleResult(Class<T> type) {
		return type.cast(getSingleResult());
	}

	public Serializable getSingleResult() {
		return intent == null ? null : intent.getSerializableExtra(SINGLE_RESULT);
	}

	public Intent intent() {
		return intent;
	}

}
