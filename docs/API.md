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


## Banco de preguntas

### Consultar preguntas

```http
GET /api/v1/admin/questions?query=&status=DRAFT&typeCode=SINGLE_CHOICE&difficultyCode=BASIC&categoryPublicId=&page=0&size=20
```

Requiere `QUESTION_VIEW`.

### Crear pregunta

```http
POST /api/v1/admin/questions
```

```json
{
  "typeCode": "SINGLE_CHOICE",
  "difficultyCode": "BASIC",
  "categoryPublicId": "11111111-1111-1111-1111-111111111102",
  "statement": "¿Cuál es la palabra reservada para declarar una clase en Java?",
  "explanation": "Java utiliza class para declarar una clase.",
  "options": [
    { "text": "class", "correct": true },
    { "text": "object", "correct": false },
    { "text": "type", "correct": false }
  ]
}
```

La pregunta se crea en estado `DRAFT`. Requiere `QUESTION_CREATE`.

### Consultar detalle

```http
GET /api/v1/admin/questions/{publicId}
```

### Consultar catálogos

```http
GET /api/v1/admin/question-catalogs
```

### Crear categoría

```http
POST /api/v1/admin/question-catalogs/categories
```

```json
{
  "name": "Salesforce",
  "description": "Preguntas de administración y desarrollo Salesforce."
}
```

Requiere `QUESTION_CATEGORY_MANAGE`.
