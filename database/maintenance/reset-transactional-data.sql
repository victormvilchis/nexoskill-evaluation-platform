SET SERVEROUTPUT ON SIZE UNLIMITED
SET VERIFY OFF
SET FEEDBACK OFF
SET HEADING OFF
SET PAGESIZE 0
SET LINESIZE 240
WHENEVER SQLERROR EXIT SQL.SQLCODE ROLLBACK

DECLARE
    c_admin_email CONSTANT VARCHAR2(254) := LOWER(TRIM('&&preserve_admin_email'));
    c_execute     CONSTANT VARCHAR2(10)  := UPPER(TRIM('&&execute_reset'));

    v_admin_id      APP_USER.USER_ID%TYPE;
    v_admin_count   PLS_INTEGER;
    v_admin_role_id APP_ROLE.ROLE_ID%TYPE;
    v_global_id     ORGANIZATION.ORGANIZATION_ID%TYPE;
    v_global_count  PLS_INTEGER;
    v_deleted       PLS_INTEGER;
    v_total_deleted PLS_INTEGER := 0;
    v_remaining     PLS_INTEGER;
    v_progress      PLS_INTEGER;
    v_count         PLS_INTEGER;
    v_sql           VARCHAR2(32767);

    TYPE t_name_list IS TABLE OF VARCHAR2(128);
    TYPE t_flag_list IS TABLE OF PLS_INTEGER INDEX BY PLS_INTEGER;
    l_tables t_name_list;
    l_pending t_flag_list;

    FUNCTION table_exists(p_table_name VARCHAR2) RETURN BOOLEAN IS
        l_count PLS_INTEGER;
    BEGIN
        SELECT COUNT(*)
          INTO l_count
          FROM USER_TABLES
         WHERE UPPER(TABLE_NAME) = UPPER(p_table_name);
        RETURN l_count = 1;
    END;

    FUNCTION column_exists(p_table_name VARCHAR2, p_column_name VARCHAR2) RETURN BOOLEAN IS
        l_count PLS_INTEGER;
    BEGIN
        SELECT COUNT(*)
          INTO l_count
          FROM USER_TAB_COLUMNS
         WHERE UPPER(TABLE_NAME) = UPPER(p_table_name)
           AND UPPER(COLUMN_NAME) = UPPER(p_column_name);
        RETURN l_count = 1;
    END;

    PROCEDURE null_column_if_exists(p_table_name VARCHAR2, p_column_name VARCHAR2) IS
        l_table  VARCHAR2(128) := DBMS_ASSERT.SIMPLE_SQL_NAME(UPPER(p_table_name));
        l_column VARCHAR2(128) := DBMS_ASSERT.SIMPLE_SQL_NAME(UPPER(p_column_name));
    BEGIN
        IF table_exists(l_table) AND column_exists(l_table, l_column) THEN
            EXECUTE IMMEDIATE
                'UPDATE ' || l_table || ' SET ' || l_column || ' = NULL WHERE ' || l_column || ' IS NOT NULL';
        END IF;
    END;

    PROCEDURE print_count(p_label VARCHAR2, p_sql VARCHAR2) IS
        l_count PLS_INTEGER;
    BEGIN
        EXECUTE IMMEDIATE p_sql INTO l_count;
        DBMS_OUTPUT.PUT_LINE(RPAD(p_label, 48, ' ') || TO_CHAR(l_count));
    EXCEPTION
        WHEN OTHERS THEN
            RAISE_APPLICATION_ERROR(
                -20109,
                'No se pudo consultar ' || TRIM(p_label) || '. SQL: ' ||
                SUBSTR(p_sql, 1, 1000) || '. Causa: ' || SQLERRM,
                TRUE
            );
    END;

    PROCEDURE delete_organization_scoped_catalog(p_table_name VARCHAR2) IS
        l_table VARCHAR2(128) := DBMS_ASSERT.SIMPLE_SQL_NAME(UPPER(p_table_name));
    BEGIN
        IF table_exists(l_table)
           AND column_exists(l_table, 'CONTENT_SCOPE')
           AND column_exists(l_table, 'OWNER_ORGANIZATION_ID') THEN
            EXECUTE IMMEDIATE
                'DELETE FROM ' || l_table ||
                ' WHERE NVL(CONTENT_SCOPE, ''GLOBAL'') <> ''GLOBAL'' OR OWNER_ORGANIZATION_ID IS NOT NULL';
            v_deleted := SQL%ROWCOUNT;
            v_total_deleted := v_total_deleted + v_deleted;
            DBMS_OUTPUT.PUT_LINE('  ' || RPAD(l_table, 46, ' ') || TO_CHAR(v_deleted));
        END IF;
    END;

    PROCEDURE sanitize_preserved_user_references IS
    BEGIN
        FOR fk IN (
            SELECT child.TABLE_NAME,
                   child_column.COLUMN_NAME,
                   tab_column.NULLABLE
              FROM USER_CONSTRAINTS child
              JOIN USER_CONS_COLUMNS child_column
                ON child_column.CONSTRAINT_NAME = child.CONSTRAINT_NAME
               AND child_column.TABLE_NAME = child.TABLE_NAME
              JOIN USER_CONSTRAINTS parent
                ON parent.CONSTRAINT_NAME = child.R_CONSTRAINT_NAME
              JOIN USER_TAB_COLUMNS tab_column
                ON tab_column.TABLE_NAME = child.TABLE_NAME
               AND tab_column.COLUMN_NAME = child_column.COLUMN_NAME
             WHERE child.CONSTRAINT_TYPE = 'R'
               AND parent.TABLE_NAME = 'APP_USER'
               AND child.TABLE_NAME IN (
                   'APP_USER',
                   'ORGANIZATION',
                   'QUESTION_TYPE_CATALOG',
                   'QUESTION_DIFFICULTY_CATALOG',
                   'CERTIFICATION_PROFILE_CATALOG',
                   'CERTIFICATION_TECHNOLOGY_CATALOG',
                   'TECHNOLOGICAL_PROFILE_CATALOG',
                   'QUESTION_TECHNOLOGY',
                   'QUESTION_CATEGORY',
                   'QUESTION_TAG'
               )
               AND 1 = (
                   SELECT COUNT(*)
                     FROM USER_CONS_COLUMNS single_column
                    WHERE single_column.CONSTRAINT_NAME = child.CONSTRAINT_NAME
                      AND single_column.TABLE_NAME = child.TABLE_NAME
               )
        ) LOOP
            IF fk.TABLE_NAME = 'APP_USER' THEN
                IF fk.NULLABLE = 'Y' THEN
                    v_sql := 'UPDATE APP_USER SET ' ||
                             DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                             ' = NULL WHERE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                             ' IS NOT NULL AND ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                             ' <> :admin_id';
                    EXECUTE IMMEDIATE v_sql USING v_admin_id;
                ELSE
                    v_sql := 'UPDATE APP_USER SET ' ||
                             DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                             ' = :admin_id WHERE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                             ' <> :admin_id';
                    EXECUTE IMMEDIATE v_sql USING v_admin_id, v_admin_id;
                END IF;
            ELSE
                v_sql := 'UPDATE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.TABLE_NAME) ||
                         ' SET ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                         ' = :admin_id WHERE ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                         ' IS NOT NULL AND ' || DBMS_ASSERT.SIMPLE_SQL_NAME(fk.COLUMN_NAME) ||
                         ' <> :admin_id';
                EXECUTE IMMEDIATE v_sql USING v_admin_id, v_admin_id;
            END IF;
        END LOOP;
    END;

