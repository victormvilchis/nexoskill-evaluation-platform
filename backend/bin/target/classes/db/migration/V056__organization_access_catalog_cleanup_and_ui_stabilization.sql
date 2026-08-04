-- La vigencia operativa ahora se resuelve desde ORGANIZATION en los servicios de
-- autenticación. Las fechas individuales se conservan intactas por compatibilidad
-- e historial; esta migración se limita a consolidar catálogos equivalentes y a
-- normalizar los códigos generados mediante importación.

-- Perfil profesional: conserva todas las relaciones antes de retirar duplicados
-- cuyo nombre solo difiere por mayúsculas, minúsculas o espacios laterales.
DECLARE
    CURSOR duplicates IS
        SELECT duplicate_id, canonical_id
          FROM (
                SELECT CERTIFICATION_PROFILE_ID duplicate_id,
                       MIN(CERTIFICATION_PROFILE_ID) OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(PROFILE_NAME))
                       ) canonical_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(PROFILE_NAME))
                           ORDER BY CERTIFICATION_PROFILE_ID
                       ) position_no
                  FROM CERTIFICATION_PROFILE_CATALOG
               )
         WHERE position_no > 1;
BEGIN
    FOR item IN duplicates LOOP
        UPDATE STUDENT
           SET PROFESSIONAL_PROFILE_ID = item.canonical_id
         WHERE PROFESSIONAL_PROFILE_ID = item.duplicate_id;

        UPDATE STUDENT_CERTIFICATION_PROFILE
           SET PROFESSIONAL_PROFILE_ID = item.canonical_id
         WHERE PROFESSIONAL_PROFILE_ID = item.duplicate_id;

        DELETE FROM CERTIFICATION_PROFILE_CATALOG
         WHERE CERTIFICATION_PROFILE_ID = item.duplicate_id;
    END LOOP;
END;
/

-- Perfil tecnológico: además del identificador principal, conserva las relaciones
-- históricas que utilizan PROFILE_CODE como llave foránea.
DECLARE
    CURSOR duplicates IS
        SELECT duplicate_id, canonical_id, duplicate_code, canonical_code
          FROM (
                SELECT TECHNOLOGICAL_PROFILE_ID duplicate_id,
                       MIN(TECHNOLOGICAL_PROFILE_ID) OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(PROFILE_NAME))
                       ) canonical_id,
                       PROFILE_CODE duplicate_code,
                       FIRST_VALUE(PROFILE_CODE) OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(PROFILE_NAME))
                           ORDER BY TECHNOLOGICAL_PROFILE_ID
                       ) canonical_code,
                       ROW_NUMBER() OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(PROFILE_NAME))
                           ORDER BY TECHNOLOGICAL_PROFILE_ID
                       ) position_no
                  FROM TECHNOLOGICAL_PROFILE_CATALOG
               )
         WHERE position_no > 1;
BEGIN
    FOR item IN duplicates LOOP
        UPDATE STUDENT
           SET TECHNOLOGICAL_PROFILE_ID = item.canonical_id
         WHERE TECHNOLOGICAL_PROFILE_ID = item.duplicate_id;

        UPDATE CERTIFICATION_PROFILE_CATALOG
           SET SUGGESTED_TECH_PROFILE = item.canonical_code,
               UPDATED_AT = SYSTIMESTAMP,
               VERSION_NO = VERSION_NO + 1
         WHERE SUGGESTED_TECH_PROFILE = item.duplicate_code;

        UPDATE STUDENT_CERTIFICATION_PROFILE
           SET TECHNOLOGICAL_PROFILE = item.canonical_code,
               UPDATED_AT = SYSTIMESTAMP,
               VERSION_NO = VERSION_NO + 1
         WHERE TECHNOLOGICAL_PROFILE = item.duplicate_code;

        DELETE FROM TECHNOLOGICAL_PROFILE_CATALOG
         WHERE TECHNOLOGICAL_PROFILE_ID = item.duplicate_id;
    END LOOP;
