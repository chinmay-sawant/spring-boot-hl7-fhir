package com.example.healthcare.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.healthcare.patient.PatientService.PatientNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single {@code @RestControllerAdvice} for the application API, returning
 * RFC 7807 {@link ProblemDetail} bodies with the fields documented in
 * {@code documentation/architecture/api-conventions.md}: {@code type},
 * {@code title}, {@code status}, {@code detail}, {@code instance}, and
 * {@code errors} for Bean Validation failures.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps the framework's
 * exceptions (malformed JSON, wrong method, unsupported media type) on the
 * same ProblemDetail shape while the methods below add the application rules.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problemDetail.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problemDetail.setInstance(requestPath(request));
        problemDetail.setProperty("errors", collectFieldErrors(exception.getBindingResult()));
        return handleExceptionInternal(exception, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(PatientNotFoundException.class)
    ProblemDetail handlePatientNotFound(PatientNotFoundException exception, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problemDetail.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        problemDetail.setInstance(requestPath(request));
        return problemDetail;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response.getBody() instanceof ProblemDetail problemDetail && problemDetail.getInstance() == null) {
            problemDetail.setInstance(requestPath(request));
        }
        return response;
    }

    private static Map<String, String> collectFieldErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return errors;
    }

    private static URI requestPath(WebRequest request) {
        String description = request.getDescription(false);
        return URI.create(description.startsWith("uri=") ? description.substring("uri=".length()) : description);
    }
}
