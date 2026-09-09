package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Читает PDF-файл, полученный через HTTP-запрос.
 *
 * <p>Класс относится к API-слою, потому что работает с {@link MultipartFile}
 * и проверяет детали входящего HTTP-запроса: наличие файла, его content type
 * и возможность прочитать байты.</p>
 */
@Component
public class PdfUploadReader {

    /**
     * Проверяет загруженный файл и возвращает его байтовое содержимое.
     *
     * @param file файл из multipart-запроса
     * @return байтовое представление PDF-файла
     * @throws AppException если файл пустой, имеет неподдерживаемый тип или не может быть прочитан
     */
    public byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.build(
                    ErrorCode.FILE_EMPTY,
                    "Файл пуст"
            );
        }

        String contentType = file.getContentType();

        if (!MediaType.APPLICATION_PDF_VALUE.equals(contentType)) {
            throw AppException.build(
                    ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Поддерживаются файлы только PDF-формата"
            );
        }

        try {
            return file.getBytes();
        }
        catch (IOException e) {
            throw AppException.build(
                    ErrorCode.FILE_READ_ERROR,
                    "Не удалось прочитать файл",
                    Map.of("details", e.getMessage())
            );
        }
    }
}
