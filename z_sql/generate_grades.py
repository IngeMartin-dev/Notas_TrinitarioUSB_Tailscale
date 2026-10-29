#!/usr/bin/env python3
"""
Script para generar INSERT statements SQL de notas aleatorias para todos los estudiantes
Usa todas las materias de la base de datos y filtra por grado del estudiante
"""

import random
from datetime import datetime

# Configuración
STUDENT_IDS = list(range(1, 911))  # IDs de estudiantes (11-911)
GRADE_TYPES = ['ACT1', 'ACT2', 'ACT3', 'Eval.Period', 'Auto.Eval', 'Prom.Parc']
PERIODS = [1, 2, 3, 4]  # Solo los 4 periodos para prueba

# Materias con sus rangos de grados (grade_min, grade_max)
SUBJECTS = [
    # Primaria (Grados 1-5)
    ('Matemáticas', 1, 5),
    ('Castellano', 1, 5),
    ('Inglés', 1, 5),
    ('Sociales', 1, 5),
    ('Ciencias Naturales', 1, 5),
    ('Ed.Emocional', 1, 5),
    ('Ed.física', 1, 5),
    ('Religión', 1, 5),
    ('Afrocolombianidad', 1, 5),
    ('Artística', 1, 5),
    ('Robotica', 1, 5),
    ('Domotica', 1, 5),
    # Bachillerato (Grados 6-9-11)
    ('Matemáticas', 6, 9),
    ('Castellano', 6, 9),
    ('Inglés', 6, 11),
    ('Sociales', 6, 11),
    ('Biología', 6, 11),
    ('Ciencias Políticas', 6, 11),
    ('Ed.Emocional', 6, 11),
    ('Ed.física', 6, 11),
    ('Física', 6, 11),
    ('Emprendimiento', 6, 11),
    ('Química', 6, 11),
    ('Cátedra de la Paz', 6, 11),
    ('Religión', 6, 11),
    ('Afrocolombianidad', 6, 11),
    ('Artística', 6, 11),
    ('Competencias Ciudadanas', 6, 11),
    ('Robotica', 6, 11),
    # Media (Grados 10-11)
    ('Matemáticas Media', 10, 11),
    ('Castellano Media', 10, 11),
    ('Filosofía', 10, 11),
    ('Orientacion Profesional', 10, 11),
]

def get_student_grade(student_id):
    """Determina el grado del estudiante basado en su ID"""
    # Mapeo basado en datos reales de la base de datos
    if student_id == 11:
        return 5
    elif 12 <= student_id <= 17:
        return 10
    elif 18 <= student_id <= 55:
        return 1
    elif 56 <= student_id <= 96:
        return 1
    elif 97 <= student_id <= 137:
        return 2
    elif 138 <= student_id <= 178:
        return 2
    elif 179 <= student_id <= 218:
        return 3
    elif 219 <= student_id <= 259:
        return 3
    elif 260 <= student_id <= 300:
        return 4
    elif 301 <= student_id <= 341:
        return 4
    elif 342 <= student_id <= 380:
        return 5
    elif 381 <= student_id <= 420:
        return 5
    elif 421 <= student_id <= 460:
        return 6
    elif 461 <= student_id <= 500:
        return 6
    elif 501 <= student_id <= 540:
        return 7
    elif 541 <= student_id <= 580:
        return 7
    elif 581 <= student_id <= 620:
        return 8
    elif 621 <= student_id <= 660:
        return 8
    elif 661 <= student_id <= 700:
        return 9
    elif 701 <= student_id <= 740:
        return 9
    elif 741 <= student_id <= 780:
        return 10
    elif 781 <= student_id <= 820:
        return 10
    elif 821 <= student_id <= 860:
        return 11
    elif 861 <= student_id <= 900:
        return 11
    # IDs individuales al final (901-911)
    elif student_id == 901:
        return 5
    elif student_id == 902:
        return 10
    elif student_id == 903:
        return 8
    elif student_id == 904:
        return 2
    elif student_id == 905:
        return 9
    elif student_id == 906:
        return 7
    elif student_id == 907:
        return 11
    elif student_id == 908:
        return 9
    elif student_id == 909:
        return 1
    elif student_id == 910:
        return 11
    elif student_id == 911:
        return 11
    else:
        return 1  # Default

def generate_random_grade():
    """Genera una nota aleatoria entre 1.5 y 5.0 con 1 decimal"""
    return round(random.uniform(1.5, 5.0), 1)

def is_evaluation(grade_name):
    """Determina si el tipo de nota es una evaluación principal"""
    return grade_name == 'Eval.Period'

def generate_sql():
    """Genera los INSERT statements SQL"""
    sql_lines = []
    sql_lines.append("-- Generated grade data for all students")
    sql_lines.append(f"-- Generated at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    sql_lines.append("-- Notas aleatorias entre 1.5 y 5.0")
    sql_lines.append("-- Excluye Recup.Esc y Recup.Oral")
    sql_lines.append("-- Filtra materias por grado del estudiante")
    sql_lines.append("")
    sql_lines.append("INSERT INTO subject_grades (student_id, teacher_id, subject_name, period, grade_name, grade_value, is_evaluation, appreciative, created_at, updated_at) VALUES")
    
    total_records = 0
    for student_id in STUDENT_IDS:
        student_grade = get_student_grade(student_id)
        
        for subject_name, grade_min, grade_max in SUBJECTS:
            # Filtrar materias por grado del estudiante
            if grade_min <= student_grade <= grade_max:
                for period in PERIODS:
                    for grade_type in GRADE_TYPES:
                        grade_value = generate_random_grade()
                        is_eval = is_evaluation(grade_type)
                        
                        sql_line = f"({student_id}, 48, '{subject_name}', {period}, '{grade_type}', {grade_value}, {str(is_eval).upper()}, NULL, NOW(), NOW()),"
                        sql_lines.append(sql_line)
                        total_records += 1
    
    # Reemplazar la última coma con punto y coma
    if sql_lines:
        sql_lines[-1] = sql_lines[-1].rstrip(',') + ';'
    
    sql_content = '\n'.join(sql_lines)
    
    # Guardar en archivo
    output_file = 'insert_all_grades.sql'
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(sql_content)
    
    print(f"✅ Generated {total_records} grade records")
    print(f"📁 Saved to {output_file}")
    print(f"👥 Students: {len(STUDENT_IDS)}")
    print(f"📚 Subjects: {len(SUBJECTS)}")
    print(f"📝 Grade types: {len(GRADE_TYPES)}")
    print(f"📅 Periods: {len(PERIODS)}")

if __name__ == '__main__':
    generate_sql()
