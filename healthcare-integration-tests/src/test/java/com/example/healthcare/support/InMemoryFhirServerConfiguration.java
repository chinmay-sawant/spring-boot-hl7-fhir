package com.example.healthcare.support;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Hosts an in-memory FHIR R4 server inside the test application: a plain HAPI
 * {@link RestfulServer} registered as a servlet at {@code /fhir/*} on the same
 * embedded Tomcat as the Spring Boot app.
 *
 * <p>No container, no JPA schema, no network: the server lives in the test JVM
 * and is backed by {@link InMemoryPatientProvider}. The application's FHIR
 * client is pointed here through {@code healthcare.fhir.base-url} by
 * {@link AbstractIntegrationTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryFhirServerConfiguration {

    @Bean
    public InMemoryPatientProvider inMemoryPatientProvider() {
        return new InMemoryPatientProvider();
    }

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServlet(
            FhirContext fhirContext, InMemoryPatientProvider patientProvider) {
        RestfulServer server = new RestfulServer(fhirContext);
        server.setResourceProviders(patientProvider);
        ServletRegistrationBean<RestfulServer> registration =
                new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
