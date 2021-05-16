package org.immregistries.iis.kernal.logic;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.immregistries.codebase.client.CodeMap;
import org.immregistries.codebase.client.generated.Code;
import org.immregistries.codebase.client.reference.CodeStatusValue;
import org.immregistries.codebase.client.reference.CodesetType;
import org.immregistries.iis.kernal.SoftwareVersion;
import org.immregistries.iis.kernal.model.MessageReceived;
import org.immregistries.iis.kernal.model.ObservationMaster;
import org.immregistries.iis.kernal.model.ObservationReported;
import org.immregistries.iis.kernal.model.OrgAccess;
import org.immregistries.iis.kernal.model.OrgLocation;
import org.immregistries.iis.kernal.model.OrgMaster;
import org.immregistries.iis.kernal.model.PatientMaster;
import org.immregistries.iis.kernal.model.PatientReported;
import org.immregistries.iis.kernal.model.Person;
import org.immregistries.iis.kernal.model.ProcessingFlavor;
import org.immregistries.iis.kernal.model.VaccinationMaster;
import org.immregistries.iis.kernal.model.VaccinationReported;
import org.immregistries.smm.tester.manager.HL7Reader;
import org.immregistries.vfa.connect.ConnectFactory;
import org.immregistries.vfa.connect.ConnectorInterface;
import org.immregistries.vfa.connect.model.Admin;
import org.immregistries.vfa.connect.model.EvaluationActual;
import org.immregistries.vfa.connect.model.ForecastActual;
import org.immregistries.vfa.connect.model.Service;
import org.immregistries.vfa.connect.model.Software;
import org.immregistries.vfa.connect.model.SoftwareResult;
import org.immregistries.vfa.connect.model.TestCase;
import org.immregistries.vfa.connect.model.TestEvent;
import org.immregistries.vfa.connect.model.VaccineGroup;


public class IncomingMessageHandler {
  private static final String PATIENT_MIDDLE_NAME_MULTI = "Multi";
  // TODO:
  // Organize logic classes, need to have access classes for every object, maybe a new Access
  // package?
  // Look at names of database fields, make more consistent



  private static final String QBP_Z34 = "Z34";
  private static final String QBP_Z44 = "Z44";
  private static final String RSP_Z42_MATCH_WITH_FORECAST = "Z42";
  private static final String RSP_Z32_MATCH = "Z32";
  private static final String RSP_Z31_MULTIPLE_MATCH = "Z31";
  private static final String RSP_Z33_NO_MATCH = "Z33";
  private static final String Z23_ACKNOWLEDGEMENT = "Z23";
  private static final String QUERY_OK = "OK";
  private static final String QUERY_NOT_FOUND = "NF";
  private static final String QUERY_TOO_MANY = "TM";
  private static final String QUERY_APPLICATION_ERROR = "AE";

  private FhirContext ctx = Context.getCtx();

  protected Session dataSession = null;

  public IncomingMessageHandler(Session dataSession) {
    this.dataSession = dataSession;
  }


  public String process(String message, OrgAccess orgAccess) {
    HL7Reader reader = new HL7Reader(message);
    String messageType = reader.getValue(9);
    String responseMessage;
    try {
      Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();
      if (processingFlavorSet.contains(ProcessingFlavor.SOURSOP)) {
        String facilityId = reader.getValue(4);
        if (!facilityId.equals(orgAccess.getOrg().getOrganizationName())) {
          throw new ProcessingException("Not allowed to submit for facility indicated in MSH-4",
              "MSH", 1, 4);
        }
      }
      if (messageType.equals("VXU")) {
        responseMessage = processVXU(orgAccess, reader, message);
      } else if (messageType.equals("ORU")) {
        responseMessage = processORU(orgAccess, reader, message);
      } else if (messageType.equals("QBP")) {
        responseMessage = processQBP(orgAccess, reader, message);
      } else {
        ProcessingException pe = new ProcessingException("Unsupported message", "", 0, 0);
        List<ProcessingException> processingExceptionList = new ArrayList<>();
        processingExceptionList.add(pe);
        responseMessage = buildAck(reader, processingExceptionList);
        recordMessageReceived(message, null, responseMessage, "Unknown", "NAck",
            orgAccess.getOrg());
      }
    } catch (Exception e) {
      e.printStackTrace(System.err);
      List<ProcessingException> processingExceptionList = new ArrayList<>();
      processingExceptionList.add(new ProcessingException(
          "Internal error prevented processing: " + e.getMessage(), null, 0, 0));
      responseMessage = buildAck(reader, processingExceptionList);
    }
    return responseMessage;
  }



  @SuppressWarnings("unchecked")
public String processQBP(OrgAccess orgAccess, HL7Reader reader, String messageReceived) {
    PatientReported patientReported = null;
    List<PatientReported> patientReportedPossibleList = new ArrayList<>();
    List<ProcessingException> processingExceptionList = new ArrayList<>();
    if (reader.advanceToSegment("QPD")) {
      String mrn = "";
      {
        mrn = reader.getValueBySearchingRepeats(3, 1, "MR", 5);
        if (mrn.equals("")) {
          mrn = reader.getValueBySearchingRepeats(3, 1, "PT", 5);
        }
      }
      String problem = null;
      int fieldPosition = 0;
      if (!mrn.equals("")) {
        Query query = dataSession.createQuery(
            "from PatientReported where orgReported = ? and patientReportedExternalLink = ?");
        query.setParameter(0, orgAccess.getOrg());
        query.setParameter(1, mrn);
        List<PatientReported> patientReportedList = query.list();
        if (patientReportedList.size() > 0) {
          patientReported = patientReportedList.get(0);
        }
      }
      String patientNameLast = reader.getValue(4, 1);
      String patientNameFirst = reader.getValue(4, 2);
      String patientNameMiddle = reader.getValue(4, 3);
      boolean strictDate = false;

      Date patientBirthDate = parseDateWarn(reader.getValue(6), "Invalid patient birth date", "QPD",
          1, 6, strictDate, processingExceptionList);
      String patientSex = reader.getValue(7);

      if (patientNameLast.equals("")) {
        problem = "Last name is missing";
        fieldPosition = 4;
      } else if (patientNameFirst.equals("")) {
        problem = "First name is missing";
        fieldPosition = 4;
      } else if (patientBirthDate == null) {
        problem = "Date of Birth is missing";
        fieldPosition = 6;
      }
      if (problem != null) {
        processingExceptionList.add(new ProcessingException(problem, "QPD", 1, fieldPosition));
      } else {

        if (patientReported == null) {
          Query query = dataSession.createQuery(
              "from PatientReported where orgReported = :orgReported and Patient.patientBirthDate = :patientBirthDate "
                  + "and Patient.patientNameLast = :patientNameLast and Patient.patientNameFirst = :patientNameFirst ");
          query.setParameter("orgReported", orgAccess.getOrg());
          query.setParameter("patientBirthDate", patientBirthDate);
          query.setParameter("patientNameLast", patientNameLast);
          query.setParameter("patientNameFirst", patientNameFirst);
          List<PatientReported> patientReportedList = query.list();
          if (patientReportedList.size() > 0) {
            patientReported = patientReportedList.get(0);
          }
        }
        if (patientReported != null) {
          int points = 0;
          if (!patientNameLast.equals("")
              && patientNameLast.equalsIgnoreCase(patientReported.getPatientNameLast())) {
            points = points + 2;
          }
          if (!patientNameFirst.equals("")
              && patientNameFirst.equalsIgnoreCase(patientReported.getPatientNameFirst())) {
            points = points + 2;
          }
          if (!patientNameMiddle.equals("")
              && patientNameMiddle.equalsIgnoreCase(patientReported.getPatientNameFirst())) {
            points = points + 2;
          }
          if (patientBirthDate != null
              && patientBirthDate.equals(patientReported.getPatientBirthDate())) {
            points = points + 2;
          }
          if (!patientSex.equals("")
              && patientSex.equalsIgnoreCase(patientReported.getPatientSex())) {
            points = points + 2;
          }
          if (points < 6) {
            // not enough matching so don't indicate this as a match
            patientReported = null;
          }
        }
        if (patientReported == null) {
          Query query = dataSession.createQuery(
              "from PatientReported where orgReported = :orgReported and Patient.patientBirthDate = :patientBirthDate "
                  + "and (Patient.patientNameLast = :patientNameLast or Patient.patientNameFirst = :patientNameFirst) ");
          query.setParameter("orgReported", orgAccess.getOrg());
          query.setParameter("patientBirthDate", patientBirthDate);
          query.setParameter("patientNameLast", patientNameLast);
          query.setParameter("patientNameFirst", patientNameFirst);
          patientReportedPossibleList = query.list();
        }
        if (patientReported != null
            && patientNameMiddle.equalsIgnoreCase(PATIENT_MIDDLE_NAME_MULTI)) {
          patientReportedPossibleList.add(patientReported);
          patientReportedPossibleList.add(patientReported);
          patientReported = null;
        }
      }
    } else {
      processingExceptionList.add(new ProcessingException("QPD segment not found", null, 0, 0));
    }

    Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();
    if (processingFlavorSet.contains(ProcessingFlavor.SNAIL)
        || processingFlavorSet.contains(ProcessingFlavor.SNAIL30)
        || processingFlavorSet.contains(ProcessingFlavor.SNAIL60)
        || processingFlavorSet.contains(ProcessingFlavor.SNAIL90)) {
      Calendar calendar = Calendar.getInstance();
      int seconds = -30;
      if (processingFlavorSet.contains(ProcessingFlavor.SNAIL30)) {
        seconds = -30;
      } else if (processingFlavorSet.contains(ProcessingFlavor.SNAIL60)) {
        seconds = -60;
      } else if (processingFlavorSet.contains(ProcessingFlavor.SNAIL90)) {
        seconds = -90;
      } else {
        int delay = calendar.get(Calendar.MINUTE) % 4;
        seconds = delay * -30;
      }

      calendar.add(Calendar.SECOND, seconds);
      Date cutoff = calendar.getTime();
      if (patientReported != null) {
        if (cutoff.before(patientReported.getReportedDate())) {
          patientReported = null;
        }
      }
      for (Iterator<PatientReported> it = patientReportedPossibleList.iterator(); it.hasNext();) {
        PatientReported pr = it.next();
        if (cutoff.before(pr.getReportedDate())) {
          it.remove();
        }
      }
    }

    return buildRSP(reader, messageReceived, patientReported, orgAccess,
        patientReportedPossibleList, processingExceptionList);
  }

