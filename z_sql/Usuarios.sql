INSERT INTO users (
        name,
        surname,
        username,
        mail,
        password,
        role_id,
        enable,
        digital_signature,
        profilePicture
    )
VALUES (
        'Usuario',
        'Ejemplo',
        'usuario.Ejemplo',
        'usuario@ejemplo.com',
        '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', -- SHA-256
        -- la Contraseña es 123456
        1,
        -- para ser Admin el RoleID = 1 
        TRUE,
        -- enable = true
        NULL,
        -- digital signature not set
        NULL -- profile picture not set
    );