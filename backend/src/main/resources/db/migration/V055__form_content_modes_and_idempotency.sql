DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'EVALUATION_FORM'
       AND COLUMN_NAME = 'CONTENT_MODE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE EVALUATION_FORM ADD CONTENT_MODE VARCHAR2(20 CHAR) DEFAULT ''MANUAL'' NOT NULL';
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'EVALUATION_FORM'
       AND COLUMN_NAME = 'CREATE_OPERATION_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE EVALUATION_FORM ADD CREATE_OPERATION_ID VARCHAR2(36 CHAR)';
    END IF;
END;
/

UPDATE EVALUATION_FORM
   SET CONTENT_MODE = CASE
       WHEN EXISTS (
           SELECT 1
             FROM FORM_SECTION section_value
             JOIN FORM_QUESTION_POOL pool_value ON pool_value.SECTION_ID = section_value.SECTION_ID
            WHERE section_value.FORM_ID = EVALUATION_FORM.FORM_ID
       ) THEN 'RANDOM_POOL'
       ELSE 'MANUAL'
   END;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'EVALUATION_FORM'
       AND CONSTRAINT_TYPE = 'C'
       AND UPPER(SEARCH_CONDITION_VC) LIKE '%CONTENT_MODE%MANUAL%RANDOM_POOL%';
    IF v_count = 0 THEN
        SELECT COUNT(*) INTO v_count
          FROM USER_CONSTRAINTS
         WHERE CONSTRAINT_NAME = 'CK_FORM_CONTENT_MODE';
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20055,
                'CK_FORM_CONTENT_MODE ya existe con una definición distinta. Revisa el objeto antes de ejecutar V055.');
        END IF;
        EXECUTE IMMEDIATE q'[ALTER TABLE EVALUATION_FORM ADD CONSTRAINT CK_FORM_CONTENT_MODE CHECK (CONTENT_MODE IN ('MANUAL','RANDOM_POOL'))]';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_INDEXES index_value
      JOIN USER_IND_COLUMNS column_value
        ON column_value.INDEX_NAME = index_value.INDEX_NAME
       AND column_value.TABLE_NAME = index_value.TABLE_NAME
     WHERE index_value.TABLE_NAME = 'EVALUATION_FORM'
       AND index_value.UNIQUENESS = 'UNIQUE'
       AND column_value.COLUMN_NAME = 'CREATE_OPERATION_ID'
       AND NOT EXISTS (
           SELECT 1
             FROM USER_IND_COLUMNS extra_column
            WHERE extra_column.INDEX_NAME = index_value.INDEX_NAME
              AND extra_column.TABLE_NAME = index_value.TABLE_NAME
              AND extra_column.COLUMN_POSITION > 1
       );
    IF v_count = 0 THEN
        SELECT COUNT(*) INTO v_count
          FROM USER_INDEXES
         WHERE INDEX_NAME = 'UK_FORM_CREATE_OPERATION';
        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20056,
                'UK_FORM_CREATE_OPERATION ya existe con una definición distinta. Revisa el objeto antes de ejecutar V055.');
        END IF;
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_FORM_CREATE_OPERATION ON EVALUATION_FORM (CREATE_OPERATION_ID)';
    END IF;
END;
/
