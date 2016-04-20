package com.github.jaceko.circuitswitcher.it.util.mock;

import com.github.jaceko.circuitswitcher.it.util.xml.XmlParser;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;

public class WebserviceMockControler {
	private static final Logger LOG = LoggerFactory.getLogger(WebserviceMockControler.class);

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
			try {
				mockServiceProxy.init(serviceType, mockedOperation.getServiceName(), mockedOperation.getOperationId());
			} catch (Throwable e) {
				LOG.warn("mock init failed: " + e);
			}
		}

		public Document recordedRequests() throws SAXException, IOException {
			String recordedRequests = mockServiceProxy.getRecordedRequests(serviceType, mockedOperation.getServiceName(),
					mockedOperation.getOperationId());
			return XmlParser.parse(recordedRequests);
		}
	}

}
