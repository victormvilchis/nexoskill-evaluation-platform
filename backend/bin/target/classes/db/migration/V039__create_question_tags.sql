-- Etiquetas temáticas flexibles para preguntas.
-- QUESTION_TAG y QUESTION_TAG_RELATION existen desde V009/V010; esta migración
-- amplía de forma idempotente esas estructuras y conserva todas las relaciones.

DECLARE
    PROCEDURE add_column_if_missing(
        p_table VARCHAR2,
        p_column VARCHAR2,
        p_definition VARCHAR2
    ) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = UPPER(p_table)
           AND COLUMN_NAME = UPPER(p_column);

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column_if_missing('QUESTION_TAG', 'DISPLAY_NAME',
            'DISPLAY_NAME VARCHAR2(120 CHAR)');
    add_column_if_missing('QUESTION_TAG', 'NORMALIZED_NAME',
            'NORMALIZED_NAME VARCHAR2(160 CHAR)');
    add_column_if_missing('QUESTION_TAG', 'SLUG',
            'SLUG VARCHAR2(160 CHAR)');
    add_column_if_missing('QUESTION_TAG', 'CONTENT_SCOPE',
            'CONTENT_SCOPE VARCHAR2(20 CHAR) DEFAULT ''GLOBAL''');
    add_column_if_missing('QUESTION_TAG', 'OWNER_ORGANIZATION_ID',
            'OWNER_ORGANIZATION_ID NUMBER');

    add_column_if_missing('QUESTION_TAG_RELATION', 'CREATED_AT',
            'CREATED_AT TIMESTAMP WITH TIME ZONE');
    add_column_if_missing('QUESTION_TAG_RELATION', 'CREATED_BY',
            'CREATED_BY NUMBER');
END;
/

-- Los registros legados se conservan como etiquetas GLOBAL. Se completan los
-- campos nuevos sin alterar sus IDs ni sus asociaciones existentes.
UPDATE QUESTION_TAG
   SET DISPLAY_NAME = SUBSTR(
           TRIM(REGEXP_REPLACE(NVL(TAG_NAME, TAG_CODE), '[[:space:]]+', ' ')),
           1,
           120
       )
 WHERE DISPLAY_NAME IS NULL;

UPDATE QUESTION_TAG
   SET NORMALIZED_NAME = SUBSTR(
           TRANSLATE(
               LOWER(
                   TRIM(
                       REGEXP_REPLACE(
                           REGEXP_REPLACE(NVL(TAG_NAME, TAG_CODE), '^#+[[:space:]]*', ''),
                           '[[:space:]]+',
                           ' '
                       )
                   )
               ),
               'áéíóúüñ',
               'aeiouun'
           ),
           1,
           160
       )
 WHERE NORMALIZED_NAME IS NULL;

UPDATE QUESTION_TAG
   SET NORMALIZED_NAME = LOWER(TAG_CODE)
 WHERE NORMALIZED_NAME IS NULL OR TRIM(NORMALIZED_NAME) IS NULL;

UPDATE QUESTION_TAG
   SET SLUG = SUBSTR(
           TRIM(
               BOTH '-' FROM REGEXP_REPLACE(
                   REPLACE(NORMALIZED_NAME, ' ', '-'),
                   '[^a-z0-9_-]+',
                   '-'
               )
           ),
           1,
           160
       )
 WHERE SLUG IS NULL;

UPDATE QUESTION_TAG
   SET SLUG = LOWER(TAG_CODE)
 WHERE SLUG IS NULL OR TRIM(SLUG) IS NULL;

UPDATE QUESTION_TAG
   SET CONTENT_SCOPE = 'GLOBAL',
       OWNER_ORGANIZATION_ID = NULL
 WHERE CONTENT_SCOPE IS NULL;

UPDATE QUESTION_TAG_RELATION
   SET CREATED_AT = NVL(CREATED_AT, ASSIGNED_AT)
 WHERE CREATED_AT IS NULL;

