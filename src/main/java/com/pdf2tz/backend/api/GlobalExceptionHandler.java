package com.pdf2tz.backend.api;

import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Возвращает клиентскую ошибку при отсутствии multipart-файла или некорректном теле загрузки.
     *
     * @param ex ошибка разбора запроса
     * @return ответ с кодом {@code INVALID_UPLOAD_REQUEST} и статусом 400
     */
    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleInvalidUpload(Exception ex) {
        return handleAppException(AppException.build(
                ErrorCode.INVALID_UPLOAD_REQUEST,
                "Ожидается multipart/form-data с файлом в поле file"
        ));
    }

    /**
     * Сохраняет отдельный статус превышения лимита загрузки вместо общей ошибки multipart.
     *
     * @param ex ошибка превышения размера
     * @return ответ с кодом {@code FILE_TOO_LARGE} и статусом 413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return handleAppException(AppException.build(ErrorCode.FILE_TOO_LARGE,
                "Превышен допустимый размер загрузки"));
    }

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
