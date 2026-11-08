package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.maintenance.model.RegistrationNumberLookupStatus;
import com.pdf2tz.backend.application.maintenance.model.RegistrationRegistryRecord;
import com.pdf2tz.backend.infrastructure.registry.RoszdravnadzorRegistryProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RegistrationNumberLookupServiceTest {
    @Test
    void returnsOnlyOneActiveExactRegistryMatchAndCachesIt() {
        RegistrationRegistryClient client = mock(RegistrationRegistryClient.class);
        when(client.search("Valleylab FT10")).thenReturn(List.of(
                new RegistrationRegistryRecord(204251L, " РЗН   2017/5614 ", "Платформа энергетическая серии FT Valleylab FT10 с принадлежностями", "Covidien", true)
        ));
        RegistrationNumberLookupService service = service(client);

        var first = service.lookup("Руководство по эксплуатации ValleylabTM FT10 Энергетическая платформа");
        var second = service.lookup("Руководство по эксплуатации ValleylabTM FT10 Энергетическая платформа");

        assertEquals(RegistrationNumberLookupStatus.FOUND, first.status());
        assertEquals("РЗН 2017/5614", first.registrationNumber());
        assertEquals(first, second);
        verify(client, times(1)).search("Valleylab FT10");
    }

    @Test
    void refusesAmbiguousOrExpiredRegistryRecords() {
        RegistrationRegistryClient client = mock(RegistrationRegistryClient.class);
        when(client.search("XENON NOVA")).thenReturn(List.of(
                new RegistrationRegistryRecord(1L, "ФС 2006/1364", "Источник света XENON NOVA", "Karl Storz", false),
                new RegistrationRegistryRecord(2L, "РЗН 2020/1", "Источник света XENON NOVA", "Karl Storz", true),
                new RegistrationRegistryRecord(3L, "РЗН 2021/2", "Источник света XENON NOVA", "Karl Storz", true)
        ));

        var result = service(client).lookup("Cold light fountain XENON NOVA");

        assertEquals(RegistrationNumberLookupStatus.AMBIGUOUS, result.status());
        assertEquals(null, result.registrationNumber());
    }

    @Test
    void acceptsAUniqueActiveSearchResultWhenTheCurrentRegistryTitleOmitsTheModel() {
        RegistrationRegistryClient client = mock(RegistrationRegistryClient.class);
        when(client.search("Arthropump")).thenReturn(List.of(
                new RegistrationRegistryRecord(5L, "ФСЗ 2011/09541", "Помпа эндоскопическая Karl Storz, с принадлежностями", "Karl Storz", true),
                new RegistrationRegistryRecord(6L, "МЗ РФ № 2002/188", "Эндоскопическое оборудование", "Karl Storz", false)
        ));

        var result = service(client).lookup("Помпа эндоскопическая Karl Storz, Arthropump с принадлежностями");

        assertEquals(RegistrationNumberLookupStatus.FOUND, result.status());
        assertEquals("ФСЗ 2011/09541", result.registrationNumber());
    }

    @Test
    void prefersTheNamedDeviceAfterCommaOverAnAddressWithDigits() {
        assertEquals("Arthropump", new MedicalDeviceIdentityExtractor()
                .extractQuery("Помпа эндоскопическая Karl Storz, Arthropump с принадлежностями. "
                        + "Karl Storz GmbH, MittelstraPe 8, D-78532 Tuttlingen")
                .orElseThrow());
    }

    private RegistrationNumberLookupService service(RegistrationRegistryClient client) {
        RoszdravnadzorRegistryProperties properties = new RoszdravnadzorRegistryProperties();
        properties.setMinRequestInterval(java.time.Duration.ZERO);
        return new RegistrationNumberLookupService(new MedicalDeviceIdentityExtractor(), client, properties);
    }
}
