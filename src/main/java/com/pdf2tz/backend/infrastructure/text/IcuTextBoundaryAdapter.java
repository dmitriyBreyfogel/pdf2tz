package com.pdf2tz.backend.infrastructure.text;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import com.pdf2tz.backend.application.ports.TextBoundaryPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ICU4J-адаптер для поиска границ предложений и слов.
 *
 * <p>Новый {@link BreakIterator} создаётся для каждого вызова: экземпляр ICU
 * изменяемый и не предназначен для совместного использования между HTTP
 * потоками.</p>
 */
@Component
public class IcuTextBoundaryAdapter implements TextBoundaryPort {

    @Override
    public List<Integer> sentenceEndOffsets(String text) {
        return boundaries(text, BreakIterator.getSentenceInstance(ULocale.ROOT));
    }

    @Override
    public List<Integer> wordEndOffsets(String text) {
        return boundaries(text, BreakIterator.getWordInstance(ULocale.ROOT));
    }

    private List<Integer> boundaries(String text, BreakIterator iterator) {
        Objects.requireNonNull(text, "Text must not be null");
        Objects.requireNonNull(iterator, "Break iterator must not be null");

        if (text.isEmpty()) {
            return List.of();
        }

        iterator.setText(text);
        List<Integer> offsets = new ArrayList<>();
        for (int offset = iterator.first();
             (offset = iterator.next()) != BreakIterator.DONE;
        ) {
            if (offset > 0 && (offsets.isEmpty() || offsets.get(offsets.size() - 1) != offset)) {
                offsets.add(offset);
            }
        }

        if (offsets.isEmpty() || offsets.get(offsets.size() - 1) != text.length()) {
            offsets.add(text.length());
        }
        return List.copyOf(offsets);
    }
}
