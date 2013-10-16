package com.github.jaceko.circuitbreaker.it.util.mock;

import java.io.IOException;

import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.github.jaceko.circuitbreaker.it.util.xml.XmlParser;

public class WebserviceMockControler {

	private MockService mockServiceProxy;

	public WebserviceMockControler(String mockServiceRootAddress) {
		JAXRSClientFactoryBean bean = new JAXRSClientFactoryBean();
		bean.setAddress(mockServiceRootAddress);
		bean.setResourceClass(MockService.class);
		mockServiceProxy = bean.create(MockService.class);

	}

	public MockServiceOperation webserviceOperation(WebserviceOperation mockedOperation) {
		return new MockServiceOperation("SOAP", mockServiceProxy, mockedOperation);
	}

	public static class MockServiceOperation {
		MockService mockServiceProxy;
		String serviceType;
		WebserviceOperation mockedOperation;

		public MockServiceOperation(String serviceType, MockService mockServiceProxy, WebserviceOperation mockedOperation) {
			super();
			this.serviceType = serviceType;
			this.mockServiceProxy = mockServiceProxy;
			this.mockedOperation = mockedOperation;
		}

		public void setUp(MockResponseBuilder mockResponse) {
			mockServiceProxy.setupResponse(serviceType, mockedOperation.getServiceName(), mockedOperation.getOperationId(),
					mockResponse.responseDelay(), mockResponse.build());
		}

		public void init() {
			mockServiceProxy.init(serviceType, mockedOperation.getServiceName(), mockedOperation.getOperationId());
		}

		public Document recordedRequests() throws SAXException, IOException {
			String recordedRequests = mockServiceProxy.getRecordedRequests(serviceType, mockedOperation.getServiceName(),
					mockedOperation.getOperationId());
			return XmlParser.parse(recordedRequests);
		}
	}

}
