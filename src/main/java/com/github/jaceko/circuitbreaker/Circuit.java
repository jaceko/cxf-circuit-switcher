package com.github.jaceko.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Circuit {
	private static final Logger LOG = LoggerFactory.getLogger(Circuit.class);
	private final int failureThreshold;
	private final long resetTimeout;
	private final AtomicReference<CircuitState> circuitState = new AtomicReference<CircuitState>();
	private String address;

	public Circuit(String address, int failureThreshold, long resetTimeout) {
		this.address = address;
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
		LOG.warn("Tripping breaker, {}", this.toString());
	}

	public void resetBreaker() {
		circuitState.set(new CircuitClosed());
		LOG.info("Breaker reset, {}", this.toString());
	}

	public void attemptReset() {
		circuitState.set(new CircuitHalfOpen());
		LOG.info("Breaker reset attempt, {}", this.toString());
	}

	public String toString2() {
		StringBuilder builder = new StringBuilder();
		builder.append("Circuit: [address=").append(address).append(", circuitState=")
				.append(circuitState.get().toString()).append(", failureThreshold=")
				.append(failureThreshold).append(", resetTimeout=").append(resetTimeout)
				.append("]");
		return builder.toString();
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Circuit [circuitState=").append(circuitState.get()).append(", address=")
				.append(address).append(", failureThreshold=").append(failureThreshold)
				.append(", resetTimeout=").append(resetTimeout).append("]");
		return builder.toString();
	}

}