  @SuppressWarnings("unchecked")
public String processVXU(OrgAccess orgAccess, HL7Reader reader, String message) {

    List<ProcessingException> processingExceptionList = new ArrayList<>();
    try {
      Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();

      CodeMap codeMap = CodeMapManager.getCodeMap();

      boolean strictDate = true;
      if (processingFlavorSet.contains(ProcessingFlavor.CANTALOUPE)) {
        strictDate = false;
      }
      PatientReported patientReported = null;

      patientReported = processPatient(orgAccess, reader, processingExceptionList,
          processingFlavorSet, codeMap, strictDate, patientReported);



      int orcCount = 0;
      int rxaCount = 0;
      int obxCount = 0;
      int vaccinationCount = 0;
      int refusalCount = 0;
      while (reader.advanceToSegment("ORC")) {
        orcCount++;
        VaccinationReported vaccinationReported = null;
        VaccinationMaster vaccination = null;
        String vaccineCode = "";
        Date administrationDate = null;
        String vaccinationReportedExternalLink = reader.getValue(3);
        if (reader.advanceToSegment("RXA", "ORC")) {
          rxaCount++;
          vaccineCode = reader.getValue(5, 1);
          if (vaccineCode.equals("")) {
            throw new ProcessingException("Vaccine code is not indicated in RXA-5.1", "RXA",
                rxaCount, 5);
          }
          if (vaccineCode.equals("998")) {
            obxCount = readAndCreateObservations(reader, processingExceptionList, patientReported,
                strictDate, obxCount, null, null);
            continue;
          }
          if (vaccinationReportedExternalLink.equals("")) {
            throw new ProcessingException("Vaccination order id was not found, unable to process",
                "ORC", orcCount, 3);
          }
          administrationDate = parseDateError(reader.getValue(3, 1),
              "Could not read administered date in RXA-5", "RXA", rxaCount, 3, strictDate);
          if (administrationDate.after(new Date())) {
            throw new ProcessingException(
                "Vaccination is indicated as occuring in the future, unable to accept future vaccination events",
                "RXA", rxaCount, 3);
          }
          {
            Query query = dataSession.createQuery(
                "from VaccinationReported where patientReported = ? and vaccinationReportedExternalLink = ?");
            query.setParameter(0, patientReported);
            query.setParameter(1, vaccinationReportedExternalLink);
            List<VaccinationReported> vaccinationReportedList = query.list();
            if (vaccinationReportedList.size() > 0) {
              vaccinationReported = vaccinationReportedList.get(0);
              vaccination = vaccinationReported.getVaccination();
            }
          }
          if (vaccinationReported == null) {
            vaccination = new VaccinationMaster();
            vaccinationReported = new VaccinationReported();
            vaccinationReported.setVaccination(vaccination);
            vaccination.setVaccinationReported(null);
            vaccinationReported.setReportedDate(new Date());
            vaccinationReported.setVaccinationReportedExternalLink(vaccinationReportedExternalLink);
          }
          vaccinationReported.setPatientReported(patientReported);
          vaccination.setPatient(patientReported.getPatient());

          String vaccineCvxCode = "";
          String vaccineNdcCode = "";
          String vaccineCptCode = "";
          String vaccineCodeType = reader.getValue(5, 3);
          if (vaccineCodeType.equals("NDC")) {
            vaccineNdcCode = vaccineCode;
          } else if (vaccineCodeType.equals("CPT") || vaccineCodeType.equals("C4")
              || vaccineCodeType.equals("C5")) {
            vaccineCptCode = vaccineCode;
          } else {
            vaccineCvxCode = vaccineCode;
          }
          {
            String altVaccineCode = reader.getValue(5, 4);
            String altVaccineCodeType = reader.getValue(5, 6);
            if (!altVaccineCode.equals("")) {
              if (altVaccineCodeType.equals("NDC")) {
                if (vaccineNdcCode.equals("")) {
                  vaccineNdcCode = altVaccineCode;
                }
              } else if (altVaccineCodeType.equals("CPT") || altVaccineCodeType.equals("C4")
                  || altVaccineCodeType.equals("C5")) {
                if (vaccineCptCode.equals("")) {
                  vaccineCptCode = altVaccineCode;
                }
              } else {
                if (vaccineCvxCode.equals("")) {
                  vaccineCvxCode = altVaccineCode;
                }
              }
            }
          }

          {
            Code ndcCode =
                codeMap.getCodeForCodeset(CodesetType.VACCINATION_NDC_CODE, vaccineNdcCode);
            if (ndcCode != null) {
              if (ndcCode.getCodeStatus() != null && ndcCode.getCodeStatus().getDeprecated() != null
                  && ndcCode.getCodeStatus().getDeprecated().getNewCodeValue() != null
                  && !ndcCode.getCodeStatus().getDeprecated().getNewCodeValue().equals("")) {
                vaccineNdcCode = ndcCode.getCodeStatus().getDeprecated().getNewCodeValue();
              }
              Code cvxCode = codeMap.getRelatedCode(ndcCode, CodesetType.VACCINATION_CVX_CODE);
              if (cvxCode == null) {
                ProcessingException pe =
                    new ProcessingException("Unrecognized NDC " + vaccineNdcCode, "RXA", rxaCount,
                        5).setWarning();
                processingExceptionList.add(pe);
              } else {
                if (vaccineCvxCode.equals("")) {
                  vaccineCvxCode = cvxCode.getValue();
                } else if (!vaccineCvxCode.equals(cvxCode.getValue())) {
                  // NDC doesn't map to the CVX code that was submitted!
                  ProcessingException pe = new ProcessingException(
                      "NDC " + vaccineNdcCode + " maps to " + cvxCode.getValue() + " but CVX "
                          + vaccineCvxCode + " was also reported, preferring CVX code",
                      "RXA", rxaCount, 5);
                  pe.setWarning();
                  processingExceptionList.add(pe);
                }
              }
            }
          }
          {
            Code cptCode =
                codeMap.getCodeForCodeset(CodesetType.VACCINATION_CPT_CODE, vaccineCptCode);
            if (cptCode != null) {
              Code cvxCode = codeMap.getRelatedCode(cptCode, CodesetType.VACCINATION_CVX_CODE);
              if (cvxCode == null) {
                ProcessingException pe =
                    new ProcessingException("Unrecognized CPT " + cptCode, "RXA", rxaCount, 5)
                        .setWarning();
                processingExceptionList.add(pe);
              } else {
                if (vaccineCvxCode.equals("")) {
                  vaccineCvxCode = cvxCode.getValue();
                } else if (!vaccineCvxCode.equals(cvxCode.getValue())) {
                  // CPT doesn't map to the CVX code that was submitted!
                  ProcessingException pe = new ProcessingException(
                      "CPT " + vaccineCptCode + " maps to " + cvxCode.getValue() + " but CVX "
                          + vaccineCvxCode + " was also reported, preferring CVX code",
                      "RXA", rxaCount, 5).setWarning();
                  processingExceptionList.add(pe);
                }
              }
            }
          }
          if (vaccineCvxCode.equals("")) {
            throw new ProcessingException(
                "Unable to find a recognized vaccine administration code (CVX, NDC, or CPT)", "RXA",
                rxaCount, 5);
          } else {
            Code cvxCode =
                codeMap.getCodeForCodeset(CodesetType.VACCINATION_CVX_CODE, vaccineCvxCode);
            if (cvxCode != null) {
              vaccineCvxCode = cvxCode.getValue();
            } else {
              throw new ProcessingException("Unrecognized CVX vaccine '" + vaccineCvxCode + "'",
                  "RXA", rxaCount, 5);
            }

          }


          {
            String administeredAtLocation = reader.getValue(11, 4);
            if (StringUtils.isNotEmpty(administeredAtLocation)) {
              Query query = dataSession.createQuery(
                  "from OrgLocation where orgMaster = :orgMaster and orgFacilityCode = :orgFacilityCode");
              query.setParameter("orgMaster", orgAccess.getOrg());
              query.setParameter("orgFacilityCode", administeredAtLocation);
              List<OrgLocation> orgMasterList = query.list();
              OrgLocation orgLocation = null;
              if (orgMasterList.size() > 0) {
                orgLocation = orgMasterList.get(0);
              }

              if (orgLocation == null) {
                if (processingFlavorSet.contains(ProcessingFlavor.PEAR)) {
                  throw new ProcessingException(
                      "Unrecognized administered at location, unable to accept immunization report",
                      "RXA", rxaCount, 11);
                }
                orgLocation = new OrgLocation();
                orgLocation.setOrgFacilityCode(administeredAtLocation);
                orgLocation.setOrgMaster(orgAccess.getOrg());
                orgLocation.setOrgFacilityName(administeredAtLocation);
                orgLocation.setLocationType("");
                orgLocation.setAddressLine1(reader.getValue(11, 9));
                orgLocation.setAddressLine2(reader.getValue(11, 10));
                orgLocation.setAddressCity(reader.getValue(11, 11));
                orgLocation.setAddressState(reader.getValue(11, 12));
                orgLocation.setAddressZip(reader.getValue(11, 13));
                orgLocation.setAddressCountry(reader.getValue(11, 14));
                Transaction transaction = dataSession.beginTransaction();
                dataSession.save(orgLocation);
                transaction.commit();
              }
              vaccinationReported.setOrgLocation(orgLocation);
            }
          }
          {
            String admininsteringProvider = reader.getValue(10);
            if (StringUtils.isNotEmpty(admininsteringProvider)) {
              Person person = null;
              {
                Query query = dataSession.createQuery(
                    "from Person where orgMaster = :orgMaster and personExternalLink = :personExternalLink");
                query.setParameter("orgMaster", orgAccess.getOrg());
                query.setParameter("personExternalLink", admininsteringProvider);
                List<Person> personList = query.list();
                if (personList.size() > 0) {
                  person = personList.get(0);
                }
              }
              if (person == null) {
                person = new Person();
                person.setPersonExternalLink(admininsteringProvider);
                person.setOrgMaster(orgAccess.getOrg());
                person.setNameLast(reader.getValue(10, 2));
                person.setNameFirst(reader.getValue(10, 3));
                person.setNameMiddle(reader.getValue(10, 4));
                person.setAssigningAuthority(reader.getValue(10, 9));
                person.setNameTypeCode(reader.getValue(10, 10));
                person.setIdentifierTypeCode(reader.getValue(10, 13));
                person.setProfessionalSuffix(reader.getValue(10, 21));
                Transaction transaction = dataSession.beginTransaction();
                dataSession.save(person);
                transaction.commit();
              }
              vaccinationReported.setAdministeringProvider(person);
            }

          }
          vaccination.setVaccineCvxCode(vaccineCvxCode);
          vaccination.setAdministeredDate(administrationDate);
          vaccinationReported.setUpdatedDate(new Date());
          vaccinationReported.setAdministeredDate(administrationDate);
          vaccinationReported.setVaccineCvxCode(vaccineCvxCode);
          vaccinationReported.setVaccineNdcCode(vaccineNdcCode);
          vaccinationReported.setAdministeredAmount(reader.getValue(6));
          vaccinationReported.setInformationSource(reader.getValue(9));
          vaccinationReported.setLotnumber(reader.getValue(15));
          vaccinationReported.setExpirationDate(
              parseDateWarn(reader.getValue(16), "Invalid vaccination expiration date", "RXA",
                  rxaCount, 16, strictDate, processingExceptionList));
          vaccinationReported.setVaccineMvxCode(reader.getValue(17));
          vaccinationReported.setRefusalReasonCode(reader.getValue(18));
          vaccinationReported.setCompletionStatus(reader.getValue(20));
          if (!vaccinationReported.getRefusalReasonCode().equals("")) {
            Code refusalCode = codeMap.getCodeForCodeset(CodesetType.VACCINATION_REFUSAL,
                vaccinationReported.getRefusalReasonCode());
            if (refusalCode == null) {
              ProcessingException pe =
                  new ProcessingException("Unrecognized refusal reason", "RXA", rxaCount, 18);
              pe.setWarning();
              processingExceptionList.add(pe);
            }
          }
          vaccinationReported.setActionCode(reader.getValue(21));
          int segmentPosition = reader.getSegmentPosition();
          if (reader.advanceToSegment("RXR", "ORC")) {
            vaccinationReported.setBodyRoute(reader.getValue(1));
            vaccinationReported.setBodySite(reader.getValue(2));
          } else if (processingFlavorSet.contains(ProcessingFlavor.SPRUCE)) {
            if (vaccinationReported.getInformationSource().equals("00")) {
              throw new ProcessingException("RXR segment is required for administered vaccinations",
                  "RXA", rxaCount, 0);
            }
          }
          if (vaccinationReported.getAdministeredDate()
              .before(patientReported.getPatientBirthDate())
              && !processingFlavorSet.contains(ProcessingFlavor.CLEMENTINE)) {
            throw new ProcessingException(
                "Vaccination is reported as having been administered before the patient was born",
                "RXA", rxaCount, 3);
          }
          if (!vaccinationReported.getVaccineCvxCode().equals("998")
              && !vaccinationReported.getVaccineCvxCode().equals("999")
              && (vaccinationReported.getCompletionStatus().equals("CP")
                  || vaccinationReported.getCompletionStatus().equals("PA")
                  || vaccinationReported.getCompletionStatus().equals(""))) {
            vaccinationCount++;
          }

          if (vaccinationReported.getCompletionStatus().equals("RE")) {
            refusalCount++;
          }



          reader.gotoSegmentPosition(segmentPosition);
          int tempObxCount = obxCount;
          while (reader.advanceToSegment("OBX", "ORC")) {
            tempObxCount++;
            String indicator = reader.getValue(3);
            if (indicator.equals("64994-7")) {
              String fundingEligibility = reader.getValue(5);
              if (!fundingEligibility.equals("")) {
                Code fundingEligibilityCode = codeMap
                    .getCodeForCodeset(CodesetType.FINANCIAL_STATUS_CODE, fundingEligibility);
                if (fundingEligibilityCode == null) {
                  ProcessingException pe = new ProcessingException(
                      "Funding eligibility '" + fundingEligibility + "' was not recognized", "OBX",
                      tempObxCount, 5).setWarning();
                  processingExceptionList.add(pe);
                } else {
                  vaccinationReported.setFundingEligibility(fundingEligibilityCode.getValue());
                }
              }
            } else if (indicator.equals("30963-3")) {
              String fundingSource = reader.getValue(5);
              if (!fundingSource.equals("")) {
                Code fundingSourceCode = codeMap
                    .getCodeForCodeset(CodesetType.VACCINATION_FUNDING_SOURCE, fundingSource);
                if (fundingSourceCode == null) {
                  ProcessingException pe = new ProcessingException(
                      "Funding source '" + fundingSource + "' was not recognized", "OBX",
                      tempObxCount, 5).setWarning();
                  processingExceptionList.add(pe);
                } else {
                  vaccinationReported.setFundingSource(fundingSourceCode.getValue());
                }
              }
            }
          }

          verifyNoErrors(processingExceptionList);
          reader.gotoSegmentPosition(segmentPosition);
          {
            Transaction transaction = dataSession.beginTransaction();
            dataSession.saveOrUpdate(vaccination);
            dataSession.saveOrUpdate(vaccinationReported);
            vaccination.setVaccinationReported(vaccinationReported);
            dataSession.saveOrUpdate(vaccination);
            transaction.commit();
          }

          reader.gotoSegmentPosition(segmentPosition);
          obxCount = readAndCreateObservations(reader, processingExceptionList, patientReported,
              strictDate, obxCount, vaccinationReported, vaccination);
        } else {
          throw new ProcessingException("RXA segment was not found after ORC segment", "ORC",
              orcCount, 0);
        }
      }
      if (processingFlavorSet.contains(ProcessingFlavor.CRANBERRY) && vaccinationCount == 0) {
        throw new ProcessingException(
            "Patient vaccination history cannot be accepted without at least one administered or historical vaccination specified",
            "", 0, 0);
      }
      if (processingFlavorSet.contains(ProcessingFlavor.BILBERRY)
          && (vaccinationCount == 0 && refusalCount == 0)) {
        throw new ProcessingException(
            "Patient vaccination history cannot be accepted without at least one administered, historical, or refused vaccination specified",
            "", 0, 0);
      }
      String ack = buildAck(reader, processingExceptionList);
      recordMessageReceived(message, patientReported, ack, "Update", "Ack", orgAccess.getOrg());
      return ack;
    } catch (ProcessingException e) {
      if (!processingExceptionList.contains(e)) {
        processingExceptionList.add(e);
      }
      String ack = buildAck(reader, processingExceptionList);
      recordMessageReceived(message, null, ack, "Update", "Exception", orgAccess.getOrg());
      return ack;
    }

  }

