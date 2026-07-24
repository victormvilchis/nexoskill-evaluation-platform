# Contratos API

## Login

`POST /api/v1/auth/login`

```json
{
  "email": "admin@nexoskill.local",
  "password": "Admin123!"
}
```

Respuesta:

```json
{
  "user": {
    "publicId": "uuid",
    "email": "admin@nexoskill.local",
    "firstName": "Administrador",
    "lastName": "NexoSkill",
    "displayName": "Administrador NexoSkill",
    "roles": ["ADMINISTRATOR"],
    "permissions": [
      "DASHBOARD_VIEW",
      "ADMIN_PANEL_VIEW",
      "USER_PANEL_VIEW",
      "PROFILE_VIEW",
      "PASSWORD_CHANGE"
    ],
    "lastLoginAt": "2026-07-24T18:00:00Z"
  }
}
```

La respuesta agrega una cookie `EVSESSION` con:

- `HttpOnly`
- `SameSite=Strict`
- `Path=/`
- `Secure` en producción

## Logout

`POST /api/v1/auth/logout`

Respuesta: `204 No Content`.

## Usuario actual

`GET /api/v1/users/me`

## Panel de bienvenida

`GET /api/v1/dashboard/welcome`

## Errores

```json
{
  "timestamp": "2026-07-24T18:00:00Z",
  "status": 401,
  "code": "AUTHENTICATION_FAILED",
  "message": "El correo o la contraseña son incorrectos.",
  "path": "/api/v1/auth/login"
}
```


## Administración del ciclo de vida de usuarios

```http
GET  /api/v1/admin/users/{publicId}
PUT  /api/v1/admin/users/{publicId}
PUT  /api/v1/admin/users/{publicId}/access
PUT  /api/v1/admin/users/{publicId}/role
POST /api/v1/admin/users/{publicId}/activate
POST /api/v1/admin/users/{publicId}/suspend
POST /api/v1/admin/users/{publicId}/reset-password
```

Las operaciones requieren permisos específicos. La suspensión, el cambio de rol
y el restablecimiento de contraseña invalidan las sesiones activas del usuario.
