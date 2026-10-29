-- insertar_padres_familia.sql
INSERT INTO users (name, surname, username, mail, password, role_id, enable)
SELECT
    CONCAT('Padre de ', s.name, ' ', s.surname),
    s.surname,
    CONCAT('padre_', LOWER(s.name), '_', LOWER(s.surname)),
    CONCAT('padre_', LOWER(s.name), '_', LOWER(s.surname), '@test.com'),
    '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92',
    8,
    TRUE
FROM students s WHERE s.active = TRUE;

INSERT INTO parent_classes (grade, classroom, user_id)
SELECT s.grade, s.class_group, u.id
FROM students s
JOIN users u ON u.username = CONCAT('padre_', LOWER(s.name), '_', LOWER(s.surname))
WHERE s.active = TRUE;

INSERT INTO student_parents (student_id, user_id)
SELECT s.id, u.id
FROM students s
JOIN users u ON u.username = CONCAT('padre_', LOWER(s.name), '_', LOWER(s.surname))
WHERE s.active = TRUE;