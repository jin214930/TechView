package com.ward.techview.interview.service;

import com.ward.techview.interview.repository.AnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewAudioReferenceServiceTest {

    @Mock
    private AnswerRepository answerRepository;

    private InterviewAudioReferenceService service;

    @BeforeEach
    void setUp() {
        service = new InterviewAudioReferenceService(answerRepository);
    }

    @Test
    @DisplayName("전달한 객체 키 중 답변이 참조하는 키만 반환한다")
    void returnsReferencedAudioObjectKeys() {
        Set<String> objectKeys = Set.of("referenced-key", "orphan-key");
        when(answerRepository.findReferencedAudioObjectKeys(objectKeys))
                .thenReturn(Set.of("referenced-key"));

        Set<String> result = service.findReferencedAudioObjectKeys(objectKeys);

        assertEquals(Set.of("referenced-key"), result);
        verify(answerRepository).findReferencedAudioObjectKeys(objectKeys);
    }

    @Test
    @DisplayName("객체 키가 비어 있으면 데이터베이스를 조회하지 않는다")
    void skipsRepositoryWhenObjectKeysAreEmpty() {
        Set<String> result = service.findReferencedAudioObjectKeys(Set.of());

        assertEquals(Set.of(), result);
        verify(answerRepository, never()).findReferencedAudioObjectKeys(Set.of());
    }
}
