package com.github.jaceko.circuitswitcher.it.util.mock;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/mock/services/{service_type}/{service_name}/operations/{operation_id}")
public interface MockService {

	@POST
	@Path("/init")
	void init(@PathParam("service_type") String serviceType, @PathParam("service_name") String serviceName,
			@PathParam("operation_id") String operationId);

	@POST
	@Path("/responses")
	void setupResponse(@PathParam("service_type") String serviceType, @PathParam("service_name") String serviceName,
			@PathParam("operation_id") String operationId, @QueryParam("delay") int delay, String response);

	@GET
	@Produces(MediaType.TEXT_XML)
	@Path("/recorded-requests")
	String getRecordedRequests(@PathParam("service_type") String serviceType, @PathParam("service_name") String serviceName,
			@PathParam("operation_id") String operationId);

}
