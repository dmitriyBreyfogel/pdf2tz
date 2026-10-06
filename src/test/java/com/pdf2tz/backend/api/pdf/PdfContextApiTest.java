package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.GlobalExceptionHandler;
import com.pdf2tz.backend.api.pdf.mapper.PdfChunkedDocumentResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfContextResponseMapper;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.PdfContextPreparationPipeline;
import com.pdf2tz.backend.application.pdf.PdfParsingPipeline;
import com.pdf2tz.backend.application.pdf.PdfTableParsingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.retrieval.PreparedPdfContext;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PdfContextApiTest {

    private final PdfContextPreparationPipeline pipeline = mock(PdfContextPreparationPipeline.class);
    private final PdfController controller = new PdfController(
            new PdfUploadReader(),
            mock(PdfParsingPipeline.class),
            mock(PdfChunkingPipeline.class),
            pipeline,
            mock(PdfTableParsingPipeline.class),
            mock(com.pdf2tz.backend.api.pdf.mapper.PdfResponseMapper.class),
            mock(com.pdf2tz.backend.api.pdf.mapper.PdfTableResponseMapper.class),
            mock(com.pdf2tz.backend.api.pdf.mapper.PdfParsedDocumentResponseMapper.class),
            new PdfChunkedDocumentResponseMapper(),
            new PdfContextResponseMapper(new PdfChunkedDocumentResponseMapper())
    );
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void acceptsPdfAndPromptAndReturnsSourcePages() throws Exception {
        byte[] content = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "manual.pdf", "application/pdf", content);
        DocumentChunk chunk = new DocumentChunk(3, SectionPath.root(),
                new PageRange(10, 11), "Проверка фильтра", 8);
        when(pipeline.prepare(any(byte[].class), eq("Периодичность проверки фильтра")))
                .thenReturn(new PreparedPdfContext("Периодичность проверки фильтра",
                        "Запрос и [SOURCE chunk=3 pages=10-11]", 20, 12, List.of(chunk)));

        mvc.perform(multipart("/api/v1/pdf/context")
                        .file(file).param("prompt", "Периодичность проверки фильтра"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextAvailable").value(true))
                .andExpect(jsonPath("$.totalChunkCount").value(12))
                .andExpect(jsonPath("$.selectedChunks[0].chunkNumber").value(3))
                .andExpect(jsonPath("$.selectedChunks[0].startPageNumber").value(10))
                .andExpect(jsonPath("$.selectedChunks[0].endPageNumber").value(11));
    }

    @Test
    void missingPromptAndMissingPdfAreClientErrors() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[]{1});
        mvc.perform(multipart("/api/v1/pdf/context").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROMPT"));
        mvc.perform(multipart("/api/v1/pdf/context").param("prompt", "Фильтр"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_REQUEST"));
    }

    @Test
    void blankPromptReturnsDomainErrorAndNoMatchIsExplicit() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "manual.pdf", "application/pdf", new byte[]{1});
        when(pipeline.prepare(any(byte[].class), eq(" "))).thenThrow(
                AppException.build(ErrorCode.INVALID_PROMPT, "Запрос пользователя не должен быть пустым"));
        when(pipeline.prepare(any(byte[].class), eq("Несуществующая операция"))).thenReturn(
                new PreparedPdfContext("Несуществующая операция", "", 0, 12, List.of()));

        mvc.perform(multipart("/api/v1/pdf/context").file(file).param("prompt", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROMPT"));
        mvc.perform(multipart("/api/v1/pdf/context").file(file)
                        .param("prompt", "Несуществующая операция"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextAvailable").value(false))
                .andExpect(jsonPath("$.selectedChunks.length()").value(0))
                .andExpect(jsonPath("$.preparedPrompt").value(""));
    }
}
