package com.recording.platform.identity.controller;

import com.recording.platform.identity.dto.CollectorProfileResponse;
import com.recording.platform.identity.service.CollectorAvatarService;
import com.recording.platform.security.PlatformPrincipal;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/miniprogram/avatar")
public class CollectorAvatarController {
	private final CollectorAvatarService avatars;
	public CollectorAvatarController(CollectorAvatarService avatars) { this.avatars = avatars; }

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public CollectorProfileResponse upload(@AuthenticationPrincipal PlatformPrincipal principal, @RequestPart MultipartFile avatar) {
		return CollectorProfileResponse.from(avatars.upload(principal.userId(), avatar));
	}

	@GetMapping
	public ResponseEntity<FileSystemResource> read(@AuthenticationPrincipal PlatformPrincipal principal) {
		CollectorAvatarService.AvatarFile file = avatars.read(principal.userId());
		return ResponseEntity.ok().cacheControl(CacheControl.noCache())
			.contentType(MediaType.parseMediaType(file.contentType())).body(new FileSystemResource(file.path()));
	}

	@DeleteMapping
	public CollectorProfileResponse delete(@AuthenticationPrincipal PlatformPrincipal principal) {
		return CollectorProfileResponse.from(avatars.delete(principal.userId()));
	}
}
