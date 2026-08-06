-- Tercera revisión integral del esquema de roles y permisos.
-- Conserva APP_ROLE, APP_PERMISSION y APP_ROLE_PERMISSION como única fuente de autorización.

-- Ningún rol organizacional puede conservar permisos exclusivos del Administrador.
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

-- Se retiran autorizaciones heredadas que ya no corresponden a una operación
-- real de la plataforma y no deben aparecer como configurables.
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
            'QUESTION_REVIEW', 'QUESTION_PUBLISH', 'QUESTION_VERSION_VIEW',
            'STUDENT_CERTIFICATION_CATALOG_VIEW'
        )
   );

-- Una acción aislada sin el permiso Ver del módulo no tiene validez. Se eliminan
-- configuraciones incoherentes para que la autorización persistida coincida con
-- la navegación, las rutas y los endpoints realmente disponibles.
DELETE FROM APP_ROLE_PERMISSION action_grant
 WHERE action_grant.ROLE_ID IN (
       SELECT role_value.ROLE_ID
         FROM APP_ROLE role_value
        WHERE role_value.ROLE_CODE NOT IN ('ADMINISTRATOR', 'USER')
   )
   AND EXISTS (
       SELECT 1
         FROM APP_PERMISSION action_permission
        WHERE action_permission.PERMISSION_ID = action_grant.PERMISSION_ID
          AND action_permission.PERMISSION_CODE NOT IN (
              'DASHBOARD_VIEW', 'PROFILE_VIEW', 'USER_PANEL_VIEW', 'PASSWORD_CHANGE',
              'STUDENT_VIEW', 'TALENT_VIEW', 'CATALOG_VIEW', 'QUESTION_VIEW',
              'COLLECTION_VIEW', 'FORM_VIEW'
          )
          AND CASE
                  WHEN action_permission.PERMISSION_CODE LIKE 'TALENT\_%' ESCAPE '\' THEN 'TALENT_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'STUDENT\_CERTIFICATION\_%' ESCAPE '\' THEN 'STUDENT_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'STUDENT\_%' ESCAPE '\' THEN 'STUDENT_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'FORM\_%' ESCAPE '\' THEN 'FORM_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'COLLECTION\_%' ESCAPE '\' THEN 'COLLECTION_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'QUESTION\_%' ESCAPE '\' THEN 'QUESTION_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'GLOBAL\_CONTENT\_%' ESCAPE '\' THEN 'QUESTION_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'CATALOG\_%' ESCAPE '\' THEN 'CATALOG_VIEW'
                  WHEN action_permission.PERMISSION_CODE LIKE 'PROFILE\_%' ESCAPE '\' THEN 'PROFILE_VIEW'
              END IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
                FROM APP_ROLE_PERMISSION view_grant
                JOIN APP_PERMISSION view_permission
                  ON view_permission.PERMISSION_ID = view_grant.PERMISSION_ID
               WHERE view_grant.ROLE_ID = action_grant.ROLE_ID
                 AND view_permission.PERMISSION_CODE = CASE
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
          )
   );

-- Fuerza la reconstrucción inmediata de autoridades desde la configuración ya
-- persistida para todos los usuarios con roles organizacionales.
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
