package org.apache.cxf.clustering;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.Bus;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.binding.soap.Soap11;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.bus.CXFBusImpl;
import org.apache.cxf.bus.managers.BindingFactoryManagerImpl;
import org.apache.cxf.bus.managers.ConduitInitiatorManagerImpl;
import org.apache.cxf.clustering.FailoverTargetSelector.InvocationContext;
import org.apache.cxf.clustering.FailoverTargetSelector.InvocationKey;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointException;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.endpoint.Retryable;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.http.HTTPTransportFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

public class CircuitBreakerFailoverTargetSelectorTest {
	private static final String ENDPOINT_TRANSPORT_ID = "http://cxf.apache.org/transports/http";

	CircuitBreakerFailoverTargetSelector circuitBreakerTargetSelector = new CircuitBreakerFailoverTargetSelector(null, 0, 0); 
	private Retryable client;
	private Endpoint ep;

	@Before
	public void before() throws EndpointException {
		EndpointInfo ei = new EndpointInfo();
		ei.setTransportId(ENDPOINT_TRANSPORT_ID);
		ep = new EndpointImpl(null, null, ei);
		circuitBreakerTargetSelector.setEndpoint(ep);
		circuitBreakerTargetSelector.setResetTimeout(100l);
	}

	@Test
	public void shouldRequireFailoverWhereIOExceptionHasBeenThrown() {
		circuitBreakerTargetSelector = new CircuitBreakerFailoverTargetSelector(null, 0, 0);
		
		Exchange exchange = new ExchangeImpl();
		Message message = new SoapMessage(Soap11.getInstance());
		exchange.setOutMessage(message);
		message.put(Exception.class, new IOException());

		boolean requiresFailover = circuitBreakerTargetSelector.requiresFailover(exchange);
		assertTrue(requiresFailover);
	}

	@Test
	public void shouldSendMessageToFirstAddress() throws EndpointException {

		List<String> addresses = new ArrayList<String>();
		addresses.add("http://address1");
		addresses.add("http://address2");
		addresses.add("http://address3");

		circuitBreakerTargetSelector.setAddressList(addresses);

		Message message = sendRequestToFirstAvailableAddress("/endpoint");

		assertSendingMessageTo(message, "http://address1/endpoint");

		message = sendRequestToFirstAvailableAddress("/endpoint2");

		assertSendingMessageTo(message, "http://address1/endpoint2");

	}

	@Test
	public void shouldSendMessageToFirstAddressAfter1Failure() throws Exception {
		List<String> addresses = new ArrayList<String>();
		addresses.add("http://address1");
		addresses.add("http://address2");
		addresses.add("http://address3");

		circuitBreakerTargetSelector.setFailureThreshold(3);
		circuitBreakerTargetSelector.setAddressList(addresses);

		Message message = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");

		assertSendingMessageTo(message, "http://address1/resourceABC");

	}

