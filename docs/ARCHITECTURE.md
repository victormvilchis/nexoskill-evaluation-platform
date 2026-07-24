# Arquitectura de la primera entrega

## Estilo

Monolito modular con arquitectura hexagonal.

La aplicación se distribuye como un solo proceso Spring Boot, pero cada
capacidad se organiza como un módulo con contratos internos claros.

## Capas

### Domain

Reglas y modelos independientes de Spring, HTTP, Oracle y JPA.

### Application

Casos de uso y puertos de salida.

### Infrastructure

Adaptadores de base de datos, criptografía, cookies, seguridad y configuración.

### Interfaces

Controladores REST y contratos HTTP.

## Regla de dependencias

```text
interfaces ──────┐
                 v
infrastructure -> application -> domain
```

El dominio no conoce infraestructura. Los casos de uso dependen de interfaces
de repositorio y puertos, no de Spring Data directamente.

## Módulos iniciales

### authentication

- Inicio de sesión.
- Cierre de sesión.
- Sesiones opacas.
- Hash de tokens.
- Protección de origen.
- Filtro de autenticación.

### users

- Usuario.
- Estado.
- Roles.
- Permisos.
- Persistencia Oracle.

### dashboard

- Construcción del panel de bienvenida.
- Diferenciación administrador/usuario.

### audit

- Puerto de auditoría.
- Persistencia de eventos funcionales.

### shared

- Configuración.
- manejo de errores.
- información común de solicitudes.

## Flujo del login

```text
React
  |
  | POST /api/v1/auth/login
  v
AuthenticationController
  |
  v
LoginService
  |---- UserRepository
  |---- PasswordHasher
  |---- SessionTokenGenerator
  |---- AuthSessionRepository
  |---- LoginAttemptPort
  `---- AuditLogPort
```

## Flujo de una solicitud protegida

```text
Cookie EVSESSION
  |
  v
SessionAuthenticationFilter
  |
  |-- SHA-256 del token
  |-- AUTH_SESSION activa y vigente
  |-- APP_USER activo
  `-- autoridades en SecurityContext
           |
           v
       Controller
```

## Despliegue local

```text
Apache XAMPP :80
├── /evaluaciones -> archivos React
├── /api          -> proxy Spring Boot :8080
└── /actuator     -> proxy Spring Boot :8080

Spring Boot :8080
└── Oracle :1521
```
