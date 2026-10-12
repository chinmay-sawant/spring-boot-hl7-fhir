package com.example.fhirdemo.service;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v251.message.ADT_A01;
import ca.uhn.hl7v2.util.Terser;
import jakarta.annotation.PreDestroy;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HL7v2 demo service: parses pipe-delimited ADT^A01 messages with HAPI HL7v2,
 * converts them to FHIR R4 Patients, and generates minimal ADT^A01 messages.
 *
 * <p>Mapping notes (HL7v2 -&gt; FHIR): PID-5 (XPN family^given) -&gt; Patient.name,
 * PID-7 (DTM, usually {@code yyyyMMdd}) -&gt; Patient.birthDate, PID-8
 * (M/F/O/U/...) -&gt; Patient.gender, PID-3.1 (CX id) -&gt; Patient.identifier.
 */
@Service
public class Hl7v2Service {

    private static final String SAMPLE_CLASSPATH = "/hl7-samples/adt-a01.hl7";
    private static final DateTimeFormatter MSH_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final HapiContext hapiContext;

    public Hl7v2Service() {
        this.hapiContext = new DefaultHapiContext();
    }

    @PreDestroy
    void close() {
        try {
            hapiContext.close();
        } catch (Exception ignored) {
            // Best-effort shutdown of HAPI executors; nothing to do on failure.
        }
    }

