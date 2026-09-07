package com.pdf2tz.backend.error;

import org.springframework.http.HttpStatus;

/**
 * Коды системных ошибок программы.
 *
 * <p>Используются при выбросах {@link AppException}
 */
public enum ErrorCode {
    /* Codes */
    PDF_READ_ERROR(HttpStatus.BAD_REQUEST),
    PDF_PARSE_ERROR(HttpStatus.BAD_REQUEST),
    FILE_EMPTY(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
