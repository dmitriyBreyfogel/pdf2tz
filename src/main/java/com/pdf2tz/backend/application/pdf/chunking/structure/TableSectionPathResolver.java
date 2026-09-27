package com.pdf2tz.backend.application.pdf.chunking.structure;

import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;

import java.util.List;
import java.util.Objects;

/**
 * Консервативно привязывает таблицу к пути раздела при отсутствии координат
 * текстовых строк.
 */
final class TableSectionPathResolver {

    SectionPath resolve(
            SectionPath pathBeforePage,
            List<SectionPath> pathsOnPage,
            SectionPath pathAfterPage
    ) {
        Objects.requireNonNull(pathBeforePage, "Path before page must not be null");
        Objects.requireNonNull(pathsOnPage, "Paths on page must not be null");
        Objects.requireNonNull(pathAfterPage, "Path after page must not be null");

        List<SectionPath> distinctPaths = pathsOnPage.stream().distinct().toList();
        if (distinctPaths.isEmpty()) {
            return pathBeforePage.equals(pathAfterPage)
                    ? pathBeforePage
                    : pathBeforePage.commonPrefix(pathAfterPage);
        }
        if (distinctPaths.size() == 1) {
            return distinctPaths.get(0);
        }

        SectionPath prefix = distinctPaths.get(0);
        for (int index = 1; index < distinctPaths.size(); index++) {
            prefix = prefix.commonPrefix(distinctPaths.get(index));
        }
        return prefix;
    }
}
