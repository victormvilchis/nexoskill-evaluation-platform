-- Align the persisted difficulty catalog with the values used by the current Question editor.
-- Historical BASIC / INTERMEDIATE / ADVANCED rows remain untouched for existing records.
MERGE INTO QUESTION_DIFFICULTY_CATALOG target
USING (
    SELECT 'JR' AS DIFFICULTY_CODE, 'JR' AS DIFFICULTY_NAME,
           'Nivel de dificultad JR.' AS DESCRIPTION, 10 AS SORT_ORDER FROM DUAL
    UNION ALL
    SELECT 'STD', 'STD', 'Nivel de dificultad STD.', 20 FROM DUAL
    UNION ALL
    SELECT 'SR', 'SR', 'Nivel de dificultad SR.', 30 FROM DUAL
) source
ON (target.DIFFICULTY_CODE = source.DIFFICULTY_CODE)
WHEN MATCHED THEN UPDATE SET
    target.DIFFICULTY_NAME = source.DIFFICULTY_NAME,
    target.DESCRIPTION = source.DESCRIPTION,
    target.SORT_ORDER = source.SORT_ORDER,
    target.STATUS = 'ACTIVE',
    target.UPDATED_AT = SYSTIMESTAMP
WHEN NOT MATCHED THEN INSERT (
    DIFFICULTY_CODE,
    DIFFICULTY_NAME,
    DESCRIPTION,
    SORT_ORDER,
    STATUS,
    CREATED_AT,
    UPDATED_AT,
    VERSION_NO
) VALUES (
    source.DIFFICULTY_CODE,
    source.DIFFICULTY_NAME,
    source.DESCRIPTION,
    source.SORT_ORDER,
    'ACTIVE',
    SYSTIMESTAMP,
    SYSTIMESTAMP,
    0
);
