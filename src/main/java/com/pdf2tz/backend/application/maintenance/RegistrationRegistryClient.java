package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.maintenance.model.RegistrationRegistryRecord;

import java.util.List;

/** Boundary for a source of official medical-device registration records. */
public interface RegistrationRegistryClient {

    List<RegistrationRegistryRecord> search(String query);
}
