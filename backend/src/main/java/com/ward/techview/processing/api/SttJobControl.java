package com.ward.techview.processing.api;

import java.util.Optional;

public interface SttJobControl {
    Optional<ClaimedSttJob> claim(Long processingJobId);
}
