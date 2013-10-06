package com.github.jaceko.circuitbreaker.it.jaxrs.client;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Authors;
import com.github.jaceko.circuitbreaker.it.jaxrs.client.dto.Books;

@Path("/services/REST")
public interface Library {
	@GET
	@Path("/books/endpoint")
	Books getAllBooks();
	
	@GET
	@Path("/authors/endpoint")
	Authors getAllAuthors();

}
