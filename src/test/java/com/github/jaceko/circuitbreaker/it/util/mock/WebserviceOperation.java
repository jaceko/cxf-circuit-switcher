package com.github.jaceko.circuitbreaker.it.util.mock;

public class WebserviceOperation {
	private String serviceName;
	private String operationId;
	public WebserviceOperation(String serviceName, String operationId) {
		super();
		this.serviceName = serviceName;
		this.operationId = operationId;
	}
	public String getServiceName() {
		return serviceName;
	}
	public String getOperationId() {
		return operationId;
	}
	

}
