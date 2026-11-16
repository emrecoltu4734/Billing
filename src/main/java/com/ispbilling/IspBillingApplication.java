package com.ispbilling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IspBillingApplication {
    public static void main(String[] args) {
        SpringApplication.run(IspBillingApplication.class, args);
    }
}
