package org.apache.cxf.clustering;

import java.util.List;

import org.apache.cxf.common.injection.NoJSR250Annotations;
import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.endpoint.Endpoint;

@NoJSR250Annotations
public class CircuitSwitcherClusteringFeature extends FailoverFeature {

	private List<String> addressList;
	private long resetTimeout;
	private int failureThreshold;
	private Long receiveTimeout;

	@Override
	public FailoverTargetSelector getTargetSelector() {
		return new CircuitSwitcherTargetSelector(addressList, resetTimeout,
				failureThreshold, receiveTimeout);
	}

	@Override
	protected ConduitSelector initTargetSelector(Endpoint endpoint) {
		CircuitSwitcherTargetSelector selector = (CircuitSwitcherTargetSelector) getTargetSelector();
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

	public void setReceiveTimeout(Long receiveTimeout) {
		this.receiveTimeout = receiveTimeout;
	}
}
