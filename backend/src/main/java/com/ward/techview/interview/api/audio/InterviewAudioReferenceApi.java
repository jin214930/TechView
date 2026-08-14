package com.ward.techview.interview.api.audio;

import java.util.Set;

public interface InterviewAudioReferenceApi {
    Set<String> findReferencedAudioObjectKeys(Set<String> objectKeys);
}
