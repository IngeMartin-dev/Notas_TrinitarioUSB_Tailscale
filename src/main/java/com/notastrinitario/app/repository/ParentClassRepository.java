package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.ParentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ParentClassRepository extends JpaRepository<ParentClass, Long> {
    List<ParentClass> findByGradeAndClassroom(String grade, String classroom);
    List<ParentClass> findByUserId(Long userId);

    @Modifying
    @Query(value = "DELETE FROM parent_classes WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") Long userId);
}
