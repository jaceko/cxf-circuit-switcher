package com.github.jaceko.circuitbreaker;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class CircuitTest {

	private Circuit circuit;

	@Test
	public void shouldBeAvailableByDefault() {
		circuit = new Circuit(null, 0, 0);
		assertThat(circuit.connectionAvailable(), is(true));
	}

	@Test
	public void shouldBeAvailableAfterFirstFailure() {
		int maxFailures = 3;
		circuit = new Circuit(null, maxFailures, 200);
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(true));
	}

	@Test
	public void shouldBeAvailableAfterTwoFailures() {
		int maxFailures = 3;
		circuit = new Circuit(null, maxFailures, 200);
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(true));
	}

	@Test
	public void shouldNotBeAvailableAfterExceedeingThreshold() {
		int maxFailures = 3;
		circuit = new Circuit(null, maxFailures, 200);
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
	}

	@Test
	public void shouldBeAvailableAfterResetTimeout() throws InterruptedException {
		long resetTimeout = 50;

		circuit = new Circuit(null, 0, resetTimeout);
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
		Thread.sleep(70);
		assertThat(circuit.connectionAvailable(), is(true));
	}

	@Test
	public void shouldGoIntoFailedStateAfterGiongBackToNormal() throws InterruptedException {
		long resetTimeout = 50;

		circuit = new Circuit(null, 0, resetTimeout);
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));
		Thread.sleep(70);
		assertThat(circuit.connectionAvailable(), is(true));
		circuit.handleFailedConnection();
		assertThat(circuit.connectionAvailable(), is(false));

	}

	@Test
	public void shouldReturnCirucitsInitialRepresenation() {
		String address = "http://someUrl";
		long resetTimeout = 50;
		int failureThreshold = 3;
		circuit = new Circuit(address, failureThreshold, resetTimeout);

		assertThat(
				circuit.toString(),
				is("Circuit [address=http://someUrl, state=CircuitClosed [failureCount=0], failureThreshold=3, resetTimeout=50]"));

	}

	@Test
	public void shouldReturnCirucitsRepresenationAfterFirstFailure() {
		String address = "http://someUrl";
		long resetTimeout = 50;
		int failureThreshold = 3;
		circuit = new Circuit(address, failureThreshold, resetTimeout);
		circuit.handleFailedConnection();
		assertThat(
				circuit.toString(),
				is("Circuit [address=http://someUrl, state=CircuitClosed [failureCount=1], failureThreshold=3, resetTimeout=50]"));
		circuit.handleSuccesfullConnection();
		assertThat(
				circuit.toString(),
				is("Circuit [address=http://someUrl, state=CircuitClosed [failureCount=0], failureThreshold=3, resetTimeout=50]"));

	}

	@Test
	public void shouldReturnCirucitsRepresenationAfterExceedingFailureThreshold() {
		String address = "http://someUrl";
		long resetTimeout = 50;
		int failureThreshold = 2;
		circuit = new Circuit(address, failureThreshold, resetTimeout);
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		assertThat(circuit.toString(), containsString("Circuit [address=http://someUrl, state=CircuitOpen"));

	}

	@Test
	public void shouldReturnCirucitsRepresenationResetTimeout() throws InterruptedException {
		String address = "http://someUrlABC";
		long resetTimeout = 50;
		int failureThreshold = 2;
		circuit = new Circuit(address, failureThreshold, resetTimeout);
		circuit.handleFailedConnection();
		circuit.handleFailedConnection();
		Thread.sleep(70);
		circuit.connectionAvailable();
		assertThat(
				circuit.toString(),
				is("Circuit [address=http://someUrlABC, state=CircuitHalfOpen, failureThreshold=2, resetTimeout=50]"));
		circuit.handleSuccesfullConnection();
		assertThat(
				circuit.toString(),
				is("Circuit [address=http://someUrlABC, state=CircuitClosed [failureCount=0], failureThreshold=2, resetTimeout=50]"));

	}

}
