package com.pdf2tz.backend.application.pdf.chunking.text;

import com.pdf2tz.backend.application.ports.TextBoundaryPort;
import com.pdf2tz.backend.infrastructure.text.IcuTextBoundaryAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSegmentSplitterTest {

    @Test
    void prefersCompleteParagraphOverExtraSentenceFromNextParagraph() {
        TextSegmentSplitter splitter = new TextSegmentSplitter(new IcuTextBoundaryAdapter());
        String text = "Alpha. Beta.\n\nGamma. Delta.";

        TextSplitResult split = splitter.splitFirst(text, part -> part.length() <= 23);

        assertEquals("Alpha. Beta.\n\n", split.acceptedPrefix());
        assertEquals("Gamma. Delta.", split.remainder());
    }

    @Test
    void fallsBackToSentenceWhenFirstParagraphExceedsBudget() {
        TextSegmentSplitter splitter = new TextSegmentSplitter(new IcuTextBoundaryAdapter());
        String text = "Alpha. Beta.\n\nGamma.";

        TextSplitResult split = splitter.splitFirst(text, part -> part.length() <= 8);

        assertEquals("Alpha. ", split.acceptedPrefix());
        assertEquals(text, split.acceptedPrefix() + split.remainder());
    }

    @Test
    void splitsUnbrokenUnicodeTextOnlyBetweenCompleteCodePoints() {
        TextBoundaryPort wholeTextOnly = new TextBoundaryPort() {
            @Override
            public List<Integer> sentenceEndOffsets(String text) {
                return List.of(text.length());
            }

            @Override
            public List<Integer> wordEndOffsets(String text) {
                return List.of(text.length());
            }
        };
        TextSegmentSplitter splitter = new TextSegmentSplitter(wholeTextOnly);
        String remainder = "🩺患者";
        List<String> parts = new ArrayList<>();

        while (!remainder.isEmpty()) {
            TextSplitResult split = splitter.splitFirst(remainder,
                    part -> part.codePointCount(0, part.length()) <= 1);
            parts.add(split.acceptedPrefix());
            remainder = split.remainder();
        }

        assertEquals(List.of("🩺", "患", "者"), parts);
        assertEquals("🩺患者", String.join("", parts));
    }
}
