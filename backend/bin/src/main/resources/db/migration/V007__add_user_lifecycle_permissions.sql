INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_UPDATE',
    'Editar usuarios',
    'USER_MANAGEMENT',
    'Permite editar los datos generales de los usuarios.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_STATUS_CHANGE',
    'Activar y suspender usuarios',
    'USER_MANAGEMENT',
    'Permite activar o suspender cuentas de usuario.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_ROLE_ASSIGN',
    'Modificar rol de usuario',
    'USER_MANAGEMENT',
    'Permite modificar el rol asignado a un usuario.'
);

INSERT INTO APP_PERMISSION (
    PERMISSION_CODE,
    PERMISSION_NAME,
    MODULE_CODE,
    DESCRIPTION
) VALUES (
    'USER_PASSWORD_RESET',
    'Restablecer contraseña de usuario',
    'USER_MANAGEMENT',
    'Permite asignar una nueva contraseña temporal a un usuario.'
);

INSERT INTO APP_ROLE_PERMISSION (ROLE_ID, PERMISSION_ID)
SELECT r.ROLE_ID, p.PERMISSION_ID
FROM APP_ROLE r
CROSS JOIN APP_PERMISSION p
WHERE r.ROLE_CODE = 'ADMINISTRATOR'
  AND p.PERMISSION_CODE IN (
      'USER_UPDATE',
      'USER_STATUS_CHANGE',
      'USER_ROLE_ASSIGN',
      'USER_PASSWORD_RESET'
  );
