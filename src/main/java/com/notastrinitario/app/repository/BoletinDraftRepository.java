package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.BoletinDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BoletinDraftRepository extends JpaRepository<BoletinDraft, Long> {
    List<BoletinDraft> findByGradeAndClassroomOrderByUpdatedAtDesc(String grade, String classroom);
    Optional<BoletinDraft> findByIdAndGradeAndClassroom(Long id, String grade, String classroom);

    Optional<BoletinDraft> findFirstByGradeAndClassroomAndPeriodOrderByUpdatedAtDesc(String grade, String classroom, Integer period);

    // Se usa para borrar el borrador de un grado/salón/período una vez que
    // sus boletines ya se generaron (ya no sirve para "retomar donde iba").
    void deleteByGradeAndClassroomAndPeriod(String grade, String classroom, Integer period);
}