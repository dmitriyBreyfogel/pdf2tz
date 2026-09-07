package com.pdf2tz.backend.infrastructure.pdf;

import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Собирает извлечённый Apache Tika текст отдельно для каждой страницы PDF-документа.
 *
 * <p>Tika передаёт результат разбора как последовательность SAX-событий XHTML.
 * Каждая страница PDF представлена элементом {@code <div class="page">}.
 * При входе в этот элемент обработчик начинает накапливать текст, а при выходе
 * создаёт {@link ExtractedPage}.</p>
 *
 * <p>Класс не разбирает PDF самостоятельно и не формирует ошибки приложения:
 * его задача ограничена преобразованием SAX-событий в постраничную модель.
 * Ошибки парсинга обрабатывает {@link TikaPdfReader}.</p>
 */
final class PageCollectingContentHandler extends DefaultHandler {

    private static final Set<String> BLOCK_ELEMENTS = Set.of(
            "p", "div", "br", "li", "tr"
    );

    private final List<ExtractedPage> pages = new ArrayList<>();

    private StringBuilder currentPageText;

    // Нужна для отличия закрытия вложенного HTML-тега от закрытия контейнера страницы.
    private int pageDepth;

    @Override
    public void startElement(
            String uri,
            String localName,
            String qName,
            Attributes attributes
    ) {
        String elementName = elementName(localName, qName);

        if (isPageElement(elementName, attributes)) {
            currentPageText = new StringBuilder();
            pageDepth = 1;
            return;
        }

        if (currentPageText != null) {
            pageDepth++;

            if (BLOCK_ELEMENTS.contains(elementName)) {
                appendLineBreak();
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (currentPageText != null) {
            currentPageText.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (currentPageText == null) {
            return;
        }

        String elementName = elementName(localName, qName);

        if (BLOCK_ELEMENTS.contains(elementName)) {
            appendLineBreak();
        }

        pageDepth--;

        if (pageDepth == 0) {
            pages.add(new ExtractedPage(
                    pages.size() + 1,
                    normalize(currentPageText.toString())
            ));

            currentPageText = null;
        }
    }

    @Override
    public void endDocument() {
        if (currentPageText != null) {
            pages.add(new ExtractedPage(
                    pages.size() + 1,
                    normalize(currentPageText.toString())
            ));
        }
    }

    /**
     * Возвращает страницы, собранные из потока SAX-событий.
     *
     * @return неизменяемый список извлечённых страниц в исходном порядке
     */
    List<ExtractedPage> getPages() {
        return List.copyOf(pages);
    }

    private boolean isPageElement(String elementName, Attributes attributes) {
        return "div".equals(elementName)
                && "page".equals(attributes.getValue("class"));
    }

    private String elementName(String localName, String qName) {
        return localName == null || localName.isBlank() ? qName : localName;
    }

    private void appendLineBreak() {
        if (!currentPageText.isEmpty()
                && currentPageText.charAt(currentPageText.length() - 1) != '\n') {
            currentPageText.append('\n');
        }
    }

    private String normalize(String text) {
        return text
                .replace('\u00A0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\f', '\n')
                .replaceAll("[ \\t]+\n", "\n")
                .replaceAll("\n[ \\t]+", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}
