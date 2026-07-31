package com.ward.techview.question.dto;

import com.ward.techview.question.entity.QuestionCategory;

public record QuestionCandidate(
        Long id,
        QuestionCategory category
) {
}
