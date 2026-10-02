package com.parkeasy.backend.external.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class PublicDataApiCallerLoggingTest {

	@Test
	void logsPageAndEachRetryAttemptWithoutUri(CapturedOutput output) {
		URI uri = URI.create("http://example.test/facilities?serviceKey=secret-value&pageNo=3");
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(uri)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(requestTo(uri)).andRespond(withSuccess("abc", null));

		Integer result = new PublicDataApiCaller(builder.build())
				.get(uri, 3, String::length, Integer::intValue);

		assertThat(result).isEqualTo(3);
		assertThat(output).contains("PUBLIC_DATA_REQUEST pageNo=3 attempt=1")
				.contains("success=false status=503")
				.contains("PUBLIC_DATA_REQUEST pageNo=3 attempt=2")
				.contains("success=true status=200 items=3")
				.doesNotContain("secret-value");
		server.verify();
	}
}