  @SuppressWarnings("unchecked")
public PatientReported processPatient(OrgAccess orgAccess, HL7Reader reader,
      List<ProcessingException> processingExceptionList, Set<ProcessingFlavor> processingFlavorSet,
      CodeMap codeMap, boolean strictDate, PatientReported patientReported)
      throws ProcessingException {
    PatientMaster patient = null;
    String patientReportedExternalLink = "";
    String patientReportedAuthority = "";
    String patientReportedType = "MR";
    if (reader.advanceToSegment("PID")) {
      patientReportedExternalLink = reader.getValueBySearchingRepeats(3, 1, patientReportedType, 5);
      patientReportedAuthority = reader.getValueBySearchingRepeats(3, 4, patientReportedType, 5);
      if (patientReportedExternalLink.equals("")) {
        patientReportedAuthority = "";
        patientReportedType = "PT";
        patientReportedExternalLink =
            reader.getValueBySearchingRepeats(3, 1, patientReportedType, 5);
        patientReportedAuthority = reader.getValueBySearchingRepeats(3, 4, patientReportedType, 5);
        if (patientReportedExternalLink.equals("")) {
          patientReportedAuthority = "";
          patientReportedType = "PI";
          patientReportedExternalLink =
              reader.getValueBySearchingRepeats(3, 1, patientReportedType, 5);
          patientReportedAuthority =
              reader.getValueBySearchingRepeats(3, 4, patientReportedType, 5);
          if (patientReportedExternalLink.equals("")) {
            throw new ProcessingException(
                "MRN was not found, required for accepting vaccination report", "PID", 1, 3);
          }
        }
      }
    } else {
      throw new ProcessingException(
          "No PID segment found, required for accepting vaccination report", "", 0, 0);
    }


    {
      Query query = dataSession.createQuery(
          "from PatientReported where orgReported = ? and patientReportedExternalLink = ?");
      query.setParameter(0, orgAccess.getOrg());
      query.setParameter(1, patientReportedExternalLink);
      List<PatientReported> patientReportedList = query.list();
      if (patientReportedList.size() > 0) {
        patientReported = patientReportedList.get(0);
        patient = patientReported.getPatient();
      }
    }

    if (patientReported == null) {
      patient = new PatientMaster();
      patient.setPatientExternalLink(generatePatientExternalLink());
      patient.setOrgMaster(orgAccess.getOrg());
      patientReported = new PatientReported();
      patientReported.setOrgReported(orgAccess.getOrg());
      patientReported.setPatientReportedExternalLink(patientReportedExternalLink);
      patientReported.setPatient(patient);
      patientReported.setReportedDate(new Date());
    }

    {
      String patientNameLast = reader.getValue(5, 1);
      String patientNameFirst = reader.getValue(5, 2);
      String patientNameMiddle = reader.getValue(5, 3);
      String patientPhone = reader.getValue(13, 6) + reader.getValue(13, 7);
      String telUseCode = reader.getValue(13, 2);
      if (patientPhone.length() >= 0) {
        if (!telUseCode.equals("PRN")) {
          ProcessingException pe = new ProcessingException(
              "Patient phone telecommunication type must be PRN ", "PID", 1, 13);
          if (!processingFlavorSet.contains(ProcessingFlavor.QUINZE)) {
            pe.setWarning();
          }
          processingExceptionList.add(pe);
        }

        {
          int countNums = 0;
          boolean invalidCharFound = false;
          char invalidChar = ' ';
          for (char c : patientPhone.toCharArray()) {
            if (c >= '0' && c <= '9') {
              countNums++;
            } else if (c != '-' && c != '.' && c != ' ' && c != '(' && c != ')') {
              if (!invalidCharFound) {
                invalidCharFound = true;
                invalidChar = c;
              }
            }
          }
          if (invalidCharFound) {
            ProcessingException pe = new ProcessingException(
                "Patient phone number has unexpected character: " + invalidChar, "PID", 1, 13);
            pe.setWarning();
            processingExceptionList.add(pe);
          }
          if (countNums != 10 || patientPhone.startsWith("555") || patientPhone.startsWith("0")
              || patientPhone.startsWith("1")) {
            ProcessingException pe = new ProcessingException(
                "Patient phone number does not appear to be valid", "PID", 1, 13);
            pe.setWarning();
            processingExceptionList.add(pe);
          }
        }
      }
      if (!telUseCode.equals("PRN")) {
        patientPhone = "";
      }

      if (patientNameLast.equals("")) {
        throw new ProcessingException(
            "Patient last name was not found, required for accepting patient and vaccination history",
            "PID", 1, 5);
      }
      if (patientNameFirst.equals("")) {
        throw new ProcessingException(
            "Patient first name was not found, required for accepting patient and vaccination history",
            "PID", 1, 5);
      }


      String zip = reader.getValue(11, 5);
      if (zip.length() > 5) {
        zip = zip.substring(0, 5);
      }
      String addressFragPrep = reader.getValue(11, 1);
      String addressFrag = "";
      {
        int spaceIndex = addressFragPrep.indexOf(" ");
        if (spaceIndex > 0) {
          addressFragPrep = addressFragPrep.substring(0, spaceIndex);
        }
        addressFrag = zip + ":" + addressFragPrep;
      }
      Date patientBirthDate;
      patientBirthDate = parseDateError(reader.getValue(7), "Bad format for date of birth", "PID",
          1, 7, strictDate);
      if (patientBirthDate.after(new Date())) {
        throw new ProcessingException(
            "Patient is indicated as being born in the future, unable to record patients who are not yet born",
            "PID", 1, 7);
      }
      patient.setPatientAddressFrag(addressFrag);
      patient.setPatientNameLast(patientNameLast);
      patient.setPatientNameFirst(patientNameFirst);
      patient.setPatientNameMiddle(patientNameMiddle);
      patient.setPatientPhoneFrag(patientPhone);
      patient.setPatientBirthDate(patientBirthDate);
      patient.setPatientSoundexFirst(""); // TODO, later
      patient.setPatientSoundexLast(""); // TODO, later
      patientReported.setPatientReportedExternalLink(patientReportedExternalLink);
      patientReported.setPatientReportedType(patientReportedType);
      patientReported.setPatientNameFirst(patientNameFirst);
      patientReported.setPatientNameLast(patientNameLast);
      patientReported.setPatientNameMiddle(patientNameMiddle);
      patientReported.setPatientMotherMaiden(reader.getValue(6));
      patientReported.setPatientBirthDate(patientBirthDate);
      patientReported.setPatientSex(reader.getValue(8));
      patientReported.setPatientRace(reader.getValue(10));
      patientReported.setPatientRace2(reader.getValueRepeat(10, 1, 2));
      patientReported.setPatientRace3(reader.getValueRepeat(10, 1, 3));
      patientReported.setPatientRace4(reader.getValueRepeat(10, 1, 4));
      patientReported.setPatientRace5(reader.getValueRepeat(10, 1, 5));
      patientReported.setPatientRace6(reader.getValueRepeat(10, 1, 6));
      patientReported.setPatientAddressLine1(reader.getValue(11, 1));
      patientReported.setPatientAddressLine2(reader.getValue(11, 2));
      patientReported.setPatientAddressCity(reader.getValue(11, 3));
      patientReported.setPatientAddressState(reader.getValue(11, 4));
      patientReported.setPatientAddressZip(reader.getValue(11, 5));
      patientReported.setPatientAddressCountry(reader.getValue(11, 6));
      patientReported.setPatientAddressCountyParish(reader.getValue(11, 9));
      patientReported.setPatientEthnicity(reader.getValue(22));
      patientReported.setPatientBirthFlag(reader.getValue(24));
      patientReported.setPatientBirthOrder(reader.getValue(25));
      patientReported.setPatientDeathDate(parseDateWarn(reader.getValue(29),
          "Invalid patient death date", "PID", 1, 29, strictDate, processingExceptionList));
      patientReported.setPatientDeathFlag(reader.getValue(30));
      patientReported.setPatientEmail(reader.getValueBySearchingRepeats(13, 4, "NET", 2));
      patientReported.setPatientPhone(patientPhone);
      patientReported.setPatientReportedAuthority(patientReportedAuthority);

      {
        String patientSex = patientReported.getPatientSex();
        if (!ValidValues.verifyValidValue(patientSex, ValidValues.SEX)) {
          ProcessingException pe =
              new ProcessingException("Patient sex '" + patientSex + "' is not recognized", "PID",
                  1, 8).setWarning();
          if (processingFlavorSet.contains(ProcessingFlavor.ELDERBERRIES)) {
            pe.setWarning();
          }
          processingExceptionList.add(pe);
        }
      }

      String patientAddressCountry = patientReported.getPatientAddressCountry();
      if (!patientAddressCountry.equals("")) {
        if (!ValidValues.verifyValidValue(patientAddressCountry, ValidValues.COUNTRY_2DIGIT)
            && !ValidValues.verifyValidValue(patientAddressCountry, ValidValues.COUNTRY_3DIGIT)) {
          ProcessingException pe = new ProcessingException("Patient address country '"
              + patientAddressCountry + "' is not recognized and cannot be accepted", "PID", 1, 11);
          if (processingFlavorSet.contains(ProcessingFlavor.GUAVA)) {
            pe.setWarning();
          }
          processingExceptionList.add(pe);
        }
      }
      if (patientAddressCountry.equals("") || patientAddressCountry.equals("US")
          || patientAddressCountry.equals("USA")) {
        String patientAddressState = patientReported.getPatientAddressState();
        if (!patientAddressState.equals("")) {
          if (!ValidValues.verifyValidValue(patientAddressState, ValidValues.STATE)) {
            ProcessingException pe = new ProcessingException("Patient address state '"
                + patientAddressState + "' is not recognized and cannot be accepted", "PID", 1, 11);
            if (processingFlavorSet.contains(ProcessingFlavor.GUAVA)) {
              pe.setWarning();
            }
            processingExceptionList.add(pe);
          }
        }
      }


      {
        String race = patientReported.getPatientRace();
        if (!race.equals("")) {
          Code raceCode = codeMap.getCodeForCodeset(CodesetType.PATIENT_RACE, race);
          if (raceCode == null
              || CodeStatusValue.getBy(raceCode.getCodeStatus()) != CodeStatusValue.VALID) {
            ProcessingException pe = new ProcessingException(
                "Invalid race '" + race + "', message cannot be accepted", "PID", 1, 10);
            if (!processingFlavorSet.contains(ProcessingFlavor.FIG)) {
              pe.setWarning();
            }
            processingExceptionList.add(pe);
          }
        }
      }

      {
        String ethnicity = patientReported.getPatientEthnicity();
        if (!ethnicity.equals("")) {
          Code ethnicityCode = codeMap.getCodeForCodeset(CodesetType.PATIENT_ETHNICITY, ethnicity);
          if (ethnicityCode == null
              || CodeStatusValue.getBy(ethnicityCode.getCodeStatus()) != CodeStatusValue.VALID) {
            ProcessingException pe = new ProcessingException(
                "Invalid ethnicity '" + ethnicity + "', message cannot be accepted", "PID", 1, 10);
            if (!processingFlavorSet.contains(ProcessingFlavor.FIG)) {
              pe.setWarning();
            }
            processingExceptionList.add(pe);
          }
        }
      }

      if (processingFlavorSet.contains(ProcessingFlavor.BLACKBERRY)) {
        if (patientReported.getPatientAddressLine1().equals("")
            || patientReported.getPatientAddressCity().equals("")
            || patientReported.getPatientAddressState().equals("")
            || patientReported.getPatientAddressZip().equals("")) {
          throw new ProcessingException("Patient address is required but it was not sent", "PID", 1,
              11);
        }
      }

      {
        String birthFlag = patientReported.getPatientBirthFlag();
        String birthOrder = patientReported.getPatientBirthOrder();
        if (!birthFlag.equals("") || !birthOrder.equals("")) {
          if (birthFlag.equals("") || birthFlag.equals("N")) {
            // The only acceptable value here is now blank or 1
            if (!birthOrder.equals("1") && !birthOrder.equals("")) {
              ProcessingException pe = new ProcessingException("Birth order was specified as "
                  + birthOrder + " but not indicated as multiple birth", "PID", 1, 25);
              if (processingFlavorSet.contains(ProcessingFlavor.PLANTAIN)) {
                pe.setWarning();
              }
              processingExceptionList.add(pe);
            }
          } else if (birthFlag.equals("Y")) {
            if (birthOrder.equals("")) {
              ProcessingException pe = new ProcessingException(
                  "Multiple birth but birth order was not specified", "PID", 1, 24);
              pe.setWarning();
              processingExceptionList.add(pe);
            } else if (!ValidValues.verifyValidValue(birthOrder, ValidValues.BIRTH_ORDER)) {
              ProcessingException pe =
                  new ProcessingException("Birth order was specified as " + birthOrder
                      + " but not an expected value, must be between 1 and 9", "PID", 1, 25);
              if (processingFlavorSet.contains(ProcessingFlavor.PLANTAIN)) {
                pe.setWarning();
              }
              processingExceptionList.add(pe);
            }
          } else {
            ProcessingException pe = new ProcessingException(
                "Multiple birth indicator " + birthFlag + " is not recognized", "PID", 1, 24);
            if (processingFlavorSet.contains(ProcessingFlavor.PLANTAIN)) {
              pe.setWarning();
            }
            processingExceptionList.add(pe);
          }
        }
      }

    }
    if (reader.advanceToSegment("PD1")) {
      patientReported.setPublicityIndicator(reader.getValue(11));
      patientReported.setProtectionIndicator(reader.getValue(12));
      patientReported.setProtectionIndicatorDate(parseDateWarn(reader.getValue(13),
          "Invalid protection indicator date", "PD1", 1, 13, strictDate, processingExceptionList));
      patientReported.setRegistryStatusIndicator(reader.getValue(16));
      patientReported.setRegistryStatusIndicatorDate(
          parseDateWarn(reader.getValue(17), "Invalid registry status indicator date", "PD1", 1, 17,
              strictDate, processingExceptionList));
      patientReported.setPublicityIndicatorDate(parseDateWarn(reader.getValue(18),
          "Invalid publicity indicator date", "PD1", 1, 18, strictDate, processingExceptionList));
    }
    reader.resetPostion();
    {
      int repeatCount = 0;
      while (reader.advanceToSegment("NK1")) {
        patientReported.setGuardianLast(reader.getValue(2, 1));
        patientReported.setGuardianFirst(reader.getValue(2, 2));
        patientReported.setGuardianMiddle(reader.getValue(2, 1));
        String guardianRelationship = reader.getValue(3);
        patientReported.setGuardianRelationship(guardianRelationship);
        repeatCount++;
        if (patientReported.getGuardianLast().equals("")) {
          ProcessingException pe =
              new ProcessingException("Next-of-kin last name is empty", "NK1", repeatCount, 2)
                  .setWarning();
          processingExceptionList.add(pe);
        }
        if (patientReported.getGuardianFirst().equals("")) {
          ProcessingException pe =
              new ProcessingException("Next-of-kin first name is empty", "NK1", repeatCount, 2)
                  .setWarning();
          processingExceptionList.add(pe);
        }
        if (guardianRelationship.equals("")) {
          ProcessingException pe =
new ProcessingException("Next-of-kin relationship is empty", "NK1", repeatCount, 3)
                  .setWarning();
          processingExceptionList.add(pe);
        }
        if (guardianRelationship.equals("MTH") || guardianRelationship.equals("FTH")
            || guardianRelationship.equals("GRD")) {
          break;
        } else {
          ProcessingException pe = new ProcessingException((guardianRelationship.equals("")
              ? "Next-of-kin relationship not specified so is not recognized as guardian and will be ignored"
              : ("Next-of-kin relationship '" + guardianRelationship
                  + "' is not a recognized guardian and will be ignored")),
              "NK1", repeatCount, 3).setWarning();
          processingExceptionList.add(pe);
        }
      }
    }
    reader.resetPostion();

    verifyNoErrors(processingExceptionList);

    patientReported.setUpdatedDate(new Date());
    {
      Transaction transaction = dataSession.beginTransaction();
      dataSession.saveOrUpdate(patient);
      dataSession.saveOrUpdate(patientReported);
      transaction.commit();
    }
    return patientReported;
  }

