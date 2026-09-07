package com.pdf2tz.backend.error;

import java.util.Map;

/**
 * Класс системной ошибки программы
 */
public final class AppException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    private AppException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /**
     * Сборка системной ошибки без явного указания деталей ошибки
     * @param code код ошибки
     * @param message сообщение ошибки
     * @return собранный объект системной ошибки по заданным параметрам
     */
    public static AppException build(ErrorCode code, String message) {
        return new AppException(code, message, null);
    }

    /**
     * Сборка системной ошибки
     * @param code код ошибки
     * @param message сообщение ошибки
     * @param details детали ошибки
     * @return собранный объект системной ошибки по заданным параметрам
     */
    public static AppException build(ErrorCode code, String message, Map<String, Object> details) {
        return new AppException(code, message, details);
    }

    /* Getters */
    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
