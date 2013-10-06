package com.github.jaceko.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

public class Circuit {
	private final int failureThreshold;
	private final long resetTimeout;
	private final AtomicReference<CircuitState> circuitState = new AtomicReference<CircuitState>();
	

	public Circuit(int failureThreshold, long resetTimeout) {
		this.failureThreshold = failureThreshold;
		this.resetTimeout = resetTimeout;
		circuitState.set(new CircuitClosed());
	}

	public boolean connectionAvailable() {
		return circuitState.get().connectionAvailable(this);
	}

	public void handleFailedConnection() {
		circuitState.get().onError(this);
	}
	
	public void handleSuccesfullConnection() {
		circuitState.get().onSuccess(this);
	}


	public int getFailureThreshold() {
		return failureThreshold;
	}


	public long getResetTimeout() {
		return resetTimeout;
	}

	public void tripBreaker() {
		circuitState.set(new CircuitOpen());
	}

	public void resetBreaker() {
		circuitState.set(new CircuitClosed());
	}

	public void attemptReset() {
		circuitState.set(new CircuitHalfOpen());
	}
	
	public String toString() {
		return circuitState.get().toString();
		
	}

}
