package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupResult;
import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupStatus;
import com.pdf2tz.backend.application.maintenance.model.RegistrationRegistryRecord;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.infrastructure.registry.RoszdravnadzorRegistryProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Finds an official registration number only when an active registry match is unique. */
@Service
public class RegistrationNumberLookupService {
    private static final String CARD_URL = "/widget/med-product/";
    private final MedicalDeviceIdentityExtractor identityExtractor;
    private final RegistrationRegistryClient registryClient;
    private final RoszdravnadzorRegistryProperties properties;
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();
    private Instant lastRequestAt = Instant.EPOCH;

    public RegistrationNumberLookupService(MedicalDeviceIdentityExtractor identityExtractor,
                                           RegistrationRegistryClient registryClient,
                                           RoszdravnadzorRegistryProperties properties) {
        this.identityExtractor = identityExtractor;
        this.registryClient = registryClient;
        this.properties = properties;
    }

    public RegistrationNumberLookupResult lookup(List<DocumentChunk> chunks) {
        String sourceText = chunks.stream().map(DocumentChunk::content).reduce("", (left, right) -> left + "\n" + right);
        return lookup(sourceText);
    }

    public RegistrationNumberLookupResult lookup(String sourceText) {
        Optional<String> query = identityExtractor.extractQuery(sourceText);
        if (query.isEmpty()) {
            return RegistrationNumberLookupResult.unavailable(RegistrationNumberLookupStatus.NOT_FOUND, null);
        }
        if (!properties.isEnabled()) {
            return RegistrationNumberLookupResult.unavailable(RegistrationNumberLookupStatus.DISABLED, query.get());
        }
        CachedResult cached = cache.get(query.get().toLowerCase(Locale.ROOT));
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.result();
        }
        try {
            waitForRequestSlot();
            RegistrationNumberLookupResult result = choose(query.get(), registryClient.search(query.get()));
            cache.put(query.get().toLowerCase(Locale.ROOT), new CachedResult(result, Instant.now().plus(properties.getCacheTtl())));
            return result;
        } catch (Exception ignored) {
            return RegistrationNumberLookupResult.unavailable(RegistrationNumberLookupStatus.UNAVAILABLE, query.get());
        }
    }

    private RegistrationNumberLookupResult choose(String query, List<RegistrationRegistryRecord> records) {
        List<RegistrationRegistryRecord> candidates = candidates(records.stream().filter(RegistrationRegistryRecord::active).toList(), query);
        if (candidates.size() == 1) {
            return found(query, candidates.get(0), RegistrationNumberLookupStatus.FOUND);
        }
        if (candidates.size() != 1) {
            if (!candidates.isEmpty()) {
                return RegistrationNumberLookupResult.unavailable(RegistrationNumberLookupStatus.AMBIGUOUS, query);
            }
        }
        List<RegistrationRegistryRecord> expired = candidates(records.stream().filter(record -> !record.active()).toList(), query);
        if (expired.size() == 1) {
            return found(query, expired.get(0), RegistrationNumberLookupStatus.EXPIRED);
        }
        return RegistrationNumberLookupResult.unavailable(expired.isEmpty() ? RegistrationNumberLookupStatus.NOT_FOUND : RegistrationNumberLookupStatus.AMBIGUOUS, query);
    }

    private List<RegistrationRegistryRecord> candidates(List<RegistrationRegistryRecord> records, String query) {
        List<RegistrationRegistryRecord> sorted = records.stream()
                .sorted(Comparator.comparingLong(RegistrationRegistryRecord::id).reversed())
                .toList();
        List<RegistrationRegistryRecord> exact = sorted.stream().filter(record -> containsAllQueryTokens(record.name(), query)).toList();
        return exact.isEmpty() ? sorted : exact;
    }

    private RegistrationNumberLookupResult found(String query, RegistrationRegistryRecord record, RegistrationNumberLookupStatus status) {
        return new RegistrationNumberLookupResult(
                status,
                normalizeNumber(record.registrationNumber()),
                query,
                record.name(),
                record.producer(),
                properties.getBaseUrl() + CARD_URL + record.id(),
                record.expirationDate(),
                Instant.now()
        );
    }

    private boolean containsAllQueryTokens(String value, String query) {
        String normalizedValue = normalize(value);
        List<String> tokens = new ArrayList<>();
        for (String token : normalize(query).split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return !tokens.isEmpty() && tokens.stream().allMatch(normalizedValue::contains);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private String normalizeNumber(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private synchronized void waitForRequestSlot() throws InterruptedException {
        Instant allowedAt = lastRequestAt.plus(properties.getMinRequestInterval());
        long delay = allowedAt.toEpochMilli() - Instant.now().toEpochMilli();
        if (delay > 0) {
            Thread.sleep(delay);
        }
        lastRequestAt = Instant.now();
    }

    private record CachedResult(RegistrationNumberLookupResult result, Instant expiresAt) {}
}
