package com.example.healthcare.fhir.config;

import java.time.Duration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires the HAPI FHIR R4 client.
 *
 * <p>The connect and read timeouts are applied to HAPI's restful client factory
 * before the client is created; HAPI passes them to the Apache HTTP client
 * builder it uses for every request. {@code read-timeout} maps to HAPI's socket
 * timeout, the HAPI name for the response read timeout.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FhirClientProperties.class)
public class FhirClientConfig {

    @Bean
    public FhirContext fhirContext(FhirClientProperties properties) {
        FhirContext context = FhirContext.forR4();
        IRestfulClientFactory clientFactory = context.getRestfulClientFactory();
        clientFactory.setConnectTimeout(toMilliseconds(properties.getConnectTimeout(), "connect-timeout"));
        clientFactory.setSocketTimeout(toMilliseconds(properties.getReadTimeout(), "read-timeout"));
        return context;
    }

    @Bean
    public IGenericClient fhirClient(FhirContext fhirContext, FhirClientProperties properties) {
        return fhirContext.newRestfulGenericClient(properties.getBaseUrl());
    }

    private static int toMilliseconds(Duration duration, String propertyName) {
        long millis = duration.toMillis();
        if (millis < 1 || millis > Integer.MAX_VALUE) {
            throw new IllegalStateException("healthcare.fhir." + propertyName
                    + " must be between 1ms and " + Integer.MAX_VALUE + "ms but was " + duration);
        }
        return (int) millis;
    }
}
