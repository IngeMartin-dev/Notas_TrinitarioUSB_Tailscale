-- Tabla de períodos académicos
CREATE TABLE IF NOT EXISTS academic_periods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    period_number INT NOT NULL UNIQUE,
    is_unlocked BOOLEAN DEFAULT FALSE,
    start_date DATETIME DEFAULT NULL,
    end_date DATETIME DEFAULT NULL,
    unlock_date DATETIME DEFAULT NULL,
    lock_date DATETIME DEFAULT NULL,
    description VARCHAR(255) DEFAULT NULL,
    is_automatic BOOLEAN DEFAULT FALSE
);

-- Verificar e insertar períodos iniciales
INSERT IGNORE INTO academic_periods (period_number, is_unlocked, is_automatic, description) VALUES (1, TRUE, FALSE, 'Período 1 del año académico');
INSERT IGNORE INTO academic_periods (period_number, is_unlocked, is_automatic, description) VALUES (2, FALSE, FALSE, 'Período 2 del año académico');
INSERT IGNORE INTO academic_periods (period_number, is_unlocked, is_automatic, description) VALUES (3, FALSE, FALSE, 'Período 3 del año académico');
INSERT IGNORE INTO academic_periods (period_number, is_unlocked, is_automatic, description) VALUES (4, FALSE, FALSE, 'Período 4 del año académico');

-- Tabla de materias del colegio
CREATE TABLE IF NOT EXISTS subjects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    level VARCHAR(50) NOT NULL,
    grade_min INT,
    grade_max INT,
    hours_per_week INT,
    credits INT,
    type VARCHAR(20),
    description VARCHAR(500)
);

-- Tabla de directores de grupo
CREATE TABLE IF NOT EXISTS homeroom_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    grade VARCHAR(50) NOT NULL,
    classroom VARCHAR(50) NOT NULL,
    user_id BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY unique_homeroom (grade, classroom)
);