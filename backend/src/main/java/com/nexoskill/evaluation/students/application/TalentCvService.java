package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentRecordModule;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.TalentCvJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.TalentCvRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TalentCvService {
	private static final long MAX_BYTES = 15L * 1024L * 1024L;
	private static final Set<String> EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx");
	private final StudentService students;
	private final TalentCvRepository documents;
	private final AuditLogPort audit;
	private final Clock clock;

	public TalentCvService(StudentService students, TalentCvRepository documents, AuditLogPort audit, Clock clock) {
		this.students = students;
		this.documents = documents;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public CvMetadata metadata(TenantContext tenant, String publicId) {
		StudentJpaEntity talent = students.findScopedForUpdate(tenant, publicId, StudentRecordModule.TALENT_BANK);
		return documents.findByStudentIdAndOrganizationId(talent.getId(), talent.getOrganizationId())
				.map(this::metadata).orElse(null);
	}

	@Transactional
	public CvMetadata upload(TenantContext tenant, String publicId, MultipartFile file, StudentService.Actor actor) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException("TALENT_CV_REQUIRED", "Selecciona un archivo para el CV.");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new BusinessException("TALENT_CV_TOO_LARGE", "El CV no puede superar 15 MB.");
		}
		String originalName = safeName(file.getOriginalFilename());
		String extension = extension(originalName);
		if (!EXTENSIONS.contains(extension)) {
			throw new BusinessException("TALENT_CV_FORMAT_INVALID",
					"El CV debe estar en formato PDF, Word o PowerPoint.");
		}
		byte[] data;
		try {
			data = file.getBytes();
		} catch (java.io.IOException exception) {
			throw new BusinessException("TALENT_CV_READ_FAILED", "No fue posible leer el archivo seleccionado.");
		}
		StudentJpaEntity talent = students.findScopedForUpdate(tenant, publicId, StudentRecordModule.TALENT_BANK);
		Instant now = clock.instant();
		TalentCvJpaEntity document = documents.findByStudentId(talent.getId())
				.orElseGet(() -> TalentCvJpaEntity.create(UUID.randomUUID().toString(), talent.getId(),
						talent.getOrganizationId(), originalName, contentType(file, extension), extension, data,
						actor.userId(), now));
		if (document.getId() != null) {
			document.replace(originalName, contentType(file, extension), extension, data, actor.userId(), now);
		}
		document = documents.saveAndFlush(document);
		audit.record(actor.userId(), "TALENT_CV_UPDATED", "TALENT_BANK", "El CV del talento fue actualizado.",
				actor.ipAddress(), actor.userAgent(), Map.of("studentPublicId", talent.getPublicId(), "organizationId",
						talent.getOrganizationId(), "fileExtension", extension, "fileSize", data.length),
				now);
		return metadata(document);
	}

	@Transactional
	public CvDownload download(TenantContext tenant, String publicId) {
		StudentJpaEntity talent = students.findScopedForUpdate(tenant, publicId, StudentRecordModule.TALENT_BANK);
		TalentCvJpaEntity document = documents
				.findByStudentIdAndOrganizationId(talent.getId(), talent.getOrganizationId()).orElseThrow(
						() -> new BusinessException("TALENT_CV_NOT_FOUND", "El talento no tiene un CV registrado."));
		return new CvDownload(document.getFileName(), document.getContentType(), document.getFileData());
	}

	private CvMetadata metadata(TalentCvJpaEntity document) {
		return new CvMetadata(document.getPublicId(), document.getFileName(), document.getContentType(),
				document.getFileExtension(), document.getFileSize(), document.getUpdatedAt());
	}

	private String safeName(String name) {
		String value = name == null ? "cv" : name.replace('\\', '/');
		value = value.substring(value.lastIndexOf('/') + 1).trim();
		if (value.isBlank() || value.length() > 255) {
			throw new BusinessException("TALENT_CV_NAME_INVALID", "El nombre del archivo no es válido.");
		}
		return value;
	}

	private String extension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private String contentType(MultipartFile file, String extension) {
		if (file.getContentType() != null && !file.getContentType().isBlank())
			return file.getContentType();
		return switch (extension) {
		case "pdf" -> "application/pdf";
		case "doc" -> "application/msword";
		case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		case "ppt" -> "application/vnd.ms-powerpoint";
		case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
		default -> "application/octet-stream";
		};
	}

	public record CvMetadata(String publicId, String fileName, String contentType, String extension, long fileSize,
			Instant updatedAt) {
	}

	public record CvDownload(String fileName, String contentType, byte[] data) {
		public CvDownload {
			data = data == null ? new byte[0] : data.clone();
		}
	}
}
