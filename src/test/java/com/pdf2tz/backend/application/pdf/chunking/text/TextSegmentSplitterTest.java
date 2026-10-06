package com.pdf2tz.backend.application.pdf.chunking.text;

import com.pdf2tz.backend.infrastructure.text.IcuTextBoundaryAdapter;
import com.pdf2tz.backend.infrastructure.text.JTokkitTokenizerAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSegmentSplitterTest {

    @Test
    void prefersCompleteParagraphOverExtraSentenceFromNextParagraph() {
        TextSegmentSplitter splitter = new TextSegmentSplitter(
                new IcuTextBoundaryAdapter(), new JTokkitTokenizerAdapter());
        String text = "Alpha. Beta.\n\nGamma. Delta.";

        TextSplitResult split = splitter.splitFirst(text, part -> part.length() <= 23);

        assertEquals("Alpha. Beta.\n\n", split.acceptedPrefix());
        assertEquals("Gamma. Delta.", split.remainder());
    }

    @Test
    void fallsBackToSentenceWhenFirstParagraphExceedsBudget() {
        TextSegmentSplitter splitter = new TextSegmentSplitter(
                new IcuTextBoundaryAdapter(), new JTokkitTokenizerAdapter());
        String text = "Alpha. Beta.\n\nGamma.";

        TextSplitResult split = splitter.splitFirst(text, part -> part.length() <= 8);

        assertEquals("Alpha. ", split.acceptedPrefix());
        assertEquals(text, split.acceptedPrefix() + split.remainder());
    }
}
