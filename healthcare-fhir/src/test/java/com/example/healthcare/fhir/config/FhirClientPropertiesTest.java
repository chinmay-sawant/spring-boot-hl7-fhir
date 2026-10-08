package com.example.healthcare.fhir.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Binding tests for {@link FhirClientProperties} under the
 * {@code healthcare.fhir} prefix.
 */
class FhirClientPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(FhirClientConfig.class);

    @Test
    void bindsTheDocumentedDefaultsWhenNothingIsConfigured() {
        contextRunner.run(context -> {
            FhirClientProperties properties = context.getBean(FhirClientProperties.class);

            assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8080/fhir");
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void bindsOverriddenValuesWithRelaxedNames() {
        contextRunner
                .withPropertyValues(
                        "healthcare.fhir.base-url=https://fhir.example.org/r4",
                        "healthcare.fhir.connect-timeout=2s",
                        "healthcare.fhir.read-timeout=45s")
                .run(context -> {
                    FhirClientProperties properties = context.getBean(FhirClientProperties.class);

                    assertThat(properties.getBaseUrl()).isEqualTo("https://fhir.example.org/r4");
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(45));
                });
    }
}
