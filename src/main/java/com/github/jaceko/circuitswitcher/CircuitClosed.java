package com.github.jaceko.circuitswitcher;

import java.util.concurrent.atomic.AtomicInteger;

public class CircuitClosed implements CircuitState {
	
	private final AtomicInteger failureCount = new AtomicInteger(0);


	public void onError(Circuit circuit) {
		 int currentCount = failureCount.incrementAndGet();
		 int threshold = circuit.getFailureThreshold();
		 if(currentCount >= threshold) {
			 circuit.tripBreaker();
		 }
	}

	public void onSuccess(Circuit circuit) {
		failureCount.set(0);
	}

	public boolean connectionAvailable(Circuit circuit) {
		return true;
	}

	@Override
	public String toString() {
		return String.format("CircuitClosed [failureCount=%s]", failureCount);
	}

}
