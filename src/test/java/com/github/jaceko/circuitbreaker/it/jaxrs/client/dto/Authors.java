package com.github.jaceko.circuitbreaker.it.jaxrs.client.dto;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "authors")
@XmlAccessorType(XmlAccessType.FIELD)
public class Authors {

	@XmlElement(name = "author")
	private List<Author> authors;

	public List<Author> getAuthors() {
		return authors;
	}


}
