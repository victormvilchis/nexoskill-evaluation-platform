package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;
import com.nexoskill.evaluation.questionbank.application.service.QuestionMediaService;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/question-media")
public class QuestionMediaController {
	private final QuestionMediaService service;

	public QuestionMediaController(QuestionMediaService s) {
		service = s;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAuthority('QUESTION_MEDIA_MANAGE')")
	public QuestionMediaView upload(@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal AuthenticatedUser a) throws IOException {
		return service.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes(), a.internalId());
	}

	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<byte[]> get(@PathVariable String id) {
		var d = service.download(id);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.media().contentType()))
				.cacheControl(CacheControl.noCache()).body(d.content());
	}
}
