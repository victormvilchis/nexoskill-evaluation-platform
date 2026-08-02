-- Configuración de códigos de colaborador e identificador corporativo.
-- Mantiene los identificadores existentes y no modifica información histórica.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'ORGANIZATION'
       AND COLUMN_NAME = 'MANUAL_STUDENT_CODE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ORGANIZATION ADD (MANUAL_STUDENT_CODE NUMBER(1) DEFAULT 0 NOT NULL)';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STUDENT'
       AND COLUMN_NAME = 'CORPORATE_USER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT ADD (CORPORATE_USER VARCHAR2(100 CHAR))';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STUDENT'
       AND COLUMN_NAME = 'NORMALIZED_CORPORATE_USER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE STUDENT ADD (NORMALIZED_CORPORATE_USER VARCHAR2(100 CHAR))';
    END IF;
END;
/

UPDATE ORGANIZATION
   SET MANUAL_STUDENT_CODE = 0
 WHERE MANUAL_STUDENT_CODE IS NULL;

DECLARE
    v_nullable USER_TAB_COLUMNS.NULLABLE%TYPE;
BEGIN
    SELECT NULLABLE INTO v_nullable
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'ORGANIZATION'
       AND COLUMN_NAME = 'MANUAL_STUDENT_CODE';

    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ORGANIZATION MODIFY (MANUAL_STUDENT_CODE DEFAULT 0 NOT NULL)';
    ELSE
        EXECUTE IMMEDIATE 'ALTER TABLE ORGANIZATION MODIFY (MANUAL_STUDENT_CODE DEFAULT 0)';
    END IF;
END;
/

UPDATE STUDENT
   SET NORMALIZED_CORPORATE_USER = UPPER(TRIM(CORPORATE_USER))
 WHERE (CORPORATE_USER IS NULL AND NORMALIZED_CORPORATE_USER IS NOT NULL)
    OR (CORPORATE_USER IS NOT NULL
        AND (NORMALIZED_CORPORATE_USER IS NULL
             OR NORMALIZED_CORPORATE_USER <> UPPER(TRIM(CORPORATE_USER))));

DECLARE
    v_duplicates NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_duplicates
      FROM (
            SELECT NORMALIZED_CORPORATE_USER
              FROM STUDENT
             WHERE NORMALIZED_CORPORATE_USER IS NOT NULL
             GROUP BY NORMALIZED_CORPORATE_USER
            HAVING COUNT(*) > 1
      );
    IF v_duplicates > 0 THEN
        RAISE_APPLICATION_ERROR(-20053,
                'Existen Usuarios corporativos duplicados. Corrige los datos antes de continuar con la migración.');
    END IF;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_name_used NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'ORGANIZATION'
       AND CONSTRAINT_TYPE = 'C'
       AND REGEXP_REPLACE(UPPER(SEARCH_CONDITION_VC), '[^A-Z0-9_,()]', '')
           IN ('MANUAL_STUDENT_CODEIN(0,1)', 'MANUAL_STUDENT_CODEIN(1,0)');

    IF v_equivalent = 0 THEN
        SELECT COUNT(*) INTO v_name_used
          FROM USER_CONSTRAINTS
         WHERE CONSTRAINT_NAME = 'CK_ORG_MANUAL_STUDENT_CODE';
        IF v_name_used = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ORGANIZATION ADD CONSTRAINT CK_ORG_MANUAL_STUDENT_CODE CHECK (MANUAL_STUDENT_CODE IN (0,1))';
        ELSE
            SELECT COUNT(*) INTO v_name_used
              FROM USER_CONSTRAINTS
             WHERE CONSTRAINT_NAME = 'CK_ORG_MAN_STUDENT_CODE';
            IF v_name_used = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE ORGANIZATION ADD CONSTRAINT CK_ORG_MAN_STUDENT_CODE CHECK (MANUAL_STUDENT_CODE IN (0,1))';
            ELSE
                RAISE_APPLICATION_ERROR(-20050,
                        'No existe un nombre disponible para la restricción de Código de colaborador manual.');
            END IF;
        END IF;
    END IF;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_name_used NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM (
            SELECT i.INDEX_NAME,
                   i.UNIQUENESS,
                   LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
              FROM USER_INDEXES i
              JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
             WHERE i.TABLE_NAME = 'STUDENT'
             GROUP BY i.INDEX_NAME, i.UNIQUENESS
      )
     WHERE UNIQUENESS = 'UNIQUE'
       AND COLUMN_LIST = 'NORMALIZED_CORPORATE_USER';

    IF v_equivalent = 0 THEN
        SELECT COUNT(*) INTO v_name_used
          FROM USER_OBJECTS
         WHERE OBJECT_NAME = 'UK_STUDENT_CORPORATE_USER';
        IF v_name_used = 0 THEN
            EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_STUDENT_CORPORATE_USER ON STUDENT (NORMALIZED_CORPORATE_USER)';
        ELSE
            SELECT COUNT(*) INTO v_name_used
              FROM USER_OBJECTS
             WHERE OBJECT_NAME = 'UK_STUDENT_CORP_USER_U1';
            IF v_name_used = 0 THEN
                EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_STUDENT_CORP_USER_U1 ON STUDENT (NORMALIZED_CORPORATE_USER)';
            ELSE
                RAISE_APPLICATION_ERROR(-20051,
                        'No existe un nombre disponible para el índice único de Usuario corporativo.');
            END IF;
        END IF;
    END IF;
END;
/

DECLARE
    v_equivalent NUMBER;
    v_name_used NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM (
            SELECT i.INDEX_NAME,
                   LISTAGG(c.COLUMN_NAME, ',') WITHIN GROUP (ORDER BY c.COLUMN_POSITION) COLUMN_LIST
              FROM USER_INDEXES i
              JOIN USER_IND_COLUMNS c ON c.INDEX_NAME = i.INDEX_NAME
             WHERE i.TABLE_NAME = 'STUDENT'
             GROUP BY i.INDEX_NAME
      )
     WHERE COLUMN_LIST = 'ORGANIZATION_ID,CORPORATE_USER';

    IF v_equivalent = 0 THEN
        SELECT COUNT(*) INTO v_name_used
          FROM USER_OBJECTS
         WHERE OBJECT_NAME = 'IX_STUDENT_ORG_CORP_SEARCH';
        IF v_name_used = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX IX_STUDENT_ORG_CORP_SEARCH ON STUDENT (ORGANIZATION_ID, CORPORATE_USER)';
        ELSE
            SELECT COUNT(*) INTO v_name_used
              FROM USER_OBJECTS
             WHERE OBJECT_NAME = 'IX_STUDENT_ORG_CORP_S1';
            IF v_name_used = 0 THEN
                EXECUTE IMMEDIATE 'CREATE INDEX IX_STUDENT_ORG_CORP_S1 ON STUDENT (ORGANIZATION_ID, CORPORATE_USER)';
            ELSE
                RAISE_APPLICATION_ERROR(-20052,
                        'No existe un nombre disponible para el índice de búsqueda de Usuario corporativo.');
            END IF;
        END IF;
    END IF;
END;
/