END;
/

-- Tecnología maestra: conserva todas las relaciones antes de retirar duplicados.
DECLARE
    CURSOR duplicates IS
        SELECT duplicate_id, canonical_id
          FROM (
                SELECT TECHNOLOGY_ID duplicate_id,
                       MIN(TECHNOLOGY_ID) OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(TECHNOLOGY_NAME))
                       ) canonical_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1),
                                        UPPER(TRIM(TECHNOLOGY_NAME))
                           ORDER BY TECHNOLOGY_ID
                       ) position_no
                  FROM QUESTION_TECHNOLOGY
               )
         WHERE position_no > 1;
BEGIN
    FOR item IN duplicates LOOP
        UPDATE STUDENT
           SET TECHNOLOGY_ID = item.canonical_id
         WHERE TECHNOLOGY_ID = item.duplicate_id;

        UPDATE STUDENT
           SET TALENT_TECHNOLOGY_ID = item.canonical_id
         WHERE TALENT_TECHNOLOGY_ID = item.duplicate_id;

        UPDATE QUESTION
           SET TECHNOLOGY_ID = item.canonical_id
         WHERE TECHNOLOGY_ID = item.duplicate_id;

        UPDATE CERTIFICATION_TECHNOLOGY_CATALOG
           SET MASTER_TECHNOLOGY_ID = item.canonical_id,
               UPDATED_AT = SYSTIMESTAMP,
               VERSION_NO = VERSION_NO + 1
         WHERE MASTER_TECHNOLOGY_ID = item.duplicate_id;

        UPDATE STUDENT_CERTIFICATION_CYCLE
           SET TECHNOLOGY_ID = item.canonical_id,
               UPDATED_AT = SYSTIMESTAMP,
               VERSION_NO = VERSION_NO + 1
         WHERE TECHNOLOGY_ID = item.duplicate_id;

        DELETE FROM QUESTION_TECHNOLOGY
         WHERE TECHNOLOGY_ID = item.duplicate_id;
    END LOOP;
END;
/

-- Sustituye códigos aleatorios por códigos legibles. Ante una colisión real entre
-- conceptos distintos se utiliza el identificador numérico estable, nunca una
-- cadena aleatoria.
DECLARE
    v_profile_code VARCHAR2(120);
    v_technology_code VARCHAR2(80);

    FUNCTION normalized_code(p_prefix VARCHAR2, p_name VARCHAR2, p_max NUMBER, p_id NUMBER,
                             p_table VARCHAR2, p_code_column VARCHAR2,
                             p_scope VARCHAR2, p_owner NUMBER) RETURN VARCHAR2 IS
        v_base VARCHAR2(4000);
        v_code VARCHAR2(4000);
        v_count NUMBER;
        v_id_column VARCHAR2(80);
    BEGIN
        v_base := REGEXP_REPLACE(UPPER(TRIM(TRANSLATE(p_name,
            'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNAEIOUUN'))), '[^A-Z0-9]+', '_');
        v_base := REGEXP_REPLACE(v_base, '^_+|_+$', '');
        IF v_base IS NULL THEN
            v_base := 'CATALOGO';
        END IF;

        v_code := SUBSTR(p_prefix || '_' || v_base, 1, p_max);
        v_id_column := CASE p_table
            WHEN 'CERTIFICATION_PROFILE_CATALOG' THEN 'CERTIFICATION_PROFILE_ID'
            WHEN 'TECHNOLOGICAL_PROFILE_CATALOG' THEN 'TECHNOLOGICAL_PROFILE_ID'
            WHEN 'QUESTION_TECHNOLOGY' THEN 'TECHNOLOGY_ID'
            ELSE NULL
        END;
        IF v_id_column IS NULL THEN
            RAISE_APPLICATION_ERROR(-20056, 'Catálogo no soportado para normalización: ' || p_table);
        END IF;

        IF p_table = 'TECHNOLOGICAL_PROFILE_CATALOG' THEN
            -- PROFILE_CODE conserva una llave única global porque existen relaciones
            -- históricas basadas en código. Las colisiones reales usan un sufijo
            -- numérico estable hasta que esas relaciones migren a identificadores.
            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || p_table ||
                ' WHERE UPPER(' || p_code_column || ') = UPPER(:code)' ||
                ' AND ' || v_id_column || ' <> :id'
                INTO v_count USING v_code, p_id;
        ELSE
            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || p_table ||
                ' WHERE CONTENT_SCOPE = :scope' ||
                ' AND NVL(OWNER_ORGANIZATION_ID,-1) = NVL(:owner,-1)' ||
                ' AND UPPER(' || p_code_column || ') = UPPER(:code)' ||
                ' AND ' || v_id_column || ' <> :id'
                INTO v_count USING p_scope, p_owner, v_code, p_id;
        END IF;

        IF v_count > 0 THEN
            v_code := SUBSTR(v_code, 1,
                GREATEST(1, p_max - LENGTH(TO_CHAR(p_id)) - 1)) || '_' || TO_CHAR(p_id);
        END IF;
        RETURN v_code;
    END;
