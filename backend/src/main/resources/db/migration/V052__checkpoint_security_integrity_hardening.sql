-- Checkpoint integral: aislamiento de archivos de preguntas, idempotencia persistente de
-- importaciones y corrección de permisos GLOBAL sobre colaboradores comerciales.
-- Conserva información histórica y tolera objetos creados parcialmente.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'QUESTION_MEDIA'
       AND COLUMN_NAME = 'CONTENT_SCOPE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUESTION_MEDIA ADD (CONTENT_SCOPE VARCHAR2(20 CHAR))';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'QUESTION_MEDIA'
       AND COLUMN_NAME = 'OWNER_ORGANIZATION_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUESTION_MEDIA ADD (OWNER_ORGANIZATION_ID NUMBER)';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
       AND COLUMN_NAME = 'CONFIRMATION_TOKEN';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_IMPORT_RECEIPT ADD (CONFIRMATION_TOKEN VARCHAR2(36 CHAR))';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
       AND COLUMN_NAME = 'FAILURE_CODE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_IMPORT_RECEIPT ADD (FAILURE_CODE VARCHAR2(80 CHAR))';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
       AND COLUMN_NAME = 'FAILED_AT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_IMPORT_RECEIPT ADD (FAILED_AT TIMESTAMP WITH TIME ZONE)';
    END IF;
END;
/

DECLARE
    v_global_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_global_count
      FROM ORGANIZATION
     WHERE ORGANIZATION_CODE = 'GLOBAL';
    IF v_global_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20060,
            'Debe existir exactamente una organización GLOBAL antes de ejecutar V052.');
    END IF;
END;
/

-- Resuelve primero la propiedad de archivos ya vinculados a preguntas. Si un mismo archivo
-- histórico fue compartido entre varias organizaciones o por contenido GLOBAL, se conserva
-- bajo GLOBAL para no adjudicarlo arbitrariamente a una organización comercial.
MERGE INTO QUESTION_MEDIA target
USING (
    SELECT relation.MEDIA_ID,
           CASE
               WHEN MAX(CASE WHEN relation.CONTENT_SCOPE = 'GLOBAL' THEN 1 ELSE 0 END) = 1
                    OR COUNT(DISTINCT relation.OWNER_ORGANIZATION_ID) > 1
               THEN 'GLOBAL'
               ELSE 'ORGANIZATION'
           END CONTENT_SCOPE,
           CASE
               WHEN MAX(CASE WHEN relation.CONTENT_SCOPE = 'GLOBAL' THEN 1 ELSE 0 END) = 1
                    OR COUNT(DISTINCT relation.OWNER_ORGANIZATION_ID) > 1
               THEN (SELECT ORGANIZATION_ID FROM ORGANIZATION WHERE ORGANIZATION_CODE = 'GLOBAL')
               ELSE MAX(relation.OWNER_ORGANIZATION_ID)
           END OWNER_ORGANIZATION_ID
      FROM (
            SELECT q.PROMPT_MEDIA_ID MEDIA_ID, q.CONTENT_SCOPE, q.OWNER_ORGANIZATION_ID
              FROM QUESTION q
             WHERE q.PROMPT_MEDIA_ID IS NOT NULL
            UNION ALL
            SELECT option_value.MEDIA_ID, q.CONTENT_SCOPE, q.OWNER_ORGANIZATION_ID
              FROM QUESTION_OPTION option_value
              JOIN QUESTION q ON q.QUESTION_ID = option_value.QUESTION_ID
             WHERE option_value.MEDIA_ID IS NOT NULL
            UNION ALL
            SELECT option_value.MATCH_MEDIA_ID, q.CONTENT_SCOPE, q.OWNER_ORGANIZATION_ID
              FROM QUESTION_OPTION option_value
              JOIN QUESTION q ON q.QUESTION_ID = option_value.QUESTION_ID
             WHERE option_value.MATCH_MEDIA_ID IS NOT NULL
      ) relation
     GROUP BY relation.MEDIA_ID
) source
ON (target.MEDIA_ID = source.MEDIA_ID)
WHEN MATCHED THEN UPDATE SET
    target.CONTENT_SCOPE = source.CONTENT_SCOPE,
    target.OWNER_ORGANIZATION_ID = source.OWNER_ORGANIZATION_ID
