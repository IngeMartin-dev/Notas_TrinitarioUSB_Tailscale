package com.notastrinitario.app.service;

import com.notastrinitario.app.entity.ReportCard;
import java.util.List;
import java.util.Optional;

public interface IReportCardService {
    ReportCard createReportCard(ReportCard reportCard);
    void sendReportCard(ReportCard reportCard);
    Optional<ReportCard> getReportCardById(Long id);
    List<ReportCard> getReportCardsByStudentId(Long studentId);
    List<ReportCard> getReportCardsByTeacherId(Long teacherId);
    List<ReportCard> getReportCardsByParentId(Long parentId);
    void deleteReportCard(Long id);
}