  public String processORU(OrgAccess orgAccess, HL7Reader reader, String message) {
    List<ProcessingException> processingExceptionList = new ArrayList<>();
    try {
      Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();

      CodeMap codeMap = CodeMapManager.getCodeMap();

      boolean strictDate = true;
      if (processingFlavorSet.contains(ProcessingFlavor.CANTALOUPE)) {
        strictDate = false;
      }
      PatientReported patientReported = null;

      patientReported = processPatient(orgAccess, reader, processingExceptionList,
          processingFlavorSet, codeMap, strictDate, patientReported);

      int orcCount = 0;
      int obxCount = 0;
      while (reader.advanceToSegment("ORC")) {
        orcCount++;
        if (reader.advanceToSegment("OBR", "ORC")) {
          obxCount = readAndCreateObservations(reader, processingExceptionList, patientReported,
              strictDate, obxCount, null, null);
        } else {
          throw new ProcessingException("OBR segment was not found after ORC segment", "ORC",
              orcCount, 0);
        }
      }
      String ack = buildAck(reader, processingExceptionList);
      recordMessageReceived(message, patientReported, ack, "Update", "Ack", orgAccess.getOrg());
      return ack;
    } catch (ProcessingException e) {
      if (!processingExceptionList.contains(e)) {
        processingExceptionList.add(e);
      }
      String ack = buildAck(reader, processingExceptionList);
      recordMessageReceived(message, null, ack, "Update", "Exception", orgAccess.getOrg());
      return ack;
    }
  }

