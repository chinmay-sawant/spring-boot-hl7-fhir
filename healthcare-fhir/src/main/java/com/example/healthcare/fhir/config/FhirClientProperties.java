package com.example.healthcare.fhir.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the outbound HAPI FHIR client.
 *
 * <p>Binds the {@code healthcare.fhir} prefix. Every property has a local
 * default, so the client works against the docker-compose stack with no
 * additional configuration.
 *
 * <dl>
 *   <dt>{@code healthcare.fhir.base-url}</dt>
 *   <dd>FHIR server base URL, default {@code http://localhost:8080/fhir}</dd>
 *   <dt>{@code healthcare.fhir.connect-timeout}</dt>
 *   <dd>TCP connect timeout, default {@code 5s}</dd>
 *   <dt>{@code healthcare.fhir.read-timeout}</dt>
 *   <dd>Response read timeout (the HAPI/Apache socket timeout), default {@code 30s}</dd>
 * </dl>
 */
@ConfigurationProperties(prefix = "healthcare.fhir")
public class FhirClientProperties {

    /** Base URL of the FHIR server. Defaults to the local HAPI FHIR R4 server. */
    private String baseUrl = "http://localhost:8080/fhir";

    /** TCP connect timeout. Defaults to 5 seconds. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** Response read timeout, applied as the HAPI socket timeout. Defaults to 30 seconds. */
    private Duration readTimeout = Duration.ofSeconds(30);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
