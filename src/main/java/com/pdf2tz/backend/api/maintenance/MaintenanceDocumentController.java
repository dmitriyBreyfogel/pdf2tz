package com.pdf2tz.backend.api.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.api.pdf.PdfUploadReader;
import com.pdf2tz.backend.application.maintenance.MaintenanceDocumentService;
import com.pdf2tz.backend.application.maintenance.MaintenancePlanService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Returns the generated Word document and its consumables in one multipart response. */
@RestController
public class MaintenanceDocumentController implements MaintenanceDocumentApi {
    private static final MediaType DOCX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final PdfUploadReader uploadReader;
    private final MaintenancePlanService planService;
    private final MaintenanceDocumentService documentService;
    private final ObjectMapper mapper = new ObjectMapper();

    public MaintenanceDocumentController(PdfUploadReader uploadReader, MaintenancePlanService planService,
                                         MaintenanceDocumentService documentService) {
        this.uploadReader = uploadReader;
        this.planService = planService;
        this.documentService = documentService;
    }

    @Override
    public ResponseEntity<byte[]> generateDocument(MultipartFile file, String modelCode) {
        var plan = planService.generate(uploadReader.read(file), modelCode);
        byte[] document = documentService.create(plan);
        String boundary = "pdf2tz-" + UUID.randomUUID();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType("multipart/mixed; boundary=" + boundary))
                .body(multipart(boundary, document, plan.path("consumables")));
    }

    private byte[] multipart(String boundary, byte[] document, com.fasterxml.jackson.databind.JsonNode consumables) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            write(output, "--" + boundary + "\r\n");
            write(output, "Content-Type: " + DOCX + "\r\n");
            write(output, "Content-Disposition: attachment; name=\"document\"; filename=\"maintenance-plan.docx\"\r\n\r\n");
            output.write(document);
            write(output, "\r\n--" + boundary + "\r\n");
            write(output, "Content-Type: application/json; charset=UTF-8\r\n");
            write(output, "Content-Disposition: inline; name=\"consumables\"\r\n\r\n");
            output.write(mapper.writeValueAsBytes(consumables));
            write(output, "\r\n--" + boundary + "--\r\n");
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось собрать ответ с DOCX и расходниками", exception);
        }
    }

    private void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