WHERE target.CONTENT_SCOPE IS NULL OR target.OWNER_ORGANIZATION_ID IS NULL;

-- Para archivos todavía no vinculados se usa la única membresía comercial activa del creador.
-- Si el creador no tiene una membresía comercial inequívoca, el archivo queda bajo GLOBAL.
MERGE INTO QUESTION_MEDIA target
USING (
    SELECT media.MEDIA_ID,
           CASE
               WHEN COUNT(DISTINCT CASE
                       WHEN organization_value.ORGANIZATION_TYPE = 'CUSTOMER'
                       THEN membership.ORGANIZATION_ID
                   END) = 1
               THEN 'ORGANIZATION'
               ELSE 'GLOBAL'
           END CONTENT_SCOPE,
           CASE
               WHEN COUNT(DISTINCT CASE
                       WHEN organization_value.ORGANIZATION_TYPE = 'CUSTOMER'
                       THEN membership.ORGANIZATION_ID
                   END) = 1
               THEN MAX(CASE
                       WHEN organization_value.ORGANIZATION_TYPE = 'CUSTOMER'
                       THEN membership.ORGANIZATION_ID
                   END)
               ELSE global_org.ORGANIZATION_ID
           END OWNER_ORGANIZATION_ID
      FROM QUESTION_MEDIA media
      LEFT JOIN APP_USER_ORGANIZATION membership
        ON membership.USER_ID = media.CREATED_BY
       AND membership.STATUS = 'ACTIVE'
      LEFT JOIN ORGANIZATION organization_value
        ON organization_value.ORGANIZATION_ID = membership.ORGANIZATION_ID
      CROSS JOIN (
          SELECT ORGANIZATION_ID
            FROM ORGANIZATION
           WHERE ORGANIZATION_CODE = 'GLOBAL'
      ) global_org
     WHERE media.CONTENT_SCOPE IS NULL OR media.OWNER_ORGANIZATION_ID IS NULL
     GROUP BY media.MEDIA_ID, global_org.ORGANIZATION_ID
) source
ON (target.MEDIA_ID = source.MEDIA_ID)
WHEN MATCHED THEN UPDATE SET
    target.CONTENT_SCOPE = source.CONTENT_SCOPE,
    target.OWNER_ORGANIZATION_ID = source.OWNER_ORGANIZATION_ID;

DECLARE
    v_missing NUMBER;
    v_nullable USER_TAB_COLUMNS.NULLABLE%TYPE;
