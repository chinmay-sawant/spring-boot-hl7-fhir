package com.example.healthcare.patient;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/**
 * REST request body for patient registration.
 *
 * <p>Bean Validation runs at the boundary, so invalid input produces a 400
 * through {@code GlobalExceptionHandler} and never reaches the use case. Field
 * names are camelCase and mirror {@link PatientResponse}.
 */
public record PatientRequest(
        @NotBlank(message = "must not be blank")
        String identifierSystem,

        @NotBlank(message = "must not be blank")
        String identifierValue,

        @NotBlank(message = "must not be blank")
        String familyName,

        @NotBlank(message = "must not be blank")
        String givenName,

        @NotNull(message = "must not be null")
        @PastOrPresent(message = "must be a past or present date")
        LocalDate birthDate,

        @NotBlank(message = "must not be blank")
        String gender) {
}
