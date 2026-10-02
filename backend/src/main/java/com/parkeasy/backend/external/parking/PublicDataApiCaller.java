package com.parkeasy.backend.external.parking;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

final class PublicDataApiCaller {
	private static final Logger log = LoggerFactory.getLogger(PublicDataApiCaller.class);
	private static final int MAX_ATTEMPTS = 2;
	private final RestClient restClient;
	private final long retryBackoffMs;
	private final PublicDataFailureClassifier failureClassifier = new PublicDataFailureClassifier();

	PublicDataApiCaller(RestClient restClient) { this(restClient, 0); }
	PublicDataApiCaller(RestClient restClient, long retryBackoffMs) {
		this.restClient = restClient;
		this.retryBackoffMs = retryBackoffMs;
	}

	static PublicDataApiCaller createDefault() {
		return createDefault(0);
	}

	static PublicDataApiCaller createDefault(long retryBackoffMs) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.version(HttpClient.Version.HTTP_1_1)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(30));
		return new PublicDataApiCaller(RestClient.builder().requestFactory(factory).build(), retryBackoffMs);
	}

	String get(URI uri) {
		return execute(uri, null, Function.identity(), ignored -> -1);
	}

	<T> SingleCallResult<T> getOnce(URI uri, Function<String, T> responseMapper) {
		var response = restClient.get().uri(uri).retrieve().toEntity(String.class);
		return new SingleCallResult<>(response.getStatusCode().value(),
				responseMapper.apply(response.getBody()));
	}

	<T> T get(URI uri, int pageNo, Function<String, T> responseMapper,
			ToIntFunction<T> itemCounter) {
		return execute(uri, pageNo, responseMapper, itemCounter);
	}

	private <T> T execute(URI uri, Integer pageNo, Function<String, T> responseMapper,
			ToIntFunction<T> itemCounter) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			Instant startedAt = Instant.now();
			long startedNanos = System.nanoTime();
			try {
				var response = restClient.get().uri(uri).retrieve().toEntity(String.class);
				T mapped = responseMapper.apply(response.getBody());
				if (pageNo != null) {
					log.info("PUBLIC_DATA_REQUEST pageNo={} attempt={} startedAt={} durationMs={} success=true status={} items={}",
							pageNo, attempt, startedAt, elapsedMillis(startedNanos),
							response.getStatusCode().value(), itemCounter.applyAsInt(mapped));
				}
				return mapped;
			} catch (RuntimeException exception) {
				if (pageNo != null) {
					PublicDataFailure failure = failureClassifier.classify(exception);
					log.info("PUBLIC_DATA_REQUEST pageNo={} attempt={} startedAt={} durationMs={} success=false status={} failureCategory={} rootErrorType={}",
							pageNo, attempt, startedAt, elapsedMillis(startedNanos),
							httpStatus(exception), failure.category(), failure.rootErrorType());
				}
				if (attempt == MAX_ATTEMPTS || !failureClassifier.classify(exception).transientFailure()) throw exception;
				waitBeforeRetry();
			}
		}
		throw new IllegalStateException("Unreachable public API retry state");
	}

	private long elapsedMillis(long startedNanos) {
		return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
	}

	private String httpStatus(RuntimeException exception) {
		return exception instanceof HttpServerErrorException serverError
				? Integer.toString(serverError.getStatusCode().value()) : "N/A";
	}

	private void waitBeforeRetry() {
		try {
			Thread.sleep(retryBackoffMs);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Public data retry backoff was interrupted", exception);
		}
	}

	record SingleCallResult<T>(int status, T body) {}
}