BEGIN
    SELECT COUNT(*) INTO v_missing
      FROM QUESTION_MEDIA
     WHERE CONTENT_SCOPE IS NULL OR OWNER_ORGANIZATION_ID IS NULL;
    IF v_missing > 0 THEN
        RAISE_APPLICATION_ERROR(-20061,
            'No fue posible determinar la organización propietaria de todos los archivos de preguntas.');
    END IF;

    SELECT NULLABLE INTO v_nullable
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'QUESTION_MEDIA'
       AND COLUMN_NAME = 'CONTENT_SCOPE';
    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUESTION_MEDIA MODIFY (CONTENT_SCOPE NOT NULL)';
    END IF;

    SELECT NULLABLE INTO v_nullable
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'QUESTION_MEDIA'
       AND COLUMN_NAME = 'OWNER_ORGANIZATION_ID';
    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUESTION_MEDIA MODIFY (OWNER_ORGANIZATION_ID NOT NULL)';
    END IF;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_named NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'QUESTION_MEDIA'
       AND CONSTRAINT_TYPE = 'C'
       AND REGEXP_REPLACE(UPPER(SEARCH_CONDITION_VC), '[^A-Z0-9_,()]', '')
           IN ('CONTENT_SCOPEIN(GLOBAL,ORGANIZATION)', 'CONTENT_SCOPEIN(ORGANIZATION,GLOBAL)');

    SELECT COUNT(*) INTO v_named
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'CK_QUESTION_MEDIA_SCOPE';

    IF v_equivalent = 0 AND v_named > 0 THEN
        RAISE_APPLICATION_ERROR(-20062,
            'CK_QUESTION_MEDIA_SCOPE ya existe con una definición distinta.');
    ELSIF v_equivalent = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE QUESTION_MEDIA ADD CONSTRAINT CK_QUESTION_MEDIA_SCOPE CHECK (CONTENT_SCOPE IN ('GLOBAL','ORGANIZATION'))]';
    END IF;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_named NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM (
            SELECT constraint_value.CONSTRAINT_NAME,
                   referenced.TABLE_NAME REFERENCED_TABLE,
                   LISTAGG(column_value.COLUMN_NAME, ',')
                       WITHIN GROUP (ORDER BY column_value.POSITION) COLUMN_LIST
              FROM USER_CONSTRAINTS constraint_value
              JOIN USER_CONS_COLUMNS column_value
                ON column_value.CONSTRAINT_NAME = constraint_value.CONSTRAINT_NAME
              JOIN USER_CONSTRAINTS referenced
                ON referenced.CONSTRAINT_NAME = constraint_value.R_CONSTRAINT_NAME
             WHERE constraint_value.TABLE_NAME = 'QUESTION_MEDIA'
               AND constraint_value.CONSTRAINT_TYPE = 'R'
             GROUP BY constraint_value.CONSTRAINT_NAME, referenced.TABLE_NAME
      )
     WHERE REFERENCED_TABLE = 'ORGANIZATION'
       AND COLUMN_LIST = 'OWNER_ORGANIZATION_ID';

    SELECT COUNT(*) INTO v_named
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'FK_QUESTION_MEDIA_OWNER_ORG';

    IF v_equivalent = 0 AND v_named > 0 THEN
        RAISE_APPLICATION_ERROR(-20063,
            'FK_QUESTION_MEDIA_OWNER_ORG ya existe con una definición distinta.');
    ELSIF v_equivalent = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUESTION_MEDIA ADD CONSTRAINT FK_QUESTION_MEDIA_OWNER_ORG FOREIGN KEY (OWNER_ORGANIZATION_ID) REFERENCES ORGANIZATION (ORGANIZATION_ID)';
    END IF;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_named NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM (
            SELECT i.INDEX_NAME,
                   LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
              FROM USER_INDEXES i
              JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
             WHERE i.TABLE_NAME = 'QUESTION_MEDIA'
             GROUP BY i.INDEX_NAME
      )
     WHERE COLUMN_LIST = 'CONTENT_SCOPE,OWNER_ORGANIZATION_ID';

    SELECT COUNT(*) INTO v_named
      FROM USER_INDEXES
     WHERE INDEX_NAME = 'IX_QUESTION_MEDIA_OWNER';

    IF v_equivalent = 0 AND v_named > 0 THEN
        RAISE_APPLICATION_ERROR(-20064,
            'IX_QUESTION_MEDIA_OWNER ya existe sobre columnas distintas.');
    ELSIF v_equivalent = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QUESTION_MEDIA_OWNER ON QUESTION_MEDIA (CONTENT_SCOPE, OWNER_ORGANIZATION_ID)';
    END IF;
END;
/

-- La misma hoja puede importarse nuevamente con una vista previa nueva. La exclusión mutua
-- depende del token de confirmación, no de la huella permanente del archivo.
UPDATE STUDENT_IMPORT_RECEIPT
   SET CONFIRMATION_TOKEN = PUBLIC_ID
 WHERE CONFIRMATION_TOKEN IS NULL;