	@Test
	public void shouldKeepSendingToFirstAddressAfter2ndFailure() throws Exception {

		circuitBreakerTargetSelector.setFailureThreshold(3);
		circuitBreakerTargetSelector.setAddressList(asList("http://address1", "http://address2"));

		Message message = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message, "http://address1/resourceABC");
		verifyRertyToNode("http://address1");
		Message message2 = sendRequestToFirstAvailableAddressAndForceFailure("/resourceBCD");
		assertSendingMessageTo(message2, "http://address1/resourceBCD");
		verifyRertyToNode("http://address1");

	}

	@Test
	public void shouldFailoverTo2ndAddressAfterExceedingFailureThreshold() throws Exception {

		circuitBreakerTargetSelector.setFailureThreshold(3);
		circuitBreakerTargetSelector.setResetTimeout(100);
		circuitBreakerTargetSelector.setAddressList(asList("http://address1", "http://address2", "http://address3"));

		Message message = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message, "http://address1/resourceABC");

		Message message2 = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message2, "http://address1/resourceABC");

		Message message3 = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message3, "http://address1/resourceABC");
		verifyRertyToNode("http://address2");
		// 3 failures on address 1, redirecting to address2

		Message message4 = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message4, "http://address2/resourceABC");

		Message message5 = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message5, "http://address2/resourceABC");

		Message message6 = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		// 3 failures on address 2, redirecting to address3
		assertSendingMessageTo(message6, "http://address2/resourceABC");
		verifyRertyToNode("http://address3");

	}

	@Test
	public void shouldDefaultTo2ndAddressAfterFailover() throws Exception {

		List<String> addresses = new ArrayList<String>();
		addresses.add("http://addressA");
		addresses.add("http://addressB");
		addresses.add("http://addressC");

		circuitBreakerTargetSelector.setFailureThreshold(0);
		circuitBreakerTargetSelector.setAddressList(addresses);

		Message message = sendRequestToFirstAvailableAddressAndForceFailure("/resourceABC");
		assertSendingMessageTo(message, "http://addressA/resourceABC");
		verifyRertyToNode("http://addressB");

		Message message2 = sendRequestToFirstAvailableAddress("/endpoint");
		assertSendingMessageTo(message2, "http://addressB/endpoint");
		Message message3 = sendRequestToFirstAvailableAddress("/endpoint2");
		assertSendingMessageTo(message3, "http://addressB/endpoint2");

	}

	@Test
	public void shouldFailbackAfterResetTimeoutElapsed() throws InterruptedException {
		List<String> addresses = new ArrayList<String>();
		addresses.add("http://addressA");
		addresses.add("http://addressB");
		addresses.add("http://addressC");
		circuitBreakerTargetSelector.setResetTimeout(50l);
		circuitBreakerTargetSelector.setFailureThreshold(0);
		circuitBreakerTargetSelector.setAddressList(asList("http://addressA", "http://addressB", "http://addressC"));

		sendRequestToFirstAvailableAddressAndForceFailure("/endpointCBA");

		Message message = sendRequestToFirstAvailableAddress("/endpointABC");
		assertSendingMessageTo(message, "http://addressB/endpointABC");
		Message message2 = sendRequestToFirstAvailableAddress("/endpointDEF");
		assertSendingMessageTo(message2, "http://addressB/endpointDEF");
		Thread.sleep(150l);

		// failback
		Message message3 = sendRequestToFirstAvailableAddress("/endpointAAA");
		assertSendingMessageTo(message3, "http://addressA/endpointAAA");

	}
	
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	
	@Test
	public void shouldThrowExceptionIfNoMoreNodesToFailover() {
		
		circuitBreakerTargetSelector.setFailureThreshold(0);
		circuitBreakerTargetSelector.setResetTimeout(200);
		
		circuitBreakerTargetSelector.setAddressList(asList("http://addressA", "http://addressB"));
		
		Message message = sendRequestToFirstAvailableAddressAndForceFailure("/endpointAAA");
		assertSendingMessageTo(message, "http://addressA/endpointAAA");
		
		Message message2 = sendRequestToFirstAvailableAddressAndForceFailure("/endpointAAA");
		assertSendingMessageTo(message2, "http://addressB/endpointAAA");
		
		thrown.expect(Fault.class);
		thrown.expectCause(isA(IOException.class));
		sendRequestToFirstAvailableAddressAndForceFailure("/endpointAAA");
	}

	private Message sendRequestToFirstAvailableAddressAndForceFailure(String requestPath) {
		Message message = messageTo("http://originalAddress", requestPath);
		message.put(Exception.class, new IOException());
		
		circuitBreakerTargetSelector.selectConduit(message);
		circuitBreakerTargetSelector.complete(message.getExchange());
		return message;
	}

	private Message sendRequestToFirstAvailableAddress(String requestPath) {
		Message message = messageTo("http://originalAddress", requestPath);
		circuitBreakerTargetSelector.selectConduit(message);
		circuitBreakerTargetSelector.complete(message.getExchange());
		return message;
	}

	private Message messageTo(String requestBaseURL, String requestPath) {
		Message message = new SoapMessage(Soap11.getInstance());
		String requestURL = requestBaseURL + requestPath;
		message.put(Message.ENDPOINT_ADDRESS, requestURL);
		message.put(Message.BASE_PATH, requestBaseURL);
		message.put(Message.REQUEST_URI, requestURL);
		
		HashMap<String, Object> ctx = new HashMap<String, Object>();
		ctx.put(Client.REQUEST_CONTEXT, new HashMap<String, Object>());
		message.put(Message.INVOCATION_CONTEXT, ctx);

		CXFBusImpl bus = new CXFBusImpl();
		BindingFactoryManagerImpl bindingFactoryManager = new BindingFactoryManagerImpl();
		bindingFactoryManager.registerBindingFactory("abc", new JAXRSBindingFactory());
		bus.setExtension(bindingFactoryManager, BindingFactoryManager.class);
		Map<String, ConduitInitiator> conduitInitiators = new HashMap<String, ConduitInitiator>();
		ConduitInitiator ci = new HTTPTransportFactory(bus);
		conduitInitiators.put(ENDPOINT_TRANSPORT_ID, ci);
		ConduitInitiatorManagerImpl cim = new ConduitInitiatorManagerImpl(conduitInitiators);
		bus.setExtension(cim, ConduitInitiatorManager.class);

		Exchange exchange = exchange(message);
		exchange.put(Bus.class, bus);
		EndpointInfo ei = new EndpointInfo();
		ei.setAddress("http://abc123");
		BindingInfo b = new BindingInfo(null, "abc");
		ei.setBinding(b);
		Endpoint endpointMock = mock(Endpoint.class);
		when(endpointMock.getEndpointInfo()).thenReturn(ei);
		exchange.put(Endpoint.class, endpointMock);
		
		message.setExchange(exchange);
		message.setContent(List.class, new ArrayList<String>());
		circuitBreakerTargetSelector.prepare(message);
		
		InvocationKey key = new InvocationKey(exchange);
		InvocationContext invocation = circuitBreakerTargetSelector.getInvocation(key);

		invocation.getContext().put(Message.ENDPOINT_ADDRESS, requestPath);
		invocation.getContext().put("org.apache.cxf.request.uri", requestPath);
		return message;
	}


	private Exchange exchange(Message message) {
		Exchange exchange = new ExchangeImpl();
		exchange.setOutMessage(message);

		client = mock(Retryable.class);
		exchange.put(Retryable.class, client);
		return exchange;
	}

	private void assertSendingMessageTo(Message message, String address) {
		assertThat((String) message.get(Message.ENDPOINT_ADDRESS), is(address));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void verifyRertyToNode(String address) throws Exception {

		ArgumentCaptor<Map> requestContextCaptor = ArgumentCaptor.forClass(Map.class);
		verify(client).invoke(any(BindingOperationInfo.class), any(Object[].class), requestContextCaptor.capture(),
				any(Exchange.class));
		Map requestContect = (Map) requestContextCaptor.getValue().get(Client.REQUEST_CONTEXT);
		assertThat((String) requestContect.get(Message.ENDPOINT_ADDRESS), is(address));

	}

}
