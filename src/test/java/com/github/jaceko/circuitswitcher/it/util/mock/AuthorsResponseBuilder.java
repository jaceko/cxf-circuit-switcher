package com.github.jaceko.circuitswitcher.it.util.mock;

import java.text.MessageFormat;

public class AuthorsResponseBuilder implements MockResponseBuilder {

	private static String RESPONSE_TEMPLATE = "<authors>\r\n" + "<author>\r\n" + "   <name>{0}</name>\r\n" + "</author>\r\n"
			+ "</authors>";
	private String name;
	private int responseDelay;

	public static AuthorsResponseBuilder anAuthorsRsponse() {
		return new AuthorsResponseBuilder();
	}

	public AuthorsResponseBuilder withAuthorName(String name) {
		this.name = name;
		return this;
	}

	public AuthorsResponseBuilder withResponseDelaySec(int responseDelay) {
		this.responseDelay = responseDelay;
		return this;
	}

	@Override
	public int responseDelay() {
		return responseDelay;
	}

	@Override
	public String build() {
		return MessageFormat.format(RESPONSE_TEMPLATE, name);
	}

}
