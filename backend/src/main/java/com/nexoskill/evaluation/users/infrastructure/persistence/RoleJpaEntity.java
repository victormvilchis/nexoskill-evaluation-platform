package com.nexoskill.evaluation.users.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "APP_ROLE")
public class RoleJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ROLE_ID")
	private Long id;

	@Column(name = "ROLE_CODE", nullable = false, unique = true, length = 50)
	private String code;

	@Column(name = "ROLE_NAME", nullable = false, length = 100)
	private String name;

	@Column(name = "STATUS", nullable = false, length = 20)
	private String status;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "APP_ROLE_PERMISSION", joinColumns = @JoinColumn(name = "ROLE_ID"), inverseJoinColumns = @JoinColumn(name = "PERMISSION_ID"))
	private Set<PermissionJpaEntity> permissions = new LinkedHashSet<>();

	protected RoleJpaEntity() {
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

	public String getStatus() {
		return status;
	}

	public Set<PermissionJpaEntity> getPermissions() {
		return permissions;
	}
}
