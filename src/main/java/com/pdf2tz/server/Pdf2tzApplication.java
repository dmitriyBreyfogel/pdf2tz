package com.pdf2tz.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class Pdf2tzApplication {

    public static void main(String[] args) {
        SpringApplication.run(Pdf2tzApplication.class, args);
    }

}
