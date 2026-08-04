INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'QUESTION_VIEW',
    'Consultar banco de preguntas',
    'QUESTION_BANK',
    'Permite consultar preguntas y sus catálogos.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'QUESTION_CREATE',
    'Crear preguntas',
    'QUESTION_BANK',
    'Permite crear preguntas en estado borrador.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'QUESTION_UPDATE',
    'Editar preguntas',
    'QUESTION_BANK',
    'Permite editar preguntas que todavía no están publicadas.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'QUESTION_ARCHIVE',
    'Archivar preguntas',
    'QUESTION_BANK',
    'Permite archivar preguntas del banco.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'QUESTION_CATEGORY_MANAGE',
    'Administrar categorías de preguntas',
    'QUESTION_BANK',
    'Permite crear categorías para clasificar las preguntas.'
);

INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM APP_ROLE r
CROSS JOIN APP_PERMISSION p
WHERE r.ROLE_CODE = 'ADMINISTRATOR'
  AND p.PERMISSION_CODE IN (
      'QUESTION_VIEW',
      'QUESTION_CREATE',
      'QUESTION_UPDATE',
      'QUESTION_ARCHIVE',
      'QUESTION_CATEGORY_MANAGE'
  );
