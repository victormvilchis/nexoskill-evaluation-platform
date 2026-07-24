package com.nexoskill.evaluation.users.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "APP_PERMISSION")
public class PermissionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PERMISSION_ID")
    private Long id;

    @Column(name = "PERMISSION_CODE", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "PERMISSION_NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "MODULE_CODE", nullable = false, length = 50)
    private String moduleCode;

    protected PermissionJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
