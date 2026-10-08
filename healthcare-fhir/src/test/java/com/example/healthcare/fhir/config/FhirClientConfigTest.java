package com.example.healthcare.fhir.config;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for the beans produced by {@link FhirClientConfig}: an R4
 * {@link FhirContext} and an {@link IGenericClient} with the configured base
 * URL and timeouts.
 */
class FhirClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FhirClientConfig.class)
            .withPropertyValues(
                    "healthcare.fhir.base-url=https://fhir.example.org/r4",
                    "healthcare.fhir.connect-timeout=2s",
                    "healthcare.fhir.read-timeout=45s");

    @Test
    void fhirContextBeanReportsR4() {
        contextRunner.run(context -> {
            FhirContext fhirContext = context.getBean(FhirContext.class);

            assertThat(fhirContext.getVersion().getVersion()).isEqualTo(FhirVersionEnum.R4);
            assertThat(fhirContext.getVersion().getVersion().getFhirVersionString()).startsWith("4.0");
        });
    }

    @Test
    void appliesTheConfiguredConnectAndReadTimeoutsToTheHapiHttpClient() {
        contextRunner.run(context -> {
            IRestfulClientFactory clientFactory = context.getBean(FhirContext.class).getRestfulClientFactory();

            assertThat(clientFactory.getConnectTimeout()).isEqualTo(2_000);
            assertThat(clientFactory.getSocketTimeout()).isEqualTo(45_000);
        });
    }

    @Test
    void genericClientBeanUsesTheConfiguredBaseUrlAndR4Context() {
        contextRunner.run(context -> {
            IGenericClient client = context.getBean(IGenericClient.class);

            assertThat(client.getServerBase()).isEqualTo("https://fhir.example.org/r4");
            assertThat(client.getFhirContext().getVersion().getVersion()).isEqualTo(FhirVersionEnum.R4);
        });
    }
}
