-- Simplify the question model for Java certification forms.
-- Difficulty remains as an internal compatibility column, but it is no longer
-- exposed or used by the application. Categories classify JR / STD / SR.

ALTER TABLE QUESTION_OPTION ADD (
    MATCH_TEXT       CLOB,
    MATCH_MEDIA_ID   NUMBER,
    FEEDBACK_TEXT    CLOB
);

ALTER TABLE QUESTION_OPTION ADD CONSTRAINT FK_QUESTION_OPTION_MATCH_MEDIA
    FOREIGN KEY (MATCH_MEDIA_ID) REFERENCES QUESTION_MEDIA (MEDIA_ID);

MERGE INTO QUESTION_TYPE_CATALOG target
USING (
    SELECT 'MATCHING' TYPE_CODE,
           'Columnas para relación' TYPE_NAME,
           'Relaciona cada elemento de la columna izquierda con su pareja de la columna derecha.' DESCRIPTION,
           1 SUPPORTS_OPTIONS
      FROM DUAL
) source
ON (target.TYPE_CODE = source.TYPE_CODE)
WHEN MATCHED THEN UPDATE SET
    target.TYPE_NAME = source.TYPE_NAME,
    target.DESCRIPTION = source.DESCRIPTION,
    target.SUPPORTS_OPTIONS = source.SUPPORTS_OPTIONS,
    target.STATUS = 'ACTIVE'
WHEN NOT MATCHED THEN INSERT (
    TYPE_CODE, TYPE_NAME, DESCRIPTION, SUPPORTS_OPTIONS, STATUS
) VALUES (
    source.TYPE_CODE, source.TYPE_NAME, source.DESCRIPTION, source.SUPPORTS_OPTIONS, 'ACTIVE'
);

MERGE INTO QUESTION_TYPE_CATALOG target
USING (
    SELECT 'OPEN_TEXT' TYPE_CODE,
           'Respuesta abierta' TYPE_NAME,
           'Respuesta libre revisada manualmente.' DESCRIPTION,
           0 SUPPORTS_OPTIONS
      FROM DUAL
) source
ON (target.TYPE_CODE = source.TYPE_CODE)
WHEN MATCHED THEN UPDATE SET
    target.TYPE_NAME = source.TYPE_NAME,
    target.DESCRIPTION = source.DESCRIPTION,
    target.SUPPORTS_OPTIONS = source.SUPPORTS_OPTIONS,
    target.STATUS = 'ACTIVE'
WHEN NOT MATCHED THEN INSERT (
    TYPE_CODE, TYPE_NAME, DESCRIPTION, SUPPORTS_OPTIONS, STATUS
) VALUES (
    source.TYPE_CODE, source.TYPE_NAME, source.DESCRIPTION, source.SUPPORTS_OPTIONS, 'ACTIVE'
);

-- Consolidate legacy question types after the new targets exist.
UPDATE QUESTION
   SET TYPE_CODE = 'SINGLE_CHOICE'
 WHERE TYPE_CODE = 'DROPDOWN';

UPDATE QUESTION
   SET TYPE_CODE = 'OPEN_TEXT'
 WHERE TYPE_CODE IN ('SHORT_TEXT', 'LONG_TEXT', 'NUMBER', 'CODE_RESPONSE');

-- The difficulty catalog is kept only to preserve the existing non-null FK.
UPDATE QUESTION
   SET DIFFICULTY_CODE = 'BASIC'
 WHERE DIFFICULTY_CODE <> 'BASIC';

UPDATE QUESTION
   SET CODE_LANGUAGE = 'JAVA'
 WHERE CODE_CONTENT IS NOT NULL;

UPDATE QUESTION_TYPE_CATALOG
   SET STATUS = 'ACTIVE'
 WHERE TYPE_CODE IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'MATCHING', 'OPEN_TEXT');

UPDATE QUESTION_TYPE_CATALOG
   SET STATUS = 'INACTIVE'
 WHERE TYPE_CODE IN ('DROPDOWN', 'SHORT_TEXT', 'LONG_TEXT', 'NUMBER', 'CODE_RESPONSE');

COMMENT ON COLUMN QUESTION_OPTION.MATCH_TEXT IS
    'Contenido de la columna derecha para preguntas de relación.';
COMMENT ON COLUMN QUESTION_OPTION.MATCH_MEDIA_ID IS
    'Imagen opcional de la columna derecha para preguntas de relación.';
COMMENT ON COLUMN QUESTION_OPTION.FEEDBACK_TEXT IS
    'Retroalimentación específica mostrada para esta opción o relación.';
