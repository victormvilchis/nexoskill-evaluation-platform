-- Permisos específicos para operaciones de catálogos y migración compatible
-- desde el permiso histórico CATALOG_MANAGE. No se modifican estructuras ni
-- relaciones de negocio existentes.
MERGE INTO APP_PERMISSION target
USING (
    SELECT 'CATALOG_CREATE' code,
           'Crear registros de catálogo' name,
           'CATALOGS' module_code,
           'Permite crear valores en los catálogos autorizados.' description
      FROM DUAL
    UNION ALL
    SELECT 'CATALOG_UPDATE',
           'Editar registros de catálogo',
           'CATALOGS',
           'Permite modificar los datos de valores de catálogo existentes.'
      FROM DUAL
    UNION ALL
    SELECT 'CATALOG_STATUS_CHANGE',
           'Activar e inactivar registros de catálogo',
           'CATALOGS',
           'Permite activar o inactivar valores conservando sus relaciones existentes.'
      FROM DUAL
    UNION ALL
    SELECT 'CATALOG_DELETE',
           'Eliminar definitivamente registros de catálogo',
           'CATALOGS',
           'Permite eliminar físicamente valores sin relaciones que deban conservarse.'
      FROM DUAL
) source
ON (target.PERMISSION_CODE = source.code)
WHEN MATCHED THEN UPDATE SET
    target.PERMISSION_NAME = source.name,
    target.MODULE_CODE = source.module_code,
    target.DESCRIPTION = source.description
WHEN NOT MATCHED THEN INSERT (
    PERMISSION_CODE, PERMISSION_NAME, MODULE_CODE, DESCRIPTION
) VALUES (
    source.code, source.name, source.module_code, source.description
);

-- Conserva el alcance actual: todo rol que administraba catálogos recibe las
-- cuatro acciones específicas. A partir de esta migración pueden retirarse de
-- forma independiente desde Roles.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT legacy_role.ROLE_ID, action_permission.PERMISSION_ID
  FROM (
        SELECT DISTINCT role_value.ROLE_ID
          FROM APP_ROLE role_value
          JOIN APP_ROLE_PERMISSION assigned
            ON assigned.ROLE_ID = role_value.ROLE_ID
          JOIN APP_PERMISSION legacy_permission
            ON legacy_permission.PERMISSION_ID = assigned.PERMISSION_ID
         WHERE legacy_permission.PERMISSION_CODE = 'CATALOG_MANAGE'
        UNION
        SELECT ROLE_ID
          FROM APP_ROLE
         WHERE ROLE_CODE = 'ADMINISTRATOR'
       ) legacy_role
 CROSS JOIN APP_PERMISSION action_permission
 WHERE action_permission.PERMISSION_CODE IN (
       'CATALOG_CREATE', 'CATALOG_UPDATE', 'CATALOG_STATUS_CHANGE', 'CATALOG_DELETE'
 )
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_value
        WHERE current_value.ROLE_ID = legacy_role.ROLE_ID
          AND current_value.PERMISSION_ID = action_permission.PERMISSION_ID
   );

-- Las sesiones almacenan la autorización resuelta. Se revocan únicamente las
-- sesiones de usuarios cuyos roles administran catálogos para que vuelvan a
-- autenticarse con la matriz específica.
UPDATE AUTH_SESSION session_value
   SET STATUS = 'REVOKED',
       REVOKED_AT = SYSTIMESTAMP
 WHERE session_value.STATUS = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM APP_USER_ROLE user_role
         JOIN APP_ROLE_PERMISSION assigned
           ON assigned.ROLE_ID = user_role.ROLE_ID
         JOIN APP_PERMISSION permission_value
           ON permission_value.PERMISSION_ID = assigned.PERMISSION_ID
        WHERE user_role.USER_ID = session_value.USER_ID
          AND permission_value.PERMISSION_CODE IN (
              'CATALOG_MANAGE', 'CATALOG_CREATE', 'CATALOG_UPDATE',
              'CATALOG_STATUS_CHANGE', 'CATALOG_DELETE'
          )
   );

COMMIT;
