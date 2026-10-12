package com.example.fhirdemo.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfig {

    @Value("${hapi.fhir.server-base:https://hapi.fhir.org/baseR4}")
    private String fhirServerBase;

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public IParser fhirJsonParser(FhirContext fhirContext) {
        return fhirContext.newJsonParser();
    }

    @Bean
    public IGenericClient fhirClient(FhirContext fhirContext) {
        return fhirContext.newRestfulGenericClient(fhirServerBase);
    }

    @Bean(destroyMethod = "close")
    public HapiContext hapiContext() {
        return new DefaultHapiContext();
    }
}
