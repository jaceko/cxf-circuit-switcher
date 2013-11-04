package com.github.jaceko.circuitswitcher;

public class CircuitHalfOpen implements CircuitState {

	public boolean connectionAvailable(Circuit circuit) {
		return true;
	}

	public void onSuccess(Circuit circuit) {
		circuit.resetBreaker();
		
	}

	public void onError(Circuit circuit) {
		circuit.tripBreaker();
	}

	@Override
	public String toString() {
		return String.format("CircuitHalfOpen");
	}

}
