package com.github.jaceko.circuitbreaker.it.util.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public final class XmlParser {
	private static final DocumentBuilder BUILDER;

	private XmlParser() {}

	static {
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilderFactory.setNamespaceAware(false);
			BUILDER = documentBuilderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("xml parser intialization problem", e);
		}
	}

	public static Document parse(String xml) throws SAXException, IOException {
		return BUILDER.parse(new ByteArrayInputStream(xml.getBytes()));
	}

}
