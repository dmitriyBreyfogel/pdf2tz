package com.pdf2tz.backend.infrastructure.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.pdf2tz.backend.application.maintenance.ChunkSemanticSearch;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.LongBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Local, pinned BGE-M3 semantic retrieval. No document data is sent outside the server. */
@Component
@ConditionalOnProperty(prefix = "pdf2tz.embeddings", name = "enabled", havingValue = "true")
public class OnnxMultilingualChunkSemanticSearch implements ChunkSemanticSearch {
    private static final List<String> QUERIES = List.of(
            "medical device technical maintenance periodic inspection preventive service",
            "medical device cleaning disinfection sterilization maintenance procedure",
            "medical device maintenance consumables cleaning agents replacement parts"
    );

    private final EmbeddingProperties properties;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final List<float[]> queryVectors;

    public OnnxMultilingualChunkSemanticSearch(EmbeddingProperties properties) {
        this.properties = properties;
        Path modelDirectory = Path.of(properties.modelPath()).toAbsolutePath().normalize();
        requireModelFiles(modelDirectory);
        try {
            this.tokenizer = HuggingFaceTokenizer.newInstance(modelDirectory);
            this.session = OrtEnvironment.getEnvironment().createSession(modelDirectory.resolve("model.onnx").toString(), new OrtSession.SessionOptions());
            this.queryVectors = QUERIES.stream().map(this::embed).toList();
        } catch (OrtException | IOException exception) {
            throw new IllegalStateException("Не удалось загрузить локальную embedding-модель из " + modelDirectory, exception);
        }
    }

    @Override
    public Set<Integer> findRelevantChunkNumbers(List<DocumentChunk> chunks) {
        List<ScoredChunk> candidates = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] vector = embed(chunk.content());
            double similarity = queryVectors.stream().mapToDouble(query -> cosine(query, vector)).max().orElse(0);
            if (similarity >= properties.similarityThreshold()) candidates.add(new ScoredChunk(chunk.chunkNumber(), similarity));
        }
        return candidates.stream().sorted(Comparator.comparingDouble(ScoredChunk::similarity).reversed())
                .limit(properties.maxCandidates()).map(ScoredChunk::chunkNumber).collect(Collectors.toSet());
    }

    private float[] embed(String text) {
        Encoding encoding = tokenizer.encode(text);
        long[] ids = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();
        OnnxTensor tokenTypes = null;
        try (OnnxTensor inputIds = tensor(ids); OnnxTensor attention = tensor(attentionMask)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIds);
            if (session.getInputNames().contains("attention_mask")) inputs.put("attention_mask", attention);
            if (session.getInputNames().contains("token_type_ids")) {
                tokenTypes = tensor(new long[ids.length]);
                inputs.put("token_type_ids", tokenTypes);
            }
            try (OrtSession.Result result = session.run(inputs)) {
                OnnxValue value = result.get(0);
                float[][][] embeddings = (float[][][]) value.getValue();
                return normalize(embeddings[0][0]);
            }
        } catch (OrtException exception) {
            throw new IllegalStateException("Не удалось вычислить embedding для чанка", exception);
        } finally {
            if (tokenTypes != null) tokenTypes.close();
        }
    }

    private OnnxTensor tensor(long[] values) throws OrtException {
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(values), new long[]{1, values.length});
    }

    private static double cosine(float[] left, float[] right) {
        double result = 0;
        for (int index = 0; index < left.length; index++) result += left[index] * right[index];
        return result;
    }

    private static float[] normalize(float[] vector) {
        double length = 0;
        for (float value : vector) length += value * value;
        float[] normalized = new float[vector.length];
        for (int index = 0; index < vector.length; index++) normalized[index] = (float) (vector[index] / Math.sqrt(length));
        return normalized;
    }

    private static void requireModelFiles(Path modelDirectory) {
        if (modelDirectory.toString().isBlank() || !Files.isRegularFile(modelDirectory.resolve("model.onnx"))
                || !Files.isRegularFile(modelDirectory.resolve("tokenizer.json"))) {
            throw new IllegalStateException("Локальная embedding-модель не установлена: " + modelDirectory);
        }
    }

    @PreDestroy
    void close() throws OrtException {
        session.close();
    }

    private record ScoredChunk(int chunkNumber, double similarity) { }
}
