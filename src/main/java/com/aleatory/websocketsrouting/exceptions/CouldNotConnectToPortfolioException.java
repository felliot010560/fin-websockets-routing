package com.aleatory.websocketsrouting.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.SERVICE_UNAVAILABLE, reason="Could not connect to portfolio")
public class CouldNotConnectToPortfolioException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public CouldNotConnectToPortfolioException() {
	}

	public CouldNotConnectToPortfolioException(String message) {
		super(message);
	}

	public CouldNotConnectToPortfolioException(Throwable cause) {
		super(cause);
	}

	public CouldNotConnectToPortfolioException(String message, Throwable cause) {
		super(message, cause);
	}

	public CouldNotConnectToPortfolioException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
