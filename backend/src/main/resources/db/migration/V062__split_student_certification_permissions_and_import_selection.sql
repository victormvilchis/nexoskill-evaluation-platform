-- Separa la consulta de la gestión manual de certificaciones de colaboradores.
MERGE INTO APP_PERMISSION target
USING (
    SELECT 'STUDENT_CERTIFICATION_VIEW' code,
           'Consultar certificaciones' name,
           'STUDENTS' module_code,
           'Permite consultar las certificaciones, estatus, fechas, resultados, intentos, vencimientos y seguimientos de los colaboradores sin modificarlos.' description
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_CERTIFICATION_MANAGE',
           'Gestionar certificaciones',
           'STUDENTS',
           'Permite consultar y modificar certificaciones, intentos, fechas, promedios, estatus y seguimientos de los colaboradores.'
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

-- Todo rol que ya gestionaba certificaciones conserva la gestión y recibe de
-- forma explícita el permiso de consulta requerido por esa operación.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT manager_grant.ROLE_ID, view_permission.PERMISSION_ID
  FROM APP_ROLE_PERMISSION manager_grant
  JOIN APP_PERMISSION manager_permission
    ON manager_permission.PERMISSION_ID = manager_grant.PERMISSION_ID
 CROSS JOIN APP_PERMISSION view_permission
 WHERE manager_permission.PERMISSION_CODE = 'STUDENT_CERTIFICATION_MANAGE'
   AND view_permission.PERMISSION_CODE = 'STUDENT_CERTIFICATION_VIEW'
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_grant
        WHERE current_grant.ROLE_ID = manager_grant.ROLE_ID
          AND current_grant.PERMISSION_ID = view_permission.PERMISSION_ID
   );

-- La consulta de certificaciones pertenece al módulo Colaboradores y requiere
-- también el permiso de consulta del colaborador.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT DISTINCT certification_grant.ROLE_ID, student_view.PERMISSION_ID
  FROM APP_ROLE_PERMISSION certification_grant
  JOIN APP_PERMISSION certification_permission
    ON certification_permission.PERMISSION_ID = certification_grant.PERMISSION_ID
 CROSS JOIN APP_PERMISSION student_view
 WHERE certification_permission.PERMISSION_CODE IN (
       'STUDENT_CERTIFICATION_VIEW', 'STUDENT_CERTIFICATION_MANAGE'
   )
   AND student_view.PERMISSION_CODE = 'STUDENT_VIEW'
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_grant
        WHERE current_grant.ROLE_ID = certification_grant.ROLE_ID
          AND current_grant.PERMISSION_ID = student_view.PERMISSION_ID
   );

-- El Administrador protegido conserva acceso completo a consulta y gestión.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT administrator.ROLE_ID, permission_value.PERMISSION_ID
  FROM APP_ROLE administrator
 CROSS JOIN APP_PERMISSION permission_value
 WHERE administrator.ROLE_CODE = 'ADMINISTRATOR'
   AND permission_value.PERMISSION_CODE IN (
       'STUDENT_VIEW', 'STUDENT_CERTIFICATION_VIEW', 'STUDENT_CERTIFICATION_MANAGE'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_grant
        WHERE current_grant.ROLE_ID = administrator.ROLE_ID
          AND current_grant.PERMISSION_ID = permission_value.PERMISSION_ID
   );

-- Las autoridades se reconstruyen desde la configuración persistida en el
-- siguiente inicio de sesión, evitando conservar permisos anteriores en sesión.
UPDATE AUTH_SESSION session_value
   SET STATUS = 'REVOKED',
       REVOKED_AT = SYSTIMESTAMP
 WHERE session_value.STATUS = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM APP_USER_ROLE user_role
         JOIN APP_ROLE_PERMISSION role_permission
           ON role_permission.ROLE_ID = user_role.ROLE_ID
         JOIN APP_PERMISSION permission_value
           ON permission_value.PERMISSION_ID = role_permission.PERMISSION_ID
        WHERE user_role.USER_ID = session_value.USER_ID
          AND permission_value.PERMISSION_CODE IN (
              'STUDENT_CERTIFICATION_VIEW', 'STUDENT_CERTIFICATION_MANAGE'
          )
   );

COMMIT;