DECLARE
    v_nullable USER_TAB_COLUMNS.NULLABLE%TYPE;
BEGIN
    SELECT NULLABLE INTO v_nullable
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
       AND COLUMN_NAME = 'CONFIRMATION_TOKEN';
    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_IMPORT_RECEIPT MODIFY (CONFIRMATION_TOKEN NOT NULL)';
    END IF;
END;
/

DECLARE
BEGIN
    FOR current_constraint IN (
        SELECT constraint_value.CONSTRAINT_NAME
          FROM (
                SELECT c.CONSTRAINT_NAME,
                       LISTAGG(cc.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY cc.POSITION) COLUMN_LIST
                  FROM USER_CONSTRAINTS c
                  JOIN USER_CONS_COLUMNS cc ON cc.CONSTRAINT_NAME = c.CONSTRAINT_NAME
                 WHERE c.TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
                   AND c.CONSTRAINT_TYPE = 'U'
                 GROUP BY c.CONSTRAINT_NAME
          ) constraint_value
         WHERE constraint_value.COLUMN_LIST = 'ORGANIZATION_ID,FILE_SHA256'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_IMPORT_RECEIPT DROP CONSTRAINT '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(current_constraint.CONSTRAINT_NAME);
    END LOOP;
END;
/

-- Elimina únicamente índices únicos residuales con la combinación anterior que ya no
-- respalden una restricción. Así se permite reimportar el mismo archivo con un token nuevo.
DECLARE
    v_constraint_count NUMBER;
BEGIN
    FOR current_index IN (
        SELECT index_value.INDEX_NAME
          FROM (
                SELECT i.INDEX_NAME, i.UNIQUENESS,
                       LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
                  FROM USER_INDEXES i
                  JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
                 WHERE i.TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
                 GROUP BY i.INDEX_NAME, i.UNIQUENESS
          ) index_value
         WHERE index_value.UNIQUENESS = 'UNIQUE'
           AND index_value.COLUMN_LIST = 'ORGANIZATION_ID,FILE_SHA256'
    ) LOOP
        SELECT COUNT(*) INTO v_constraint_count
          FROM USER_CONSTRAINTS
         WHERE INDEX_NAME = current_index.INDEX_NAME;
        IF v_constraint_count = 0 THEN
            EXECUTE IMMEDIATE 'DROP INDEX '
                    || DBMS_ASSERT.SIMPLE_SQL_NAME(current_index.INDEX_NAME);
        END IF;
    END LOOP;
END;
/

DECLARE
    v_unique_equivalent NUMBER;
    v_any_equivalent NUMBER;
    v_named NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_unique_equivalent
      FROM (
            SELECT i.INDEX_NAME, i.UNIQUENESS,
                   LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
              FROM USER_INDEXES i
              JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
             WHERE i.TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
             GROUP BY i.INDEX_NAME, i.UNIQUENESS
      )
     WHERE UNIQUENESS = 'UNIQUE'
       AND COLUMN_LIST = 'CONFIRMATION_TOKEN';

    SELECT COUNT(*) INTO v_any_equivalent
      FROM (
            SELECT i.INDEX_NAME,
                   LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
              FROM USER_INDEXES i
              JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
             WHERE i.TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
             GROUP BY i.INDEX_NAME
      )
     WHERE COLUMN_LIST = 'CONFIRMATION_TOKEN';

    SELECT COUNT(*) INTO v_named
      FROM USER_INDEXES
     WHERE INDEX_NAME = 'UK_STU_IMPORT_RECEIPT_TOKEN';

    IF v_unique_equivalent = 0 AND v_any_equivalent > 0 THEN
        FOR current_index IN (
            SELECT index_value.INDEX_NAME
              FROM (
                    SELECT i.INDEX_NAME,
                           LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
                      FROM USER_INDEXES i
                      JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
                     WHERE i.TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
                     GROUP BY i.INDEX_NAME
              ) index_value
             WHERE index_value.COLUMN_LIST = 'CONFIRMATION_TOKEN'
        ) LOOP
            SELECT COUNT(*) INTO v_named
              FROM USER_CONSTRAINTS
             WHERE INDEX_NAME = current_index.INDEX_NAME;
            IF v_named > 0 THEN
                RAISE_APPLICATION_ERROR(-20069,
                    'Existe una restricción no única incompatible sobre CONFIRMATION_TOKEN.');
            END IF;
            EXECUTE IMMEDIATE 'DROP INDEX '
                    || DBMS_ASSERT.SIMPLE_SQL_NAME(current_index.INDEX_NAME);
        END LOOP;
        v_any_equivalent := 0;
        SELECT COUNT(*) INTO v_named
          FROM USER_INDEXES
         WHERE INDEX_NAME = 'UK_STU_IMPORT_RECEIPT_TOKEN';
    END IF;

    IF v_unique_equivalent = 0 AND v_named > 0 THEN
        RAISE_APPLICATION_ERROR(-20065,
            'UK_STU_IMPORT_RECEIPT_TOKEN ya existe sobre columnas distintas.');
    ELSIF v_unique_equivalent = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_STU_IMPORT_RECEIPT_TOKEN ON STUDENT_IMPORT_RECEIPT (CONFIRMATION_TOKEN)';
    END IF;
END;
/

DECLARE
BEGIN
    FOR status_constraint IN (
        SELECT CONSTRAINT_NAME
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
           AND CONSTRAINT_TYPE = 'C'
           AND REGEXP_REPLACE(UPPER(SEARCH_CONDITION_VC), '[^A-Z0-9_,()]', '')
               LIKE 'STATUS_CODEIN(%'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_IMPORT_RECEIPT DROP CONSTRAINT '
                || DBMS_ASSERT.SIMPLE_SQL_NAME(status_constraint.CONSTRAINT_NAME);
    END LOOP;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_named NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'STUDENT_IMPORT_RECEIPT'
       AND CONSTRAINT_TYPE = 'C'
       AND REGEXP_REPLACE(UPPER(SEARCH_CONDITION_VC), '[^A-Z0-9_,()]', '')
           IN ('STATUS_CODEIN(PROCESSING,COMPLETED,FAILED)',
               'STATUS_CODEIN(PROCESSING,FAILED,COMPLETED)',
               'STATUS_CODEIN(COMPLETED,PROCESSING,FAILED)',
               'STATUS_CODEIN(COMPLETED,FAILED,PROCESSING)',
               'STATUS_CODEIN(FAILED,PROCESSING,COMPLETED)',
               'STATUS_CODEIN(FAILED,COMPLETED,PROCESSING)');

    SELECT COUNT(*) INTO v_named
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'CK_STU_IMPORT_RECEIPT_STATUS';

    IF v_equivalent = 0 AND v_named > 0 THEN
        RAISE_APPLICATION_ERROR(-20066,
            'CK_STU_IMPORT_RECEIPT_STATUS ya existe con una definición distinta.');
    ELSIF v_equivalent = 0 THEN
        EXECUTE IMMEDIATE q'[ALTER TABLE STUDENT_IMPORT_RECEIPT ADD CONSTRAINT CK_STU_IMPORT_RECEIPT_STATUS CHECK (STATUS_CODE IN ('PROCESSING','COMPLETED','FAILED'))]';
    END IF;
END;
/

-- Evita que dos solicitudes concurrentes asignen el mismo número de intento dentro de un ciclo.
-- No se renumeran históricos: una instalación que ya contenga duplicados se detiene con un
-- diagnóstico explícito para no modificar evidencias válidas de certificación.
DECLARE
    v_duplicates NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_duplicates
      FROM (
            SELECT STUDENT_CERTIFICATION_CYCLE_ID, ATTEMPT_NUMBER
              FROM STUDENT_CERTIFICATION_CYCLE_ATTEMPT
             GROUP BY STUDENT_CERTIFICATION_CYCLE_ID, ATTEMPT_NUMBER
            HAVING COUNT(*) > 1
      );
    IF v_duplicates > 0 THEN
        RAISE_APPLICATION_ERROR(-20067,
            'Existen números de intento duplicados dentro de un ciclo. Revísalos antes de ejecutar V052.');
    END IF;
END;
/

DECLARE
    v_unique_equivalent NUMBER;
    v_named NUMBER;
    v_constraint_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_unique_equivalent
      FROM (
            SELECT i.INDEX_NAME, i.UNIQUENESS,
                   LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
              FROM USER_INDEXES i
              JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
             WHERE i.TABLE_NAME = 'STUDENT_CERTIFICATION_CYCLE_ATTEMPT'
             GROUP BY i.INDEX_NAME, i.UNIQUENESS
      )
     WHERE UNIQUENESS = 'UNIQUE'
       AND COLUMN_LIST = 'STUDENT_CERTIFICATION_CYCLE_ID,ATTEMPT_NUMBER';

    IF v_unique_equivalent = 0 THEN
        FOR current_index IN (
            SELECT index_value.INDEX_NAME
              FROM (
                    SELECT i.INDEX_NAME, i.UNIQUENESS,
                           LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
                      FROM USER_INDEXES i
                      JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
                     WHERE i.TABLE_NAME = 'STUDENT_CERTIFICATION_CYCLE_ATTEMPT'
                     GROUP BY i.INDEX_NAME, i.UNIQUENESS
              ) index_value
             WHERE index_value.UNIQUENESS = 'NONUNIQUE'
               AND index_value.COLUMN_LIST = 'STUDENT_CERTIFICATION_CYCLE_ID,ATTEMPT_NUMBER'
        ) LOOP
            SELECT COUNT(*) INTO v_constraint_count
              FROM USER_CONSTRAINTS
             WHERE INDEX_NAME = current_index.INDEX_NAME;
            IF v_constraint_count = 0 THEN
                EXECUTE IMMEDIATE 'DROP INDEX ' || DBMS_ASSERT.SIMPLE_SQL_NAME(current_index.INDEX_NAME);
            END IF;
        END LOOP;

        SELECT COUNT(*) INTO v_named
          FROM USER_INDEXES
         WHERE INDEX_NAME = 'UK_STUDENT_CERT_CYCLE_ATT';
        IF v_named > 0 THEN
            RAISE_APPLICATION_ERROR(-20068,
                'UK_STUDENT_CERT_CYCLE_ATT ya existe sobre columnas distintas.');
        END IF;
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_STUDENT_CERT_CYCLE_ATT ON STUDENT_CERTIFICATION_CYCLE_ATTEMPT (STUDENT_CERTIFICATION_CYCLE_ID, ATTEMPT_NUMBER)';
    END IF;
END;
/

-- GLOBAL administra contenido y organizaciones, pero no colaboradores comerciales.
DELETE FROM APP_ROLE_PERMISSION role_permission
 WHERE role_permission.ROLE_ID IN (
       SELECT role_value.ROLE_ID
         FROM APP_ROLE role_value
        WHERE role_value.ROLE_CODE = 'ADMINISTRATOR'
   )
   AND role_permission.PERMISSION_ID IN (
       SELECT permission_value.PERMISSION_ID
         FROM APP_PERMISSION permission_value
        WHERE permission_value.PERMISSION_CODE LIKE 'STUDENT\_%' ESCAPE '\'
   );

-- Las sesiones activas de administradores se invalidan para que sus permisos efectivos se
-- reconstruyan inmediatamente y no conserven capacidades de colaboradores ya revocadas.
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