  public int readAndCreateObservations(HL7Reader reader,
      List<ProcessingException> processingExceptionList, PatientReported patientReported,
      boolean strictDate, int obxCount, VaccinationReported vaccinationReported,
      VaccinationMaster vaccination) {
    while (reader.advanceToSegment("OBX", "ORC")) {
      obxCount++;
      String identifierCode = reader.getValue(3);
      String valueCode = reader.getValue(5);
      ObservationMaster observation =
          readObservations(reader, processingExceptionList, patientReported, strictDate, obxCount,
              vaccinationReported, vaccination, identifierCode, valueCode);
      if (observation.getIdentifierCode().equals("30945-0")) // contraindication!
      {
        CodeMap codeMap = CodeMapManager.getCodeMap();
        Code contraCode = codeMap.getCodeForCodeset(CodesetType.CONTRAINDICATION_OR_PRECAUTION,
            observation.getValueCode());
        if (contraCode == null) {
          ProcessingException pe = new ProcessingException(
              "Unrecognized contraindication or precaution", "OBX", obxCount, 5);
          pe.setWarning();
          processingExceptionList.add(pe);
        }
        if (observation.getObservationReported().getObservationDate() != null) {
          Date today = new Date();
          if (observation.getObservationReported().getObservationDate().after(today)) {
            ProcessingException pe = new ProcessingException(
                "Contraindication or precaution observed in the future", "OBX", obxCount, 5);
            pe.setWarning();
            processingExceptionList.add(pe);
          }
          if (patientReported.getPatientBirthDate() != null && observation.getObservationReported()
              .getObservationDate().before(patientReported.getPatientBirthDate())) {
            ProcessingException pe = new ProcessingException(
                "Contraindication or precaution observed before patient was born", "OBX", obxCount,
                14);
            pe.setWarning();
            processingExceptionList.add(pe);
          }
        }
      }
      {
        ObservationReported observationReported = observation.getObservationReported();
        observation.setObservationReported(null);
        Transaction transaction = dataSession.beginTransaction();
        dataSession.saveOrUpdate(observation);
        dataSession.saveOrUpdate(observationReported);
        observation.setObservationReported(observationReported);
        dataSession.saveOrUpdate(observation);
        transaction.commit();
      }
    }
    return obxCount;
  }

  @SuppressWarnings("unchecked")
public ObservationMaster readObservations(HL7Reader reader,
      List<ProcessingException> processingExceptionList, PatientReported patientReported,
      boolean strictDate, int obxCount, VaccinationReported vaccinationReported,
      VaccinationMaster vaccination, String identifierCode, String valueCode) {
    ObservationMaster observation;
    String q;
    if (vaccination == null) {
      q = "from ObservationMaster where " + "patient = :patient and vaccination is null "
          + "and identifierCode = :identifierCode";
    } else {
      q = "from ObservationMaster where " + "patient = :patient and vaccination = :vaccination "
          + "and identifierCode = :identifierCode";
    }
    Query query = dataSession.createQuery(q);
    query.setParameter("patient", patientReported.getPatient());
    if (vaccination != null) {
      query.setParameter("vaccination", vaccination);
    }
    query.setParameter("identifierCode", identifierCode);
    List<ObservationMaster> l = query.list();
    ObservationReported observationReported;
    if (l.size() > 0) {
      observation = l.get(0);
      observationReported = observation.getObservationReported();
    } else {
      observation = new ObservationMaster();
      observation.setPatient(patientReported.getPatient());
      observation.setVaccination(vaccination);
      observation.setIdentifierCode(identifierCode);
      observationReported = new ObservationReported();
      observation.setObservationReported(observationReported);
      observationReported.setReportedDate(new Date());
    }
    observation.setValueCode(valueCode);
    observationReported.setPatientReported(patientReported);
    observationReported.setVaccinationReported(vaccinationReported);
    observationReported.setObservation(observation);
    observationReported.setUpdatedDate(new Date());
    observationReported.setIdentifierCode(identifierCode);
    observationReported.setValueType(reader.getValue(2));
    observationReported.setIdentifierLabel(reader.getValue(3, 2));
    observationReported.setIdentifierTable(reader.getValue(3, 3));
    observationReported.setValueCode(valueCode);
    observationReported.setValueLabel(reader.getValue(5, 2));
    observationReported.setValueTable(reader.getValue(5, 3));
    observationReported.setUnitsCode(reader.getValue(6, 1));
    observationReported.setUnitsLabel(reader.getValue(6, 2));
    observationReported.setUnitsTable(reader.getValue(6, 3));
    observationReported.setResultStatus(reader.getValue(11));
    observationReported.setObservationDate(
        parseDateWarn(reader.getValue(14), "Unparsable date/time of observation", "OBX", obxCount,
            14, strictDate, processingExceptionList));
    observationReported.setMethodCode(reader.getValue(17, 1));
    observationReported.setMethodLabel(reader.getValue(17, 2));
    observationReported.setMethodTable(reader.getValue(17, 3));
    return observation;
  }

  public void verifyNoErrors(List<ProcessingException> processingExceptionList)
      throws ProcessingException {
    for (ProcessingException pe : processingExceptionList) {
      if (pe.isError()) {
        throw pe;
      }
    }
  }

  public boolean hasErrors(List<ProcessingException> processingExceptionList) {
    for (ProcessingException pe : processingExceptionList) {
      if (pe.isError()) {
        return true;
      }
    }
    return false;
  }

  public void recordMessageReceived(String message, PatientReported patientReported,
      String messageResponse, String categoryRequest, String categoryResponse,
      OrgMaster orgMaster) {
    MessageReceived messageReceived = new MessageReceived();
    messageReceived.setOrgMaster(orgMaster);
    messageReceived.setMessageRequest(message);
    messageReceived.setPatientReported(patientReported);
    messageReceived.setMessageResponse(messageResponse);
    messageReceived.setReportedDate(new Date());
    messageReceived.setCategoryRequest(categoryRequest);
    messageReceived.setCategoryResponse(categoryResponse);
    Transaction transaction = dataSession.beginTransaction();
    dataSession.save(messageReceived);
    transaction.commit();
  }

