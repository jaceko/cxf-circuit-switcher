package com.github.jaceko.circuitbreaker.it.jaxws;

import static com.github.jaceko.circuitbreaker.it.util.mock.SayHiResponseBuilder.sayHiResponse;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.IOException;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;

import org.apache.cxf.clustering.CircuitBreakerClusteringFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.github.jaceko.circuitbreaker.it.jaxws.client.hello_world_soap_http.Greeter;
import com.github.jaceko.circuitbreaker.it.util.mock.WebserviceMockControler;
import com.github.jaceko.circuitbreaker.it.util.mock.WebserviceOperation;

public class JaxwsFailoverIntegrationTest {

	private static final String NODE1_ENDPOINT_ADDRESS = "http://localhost:9090/mock/services/SOAP/hello-world/endpoint";
	private static final String NODE2_ENDPOINT_ADDRESS = "http://localhost:9191/mock/services/SOAP/hello-world/endpoint";

	private WebserviceMockControler node1Controller = new WebserviceMockControler("http://localhost:9090");
	private WebserviceMockControler node2Controller = new WebserviceMockControler("http://localhost:9191");

	WebserviceOperation sayHiOperation = new WebserviceOperation("hello-world", "sayHi");

	private JaxWsProxyFactoryBean bean;

	public JaxwsFailoverIntegrationTest() {
		bean = new JaxWsProxyFactoryBean();
		bean.setServiceClass(Greeter.class);

	}

	private CircuitBreakerClusteringFeature createCircuitBreakerFeature() {
		CircuitBreakerClusteringFeature cbff = new CircuitBreakerClusteringFeature();
		cbff.setAddressList(asList(NODE1_ENDPOINT_ADDRESS, NODE2_ENDPOINT_ADDRESS));
		return cbff;
	}

	@Before
	public void init() {
		node1Controller.soapOperation(sayHiOperation).init();
		node2Controller.soapOperation(sayHiOperation).init();
	}

	@Test
	public void shouldReturnHiResponseRerunedByFirstNode() {

		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		Greeter greeterClient = createServiceClient(cbcFeature);

		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("How you doin"));

