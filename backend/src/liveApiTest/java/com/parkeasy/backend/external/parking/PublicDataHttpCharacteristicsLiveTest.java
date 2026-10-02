package com.parkeasy.backend.external.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.util.UriComponentsBuilder;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

class PublicDataHttpCharacteristicsLiveTest {
	private static final String ENDPOINT =
			"http://apis.data.go.kr/B553881/Parking/PrkSttusInfo";
	private static final String DIAGNOSTIC_USER_AGENT = "ParkEasy-PublicData-Diagnostic/1.0";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	@EnabledIfEnvironmentVariable(named = "PARKEASY_PUBLIC_DATA_SERVICE_KEY", matches = ".+")
	void comparesDefaultAndExplicitHeadersWithOneRequestEach() throws Exception {
		inspectDefaultHeadersWithoutExternalRequest();
		callOnce("default", newClient(), false);
		callOnce("explicit", newClient(), true);
	}

	private void inspectDefaultHeadersWithoutExternalRequest() throws Exception {
		AtomicReference<String> captured = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/headers", exchange -> {
			captured.set(String.format(
					"JAVA_HEADERS userAgent=%s accept=%s acceptEncoding=%s connection=%s httpVersion=%s",
					value(exchange.getRequestHeaders().getFirst("User-Agent")),
					value(exchange.getRequestHeaders().getFirst("Accept")),
					value(exchange.getRequestHeaders().getFirst("Accept-Encoding")),
					value(exchange.getRequestHeaders().getFirst("Connection")),
					exchange.getProtocol()));
			exchange.sendResponseHeaders(204, -1);
			exchange.close();
		});
		server.start();
		try {
			URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/headers");
			newClient().send(HttpRequest.newBuilder(uri).GET().build(),
					HttpResponse.BodyHandlers.discarding());
			System.out.println(captured.get());
		} finally {
			server.stop(0);
		}
	}

	private void callOnce(String mode, HttpClient client, boolean explicitHeaders) throws Exception {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(buildUri())
				.timeout(Duration.ofSeconds(30))
				.GET();
		if (explicitHeaders) {
			requestBuilder.header("User-Agent", DIAGNOSTIC_USER_AGENT)
					.header("Accept", "*/*");
		}

		long startedNanos = System.nanoTime();
		try {
			HttpResponse<String> httpResponse = client.send(
					requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
			PublicParkingFacilityResponse response = jsonMapper.readValue(
					httpResponse.body(), PublicParkingFacilityResponse.class);
			long durationMs = elapsedMillis(startedNanos);
			System.out.printf(
					"JAVA_HTTP_COMPARISON mode=%s durationMs=%d status=%d resultCode=%s items=%d%n",
					mode, durationMs, httpResponse.statusCode(), response.resultCode(),
					response.safeItems().size());
			assertEquals(200, httpResponse.statusCode());
			assertEquals("0", response.resultCode());
		} catch (Exception exception) {
			System.out.printf(
					"JAVA_HTTP_COMPARISON mode=%s durationMs=%d status=N/A resultCode=N/A items=N/A%n",
					mode, elapsedMillis(startedNanos));
		}
	}

	private HttpClient newClient() {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.version(HttpClient.Version.HTTP_1_1)
				.build();
	}

	private URI buildUri() {
		return UriComponentsBuilder.fromUriString(ENDPOINT)
				.queryParam("serviceKey", System.getenv("PARKEASY_PUBLIC_DATA_SERVICE_KEY"))
				.queryParam("numOfRows", 100)
				.queryParam("pageNo", 1)
				.queryParam("format", 2)
				.build(true)
				.toUri();
	}

	private long elapsedMillis(long startedNanos) {
		return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
	}

	private String value(String header) {
		return header == null ? "<absent>" : header;
	}
}
