package com.pdf2tz.backend.application.pdf.assembly;

import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedPage;
import com.pdf2tz.backend.application.pdf.model.document.DocumentBlock;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Собирает готовый документ из очищенного постраничного текста.
 *
 * <p>На текущем этапе каждая непустая очищенная страница превращается в один
 * {@link TextBlock}. Табличные блоки будут добавлены отдельной итерацией,
 * когда появится стабильное извлечение и нормализация таблиц.</p>
 */
@Component
public class ParsedDocumentAssembler {

    /**
     * Преобразует очищенный документ в готовую блочную модель.
     *
     * <p>Метод сохраняет все страницы исходного очищенного документа, включая
     * пустые. Это важно, чтобы ссылки на номера страниц оставались стабильными
     * при последующей сборке чанков и ответов LLM.</p>
     *
     * @param cleanedDocument документ после очистки PDF-текста
     * @return готовый документ с текстовыми блоками
     */
    public ParsedDocument assemble(CleanedDocument cleanedDocument) {
        Objects.requireNonNull(cleanedDocument, "Cleaned document must not be null");

        List<ParsedPage> pages = cleanedDocument.pages().stream()
                .map(page -> new ParsedPage(
                        page.pageNumber(),
                        buildPageBlocks(page)
                ))
                .toList();

        return new ParsedDocument(pages);
    }

    private List<DocumentBlock> buildPageBlocks(CleanedPage page) {
        String text = Objects.requireNonNull(page.text(), "Cleaned page text must not be null")
                .trim();

        if (text.isBlank()) {
            return List.of();
        }

        return List.of(new TextBlock(text));
    }
}
