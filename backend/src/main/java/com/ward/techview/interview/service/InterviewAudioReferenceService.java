package com.ward.techview.interview.service;

import com.ward.techview.interview.api.InterviewAudioReferenceApi;
import com.ward.techview.interview.repository.AnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class InterviewAudioReferenceService implements InterviewAudioReferenceApi {
    private final AnswerRepository answerRepository;

    @Override
    @Transactional(readOnly = true)
    public Set<String> findReferencedAudioObjectKeys(Set<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return Set.of();
        }

        return answerRepository.findReferencedAudioObjectKeys(Set.copyOf(objectKeys));
    }
}
