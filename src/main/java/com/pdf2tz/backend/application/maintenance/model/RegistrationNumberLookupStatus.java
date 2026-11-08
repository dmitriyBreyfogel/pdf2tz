package com.pdf2tz.backend.application.maintenance.model;

/** Result of a non-authoritative lookup in the public Roszdravnadzor registry. */
public enum RegistrationNumberLookupStatus {
    FOUND,
    EXPIRED,
    NOT_FOUND,
    AMBIGUOUS,
    UNAVAILABLE,
    DISABLED
}