    /**
     * Parses an ADT^A01 pipe message and extracts showcase fields.
     *
     * @return map with sendingApp, messageType, messageCode, triggerEvent,
     *         patientId, familyName, givenName, dob, gender, patientClass
     * @throws IllegalArgumentException if the message is blank, unparseable, or not ADT
     */
    public Map<String, String> parseAdt(String hl7) {
        Terser terser = terserOf(requireAdt(hl7));
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("sendingApp", terser.get("/MSH-3"));
            fields.put("messageType", terser.get("/MSH-9"));
            fields.put("messageCode", terser.get("/MSH-9-1"));
            fields.put("triggerEvent", terser.get("/MSH-9-2"));
            fields.put("patientId", terser.get("/PID-3-1"));
            fields.put("familyName", terser.get("/PID-5-1"));
            fields.put("givenName", terser.get("/PID-5-2"));
            fields.put("dob", terser.get("/PID-7-1"));
            fields.put("gender", terser.get("/PID-8"));
            fields.put("patientClass", terser.get("/PV1-2"));
            return fields;
        } catch (HL7Exception e) {
            throw new IllegalArgumentException("Failed to read ADT fields: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the bundled {@code hl7-samples/adt-a01.hl7} sample and parses it.
     *
     * @throws IllegalStateException if the sample is missing or unreadable
     */
    public Map<String, String> parseSampleFile() {
        try (InputStream in = getClass().getResourceAsStream(SAMPLE_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Classpath sample not found: hl7-samples/adt-a01.hl7");
            }
            String hl7 = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return parseAdt(hl7);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read HL7 sample file", e);
        }
    }

    /**
     * Converts an ADT^A01 message to an FHIR R4 Patient
     * (PID-5 name, PID-7 birth date, PID-8 gender, PID-3 identifier).
     */
    public Patient convertAdtToFhirPatient(String hl7) {
        Terser terser = terserOf(requireAdt(hl7));
        try {
            String family = terser.get("/PID-5-1");
            String given = terser.get("/PID-5-2");
            String dobRaw = terser.get("/PID-7-1");
            String genderCode = terser.get("/PID-8");
            String identifier = terser.get("/PID-3-1");

            Patient patient = new Patient();
            if (isPresent(family) || isPresent(given)) {
                patient.addName().setFamily(nullToEmpty(family)).addGiven(nullToEmpty(given));
            }
            Date birthDate = parseHl7Date(dobRaw);
            if (birthDate != null) {
                patient.setBirthDate(birthDate);
            }
            patient.setGender(mapGender(genderCode));
            if (isPresent(identifier)) {
                patient.addIdentifier()
                        .setSystem("http://hospital.example.org/mrn")
                        .setValue(identifier);
            }
            return patient;
        } catch (HL7Exception e) {
            throw new IllegalArgumentException("Failed to convert ADT to Patient: " + e.getMessage(), e);
        }
    }

    /**
     * Builds a minimal ADT^A01 pipe message (MSH|EVN|PID|PV1 segments,
     * {@code \r}-terminated) for the given patient data.
     *
     * @param dob    birth date as {@code yyyyMMdd} (may be null/blank)
     * @param gender M/F/O/U or full word (male/female/...), defaults to U
     * @param id     MRN for PID-3 (may be null/blank)
     */
    public String generateAdtForPatient(String family, String given, String dob, String gender, String id) {
        String now = LocalDateTime.now().format(MSH_DATETIME);
        String controlId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String genderNorm = normalizeGender(gender);

        String msh = String.join("|", "MSH", "^~\\&",
                escape("FHIRDEMO"), escape("DEMO_FACILITY"),
                escape("RECEIVER"), escape("RECEIVING_FACILITY"),
                now, "", "ADT^A01^ADT_A01", controlId, "P", "2.5.1");
        String evn = "EVN||" + now;
        String pid = String.join("|", "PID", "1", "",
                escape(nullToEmpty(id)) + "^^^DEMO^MR", "",
                escape(nullToEmpty(family)) + "^" + escape(nullToEmpty(given)), "",
                nullToEmpty(dob).trim(), genderNorm);
        String pv1 = "PV1|1|I";
        return msh + "\r" + evn + "\r" + pid + "\r" + pv1 + "\r";
    }

    /**
     * Round-trip check: generate an ADT^A01 from the given data, re-parse it,
     * and verify family/given/DOB/gender survive the trip.
     */
    public boolean roundTrip(String family, String given, String dob, String gender, String id) {
        Map<String, String> parsed = parseAdt(generateAdtForPatient(family, given, dob, gender, id));
        return Objects.equals(nullToEmpty(family), nullToEmpty(parsed.get("familyName")))
                && Objects.equals(nullToEmpty(given), nullToEmpty(parsed.get("givenName")))
                && Objects.equals(nullToEmpty(dob).trim(), nullToEmpty(parsed.get("dob")))
                && Objects.equals(normalizeGender(gender), nullToEmpty(parsed.get("gender")));
    }

    // ---- helpers ----

    private Message requireAdt(String hl7) {
        if (hl7 == null || hl7.isBlank()) {
            throw new IllegalArgumentException("HL7 message must not be blank");
        }
        try {
            Message message = hapiContext.getPipeParser().parse(hl7);
            if (!(message instanceof ADT_A01)) {
                throw new IllegalArgumentException(
                        "Expected ADT^A01 message but got: " + message.getName());
            }
            return message;
        } catch (HL7Exception e) {
            throw new IllegalArgumentException("Invalid HL7v2 message: " + e.getMessage(), e);
        }
    }

    private static Terser terserOf(Message message) {
        return new Terser(message);
    }

    static Enumerations.AdministrativeGender mapGender(String code) {
        if (code == null) {
            return Enumerations.AdministrativeGender.UNKNOWN;
        }
        switch (code.trim().toUpperCase()) {
            case "M":
            case "MALE":
                return Enumerations.AdministrativeGender.MALE;
            case "F":
            case "FEMALE":
                return Enumerations.AdministrativeGender.FEMALE;
            case "O":
            case "OTHER":
            case "A":
            case "AMBIGUOUS":
                return Enumerations.AdministrativeGender.OTHER;
            default:
                return Enumerations.AdministrativeGender.UNKNOWN;
        }
    }

    static String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return "U";
        }
        switch (gender.trim().toUpperCase()) {
            case "M":
            case "MALE":
                return "M";
            case "F":
            case "FEMALE":
                return "F";
            case "O":
            case "OTHER":
                return "O";
            case "A":
            case "AMBIGUOUS":
                return "A";
            case "U":
            case "UNKNOWN":
                return "U";
            default:
                return "U";
        }
    }

    static Date parseHl7Date(String raw) {
        if (raw == null || raw.trim().length() < 8) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim().substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** HL7 escape for free-text fields so generated separators survive parsing. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\E\\")
                .replace("|", "\\F\\")
                .replace("^", "\\S\\")
                .replace("~", "\\R\\")
                .replace("&", "\\T\\")
                .replace("\r", "")
                .replace("\n", "");
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
