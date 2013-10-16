package com.github.jaceko.circuitbreaker.it.jaxrs;

import static com.github.jaceko.circuitbreaker.it.util.mock.AuthorsResponseBuilder.anAuthorsRsponse;
import static com.github.jaceko.circuitbreaker.it.util.mock.BooksResponseBuilder.aBooksRsponse;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Proxy;

import org.apache.cxf.clustering.CircuitBreakerClusteringFeature;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.ClientProxyImpl;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.junit.Before;
import org.junit.Test;

import com.github.jaceko.circuitbreaker.it.jaxrs.client.Library;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Authors;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Books;
import com.github.jaceko.circuitbreaker.it.util.mock.WebserviceMockControler;
import com.github.jaceko.circuitbreaker.it.util.mock.WebserviceOperation;

public class JaxrsFailoverIntegrationTest extends AbstractIntegrationTest {

	private static final String NODE1_ADDRESS = "http://localhost:9090";
	private static final String NODE2_ADDRESS = "http://localhost:9191";

	private WebserviceMockControler node1Controller = new WebserviceMockControler(NODE1_ADDRESS);
	private WebserviceMockControler node2Controller = new WebserviceMockControler(NODE2_ADDRESS);

	WebserviceOperation booksGETOperation = new WebserviceOperation("books", "GET");
	WebserviceOperation authorsGETOperation = new WebserviceOperation("authors", "GET");

	JAXRSClientFactoryBean bean;

	public JaxrsFailoverIntegrationTest() {
		bean = new JAXRSClientFactoryBean();
		bean.setResourceClass(Library.class);
		bean.setAddress("http://dummy:8380");
	}

	private CircuitBreakerClusteringFeature createCircuitBreakerFeature() {
		CircuitBreakerClusteringFeature cbff = new CircuitBreakerClusteringFeature();
		cbff.setAddressList(asList(NODE1_ADDRESS, NODE2_ADDRESS));
		return cbff;
	}

	@Before
	public void init() {
		node1Controller.webserviceOperation(booksGETOperation).init();
		node2Controller.webserviceOperation(booksGETOperation).init();
		node1Controller.webserviceOperation(authorsGETOperation).init();
		node2Controller.webserviceOperation(authorsGETOperation).init();

	}

	@Test
	public void shouldReturnResponsesReturnedByFirstNode() {
		node1Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Fight Club"));
		node1Controller.webserviceOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Chuck Palahniuk"));

		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();

		Library library = createJaxrsClient(cbcFeature);

		assertThat(library.getAllAuthors().getAuthors().get(0).getName(), is("Chuck Palahniuk"));
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Fight Club"));
	}

	@Test
	public void shouldFailoverTo2ndNodeIfFirstNodeNotResponsing() {
		node1Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Godfather"));
		node1Controller.webserviceOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Mario Puzo"));

		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setAddressList(asList("http://nonexising", NODE1_ADDRESS));
		cbcFeature.setResetTimeout(100000);

		Library library = createJaxrsClient(cbcFeature);

		assertThat(library.getAllAuthors().getAuthors().get(0).getName(), is("Mario Puzo"));
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Godfather"));

	}

	@Test
	public void shouldFailbackToFirstNodeAfterResetTimeout() throws InterruptedException {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setFailureThreshold(1);
		long resetTimeout = 2000;
		cbcFeature.setResetTimeout(resetTimeout);
		Library library = createJaxrsClientWithTimeout(cbcFeature, 800);

		// causing timeout on node1 (1st request)
		node1Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withResponseDelaySec(1));

		node1Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Gomorra node1"));
		node1Controller.webserviceOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Roberto Saviano node1"));

		node2Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Gomorra node2"));
		node2Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Gomorra2 node2"));
		node2Controller.webserviceOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Gomorra3 node2"));
		node2Controller.webserviceOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Roberto Saviano node2"));
		node2Controller.webserviceOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Roberto Saviano2 node2"));

		// this request fails over to node 2
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Gomorra node2"));
		assertThat(library.getAllAuthors().getAuthors().get(0).getName(), is("Roberto Saviano node2"));
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Gomorra2 node2"));

		// waiting till failover reset timeout elapses
		Thread.sleep(resetTimeout + 100);

		// failback to node1
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Gomorra node1"));
		assertThat(library.getAllAuthors().getAuthors().get(0).getName(), is("Roberto Saviano node1"));
	}

	@Test
	public void testFaiover() {
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setResetTimeout(100000);

		Library library = createJaxrsClient(cbcFeature);

		Authors allAuthors = library.getAllAuthors();
		assertThat(allAuthors.getAuthors().size(), is(1));
		assertThat(allAuthors.getAuthors().get(0).getName(), is("Karl May"));

		library.getAllAuthors();

		Books allBooks = library.getAllBooks();
		assertThat(allBooks.getBooks().size(), is(2));
		assertThat(allBooks.getBooks().get(1).getTitle(), is("Winnetou"));
	}

	private Library createJaxrsClient(CircuitBreakerClusteringFeature cbcFeature) {
		bean.setFeatures(asList(cbcFeature));
		Library library = bean.create(Library.class);
		return library;
	}

	private Library createJaxrsClientWithTimeout(CircuitBreakerClusteringFeature cbcFeature, int timeout) {
		Library library = createJaxrsClient(cbcFeature);
		ClientProxyImpl clientProxyImpl = (ClientProxyImpl) Proxy.getInvocationHandler(library);
		ClientConfiguration config = WebClient.getConfig(clientProxyImpl);
		HTTPConduit http = (HTTPConduit) config.getConduit();
		HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
		httpClientPolicy.setConnectionTimeout(timeout);
		httpClientPolicy.setReceiveTimeout(timeout);
		http.setClient(httpClientPolicy);

		return library;
	}

}