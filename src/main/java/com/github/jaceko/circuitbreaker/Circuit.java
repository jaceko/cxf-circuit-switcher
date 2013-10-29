package com.github.jaceko.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Circuit {
	private static final Logger LOG = LoggerFactory.getLogger(Circuit.class);
	private final int failureThreshold;
	private final long resetTimeout;
	private final AtomicReference<CircuitState> circuitState = new AtomicReference<CircuitState>();
	private final String address;

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

	public String getAddress() {
		return address;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Circuit [address=").append(address).append(", state=")
				.append(circuitState.get()).append(", failureThreshold=").append(failureThreshold)
				.append(", resetTimeout=").append(resetTimeout).append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Circuit other = (Circuit) obj;
		if (address == null) {
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		return true;
	}


}
