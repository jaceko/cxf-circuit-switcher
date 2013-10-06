package com.github.jaceko.circuitbreaker.it.jaxrs.client.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "book")
@XmlAccessorType(XmlAccessType.FIELD)
public class Book {
	
	private String title;

	public String getTitle() {
		return title;
	}

}
