-- Aislamiento multiorganizacion de identificadores de colaboradores y catalogos.
-- Corrige restricciones historicas globales sin eliminar informacion existente.

-- Valida primero la regla destino usando el valor canonico real. Esto ocurre antes
-- de cualquier DDL para evitar dejar el esquema parcialmente modificado si existen
-- duplicados dentro de una misma organizacion.
DECLARE
    v_duplicates NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_duplicates
      FROM (
            SELECT ORGANIZATION_ID, UPPER(TRIM(CORPORATE_USER)) CORPORATE_USER_KEY
              FROM STUDENT
             WHERE CORPORATE_USER IS NOT NULL
             GROUP BY ORGANIZATION_ID, UPPER(TRIM(CORPORATE_USER))
            HAVING COUNT(*) > 1
      );
    IF v_duplicates > 0 THEN
        RAISE_APPLICATION_ERROR(-20066,
            'Existen Usuarios corporativos duplicados dentro de una misma organizacion. Corrige esos registros antes de continuar.');
    END IF;
END;
/

-- V051 creo una unicidad global sobre NORMALIZED_CORPORATE_USER. Se elimina
-- cualquier indice/restriccion equivalente de una sola columna, sin depender
-- exclusivamente del nombre historico del objeto.
DECLARE
    v_constraint_name USER_CONSTRAINTS.CONSTRAINT_NAME%TYPE;
BEGIN
    FOR item IN (
        SELECT i.INDEX_NAME
          FROM USER_INDEXES i
         WHERE i.TABLE_NAME = 'STUDENT'
           AND i.UNIQUENESS = 'UNIQUE'
           AND (SELECT COUNT(*)
                  FROM USER_IND_COLUMNS c
                 WHERE c.INDEX_NAME = i.INDEX_NAME
                   AND c.TABLE_NAME = i.TABLE_NAME) = 1
           AND EXISTS (
                SELECT 1
                  FROM USER_IND_COLUMNS c
                 WHERE c.INDEX_NAME = i.INDEX_NAME
                   AND c.TABLE_NAME = i.TABLE_NAME
                   AND c.COLUMN_POSITION = 1
                   AND c.COLUMN_NAME = 'NORMALIZED_CORPORATE_USER'
           )
    ) LOOP
        SELECT MAX(CONSTRAINT_NAME) INTO v_constraint_name
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = 'STUDENT'
           AND INDEX_NAME = item.INDEX_NAME
           AND CONSTRAINT_TYPE = 'U';

        IF v_constraint_name IS NOT NULL THEN
            EXECUTE IMMEDIATE 'ALTER TABLE STUDENT DROP CONSTRAINT ' || DBMS_ASSERT.SIMPLE_SQL_NAME(v_constraint_name);
        ELSE
            EXECUTE IMMEDIATE 'DROP INDEX ' || DBMS_ASSERT.SIMPLE_SQL_NAME(item.INDEX_NAME);
        END IF;
    END LOOP;
END;
/

UPDATE STUDENT
   SET NORMALIZED_CORPORATE_USER = CASE
       WHEN CORPORATE_USER IS NULL THEN NULL
       ELSE UPPER(TRIM(CORPORATE_USER))
   END
 WHERE NVL(NORMALIZED_CORPORATE_USER, '#NULL#') <>
       NVL(CASE WHEN CORPORATE_USER IS NULL THEN NULL ELSE UPPER(TRIM(CORPORATE_USER)) END, '#NULL#');

