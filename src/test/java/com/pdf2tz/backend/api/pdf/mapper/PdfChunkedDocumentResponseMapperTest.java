package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfChunkedDocumentResponseDto;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfChunkedDocumentResponseMapperTest {

    @Test
    void mapsChunkMetadataAndContentWithoutRecalculatingIt() {
        SectionPath path = new SectionPath(List.of(
                new SectionHeading(1, "1 Operation", 4)
        ));
        DocumentChunk chunk = new DocumentChunk(
                1,
                path,
                new PageRange(4, 6),
                "# 1 Operation\n\nBody",
                17
        );

        PdfChunkedDocumentResponseDto response =
                new PdfChunkedDocumentResponseMapper().toResponse(
                        new ChunkedDocument(List.of(chunk))
                );

        assertEquals(1, response.chunks().size());
        assertEquals(17, response.chunks().get(0).estimatedTokenCount());
        assertEquals("Body", response.chunks().get(0).content().substring(
                response.chunks().get(0).content().indexOf("Body")
        ));
        assertEquals(1, response.chunks().get(0).sectionPath().get(0).level());
    }
}
