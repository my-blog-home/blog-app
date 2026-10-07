package com.myblog.common.error;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorResponse.FieldError> fieldErrors;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of(), Map.of());
    }

    public ApiException(ErrorCode code, String message, List<ErrorResponse.FieldError> fieldErrors, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors;
        this.details = details;
    }

    public static ApiException field(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message,
                List.of(new ErrorResponse.FieldError(field, message)), Map.of());
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }

    public Map<String, Object> details() {
        return details;
    }
}
