package com.nexoskill.evaluation.students.application;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.organizations.domain.model.OrganizationType;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationJpaEntity;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.OrganizationRepository;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.students.domain.StudentRecordModule;
import com.nexoskill.evaluation.students.domain.StudentStatus;
import com.nexoskill.evaluation.students.domain.TalentProfileCode;
import com.nexoskill.evaluation.students.domain.TalentType;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentJpaEntity;
import com.nexoskill.evaluation.students.infrastructure.persistence.StudentRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TalentBankService {
	private static final String SELECT = """
		SELECT s.PUBLIC_ID, s.STUDENT_CODE, s.CORPORATE_USER, s.EMAIL, s.FIRST_NAME, s.LAST_NAME,
			s.DISPLAY_NAME, s.STATUS, s.ACCESS_VALID_FROM, s.ACCESS_EXPIRES_ON, s.ADMISSION_DATE,
			s.ORGANIZATION_HIRED_ON, COALESCE(s.TALENT_PROFILE_CODE, profile.PROFILE_CODE) DISPLAY_PROFILE_CODE,
			s.TALENT_TYPE, s.TALENT_MOVED_AT,
			s.CREATED_AT, s.UPDATED_AT, s.VERSION_NO,
			o.PUBLIC_ID ORGANIZATION_PUBLIC_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME,
			o.MANUAL_STUDENT_CODE, o.APPLIES_CERTIFICATIONS,
			CASE WHEN s.TALENT_TYPE = 'BBVA_EXIT' THEN NULL
					ELSE COALESCE(t.PUBLIC_ID, tech_profile.PUBLIC_ID) END TECHNOLOGY_PUBLIC_ID,
			CASE WHEN s.TALENT_TYPE = 'BBVA_EXIT' THEN NULL
					ELSE COALESCE(t.TECHNOLOGY_CODE, tech_profile.PROFILE_CODE) END TECHNOLOGY_CODE,
			CASE WHEN s.TALENT_TYPE = 'BBVA_EXIT' THEN NULL
					ELSE COALESCE(t.TECHNOLOGY_NAME, tech_profile.PROFILE_NAME) END TECHNOLOGY_NAME,
			CASE WHEN s.TALENT_TYPE = 'BBVA_EXIT' THEN experience.CURRENT_TECHNOLOGY_EXPERTISE END CURRENT_TECHNOLOGY_EXPERTISE,
			CASE WHEN cv.TALENT_CV_DOCUMENT_ID IS NULL THEN 0 ELSE 1 END HAS_CV
		FROM STUDENT s
		JOIN ORGANIZATION o ON o.ORGANIZATION_ID = s.ORGANIZATION_ID
		LEFT JOIN QUESTION_TECHNOLOGY t ON t.TECHNOLOGY_ID = s.TALENT_TECHNOLOGY_ID
		LEFT JOIN CERTIFICATION_PROFILE_CATALOG profile ON profile.CERTIFICATION_PROFILE_ID = s.PROFESSIONAL_PROFILE_ID
		LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tech_profile ON tech_profile.TECHNOLOGICAL_PROFILE_ID = s.TECHNOLOGICAL_PROFILE_ID
		LEFT JOIN (
				SELECT exp.STUDENT_ID, exp.ORGANIZATION_ID,
					LISTAGG(
						exp.ITEM_NAME || CASE
							WHEN TRIM(exp.LEVEL_CODE) IS NOT NULL
							THEN ' - ' || exp.LEVEL_CODE
							ELSE ''
						END,
						' · '
					) WITHIN GROUP (ORDER BY exp.DISPLAY_ORDER, LOWER(exp.ITEM_NAME)) CURRENT_TECHNOLOGY_EXPERTISE
				FROM STUDENT_EXPERIENCE_ITEM exp
				WHERE exp.ITEM_TYPE = 'CURRENT_TECHNOLOGY'
				GROUP BY exp.STUDENT_ID, exp.ORGANIZATION_ID
		) experience
			ON experience.STUDENT_ID = s.STUDENT_ID
		AND experience.ORGANIZATION_ID = s.ORGANIZATION_ID
		LEFT JOIN TALENT_CV_DOCUMENT cv ON cv.STUDENT_ID = s.STUDENT_ID
		""";

	private final StudentService students;
	private final StudentFoundationService foundation;
	private final StudentRepository studentRepository;
	private final OrganizationRepository organizations;
	private final NamedParameterJdbcTemplate jdbc;
	private final AuditLogPort audit;
	private final Clock clock;

	public TalentBankService(StudentService students, StudentFoundationService foundation,
			StudentRepository studentRepository, OrganizationRepository organizations, NamedParameterJdbcTemplate jdbc,
			AuditLogPort audit, Clock clock) {
		this.students = students;
		this.foundation = foundation;
		this.studentRepository = studentRepository;
		this.organizations = organizations;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResult search(TenantContext tenant, SearchCriteria criteria, int page, int size) {
		requireTenant(tenant);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("offset", safePage * safeSize)
				.addValue("size", safeSize);
		String where = buildWhere(tenant,
				criteria == null ? new SearchCriteria(null, null, null, null, null, null, null) : criteria, params);
		Long total = jdbc.queryForObject("""
				SELECT COUNT(*)
				  FROM STUDENT s
				  JOIN ORGANIZATION o ON o.ORGANIZATION_ID=s.ORGANIZATION_ID
				  LEFT JOIN QUESTION_TECHNOLOGY t ON t.TECHNOLOGY_ID=s.TALENT_TECHNOLOGY_ID
				  LEFT JOIN CERTIFICATION_PROFILE_CATALOG profile
				    ON profile.CERTIFICATION_PROFILE_ID=s.PROFESSIONAL_PROFILE_ID
				  LEFT JOIN TECHNOLOGICAL_PROFILE_CATALOG tech_profile
				    ON tech_profile.TECHNOLOGICAL_PROFILE_ID=s.TECHNOLOGICAL_PROFILE_ID
				""" + where, params, Long.class);
		if (total == null || total == 0)
			return new PageResult(List.of(), safePage, safeSize, 0, 0);
		String order = allowedOrder(criteria == null ? null : criteria.sort(),
				criteria == null ? null : criteria.direction());
		List<TalentView> content = jdbc.query(
				SELECT + where + " ORDER BY " + order + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY", params,
				this::mapTalent);
		return new PageResult(content, safePage, safeSize, total, (int) Math.ceil((double) total / safeSize));
	}

	@Transactional(readOnly = true)
	public TalentView get(TenantContext tenant, String publicId) {
		TenantContext effective = effectiveTenant(tenant, publicId);
		List<TalentView> rows = jdbc.query(SELECT
				+ " WHERE s.PUBLIC_ID=:publicId AND s.ORGANIZATION_ID=:organizationId AND s.RECORD_MODULE='TALENT_BANK' AND s.STATUS<>'DELETED'",
				new MapSqlParameterSource("publicId", publicId).addValue("organizationId", effective.organizationId()),
				this::mapTalent);
		if (rows.isEmpty())
			throw new BusinessException("TALENT_NOT_FOUND", "El talento no existe.");
		return rows.getFirst();
	}

	@Transactional(readOnly = true)
	public HistoryPage history(TenantContext tenant, String publicId, int page, int size) {
		get(tenant, publicId);
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		MapSqlParameterSource params = new MapSqlParameterSource("studentPublicId", publicId)
				.addValue("offset", safePage * safeSize).addValue("size", safeSize);
		String where = " WHERE MODULE_CODE IN ('TALENT_BANK','STUDENTS') "
				+ "AND EVENT_TYPE IN ('TALENT_ACADEMY_CREATED','TALENT_PROSPECT_CREATED','TALENT_UPDATED',"
				+ "'TALENT_FOUNDATION_UPDATED','TALENT_CV_UPDATED','STUDENT_MOVED_TO_TALENT_BANK') "
				+ "AND DBMS_LOB.INSTR(EVENT_DATA, :studentPublicId) > 0 ";
		Long total = jdbc.queryForObject("SELECT COUNT(*) FROM AUDIT_EVENT" + where, params, Long.class);
		List<HistoryItem> content = jdbc.query(
				"""
						SELECT PUBLIC_ID, EVENT_TYPE, DESCRIPTION, OCCURRED_AT
						  FROM AUDIT_EVENT
						""" + where + " ORDER BY OCCURRED_AT DESC, AUDIT_EVENT_ID DESC "
						+ "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
				params,
				(rs, rowNum) -> new HistoryItem(rs.getString("PUBLIC_ID"), historyLabel(rs.getString("EVENT_TYPE")),
						rs.getString("DESCRIPTION"), instant(rs, "OCCURRED_AT")));
		long safeTotal = total == null ? 0 : total;
		return new HistoryPage(content, safePage, safeSize, safeTotal,
				safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / safeSize));
	}

	@Transactional(readOnly = true)
	public StudentFoundationService.StudentView getFoundation(TenantContext tenant, String publicId) {
		get(tenant, publicId);
		return foundation.getTalent(tenant, publicId);
	}

	@Transactional(readOnly = true)
	public CatalogBundle catalogs(TenantContext tenant, String organizationPublicId) {
		OrganizationJpaEntity organization = resolveTargetOrganization(tenant, organizationPublicId);
		List<TechnologyRef> technologies = jdbc.query(
				"""
						SELECT PUBLIC_ID, TECHNOLOGY_CODE, TECHNOLOGY_NAME
						  FROM QUESTION_TECHNOLOGY
						 WHERE STATUS='ACTIVE'
						   AND (CONTENT_SCOPE='GLOBAL' OR (CONTENT_SCOPE='ORGANIZATION' AND OWNER_ORGANIZATION_ID=:organizationId))
						 ORDER BY DISPLAY_ORDER, LOWER(TECHNOLOGY_NAME)
						""",
				Map.of("organizationId", organization.getId()),
				(rs, rowNum) -> new TechnologyRef(rs.getString("PUBLIC_ID"), rs.getString("TECHNOLOGY_CODE"),
						rs.getString("TECHNOLOGY_NAME")));
		return new CatalogBundle(organizationRef(organization), List.of("TR", "JR", "STD", "SR"), technologies);
	}

	@Transactional
	public CreateResult createAcademy(TenantContext tenant, AcademyCommand command, StudentService.Actor actor) {
		if (command == null)
			throw new BusinessException("TALENT_REQUIRED", "La información del talento es obligatoria.");
		OrganizationJpaEntity organization = resolveTargetOrganization(tenant, command.organizationPublicId());
		TenantContext effective = organizationTenant(organization, tenant.globalAdministrator());
		TalentProfileCode profile = requiredProfile(command.profileCode());
		Long technologyId = requiredTechnology(organization.getId(), command.technologyPublicId());
		StudentService.CreateResult creation = students.createTalentIdentity(effective,
				new StudentService.CreateCommand(command.email(), command.firstName(), command.lastName(),
						command.displayName(), StudentStatus.INACTIVE, command.validFrom(), command.expiresAt(), null,
						command.studentCode(), null),
				actor);
		StudentJpaEntity entity = studentRepository
				.findByOrganizationIdAndPublicIdForUpdate(organization.getId(), creation.student().publicId())
				.orElseThrow(() -> new BusinessException("TALENT_NOT_FOUND", "El talento no pudo ser localizado."));
		entity.configureTalent(TalentType.ACADEMY, command.organizationHiredOn(), profile, technologyId, actor.userId(),
				clock.instant());
		studentRepository.saveAndFlush(entity);
		record(actor, "TALENT_ACADEMY_CREATED", entity, "El talento fue registrado como integrante de Academia.");
		return new CreateResult(get(tenant, entity.getPublicId()));
	}

	@Transactional
	public CreateResult createProspect(TenantContext tenant, StudentFoundationService.CreateCommand command,
			StudentService.Actor actor) {
		if (command == null)
			throw new BusinessException("TALENT_REQUIRED", "La información del prospecto es obligatoria.");
		OrganizationJpaEntity organization = resolveTargetOrganization(tenant, command.organizationPublicId());
		StudentFoundationService.CreateCommand safe = new StudentFoundationService.CreateCommand(
				tenant.globalAdministrator() ? organization.getPublicId() : null, command.email(), command.firstName(),
				command.lastName(), command.displayName(), StudentStatus.INACTIVE, command.validFrom(),
				command.expiresAt(), null, command.studentCode(), null, command.professionalProfilePublicId(),
				command.technologicalProfilePublicId(), command.appliesTechnologicalCertification(),
				command.appliesDevelopmentSecurity(), command.appliesNormativeTesting(), command.appliesOne(),
				command.appliesAgile(), command.appliesJira());
		StudentFoundationService.CreateResult creation = foundation.createTalentIdentity(tenant, safe, actor);
		StudentJpaEntity entity = studentRepository
				.findByOrganizationIdAndPublicIdForUpdate(organization.getId(), creation.student().publicId())
				.orElseThrow(() -> new BusinessException("TALENT_NOT_FOUND", "El prospecto no pudo ser localizado."));
		entity.configureTalent(TalentType.PROSPECT, null, null, null, actor.userId(), clock.instant());
		studentRepository.saveAndFlush(entity);
		record(actor, "TALENT_PROSPECT_CREATED", entity, "El prospecto fue registrado en Talent Bank.");
		return new CreateResult(get(tenant, entity.getPublicId()));
	}

	@Transactional
	public TalentView updateAcademy(TenantContext tenant, String publicId, AcademyUpdateCommand command,
			StudentService.Actor actor) {
		TenantContext effective = effectiveTenant(tenant, publicId);
		StudentJpaEntity entity = students.findScopedForUpdate(effective, publicId, StudentRecordModule.TALENT_BANK);
		if (entity.getTalentType() != TalentType.ACADEMY) {
			throw new BusinessException("TALENT_TYPE_INVALID",
					"Este registro no corresponde a un talento de Academia.");
		}
		students.updateTalentRecord(effective, publicId,
				new StudentService.UpdateCommand(command.email(), command.firstName(), command.lastName(),
						command.displayName(), command.validFrom(), command.expiresAt(), null, command.studentCode(),
						null, command.version()),
				actor);
		Long technologyId = requiredTechnology(entity.getOrganizationId(), command.technologyPublicId());
		entity = students.findScopedForUpdate(effective, publicId, StudentRecordModule.TALENT_BANK);
		entity.updateTalentMetadata(command.organizationHiredOn(), requiredProfile(command.profileCode()), technologyId,
				actor.userId(), clock.instant());
		studentRepository.saveAndFlush(entity);
		record(actor, "TALENT_UPDATED", entity, "Los datos del talento fueron actualizados.");
		return get(tenant, publicId);
	}

	@Transactional
	public TalentView updateFullTalent(TenantContext tenant, String publicId,
			StudentFoundationService.UpdateCommand command, StudentService.Actor actor) {
		TalentView current = get(tenant, publicId);
		if (current.talentType() == TalentType.ACADEMY) {
			throw new BusinessException("TALENT_TYPE_INVALID",
					"Los talentos de Academia utilizan el formulario simplificado.");
		}
		StudentFoundationService.UpdateCommand safeCommand = current.talentType() == TalentType.BBVA_EXIT
				? new StudentFoundationService.UpdateCommand(command.email(), command.firstName(), command.lastName(),
						command.displayName(), command.validFrom(), command.expiresAt(),
						command.admissionDate() == null ? current.admissionDate() : command.admissionDate(),
						command.studentCode(),
						command.corporateUser() == null ? current.corporateUser() : command.corporateUser(),
						command.professionalProfilePublicId(), command.technologicalProfilePublicId(),
						command.appliesTechnologicalCertification(), command.appliesDevelopmentSecurity(),
						command.appliesNormativeTesting(), command.appliesOne(), command.appliesAgile(),
						command.appliesJira(), command.version())
				: command;
		foundation.updateTalent(tenant, publicId, safeCommand, actor);
		StudentJpaEntity entity = students.findScopedForUpdate(effectiveTenant(tenant, publicId), publicId,
				StudentRecordModule.TALENT_BANK);
		record(actor, "TALENT_UPDATED", entity,
				current.talentType() == TalentType.PROSPECT ? "Los datos del prospecto fueron actualizados."
						: "Los datos del colaborador en Talent Bank fueron actualizados.");
		return get(tenant, publicId);
	}

	@Transactional
	public StudentFoundationService.StudentView convert(TenantContext tenant, String publicId,
			StudentFoundationService.UpdateCommand command, StudentService.Actor actor) {
		get(tenant, publicId);
		if (command == null || command.admissionDate() == null) {
			throw new BusinessException("STUDENT_ADMISSION_DATE_REQUIRED",
					"La Fecha de alta es obligatoria para convertir el talento en colaborador.",
					Map.of("admissionDate", "Captura la Fecha de alta en BBVA."));
		}
		foundation.updateTalent(tenant, publicId, command, actor);
		TenantContext effective = effectiveTenant(tenant, publicId);
		StudentJpaEntity entity = students.findScopedForUpdate(effective, publicId, StudentRecordModule.TALENT_BANK);
		entity.convertToCollaborator(command.admissionDate(), actor.userId(), clock.instant());
		studentRepository.saveAndFlush(entity);
		record(actor, "TALENT_CONVERTED_TO_STUDENT", entity, "El talento fue convertido en colaborador.");
		return foundation.get(tenant, publicId);
	}

	public TenantContext effectiveTenant(TenantContext tenant, String publicId) {
		requireTenant(tenant);
		if (!tenant.globalAdministrator())
			return tenant;
		if (!tenant.globalScope()) {
			Long matches = jdbc.queryForObject("""
					SELECT COUNT(*) FROM STUDENT
					 WHERE PUBLIC_ID=:publicId AND ORGANIZATION_ID=:organizationId
					   AND RECORD_MODULE='TALENT_BANK' AND STATUS<>'DELETED'
					""",
					new MapSqlParameterSource("publicId", publicId).addValue("organizationId", tenant.organizationId()),
					Long.class);
			if (matches == null || matches == 0) {
				throw new BusinessException("TALENT_NOT_FOUND",
						"El talento no existe o no pertenece a la organización activa.");
			}
			return tenant;
		}
		List<TenantContext> rows = jdbc.query("""
				SELECT o.ORGANIZATION_ID, o.PUBLIC_ID, o.ORGANIZATION_CODE
				  FROM STUDENT s JOIN ORGANIZATION o ON o.ORGANIZATION_ID=s.ORGANIZATION_ID
				 WHERE s.PUBLIC_ID=:publicId AND s.RECORD_MODULE='TALENT_BANK' AND s.STATUS<>'DELETED'
				""", Map.of("publicId", publicId),
				(rs, rowNum) -> TenantContext.organization(rs.getLong("ORGANIZATION_ID"), rs.getString("PUBLIC_ID"),
						rs.getString("ORGANIZATION_CODE"), true));
		if (rows.isEmpty())
			throw new BusinessException("TALENT_NOT_FOUND", "El talento no existe.");
		return rows.getFirst();
	}

	private OrganizationJpaEntity resolveTargetOrganization(TenantContext tenant, String organizationPublicId) {
		requireTenant(tenant);
		OrganizationJpaEntity organization;
		if (tenant.globalAdministrator()) {
			if (!tenant.globalScope() && tenant.hasOrganization()) {
				organization = organizations.findById(tenant.organizationId()).orElseThrow(
						() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
				if (organizationPublicId != null && !organizationPublicId.isBlank()
						&& !organization.getPublicId().equals(organizationPublicId.trim())) {
					throw new BusinessException("TALENT_ORGANIZATION_FORBIDDEN",
							"La organización solicitada no coincide con el contexto activo.");
				}
			} else if (organizationPublicId != null && !organizationPublicId.isBlank()) {
				organization = organizations.findByPublicId(organizationPublicId.trim()).orElseThrow(
						() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
			} else {
				throw new BusinessException("TALENT_ORGANIZATION_REQUIRED", "Selecciona la organización del talento.",
						Map.of("organizationPublicId", "Debes seleccionar una organización."));
			}
		} else {
			if (organizationPublicId != null && !organizationPublicId.isBlank()) {
				throw new BusinessException("TALENT_ORGANIZATION_FORBIDDEN",
						"La organización se obtiene de la sesión autenticada.");
			}
			organization = organizations.findById(tenant.organizationId())
					.orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "La organización no existe."));
		}
		if (organization.getOrganizationType() != OrganizationType.CUSTOMER || organization.isGlobal()) {
			throw new BusinessException("TALENT_GLOBAL_FORBIDDEN", "Talent Bank requiere una organización comercial.");
		}
		if (!organization.isOperational(LocalDate.now(clock))) {
			throw new BusinessException("ORGANIZATION_NOT_OPERATIONAL", "La organización no está activa o vigente.");
		}
		return organization;
	}

	private TenantContext organizationTenant(OrganizationJpaEntity organization, boolean administrator) {
		return TenantContext.organization(organization.getId(), organization.getPublicId(), organization.getCode(),
				administrator);
	}

	private Long requiredTechnology(Long organizationId, String publicId) {
		if (publicId == null || publicId.isBlank()) {
			throw new BusinessException("TALENT_TECHNOLOGY_REQUIRED", "Selecciona la tecnología del talento.",
					Map.of("technologyPublicId", "La tecnología es obligatoria."));
		}
		List<Long> ids = jdbc.query(
				"""
						SELECT TECHNOLOGY_ID FROM QUESTION_TECHNOLOGY
						 WHERE PUBLIC_ID=:publicId AND STATUS='ACTIVE'
						   AND (CONTENT_SCOPE='GLOBAL' OR (CONTENT_SCOPE='ORGANIZATION' AND OWNER_ORGANIZATION_ID=:organizationId))
						""",
				new MapSqlParameterSource("publicId", publicId.trim()).addValue("organizationId", organizationId),
				(rs, rowNum) -> rs.getLong(1));
		if (ids.isEmpty())
			throw new BusinessException("TALENT_TECHNOLOGY_INVALID",
					"La tecnología no está disponible para la organización.");
		return ids.getFirst();
	}

	private TalentProfileCode requiredProfile(String value) {
		try {
			return TalentProfileCode.valueOf(value == null ? "" : value.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BusinessException("TALENT_PROFILE_INVALID", "El perfil debe ser TR, JR, STD o SR.",
					Map.of("profileCode", "Selecciona TR, JR, STD o SR."));
		}
	}

	private String buildWhere(TenantContext tenant, SearchCriteria criteria, MapSqlParameterSource params) {
		StringBuilder where = new StringBuilder(
				" WHERE s.RECORD_MODULE='TALENT_BANK' AND s.STATUS<>'DELETED' AND o.ORGANIZATION_TYPE='CUSTOMER' ");
		if (!tenant.globalAdministrator() || !tenant.globalScope()) {
			if (!tenant.hasOrganization())
				throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "No existe un contexto autorizado.");
			where.append(" AND s.ORGANIZATION_ID=:tenantOrganizationId ");
			params.addValue("tenantOrganizationId", tenant.organizationId());
		} else if (notBlank(criteria.organizationPublicId())) {
			where.append(" AND o.PUBLIC_ID=:organizationPublicId ");
			params.addValue("organizationPublicId", criteria.organizationPublicId().trim());
		}
		if (notBlank(criteria.query())) {
			where.append(
					" AND (LOWER(s.DISPLAY_NAME) LIKE :query OR LOWER(s.EMAIL) LIKE :query OR LOWER(s.STUDENT_CODE) LIKE :query) ");
			params.addValue("query", "%" + criteria.query().trim().toLowerCase() + "%");
		}
		if (criteria.type() != null) {
			where.append(" AND s.TALENT_TYPE=:talentType ");
			params.addValue("talentType", criteria.type().name());
		}
		if (notBlank(criteria.profileCode())) {
			where.append(" AND COALESCE(s.TALENT_PROFILE_CODE, profile.PROFILE_CODE)=:profileCode ");
			params.addValue("profileCode", requiredProfile(criteria.profileCode()).name());
		}
		if (notBlank(criteria.technologyPublicId())) {
			where.append(" AND COALESCE(t.PUBLIC_ID, tech_profile.PUBLIC_ID)=:technologyPublicId ");
			params.addValue("technologyPublicId", criteria.technologyPublicId().trim());
		}
		return where.toString();
	}

	private String allowedOrder(String sort, String direction) {
		String column = switch (sort == null ? "" : sort) {
		case "displayName" -> "LOWER(s.DISPLAY_NAME)";
		case "email" -> "LOWER(s.EMAIL)";
		case "organization" -> "LOWER(o.ORGANIZATION_NAME)";
		case "type" -> "s.TALENT_TYPE";
		default -> "NVL(s.UPDATED_AT,s.CREATED_AT)";
		};
		return column + ("ASC".equalsIgnoreCase(direction) ? " ASC" : " DESC") + ", s.STUDENT_ID DESC";
	}

	private TalentView mapTalent(ResultSet rs, int rowNum) throws SQLException {
		return new TalentView(rs.getString("PUBLIC_ID"), rs.getString("STUDENT_CODE"), rs.getString("CORPORATE_USER"),
				rs.getString("EMAIL"), rs.getString("FIRST_NAME"), rs.getString("LAST_NAME"),
				rs.getString("DISPLAY_NAME"), TalentType.valueOf(rs.getString("TALENT_TYPE")),
				rs.getString("DISPLAY_PROFILE_CODE"), technology(rs), rs.getString("CURRENT_TECHNOLOGY_EXPERTISE"),
				localDate(rs, "ORGANIZATION_HIRED_ON"), localDate(rs, "ACCESS_VALID_FROM"),
				localDate(rs, "ACCESS_EXPIRES_ON"), localDate(rs, "ADMISSION_DATE"), rs.getBoolean("HAS_CV"),
				organizationRef(rs), instant(rs, "TALENT_MOVED_AT"), instant(rs, "CREATED_AT"),
				instant(rs, "UPDATED_AT"), rs.getLong("VERSION_NO"));
	}

	private OrganizationRef organizationRef(ResultSet rs) throws SQLException {
		return new OrganizationRef(rs.getString("ORGANIZATION_PUBLIC_ID"), rs.getString("ORGANIZATION_CODE"),
				rs.getString("ORGANIZATION_NAME"), rs.getBoolean("MANUAL_STUDENT_CODE"),
				rs.getBoolean("APPLIES_CERTIFICATIONS"));
	}

	private OrganizationRef organizationRef(OrganizationJpaEntity org) {
		return new OrganizationRef(org.getPublicId(), org.getCode(), org.getName(), org.isManualStudentCode(),
				org.isAppliesCertifications());
	}

	private TechnologyRef technology(ResultSet rs) throws SQLException {
		String id = rs.getString("TECHNOLOGY_PUBLIC_ID");
		return id == null ? null
				: new TechnologyRef(id, rs.getString("TECHNOLOGY_CODE"), rs.getString("TECHNOLOGY_NAME"));
	}

	private LocalDate localDate(ResultSet rs, String column) throws SQLException {
		Date value = rs.getDate(column);
		return value == null ? null : value.toLocalDate();
	}

	private Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private String historyLabel(String eventType) {
		return switch (eventType == null ? "" : eventType) {
		case "TALENT_ACADEMY_CREATED" -> "Alta en Academia";
		case "TALENT_PROSPECT_CREATED" -> "Alta de prospecto";
		case "STUDENT_MOVED_TO_TALENT_BANK" -> "Baja";
		case "TALENT_CV_UPDATED" -> "Actualización de CV";
		case "TALENT_UPDATED", "TALENT_FOUNDATION_UPDATED" -> "Actualización de talento";
		default -> "Cambio relevante";
		};
	}

	private void record(StudentService.Actor actor, String event, StudentJpaEntity student, String description) {
		HashMap<String, Object> data = new HashMap<>();
		data.put("studentPublicId", student.getPublicId());
		data.put("organizationId", student.getOrganizationId());
		data.put("recordModule", student.getRecordModule().name());
		if (student.getTalentType() != null)
			data.put("talentType", student.getTalentType().name());
		audit.record(actor.userId(), event, "TALENT_BANK", description, actor.ipAddress(), actor.userAgent(), data,
				clock.instant());
	}

	private void requireTenant(TenantContext tenant) {
		if (tenant == null || (!tenant.globalAdministrator() && !tenant.hasOrganization())) {
			throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "No existe un contexto autorizado.");
		}
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	public record SearchCriteria(String query, String organizationPublicId, TalentType type, String profileCode,
			String technologyPublicId, String sort, String direction) {
	}

	public record AcademyCommand(String organizationPublicId, String studentCode, String email, String firstName,
			String lastName, String displayName, LocalDate validFrom, LocalDate expiresAt,
			LocalDate organizationHiredOn, String profileCode, String technologyPublicId) {
	}

	public record AcademyUpdateCommand(String studentCode, String email, String firstName, String lastName,
			String displayName, LocalDate validFrom, LocalDate expiresAt, LocalDate organizationHiredOn,
			String profileCode, String technologyPublicId, Long version) {
	}

	public record OrganizationRef(String publicId, String code, String name, boolean manualStudentCode,
			boolean appliesCertifications) {
	}

	public record TechnologyRef(String publicId, String code, String name) {
	}

	public record CatalogBundle(OrganizationRef organization, List<String> profiles, List<TechnologyRef> technologies) {
	}

	public record TalentView(String publicId, String studentCode, String corporateUser, String email, String firstName,
			String lastName, String displayName, TalentType talentType, String profileCode, TechnologyRef technology,
			String currentTechnologyExpertise, LocalDate organizationHiredOn, LocalDate validFrom, LocalDate expiresAt,
			LocalDate admissionDate, boolean hasCv, OrganizationRef organization, Instant movedAt, Instant createdAt,
			Instant updatedAt, Long version) {
	}

	public record HistoryItem(String publicId, String eventType, String description, Instant occurredAt) {
	}

	public record HistoryPage(List<HistoryItem> content, int page, int size, long totalElements, int totalPages) {
		public HistoryPage {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}

	public record CreateResult(TalentView talent) {
	}

	public record PageResult(List<TalentView> content, int page, int size, long totalElements, int totalPages) {
		public PageResult {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}
}
