package org.immregistries.iis.kernal.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.ContactComponent;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.immregistries.codebase.client.CodeMap;
import org.immregistries.codebase.client.generated.Code;
import org.immregistries.codebase.client.reference.CodesetType;
import org.immregistries.iis.kernal.logic.CodeMapManager;
import org.immregistries.iis.kernal.logic.IncomingMessageHandler;
import org.immregistries.iis.kernal.model.OrgAccess;
import org.immregistries.iis.kernal.model.OrgMaster;
import org.immregistries.iis.kernal.model.PatientMaster;
import org.immregistries.iis.kernal.model.PatientReported;
import org.immregistries.iis.kernal.model.VaccinationMaster;
import org.immregistries.iis.kernal.model.VaccinationReported;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

@SuppressWarnings("serial")
public class V2ToFhirServlet extends HttpServlet {

  public static final String PARAM_PATIENT_REPORTED_ID = "patientReportedId";


  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    HttpSession session = req.getSession(true);
    OrgAccess orgAccess = (OrgAccess) session.getAttribute("orgAccess");
    if (orgAccess == null) {
      RequestDispatcher dispatcher = req.getRequestDispatcher("home");
      dispatcher.forward(req, resp);
      return;
    }

    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    Session dataSession = PopServlet.getDataSession();
    HomeServlet.doHeader(out, session);
    try {
      PatientReported pr = (PatientReported) dataSession.get(PatientReported.class,
          Integer.parseInt(req.getParameter(PARAM_PATIENT_REPORTED_ID)));


      try {
        CodeMap codeMap = CodeMapManager.getCodeMap();
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();
        parser.setPrettyPrint(true);

        Bundle bundle = new Bundle();

        Patient p = new Patient();
        createPatientResource(pr, p);
        bundle.addEntry().setResource(p);
        List<VaccinationMaster> vaccinationMasterList =
            IncomingMessageHandler.getVaccinationMasterList(pr.getPatient(), dataSession);

        for (VaccinationMaster vaccination : vaccinationMasterList) {
          Immunization immunization = new Immunization();
          Code cvxCode = codeMap.getCodeForCodeset(CodesetType.VACCINATION_CVX_CODE,
              vaccination.getVaccineCvxCode());
          if (cvxCode == null) {
            continue;
          }
          VaccinationReported vaccinationReported = vaccination.getVaccinationReported();
          if ("D".equals(vaccinationReported.getActionCode())) {
            continue;
          }
          createImmunizationResource(vaccination, immunization, cvxCode, codeMap);
          bundle.addEntry().setResource(immunization);
        }



        String serialized = parser.encodeResourceToString(bundle);
        out.println("<h3>JSON</h3>");
        out.println("<textarea cols=\"100\" rows=\"30\">" + serialized + "</textarea>");

      } catch (Exception e) {
        out.println("<h3>Exception Thrown</h3>");
        out.println("<pre>");
        e.printStackTrace();
        out.println("</pre>");
      }



    } catch (Exception e) {
      System.err.println("Unable to render page: " + e.getMessage());
      e.printStackTrace(System.err);
    } finally {
      dataSession.close();
    }
    HomeServlet.doFooter(out, session);
    out.flush();
    out.close();
  }

  public CodeableConcept createCodeableConcept(String value, CodesetType codesetType,
      String tableName, CodeMap codeMap) {
    CodeableConcept codeableConcept = null;
    if (value != null) {
      Code code = codeMap.getCodeForCodeset(codesetType, value);
      if (code != null) {
        if (tableName != null) {
          codeableConcept = new CodeableConcept();
          Coding coding = codeableConcept.addCoding();
          coding.setCode(code.getValue());
          coding.setDisplay(code.getLabel());
          coding.setSystem(tableName);
        }
      }
    }
    return codeableConcept;
  }

  private void createImmunizationResource(VaccinationMaster vaccination, Immunization immunization,
      Code cvxCode, CodeMap codeMap) {
    VaccinationReported vaccinationReported = vaccination.getVaccinationReported();

    {
      DateTimeType occurance = new DateTimeType(vaccinationReported.getAdministeredDate());
      immunization.setOccurrence(occurance);
    }
    {
      CodeableConcept vaccineCode = new CodeableConcept();
      Coding cvxCoding = vaccineCode.addCoding();
      cvxCoding.setCode(cvxCode.getValue());
      cvxCoding.setDisplay(cvxCode.getLabel());
      cvxCoding.setSystem("CVX");
      immunization.setVaccineCode(vaccineCode);
    }
    if (StringUtils.isNotEmpty(vaccinationReported.getVaccineNdcCode())) {
      CodeableConcept ndcCoding = createCodeableConcept(vaccinationReported.getVaccineNdcCode(),
          CodesetType.VACCINATION_NDC_CODE, "NDC", codeMap);
      immunization.setVaccineCode(ndcCoding);
    }
    {
      String administeredAmount = vaccinationReported.getAdministeredAmount();
      if (StringUtils.isNotEmpty(administeredAmount)) {
        SimpleQuantity doseQuantity = new SimpleQuantity();
        try {
          double d = Double.parseDouble(administeredAmount);
          doseQuantity.setValue(d);
          immunization.setDoseQuantity(doseQuantity);
        } catch (NumberFormatException nfe) {
          //ignore
        }
      }
    }

    {
      String infoSource = vaccinationReported.getInformationSource();
      if (StringUtils.isNotEmpty(infoSource)) {
        immunization.setPrimarySource(infoSource.equals("00"));
      }
    }

    {
      String lotNumber = vaccinationReported.getLotnumber();
      if (StringUtils.isNotEmpty(lotNumber)) {
        immunization.setLotNumber(lotNumber);
      }
    }

    {
      Date expirationDate = vaccinationReported.getExpirationDate();
      if (expirationDate != null) {
        immunization.setExpirationDate(expirationDate);
      }
    }


    {
      CodeableConcept mvxCoding = createCodeableConcept(vaccinationReported.getVaccineMvxCode(),
          CodesetType.VACCINATION_MANUFACTURER_CODE, "MVX", codeMap);
      // todo, need to make a reference
    }

    // TODO Refusal reasons
    // TODO Vaccination completion
    // TODO Route
    // TODO Site

    // TODO Observations

  }

  private void createPatientResource(PatientReported pr, Patient p) {
    PatientMaster pm = pr.getPatient();
    {
      Identifier id = p.addIdentifier();
      id.setValue(pm.getPatientExternalLink());
      CodeableConcept type = new CodeableConcept();
      type.addCoding().setCode("MR");
      id.setType(type);
    }
    {
      HumanName name = p.addName();
      name.setFamily(pr.getPatientNameLast());
      name.addGiven(pr.getPatientNameFirst());
      name.addGiven(pr.getPatientNameMiddle());
    }
    // TODO Mother's maiden name
    p.setBirthDate(pr.getPatientBirthDate());
    {
      AdministrativeGender administrativeGender = null;
      if (pr.getPatientSex().equals("F")) {
        administrativeGender = Enumerations.AdministrativeGender.FEMALE;
      } else if (pr.getPatientSex().equals("M")) {
        administrativeGender = Enumerations.AdministrativeGender.MALE;
      } else if (pr.getPatientSex().equals("O")) {
        administrativeGender = Enumerations.AdministrativeGender.OTHER;
      } else if (pr.getPatientSex().equals("U")) {
        administrativeGender = Enumerations.AdministrativeGender.UNKNOWN;
      } else if (pr.getPatientSex().equals("X")) {
        administrativeGender = Enumerations.AdministrativeGender.OTHER;
      }
      if (administrativeGender != null) {
        p.setGender(administrativeGender);
      }
    }
    // TODO Race - not supported by base specification, probably have to use extensions
    if (StringUtils.isNotEmpty(pr.getPatientAddressLine1())
        || StringUtils.isNotEmpty(pr.getPatientAddressZip())) {
      Address address = p.addAddress();
      if (StringUtils.isNotEmpty(pr.getPatientAddressLine1())) {
        address.addLine(pr.getPatientAddressLine1());
      }
      if (StringUtils.isNotEmpty(pr.getPatientAddressLine2())) {
        address.addLine(pr.getPatientAddressLine2());
      }
      address.setCity(pr.getPatientAddressCity());
      address.setState(pr.getPatientAddressState());
      address.setPostalCode(pr.getPatientAddressZip());
      address.setCountry(pr.getPatientAddressCountry());
      address.setDistrict(pr.getPatientAddressCountyParish());
    }
    {
      ContactPoint contactPoint = p.addTelecom();
      contactPoint.setSystem(ContactPointSystem.PHONE);
      contactPoint.setValue(pr.getPatientPhone());
    }
    // TODO Ethnicity not supported by base standard

    if (pr.getPatientBirthFlag().equals("Y")) {
      BooleanType booleanType = new BooleanType(true);
      p.setMultipleBirth(booleanType);
      if (StringUtils.isNotEmpty(pr.getPatientBirthOrder())) {
        try {
          int birthOrder = Integer.parseInt(pr.getPatientBirthOrder());
          IntegerType integerType = new IntegerType();
          integerType.setValue(birthOrder);
          p.setMultipleBirth(integerType);
        } catch (NumberFormatException nfe) {
          // ignore
        }
      }
    } else if (pr.getPatientBirthFlag().equals("N")) {
      BooleanType booleanType = new BooleanType(false);
      p.setMultipleBirth(booleanType);
    }

    if (!pr.getGuardianRelationship().equals("")
        && (!pr.getGuardianLast().equals("") || !pr.getGuardianFirst().equals(""))) {
      ContactComponent contactComponent = p.addContact();
      contactComponent.addRelationship().addCoding().setCode(pr.getGuardianRelationship());
      HumanName humanName = new HumanName();
      humanName.setFamily(pr.getGuardianLast());
      humanName.addGiven(pr.getGuardianFirst());
      contactComponent.setName(humanName);
    }

  }

}