-- La unicidad fisica queda exactamente en el alcance funcional solicitado:
-- ORGANIZATION_ID + NORMALIZED_CORPORATE_USER.
DECLARE
    v_equivalent NUMBER;
    v_name_collision NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_equivalent
      FROM USER_INDEXES i
     WHERE i.TABLE_NAME = 'STUDENT'
       AND i.UNIQUENESS = 'UNIQUE'
       AND (SELECT COUNT(*) FROM USER_IND_COLUMNS c
             WHERE c.INDEX_NAME = i.INDEX_NAME AND c.TABLE_NAME = i.TABLE_NAME) = 2
       AND EXISTS (SELECT 1 FROM USER_IND_COLUMNS c
                    WHERE c.INDEX_NAME = i.INDEX_NAME AND c.TABLE_NAME = i.TABLE_NAME
                      AND c.COLUMN_POSITION = 1 AND c.COLUMN_NAME = 'ORGANIZATION_ID')
       AND EXISTS (SELECT 1 FROM USER_IND_COLUMNS c
                    WHERE c.INDEX_NAME = i.INDEX_NAME AND c.TABLE_NAME = i.TABLE_NAME
                      AND c.COLUMN_POSITION = 2 AND c.COLUMN_NAME = 'NORMALIZED_CORPORATE_USER');

    IF v_equivalent = 0 THEN
        SELECT COUNT(*) INTO v_name_collision
          FROM USER_OBJECTS
         WHERE OBJECT_NAME = 'UK_STUDENT_ORG_CORP_USER';
        IF v_name_collision > 0 THEN
            RAISE_APPLICATION_ERROR(-20069,
                'Existe UK_STUDENT_ORG_CORP_USER con una estructura distinta a la esperada.');
        END IF;
        EXECUTE IMMEDIATE
            'CREATE UNIQUE INDEX UK_STUDENT_ORG_CORP_USER ON STUDENT (ORGANIZATION_ID, NORMALIZED_CORPORATE_USER)';
    END IF;
END;
/

-- Las dos relaciones historicas por PROFILE_CODE no pueden representar dos
-- perfiles tecnologicos con el mismo codigo en organizaciones distintas. Antes
-- de retirarlas se instala una validacion de alcance equivalente que permite
-- GLOBAL o la misma organizacion, pero nunca otra organizacion.
CREATE OR REPLACE TRIGGER TRG_CERT_PROFILE_TECH_SCOPE
BEFORE INSERT OR UPDATE OF SUGGESTED_TECH_PROFILE, CONTENT_SCOPE, OWNER_ORGANIZATION_ID
ON CERTIFICATION_PROFILE_CATALOG
FOR EACH ROW
WHEN (NEW.SUGGESTED_TECH_PROFILE IS NOT NULL)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM TECHNOLOGICAL_PROFILE_CATALOG tech
     WHERE UPPER(tech.PROFILE_CODE) = UPPER(:NEW.SUGGESTED_TECH_PROFILE)
       AND (
            (:NEW.CONTENT_SCOPE = 'GLOBAL' AND tech.CONTENT_SCOPE = 'GLOBAL')
            OR
            (:NEW.CONTENT_SCOPE = 'ORGANIZATION' AND
                (tech.CONTENT_SCOPE = 'GLOBAL'
                 OR (tech.CONTENT_SCOPE = 'ORGANIZATION'
                     AND tech.OWNER_ORGANIZATION_ID = :NEW.OWNER_ORGANIZATION_ID)))
       );
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20067,
            'El perfil tecnologico sugerido no pertenece al alcance del perfil profesional.');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER TRG_STUDENT_CERT_TECH_SCOPE
BEFORE INSERT OR UPDATE OF TECHNOLOGICAL_PROFILE, ORGANIZATION_ID
ON STUDENT_CERTIFICATION_PROFILE
FOR EACH ROW
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM TECHNOLOGICAL_PROFILE_CATALOG tech
     WHERE UPPER(tech.PROFILE_CODE) = UPPER(:NEW.TECHNOLOGICAL_PROFILE)
       AND (tech.CONTENT_SCOPE = 'GLOBAL'
            OR (tech.CONTENT_SCOPE = 'ORGANIZATION'
                AND tech.OWNER_ORGANIZATION_ID = :NEW.ORGANIZATION_ID));
    IF v_count = 0 THEN
        RAISE_APPLICATION_ERROR(-20068,
            'El perfil tecnologico no pertenece a la organizacion de la configuracion de certificacion.');
    END IF;
END;
/

