package org.cryptomator.presentation.workflow;

import android.app.Activity;
import android.content.Intent;

import org.cryptomator.generator.BoundCallback;
import org.cryptomator.presentation.intent.IntentBuilder;
import org.cryptomator.presentation.presenter.Presenter;

import java.io.Serializable;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;

public abstract class Workflow<State extends Serializable> {

	private static final String WORKFLOW_STATE_EXTRA = "workflowState";
	private static final String WORKFLOW_CALLBACK_EXTRA = "workflowCallback";
	private static final String WORKFLOW_ACTIVITY_CLASS_EXTRA = "workflowActivityClass";

	private State state;
	private Class<? extends Activity> activityClass;
	private BoundCallback callback;

	private Presenter presenter;
	private boolean running;

	Workflow(State state) {
		this.state = state;
	}

	public void start() {
		activityClass = presenter().activity().getClass();
		doStart();
	}

	abstract void doStart();

	public void setup(Presenter presenter, Intent intent) {
		this.presenter = presenter;
		setStateFrom(intent);
	}

	private boolean setStateFrom(Intent intent) {
		Serializable stateExtra = intent.getSerializableExtra(WORKFLOW_STATE_EXTRA);
		Serializable callbackExtra = intent.getSerializableExtra(WORKFLOW_CALLBACK_EXTRA);
		Serializable activityClassExtra = intent.getSerializableExtra(WORKFLOW_ACTIVITY_CLASS_EXTRA);
		if (stateExtra != null && stateExtra.getClass() == state.getClass()) {
			this.state = (State) state.getClass().cast(stateExtra);
			this.callback = (BoundCallback) callbackExtra;
			this.activityClass = (Class<? extends Activity>) activityClassExtra;
			running = true;
			return true;
		}
		return false;
	}

	Presenter presenter() {
		if (presenter == null) {
			throw new IllegalStateException("Presenter not set");
		}
		return presenter;
	}

	State state() {
		return state;
	}

	void finish() {
		Intent intent = new Intent();
		intent.setClass(presenter.context(), activityClass);
		intent.putExtra(WORKFLOW_STATE_EXTRA, state);
		intent.setFlags(FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP);
		presenter.context().startActivity(intent);
	}

	abstract void completed();

	void chain(IntentBuilder intentBuilder, BoundCallback callback) {
		Intent intent = intentBuilder.build(presenter);
		intent.putExtra(WORKFLOW_STATE_EXTRA, state);
		intent.putExtra(WORKFLOW_ACTIVITY_CLASS_EXTRA, activityClass);
		intent.putExtra(WORKFLOW_CALLBACK_EXTRA, callback);
		presenter.startIntent(intent);
	}

	public void complete(Intent intent) {
		if (setStateFrom(intent)) {
			completed();
			running = false;
		}
	}

	public void dispatch(Serializable result) {
		SerializableResult serializable = new SerializableResult(this.callback, result);
		callback.call(this, serializable);
	}

	public boolean isRunning() {
		return running;
	}
}
