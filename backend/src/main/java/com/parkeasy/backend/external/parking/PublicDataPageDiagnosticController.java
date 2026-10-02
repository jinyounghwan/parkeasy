package com.parkeasy.backend.external.parking;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Development-only diagnostic endpoint. Protect or remove this endpoint before production. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "parkeasy.admin.enabled", havingValue = "true")
@org.springframework.context.annotation.Profile("!prod")
@RequestMapping("/api/admin/public-data/diagnostic")
public class PublicDataPageDiagnosticController {
	private final PublicDataPageDiagnosticService diagnosticService;

	@Autowired
	public PublicDataPageDiagnosticController(PublicDataPageDiagnosticService diagnosticService) {
		this.diagnosticService = diagnosticService;
	}

	@GetMapping("/pages")
	public PublicDataPagesDiagnosticResponse diagnosePages(
			@RequestParam(defaultValue = "3") @Min(1) @Max(3) int pages,
			@RequestParam(defaultValue = "100") @Min(1) @Max(100) int numOfRows,
			@RequestParam(defaultValue = "1000") @Min(0) @Max(10000) long delayMs) {
		return diagnosticService.diagnose(pages, numOfRows, delayMs);
	}
}
