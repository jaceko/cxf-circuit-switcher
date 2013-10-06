package com.github.jaceko.circuitbreaker.it.jaxrs.client.dto;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "author")
@XmlAccessorType(XmlAccessType.FIELD)
public class Author {

	private String name;

	public String getName() {
		return name;
	}
	
}
