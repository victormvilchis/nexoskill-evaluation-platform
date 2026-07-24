INSERT INTO APP_ROLE (
    ROLE_CODE,
    ROLE_NAME,
    DESCRIPTION,
    SYSTEM_ROLE,
    STATUS
) VALUES (
    'ADMINISTRATOR',
    'Administrador',
    'Administrador general de la plataforma.',
    1,
    'ACTIVE'
);

INSERT INTO APP_ROLE (
    ROLE_CODE,
    ROLE_NAME,
    DESCRIPTION,
    SYSTEM_ROLE,
    STATUS
) VALUES (
    'USER',
    'Usuario',
    'Usuario que realizará evaluaciones.',
    1,
    'ACTIVE'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'DASHBOARD_VIEW',
    'Consultar panel de bienvenida',
    'DASHBOARD',
    'Permite consultar el panel principal.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'ADMIN_PANEL_VIEW',
    'Consultar panel administrativo',
    'ADMIN',
    'Permite acceder a funciones administrativas.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_PANEL_VIEW',
    'Consultar panel de usuario',
    'USER',
    'Permite acceder al portal de usuario.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'PROFILE_VIEW',
    'Consultar perfil',
    'PROFILE',
    'Permite consultar el perfil propio.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'PASSWORD_CHANGE',
    'Cambiar contraseña',
    'PROFILE',
    'Permite cambiar la contraseña propia.'
);

INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM APP_ROLE r
CROSS JOIN APP_PERMISSION p
WHERE r.ROLE_CODE = 'ADMINISTRATOR';

INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM APP_ROLE r
JOIN APP_PERMISSION p
  ON p.PERMISSION_CODE IN (
      'DASHBOARD_VIEW',
      'USER_PANEL_VIEW',
      'PROFILE_VIEW',
      'PASSWORD_CHANGE'
  )
WHERE r.ROLE_CODE = 'USER';
