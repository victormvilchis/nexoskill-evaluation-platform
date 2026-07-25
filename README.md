# NexoSkill Evaluation Platform

Base funcional de una plataforma web de evaluaciones con:

- Backend Java 21 + Spring Boot 3.5.
- Arquitectura monolítica modular con separación hexagonal.
- Oracle Database.
- Frontend React + TypeScript + Vite.
- Inicio y cierre de sesión mediante cookie `HttpOnly`.
- Panel de bienvenida diferenciado para administrador y usuario.
- Roles, permisos, sesiones, intentos de acceso y auditoría inicial.
- Despliegue del frontend en Apache de XAMPP.
- Backend ejecutado directamente con Spring Boot, sin Docker.

## Importante sobre XAMPP y Oracle

XAMPP se utiliza únicamente para servir el frontend compilado mediante Apache y
para hacer proxy de `/api` hacia Spring Boot. XAMPP no instala Oracle Database.

Se necesita una instalación independiente de Oracle Database, por ejemplo:

- Oracle Database Free para desarrollo.
- Una instancia Oracle 19c o superior ya disponible.

## Estructura

```text
nexoskill-evaluation-platform/
├── backend/
├── frontend/
├── database/
├── xampp/
├── docs/
└── scripts/
```

## Credenciales locales iniciales

Al iniciar el backend con el perfil `local`, si la tabla de usuarios está vacía,
se crean:

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | admin@nexoskill.local | Admin123! |
| Usuario | usuario@nexoskill.local | Usuario123! |

Estas contraseñas son exclusivamente para desarrollo local. Para otro ambiente,
se deben establecer las variables:

```text
APP_SEED_ADMIN_EMAIL
APP_SEED_ADMIN_PASSWORD
APP_SEED_USER_EMAIL
APP_SEED_USER_PASSWORD
```

## Arranque resumido

1. Crear el esquema Oracle con `database/setup/00_create_schema.sql`.
2. Configurar las credenciales en `backend/src/main/resources/application-local.yml`
   o mediante variables de entorno.
3. Ejecutar el backend:

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

4. Para desarrollo del frontend:

```powershell
cd frontend
npm install
npm run dev
```

5. Abrir `http://localhost:5173/evaluaciones/`.

6. Para servirlo con XAMPP, seguir `xampp/README-XAMPP.md`.

## Endpoints iniciales

```text
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET  /api/v1/users/me
GET  /api/v1/dashboard/welcome
GET  /actuator/health
```

## Seguridad aplicada

- Contraseñas con BCrypt.
- Token de sesión opaco y aleatorio.
- Solo se almacena SHA-256 del token en Oracle.
- Cookie `HttpOnly`, `SameSite=Strict` y configurable como `Secure`.
- Sesiones revocables y con vencimiento.
- Límite y bloqueo temporal por intentos fallidos.
- Comprobación de origen para operaciones que modifican datos.
- Autorización por roles y permisos en backend.
- Respuestas de autenticación genéricas para evitar enumeración de usuarios.
- Auditoría funcional de login, logout y acceso al panel.

## Compilación

Backend:

```powershell
cd backend
mvn clean package
java -jar target/evaluation-platform-backend-0.1.0.jar --spring.profiles.active=local
```

Frontend:

```powershell
cd frontend
npm install
npm run build
```

El contenido generado en `frontend/dist` se copia a
`C:\xampp\htdocs\evaluaciones`.

## Alcance no incluido

- Flujo editorial completo de publicación y aprobación de preguntas.
- Evaluaciones.
- Calificación.
- Resultados.
- Pagos.
- Suscripciones.
- Archivos.
- Monitoreo avanzado.


## Actualización 0.2.0 — Administración de usuarios, parte 1

Esta versión agrega:

- Consulta paginada de usuarios.
- Creación de usuarios desde el panel administrativo.
- Rol inicial y contraseña temporal.
- Inicio y vencimiento de acceso.
- Validación de vigencia durante la autenticación.

Endpoints agregados:

```text
GET  /api/v1/admin/users
POST /api/v1/admin/users
GET  /api/v1/admin/roles
```


## Actualización 0.3.0 — Banco de preguntas, parte 1

Esta entrega agrega:

- Catálogos de tipos, dificultades y categorías.
- Listado administrativo de preguntas con filtros y paginación.
- Alta de preguntas de opción única, opción múltiple y verdadero/falso.
- Versionado inicial del contenido y opciones.
- Estado inicial `DRAFT`.
- Alta de categorías desde el panel.
- Auditoría y autorización por permisos.

Endpoints principales:

```text
GET  /api/v1/admin/questions
POST /api/v1/admin/questions
GET  /api/v1/admin/questions/{publicId}
GET  /api/v1/admin/question-catalogs
POST /api/v1/admin/question-catalogs/categories
```
