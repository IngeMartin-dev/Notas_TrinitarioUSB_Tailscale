package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.HomeroomAssignment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomeroomAssignmentRepository extends JpaRepository<HomeroomAssignment, Long> {
    Optional<HomeroomAssignment> findByGradeAndClassroom(String grade, String classroom);
    Optional<HomeroomAssignment> findByUserId(Long userId);
    void deleteByGradeAndClassroom(String grade, String classroom);

    @Modifying
    @Query(value = "DELETE FROM homeroom_assignments WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}