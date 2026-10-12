package com.example.fhirdemo.controller;

import com.example.fhirdemo.repository.EncounterRepository;
import com.example.fhirdemo.repository.ObservationRepository;
import com.example.fhirdemo.repository.PatientRepository;
import com.example.fhirdemo.repository.PractitionerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Server-rendered dashboard. Injects live H2 counts + patient list into Thymeleaf view.
 * Only depends on repository count()/findAll() to stay decoupled from service internals.
 */
@Controller
public class DashboardController {

  private final PatientRepository patientRepository;
  private final ObservationRepository observationRepository;
  private final EncounterRepository encounterRepository;
  private final PractitionerRepository practitionerRepository;

  public DashboardController(
      PatientRepository patientRepository,
      ObservationRepository observationRepository,
      EncounterRepository encounterRepository,
      PractitionerRepository practitionerRepository) {
    this.patientRepository = patientRepository;
    this.observationRepository = observationRepository;
    this.encounterRepository = encounterRepository;
    this.practitionerRepository = practitionerRepository;
  }

  @GetMapping("/")
  public String index(Model model) {
    model.addAttribute("patientCount", patientRepository.count());
    model.addAttribute("observationCount", observationRepository.count());
    model.addAttribute("encounterCount", encounterRepository.count());
    model.addAttribute("practitionerCount", practitionerRepository.count());
    model.addAttribute("patients", patientRepository.findAll());
    return "index";
  }
}
