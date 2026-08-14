package com.ward.techview.voice.api.storage;

import java.net.URI;
import java.time.OffsetDateTime;

public record PresignedUpload(
        URI uploadUrl,
        OffsetDateTime expiresAt
) {
}
