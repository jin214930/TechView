package com.ward.techview.interview.api.answer;

public interface InterviewAnswerApi {
    PreparedAnswer prepareAnswer(AnswerCreateCommand command);

    AnswerInfo getAnswer(Long memberId, Long answerId);

    AnswerInfo lockAnswerForProcessing(Long memberId, Long interviewSessionId, Long answerId);

    void completeTranscription(AnswerTranscriptionCommand command);
}
