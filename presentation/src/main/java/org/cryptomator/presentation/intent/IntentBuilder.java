package org.cryptomator.presentation.intent;

import android.content.Intent;

import org.cryptomator.presentation.presenter.ContextHolder;

public interface IntentBuilder {

	void startActivity(ContextHolder contextHolder);

	Intent build(ContextHolder contextHolder);

}
