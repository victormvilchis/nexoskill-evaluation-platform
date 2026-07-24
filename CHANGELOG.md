# Changelog

## 0.2.0 - Parte 1

### Agregado

- Consulta paginada de usuarios para administradores.
- Alta de usuarios con rol inicial y contraseña temporal.
- Vigencia de acceso con fecha inicial y vencimiento opcional.
- Permisos `USER_VIEW`, `USER_CREATE` y `USER_ACCESS_MANAGE`.
- Validación de vigencia durante el inicio de sesión y la sesión activa.
- Pantallas React para consultar y crear usuarios.
- Migraciones Flyway V004, V005 y V006.

### Seguridad

- La contraseña temporal se cifra con BCrypt antes de persistirse.
- Solo usuarios con permisos administrativos pueden utilizar los endpoints.
- La creación de usuarios genera el evento de auditoría `USER_CREATED`.

## 0.2.0 - Parte 2: expiración de acceso

### Agregado

- Expulsión automática del usuario al llegar `expiresAt`.
- Respuesta `ACCESS_EXPIRED` al intentar iniciar sesión con una cuenta vencida.
- Revocación de la sesión y eliminación de cookie al detectar vigencia vencida.
- Temporizador y verificación periódica de sesión en React.
- Estado efectivo `EXPIRED` actualizado en tiempo real en la tabla administrativa.
- La sesión nunca puede durar más que la vigencia del usuario.
