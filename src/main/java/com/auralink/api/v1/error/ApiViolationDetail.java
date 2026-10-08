package com.auralink.api.v1.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record ApiViolationDetail(String code, String path, String nodeId, Integer edgeIndex, String message) {
}