-- Retira FKs por codigo que apunten desde las columnas legadas al codigo del
-- perfil tecnologico. La deteccion usa metadatos de columnas y tabla referida,
-- no solamente los nombres historicos de las restricciones.
DECLARE
    PROCEDURE drop_code_fk(p_child_table VARCHAR2, p_child_column VARCHAR2) IS
    BEGIN
        FOR item IN (
            SELECT fk.CONSTRAINT_NAME
              FROM USER_CONSTRAINTS fk
              JOIN USER_CONS_COLUMNS child_col
                ON child_col.CONSTRAINT_NAME = fk.CONSTRAINT_NAME
               AND child_col.TABLE_NAME = fk.TABLE_NAME
              JOIN USER_CONSTRAINTS parent
                ON parent.CONSTRAINT_NAME = fk.R_CONSTRAINT_NAME
              JOIN USER_CONS_COLUMNS parent_col
                ON parent_col.CONSTRAINT_NAME = parent.CONSTRAINT_NAME
               AND parent_col.TABLE_NAME = parent.TABLE_NAME
             WHERE fk.CONSTRAINT_TYPE = 'R'
               AND fk.TABLE_NAME = UPPER(p_child_table)
               AND child_col.COLUMN_NAME = UPPER(p_child_column)
               AND parent.TABLE_NAME = 'TECHNOLOGICAL_PROFILE_CATALOG'
               AND parent_col.COLUMN_NAME = 'PROFILE_CODE'
        ) LOOP
            EXECUTE IMMEDIATE 'ALTER TABLE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_child_table)
                || ' DROP CONSTRAINT ' || DBMS_ASSERT.SIMPLE_SQL_NAME(item.CONSTRAINT_NAME);
        END LOOP;
    END;
BEGIN
    drop_code_fk('CERTIFICATION_PROFILE_CATALOG', 'SUGGESTED_TECH_PROFILE');
    drop_code_fk('STUDENT_CERTIFICATION_PROFILE', 'TECHNOLOGICAL_PROFILE');
END;
/

-- Retira cualquier unicidad residual de una sola columna que siga haciendo los
-- codigos de catalogo globales. Los indices de alcance de V041 y V056 se dejan
-- intactos y siguen imponiendo unicidad dentro de GLOBAL o de cada organizacion.
DECLARE
    PROCEDURE drop_single_column_unique(p_table VARCHAR2, p_column VARCHAR2) IS
        v_constraint_name USER_CONSTRAINTS.CONSTRAINT_NAME%TYPE;
    BEGIN
        FOR item IN (
            SELECT i.INDEX_NAME
              FROM USER_INDEXES i
             WHERE i.TABLE_NAME = UPPER(p_table)
               AND i.UNIQUENESS = 'UNIQUE'
               AND (SELECT COUNT(*)
                      FROM USER_IND_COLUMNS c
                     WHERE c.INDEX_NAME = i.INDEX_NAME
                       AND c.TABLE_NAME = i.TABLE_NAME) = 1
               AND EXISTS (
                    SELECT 1
                      FROM USER_IND_COLUMNS c
                     WHERE c.INDEX_NAME = i.INDEX_NAME
                       AND c.TABLE_NAME = i.TABLE_NAME
                       AND c.COLUMN_POSITION = 1
                       AND c.COLUMN_NAME = UPPER(p_column)
               )
        ) LOOP
            SELECT MAX(CONSTRAINT_NAME) INTO v_constraint_name
              FROM USER_CONSTRAINTS
             WHERE TABLE_NAME = UPPER(p_table)
               AND INDEX_NAME = item.INDEX_NAME
               AND CONSTRAINT_TYPE = 'U';

            IF v_constraint_name IS NOT NULL THEN
                EXECUTE IMMEDIATE 'ALTER TABLE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p_table)
                    || ' DROP CONSTRAINT ' || DBMS_ASSERT.SIMPLE_SQL_NAME(v_constraint_name);
            ELSE
                EXECUTE IMMEDIATE 'DROP INDEX ' || DBMS_ASSERT.SIMPLE_SQL_NAME(item.INDEX_NAME);
            END IF;
        END LOOP;
    END;
BEGIN
    drop_single_column_unique('CERTIFICATION_PROFILE_CATALOG', 'PROFILE_CODE');
    drop_single_column_unique('TECHNOLOGICAL_PROFILE_CATALOG', 'PROFILE_CODE');
    drop_single_column_unique('QUESTION_TECHNOLOGY', 'TECHNOLOGY_CODE');
END;
/

COMMIT;
