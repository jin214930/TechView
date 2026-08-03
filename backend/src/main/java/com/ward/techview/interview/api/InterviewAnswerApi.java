package com.ward.techview.interview.api;

public interface InterviewAnswerApi {
    PreparedAnswer prepareAnswer(AnswerCreateCommand command);

    AnswerInfo getAnswer(Long memberId, Long answerId);
}
