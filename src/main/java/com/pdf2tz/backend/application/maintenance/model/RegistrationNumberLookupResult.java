package com.pdf2tz.backend.application.maintenance.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A traceable result of matching one device identity against the public registry.
 *
 * <p>The registration number is populated only for a unique active registry record.
 * The legal spelling returned by the registry is preserved: historic certificates
 * such as {@code ФСЗ} are not converted to a newer {@code РЗН} format.</p>
 */
public record RegistrationNumberLookupResult(
        RegistrationNumberLookupStatus status,
        String registrationNumber,
        String query,
        String registryName,
        String producer,
        String sourceUrl,
        LocalDate expirationDate,
        Instant checkedAt
) {
    public static RegistrationNumberLookupResult unavailable(RegistrationNumberLookupStatus status, String query) {
        return new RegistrationNumberLookupResult(status, null, query, null, null, null, null, Instant.now());
    }
}
