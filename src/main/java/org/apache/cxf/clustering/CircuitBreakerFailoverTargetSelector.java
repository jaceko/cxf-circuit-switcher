package org.apache.cxf.clustering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Retryable;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.transport.Conduit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jaceko.circuitbreaker.Circuit;

public class CircuitBreakerFailoverTargetSelector extends FailoverTargetSelector {
	private static final Logger LOG = LoggerFactory
			.getLogger(CircuitBreakerFailoverTargetSelector.class);

	private static final String IS_SELECTED = "org.apache.cxf.clustering.CircuitBreakerTargetSelector.IS_SELECTED";

	private Map<String, Circuit> circuitMap = Collections.emptyMap();
	private long resetTimeout;
	private int failureThreshold;

	public CircuitBreakerFailoverTargetSelector(List<String> addressList, long resetTimeout,
			int failureThreshold) {
		this.resetTimeout = resetTimeout;
		this.failureThreshold = failureThreshold;
		if (addressList != null) {
			setAddressList(new ArrayList<String>(addressList));
			LOG.info("Failover nodes: " + addressList.toString());
		}
		LOG.info("Failover reset timeout: " + resetTimeout);
		LOG.info("Failure threshold: " + failureThreshold);

	}

	/**
	 * Called when a Conduit is actually required.
	 * 
	 * @param message
	 * @return the Conduit to use for mediation of the message
	 */

	public synchronized Conduit selectConduit(Message message) {
		Conduit c = message.get(Conduit.class);
		if (c != null) {
			return c;
		}
		Exchange exchange = message.getExchange();
		InvocationKey key = new InvocationKey(exchange);
		InvocationContext invocation = getInvocation(key);

		if ((invocation != null) && !invocation.getContext().containsKey(IS_SELECTED)) {
			Endpoint target = getAvailableTarget();
			if (target != null && targetChanged(message, target)) {
				setEndpoint(target);
				message.put(Message.ENDPOINT_ADDRESS, target.getEndpointInfo().getAddress());
				overrideAddressProperty(invocation.getContext());
				invocation.getContext().put(IS_SELECTED, "");
			} else if (target == null) {
				throw new Fault(new IOException("No available targets"));
			}
		}

		message.put(CONDUIT_COMPARE_FULL_URL, Boolean.TRUE);
		return getSelectedConduit(message);
	}

	private boolean targetChanged(Message message, Endpoint target) {
		Object endpoinAddress = message.get(Message.ENDPOINT_ADDRESS);
		return endpoinAddress == null
				|| !endpoinAddress.toString().contains(target.getEndpointInfo().getAddress());
	}

	/**
	 * Get the failover target endpoint, if a suitable one is available.
	 * 
	 * @param exchange
	 *            the current Exchange
	 * @param invocation
	 *            the current InvocationContext
	 * @return a failover endpoint if one is available
	 * 
	 * 
	 */
	protected Endpoint getFailoverTarget(Exchange exchange, InvocationContext invocation) {
		Endpoint failoverTarget = getAvailableTarget();
		if (failoverTarget != null) {
			LOG.error("Connection error, retrying " + failoverTarget.getEndpointInfo().getAddress());
		} else {
			LOG.error("No more failover nodes available");
		}
		return failoverTarget;
	}

	/**
	 * Get first available target endpoint, if a suitable one is available.
	 * 
	 * @return healthy endpoint if one is available
	 */
	private Endpoint getAvailableTarget() {

		if ((circuitMap == null) || (circuitMap.isEmpty())) {
			LOG.error("No adresses configured");
			return null;
		}

		Set<Entry<String, Circuit>> entrySet = circuitMap.entrySet();
		Iterator<Entry<String, Circuit>> iterator = entrySet.iterator();
		String alternateAddress = null;
		LOG.info("Checking available targets:");
		while (iterator.hasNext()) {
			Entry<String, Circuit> entry = iterator.next();
			LOG.info("Target: {}, Connection state: {}", entry.getKey(), entry.getValue());
			if (entry.getValue().connectionAvailable()) {
				alternateAddress = entry.getKey();
				LOG.info("Selecting: {}, Connection state: {},", entry.getKey(), entry.getValue());
				break;
			}

		}
		if (alternateAddress != null) {
			Endpoint distributionTarget = getEndpoint();
			distributionTarget.getEndpointInfo().setAddress(alternateAddress);
			return distributionTarget;
		} else {
			return null;
		}
	}

