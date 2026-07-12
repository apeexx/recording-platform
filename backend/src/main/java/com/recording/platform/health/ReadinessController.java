package com.recording.platform.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class ReadinessController {
	private final ReadinessService readiness;

	public ReadinessController(ReadinessService readiness) {
		this.readiness = readiness;
	}

	@GetMapping("/ready")
	public ResponseEntity<ReadinessService.Readiness> ready() {
		ReadinessService.Readiness status = readiness.check();
		return ResponseEntity.status("UP".equals(status.overall()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
			.body(status);
	}
}
