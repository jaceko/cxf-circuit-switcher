package com.github.jaceko.circuitswitcher.it.util.mock;

import java.text.MessageFormat;

public class BooksResponseBuilder implements MockResponseBuilder {

	private static String RESPONSE_TEMPLATE = "<books>\r\n" + "<book>\r\n" + "   <title>{0}</title>\r\n" + "</book>\r\n"
			+ "</books>";
	private String title;
	private int responseDelay;

	public static BooksResponseBuilder aBooksRsponse() {
		return new BooksResponseBuilder();
	}

	public BooksResponseBuilder withBookTitle(String title) {
		this.title = title;
		return this;
	}
	
	public BooksResponseBuilder withResponseDelaySec(int responseDelay) {
		this.responseDelay = responseDelay;
		return this;
	}

	@Override
	public int responseDelay() {
		return responseDelay;
	}

	@Override
	public String build() {
		return MessageFormat.format(RESPONSE_TEMPLATE, title);
	}

}
