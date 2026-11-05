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
            this.queryVectors = embedBatch(QUERIES);
        } catch (OrtException | IOException exception) {
            throw new IllegalStateException("Не удалось загрузить локальную embedding-модель из " + modelDirectory, exception);
        }
    }

    @Override
    public Set<Integer> findRelevantChunkNumbers(List<DocumentChunk> chunks) {
        List<float[]> vectors = new ArrayList<>();
        for (int offset = 0; offset < chunks.size(); offset += properties.batchSize()) {
            List<String> texts = chunks.subList(offset, Math.min(offset + properties.batchSize(), chunks.size())).stream()
                    .map(chunk -> compact(chunk.content())).toList();
            vectors.addAll(embedBatch(texts));
        }
        List<ScoredChunk> candidates = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = chunks.get(index);
            float[] vector = vectors.get(index);
            double similarity = queryVectors.stream().mapToDouble(query -> cosine(query, vector)).max().orElse(0);
            if (similarity >= properties.similarityThreshold()) candidates.add(new ScoredChunk(chunk.chunkNumber(), similarity));
        }
        return candidates.stream().sorted(Comparator.comparingDouble(ScoredChunk::similarity).reversed())
                .limit(properties.maxCandidates()).map(ScoredChunk::chunkNumber).collect(Collectors.toSet());
    }

    private List<float[]> embedBatch(List<String> texts) {
        Encoding[] encodings = tokenizer.batchEncode(texts);
        int maxLength = java.util.Arrays.stream(encodings).mapToInt(encoding -> encoding.getIds().length).max().orElseThrow();
        long[] ids = new long[encodings.length * maxLength];
        long[] attentionMask = new long[encodings.length * maxLength];
        for (int index = 0; index < encodings.length; index++) {
            System.arraycopy(encodings[index].getIds(), 0, ids, index * maxLength, encodings[index].getIds().length);
            System.arraycopy(encodings[index].getAttentionMask(), 0, attentionMask, index * maxLength, encodings[index].getAttentionMask().length);
        }
        OnnxTensor tokenTypes = null;
        try (OnnxTensor inputIds = tensor(ids, encodings.length, maxLength); OnnxTensor attention = tensor(attentionMask, encodings.length, maxLength)) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIds);
            if (session.getInputNames().contains("attention_mask")) inputs.put("attention_mask", attention);
            if (session.getInputNames().contains("token_type_ids")) {
                tokenTypes = tensor(new long[ids.length], encodings.length, maxLength);
                inputs.put("token_type_ids", tokenTypes);
            }
            try (OrtSession.Result result = session.run(inputs)) {
                OnnxValue value = result.get(0);
                float[][][] embeddings = (float[][][]) value.getValue();
                return java.util.Arrays.stream(embeddings).map(embedding -> normalize(embedding[0])).toList();
            }
        } catch (OrtException exception) {
            throw new IllegalStateException("Не удалось вычислить embedding для чанка", exception);
        } finally {
            if (tokenTypes != null) tokenTypes.close();
        }
    }

    private OnnxTensor tensor(long[] values, int batchSize, int length) throws OrtException {
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(values), new long[]{batchSize, length});
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

    private static String compact(String content) {
        // The serialized section path is at the beginning. Keeping both ends preserves
        // the section meaning and table conclusions while making CPU inference practical.
        int limit = 1_800;
        if (content.length() <= limit) return content;
        return content.substring(0, 1_200) + "\n…\n" + content.substring(content.length() - 600);
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
