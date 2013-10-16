package com.github.jaceko.circuitbreaker.it;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jaceko.circuitbreaker.it.util.mock.MockServer;

public abstract class AbstractIntegrationTest {
	private static final Logger LOG = LoggerFactory.getLogger(AbstractIntegrationTest.class);

	private static Process mockServerProcess1;
	private static Process mockServerProcess2;

	@BeforeClass
	public static void beforeClass() throws Exception {
		LOG.info("starting mock servers");
		mockServerProcess1 = MockServer.startNewProcess("9090");
		mockServerProcess2 = MockServer.startNewProcess("9191");
		
	}

	@AfterClass
	public static void afterClass() throws Throwable {
		LOG.info("stopping mock servers");
		mockServerProcess1.destroy();
		mockServerProcess2.destroy();
	}

	public AbstractIntegrationTest() {
		super();
	}

}