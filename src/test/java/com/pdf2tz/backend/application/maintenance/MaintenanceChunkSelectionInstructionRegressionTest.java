package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-PDF regression suite for the evidence selector; no LLM requests are made. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "PDF_INSTRUCTION_DIRECTORY", matches = ".+")
class MaintenanceChunkSelectionInstructionRegressionTest {

    @Autowired
    private PdfChunkingPipeline chunkingPipeline;
    @Autowired
    private MaintenanceChunkSelector selector;

    @Test
    void prioritizesActualMaintenanceSectionOverSafetyAndContentsInFabiusInstruction() throws Exception {
        List<DocumentChunk> selected = selector.select(chunkingPipeline.chunk(content("1373967.pdf")).chunks());

        assertFalse(selected.isEmpty());
        assertTrue(selected.stream().anyMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("Профилактическое техническое обслуживание"))));
        assertTrue(selected.stream().anyMatch(chunk -> chunk.pageRange().startPageNumber() >= 200));
        assertTrue(selected.stream().anyMatch(chunk -> chunk.pageRange().startPageNumber() == 187));
        assertTrue(selected.stream().anyMatch(chunk -> chunk.pageRange().startPageNumber() == 202));
        assertTrue(selected.stream().filter(chunk -> chunk.chunkNumber() != 1).noneMatch(chunk ->
                chunk.sectionPath().headings().stream().anyMatch(heading ->
                        heading.text().contains("Общая информация по технике безопасности"))));
    }

    @Test
    void excludesSafetySectionsWhenInstructionContainsRealMaintenanceSection() throws Exception {
        List<DocumentChunk> selected = selector.select(chunkingPipeline.chunk(content("1373228.pdf")).chunks());

        assertTrue(selected.stream().anyMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("Техобслуживание"))));
        assertTrue(selected.stream().anyMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("Reinigen und Desinfizieren"))));
        assertTrue(selected.stream().noneMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("Personal safety instructions")
                        || heading.text().contains("S a fe handling"))),
                () -> selected.stream().map(chunk -> chunk.chunkNumber() + ":" + chunk.sectionPath().headings()).toList().toString());
    }

    @Test
    void retainsCleaningAndPeriodicMaintenanceEvidence() throws Exception {
        List<DocumentChunk> selected = selector.select(chunkingPipeline.chunk(content("1373589.pdf")).chunks());

        assertTrue(selected.stream().anyMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("Чистка и дезинфекция"))));
        assertTrue(selected.stream().anyMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("Периодичность технического обслуживания"))));
    }

    @Test
    void retainsEnglishCleaningProcedureWithoutIntroductionOrErrorCodes() throws Exception {
        List<DocumentChunk> selected = selector.select(chunkingPipeline.chunk(content("1373694.pdf")).chunks());

        assertTrue(selected.stream().anyMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("DESCRIBING SPECIFIC CLEANING PROCEDURES"))));
        assertTrue(selected.stream().noneMatch(chunk -> chunk.sectionPath().headings().stream()
                .anyMatch(heading -> heading.text().contains("INTRODUCING THE CELL SAVER 5+ SYSTEM")
                        || heading.text().contains("DESCRIBING CELL SAVER 5+ ERROR CODES"))),
                () -> selected.stream().map(chunk -> chunk.chunkNumber() + ":" + chunk.sectionPath().headings()).toList().toString());
    }

    private byte[] content(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getenv("PDF_INSTRUCTION_DIRECTORY"), name));
    }
}
