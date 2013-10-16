package com.github.jaceko.circuitbreaker.it.util.mock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class MockServer {
	Server jettyServer;

	public MockServer(int port) {
		jettyServer = new Server(port);

		WebAppContext soapRestMockWebApp = new WebAppContext();
		soapRestMockWebApp.setExtraClasspath("src/test/resources/mock-config");
		soapRestMockWebApp.setWar("../external-wars/soaprest-mock-service.war");
		soapRestMockWebApp.setContextPath("/mock");
		jettyServer.setHandler(soapRestMockWebApp);
	}
	
	public void start() throws Exception {
		jettyServer.start();
	}
	
	public void stop() throws Exception {
		jettyServer.stop();
		jettyServer.join();
	}
	
	public static Process startNewProcess(String port) throws IOException {
		List<String> argumentsList = new ArrayList<String>();
		argumentsList.add("java");
		argumentsList.add("-classpath");
		argumentsList
				.add(".;../jetty/jetty-all.jar;../jetty/javax.servlet-api.jar;../../src/test/resources/mock-config/");

		argumentsList.add("com.github.jaceko.circuitbreaker.it.util.mock.MockServer");
		argumentsList.add(port);

		ProcessBuilder processBuilder = new ProcessBuilder(argumentsList.toArray(new String[argumentsList.size()]));
		processBuilder.redirectErrorStream(true);
		processBuilder.directory(new File("target/test-classes"));
		processBuilder.redirectErrorStream(true); // redirect error stream to
													// output stream
		processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

		Process process = processBuilder.start();
		return process;
	}

	
	public static void main(String[] args) throws Exception {
		MockServer mockServer = new MockServer(Integer.valueOf(args[0]));
		mockServer.start();

	}

}
