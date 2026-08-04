INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_VIEW',
    'Consultar usuarios',
    'USER_MANAGEMENT',
    'Permite consultar el catálogo administrativo de usuarios.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_CREATE',
    'Crear usuarios',
    'USER_MANAGEMENT',
    'Permite crear usuarios y asignar su rol inicial.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_ACCESS_MANAGE',
    'Administrar vigencias',
    'USER_MANAGEMENT',
    'Permite administrar el periodo de acceso de los usuarios.'
);

INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM APP_ROLE r
CROSS JOIN APP_PERMISSION p
WHERE r.ROLE_CODE = 'ADMINISTRATOR'
  AND p.PERMISSION_CODE IN (
      'USER_VIEW',
      'USER_CREATE',
      'USER_ACCESS_MANAGE'
  );