		assertThat(greeterClient.sayHi(), is("How you doin"));
	}

	@Test
	public void shouldFailoverTo2ndNodeIfFirstNodeNotResponsing() {

		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setAddressList(asList("http://not-existing.com", NODE1_ENDPOINT_ADDRESS));
		cbcFeature.setResetTimeout(100000);

		Greeter greeterClient = createServiceClient(cbcFeature);

		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Whats up?"));

		assertThat(greeterClient.sayHi(), is("Whats up?"));
	}

	@Test
	public void shouldFailoverTo2ndNodeAfterTimeoutOn1stNode() {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);

		Greeter greeterClient = createServiceClientWithTimeout(cbcFeature, 800);

		// causing timeout on the client
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heeey"));

		assertThat(greeterClient.sayHi(), is("Heeey"));

	}

	@Test
	public void shouldFailoverToSecondNodeAfterExceedingFailureThreshold() throws SAXException, IOException {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		// setting failure threshold to 2 so it will retry first request to the
		// 1st node
		cbcFeature.setFailureThreshold(2);
		cbcFeature.setResetTimeout(100000);

		Greeter greeterClient = createServiceClientWithTimeout(cbcFeature, 800);

		// setting up timeouts of 2 first requests
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Ho"));

		assertThat(greeterClient.sayHi(), is("Ho"));

		// this request fails over to 2nd node after exceeding threshold of 2
		// attempts on on 1st node
		Document recordedRequests1Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("2")));

		// 2nd node
		Document recordedRequests2Node = node2Controller.soapOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));
	}

	@Test
	public void shouldContinueUsingNode2AfterFailover() throws SAXException, IOException {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);

		Greeter greeterClient = createServiceClientWithTimeout(cbcFeature, 800);

		// causing timeout on node 1
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		// setting up 2 consecutive responses for node2
		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Ha"));
		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Ha ha"));

		// this request fails over to node 2 after one delivery attempt to node
		// 1
		assertThat(greeterClient.sayHi(), is("Ha"));

		Document recordedRequests1Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

		Document recordedRequests2Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));

		// 2nd request goes directly to node 2
		assertThat(greeterClient.sayHi(), is("Ha ha"));

		recordedRequests1Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

		recordedRequests2Node = node2Controller.soapOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("2")));

	}

	@Test
	public void shouldFailbackToFirstNodeAfterResetTimeout() throws InterruptedException {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		long resetTimeout = 1300;
		cbcFeature.setResetTimeout(resetTimeout);
		Greeter greeterClient = createServiceClientWithTimeout(cbcFeature, 800);

		// causing timeout on node1 (1st request)
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heyah node1 speaking!"));

		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heyah node2 speaking!"));
		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heyah ho node2 speaking!"));

		// this request fails over to node 2 
		assertThat(greeterClient.sayHi(), is("Heyah node2 speaking!"));
		// this request goes directly to node2
		assertThat(greeterClient.sayHi(), is("Heyah ho node2 speaking!"));

		// waiting till failover reset timeout elapses
		Thread.sleep(resetTimeout + 100);

		//failback, node1 is healthy again
		assertThat(greeterClient.sayHi(), is("Heyah node1 speaking!"));

	}

	@Test(expected = WebServiceException.class)
	public void shouldThrowExceptionAfterAllNodesFail() throws SAXException, IOException {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);
		Greeter greeterClient = createServiceClientWithTimeout(cbcFeature, 800);

		// causing timeout on node1 (1st request)
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		// causing timeout on node2 (1st request)
		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		// the request is sent to each node and returns an error because
		// no more nodes are available
		try {
			greeterClient.sayHi();
		} finally {
			Document recordedRequests1Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

			Document recordedRequests2Node = node2Controller.soapOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));

		}
	}

	@Test(expected = WebServiceException.class)
	public void shouldFailFastAfterAllNodesFail() throws SAXException, IOException {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);

		Greeter greeterClient = createServiceClientWithTimeout(cbcFeature, 800);

		// causing timeout on node1 (1st request)
		node1Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		// causing timeout on node2 (1st request)
		node2Controller.soapOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		// the request is sent to each node and returns an error because
		// no more nodes are available
		try {
			greeterClient.sayHi();
		} catch (WebServiceException e) {

		} finally {
			Document recordedRequests1Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

			Document recordedRequests2Node = node2Controller.soapOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));

		}
		
		//reinitalising mocks to clear recorded requests
		node1Controller.soapOperation(sayHiOperation).init();
		node2Controller.soapOperation(sayHiOperation).init();

		// next attempt fails fast, not sending any requests
		try {
			greeterClient.sayHi();
		} finally {
			Document recordedRequests1Node = node1Controller.soapOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("0")));

			Document recordedRequests2Node = node2Controller.soapOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("0")));

		}

	}

	private Greeter createServiceClient(CircuitBreakerClusteringFeature cbcFeature) {
		bean.setFeatures(asList(cbcFeature));
		Greeter serviceClient = bean.create(Greeter.class);
		return serviceClient;
	}

	private Greeter createServiceClientWithTimeout(CircuitBreakerClusteringFeature cbcFeature, int timeout) {
		Greeter serviceClient = createServiceClient(cbcFeature);
		if (serviceClient instanceof BindingProvider) {
			BindingProvider bindingProvider = (BindingProvider) serviceClient;
			bindingProvider.getRequestContext().put("javax.xml.ws.client.receiveTimeout", String.valueOf(timeout));
			bindingProvider.getRequestContext().put("javax.xml.ws.client.connectionTimeout", String.valueOf(timeout));
		} else {
			throw new IllegalStateException("Unable to access CXF request context while initialising client");
		}

		return serviceClient;
	}

}
