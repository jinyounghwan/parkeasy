package com.parkeasy.backend.external.parking;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;

public final class PublicDataFailureClassifier {
	public PublicDataFailure classify(Throwable failure) {
		PublicDataFailureCategory category = classifyCategory(failure);
		return new PublicDataFailure(category, rootType(failure));
	}

	private PublicDataFailureCategory classifyCategory(Throwable failure) {
		for (Throwable cause : causeChain(failure)) {
			if (cause instanceof IncompletePublicDataPageException) {
				return PublicDataFailureCategory.TRANSIENT_UPSTREAM;
			}
			if (cause instanceof HttpServerErrorException serverError) {
				int status = serverError.getStatusCode().value();
				if (status == 502 || status == 503 || status == 504) {
					return PublicDataFailureCategory.TRANSIENT_UPSTREAM;
				}
				return PublicDataFailureCategory.PERMANENT_CLIENT;
			}
			if (cause instanceof HttpClientErrorException) return PublicDataFailureCategory.PERMANENT_CLIENT;
		}
		if (hasNetworkCause(failure)) return PublicDataFailureCategory.TRANSIENT_NETWORK;
		for (Throwable cause : causeChain(failure)) {
			if (cause instanceof StreamReadException streamRead) {
				return isPrematureStream(streamRead)
						? PublicDataFailureCategory.TRANSIENT_NETWORK
						: PublicDataFailureCategory.PERMANENT_PARSE;
			}
			if (cause instanceof DatabindException || cause instanceof JacksonException) {
				return PublicDataFailureCategory.PERMANENT_PARSE;
			}
		}
		for (Throwable cause : causeChain(failure)) {
			if (cause instanceof IOException) return PublicDataFailureCategory.TRANSIENT_NETWORK;
		}
		return PublicDataFailureCategory.UNKNOWN;
	}

	private boolean hasNetworkCause(Throwable failure) {
		for (Throwable cause : causeChain(failure)) {
			if (cause instanceof HttpTimeoutException
					|| cause instanceof SocketTimeoutException
					|| cause instanceof ConnectException
					|| cause instanceof SocketException
					|| cause instanceof UnresolvedAddressException
					|| cause instanceof EOFException) return true;
		}
		return false;
	}

	private boolean isPrematureStream(StreamReadException failure) {
		String message = failure.getMessage();
		return message != null && message.toLowerCase(Locale.ROOT).contains("premature end of stream");
	}

	private Iterable<Throwable> causeChain(Throwable failure) {
		Set<Throwable> causes = new HashSet<>();
		List<Throwable> ordered = new ArrayList<>();
		Throwable current = failure;
		while (current != null && causes.add(current)) {
			ordered.add(current);
			current = current.getCause();
		}
		return ordered;
	}

	private String rootType(Throwable failure) {
		Set<Throwable> seen = new HashSet<>();
		Throwable current = failure;
		while (current.getCause() != null && seen.add(current)) current = current.getCause();
		return current.getClass().getSimpleName();
	}
}
