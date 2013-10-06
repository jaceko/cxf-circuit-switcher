package com.github.jaceko.circuitbreaker.it.jaxrs;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.clustering.CircuitBreakerClusteringFeature;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.Test;

import com.github.jaceko.circuitbreaker.it.jaxrs.client.Library;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Authors;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Books;

public class RestClientFailoverIntegrationTest {

	@Test
	public void testFaiover() {
		CircuitBreakerClusteringFeature cbff= new CircuitBreakerClusteringFeature();
		cbff.setAddressList(asList("http://localhost:8080", "http://localhost:9090"));
		cbff.setResetTimeout(100000);
		

		JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
		List<Feature> features = new ArrayList<Feature>();
		features.add(cbff);
		bean.setFeatures(features);
		bean.setAddress("http://alamaKOOOOTA:8380");
		bean.setResourceClass(Library.class);
		Library library = bean.create(Library.class);
		
		Authors allAuthors = library.getAllAuthors();
		assertThat(allAuthors.getAuthors().size(), is(1));
		assertThat(allAuthors.getAuthors().get(0).getName(), is("Karl May"));
		
		library.getAllAuthors();

		Books allBooks = library.getAllBooks();
		assertThat(allBooks.getBooks().size(), is(2));
		assertThat(allBooks.getBooks().get(1).getTitle(), is("Winnetou"));
	}

}