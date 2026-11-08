package com.pdf2tz.backend.application.maintenance.model;

import java.time.LocalDate;

/** Minimal public registry card needed for deterministic matching. */
public record RegistrationRegistryRecord(
        long id,
        String registrationNumber,
        String name,
        String producer,
        boolean active,
        LocalDate expirationDate
) {}
