package com.myblog.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors, Map<String, Object> details) {

    public record FieldError(String field, String message) {
    }
}
