package com.github.jaceko.circuitswitcher.it.util.mock;

import java.io.IOException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.github.jaceko.circuitswitcher.it.util.xml.XmlParser;

public class Response {

	private final int statusCode;
	private final String body;
	private final String mediaType;

	public Response(int statusCode, String body, String mediaType) {
		this.statusCode = statusCode;
		this.body = body;
		this.mediaType = mediaType;
	}

	public int code() {
		return statusCode;
	}

	public String body() {
		return body;
	}

	public String mediaType() {
		return mediaType;
	}

	public Document xmlDocument() throws SAXException, IOException {
		return XmlParser.parse(body);
	}

}
