package com.github.jaceko.circuitswitcher;

public interface CircuitState {

	boolean connectionAvailable(Circuit circuit);
	void onSuccess(Circuit circuit);
	void onError(Circuit circuit);
	
}
