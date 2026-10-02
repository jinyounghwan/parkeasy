package com.parkeasy.backend.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class ParkingWebConfiguration implements WebMvcConfigurer {
	private final List<String> allowedOrigins;

	public ParkingWebConfiguration(@Value("${parkeasy.cors.allowed-origins:}") String origins) {
		allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		for (String origin : allowedOrigins) {
			try {
				URI uri = URI.create(origin);
				if (origin.contains("*") || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
						|| uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
						|| uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) {
					throw new IllegalArgumentException();
				}
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("CORS requires exact HTTP(S) origins without paths or wildcards");
			}
		}
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (allowedOrigins.isEmpty()) return;
		registry.addMapping("/api/parking-lots/**").allowedOrigins(allowedOrigins.toArray(String[]::new))
				.allowedMethods("GET").allowCredentials(false).maxAge(600);
	}
}
