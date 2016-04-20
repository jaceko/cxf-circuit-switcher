package com.github.jaceko.circuitswitcher.it.jaxws;

import com.github.jaceko.circuitswitcher.it.AbstractIntegrationTest;
import com.github.jaceko.circuitswitcher.it.jaxws.client.hello_world_soap_http.Greeter;
import com.github.jaceko.circuitswitcher.it.util.mock.WebserviceMockControler;
import com.github.jaceko.circuitswitcher.it.util.mock.WebserviceOperation;
import org.apache.cxf.clustering.CircuitSwitcherClusteringFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.ws.WebServiceException;
import java.io.IOException;

import static com.github.jaceko.circuitswitcher.it.util.mock.SayHiResponseBuilder.sayHiResponse;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class JaxwsFailoverIntegrationTest extends AbstractIntegrationTest {

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

	private CircuitSwitcherClusteringFeature createCircuitBreakerFeature() {
		CircuitSwitcherClusteringFeature cbff = new CircuitSwitcherClusteringFeature();
		cbff.setAddressList(asList(NODE1_ENDPOINT_ADDRESS, NODE2_ENDPOINT_ADDRESS));
		return cbff;
	}

	@Before
	public void init() {
		node1Controller.webserviceOperation(sayHiOperation).init();
		node2Controller.webserviceOperation(sayHiOperation).init();
	}

	@Test
	public void shouldReturnHiResponseRerunedByFirstNode() {

		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		Greeter greeterClient = createServiceClient(cbcFeature);

		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("How you doin"));

		assertThat(greeterClient.sayHi(), is("How you doin"));
	}

	@Test
	public void shouldFailoverTo2ndNodeIfFirstNodeNotResponsing() {

		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setAddressList(asList("http://not-existing.com", NODE1_ENDPOINT_ADDRESS));
		cbcFeature.setResetTimeout(100000);

		Greeter greeterClient = createServiceClient(cbcFeature);

		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Whats up?"));

		assertThat(greeterClient.sayHi(), is("Whats up?"));
	}

	@Test
	public void shouldFailoverTo2ndNodeAfterTimeoutOn1stNode() {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000l);
		cbcFeature.setReceiveTimeout(800l);

		Greeter greeterClient = createServiceClient(cbcFeature);

		// causing timeout on the client
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heeey node 2"));

		assertThat(greeterClient.sayHi(), is("Heeey node 2"));

	}

	@Test
	public void shouldContinueUsingFirstNodeIfFailureThresholdNotExceeded() {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(3);
		cbcFeature.setResetTimeout(100000);
		cbcFeature.setReceiveTimeout(800l);

		Greeter greeterClient = createServiceClient(cbcFeature);

		// causing two timeouts on the client
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heeey node 1"));
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heeey2 node 1"));
		
		assertThat(greeterClient.sayHi(), is("Heeey node 1"));
		
		assertThat(greeterClient.sayHi(), is("Heeey2 node 1"));

	}

	
	@Test
	public void shouldFailoverToSecondNodeAfterExceedingFailureThreshold() throws SAXException, IOException {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		// setting failure threshold to 2 so it will retry first request to the
		// 1st node
		cbcFeature.setFailureThreshold(2);
		cbcFeature.setResetTimeout(100000);
		cbcFeature.setReceiveTimeout(800l);

		Greeter greeterClient = createServiceClient(cbcFeature);

		// setting up timeouts of 2 first requests
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Ho"));

		assertThat(greeterClient.sayHi(), is("Ho"));

		// this request fails over to 2nd node after exceeding threshold of 2
		// attempts on on 1st node
		Document recordedRequests1Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("2")));

		// 2nd node
		Document recordedRequests2Node = node2Controller.webserviceOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));
	}

	@Test
	public void shouldContinueUsingNode2AfterFailover() throws SAXException, IOException {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);
		cbcFeature.setReceiveTimeout(800l);

		Greeter greeterClient = createServiceClient(cbcFeature);

		// causing timeout on node 1
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		// setting up 2 consecutive responses for node2
		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Ha"));
		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Ha ha"));

		// this request fails over to node 2 after one delivery attempt to node
		// 1
		assertThat(greeterClient.sayHi(), is("Ha"));

		Document recordedRequests1Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

		Document recordedRequests2Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));

		// 2nd request goes directly to node 2
		assertThat(greeterClient.sayHi(), is("Ha ha"));

		recordedRequests1Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

		recordedRequests2Node = node2Controller.webserviceOperation(sayHiOperation).recordedRequests();
		assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("2")));

	}

	@Test
	public void shouldFailbackToFirstNodeAfterResetTimeout() throws InterruptedException {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		long resetTimeout = 1300;
		cbcFeature.setResetTimeout(resetTimeout);
		cbcFeature.setReceiveTimeout(800l);
		
		Greeter greeterClient = createServiceClient(cbcFeature);

		// causing timeout on node1 (1st request)
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heyah node1 speaking!"));

		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heyah node2 speaking!"));
		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseText("Heyah ho node2 speaking!"));

		// this request fails over to node 2
		assertThat(greeterClient.sayHi(), is("Heyah node2 speaking!"));
		// this request goes directly to node2
		assertThat(greeterClient.sayHi(), is("Heyah ho node2 speaking!"));

		// waiting till failover reset timeout elapses
		Thread.sleep(resetTimeout + 300);

		// failback, node1 is healthy again
		assertThat(greeterClient.sayHi(), is("Heyah node1 speaking!"));

	}

	@Test(expected = WebServiceException.class)
	public void shouldThrowExceptionAfterAllNodesFail() throws SAXException, IOException {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);
		cbcFeature.setReceiveTimeout(800l);
		
		Greeter greeterClient = createServiceClient(cbcFeature);

		// causing timeout on node1 (1st request)
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		// causing timeout on node2 (1st request)
		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		// the request is sent to each node and returns an error because
		// no more nodes are available
		try {
			greeterClient.sayHi();
		} finally {
			Document recordedRequests1Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

			Document recordedRequests2Node = node2Controller.webserviceOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));

		}
	}

	@Test(expected = WebServiceException.class)
	public void shouldFailFastAfterAllNodesFail() throws SAXException, IOException {
		CircuitSwitcherClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		cbcFeature.setResetTimeout(100000);
		cbcFeature.setReceiveTimeout(800l);

		Greeter greeterClient = createServiceClient(cbcFeature);

		// causing timeout on node1 (1st request)
		node1Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));
		// causing timeout on node2 (1st request)
		node2Controller.webserviceOperation(sayHiOperation).setUp(sayHiResponse().withResponseDelaySec(1));

		// the request is sent to each node and returns an error because
		// no more nodes are available
		try {
			greeterClient.sayHi();
		} catch (WebServiceException e) {

		} finally {
			Document recordedRequests1Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("1")));

			Document recordedRequests2Node = node2Controller.webserviceOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("1")));

		}

		// reinitalising mocks to clear recorded requests
		node1Controller.webserviceOperation(sayHiOperation).init();
		node2Controller.webserviceOperation(sayHiOperation).init();

		// next attempt fails fast, not sending any requests
		try {
			greeterClient.sayHi();
		} finally {
			Document recordedRequests1Node = node1Controller.webserviceOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests1Node, hasXPath("count(//sayHi)", equalTo("0")));

			Document recordedRequests2Node = node2Controller.webserviceOperation(sayHiOperation).recordedRequests();
			assertThat(recordedRequests2Node, hasXPath("count(//sayHi)", equalTo("0")));

		}

	}

	private Greeter createServiceClient(CircuitSwitcherClusteringFeature cbcFeature) {
		bean.setFeatures(asList(cbcFeature));
		Greeter serviceClient = bean.create(Greeter.class);
		return serviceClient;
	}

}
