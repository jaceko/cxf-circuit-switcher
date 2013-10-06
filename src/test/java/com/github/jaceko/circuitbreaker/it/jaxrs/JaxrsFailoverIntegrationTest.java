package com.github.jaceko.circuitbreaker.it.jaxrs;

import static com.github.jaceko.circuitbreaker.it.util.mock.AuthorsResponseBuilder.anAuthorsRsponse;
import static com.github.jaceko.circuitbreaker.it.util.mock.BooksResponseBuilder.aBooksRsponse;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.apache.cxf.clustering.CircuitBreakerClusteringFeature;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.Before;
import org.junit.Test;

import com.github.jaceko.circuitbreaker.it.jaxrs.client.Library;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Authors;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Books;
import com.github.jaceko.circuitbreaker.it.util.mock.WebserviceMockControler;
import com.github.jaceko.circuitbreaker.it.util.mock.WebserviceOperation;

public class JaxrsFailoverIntegrationTest {

	private static final String NODE1_ADDRESS = "http://localhost:9090/mock";
	private static final String NODE2_ADDRESS = "http://localhost:9090/mock2";

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
		node1Controller.soapOperation(booksGETOperation).init();
		node2Controller.soapOperation(booksGETOperation).init();
		node1Controller.soapOperation(authorsGETOperation).init();
		node2Controller.soapOperation(authorsGETOperation).init();

	}

	@Test
	public void shouldReturnResponsesReturnedByFirstNode() {
		node1Controller.soapOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Fight Club"));
		node1Controller.soapOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Chuck Palahniuk"));
		
		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();

		Library library = createJaxrsClient(cbcFeature);
		
		assertThat(library.getAllAuthors().getAuthors().get(0).getName(), is("Chuck Palahniuk"));
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Fight Club"));
	}
	
	@Test
	public void shouldFailoverTo2ndNodeIfFirstNodeNotResponsing() {
		node1Controller.soapOperation(booksGETOperation).setUp(aBooksRsponse().withBookTitle("Godfather"));
		node1Controller.soapOperation(authorsGETOperation).setUp(anAuthorsRsponse().withAuthorName("Mario Puzo"));

		CircuitBreakerClusteringFeature cbcFeature = createCircuitBreakerFeature();
		cbcFeature.setAddressList(asList("http://nonexising", NODE1_ADDRESS));
		cbcFeature.setResetTimeout(100000);

		Library library = createJaxrsClient(cbcFeature);
		
		assertThat(library.getAllAuthors().getAuthors().get(0).getName(), is("Mario Puzo"));
		assertThat(library.getAllBooks().getBooks().get(0).getTitle(), is("Godfather"));
		
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

}