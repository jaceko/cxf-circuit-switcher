package com.github.jaceko.circuitswitcher.it.jaxrs.client.dto;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "books")
@XmlAccessorType(XmlAccessType.FIELD)
public class Books {
	
	@XmlElement(name = "book")
	private List<Book> books;

	public List<Book> getBooks() {
		return books;
	}

}
