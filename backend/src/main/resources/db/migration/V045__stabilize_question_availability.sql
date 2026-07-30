-- Stabiliza la disponibilidad de preguntas GLOBAL.
-- Agrega NONE para mantener preguntas globales sin publicación organizacional.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'QUESTION'
       AND CONSTRAINT_NAME = 'CK_QUESTION_AVAILABILITY_MODE';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUESTION DROP CONSTRAINT CK_QUESTION_AVAILABILITY_MODE';
    END IF;
END;
/

ALTER TABLE QUESTION ADD CONSTRAINT CK_QUESTION_AVAILABILITY_MODE
    CHECK (AVAILABILITY_MODE IN ('NONE', 'GLOBAL', 'SELECTED_ORGANIZATIONS'));

DECLARE
    v_index_by_name NUMBER;
    v_equivalent_index NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_index_by_name
      FROM USER_INDEXES
     WHERE INDEX_NAME = 'IX_QUESTION_SCOPE_AVAILABILITY';

    SELECT COUNT(*) INTO v_equivalent_index
      FROM (
            SELECT INDEX_NAME
              FROM USER_IND_COLUMNS
             WHERE TABLE_NAME = 'QUESTION'
             GROUP BY INDEX_NAME
            HAVING LISTAGG(COLUMN_NAME, ',') WITHIN GROUP (ORDER BY COLUMN_POSITION)
                   = 'CONTENT_SCOPE,AVAILABILITY_MODE,STATUS,QUESTION_ID'
      );

    IF v_equivalent_index = 0 AND v_index_by_name = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IX_QUESTION_SCOPE_AVAILABILITY ON QUESTION (CONTENT_SCOPE, AVAILABILITY_MODE, STATUS, QUESTION_ID)';
    ELSIF v_equivalent_index = 0 AND v_index_by_name > 0 THEN
        RAISE_APPLICATION_ERROR(-20045,
            'IX_QUESTION_SCOPE_AVAILABILITY existe con una definición diferente. Revisa el objeto parcial antes de reintentar V045.');
    END IF;
END;
/
