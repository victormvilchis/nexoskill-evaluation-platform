export interface RoleSummary {
  code: string
  name: string
  status: 'ACTIVE' | 'INACTIVE'
  protectedRole: boolean
  assignedUsers: number
  permissionCount: number
  updatedAt?: string
}

export interface RoleDetail extends RoleSummary {
  description?: string
  permissionCodes: string[]
  createdAt?: string
  version: number
}

export interface PermissionDescriptor {
  code: string
  name: string
  description?: string
  actionType: 'VIEW' | 'CREATE' | 'UPDATE' | 'DELETE' | 'STATUS' | 'SPECIAL'
  viewPermission: boolean
  required: boolean
}

export interface PermissionModule {
  code: string
  name: string
  permissions: PermissionDescriptor[]
}

export interface PermissionCatalog {
  modules: PermissionModule[]
}