  @SuppressWarnings("unchecked")
  public String buildRSP(HL7Reader reader, String messageRecieved, PatientReported patientReported,
      OrgAccess orgAccess, List<PatientReported> patientReportedPossibleList,
      List<ProcessingException> processingExceptionList) {
    reader.resetPostion();
    reader.advanceToSegment("MSH");

    Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();
    StringBuilder sb = new StringBuilder();
    String profileIdSubmitted = reader.getValue(21);
    CodeMap codeMap = CodeMapManager.getCodeMap();
    String categoryResponse = "No Match";
    String profileId = RSP_Z33_NO_MATCH;
    boolean sendBackForecast = true;
    if (processingFlavorSet.contains(ProcessingFlavor.COCONUT)) {
      sendBackForecast = false;
    } else if (processingFlavorSet.contains(ProcessingFlavor.ORANGE)) {
      sendBackForecast = false;
    }

    String queryId = "";
    int maxCount = 20;
    if (reader.advanceToSegment("QPD")) {
      queryId = reader.getValue(2);
      if (reader.advanceToSegment("RCP")) {
        String s = reader.getValue(2);
        try {
          int i = Integer.parseInt(s);
          if (i < maxCount) {
            maxCount = i;
          }
        } catch (NumberFormatException nfe) {
          // ignore
        }
      }
    }
    String queryResponse = QUERY_OK;
    {
      String messageType = "RSP^K11^RSP_K11";
      if (patientReported == null) {
        queryResponse = QUERY_NOT_FOUND;
        profileId = RSP_Z33_NO_MATCH;
        categoryResponse = "No Match";
        if (patientReportedPossibleList.size() > 0) {
          if (profileIdSubmitted.equals(QBP_Z34)) {
            if (patientReportedPossibleList.size() > maxCount) {
              queryResponse = QUERY_TOO_MANY;
              profileId = RSP_Z33_NO_MATCH;
              categoryResponse = "Too Many Matches";
            } else {
              queryResponse = QUERY_OK;
              profileId = RSP_Z31_MULTIPLE_MATCH;
              categoryResponse = "Possible Match";
            }
          } else if (profileIdSubmitted.equals("Z44")) {
            queryResponse = QUERY_NOT_FOUND;
            profileId = RSP_Z33_NO_MATCH;
            categoryResponse = "No Match";
          }
        }
        if (hasErrors(processingExceptionList)) {
          queryResponse = QUERY_APPLICATION_ERROR;
        }
      } else if (profileIdSubmitted.equals(QBP_Z34)) {
        profileId = RSP_Z32_MATCH;
        categoryResponse = "Match";
      } else if (profileIdSubmitted.equals(QBP_Z44)) {
        if (processingFlavorSet.contains(ProcessingFlavor.ORANGE)) {
          profileId = RSP_Z32_MATCH;
          categoryResponse = "Match";
        } else {
          sendBackForecast = true;
          profileId = RSP_Z42_MATCH_WITH_FORECAST;
          categoryResponse = "Match";
        }
      } else {
        processingExceptionList.add(new ProcessingException(
            "Unrecognized profile id '" + profileIdSubmitted + "'", "MSH", 1, 21));
      }
      createMSH(messageType, profileId, reader, sb, processingFlavorSet);
    }
    {
      String sendersUniqueId = reader.getValue(10);
      if (hasErrors(processingExceptionList)) {
        sb.append("MSA|AE|" + sendersUniqueId + "\r");
      } else {
        sb.append("MSA|AA|" + sendersUniqueId + "\r");
      }
      if (processingExceptionList.size() > 0) {
        printERRSegment(processingExceptionList.get(processingExceptionList.size() - 1), sb);
      }
    }
    String profileName = "Request a Complete Immunization History";
    if (profileIdSubmitted.equals("")) {
      profileIdSubmitted = "Z34";
      profileName = "Request a Complete Immunization History";
    } else if (profileIdSubmitted.equals("Z34")) {
      profileName = "Request a Complete Immunization History";
    } else if (profileIdSubmitted.equals("Z44")) {
      profileName = "Request Evaluated Immunization History and Forecast Query";
    }
    {
      sb.append("QAK|" + queryId);
      sb.append("|" + queryResponse);
      sb.append("|");
      sb.append("" + profileIdSubmitted + "^" + profileName + "^CDCPHINVS\r");
    }
    reader.resetPostion();
    if (reader.advanceToSegment("QPD")) {
      sb.append(reader.getOriginalSegment() + "\r");
    } else {
      sb.append("QPD|");
    }
    if (profileId.equals(RSP_Z31_MULTIPLE_MATCH)) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
      int count = 0;
      for (PatientReported pr : patientReportedPossibleList) {
        count++;
        PatientMaster patient = pr.getPatient();
        printQueryPID(pr, processingFlavorSet, sb, patient, sdf, count);
      }
    } else if (profileId.equals(RSP_Z32_MATCH) || profileId.equals(RSP_Z42_MATCH_WITH_FORECAST)) {
      PatientMaster patient = patientReported.getPatient();
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
      printQueryPID(patientReported, processingFlavorSet, sb, patient, sdf, 1);
      if (profileId.equals(RSP_Z32_MATCH)) {
        printQueryNK1(patientReported, sb, codeMap);
      }
      List<VaccinationMaster> vaccinationMasterList =
          getVaccinationMasterList(patient, dataSession);

      if (processingFlavorSet.contains(ProcessingFlavor.LEMON)) {
        for (Iterator<VaccinationMaster> it = vaccinationMasterList.iterator(); it.hasNext();) {
          it.next();
          if (random.nextInt(4) == 0) {
            it.remove();
          }
        }
      }
      if (processingFlavorSet.contains(ProcessingFlavor.GREEN)) {
        for (Iterator<VaccinationMaster> it = vaccinationMasterList.iterator(); it.hasNext();) {
          VaccinationMaster vaccinationMaster = it.next();
          if (vaccinationMaster.getVaccineCvxCode().equals("91")) {
            it.remove();
          }
        }
      }
      List<ForecastActual> forecastActualList = null;
      if (sendBackForecast) {
        forecastActualList = doForecast(patient, patientReported, codeMap, vaccinationMasterList, orgAccess);
      }
      int obxSetId = 0;
      int obsSubId = 0;
      for (VaccinationMaster vaccination : vaccinationMasterList) {
        Code cvxCode = codeMap.getCodeForCodeset(CodesetType.VACCINATION_CVX_CODE,
            vaccination.getVaccineCvxCode());
        if (cvxCode == null) {
          continue;
        }
        VaccinationReported vaccinationReported = vaccination.getVaccinationReported();
        boolean originalReporter =
            vaccinationReported.getPatientReported().getOrgReported().equals(orgAccess.getOrg());
        if ("D".equals(vaccinationReported.getActionCode())) {
          continue;
        }
        printORC(orgAccess, sb, vaccination, vaccinationReported, originalReporter);
        sb.append("RXA");
        // RXA-1
        sb.append("|0");
        // RXA-2
        sb.append("|1");
        // RXA-3
        sb.append("|" + sdf.format(vaccination.getAdministeredDate()));
        // RXA-4
        sb.append("|");
        // RXA-5
        sb.append("|" + cvxCode.getValue() + "^" + cvxCode.getLabel() + "^CVX");
        if (!vaccinationReported.getVaccineNdcCode().equals("")) {
          Code ndcCode = codeMap.getCodeForCodeset(CodesetType.VACCINATION_NDC_CODE,
              vaccinationReported.getVaccineNdcCode());
          if (ndcCode != null) {
            sb.append("~" + ndcCode.getValue() + "^" + ndcCode.getLabel() + "^NDC");
          }
        }
        {
          // RXA-6
          sb.append("|");
          double adminAmount = 0.0;
          if (!vaccinationReported.getAdministeredAmount().equals("")) {
            try {
              adminAmount = Double.parseDouble(vaccinationReported.getAdministeredAmount());
            } catch (NumberFormatException nfe) {
              adminAmount = 0.0;
            }
          }
          if (adminAmount > 0) {
            sb.append(adminAmount);
          }
          // RXA-7
          sb.append("|");
          if (adminAmount > 0) {
            sb.append("mL^milliliters^UCUM");
          }
        }
        // RXA-8
        sb.append("|");
        // RXA-9
        sb.append("|");
        {
          Code informationCode = null;
          if (vaccinationReported.getInformationSource() != null) {
            informationCode = codeMap.getCodeForCodeset(CodesetType.VACCINATION_INFORMATION_SOURCE,
                vaccinationReported.getInformationSource());
          }
          if (informationCode != null) {
            sb.append(informationCode.getValue() + "^" + informationCode.getLabel() + "^NIP001");
          }
        }
        // RXA-10
        sb.append("|");
        // RXA-11
        sb.append("|");
        // RXA-12
        sb.append("|");
        // RXA-13
        sb.append("|");
        // RXA-14
        sb.append("|");
        // RXA-15
        sb.append("|");
        if (vaccinationReported.getLotnumber() != null) {
          sb.append(vaccinationReported.getLotnumber());
        }
        // RXA-16
        sb.append("|");
        if (vaccinationReported.getExpirationDate() != null) {
          sb.append(sdf.format(vaccinationReported.getExpirationDate()));
        }
        // RXA-17
        sb.append("|");
        sb.append(printCode(vaccinationReported.getVaccineMvxCode(),
            CodesetType.VACCINATION_MANUFACTURER_CODE, "MVX", codeMap));
        // RXA-18
        sb.append("|");
        sb.append(printCode(vaccinationReported.getRefusalReasonCode(),
            CodesetType.VACCINATION_REFUSAL, "NIP002", codeMap));
        // RXA-19
        sb.append("|");
        // RXA-20
        sb.append("|");
        if (!processingFlavorSet.contains(ProcessingFlavor.LIME)) {
          String completionStatus = vaccinationReported.getCompletionStatus();
          if (completionStatus == null || completionStatus.equals("")) {
            completionStatus = "CP";
          }
          sb.append(printCode(completionStatus, CodesetType.VACCINATION_COMPLETION, null, codeMap));
        }

        // RXA-21
        sb.append("|A");
        sb.append("\r");
        if (vaccinationReported.getBodyRoute() != null
            && !vaccinationReported.getBodyRoute().equals("")) {
          sb.append("RXR");
          // RXR-1
          sb.append("|");
          sb.append(printCode(vaccinationReported.getBodyRoute(), CodesetType.BODY_ROUTE, "NCIT",
              codeMap));
          // RXR-2
          sb.append("|");
          sb.append(printCode(vaccinationReported.getBodySite(), CodesetType.BODY_SITE, "HL70163",
              codeMap));
          sb.append("\r");
        }
        TestEvent testEvent = vaccinationReported.getTestEvent();
        if (testEvent != null && testEvent.getEvaluationActualList() != null) {
          for (EvaluationActual evaluationActual : testEvent.getEvaluationActualList()) {
            obsSubId++;
            {
              obxSetId++;
              String loinc = "30956-7";
              String loincLabel = "Vaccine type";
              String value = evaluationActual.getVaccineCvx();
              String valueLabel = evaluationActual.getVaccineCvx();
              String valueTable = "CVX";
              printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value, valueLabel, valueTable);
            }
            {
              obxSetId++;
              String loinc = "59781-5";
              String loincLabel = "Dose validity";
              String value = evaluationActual.getDoseValid();
              String valueLabel = value;
              String valueTable = "99107";
              printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value, valueLabel, valueTable);
            }
          }
        }
        {
          Query query = dataSession.createQuery(
              "from ObservationMaster where patient = :patient and vaccination = :vaccination");
          query.setParameter("patient", patient);
          query.setParameter("vaccination", vaccination);
          List<ObservationMaster> observationList = query.list();
          if (observationList.size() > 0) {
            obsSubId++;
            for (ObservationMaster observation : observationList) {
              obxSetId++;
              printObx(sb, obxSetId, obsSubId, observation);
            }
          }
        }
      }
      {
        Query query = dataSession
            .createQuery("from ObservationMaster where patient = :patient and vaccination is null");
        query.setParameter("patient", patient);
        List<ObservationMaster> observationList = query.list();
        if (observationList.size() > 0) {
          printORC(orgAccess, sb, null, null, false);
          obsSubId++;
          for (ObservationMaster observation : observationList) {
            obxSetId++;
            printObx(sb, obxSetId, obsSubId, observation);
          }
        }
      }

      if (sendBackForecast && forecastActualList != null && forecastActualList.size() > 0) {
        printORC(orgAccess, sb, null, null, false);
        sb.append("RXA");
        // RXA-1
        sb.append("|0");
        // RXA-2
        sb.append("|1");
        // RXA-3
        sb.append("|" + sdf.format(new Date()));
        // RXA-4
        sb.append("|");
        // RXA-5
        sb.append("|998^No Vaccination Administered^CVX");
        // RXA-6
        sb.append("|999");
        // RXA-7
        sb.append("|");
        // RXA-8
        sb.append("|");
        // RXA-9
        sb.append("|");
        // RXA-10
        sb.append("|");
        // RXA-11
        sb.append("|");
        // RXA-12
        sb.append("|");
        // RXA-13
        sb.append("|");
        // RXA-14
        sb.append("|");
        // RXA-15
        sb.append("|");
        // RXA-16
        sb.append("|");
        // RXA-17
        sb.append("|");
        // RXA-18
        sb.append("|");
        // RXA-19
        sb.append("|");
        // RXA-20
        sb.append("|NA");
        sb.append("\r");
        for (ForecastActual forecastActual : forecastActualList) {
          obsSubId++;
          {
            obxSetId++;
            String loinc = "30956-7";
            String loincLabel = "Vaccine type";
            String value = forecastActual.getVaccineGroup().getVaccineCvx();
            String valueLabel = forecastActual.getVaccineGroup().getLabel();
            String valueTable = "CVX";
            printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value, valueLabel, valueTable);
          }
          {
            obxSetId++;
            String loinc = "59783-1";
            String loincLabel = "Status in series";
            Admin admin = forecastActual.getAdmin();
            String value = admin.getAdminStatus();
            String valueLabel = admin.getLabel();
            String valueTable = "99106";
            printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value, valueLabel, valueTable);
          }
          if (forecastActual.getDueDate() != null) {
            obxSetId++;
            String loinc = "30981-5";
            String loincLabel = "Earliest date";
            Date value = forecastActual.getValidDate();
            printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value);
          }
          if (forecastActual.getDueDate() != null) {
            obxSetId++;
            String loinc = "30980-7";
            String loincLabel = "Recommended date";
            Date value = forecastActual.getDueDate();
            printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value);
          }
          if (forecastActual.getDueDate() != null) {
            obxSetId++;
            String loinc = "59778-1";
            String loincLabel = "Latest date";
            Date value = forecastActual.getOverdueDate();
            printObx(sb, obxSetId, obsSubId, loinc, loincLabel, value);
          }
        }
      }
    }

    String messageResponse = sb.toString();
    recordMessageReceived(messageRecieved, patientReported, messageResponse, "Query",
        categoryResponse, orgAccess.getOrg());
    return messageResponse;
  }

  public static List<VaccinationMaster> getVaccinationMasterList(PatientMaster patient,
      Session dataSession) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    List<VaccinationMaster> vaccinationMasterList;
    {
      vaccinationMasterList = new ArrayList<>();
      Query query = dataSession
          .createQuery("from VaccinationMaster where patient = ? order by vaccinationReported asc");
      query.setParameter(0, patient);
      @SuppressWarnings("unchecked")
	List<VaccinationMaster> vmList = query.list();
      Map<String, VaccinationMaster> map = new HashMap<>();
      for (VaccinationMaster vaccinationMaster : vmList) {
        if (vaccinationMaster.getAdministeredDate() != null) {
          String key = sdf.format(vaccinationMaster.getAdministeredDate());
          if (!vaccinationMaster.getVaccineCvxCode().equals("")) {
            key += key + vaccinationMaster.getVaccineCvxCode();
            map.put(key, vaccinationMaster);
          }
        }
      }
      List<String> keyList = new ArrayList<>(map.keySet());
      Collections.sort(keyList);
      for (String key : keyList) {
        vaccinationMasterList.add(map.get(key));
      }

    }
    return vaccinationMasterList;
  }

  public void printQueryNK1(PatientReported patientReported, StringBuilder sb, CodeMap codeMap) {
    if (patientReported != null) {
      if (!patientReported.getGuardianRelationship().equals("")
          && !patientReported.getGuardianLast().equals("")
          && !patientReported.getGuardianFirst().equals("")) {
        Code code = codeMap.getCodeForCodeset(CodesetType.PERSON_RELATIONSHIP,
            patientReported.getGuardianRelationship());
        if (code != null) {
          sb.append("NK1");
          sb.append("|1");
          sb.append("|" + patientReported.getGuardianLast() + "^"
              + patientReported.getGuardianFirst() + "^^^^^L");
          sb.append("|" + code.getValue() + "^" + code.getLabel() + "^HL70063");
          sb.append("\r");
        }
      }
    }
  }

  public void printQueryPID(PatientReported patientReported,
      Set<ProcessingFlavor> processingFlavorSet, StringBuilder sb, PatientMaster patient,
      SimpleDateFormat sdf, int pidCount) {
    // PID
    sb.append("PID");
    // PID-1
    sb.append("|" + pidCount);
    // PID-2
    sb.append("|");
    // PID-3
    sb.append("|" + patient.getPatientExternalLink() + "^^^IIS^SR");
    if (patientReported != null) {
      sb.append("~" + patientReported.getPatientReportedExternalLink() + "^^^"
          + patientReported.getPatientReportedAuthority() + "^"
          + patientReported.getPatientReportedType());
    }
    // PID-4
    sb.append("|");
    // PID-5
    sb.append("|" + patient.getPatientNameLast() + "^" + patient.getPatientNameFirst() + "^"
        + patient.getPatientNameMiddle() + "^^^^L");

    // PID-6
    sb.append("|");
    if (patientReported != null) {
      sb.append(patientReported.getPatientMotherMaiden() + "^^^^^^M");
    }
    // PID-7
    sb.append("|" + sdf.format(patient.getPatientBirthDate()));
    if (patientReported != null) {
      // PID-8
      {
        String sex = patientReported.getPatientSex();
        if (!sex.equals("F") && !sex.equals("M") && !sex.equals("X")) {
          sex = "U";
        }
        sb.append("|" + sex);
      }
      // PID-9
      sb.append("|");
      // PID-10
      sb.append("|");
      {
        String race = patientReported.getPatientRace();
        if (!race.equals("")) {
          if (processingFlavorSet.contains(ProcessingFlavor.PITAYA)
              || processingFlavorSet.contains(ProcessingFlavor.PERSIMMON)) {
            CodeMap codeMap = CodeMapManager.getCodeMap();
            Code raceCode = codeMap.getCodeForCodeset(CodesetType.PATIENT_RACE, race);
            if (processingFlavorSet.contains(ProcessingFlavor.PITAYA) || (raceCode != null
                && CodeStatusValue.getBy(raceCode.getCodeStatus()) != CodeStatusValue.VALID)) {
              sb.append(raceCode);
              sb.append("^");
              if (raceCode != null) {
                sb.append(raceCode.getDescription());
              }
              sb.append("^CDCREC");
            }

          }
        }
      }
      // PID-11
      sb.append("|" + patientReported.getPatientAddressLine1() + "^"
          + patientReported.getPatientAddressLine2() + "^" + patientReported.getPatientAddressCity()
          + "^" + patientReported.getPatientAddressState() + "^"
          + patientReported.getPatientAddressZip() + "^"
          + patientReported.getPatientAddressCountry() + "^");
      if (!processingFlavorSet.contains(ProcessingFlavor.LIME)) {
        sb.append("P");
      }
      // PID-12
      sb.append("|");
      // PID-13
      sb.append("|");
      String phone = patientReported.getPatientPhone();
      if (phone.length() == 10) {
        sb.append("^PRN^PH^^^" + phone.substring(0, 3) + "^" + phone.substring(3, 10));
      }
      // PID-14
      sb.append("|");
      // PID-15
      sb.append("|");
      // PID-16
      sb.append("|");
      // PID-17
      sb.append("|");
      // PID-18
      sb.append("|");
      // PID-19
      sb.append("|");
      // PID-20
      sb.append("|");
      // PID-21
      sb.append("|");
      // PID-22
      sb.append("|");
      {
        String ethnicity = patientReported.getPatientEthnicity();
        if (!ethnicity.equals("")) {
          if (processingFlavorSet.contains(ProcessingFlavor.PITAYA)
              || processingFlavorSet.contains(ProcessingFlavor.PERSIMMON)) {
            CodeMap codeMap = CodeMapManager.getCodeMap();
            Code ethnicityCode =
                codeMap.getCodeForCodeset(CodesetType.PATIENT_ETHNICITY, ethnicity);
            if (processingFlavorSet.contains(ProcessingFlavor.PITAYA) || (ethnicityCode != null
                && CodeStatusValue.getBy(ethnicityCode.getCodeStatus()) != CodeStatusValue.VALID)) {
              sb.append(ethnicityCode);
              sb.append("^");
              if (ethnicityCode != null) {
                sb.append(ethnicityCode.getDescription());
              }
              sb.append("^CDCREC");
            }
          }
        }
      }
      // PID-23
      sb.append("|");
      // PID-24
      sb.append("|");
      sb.append(patientReported.getPatientBirthFlag());
      // PID-25
      sb.append("|");
      sb.append(patientReported.getPatientBirthOrder());

    }
    sb.append("\r");
  }

  public void printORC(OrgAccess orgAccess, StringBuilder sb, VaccinationMaster vaccination,
      VaccinationReported vaccinationReported, boolean originalReporter) {
    Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();
    sb.append("ORC");
    // ORC-1
    sb.append("|RE");
    // ORC-2
    sb.append("|");
    if (vaccination != null) {
      sb.append(vaccination.getVaccinationId() + "^IIS");
    }
    // ORC-3
    sb.append("|");
    if (vaccination == null) {
      if (processingFlavorSet.contains(ProcessingFlavor.LIME)) {
        sb.append("999^IIS");
      } else {
        sb.append("9999^IIS");
      }
    } else {
      if (originalReporter) {
        sb.append(vaccinationReported.getVaccinationReportedExternalLink() + "^"
            + orgAccess.getOrg().getOrganizationName());
      }
    }
    sb.append("\r");
  }

  public List<ForecastActual> doForecast(PatientMaster patient, PatientReported patientReported,
      CodeMap codeMap, List<VaccinationMaster> vaccinationMasterList, OrgAccess orgAccess) {
    List<ForecastActual> forecastActualList = null;
    Set<ProcessingFlavor> processingFlavorSet = orgAccess.getOrg().getProcessingFlavorSet();
    try {
      TestCase testCase = new TestCase();
      testCase.setEvalDate(new Date());
      testCase.setPatientSex(patientReported == null ? "F" : patientReported.getPatientSex());
      testCase.setPatientDob(patient.getPatientBirthDate());
      List<TestEvent> testEventList = new ArrayList<>();
      for (VaccinationMaster vaccination : vaccinationMasterList) {
        Code cvxCode = codeMap.getCodeForCodeset(CodesetType.VACCINATION_CVX_CODE,
            vaccination.getVaccineCvxCode());
        if (cvxCode == null) {
          continue;
        }
        VaccinationReported vaccinationReported = vaccination.getVaccinationReported();
        if ("D".equals(vaccinationReported.getActionCode())) {
          continue;
        }
        int cvx = 0;
        try {
          cvx = Integer.parseInt(vaccinationReported.getVaccineCvxCode());
          TestEvent testEvent = new TestEvent(cvx, vaccinationReported.getAdministeredDate());
          testEventList.add(testEvent);
          vaccinationReported.setTestEvent(testEvent);
        } catch (NumberFormatException nfe) {
          continue;
        }
      }
      testCase.setTestEventList(testEventList);
      Software software = new Software();
      software.setServiceUrl("https://florence.immregistries.org/lonestar/forecast");
      software.setService(Service.LSVF);
      if(processingFlavorSet.contains(ProcessingFlavor.ICE)) {
        software.setServiceUrl("https://florence.immregistries.org/opencds-decision-support-service/evaluate");
        software.setService(Service.ICE);
      }

      ConnectorInterface connector =
          ConnectFactory.createConnecter(software, VaccineGroup.getForecastItemList());
      connector.setLogText(false);
      try {
        forecastActualList = connector.queryForForecast(testCase, new SoftwareResult());
      } catch (IOException ioe) {
        System.err.println("Unable to query for forecast");
        ioe.printStackTrace();
      }
    } catch (Exception e) {
      System.err.println("Unable to query for forecast");
      e.printStackTrace(System.err);
    }
    return forecastActualList;
  }

  public void printObx(StringBuilder sb, int obxSetId, int obsSubId, String loinc,
      String loincLabel, String value) {
    sb.append("OBX");
    // OBX-1
    sb.append("|");
    sb.append(obxSetId);
    // OBX-2
    sb.append("|");
    sb.append("CE");
    // OBX-3
    sb.append("|");
    sb.append(loinc + "^" + loincLabel + "^LN");
    // OBX-4
    sb.append("|");
    sb.append(obsSubId);
    // OBX-5
    sb.append("|");
    sb.append(value);
    // OBX-6
    sb.append("|");
    // OBX-7
    sb.append("|");
    // OBX-8
    sb.append("|");
    // OBX-9
    sb.append("|");
    // OBX-10
    sb.append("|");
    // OBX-11
    sb.append("|");
    sb.append("F");
    sb.append("\r");
  }

  public void printObx(StringBuilder sb, int obxSetId, int obsSubId,
      ObservationMaster observation) {
    ObservationReported ob = observation.getObservationReported();
    sb.append("OBX");
    // OBX-1
    sb.append("|");
    sb.append(obxSetId);
    // OBX-2
    sb.append("|");
    sb.append(ob.getValueType());
    // OBX-3
    sb.append("|");
    sb.append(
        ob.getIdentifierCode() + "^" + ob.getIdentifierLabel() + "^" + ob.getIdentifierTable());
    // OBX-4
    sb.append("|");
    sb.append(obsSubId);
    // OBX-5
    sb.append("|");
    if (ob.getValueTable().equals("")) {
      sb.append(ob.getValueCode());
    } else {
      sb.append(ob.getValueCode() + "^" + ob.getValueLabel() + "^" + ob.getValueTable());
    }
    // OBX-6
    sb.append("|");
    if (ob.getUnitsTable().equals("")) {
      sb.append(ob.getUnitsCode());
    } else {
      sb.append(ob.getUnitsCode() + "^" + ob.getUnitsLabel() + "^" + ob.getUnitsTable());
    }
    // OBX-7
    sb.append("|");
    // OBX-8
    sb.append("|");
    // OBX-9
    sb.append("|");
    // OBX-10
    sb.append("|");
    // OBX-11
    sb.append("|");
    sb.append(ob.getResultStatus());
    // OBX-12
    sb.append("|");
    // OBX-13
    sb.append("|");
    // OBX-14
    sb.append("|");
    if (ob.getObservationDate() != null) {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
      sb.append(sdf.format(ob.getObservationDate()));
    }
    // OBX-15
    sb.append("|");
    // OBX-16
    sb.append("|");
    // OBX-17
    sb.append("|");
    if (ob.getMethodTable().equals("")) {
      sb.append(ob.getMethodCode());
    } else {
      sb.append(ob.getMethodCode() + "^" + ob.getMethodLabel() + "^" + ob.getMethodTable());
    }
    sb.append("\r");
  }

  public void printObx(StringBuilder sb, int obxSetId, int obsSubId, String loinc,
      String loincLabel, String value, String valueLabel, String valueTable) {
    sb.append("OBX");
    // OBX-1
    sb.append("|");
    sb.append(obxSetId);
    // OBX-2
    sb.append("|");
    sb.append("CE");
    // OBX-3
    sb.append("|");
    sb.append(loinc + "^" + loincLabel + "^LN");
    // OBX-4
    sb.append("|");
    sb.append(obsSubId);
    // OBX-5
    sb.append("|");
    sb.append(value + "^" + valueLabel + "^" + valueTable);
    // OBX-6
    sb.append("|");
    // OBX-7
    sb.append("|");
    // OBX-8
    sb.append("|");
    // OBX-9
    sb.append("|");
    // OBX-10
    sb.append("|");
    // OBX-11
    sb.append("|");
    sb.append("F");
    sb.append("\r");
  }


  public void printObx(StringBuilder sb, int obxSetId, int obsSubId, String loinc,
      String loincLabel, Date value) {
    sb.append("OBX");
    // OBX-1
    sb.append("|");
    sb.append(obxSetId);
    // OBX-2
    sb.append("|");
    sb.append("DT");
    // OBX-3
    sb.append("|");
    sb.append(loinc + "^" + loincLabel + "^LN");
    // OBX-4
    sb.append("|");
    sb.append(obsSubId);
    // OBX-5
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    sb.append("|");
    if(value != null){
      sb.append(sdf.format(value));
    }
    // OBX-6
    sb.append("|");
    // OBX-7
    sb.append("|");
    // OBX-8
    sb.append("|");
    // OBX-9
    sb.append("|");
    // OBX-10
    sb.append("|");
    // OBX-11
    sb.append("|");
    sb.append("F");
    sb.append("\r");
  }

  public String printCode(String value, CodesetType codesetType, String tableName,
      CodeMap codeMap) {
    if (value != null) {
      Code code = codeMap.getCodeForCodeset(codesetType, value);
      if (code != null) {
        if (tableName == null) {
          return code.getValue();
        }
        return code.getValue() + "^" + code.getLabel() + "^" + tableName;
      }
    }
    return "";
  }

  public String buildAck(HL7Reader reader, List<ProcessingException> processingExceptionList) {
    StringBuilder sb = new StringBuilder();
    {
      String messageType = "ACK^V04^ACK";
      String profileId = Z23_ACKNOWLEDGEMENT;
      createMSH(messageType, profileId, reader, sb, null);
    }

    String sendersUniqueId = "";
    reader.resetPostion();
    if (reader.advanceToSegment("MSH")) {
      sendersUniqueId = reader.getValue(10);
    } else {
      sendersUniqueId = "MSH NOT FOUND";
    }
    if (sendersUniqueId.equals("")) {
      sendersUniqueId = "MSH-10 NOT VALUED";
    }
    String overallStatus = "AA";
    for (ProcessingException pe : processingExceptionList) {
      if (pe.isError() || pe.isWarning()) {
        overallStatus = "AE";
        break;
      }
    }

    sb.append("MSA|" + overallStatus + "|" + sendersUniqueId + "\r");
    for (ProcessingException pe : processingExceptionList) {
      printERRSegment(pe, sb);
    }
    return sb.toString();
  }

  public void printERRSegment(ProcessingException e, StringBuilder sb) {
    sb.append("ERR|");
    sb.append("|"); // 2
    if (e.getSegmentId() != null && !e.getSegmentId().equals("")) {
      sb.append(e.getSegmentId() + "^" + e.getSegmentRepeat());
      if (e.getFieldPosition() > 0) {
        sb.append("^" + e.getFieldPosition());
      }
    }
    sb.append("|101^Required field missing^HL70357"); // 3
    sb.append("|"); // 4
    if (e.isError()) {
      sb.append("E");
    } else if (e.isWarning()) {
      sb.append("W");
    } else if (e.isInformation()) {
      sb.append("I");
    }
    sb.append("|"); // 5
    sb.append("|"); // 6
    sb.append("|"); // 7
    sb.append("|" + e.getMessage()); // 8
    sb.append("|\r");
  }

  public void createMSH(String messageType, String profileId, HL7Reader reader, StringBuilder sb,
      Set<ProcessingFlavor> processingFlavorSet) {
    String sendingApp = "";
    String sendingFac = "";
    String receivingApp = "";
    String receivingFac = "IIS Sandbox";
    if (processingFlavorSet != null) {
      for (ProcessingFlavor processingFlavor : ProcessingFlavor.values()) {
        if (processingFlavorSet.contains(processingFlavor)) {
          receivingFac += " " + processingFlavor.getKey();
        }
      }
    }
    receivingFac += " v" + SoftwareVersion.VERSION;

    reader.resetPostion();
    if (reader.advanceToSegment("MSH")) {
      sendingApp = reader.getValue(3);
      sendingFac = reader.getValue(4);
      receivingApp = reader.getValue(5);
    }


    String sendingDateString;
    {
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddhhmmssZ");
      sendingDateString = simpleDateFormat.format(new Date());
    }
    String uniqueId;
    {
      uniqueId = "" + System.currentTimeMillis() + nextIncrement();
    }
    String production = reader.getValue(11);
    // build MSH
    sb.append("MSH|^~\\&|");
    sb.append(receivingApp + "|");
    sb.append(receivingFac + "|");
    sb.append(sendingApp + "|");
    sb.append(sendingFac + "|");
    sb.append(sendingDateString + "|");
    sb.append("|");
    sb.append(messageType + "|");
    sb.append(uniqueId + "|");
    sb.append(production + "|");
    sb.append("2.5.1|");
    sb.append("|");
    sb.append("|");
    sb.append("NE|");
    sb.append("NE|");
    sb.append("|");
    sb.append("|");
    sb.append("|");
    sb.append("|");
    sb.append(profileId + "^CDCPHINVS\r");
  }

  private static Integer increment = 1;

  private static int nextIncrement() {
    synchronized (increment) {
      if (increment < Integer.MAX_VALUE) {
        increment = increment + 1;
      } else {
        increment = 1;
      }
      return increment;
    }
  }

  public Date parseDateWarn(String dateString, String errorMessage, String segmentId,
      int segmentRepeat, int fieldPosition, boolean strict,
      List<ProcessingException> processingExceptionList) {
    try {
      return parseDateInternal(dateString, strict);
    } catch (ParseException e) {
      if (errorMessage != null) {
        ProcessingException pe = new ProcessingException(errorMessage + ": " + e.getMessage(),
            segmentId, segmentRepeat, fieldPosition).setWarning();
        processingExceptionList.add(pe);
      }
    }
    return null;
  }

  public Date parseDateError(String dateString, String errorMessage, String segmentId,
      int segmentRepeat, int fieldPosition, boolean strict) throws ProcessingException {
    try {
      Date date = parseDateInternal(dateString, strict);
      if (date == null) {
        if (errorMessage != null) {
          throw new ProcessingException(errorMessage + ": No date was specified", segmentId,
              segmentRepeat, fieldPosition);
        }
      }
      return date;
    } catch (ParseException e) {
      if (errorMessage != null) {
        throw new ProcessingException(errorMessage + ": " + e.getMessage(), segmentId,
            segmentRepeat, fieldPosition);
      }
    }
    return null;
  }

  public Date parseDateInternal(String dateString, boolean strict) throws ParseException {
    if (dateString.length() == 0) {
      return null;
    }
    Date date;
    if (dateString.length() > 8) {
      dateString = dateString.substring(0, 8);
    }
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
    simpleDateFormat.setLenient(!strict);
    date = simpleDateFormat.parse(dateString);
    return date;
  }

  private static final Random random = new Random();
  private static final char[] ID_CHARS =
      {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T',
          'U', 'V', 'W', 'X', 'Y', 'Z', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

  public String generatePatientExternalLink() {
    boolean keepLooking = true;
    int count = 0;
    while (keepLooking) {
      count++;
      if (count > 1000) {
        throw new RuntimeException("Unable to get a new id, tried 1000 times!");
      }
      String patientExternalLink = generateId();
      Query query = dataSession.createQuery("from PatientMaster where patientExternalLink = ?");
      query.setParameter(0, patientExternalLink);
      if (query.list().size() == 0) {
        return patientExternalLink;
        // we found a unique id!
      }
    }
    return null;
  }

  public String generateId() {
    String patientRegistryId = "";
    for (int i = 0; i < 12; i++) {
      patientRegistryId += ID_CHARS[random.nextInt(ID_CHARS.length)];
    }
    return patientRegistryId;
  }
}  
  