BEGIN
    IF UPPER(USER) <> 'EVALUATION_APP' THEN
        RAISE_APPLICATION_ERROR(
            -20099,
            'La limpieza solo puede ejecutarse conectado al esquema EVALUATION_APP. Esquema actual: ' || USER
        );
    END IF;

    IF c_admin_email IS NULL OR c_admin_email NOT LIKE '%@%' THEN
        RAISE_APPLICATION_ERROR(-20100, 'El correo del administrador a conservar no es valido.');
    END IF;

    SELECT COUNT(*), MIN(USER_ID)
      INTO v_admin_count, v_admin_id
      FROM APP_USER
     WHERE LOWER(TRIM(EMAIL)) = c_admin_email
        OR LOWER(TRIM(NORMALIZED_EMAIL)) = c_admin_email;

    IF v_admin_count <> 1 THEN
        RAISE_APPLICATION_ERROR(
            -20101,
            'Debe existir exactamente un usuario con el correo ' || c_admin_email ||
            '. Coincidencias encontradas: ' || v_admin_count
        );
    END IF;

    SELECT COUNT(*), MIN(ORGANIZATION_ID)
      INTO v_global_count, v_global_id
      FROM ORGANIZATION
     WHERE ORGANIZATION_CODE = 'GLOBAL'
       AND ORGANIZATION_TYPE = 'GLOBAL';

    IF v_global_count <> 1 THEN
        RAISE_APPLICATION_ERROR(
            -20102,
            'Debe existir exactamente una organizacion tecnica GLOBAL. Coincidencias encontradas: ' ||
            v_global_count
        );
    END IF;

    SELECT ROLE_ID
      INTO v_admin_role_id
      FROM APP_ROLE
     WHERE ROLE_CODE = 'ADMINISTRATOR';

    DBMS_OUTPUT.PUT_LINE('============================================================');
    DBMS_OUTPUT.PUT_LINE('NEXOSKILL - LIMPIEZA TRANSACCIONAL CONTROLADA');
    DBMS_OUTPUT.PUT_LINE('============================================================');
    DBMS_OUTPUT.PUT_LINE('Esquema:                ' || USER);
    DBMS_OUTPUT.PUT_LINE('Administrador conservado: ' || c_admin_email || ' (USER_ID=' || v_admin_id || ')');
    DBMS_OUTPUT.PUT_LINE('Organizacion conservada:  GLOBAL (ORGANIZATION_ID=' || v_global_id || ')');
    DBMS_OUTPUT.PUT_LINE('Modo:                    ' || CASE WHEN c_execute = 'YES' THEN 'EJECUCION' ELSE 'VISTA PREVIA' END);
    DBMS_OUTPUT.PUT_LINE('');

    SELECT TABLE_NAME
      BULK COLLECT INTO l_tables
      FROM USER_TABLES
     WHERE TABLE_NAME NOT LIKE 'BIN$%'
       AND NVL(NESTED, 'NO') = 'NO'
       AND NVL(SECONDARY, 'N') = 'N'
       AND IOT_TYPE IS NULL
       AND NVL(TEMPORARY, 'N') = 'N'
       AND NVL(EXTERNAL, 'NO') = 'NO'
       AND UPPER(TABLE_NAME) NOT IN (
           'FLYWAY_SCHEMA_HISTORY',
           'APP_ROLE',
           'APP_PERMISSION',
           'APP_ROLE_PERMISSION',
           'APP_USER',
           'APP_USER_ROLE',
           'APP_USER_ORGANIZATION',
           'USER_ACCESS',
           'ORGANIZATION',
           'QUESTION_TYPE_CATALOG',
           'QUESTION_DIFFICULTY_CATALOG',
           'CERTIFICATION_PROFILE_CATALOG',
           'CERTIFICATION_TECHNOLOGY_CATALOG',
           'TECHNOLOGICAL_PROFILE_CATALOG',
           'QUESTION_TECHNOLOGY',
           'QUESTION_CATEGORY',
           'QUESTION_TAG'
       )
     ORDER BY TABLE_NAME;

    DBMS_OUTPUT.PUT_LINE('Objetos tecnicos de Oracle excluidos de la limpieza directa:');
    v_count := 0;
    FOR technical_table IN (
        SELECT TABLE_NAME,
               NVL(NESTED, 'NO') AS NESTED_FLAG,
               NVL(SECONDARY, 'N') AS SECONDARY_FLAG,
               NVL(IOT_TYPE, '-') AS IOT_FLAG,
               NVL(TEMPORARY, 'N') AS TEMPORARY_FLAG,
               NVL(EXTERNAL, 'NO') AS EXTERNAL_FLAG
          FROM USER_TABLES
         WHERE TABLE_NAME NOT LIKE 'BIN$%'
           AND (
               NVL(NESTED, 'NO') <> 'NO'
               OR NVL(SECONDARY, 'N') <> 'N'
               OR IOT_TYPE IS NOT NULL
               OR NVL(TEMPORARY, 'N') <> 'N'
               OR NVL(EXTERNAL, 'NO') <> 'NO'
           )
         ORDER BY TABLE_NAME
    ) LOOP
        v_count := v_count + 1;
        DBMS_OUTPUT.PUT_LINE(
            '  ' || technical_table.TABLE_NAME ||
            ' [NESTED=' || technical_table.NESTED_FLAG ||
            ', SECONDARY=' || technical_table.SECONDARY_FLAG ||
            ', IOT=' || technical_table.IOT_FLAG ||
            ', TEMPORARY=' || technical_table.TEMPORARY_FLAG ||
            ', EXTERNAL=' || technical_table.EXTERNAL_FLAG || ']'
        );
    END LOOP;
    IF v_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('  Ninguno');
    END IF;
    DBMS_OUTPUT.PUT_LINE('');

    DBMS_OUTPUT.PUT_LINE('Registros que se limpiaran por completo:');
    FOR i IN 1 .. l_tables.COUNT LOOP
        print_count('  ' || l_tables(i),
                    'SELECT COUNT(*) FROM ' || DBMS_ASSERT.ENQUOTE_NAME(l_tables(i), FALSE));
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('Registros especiales que se retiraran:');
    print_count('  Usuarios distintos del administrador',
                'SELECT COUNT(*) FROM APP_USER WHERE USER_ID <> ' || TO_CHAR(v_admin_id));
    print_count('  Organizaciones distintas de GLOBAL',
                'SELECT COUNT(*) FROM ORGANIZATION WHERE ORGANIZATION_ID <> ' || TO_CHAR(v_global_id));
    print_count('  Roles de usuario actuales', 'SELECT COUNT(*) FROM APP_USER_ROLE');
    print_count('  Asignaciones usuario-organizacion', 'SELECT COUNT(*) FROM APP_USER_ORGANIZATION');
    print_count('  Vigencias de usuarios', 'SELECT COUNT(*) FROM USER_ACCESS');

    FOR catalog_name IN (
        SELECT 'CERTIFICATION_PROFILE_CATALOG' TABLE_NAME FROM DUAL UNION ALL
        SELECT 'TECHNOLOGICAL_PROFILE_CATALOG' FROM DUAL UNION ALL
        SELECT 'QUESTION_TECHNOLOGY' FROM DUAL UNION ALL
        SELECT 'QUESTION_CATEGORY' FROM DUAL UNION ALL
        SELECT 'QUESTION_TAG' FROM DUAL
    ) LOOP
        IF table_exists(catalog_name.TABLE_NAME)
           AND column_exists(catalog_name.TABLE_NAME, 'CONTENT_SCOPE')
           AND column_exists(catalog_name.TABLE_NAME, 'OWNER_ORGANIZATION_ID') THEN
            print_count(
                '  ' || catalog_name.TABLE_NAME || ' organizacionales',
                'SELECT COUNT(*) FROM ' || DBMS_ASSERT.SIMPLE_SQL_NAME(catalog_name.TABLE_NAME) ||
                ' WHERE NVL(CONTENT_SCOPE, ''GLOBAL'') <> ''GLOBAL'' OR OWNER_ORGANIZATION_ID IS NOT NULL'
            );
        END IF;
    END LOOP;

    IF c_execute <> 'YES' THEN
        DBMS_OUTPUT.PUT_LINE('');
        DBMS_OUTPUT.PUT_LINE('Vista previa terminada. No se modifico informacion.');
    ELSE

    DBMS_OUTPUT.PUT_LINE('');
    DBMS_OUTPUT.PUT_LINE('Ejecutando limpieza...');

    -- Rompe ciclos concretos del modelo sin alterar relaciones funcionales ajenas.
    -- QUESTION mantiene una referencia opcional a su version actual mientras QUESTION_VERSION
    -- referencia de regreso a QUESTION. Los campos SOURCE_GLOBAL_ID son autorreferencias opcionales.
    null_column_if_exists('QUESTION', 'CURRENT_VERSION_ID');
    null_column_if_exists('QUESTION', 'PUBLISHED_VERSION_ID');
    null_column_if_exists('QUESTION', 'PROMPT_MEDIA_ID');
    null_column_if_exists('QUESTION', 'SOURCE_GLOBAL_ID');
    null_column_if_exists('EVALUATION_FORM', 'SOURCE_GLOBAL_ID');
    null_column_if_exists('LEARNING_COLLECTION', 'SOURCE_GLOBAL_ID');
    null_column_if_exists('QUESTION_CATEGORY', 'SOURCE_GLOBAL_ID');
    null_column_if_exists('GLOBAL_CONTENT_VERSION', 'PROMOTION_ID');

    FOR i IN 1 .. l_tables.COUNT LOOP
        l_pending(i) := 1;
    END LOOP;

    v_remaining := l_tables.COUNT;
    WHILE v_remaining > 0 LOOP
        v_progress := 0;

        FOR i IN 1 .. l_tables.COUNT LOOP
            IF l_pending(i) = 1 THEN
                BEGIN
                    EXECUTE IMMEDIATE
                        'DELETE FROM ' || DBMS_ASSERT.ENQUOTE_NAME(l_tables(i), FALSE);
                    v_deleted := SQL%ROWCOUNT;
                    v_total_deleted := v_total_deleted + v_deleted;
                    l_pending(i) := 0;
                    v_remaining := v_remaining - 1;
                    v_progress := v_progress + 1;
                    DBMS_OUTPUT.PUT_LINE('  ' || RPAD(l_tables(i), 46, ' ') || TO_CHAR(v_deleted));
                EXCEPTION
                    WHEN OTHERS THEN
                        IF SQLCODE = -2292 THEN
                            NULL;
                        ELSE
                            RAISE_APPLICATION_ERROR(
                                -20110,
                                'No se pudo limpiar la tabla ' || l_tables(i) ||
                                '. Causa: ' || SQLERRM,
                                TRUE
                            );
                        END IF;
                END;
            END IF;
        END LOOP;

        IF v_progress = 0 THEN
            DBMS_OUTPUT.PUT_LINE('No fue posible resolver las dependencias de estas tablas:');
            FOR i IN 1 .. l_tables.COUNT LOOP
                IF l_pending(i) = 1 THEN
                    DBMS_OUTPUT.PUT_LINE('  - ' || l_tables(i));
                END IF;
            END LOOP;
            RAISE_APPLICATION_ERROR(
                -20103,
                'Existen dependencias no contempladas. La transaccion sera revertida.'
            );
        END IF;
    END LOOP;

    -- Retira puentes que apunten a tecnologias organizacionales antes de limpiar esos catalogos.
    IF table_exists('CERTIFICATION_TECHNOLOGY_CATALOG')
       AND table_exists('QUESTION_TECHNOLOGY')
       AND column_exists('CERTIFICATION_TECHNOLOGY_CATALOG', 'MASTER_TECHNOLOGY_ID')
       AND column_exists('QUESTION_TECHNOLOGY', 'CONTENT_SCOPE')
       AND column_exists('QUESTION_TECHNOLOGY', 'OWNER_ORGANIZATION_ID') THEN
        DELETE FROM CERTIFICATION_TECHNOLOGY_CATALOG bridge
         WHERE bridge.MASTER_TECHNOLOGY_ID IN (
             SELECT technology.TECHNOLOGY_ID
               FROM QUESTION_TECHNOLOGY technology
              WHERE NVL(technology.CONTENT_SCOPE, 'GLOBAL') <> 'GLOBAL'
                 OR technology.OWNER_ORGANIZATION_ID IS NOT NULL
         );
        v_total_deleted := v_total_deleted + SQL%ROWCOUNT;
    END IF;

    DBMS_OUTPUT.PUT_LINE('Catalogos organizacionales retirados:');
    delete_organization_scoped_catalog('CERTIFICATION_PROFILE_CATALOG');
    delete_organization_scoped_catalog('TECHNOLOGICAL_PROFILE_CATALOG');
    delete_organization_scoped_catalog('QUESTION_TECHNOLOGY');
    delete_organization_scoped_catalog('QUESTION_CATEGORY');
    delete_organization_scoped_catalog('QUESTION_TAG');

    -- Los catalogos globales y GLOBAL deben dejar de referenciar usuarios que se eliminaran.
    sanitize_preserved_user_references;

    DELETE FROM APP_USER_ORGANIZATION;
    v_total_deleted := v_total_deleted + SQL%ROWCOUNT;

    DELETE FROM USER_ACCESS;
    v_total_deleted := v_total_deleted + SQL%ROWCOUNT;

    DELETE FROM APP_USER_ROLE;
    v_total_deleted := v_total_deleted + SQL%ROWCOUNT;

    DELETE FROM ORGANIZATION
     WHERE ORGANIZATION_ID <> v_global_id;
    v_total_deleted := v_total_deleted + SQL%ROWCOUNT;

    DELETE FROM APP_USER
     WHERE USER_ID <> v_admin_id;
    v_total_deleted := v_total_deleted + SQL%ROWCOUNT;

    UPDATE APP_USER
       SET EMAIL = c_admin_email,
           NORMALIZED_EMAIL = c_admin_email,
           STATUS = 'ACTIVE',
           FAILED_LOGIN_ATTEMPTS = 0,
           LOCKED_UNTIL = NULL,
           LAST_LOGIN_AT = NULL,
           UPDATED_AT = SYSTIMESTAMP,
           STATUS_REASON = NULL,
           STATUS_CHANGED_AT = SYSTIMESTAMP,
           STATUS_CHANGED_BY = NULL,
           DELETED_AT = NULL,
           DELETED_BY = NULL,
           DELETE_REASON = NULL
     WHERE USER_ID = v_admin_id;

    UPDATE ORGANIZATION
       SET PUBLIC_ID = '00000000-0000-0000-0000-000000000001',
           ORGANIZATION_CODE = 'GLOBAL',
           ORGANIZATION_NAME = 'GLOBAL',
           ORGANIZATION_TYPE = 'GLOBAL',
           STATUS = 'ACTIVE',
           CONTENT_MODE = 'GLOBAL_CATALOG',
           VALID_FROM = DATE '2000-01-01',
           EXPIRES_ON = NULL,
           APPLIES_CERTIFICATIONS = 0,
           CREATED_BY = v_admin_id,
           UPDATED_BY = v_admin_id,
           UPDATED_AT = SYSTIMESTAMP
     WHERE ORGANIZATION_ID = v_global_id;

    INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID, ASSIGNED_AT)
    VALUES (v_admin_id, v_admin_role_id, SYSTIMESTAMP);

    INSERT INTO APP_USER_ORGANIZATION (
        USER_ID, ORGANIZATION_ID, STATUS, ASSIGNED_AT, ASSIGNED_BY
    ) VALUES (
        v_admin_id, v_global_id, 'ACTIVE', SYSTIMESTAMP, NULL
    );

    INSERT INTO USER_ACCESS (
        USER_ID, STARTS_AT, EXPIRES_AT, STATUS, CREATED_AT, UPDATED_AT, VERSION_NO
    ) VALUES (
        v_admin_id, SYSTIMESTAMP, NULL, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP, 0
    );

    FOR i IN 1 .. l_tables.COUNT LOOP
        EXECUTE IMMEDIATE
            'SELECT COUNT(*) FROM ' || DBMS_ASSERT.ENQUOTE_NAME(l_tables(i), FALSE)
            INTO v_count;
        IF v_count <> 0 THEN
            RAISE_APPLICATION_ERROR(
                -20104,
                'La tabla transaccional ' || l_tables(i) || ' conserva ' || v_count || ' registros.'
            );
        END IF;
    END LOOP;

    FOR catalog_name IN (
        SELECT 'CERTIFICATION_PROFILE_CATALOG' TABLE_NAME FROM DUAL UNION ALL
        SELECT 'TECHNOLOGICAL_PROFILE_CATALOG' FROM DUAL UNION ALL
        SELECT 'QUESTION_TECHNOLOGY' FROM DUAL UNION ALL
        SELECT 'QUESTION_CATEGORY' FROM DUAL UNION ALL
        SELECT 'QUESTION_TAG' FROM DUAL
    ) LOOP
        IF table_exists(catalog_name.TABLE_NAME)
           AND column_exists(catalog_name.TABLE_NAME, 'CONTENT_SCOPE')
           AND column_exists(catalog_name.TABLE_NAME, 'OWNER_ORGANIZATION_ID') THEN
            EXECUTE IMMEDIATE
                'SELECT COUNT(*) FROM ' || DBMS_ASSERT.SIMPLE_SQL_NAME(catalog_name.TABLE_NAME) ||
                ' WHERE NVL(CONTENT_SCOPE, ''GLOBAL'') <> ''GLOBAL'' OR OWNER_ORGANIZATION_ID IS NOT NULL'
                INTO v_count;
            IF v_count <> 0 THEN
                RAISE_APPLICATION_ERROR(
                    -20105,
                    'El catalogo ' || catalog_name.TABLE_NAME || ' conserva registros organizacionales.'
                );
            END IF;
        END IF;
    END LOOP;

    SELECT COUNT(*) INTO v_count FROM APP_USER;
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20106, 'La validacion final encontro mas de un usuario.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM APP_USER user_value
      JOIN APP_USER_ROLE user_role ON user_role.USER_ID = user_value.USER_ID
      JOIN APP_ROLE role_value ON role_value.ROLE_ID = user_role.ROLE_ID
      JOIN APP_USER_ORGANIZATION user_org ON user_org.USER_ID = user_value.USER_ID
      JOIN ORGANIZATION org_value ON org_value.ORGANIZATION_ID = user_org.ORGANIZATION_ID
     WHERE user_value.USER_ID = v_admin_id
       AND LOWER(user_value.NORMALIZED_EMAIL) = c_admin_email
       AND user_value.STATUS = 'ACTIVE'
       AND role_value.ROLE_CODE = 'ADMINISTRATOR'
       AND org_value.ORGANIZATION_CODE = 'GLOBAL'
       AND org_value.ORGANIZATION_TYPE = 'GLOBAL'
       AND org_value.STATUS = 'ACTIVE';

    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(
            -20107,
            'No se pudo reconstruir correctamente la relacion Administrador - GLOBAL.'
        );
    END IF;

    SELECT COUNT(*) INTO v_count FROM ORGANIZATION;
    IF v_count <> 1 THEN
        RAISE_APPLICATION_ERROR(-20108, 'La validacion final encontro mas de una organizacion.');
    END IF;

    COMMIT;

    DBMS_OUTPUT.PUT_LINE('');
    DBMS_OUTPUT.PUT_LINE('============================================================');
    DBMS_OUTPUT.PUT_LINE('LIMPIEZA COMPLETADA CORRECTAMENTE');
    DBMS_OUTPUT.PUT_LINE('============================================================');
    DBMS_OUTPUT.PUT_LINE('Registros eliminados: ' || v_total_deleted);
    DBMS_OUTPUT.PUT_LINE('Usuario conservado:    ' || c_admin_email);
    DBMS_OUTPUT.PUT_LINE('Rol conservado:        ADMINISTRATOR');
    DBMS_OUTPUT.PUT_LINE('Organizacion conservada: GLOBAL');
    DBMS_OUTPUT.PUT_LINE('Las migraciones Flyway y los catalogos globales base se conservaron.');
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('');
        DBMS_OUTPUT.PUT_LINE('La limpieza fallo y toda la transaccion fue revertida.');
        DBMS_OUTPUT.PUT_LINE(SQLERRM);
        RAISE;
END;
/

EXIT SUCCESS
