INSERT INTO QUESTION_TYPE_CATALOG (
    TYPE_CODE,
    TYPE_NAME,
    DESCRIPTION,
    SUPPORTS_OPTIONS
) VALUES (
    'SINGLE_CHOICE',
    'Opción única',
    'Pregunta con varias opciones y una sola respuesta correcta.',
    1
);

INSERT INTO QUESTION_TYPE_CATALOG (
    TYPE_CODE,
    TYPE_NAME,
    DESCRIPTION,
    SUPPORTS_OPTIONS
) VALUES (
    'MULTIPLE_CHOICE',
    'Opción múltiple',
    'Pregunta con varias opciones y más de una respuesta correcta.',
    1
);

INSERT INTO QUESTION_TYPE_CATALOG (
    TYPE_CODE,
    TYPE_NAME,
    DESCRIPTION,
    SUPPORTS_OPTIONS
) VALUES (
    'TRUE_FALSE',
    'Verdadero o falso',
    'Pregunta con las opciones Verdadero y Falso.',
    1
);

INSERT INTO QUESTION_DIFFICULTY_CATALOG (
    DIFFICULTY_CODE,
    DIFFICULTY_NAME,
    SORT_ORDER
) VALUES ('BASIC', 'Básico', 1);

INSERT INTO QUESTION_DIFFICULTY_CATALOG (
    DIFFICULTY_CODE,
    DIFFICULTY_NAME,
    SORT_ORDER
) VALUES ('INTERMEDIATE', 'Intermedio', 2);

INSERT INTO QUESTION_DIFFICULTY_CATALOG (
    DIFFICULTY_CODE,
    DIFFICULTY_NAME,
    SORT_ORDER
) VALUES ('ADVANCED', 'Avanzado', 3);

INSERT INTO QUESTION_CATEGORY (
    PUBLIC_ID,
    CATEGORY_CODE,
    CATEGORY_NAME,
    DESCRIPTION,
    STATUS
) VALUES (
    '11111111-1111-4111-8111-111111111111',
    'GENERAL',
    'General',
    'Categoría general para preguntas que todavía no tienen una clasificación específica.',
    'ACTIVE'
);

INSERT INTO QUESTION_CATEGORY (
    PUBLIC_ID,
    CATEGORY_CODE,
    CATEGORY_NAME,
    DESCRIPTION,
    STATUS
) VALUES (
    '22222222-2222-4222-8222-222222222222',
    'JAVA',
    'Java',
    'Preguntas relacionadas con Java y programación orientada a objetos.',
    'ACTIVE'
);

INSERT INTO QUESTION_CATEGORY (
    PUBLIC_ID,
    CATEGORY_CODE,
    CATEGORY_NAME,
    DESCRIPTION,
    STATUS
) VALUES (
    '33333333-3333-4333-8333-333333333333',
    'SPRING_BOOT',
    'Spring Boot',
    'Preguntas relacionadas con Spring Framework y Spring Boot.',
    'ACTIVE'
);

INSERT INTO QUESTION_CATEGORY (
    PUBLIC_ID,
    CATEGORY_CODE,
    CATEGORY_NAME,
    DESCRIPTION,
    STATUS
) VALUES (
    '44444444-4444-4444-8444-444444444444',
    'SQL',
    'SQL',
    'Preguntas de bases de datos y lenguaje SQL.',
    'ACTIVE'
);

INSERT INTO QUESTION_CATEGORY (
    PUBLIC_ID,
    CATEGORY_CODE,
    CATEGORY_NAME,
    DESCRIPTION,
    STATUS
) VALUES (
    '55555555-5555-4555-8555-555555555555',
    'ARCHITECTURE',
    'Arquitectura',
    'Preguntas de arquitectura de software, APIs y microservicios.',
    'ACTIVE'
);
