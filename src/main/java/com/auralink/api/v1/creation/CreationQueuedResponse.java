package com.auralink.api.v1.creation;

import com.auralink.creation.CreationStatus;

public record CreationQueuedResponse(String creationId, CreationStatus status) {
}