-- Las restricciones globales antiguas impedían reutilizar el mismo nombre en
-- organizaciones diferentes. Se sustituyen por unicidad basada en alcance y tenant.
DECLARE
    PROCEDURE drop_constraint_if_exists(p_table VARCHAR2, p_name VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = UPPER(p_table)
           AND CONSTRAINT_NAME = UPPER(p_name);
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' DROP CONSTRAINT ' || p_name;
        END IF;
    END;

    PROCEDURE drop_index_if_exists(p_name VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_INDEXES
         WHERE INDEX_NAME = UPPER(p_name);
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'DROP INDEX ' || p_name;
        END IF;
    END;
BEGIN
    drop_constraint_if_exists('QUESTION_TAG', 'UK_QUESTION_TAG_CODE');
    drop_index_if_exists('UK_QUESTION_TAG_NAME_CI');
END;
/

-- Si existieran duplicados legados después de normalizar, se conserva el TAG_ID
-- menor y se trasladan sus relaciones antes de eliminar únicamente el duplicado.
BEGIN
    FOR duplicate_tag IN (
        SELECT TAG_ID,
               MIN(TAG_ID) OVER (
                   PARTITION BY CONTENT_SCOPE,
                                NVL(OWNER_ORGANIZATION_ID, -1),
                                NORMALIZED_NAME
               ) AS CANONICAL_TAG_ID
          FROM QUESTION_TAG
    ) LOOP
        IF duplicate_tag.TAG_ID <> duplicate_tag.CANONICAL_TAG_ID THEN
            MERGE INTO QUESTION_TAG_RELATION target
            USING (
                SELECT QUESTION_ID,
                       duplicate_tag.CANONICAL_TAG_ID AS TAG_ID,
                       NVL(CREATED_AT, ASSIGNED_AT) AS CREATED_AT,
                       CREATED_BY
                  FROM QUESTION_TAG_RELATION
                 WHERE TAG_ID = duplicate_tag.TAG_ID
            ) source
               ON (target.QUESTION_ID = source.QUESTION_ID AND target.TAG_ID = source.TAG_ID)
            WHEN NOT MATCHED THEN INSERT (
                QUESTION_ID, TAG_ID, CREATED_AT, CREATED_BY
            ) VALUES (
                source.QUESTION_ID, source.TAG_ID, source.CREATED_AT, source.CREATED_BY
            );

            DELETE FROM QUESTION_TAG_RELATION
             WHERE TAG_ID = duplicate_tag.TAG_ID;

            DELETE FROM QUESTION_TAG
             WHERE TAG_ID = duplicate_tag.TAG_ID;
        END IF;
    END LOOP;
END;
/

ALTER TABLE QUESTION_TAG MODIFY (
    DISPLAY_NAME NOT NULL,
    NORMALIZED_NAME NOT NULL,
    SLUG NOT NULL,
    CONTENT_SCOPE DEFAULT 'GLOBAL' NOT NULL
);

ALTER TABLE QUESTION_TAG_RELATION MODIFY (CREATED_AT NOT NULL);

DECLARE
    PROCEDURE add_constraint_if_missing(
        p_table VARCHAR2,
        p_name VARCHAR2,
        p_sql VARCHAR2
    ) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = UPPER(p_table)
           AND CONSTRAINT_NAME = UPPER(p_name);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;
BEGIN
    add_constraint_if_missing(
        'QUESTION_TAG',
        'CK_QUESTION_TAG_SCOPE',
        'ALTER TABLE QUESTION_TAG ADD CONSTRAINT CK_QUESTION_TAG_SCOPE CHECK (' ||
        '(CONTENT_SCOPE = ''GLOBAL'' AND OWNER_ORGANIZATION_ID IS NULL) OR ' ||
        '(CONTENT_SCOPE = ''ORGANIZATION'' AND OWNER_ORGANIZATION_ID IS NOT NULL))'
    );
    add_constraint_if_missing(
        'QUESTION_TAG',
        'FK_QUESTION_TAG_OWNER_ORG',
        'ALTER TABLE QUESTION_TAG ADD CONSTRAINT FK_QUESTION_TAG_OWNER_ORG ' ||
        'FOREIGN KEY (OWNER_ORGANIZATION_ID) REFERENCES ORGANIZATION (ORGANIZATION_ID)'
    );
    add_constraint_if_missing(
        'QUESTION_TAG_RELATION',
        'FK_QUESTION_TAG_REL_USER',
        'ALTER TABLE QUESTION_TAG_RELATION ADD CONSTRAINT FK_QUESTION_TAG_REL_USER ' ||
        'FOREIGN KEY (CREATED_BY) REFERENCES APP_USER (USER_ID)'
    );
END;
/

DECLARE
    PROCEDURE create_index_if_missing(p_name VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_INDEXES
         WHERE INDEX_NAME = UPPER(p_name);
        IF v_count = 0 THEN
            BEGIN
                EXECUTE IMMEDIATE p_sql;
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE NOT IN (-1408, -955) THEN
                        RAISE;
                    END IF;
            END;
        END IF;
    END;
BEGIN
    create_index_if_missing(
        'UK_QUESTION_TAG_OWNER_NAME',
        'CREATE UNIQUE INDEX UK_QUESTION_TAG_OWNER_NAME ON QUESTION_TAG ' ||
        '(CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1), NORMALIZED_NAME)'
    );
    create_index_if_missing(
        'IX_QUESTION_TAG_REL_TAG',
        'CREATE INDEX IX_QUESTION_TAG_REL_TAG ON QUESTION_TAG_RELATION (TAG_ID, QUESTION_ID)'
    );
END;
/

COMMIT;
