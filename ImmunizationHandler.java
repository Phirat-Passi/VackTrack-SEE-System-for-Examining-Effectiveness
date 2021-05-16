package org.immregistries.iis.kernal.logic;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import org.hibernate.Query;
import org.hibernate.Session;

import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.immregistries.iis.kernal.model.OrgLocation;
import org.immregistries.iis.kernal.model.PatientReported;
import org.immregistries.iis.kernal.model.VaccinationMaster;
import org.immregistries.iis.kernal.model.VaccinationReported;

import org.immregistries.vaccination_deduplication.computation_classes.Deterministic;
import org.immregistries.vaccination_deduplication.reference.ComparisonResult;
import org.immregistries.vaccination_deduplication.reference.ImmunizationSource;
import ca.uhn.fhir.rest.api.server.RequestDetails;

public class ImmunizationHandler {

  /**
   * This method set the patientReported information based on the patient information
   * @param patientReported the patientReported
   * @param i the Immunization resource
   */
  public static void patientReportedFromFhirImmunization(PatientReported patientReported,
      Immunization i) {
    if (!i.equals(null)) {
      patientReported.setReportedDate(i.getRecorded());
      patientReported.setUpdatedDate(i.getOccurrenceDateTimeType().getValue());
      patientReported.setPatientReportedAuthority(i.getIdentifierFirstRep().getValue());
      //patientReported.setPatientReportedType(patientReportedType);
    }

  }
  /**
   * This method set the vaccinnationReported information based on the immunization information
   * @param vaccinationReported the vaccinationReported
   * @param i the Immunization resource
   */
  public static void vaccinationReportedFromFhirImmunization(
      VaccinationReported vaccinationReported, Immunization i) {
    //vaccinationReported.setVaccinationReportedId(0);
    vaccinationReported.setVaccinationReportedExternalLink(i.getId());
    if (i.getRecorded() != null) {
      vaccinationReported.setReportedDate(i.getRecorded());
    } else {
      vaccinationReported.setReportedDate(new Date());
    }
    vaccinationReported.setUpdatedDate(new Date());
    vaccinationReported.setLotnumber(i.getLotNumber());
    vaccinationReported.setAdministeredDate(i.getOccurrenceDateTimeType().getValue());
    vaccinationReported.setAdministeredAmount(i.getDoseQuantity().getValue().toString());
    vaccinationReported.setExpirationDate(i.getExpirationDate());
    vaccinationReported.setVaccinationReportedExternalLink(i.getIdentifier().get(0).getValue());
    /*switch (i.getStatus().toCode()){
        case "completed" : vaccinationReported.setCompletionStatus("CP");
        case "entered-in-error" : vaccinationReported.setCompletionStatus("entered-in-error"); //TODO find accurate value
        case "not-done" : vaccinationReported.setCompletionStatus("not-done");  //TODO find accurate value
    }*/

    //vaccinationReported.setActionCode();
    vaccinationReported.setRefusalReasonCode(i.getReasonCodeFirstRep().getText());
    vaccinationReported.setVaccineCvxCode(i.getVaccineCode().getCodingFirstRep().getCode());
  }

  /**
   * This method set the vaccinnationMaster information based on the immunization information
   * @param vaccinationMaster the vaccinationReported
   * @param i the Immunization resource
   */
  public static void vaccinationMasterFromFhirImmunization(VaccinationMaster vaccinationMaster,
      Immunization i) {
    vaccinationMaster.setAdministeredDate(i.getOccurrenceDateTimeType().getValue());
    vaccinationMaster.setVaccineCvxCode(i.getVaccineCode().getCodingFirstRep().getCode());
  }

  /**
   * This method set the Location information based on the immunization information
   * @param orgLocation the orgLocation
   * @param i the Immunization resource
   */
  public static void orgLocationFromFhirImmunization(OrgLocation orgLocation, Immunization i) {
    Location l = i.getLocationTarget();
    orgLocation.setOrgFacilityCode(l.getId()); //TODO create an external identifier or change the usage of the name
    orgLocation.setOrgFacilityName(l.getName());
    //orgLocation.setLocationType(l.getTypeFirstRep());
    orgLocation.setAddressCity(l.getAddress().getLine().get(0).getValueNotNull());
    if (l.getAddress().getLine().size() > 1) {
      orgLocation.setAddressLine2(l.getAddress().getLine().get(1).getValueNotNull());
    }
    orgLocation.setAddressCity(l.getAddress().getCity());
    orgLocation.setAddressState(l.getAddress().getState());
    orgLocation.setAddressZip(l.getAddress().getPostalCode());
    orgLocation.setAddressCountry(l.getAddress().getCountry());
  }

