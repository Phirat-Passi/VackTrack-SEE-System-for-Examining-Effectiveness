package org.immregistries.iis.kernal.fhir;

import java.util.List;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.immregistries.iis.kernal.model.OrgAccess;
import org.immregistries.iis.kernal.model.OrgMaster;
import org.immregistries.iis.kernal.model.VaccinationMaster;
import org.immregistries.iis.kernal.model.VaccinationReported;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;

public class RestfulMedicationAdministrationProvider implements IResourceProvider {
  protected Session dataSession = null;
  protected OrgAccess orgAccess = null;
  protected OrgMaster orgMaster = null;
  protected VaccinationMaster vaccinationMaster = null;
  private static SessionFactory factory;

  /**
   * The getResourceType method comes from IResourceProvider, and must
   * be overridden to indicate what type of resource this provider
   * supplies.
   */
  @Override
  public Class<MedicationAdministration> getResourceType() {
    return MedicationAdministration.class;
  }

  public static Session getDataSession() {
    if (factory == null) {
      factory = new AnnotationConfiguration().configure().buildSessionFactory();
    }
    return factory.openSession();
  }

  /**
   * This methods asks to find and rebuild the MedicationAdministration resource with the id provided
   * @param theRequestDetails autenthification access information
   * @param theId The id of the medicationAdministration resource
   *
   *
   * @return the MedicationAdministration, null is none was found in the database
   */
  @Read()
  public MedicationAdministration getResourceById(RequestDetails theRequestDetails,
      @IdParam IdType theId) {
    MedicationAdministration medicationAdministration = null;
    Session dataSession = getDataSession();
    String id = theId.getIdPart();
    try {
      orgAccess = Authentication.authenticateOrgAccess(theRequestDetails, dataSession);
      {
        Query query = dataSession
            .createQuery("from VaccinationReported where vaccinationReportedExternalLink= ?");
        //query.setParameter(0, orgAccess.getOrg());
        query.setParameter(0, id);
        @SuppressWarnings("unchecked")
		List<VaccinationReported> vaccinationReportedList = query.list();
        if (vaccinationReportedList.size() > 0) {
          vaccinationMaster = vaccinationReportedList.get(0).getVaccination();
        }
      }
      if (vaccinationMaster != null) {
        medicationAdministration = new MedicationAdministration();
        medicationAdministration.setId(id);
        medicationAdministration
            .setEffective(new DateTimeType(vaccinationMaster.getAdministeredDate()));
        medicationAdministration.setSubject(new Reference(theRequestDetails.getFhirServerBase()
            + "/Patient/" + vaccinationMaster.getPatient().getPatientId()));
        {
          Query query = dataSession.createQuery("from VaccinationReported where vaccination= ?");
          query.setParameter(0, vaccinationMaster);
          @SuppressWarnings("unchecked")
		List<VaccinationReported> vaccinationReportedList = query.list();
          if (vaccinationReportedList.size() > 0) {
            Extension links = new Extension("#links");
            Extension link;
            for (VaccinationReported vl : vaccinationReportedList) {
              link = new Extension();
              link.setValue(new StringType(theRequestDetails.getFhirServerBase() + "/Immunization/"
                  + vl.getVaccinationReportedExternalLink()));
              links.addExtension(link);
            }
            medicationAdministration.addExtension(links);
          }
        }

      }

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      dataSession.close();
    }


    return medicationAdministration;
  }
}
