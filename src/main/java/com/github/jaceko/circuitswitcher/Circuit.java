package com.github.jaceko.circuitswitcher;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Circuit {
	private static final Logger LOG = LoggerFactory.getLogger(Circuit.class);
	private final int failureThreshold;
	private final long resetTimeout;
	private final AtomicReference<CircuitState> circuitState = new AtomicReference<CircuitState>();
	private final String targetAddress;

	public Circuit(String targetAddress, int failureThreshold, long resetTimeout) {
		this.targetAddress = targetAddress;
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

	public String getTargetAddress() {
		return targetAddress;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Circuit [targetAddress=").append(targetAddress).append(", state=")
				.append(circuitState.get()).append(", failureThreshold=").append(failureThreshold)
				.append(", resetTimeout=").append(resetTimeout).append("]");
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((targetAddress == null) ? 0 : targetAddress.hashCode());
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
		if (targetAddress == null) {
			if (other.targetAddress != null)
				return false;
		} else if (!targetAddress.equals(other.targetAddress))
			return false;
		return true;
	}


}