	/**
	 * Called on completion of the MEP for which the Conduit was required.
	 * 
	 * @param exchange
	 *            represents the completed MEP
	 */
	public void complete(Exchange exchange) {
		InvocationKey key = new InvocationKey(exchange);
		InvocationContext invocation = getInvocation(key);
		boolean failover = false;
		Conduit old = (Conduit) exchange.getOutMessage().remove(Conduit.class.getName());
		if (requiresFailover(exchange)) {
			onFailure(invocation.getContext());
			LOG.debug("Failover {}", invocation.getContext());
			Endpoint failoverTarget = getFailoverTarget(exchange, invocation);
			if (failoverTarget != null) {
				setEndpoint(failoverTarget);
				if (old != null) {
					old.close();
					conduits.remove(old);
				}
				failover = performFailover(exchange, invocation);
			}
		} else {
			if (invocation != null) {
				onSuccess(invocation.getContext());
			}
		}
		if (!failover) {
			LOG.debug("Failover not required");
			synchronized (this) {
				inProgress.remove(key);
			}
			if (MessageUtils.isTrue(exchange.get("KeepConduitAlive"))) {
				return;
			}

			try {
				if (exchange.getInMessage() != null) {
					Conduit c = (Conduit) exchange.getOutMessage().get(Conduit.class);
					if (c == null) {
						getSelectedConduit(exchange.getInMessage()).close(exchange.getInMessage());
					} else {
						c.close(exchange.getInMessage());
					}
				}
			} catch (IOException e) {
			}
		}
	}

	private boolean performFailover(Exchange exchange, InvocationContext invocation) {
		Exception prevExchangeFault = (Exception) exchange.remove(Exception.class.getName());
		Message outMessage = exchange.getOutMessage();
		Exception prevMessageFault = outMessage.getContent(Exception.class);
		outMessage.setContent(Exception.class, null);
		overrideAddressProperty(invocation.getContext());
		Retryable retry = exchange.get(Retryable.class);
		exchange.clear();
		boolean failover = false;
		if (retry != null) {
			try {
				failover = true;
				long delay = getDelayBetweenRetries();
				if (delay > 0) {
					Thread.sleep(delay);
				}
				Map<String, Object> context = invocation.getContext();
				retry.invoke(invocation.getBindingOperationInfo(), invocation.getParams(), context,
						exchange);
			} catch (Exception e) {
				if (exchange.get(Exception.class) != null) {
					exchange.put(Exception.class, prevExchangeFault);
				}
				if (outMessage.getContent(Exception.class) != null) {
					outMessage.setContent(Exception.class, prevMessageFault);
				}
			}
		}
		return failover;
	}

	protected InvocationContext getInvocation(InvocationKey key) {
		InvocationContext invocation;
		synchronized (this) {
			invocation = inProgress.get(key);
		}
		return invocation;
	}

	protected void onFailure(Map<String, Object> context) {
		String address = getAddress(context);
		Circuit circuit = circuitMap.get(address);
		LOG.debug("onFailure: " + address + ", circuit " + circuit + "context: " + context);
		if (circuit != null) {
			circuit.handleFailedConnection();
		}

	}

	protected void onSuccess(Map<String, Object> context) {
		String address = getAddress(context);
		Circuit circuit = circuitMap.get(address);
		LOG.debug("onSuccess: address: " + address);
		if (circuit != null) {
			circuit.handleSuccesfullConnection();
		}

	}

	private String getAddress(Map<String, Object> context) {
		Map<String, Object> requestContext = CastUtils.cast((Map<?, ?>) context
				.get(Client.REQUEST_CONTEXT));

		return (String) requestContext.get(Message.ENDPOINT_ADDRESS);
	}

	final void setAddressList(List<String> addressList) {
		circuitMap = new LinkedHashMap<String, Circuit>();
		for (String address : addressList) {
			circuitMap.put(address, new Circuit(address, this.failureThreshold, this.resetTimeout));
		}
	}

	void setResetTimeout(long resetTimeout) {
		this.resetTimeout = resetTimeout;
	}

	void setFailureThreshold(int failureThreshold) {
		this.failureThreshold = failureThreshold;
	}

}
