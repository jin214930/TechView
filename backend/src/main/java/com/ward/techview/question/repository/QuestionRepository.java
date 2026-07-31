package com.ward.techview.question.repository;

import com.ward.techview.question.dto.QuestionCandidate;
import com.ward.techview.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    @Query("""
            SELECT new com.ward.techview.question.dto.QuestionCandidate(q.id, q.category)
            FROM Question q
            """)
    List<QuestionCandidate> findAllCandidates();
}
