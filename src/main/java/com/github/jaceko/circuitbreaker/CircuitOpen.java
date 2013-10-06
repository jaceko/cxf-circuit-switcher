package com.github.jaceko.circuitbreaker;

import java.util.concurrent.atomic.AtomicLong;

public class CircuitOpen implements CircuitState {

	private final AtomicLong tripTime = new AtomicLong(System.currentTimeMillis());


	public void onSuccess(Circuit circuit) {
		
	}

	public void onError(Circuit circuit) {

	}

	public boolean connectionAvailable(Circuit circuit) {
		if (elapsed() > circuit.getResetTimeout()) {
			circuit.attemptReset();
			return true;
		} else {
			return false;
		}
	}

	private long elapsed() {
		long now = System.currentTimeMillis();
		long elapsed = elapsed(now);
		return elapsed;
	}

	private long elapsed(long now) {
		return now - tripTime.get();
	}

	@Override
	public String toString() {
		return String.format("CircuitOpen [tripTime=%s, elapsedTime=%s]", tripTime, elapsed());
	}


}
