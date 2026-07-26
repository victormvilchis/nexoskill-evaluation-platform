package com.nexoskill.evaluation.organizations.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "ORGANIZATION_LICENSE_POLICY")
public class OrganizationLicensePolicyJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LICENSE_POLICY_ID")
    private Long id;

    @Column(name = "ORGANIZATION_ID", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "CONTRACTED_SEATS", nullable = false)
    private Integer contractedSeats;

    @Column(name = "INCLUDED_REPLACEMENTS", nullable = false)
    private Integer includedReplacements;

    @Column(name = "ADDITIONAL_REPLACEMENTS", nullable = false)
    private Integer additionalReplacements;

    @Column(name = "STANDARD_RELEASE_HOURS", nullable = false)
    private Integer standardReleaseHours;

    @Column(name = "EXHAUSTED_RELEASE_DAYS", nullable = false)
    private Integer exhaustedReleaseDays;

    @Column(name = "CYCLE_STARTS_ON", nullable = false)
    private LocalDate cycleStartsOn;

    @Column(name = "CYCLE_ENDS_ON", nullable = false)
    private LocalDate cycleEndsOn;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private Long version;

    protected OrganizationLicensePolicyJpaEntity() {}

    public static OrganizationLicensePolicyJpaEntity create(Long organizationId, int seats, int replacements,
                                                             LocalDate cycleStart, LocalDate cycleEnd, Instant now) {
        OrganizationLicensePolicyJpaEntity entity = new OrganizationLicensePolicyJpaEntity();
        entity.organizationId = organizationId;
        entity.contractedSeats = seats;
        entity.includedReplacements = replacements;
        entity.additionalReplacements = 0;
        entity.standardReleaseHours = 24;
        entity.exhaustedReleaseDays = 7;
        entity.cycleStartsOn = cycleStart;
        entity.cycleEndsOn = cycleEnd;
        entity.status = "ACTIVE";
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.version = 0L;
        return entity;
    }

    public void update(int seats, int included, int additional, int releaseHours, int exhaustedDays,
                       LocalDate cycleStart, LocalDate cycleEnd, Instant now) {
        this.contractedSeats = seats;
        this.includedReplacements = included;
        this.additionalReplacements = additional;
        this.standardReleaseHours = releaseHours;
        this.exhaustedReleaseDays = exhaustedDays;
        this.cycleStartsOn = cycleStart;
        this.cycleEndsOn = cycleEnd;
        this.updatedAt = now;
    }

    public Long getOrganizationId() { return organizationId; }
    public Integer getContractedSeats() { return contractedSeats; }
    public Integer getIncludedReplacements() { return includedReplacements; }
    public Integer getAdditionalReplacements() { return additionalReplacements; }
    public Integer getStandardReleaseHours() { return standardReleaseHours; }
    public Integer getExhaustedReleaseDays() { return exhaustedReleaseDays; }
    public LocalDate getCycleStartsOn() { return cycleStartsOn; }
    public LocalDate getCycleEndsOn() { return cycleEndsOn; }
    public Long getVersion() { return version; }
}
