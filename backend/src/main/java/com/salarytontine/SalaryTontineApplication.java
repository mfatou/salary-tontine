package com.salarytontine;

import com.salarytontine.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SalaryTontineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalaryTontineApplication.class, args);
    }
}
