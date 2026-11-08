package com.pdf2tz.backend.infrastructure.registry;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoszdravnadzorRegistryClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsOnlyTheRegistryFieldsNeededForMatching() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/public-gateway/registered-med-product/api/v1/med-product/filter-public", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] body = """
                    {"content":[
                      {"id":12,"noRu":"РЗН 2017/5614","name":"Платформа Valleylab FT10","producer":{"name":"Covidien"},"status":{"code":"active"}},
                      {"id":13,"noRu":"ФС 2006/1364","name":"Старое изделие","producer":{"name":"Karl Storz"},"status":{"code":"deadline_has_expired"},"endDateRu":"2016-08-31"}
                    ]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        RoszdravnadzorRegistryProperties properties = new RoszdravnadzorRegistryProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());

        var records = new RoszdravnadzorRegistryClient(properties).search("Valleylab FT10");

        assertEquals(2, records.size());
        assertEquals("РЗН 2017/5614", records.get(0).registrationNumber());
        assertEquals(true, records.get(0).active());
        assertEquals(false, records.get(1).active());
        assertEquals(java.time.LocalDate.of(2016, 8, 31), records.get(1).expirationDate());
    }
}
