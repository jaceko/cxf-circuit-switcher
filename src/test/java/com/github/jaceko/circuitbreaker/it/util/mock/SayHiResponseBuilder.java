package com.github.jaceko.circuitbreaker.it.util.mock;

import java.text.MessageFormat;

public class SayHiResponseBuilder implements MockResponseBuilder {
	
	private static String RESPONSE_TEMPLATE = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:typ=\"http://apache.org/hello_world_soap_http/types\">\r\n" + 
			"   <soapenv:Body>\r\n" + 
			"      <typ:sayHiResponse>\r\n" + 
			"         <typ:responseText>{0}</typ:responseText>\r\n" + 
			"      </typ:sayHiResponse>\r\n" + 
			"   </soapenv:Body>\r\n" + 
			"</soapenv:Envelope>";
	
	private String responseText;

	private int responseDelay;
	
	public SayHiResponseBuilder withResponseText(String responseText) {
		this.responseText = responseText;
		return this;
	}
	
	public SayHiResponseBuilder withResponseDelaySec(int responseDelay) {
		this.responseDelay = responseDelay;
		return this;
	}
	
	public static SayHiResponseBuilder sayHiResponse() {
		return new SayHiResponseBuilder();
		
	}

	@Override
	public int responseDelay() {
		return responseDelay;
	}

	@Override
	public String build() {
		return MessageFormat.format(RESPONSE_TEMPLATE, responseText);
	}

}
