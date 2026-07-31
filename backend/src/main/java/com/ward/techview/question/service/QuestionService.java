package com.ward.techview.question.service;

import com.ward.techview.common.exception.BusinessException;
import com.ward.techview.question.api.QuestionSelector;
import com.ward.techview.question.api.SelectedQuestion;
import com.ward.techview.question.dto.QuestionCandidate;
import com.ward.techview.question.entity.QuestionCategory;
import com.ward.techview.question.repository.QuestionRepository;
import com.ward.techview.question.response.QuestionResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.*;

@Service
@RequiredArgsConstructor
public class QuestionService implements QuestionSelector {
    private static final int CORE_QUESTION_COUNT = 5;

    private final QuestionRepository questionRepository;
    private final QuestionRandomizer questionRandomizer;

    @Override
    @Transactional(readOnly = true)
    public List<SelectedQuestion> selectCoreQuestions() {
        Map<QuestionCategory, List<Long>> candidateIdsByCategory = questionRepository.findAllCandidates()
                .stream()
                .collect(groupingBy(
                        QuestionCandidate::category,
                        mapping(QuestionCandidate::id, toList())
                ));

        validateQuestionPool(candidateIdsByCategory);

        List<QuestionCategory> categories = new ArrayList<>(candidateIdsByCategory.keySet());
        questionRandomizer.shuffle(categories);

        List<Long> selectedIds = categories.stream()
                .limit(CORE_QUESTION_COUNT)
                .map(category -> questionRandomizer.select(candidateIdsByCategory.get(category)))
                .toList();

        List<SelectedQuestion> selectedQuestions = new ArrayList<>(
                questionRepository.findAllById(selectedIds)
                        .stream()
                        .map(question -> new SelectedQuestion(
                                question.getId(),
                                question.getContent(),
                                question.getCategory().name()
                        ))
                        .toList()
        );

        validateSelectedQuestions(selectedQuestions);
        questionRandomizer.shuffle(selectedQuestions);

        return List.copyOf(selectedQuestions);
    }

    private void validateQuestionPool(Map<QuestionCategory, List<Long>> candidateIdsByCategory) {
        if (candidateIdsByCategory.size() < CORE_QUESTION_COUNT) {
            throw insufficientQuestionPool();
        }
    }

    private void validateSelectedQuestions(List<SelectedQuestion> selectedQuestions) {
        if (selectedQuestions.size() != CORE_QUESTION_COUNT) {
            throw insufficientQuestionPool();
        }
    }

    private BusinessException insufficientQuestionPool() {
        return new BusinessException(QuestionResponseCode.QUESTION_POOL_INSUFFICIENT);
    }
}
