package com.github.jaceko.circuitbreaker;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class CircuitTest {
	
	private Circuit circuit;
	
	@Test
	public void shouldBeAvailableByDefault() {
		circuit = new Circuit(0,0);
		assertThat(circuit.connectionAvailable(), is(true));
	}
	
	@Test
	public void shouldBeAvailableAfterFirstFailure() {
		int maxFailures = 3;
		circuit = new Circuit(maxFailures, 200);
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(true));
	}
	
	@Test
	public void shouldBeAvailableAfterTwoFailures() {
		int maxFailures = 3;
		circuit = new Circuit(maxFailures, 200);
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(true));
	}

	
	@Test
	public void shouldNotBeAvailableAfterExceedeingThreshold() {
		int maxFailures = 3;
		circuit = new Circuit(maxFailures, 200);
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
	}
	
	@Test
	public void shouldBeAvailableAfterResetTimeout() throws InterruptedException {
		long resetTimeout = 50;

		circuit = new Circuit(0, resetTimeout);
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
		Thread.sleep(100);
		assertThat(circuit.connectionAvailable(), is(true));
	}
	
	@Test
	public void shouldGoIntoFailedStateAfterGiongBackToNormal() throws InterruptedException {
		long resetTimeout = 50;

		circuit = new Circuit(0, resetTimeout);
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
		Thread.sleep(100);
		assertThat(circuit.connectionAvailable(), is(true));
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
		
	}


}
