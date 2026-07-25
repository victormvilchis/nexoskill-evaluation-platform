# Backend Spring Boot

## Requisitos

- Java 21.
- Maven 3.6.3 o superior.
- Oracle Database accesible.
- Esquema `EVALUATION_APP`.

## Ejecución local

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Variables principales

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
SERVER_PORT
APP_COOKIE_SECURE
APP_SESSION_DURATION
APP_SEED_ENABLED
APP_SEED_ADMIN_EMAIL
APP_SEED_ADMIN_PASSWORD
APP_SEED_USER_EMAIL
APP_SEED_USER_PASSWORD
```

## Arquitectura

```text
módulo/
├── domain
├── application
├── infrastructure
└── interfaces
```

Los módulos iniciales son:

- `authentication`
- `users`
- `dashboard`
- `audit`
- `shared`
- `questionbank`

## Sesión

El backend genera un token opaco de 384 bits. El navegador recibe el token en
una cookie `HttpOnly`. Oracle únicamente almacena su hash SHA-256.

No se utiliza `localStorage`, JWT ni la sesión HTTP tradicional de Tomcat.

## Migraciones

Flyway aplica automáticamente:

- `V001__create_initial_schema.sql`
- `V002__insert_security_catalogs.sql`
- `V003__create_initial_indexes.sql`


A partir del banco de preguntas también se aplican:

- `V009__create_question_catalogs.sql`
- `V010__create_question_bank.sql`
- `V011__add_question_management_permissions.sql`
- `V012__insert_initial_question_catalogs.sql`
