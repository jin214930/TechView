package com.ward.techview.evaluation.repository;

import com.ward.techview.evaluation.entity.AnswerAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerAnalysisRepository extends JpaRepository<AnswerAnalysis, Long> {
    List<AnswerAnalysis> findAllByAnswerIdIn(List<Long> answerIds);
}
