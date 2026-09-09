package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP-контракт для работы с PDF-файлами
 */
@RequestMapping("/api/v1/pdf")
public interface PdfApi {

    /**
     * Извлечение текста из PDF-файла
     * @param file PDF-файл
     * @return ответ с очищенным текстом по страницам
     * @throws com.pdf2tz.backend.error.AppException с кодом:
     *  <ul>
     *      <li>{@code FILE_EMPTY} - файл пуст</li>
     *      <li>{@code FILE_READ_ERROR} - ошибка чтения файла</li>
     *      <li>{@code PDF_PARSE_ERROR} - не удалось распарсить PDF-файл</li>
     *      <li>{@code UNSUPPORTED_FILE_TYPE} - файл не поддерживаемого формата</li>
     *  </ul>
     */
    @PostMapping("/extract")
    ResponseEntity<PdfResponseDto> extractText(
            @RequestParam("file")
            MultipartFile file
    );
}
