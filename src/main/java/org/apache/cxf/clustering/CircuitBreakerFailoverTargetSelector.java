package org.apache.cxf.clustering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Retryable;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jaceko.circuitbreaker.Circuit;

public class CircuitBreakerFailoverTargetSelector extends FailoverTargetSelector {
	private static final Logger LOG = LoggerFactory
			.getLogger(CircuitBreakerFailoverTargetSelector.class);

	private static final String IS_SELECTED = "org.apache.cxf.clustering.CircuitBreakerTargetSelector.IS_SELECTED";

	private Collection<Circuit> circuits = new LinkedHashSet<Circuit>();
	private long resetTimeout;
	private int failureThreshold;

	private Long receiveTimeout;

	public CircuitBreakerFailoverTargetSelector(List<String> addressList, long resetTimeout,
			int failureThreshold, Long receiveTimeout) {
		this.resetTimeout = resetTimeout;
		this.failureThreshold = failureThreshold;
		this.receiveTimeout = receiveTimeout;
		if (addressList != null) {
			setAddressList(new ArrayList<String>(addressList));
			LOG.info("Failover nodes: " + addressList.toString());
		}
		LOG.info("Failover reset timeout: " + resetTimeout);
		LOG.info("Failure threshold: " + failureThreshold);
		LOG.info("Receive timeout: " + receiveTimeout);

	}

	/**
	 * Called when a Conduit is actually required.
	 * 
	 * @param message
	 * @return the Conduit to use for mediation of the message
	 */

	public synchronized Conduit selectConduit(Message message) {
		Conduit c = message.get(Conduit.class);
		if (c == null) {

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
			c = getSelectedConduit(message);
		}
		if (receiveTimeout != null) {
			HTTPClientPolicy httpClientPolicy = ((HTTPConduit) c).getClient();
			httpClientPolicy.setReceiveTimeout(receiveTimeout);
		}

		return c;
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
	@Override
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

		if ((circuits == null) || (circuits.isEmpty())) {
			LOG.error("No adresses configured");
			return null;
		}

		Iterator<Circuit> iterator = circuits.iterator();
		String alternateAddress = null;
		LOG.info("Checking available targets:");
		while (iterator.hasNext()) {
			Circuit target = iterator.next();
			LOG.info("Target: {}", target);
			if (target.connectionAvailable()) {
				alternateAddress = target.getAddress();
				LOG.info("Selecting: {}", target);
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

	@Override
	protected long getDelayBetweenRetries() {
		return 0;
	}

	protected InvocationContext getInvocation(InvocationKey key) {
		InvocationContext invocation;
		synchronized (this) {
			invocation = inProgress.get(key);
		}
		return invocation;
	}

	private Circuit findCircuit(String address) {
		Circuit foundCircuit = null;
		for (Circuit circuit : circuits) {
			if (address.contains(circuit.getAddress())) {
				foundCircuit = circuit;
				break;
			}
		}
		return foundCircuit;
	}

	protected void onSuccess(Map<String, Object> context) {
		String address = getAddressFrom(context);
		Circuit circuit = findCircuit(address);
		if (circuit != null) {
			circuit.handleSuccesfullConnection();
			LOG.debug("onSuccess: address: {}, circuit: {}, context: {}", address, circuit, context);
		} 

	}
	
	protected void onFailure(Map<String, Object> context) {
		String address = getAddressFrom(context);
		Circuit circuit = findCircuit(address);

		
		if (circuit != null) {
			circuit.handleFailedConnection();
			LOG.debug("onFailure: address: {}, circuit: {}, context: {}", address, circuit, context);
		}

	}


	private String getAddressFrom(Map<String, Object> context) {
		Map<String, Object> requestContext = CastUtils.cast((Map<?, ?>) context
				.get(Client.REQUEST_CONTEXT));

		return (String) requestContext.get(Message.ENDPOINT_ADDRESS);
	}

	final void setAddressList(List<String> addressList) {
		circuits = new LinkedHashSet<Circuit>();
		for (String address : addressList) {
			circuits.add(new Circuit(address, this.failureThreshold, this.resetTimeout));
		}
	}

	void setResetTimeout(long resetTimeout) {
		this.resetTimeout = resetTimeout;
	}

	void setFailureThreshold(int failureThreshold) {
		this.failureThreshold = failureThreshold;
	}

	void setReceiveTimeout(Long receiveTimeout) {
		this.receiveTimeout = receiveTimeout;
	}

}