BEGIN
    FOR item IN (
        SELECT CERTIFICATION_PROFILE_ID id, PROFILE_NAME name,
               CONTENT_SCOPE scope_value, OWNER_ORGANIZATION_ID owner_id
          FROM CERTIFICATION_PROFILE_CATALOG
         WHERE CONTENT_SCOPE = 'ORGANIZATION'
         ORDER BY CERTIFICATION_PROFILE_ID
    ) LOOP
        v_profile_code := normalized_code('PRF', item.name, 120, item.id,
                'CERTIFICATION_PROFILE_CATALOG', 'PROFILE_CODE',
                item.scope_value, item.owner_id);

        UPDATE CERTIFICATION_PROFILE_CATALOG
           SET PROFILE_CODE = v_profile_code,
               UPDATED_AT = SYSTIMESTAMP,
               VERSION_NO = VERSION_NO + 1
         WHERE CERTIFICATION_PROFILE_ID = item.id;
    END LOOP;

    -- Dos relaciones históricas apuntan a PROFILE_CODE. Se deshabilitan solo
    -- durante la actualización coordinada del padre y sus referencias, y se
    -- reactivan con VALIDATE antes de concluir la migración.
    DECLARE
        v_cert_fk_enabled NUMBER := 0;
        v_student_fk_enabled NUMBER := 0;
        v_new_code VARCHAR2(40);

        PROCEDURE enable_profile_constraints IS
        BEGIN
            IF v_cert_fk_enabled = 1 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE CERTIFICATION_PROFILE_CATALOG ENABLE VALIDATE CONSTRAINT FK_CERT_PROFILE_TECH_PROF';
            END IF;
            IF v_student_fk_enabled = 1 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_CERTIFICATION_PROFILE ENABLE VALIDATE CONSTRAINT FK_STUDENT_CERT_TECH_PROF';
            END IF;
        END;
    BEGIN
        SELECT COUNT(*) INTO v_cert_fk_enabled
          FROM USER_CONSTRAINTS
         WHERE CONSTRAINT_NAME = 'FK_CERT_PROFILE_TECH_PROF'
           AND TABLE_NAME = 'CERTIFICATION_PROFILE_CATALOG'
           AND STATUS = 'ENABLED';
        SELECT COUNT(*) INTO v_student_fk_enabled
          FROM USER_CONSTRAINTS
         WHERE CONSTRAINT_NAME = 'FK_STUDENT_CERT_TECH_PROF'
           AND TABLE_NAME = 'STUDENT_CERTIFICATION_PROFILE'
           AND STATUS = 'ENABLED';

        IF v_cert_fk_enabled = 1 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CERTIFICATION_PROFILE_CATALOG DISABLE CONSTRAINT FK_CERT_PROFILE_TECH_PROF';
        END IF;
        IF v_student_fk_enabled = 1 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE STUDENT_CERTIFICATION_PROFILE DISABLE CONSTRAINT FK_STUDENT_CERT_TECH_PROF';
        END IF;

        BEGIN
            FOR item IN (
                SELECT TECHNOLOGICAL_PROFILE_ID id, PROFILE_CODE old_code, PROFILE_NAME name,
                       CONTENT_SCOPE scope_value, OWNER_ORGANIZATION_ID owner_id
                  FROM TECHNOLOGICAL_PROFILE_CATALOG
                 WHERE CONTENT_SCOPE = 'ORGANIZATION'
                 ORDER BY TECHNOLOGICAL_PROFILE_ID
            ) LOOP
                v_new_code := normalized_code('TPR', item.name, 40, item.id,
                        'TECHNOLOGICAL_PROFILE_CATALOG', 'PROFILE_CODE',
                        item.scope_value, item.owner_id);
                IF item.old_code <> v_new_code THEN
                    UPDATE TECHNOLOGICAL_PROFILE_CATALOG
                       SET PROFILE_CODE = v_new_code,
                           UPDATED_AT = SYSTIMESTAMP,
                           VERSION_NO = VERSION_NO + 1
                     WHERE TECHNOLOGICAL_PROFILE_ID = item.id;
                    UPDATE CERTIFICATION_PROFILE_CATALOG
                       SET SUGGESTED_TECH_PROFILE = v_new_code,
                           UPDATED_AT = SYSTIMESTAMP,
                           VERSION_NO = VERSION_NO + 1
                     WHERE SUGGESTED_TECH_PROFILE = item.old_code;
                    UPDATE STUDENT_CERTIFICATION_PROFILE
                       SET TECHNOLOGICAL_PROFILE = v_new_code,
                           UPDATED_AT = SYSTIMESTAMP,
                           VERSION_NO = VERSION_NO + 1
                     WHERE TECHNOLOGICAL_PROFILE = item.old_code;
                END IF;
            END LOOP;
        EXCEPTION
            WHEN OTHERS THEN
                enable_profile_constraints;
                RAISE;
        END;

        enable_profile_constraints;
    END;

    FOR item IN (
        SELECT TECHNOLOGY_ID id, TECHNOLOGY_NAME name,
               CONTENT_SCOPE scope_value, OWNER_ORGANIZATION_ID owner_id
          FROM QUESTION_TECHNOLOGY
         WHERE CONTENT_SCOPE = 'ORGANIZATION'
         ORDER BY TECHNOLOGY_ID
    ) LOOP
        v_technology_code := normalized_code('TEC', item.name, 80, item.id,
                'QUESTION_TECHNOLOGY', 'TECHNOLOGY_CODE',
                item.scope_value, item.owner_id);

        UPDATE QUESTION_TECHNOLOGY
           SET TECHNOLOGY_CODE = v_technology_code,
               UPDATED_AT = SYSTIMESTAMP,
               VERSION_NO = VERSION_NO + 1
         WHERE TECHNOLOGY_ID = item.id;
    END LOOP;
END;
/

-- Impide que vuelvan a crearse conceptos equivalentes por diferencias de
-- mayúsculas, minúsculas o espacios dentro del mismo alcance.
BEGIN
    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_CERT_PROFILE_SCOPE_NAME_CI ON CERTIFICATION_PROFILE_CATALOG (CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1), UPPER(TRIM(PROFILE_NAME)))';
EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_TECH_PROFILE_SCOPE_NAME_CI ON TECHNOLOGICAL_PROFILE_CATALOG (CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1), UPPER(TRIM(PROFILE_NAME)))';
EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
BEGIN
    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX UK_QUESTION_TECH_SCOPE_NAME_CI ON QUESTION_TECHNOLOGY (CONTENT_SCOPE, NVL(OWNER_ORGANIZATION_ID, -1), UPPER(TRIM(TECHNOLOGY_NAME)))';
EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

COMMIT;
