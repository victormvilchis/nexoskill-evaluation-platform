DECLARE
    v_table_count NUMBER;
    v_scope_column_count NUMBER;
    v_availability_column_count NUMBER;
    v_availability_table_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM USER_TABLES
     WHERE TABLE_NAME = 'QUESTION';

    SELECT COUNT(*)
      INTO v_scope_column_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'QUESTION'
       AND COLUMN_NAME = 'CONTENT_SCOPE';

    SELECT COUNT(*)
      INTO v_availability_column_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'QUESTION'
       AND COLUMN_NAME = 'AVAILABILITY_MODE';

    SELECT COUNT(*)
      INTO v_availability_table_count
      FROM USER_TABLES
     WHERE TABLE_NAME = 'QUESTION_ORGANIZATION_AVAILABILITY';

    IF v_table_count = 0
       OR v_scope_column_count = 0
       OR v_availability_column_count = 0
       OR v_availability_table_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20046,
            'V046 requiere QUESTION.CONTENT_SCOPE, QUESTION.AVAILABILITY_MODE y QUESTION_ORGANIZATION_AVAILABILITY. Verifica las migraciones anteriores.');
    END IF;
END;
/

UPDATE QUESTION_ORGANIZATION_AVAILABILITY availability
   SET STATUS = 'INACTIVE',
       DISABLED_AT = SYSTIMESTAMP,
       DISABLED_BY = NULL,
       UPDATED_AT = SYSTIMESTAMP,
       VERSION_NO = VERSION_NO + 1
 WHERE availability.STATUS = 'ACTIVE'
   AND EXISTS (
       SELECT 1
         FROM QUESTION question
        WHERE question.QUESTION_ID = availability.QUESTION_ID
          AND question.CONTENT_SCOPE = 'ORGANIZATION'
   );

UPDATE QUESTION
   SET AVAILABILITY_MODE = 'NONE'
 WHERE CONTENT_SCOPE = 'ORGANIZATION'
   AND AVAILABILITY_MODE <> 'NONE';

ALTER TABLE QUESTION MODIFY (AVAILABILITY_MODE DEFAULT 'NONE');

DECLARE
    v_named_constraint NUMBER := 0;
    v_equivalent_constraint NUMBER := 0;
    v_condition VARCHAR2(4000);
BEGIN
    FOR constraint_row IN (
        SELECT CONSTRAINT_NAME, SEARCH_CONDITION_VC
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = 'QUESTION'
           AND CONSTRAINT_TYPE = 'C'
    ) LOOP
        v_condition := UPPER(NVL(constraint_row.SEARCH_CONDITION_VC, ''));
        v_condition := REPLACE(v_condition, '"', '');
        v_condition := REPLACE(v_condition, ' ', '');
        v_condition := REPLACE(v_condition, CHR(9), '');
        v_condition := REPLACE(v_condition, CHR(10), '');
        v_condition := REPLACE(v_condition, CHR(13), '');
        v_condition := REPLACE(v_condition, '(', '');
        v_condition := REPLACE(v_condition, ')', '');

        IF constraint_row.CONSTRAINT_NAME = 'CK_QUESTION_AVAIL_SCOPE' THEN
            v_named_constraint := 1;
        END IF;

        IF INSTR(v_condition,
                'CONTENT_SCOPE=''GLOBAL''ORAVAILABILITY_MODE=''NONE''') > 0
           OR INSTR(v_condition,
                'AVAILABILITY_MODE=''NONE''ORCONTENT_SCOPE=''GLOBAL''') > 0 THEN
            v_equivalent_constraint := 1;
        ELSIF constraint_row.CONSTRAINT_NAME = 'CK_QUESTION_AVAIL_SCOPE' THEN
            RAISE_APPLICATION_ERROR(-20047,
                'CK_QUESTION_AVAIL_SCOPE existe con una definición diferente. Revísala antes de continuar.');
        END IF;
    END LOOP;

    IF v_equivalent_constraint = 0 THEN
        IF v_named_constraint > 0 THEN
            RAISE_APPLICATION_ERROR(-20048,
                'No fue posible validar CK_QUESTION_AVAIL_SCOPE. Revisa los objetos parciales del esquema.');
        END IF;

        EXECUTE IMMEDIATE '
            ALTER TABLE QUESTION ADD CONSTRAINT CK_QUESTION_AVAIL_SCOPE
            CHECK (CONTENT_SCOPE = ''GLOBAL'' OR AVAILABILITY_MODE = ''NONE'')
            ENABLE VALIDATE';
    END IF;
END;
/
