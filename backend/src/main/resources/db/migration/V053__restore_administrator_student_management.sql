-- El Administrador global puede consultar y gestionar colaboradores comerciales.
-- La organización efectiva continúa resolviéndose y validándose por recurso en backend.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT role_value.ROLE_ID, permission_value.PERMISSION_ID
  FROM APP_ROLE role_value
 CROSS JOIN APP_PERMISSION permission_value
 WHERE role_value.ROLE_CODE = 'ADMINISTRATOR'
   AND permission_value.PERMISSION_CODE LIKE 'STUDENT\_%' ESCAPE '\'
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION existing_value
        WHERE existing_value.ROLE_ID = role_value.ROLE_ID
          AND existing_value.PERMISSION_ID = permission_value.PERMISSION_ID
   );

-- Las sesiones se invalidan para reconstruir inmediatamente los permisos efectivos.
UPDATE AUTH_SESSION session_value
   SET STATUS = 'REVOKED',
       REVOKED_AT = SYSTIMESTAMP
 WHERE session_value.STATUS = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM APP_USER_ROLE user_role
         JOIN APP_ROLE role_value ON role_value.ROLE_ID = user_role.ROLE_ID
        WHERE user_role.USER_ID = session_value.USER_ID
          AND role_value.ROLE_CODE = 'ADMINISTRATOR'
   );

COMMIT;
