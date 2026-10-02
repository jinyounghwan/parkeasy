package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;

class PublicDataFailureClassifierTest {
	private final PublicDataFailureClassifier classifier = new PublicDataFailureClassifier();

	@Test
	void classifiesNetworkFailuresAsTransient() {
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, new HttpTimeoutException("timeout"));
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, new SocketException("reset"));
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, new SocketTimeoutException("timeout"));
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, new ConnectException("refused"));
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, new UnresolvedAddressException());
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, new IOException("transport closed"));
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK,
				new IOException("request failed", new SocketException("reset")));
	}

	@Test
	void classifiesStreamReadEofAsTransient() {
		StreamReadException failure = new StreamReadException(
				(JsonParser) null, "truncated", new EOFException("eof"));
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK, failure);
		assertCategory(PublicDataFailureCategory.TRANSIENT_NETWORK,
				new StreamReadException("premature end of stream"));
	}

	@Test
	void classifiesStreamReadWithMappingCauseAsPermanentParse() {
		DatabindException mapping = DatabindException.from((JsonParser) null, "schema mismatch");
		StreamReadException failure = new StreamReadException((JsonParser) null, "mapping", mapping);
		assertCategory(PublicDataFailureCategory.PERMANENT_PARSE, failure);
	}

	private void assertCategory(PublicDataFailureCategory expected, Throwable failure) {
		assertEquals(expected, classifier.classify(failure).category());
	}
}