  /**
   * This methods create the immunization resource based on the vaccinationReported information
   *@param theRequestDetails authentification access information
   * @param vr the vaccinationReported
   * @return the Immunization resource
   */
  public static Immunization getImmunization(RequestDetails theRequestDetails,
      VaccinationReported vr) {
    Immunization i = new Immunization();
    i.setId(vr.getVaccinationReportedExternalLink());
    i.setRecorded(vr.getReportedDate());
    i.setLotNumber(vr.getLotnumber());
    i.getOccurrenceDateTimeType().setValue(vr.getAdministeredDate());
    i.setDoseQuantity(new Quantity());
    i.getDoseQuantity().setValue(new BigDecimal(vr.getAdministeredAmount()));
    i.setExpirationDate(vr.getExpirationDate());
    if (!vr.getCompletionStatus().equals("")) {
      i.setStatus(Immunization.ImmunizationStatus.valueOf(vr.getCompletionStatus()));
    }

    i.addReasonCode().addCoding().setCode(vr.getRefusalReasonCode());
    i.getVaccineCode().addCoding().setCode(vr.getVaccineCvxCode());
    //if (pr != null){
    i.setPatient(new Reference(theRequestDetails.getFhirServerBase() + "/Patient/"
        + vr.getPatientReported().getPatientReportedExternalLink()));
    //}

    Location location = i.getLocationTarget();
    OrgLocation ol = vr.getOrgLocation();
    if (ol != null) {
      location.setId(ol.getOrgFacilityCode());
      location.setName(ol.getOrgFacilityName());

      Address address = location.getAddress();
      address.addLine(ol.getAddressLine1());
      address.addLine(ol.getAddressLine2());
      address.setCity(ol.getAddressCity());
      address.setState(ol.getAddressState());
      address.setPostalCode(ol.getAddressZip());
      address.setCountry(ol.getAddressCountry());
    }
    Extension links = new Extension("#links");
    Extension link;
    link = new Extension();
    link.setValue(
        new StringType(theRequestDetails.getFhirServerBase() + "/MedicationAdministration/"
            + vr.getVaccination().getVaccinationReported().getVaccinationReportedExternalLink()));
    links.addExtension(link);
    i.addExtension(links);
    return i;
  }

  /**
   * This methods is looking for matches based on the algorithm used in deduplication servlet
   * @param dataSession the Session
   * @param patientReported the patient
   * @param immunization the immunization resource
   * @return the vaccinationMaster found, null if none has been found
   */

  public static VaccinationMaster findMatch(Session dataSession, PatientReported patientReported, Immunization immunization) throws ParseException {
    VaccinationMaster vm = null;
    Deterministic comparer = new Deterministic();
    ComparisonResult comparison;
    org.immregistries.vaccination_deduplication.Immunization i1;
    org.immregistries.vaccination_deduplication.Immunization i2;

    i1 = new org.immregistries.vaccination_deduplication.Immunization();
    i1.setCVX(immunization.getVaccineCode().toString());
    //i1.setDate(String.valueOf(immunization.getOccurrenceDateTimeType()));
    i1.setLotNumber(immunization.getLotNumber());
    if (immunization.getPrimarySource()){
      i1.setSource(ImmunizationSource.SOURCE);
    }else {
      i1.setSource(ImmunizationSource.HISTORICAL);
    }

    {
      Query query = dataSession.createQuery(
          "from VaccinationReported where patientReported = ?");
      query.setParameter(0, patientReported);
      @SuppressWarnings("unchecked")
      List<VaccinationReported> vaccinationReportedList = query.list();

      for (VaccinationReported vaccinationReported : vaccinationReportedList){
        i2 = new org.immregistries.vaccination_deduplication.Immunization();
        i2.setCVX(vaccinationReported.getVaccineCvxCode());
        i2.setDate(vaccinationReported.getAdministeredDate());
        i2.setLotNumber(vaccinationReported.getLotnumber());
        if (immunization.getPrimarySource()){
          i2.setSource(ImmunizationSource.SOURCE);
        }else {
          i2.setSource(ImmunizationSource.HISTORICAL);
        }
        comparison = comparer.compare(i1,i2);
        if (comparison.equals(ComparisonResult.EQUAL)) {
          return vaccinationReported.getVaccination();
        }
      }
    }
    return vm;
  }

}
