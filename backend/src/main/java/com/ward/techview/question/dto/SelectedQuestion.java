package com.ward.techview.question.dto;

import com.ward.techview.question.entity.Question;
import com.ward.techview.question.entity.QuestionCategory;

public record SelectedQuestion(
        Long id,
        String content,
        QuestionCategory category
) {
    public static SelectedQuestion from(Question question) {
        return new SelectedQuestion(
                question.getId(),
                question.getContent(),
                question.getCategory()
        );
    }
}
