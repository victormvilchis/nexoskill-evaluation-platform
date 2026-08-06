-- Homologa la nomenclatura visible de Colaboradores y hace explícitos los
-- permisos particulares utilizados por importación y certificaciones.
MERGE INTO APP_PERMISSION target
USING (
    SELECT 'STUDENT_VIEW' code,
           'Consultar colaboradores' name,
           'STUDENTS' module_code,
           'Permite consultar el listado, detalle e información autorizada de los colaboradores.' description
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_UPDATE',
           'Modificar colaboradores',
           'STUDENTS',
           'Permite editar la información de los colaboradores.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_CREATE',
           'Crear colaboradores',
           'STUDENTS',
           'Permite crear colaboradores manualmente.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_DELETE',
           'Eliminar definitivamente colaboradores',
           'STUDENTS',
           'Permite eliminar física y definitivamente a un colaborador cuando las reglas lo permiten.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_STATUS_CHANGE',
           'Dar de baja colaboradores',
           'STUDENTS',
           'Permite dar de baja a un colaborador y cambiarlo al estado correspondiente.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_SESSION_MANAGE',
           'Administrar sesiones de colaboradores',
           'STUDENTS',
           'Permite consultar y revocar las sesiones activas de los colaboradores.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_IMPORT',
           'Importar colaboradores',
           'STUDENTS',
           'Permite previsualizar, confirmar y aplicar importaciones masivas de colaboradores, incluida la información de certificaciones contenida en el archivo.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_CERTIFICATION_MANAGE',
           'Gestionar certificaciones',
           'STUDENTS',
           'Permite modificar certificaciones, intentos, fechas, promedios, estatus y seguimientos de los colaboradores.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_CERTIFICATION_CATALOG_VIEW',
           'Consultar catálogos de certificaciones de colaboradores',
           'STUDENTS',
           'Permite consultar catálogos internos requeridos por el seguimiento de certificaciones.'
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_PASSWORD_RESET',
           'Restablecer contraseña de colaboradores',
           'STUDENTS',
           'Permite restablecer la contraseña de acceso de un colaborador cuando corresponda.'
      FROM DUAL
    UNION ALL
    SELECT 'PROFILE_VIEW',
           'Consultar perfil',
           'PROFILE',
           'Permite consultar la información del perfil propio.'
      FROM DUAL
    UNION ALL
    SELECT 'PROFILE_UPDATE',
           'Actualizar perfil propio',
           'PROFILE',
           'Permite modificar los datos personales autorizados del perfil propio.'
      FROM DUAL
    UNION ALL
    SELECT 'PASSWORD_CHANGE',
           'Cambiar contraseña',
           'PROFILE',
           'Permite cambiar la contraseña de la cuenta autenticada.'
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

-- El Administrador conserva acceso completo también sobre la gestión manual de
-- certificaciones, sin convertir su rol protegido en una configuración editable.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT role_value.ROLE_ID, permission_value.PERMISSION_ID
  FROM APP_ROLE role_value
 CROSS JOIN APP_PERMISSION permission_value
 WHERE role_value.ROLE_CODE = 'ADMINISTRATOR'
   AND permission_value.PERMISSION_CODE IN (
       'STUDENT_VIEW', 'STUDENT_CREATE', 'STUDENT_UPDATE', 'STUDENT_DELETE',
       'STUDENT_STATUS_CHANGE', 'STUDENT_SESSION_MANAGE', 'STUDENT_IMPORT',
       'STUDENT_CERTIFICATION_MANAGE', 'STUDENT_PASSWORD_RESET',
       'PROFILE_VIEW', 'PROFILE_UPDATE', 'PASSWORD_CHANGE'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_value
        WHERE current_value.ROLE_ID = role_value.ROLE_ID
          AND current_value.PERMISSION_ID = permission_value.PERMISSION_ID
   );

-- Fuerza que las sesiones del Administrador recuperen el permiso operativo que
-- anteriormente había sido retirado por una migración histórica.
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
