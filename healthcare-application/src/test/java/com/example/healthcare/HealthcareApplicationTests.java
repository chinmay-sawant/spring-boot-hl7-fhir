package com.example.healthcare;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full application context on the test profile: the inert datasource
 * and disabled Flyway in {@code application-test.yml} mean no PostgreSQL and no
 * FHIR server are required.
 */
@SpringBootTest
@ActiveProfiles("test")
class HealthcareApplicationTests {

    @Autowired
    private FhirContext fhirContext;

    @Test
    void contextLoadsWithTheFhirClientWired() {
        assertThat(fhirContext.getVersion().getVersion()).isEqualTo(FhirVersionEnum.R4);
    }
}
