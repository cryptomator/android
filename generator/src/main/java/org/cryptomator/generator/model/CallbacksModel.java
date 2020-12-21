package org.cryptomator.generator.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class CallbacksModel {

	private final List<CallbackModel> callbacks = new ArrayList<>();

	public void add(CallbackModel callback) {
		callbacks.add(callback);
	}

	public Collection<CallbacksClassModel> getCallbacksClasses() {
		return callbacks.stream().collect(Collectors.groupingBy(CallbackModel::getCallbacksClassName)).entrySet().stream().map(CallbacksClassModel::new).collect(toList());
	}

	public static class CallbacksClassModel {

		private final String callbacksClassName;
		private final String javaPackage;
		private final List<CallbackModel> callbacks;

		public CallbacksClassModel(Map.Entry<String, List<CallbackModel>> entry) {
			String qualifiedCallbacksClassName = entry.getKey();
			int lastDot = qualifiedCallbacksClassName.lastIndexOf('.');
			this.javaPackage = qualifiedCallbacksClassName.substring(0, lastDot);
			this.callbacksClassName = qualifiedCallbacksClassName.substring(lastDot + 1);
			this.callbacks = entry.getValue();
		}

		public String getCallbacksClassName() {
			return callbacksClassName;
		}

		public String getJavaPackage() {
			return javaPackage;
		}

		public List<CallbackModel> getCallbacks() {
			return callbacks;
		}

	}

}
