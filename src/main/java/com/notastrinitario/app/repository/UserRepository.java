package com.notastrinitario.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.notastrinitario.app.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    /** Todas las cuentas que comparten un correo (el correo ya no es único). */
    List<User> findAllByEmail(String email);

    /** La cuenta de ese correo, solo si es de UNA única persona; vacío si no existe o si lo comparten varias. */
    default Optional<User> findByEmail(String email) {
        List<User> todas = findAllByEmail(email);
        return todas.size() == 1 ? Optional.of(todas.get(0)) : Optional.empty();
    }
    @Query("SELECT u FROM User u JOIN u.role r WHERE UPPER(r.name) = UPPER(:roleName)")
    List<User> findByRoleName(@Param("roleName") String roleName);
    Optional<User> findByTemp2faCodeAndTemp2faExpiryGreaterThan(String temp2faCode, Long expiryTime);

    @Query("SELECT DISTINCT p FROM Student s JOIN s.parents p WHERE s.grade = :grade AND s.classGroup = :classGroup ORDER BY p.surname, p.name")
    List<User> findParentsByGradeAndClassroom(@Param("grade") String grade, @Param("classGroup") String classGroup);

    @Query("SELECT DISTINCT p FROM Student s JOIN s.parents p WHERE s.grade = :grade ORDER BY p.surname, p.name")
    List<User> findParentsByGrade(@Param("grade") String grade);

    @Query("SELECT u FROM User u JOIN u.role r WHERE UPPER(r.name) = UPPER('PARENT') ORDER BY u.surname, u.name")
    List<User> findAllParentsOrdered();
}
