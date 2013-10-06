package org.apache.cxf.clustering;

import java.util.List;

import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.endpoint.Endpoint;

@NoJSR250Annotations
public class CircuitBreakerClusteringFeature extends FailoverFeature {

	private List<String> addressList;
	private long resetTimeout;
	private int failureThreshold;

	@Override
	public FailoverTargetSelector getTargetSelector() {
		return new CircuitBreakerFailoverTargetSelector(addressList, resetTimeout, failureThreshold);
	}

	@Override
	protected ConduitSelector initTargetSelector(Endpoint endpoint) {
		CircuitBreakerFailoverTargetSelector selector = (CircuitBreakerFailoverTargetSelector) getTargetSelector();
		selector.setEndpoint(endpoint);
		return selector;
	}

	public void setAddressList(List<String> addressList) {
		this.addressList = addressList;
	}

	public void setResetTimeout(long resetTimeout) {
		this.resetTimeout = resetTimeout;
	}

	public void setFailureThreshold(int failureThreshold) {
		this.failureThreshold = failureThreshold;
	}

}
