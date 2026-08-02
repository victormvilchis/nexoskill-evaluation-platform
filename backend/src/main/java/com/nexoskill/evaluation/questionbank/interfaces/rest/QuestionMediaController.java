package com.nexoskill.evaluation.questionbank.interfaces.rest;

import com.nexoskill.evaluation.authentication.infrastructure.security.AuthenticatedUser;
import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.questionbank.application.model.QuestionMediaView;
import com.nexoskill.evaluation.questionbank.application.service.QuestionMediaService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/question-media")
public class QuestionMediaController {
    private final QuestionMediaService service;
    private final TenantContextResolver tenantResolver;

    public QuestionMediaController(QuestionMediaService service, TenantContextResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('QUESTION_MEDIA_MANAGE')")
    public QuestionMediaView upload(@RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) throws IOException {
        return service.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes(), actor.internalId(),
                tenantResolver.resolve(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QUESTION_VIEW')")
    public ResponseEntity<byte[]> get(@PathVariable String id,
            @AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        var download = service.download(id, actor.internalId(), tenantResolver.resolve(request));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.media().contentType()))
                .cacheControl(CacheControl.noCache()).body(download.content());
    }
}
