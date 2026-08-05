-- Administración centralizada de roles sobre el modelo de seguridad existente.
-- No crea un sistema paralelo: reutiliza APP_ROLE, APP_PERMISSION y APP_ROLE_PERMISSION.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'APP_ROLE'
       AND COLUMN_NAME = 'UPDATED_AT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE APP_ROLE ADD (UPDATED_AT TIMESTAMP WITH TIME ZONE)';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'APP_ROLE'
       AND COLUMN_NAME = 'VERSION_NO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE APP_ROLE ADD (VERSION_NO NUMBER DEFAULT 0 NOT NULL)';
    END IF;
END;
/

UPDATE APP_ROLE
   SET UPDATED_AT = NVL(UPDATED_AT, CREATED_AT)
 WHERE UPDATED_AT IS NULL;

DECLARE
    v_duplicate_groups NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_duplicate_groups
      FROM (
            SELECT UPPER(TRIM(ROLE_NAME)) NORMALIZED_NAME
              FROM APP_ROLE
             GROUP BY UPPER(TRIM(ROLE_NAME))
            HAVING COUNT(*) > 1
      );

    IF v_duplicate_groups > 0 THEN
        RAISE_APPLICATION_ERROR(-20057,
            'Existen roles con nombres duplicados ignorando mayusculas, minusculas y espacios. Corrige los datos antes de continuar.');
    END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_APP_ROLE_NAME_CI_R1 ON APP_ROLE (UPPER(TRIM(ROLE_NAME)))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE NOT IN (-955, -1408) THEN
            RAISE;
        END IF;
END;
/

MERGE INTO APP_PERMISSION target
USING (
    SELECT 'ROLE_MANAGE' code,
           'Administrar roles y permisos' name,
           'ROLE_MANAGEMENT' module_code,
           'Permite consultar y administrar roles configurables y su matriz de permisos.' description
      FROM DUAL
    UNION ALL
    SELECT 'STUDENT_IMPORT',
           'Importar colaboradores',
           'STUDENTS',
           'Permite previsualizar, confirmar y descartar importaciones masivas de colaboradores.'
      FROM DUAL
    UNION ALL
    SELECT 'TALENT_VIEW',
           'Ver Talent Bank',
           'TALENT_BANK',
           'Permite consultar personas y documentos autorizados de Talent Bank.'
      FROM DUAL
    UNION ALL
    SELECT 'TALENT_CREATE',
           'Agregar talentos',
           'TALENT_BANK',
           'Permite registrar personas nuevas en Talent Bank.'
      FROM DUAL
    UNION ALL
    SELECT 'TALENT_UPDATE',
           'Editar talentos',
           'TALENT_BANK',
           'Permite actualizar personas y documentos de Talent Bank.'
      FROM DUAL
    UNION ALL
    SELECT 'TALENT_DELETE',
           'Eliminar talentos',
           'TALENT_BANK',
           'Permite eliminar definitivamente registros de Talent Bank cuando las reglas lo permiten.'
      FROM DUAL
    UNION ALL
    SELECT 'TALENT_CONVERT',
           'Convertir a colaborador',
           'TALENT_BANK',
           'Permite convertir una persona de Talent Bank en colaborador.'
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

-- El Administrador conserva acceso completo al nuevo módulo y a las acciones particulares.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT role_value.ROLE_ID, permission_value.PERMISSION_ID
  FROM APP_ROLE role_value
 CROSS JOIN APP_PERMISSION permission_value
 WHERE role_value.ROLE_CODE = 'ADMINISTRATOR'
   AND permission_value.PERMISSION_CODE IN ('ROLE_MANAGE', 'STUDENT_IMPORT', 'TALENT_VIEW', 'TALENT_CREATE', 'TALENT_UPDATE', 'TALENT_DELETE', 'TALENT_CONVERT')
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_value
        WHERE current_value.ROLE_ID = role_value.ROLE_ID
          AND current_value.PERMISSION_ID = permission_value.PERMISSION_ID
   );

-- Conserva el comportamiento existente de Gestor y Supervisor al separar Talent Bank
-- de Colaboradores. Cada permiso nuevo se deriva de la autorización equivalente previa.
INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT role_value.ROLE_ID, new_permission.PERMISSION_ID
  FROM APP_ROLE role_value
  JOIN APP_ROLE_PERMISSION old_grant ON old_grant.ROLE_ID = role_value.ROLE_ID
  JOIN APP_PERMISSION old_permission ON old_permission.PERMISSION_ID = old_grant.PERMISSION_ID
  JOIN APP_PERMISSION new_permission ON new_permission.PERMISSION_CODE =
       CASE old_permission.PERMISSION_CODE
           WHEN 'STUDENT_VIEW' THEN 'TALENT_VIEW'
           WHEN 'STUDENT_CREATE' THEN 'TALENT_CREATE'
           WHEN 'STUDENT_UPDATE' THEN 'TALENT_UPDATE'
           WHEN 'STUDENT_DELETE' THEN 'TALENT_DELETE'
       END
 WHERE role_value.ROLE_CODE IN ('MANAGER', 'SUPERVISOR')
   AND old_permission.PERMISSION_CODE IN ('STUDENT_VIEW', 'STUDENT_CREATE', 'STUDENT_UPDATE', 'STUDENT_DELETE')
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_value
        WHERE current_value.ROLE_ID = role_value.ROLE_ID
          AND current_value.PERMISSION_ID = new_permission.PERMISSION_ID
   );

INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT role_value.ROLE_ID, permission_value.PERMISSION_ID
  FROM APP_ROLE role_value
 CROSS JOIN APP_PERMISSION permission_value
 WHERE role_value.ROLE_CODE IN ('MANAGER', 'SUPERVISOR')
   AND permission_value.PERMISSION_CODE IN ('STUDENT_IMPORT', 'TALENT_CONVERT')
   AND EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION create_grant
         JOIN APP_PERMISSION create_permission ON create_permission.PERMISSION_ID = create_grant.PERMISSION_ID
        WHERE create_grant.ROLE_ID = role_value.ROLE_ID
          AND create_permission.PERMISSION_CODE = 'STUDENT_CREATE'
   )
   AND EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION update_grant
         JOIN APP_PERMISSION update_permission ON update_permission.PERMISSION_ID = update_grant.PERMISSION_ID
        WHERE update_grant.ROLE_ID = role_value.ROLE_ID
          AND update_permission.PERMISSION_CODE = 'STUDENT_UPDATE'
   )
   AND NOT EXISTS (
       SELECT 1
         FROM APP_ROLE_PERMISSION current_value
        WHERE current_value.ROLE_ID = role_value.ROLE_ID
          AND current_value.PERMISSION_ID = permission_value.PERMISSION_ID
   );

COMMIT;
