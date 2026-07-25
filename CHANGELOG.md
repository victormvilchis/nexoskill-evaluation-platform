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


## 0.2.0 - Parte 3: ciclo de vida administrativo de usuarios

### Agregado

- Consulta del detalle de usuario.
- Edición de nombre, correo y nombre visible.
- Edición de vigencia.
- Activación y suspensión de cuentas.
- Cambio de rol con invalidación de sesiones.
- Restablecimiento de contraseña temporal con BCrypt.
- Revocación de sesiones al suspender o restablecer credenciales.
- Protección para impedir la auto-suspensión de un administrador.
- Auditoría con valores anteriores y posteriores.
- Confirmaciones y mensajes de resultado en el frontend.

## 0.3.0 - Parte 2: edición, versionado y flujo editorial

### Agregado

- Edición de preguntas en borrador.
- Nueva versión automática al editar una pregunta publicada.
- Preservación de la última versión publicada mientras se revisa una nueva.
- Flujo editorial `DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED → ARCHIVED`.
- Retorno controlado a borrador desde revisión o aprobación.
- Duplicación de preguntas como nuevos borradores.
- Historial de versiones y resumen de cambios.
- Control de concurrencia mediante la versión JPA de la entidad.
- Permisos específicos de revisión, aprobación, publicación, duplicación e historial.
- Auditoría de todas las acciones editoriales.
