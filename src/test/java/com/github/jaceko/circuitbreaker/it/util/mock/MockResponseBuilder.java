package com.github.jaceko.circuitbreaker.it.util.mock;

public interface MockResponseBuilder {
	int responseDelay();
	String build();
}
