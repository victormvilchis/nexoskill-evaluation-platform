-- Segunda revisión integral del módulo de roles.
-- Reutiliza APP_ROLE, APP_PERMISSION y APP_ROLE_PERMISSION; no crea un sistema paralelo.

-- Los módulos globales y sus acciones administrativas pertenecen exclusivamente
-- al Administrador. Se retiran de Gestor, Supervisor y roles personalizados.
DELETE FROM APP_ROLE_PERMISSION role_permission
 WHERE role_permission.ROLE_ID IN (
       SELECT role_value.ROLE_ID
         FROM APP_ROLE role_value
        WHERE role_value.ROLE_CODE NOT IN ('ADMINISTRATOR', 'USER')
   )
   AND role_permission.PERMISSION_ID IN (
       SELECT permission_value.PERMISSION_ID
         FROM APP_PERMISSION permission_value
        WHERE permission_value.PERMISSION_CODE IN (
            'ROLE_MANAGE', 'ADMIN_PANEL_VIEW', 'TENANT_CONTEXT_SELECT',
            'USER_VIEW', 'USER_CREATE', 'USER_UPDATE', 'USER_STATUS_CHANGE',
            'USER_PASSWORD_RESET', 'USER_ACCESS_MANAGE', 'USER_ROLE_ASSIGN',
            'ORGANIZATION_VIEW', 'ORGANIZATION_CREATE', 'ORGANIZATION_UPDATE',
            'ORGANIZATION_STATUS_CHANGE', 'ORGANIZATION_LICENSE_VIEW', 'ORGANIZATION_LICENSE_UPDATE',
            'GLOBAL_CONTENT_DISTRIBUTE', 'GLOBAL_CONTENT_PROMOTE', 'GLOBAL_CONTENT_PUBLISH',
            'GLOBAL_CONTENT_REVIEW', 'GLOBAL_CONTENT_SYNCHRONIZE', 'GLOBAL_CONTENT_VERSION_MANAGE'
        )
   );

-- Los accesos básicos son inherentes a toda cuenta interna activa y no dependen
-- de una selección manual en la matriz.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT role_value.ROLE_ID, permission_value.PERMISSION_ID
  FROM APP_ROLE role_value
 CROSS JOIN APP_PERMISSION permission_value
 WHERE role_value.ROLE_CODE NOT IN ('ADMINISTRATOR', 'USER')
   AND permission_value.PERMISSION_CODE IN (
       'DASHBOARD_VIEW', 'USER_PANEL_VIEW', 'PROFILE_VIEW', 'PASSWORD_CHANGE'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_value
        WHERE current_value.ROLE_ID = role_value.ROLE_ID
          AND current_value.PERMISSION_ID = permission_value.PERMISSION_ID
   );

-- Repara configuraciones históricas con acciones aisladas: toda acción conserva
-- el permiso Ver del módulo correspondiente.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT DISTINCT current_value.ROLE_ID, view_permission.PERMISSION_ID
  FROM APP_ROLE_PERMISSION current_value
  JOIN APP_ROLE role_value
    ON role_value.ROLE_ID = current_value.ROLE_ID
  JOIN APP_PERMISSION action_permission
    ON action_permission.PERMISSION_ID = current_value.PERMISSION_ID
  JOIN APP_PERMISSION view_permission
    ON view_permission.PERMISSION_CODE =
       CASE
           WHEN action_permission.PERMISSION_CODE LIKE 'TALENT\_%' ESCAPE '\' THEN 'TALENT_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'STUDENT\_CERTIFICATION\_%' ESCAPE '\' THEN 'STUDENT_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'STUDENT\_%' ESCAPE '\' THEN 'STUDENT_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'FORM\_%' ESCAPE '\' THEN 'FORM_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'COLLECTION\_%' ESCAPE '\' THEN 'COLLECTION_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'QUESTION\_%' ESCAPE '\' THEN 'QUESTION_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'GLOBAL\_CONTENT\_%' ESCAPE '\' THEN 'QUESTION_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'CATALOG\_%' ESCAPE '\' THEN 'CATALOG_VIEW'
           WHEN action_permission.PERMISSION_CODE LIKE 'PROFILE\_%' ESCAPE '\' THEN 'PROFILE_VIEW'
       END
 WHERE role_value.ROLE_CODE NOT IN ('ADMINISTRATOR', 'USER')
   AND action_permission.PERMISSION_CODE NOT IN (
       'TALENT_VIEW', 'STUDENT_VIEW', 'FORM_VIEW', 'COLLECTION_VIEW',
       'QUESTION_VIEW', 'CATALOG_VIEW', 'PROFILE_VIEW'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION existing_value
        WHERE existing_value.ROLE_ID = current_value.ROLE_ID
          AND existing_value.PERMISSION_ID = view_permission.PERMISSION_ID
   );

-- Las sesiones se revocan para que la navegación, las rutas y las acciones
-- recuperen inmediatamente el alcance organizacional corregido.
UPDATE AUTH_SESSION session_value
   SET STATUS = 'REVOKED',
       REVOKED_AT = SYSTIMESTAMP
 WHERE session_value.STATUS = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM APP_USER_ROLE user_role
         JOIN APP_ROLE role_value ON role_value.ROLE_ID = user_role.ROLE_ID
        WHERE user_role.USER_ID = session_value.USER_ID
          AND role_value.ROLE_CODE NOT IN ('ADMINISTRATOR', 'USER')
   );

COMMIT;
