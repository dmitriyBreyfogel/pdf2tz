package com.pdf2tz.server.api;

import com.pdf2tz.server.error.AppException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex) {
        return ResponseEntity.status(ex.getCode().httpStatus()).body(
                new ErrorResponse(
                        ex.getCode().name(),
                        ex.getCode().httpStatus().value(),
                        ex.getMessage(),
                        ex.getDetails(),
                        Instant.now().toString()
                )
        );
    }

    public record ErrorResponse(
            String code,
            int status,
            String message,
            Map<String, Object> details,
            String timestamp
    ) {}
}
