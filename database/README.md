# Base de datos Oracle

## 1. Crear el esquema

Conéctate a `FREEPDB1` o al PDB correspondiente como `SYSTEM` y ejecuta:

```sql
@setup/00_create_schema.sql
```

## 2. Migraciones

No ejecutes manualmente los archivos dentro de
`backend/src/main/resources/db/migration`.

Flyway los ejecuta automáticamente al iniciar Spring Boot con el usuario
`EVALUATION_APP`.

## 3. Cadena JDBC local

```text
jdbc:oracle:thin:@//localhost:1521/FREEPDB1
```

## 4. Verificación

```sql
SELECT TABLE_NAME
FROM USER_TABLES
ORDER BY TABLE_NAME;
```

Después de iniciar el backend deben aparecer las tablas de autenticación,
usuarios, permisos, sesiones y auditoría.
