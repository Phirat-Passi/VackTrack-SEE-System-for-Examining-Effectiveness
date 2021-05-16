package org.immregistries.iis.kernal.model;

import java.util.HashSet;
import java.util.Set;

public enum LoincIdentifier {
                             DATE_VACCINE_INFORMATION_STATEMENT_PUBLISHED("29768-9",
                                 "Date Vaccine Information Statement Published"),
                             DATE_VACCINE_INFORMATION_STATEMENT_PRESENTED("29769-7",
                                 "Date Vaccine Information Statement Presented"),
                             VACCINATION_TEMPORARY_CONTRAINDICATION_PRECAUTION_EXPIRATION_DATE(
                                 "30944-3",
                                 "Vaccination temporary contraindication/precaution expiration date"),
                             VACCINATION_CONTRAINDICATION_PRECAUTION("30945-0",
                                 "Vaccination contraindication/precaution"),
                             VACCINATION_CONTRAINDICATION_PRECAUTION_EFFECTIVE_DATE("30946-8",
                                 "Vaccination contraindication/precaution effective date"),
                             VACCINE_TYPE_VACCINE_GROUP_OR_FAMILY("30956-7",
                                 "Vaccine Type (Vaccine group or family)"),
                             VACCINE_FUNDING_SOURCE("30963-3", "Vaccine funding source"),
                             DOSE_NUMBER_IN_SERIES("30973-2", "Dose number in series"),
                             VACCINES_DUE_NEXT("30979-9", "Vaccines due next"),
                             DATE_VACCINE_DUE("30980-7", "Date vaccine due"),
                             EARLIEST_DATE_TO_GIVE("30981-5", "Earliest date to give"),
                             REASON_APPLIED_BY_FORECAST_LOGIC_TO_PROJECT_THIS_VACCINE("30982-3",
                                 "Reason applied by forecast logic to project this vaccine"),
                             REACTION("31044-1", "Reaction"),
                             COMPONENT_VACCINE_TYPE("38890-0", "Component Vaccine Type"),
                             VACCINATION_TAKE_RESPONSE_TYPE("46249-9",
                                 "VACCINATION TAKE-RESPONSE TYPE"),
                             VACCINATION_TAKE_RESPONSE_DATE("46250-7",
                                 "VACCINATION TAKE-RESPONSE DATE"),
                             ANNOTATION_COMMENT_INTERPRETATION_NARRATIVE("48767-8",
                                 "Annotation comment [Interpretation] Narrative"),
                             LATEST_DATE_NEXT_DOSE_MAY_BE_GIVEN("59777-3",
                                 "Latest date next dose may be given"),
                             DATE_DOSE_IS_OVERDUE("59778-1", "Date Dose is Overdue"),
                             IMMUNIZATION_SCHEDULE_USED("59779-9", "Immunization Schedule used"),
                             IMMUNIZATION_SERIES_NAME("59780-7", "Immunization Series name"),
                             DOSE_VALIDITY("59781-5", "Dose validity"),
                             NUMBER_OF_DOSES_IN_PRIMARY_SERIES("59782-3",
                                 "Number of doses in primary series"),
                             STATUS_IN_IMMUNIZATION_SERIES("59783-1",
                                 "Status in immunization series"),
                             DISEASE_WITH_PRESUMED_IMMUNITY("59784-9",
                                 "Disease with presumed immunity"),
                             INDICATIONS_TO_IMMUNIZE("59785-6", "Indications to immunize"),
                             VACCINE_FUNDING_PROGRAM_ELIGIBILITY_CATEGORY("64994-7",
                                 "Vaccine funding program eligibility category"),
                             DOCUMENT_TYPE("69764-9", "Document type"),
                             SEROLOGICAL_EVIDENCE_OF_IMMUNITY("75505-8",
                                 "Serological Evidence of Immunity"),

                             SARS_COV_2_XXX_QL_CULT("94763-0", "SARS-CoV-2 XXX Ql Cult"),
                             SARS_COV_2_AB_SERPL_IMP("94661-6", "SARS-CoV-2 Ab SerPl-Imp"),
                             SARS_COV_2_AB_SERPL_QL_IA("94762-2", "SARS-CoV-2 Ab SerPl Ql IA"),
                             SARS_COV_2_AB_SERPL_IA_ACNC("94769-7", "SARS-CoV-2 Ab SerPl IA-aCnc"),
                             SARS_COV_2_AB_PNL_SERPL_IA("94504-8", "SARS-CoV-2 Ab Pnl SerPl IA"),
                             SARS_COV_2_AG_RESP_QL_IA_RAPID("94558-4",
                                 "SARS-CoV-2 Ag Resp Ql IA.rapid"),
                             SARS_COV_2_E_GENE_CT_XXX_QN_NAA_PLUS_PROBE("94509-7",
                                 "SARS-CoV-2 E gene Ct XXX Qn NAA+probe"),
                             SARS_COV_2_E_GENE_RESP_QL_NAA_PLUS_PROBE("94758-0",
                                 "SARS-CoV-2 E gene Resp Ql NAA+probe"),
                             SARS_COV_2_E_GENE_SERPL_QL_NAA_PLUS_PROBE("94765-5",
                                 "SARS-CoV-2 E gene SerPl Ql NAA+probe"),
                             SARS_COV_2_E_GENE_XXX_QL_NAA_PLUS_PROBE("94315-9",
                                 "SARS-CoV-2 E gene XXX Ql NAA+probe"),
                             SARS_COV_2_IGA_SERPL_QL_IA("94562-6", "SARS-CoV-2 IgA SerPl Ql IA"),
                             SARS_COV_2_IGA_SERPLBLD_QL_IA_RAPID("94768-9",
                                 "SARS-CoV-2 IgA SerPlBld Ql IA.rapid"),
                             SARS_COV_2_IGA_SERPL_IA_ACNC("94720-0",
                                 "SARS-CoV-2 IgA SerPl IA-aCnc"),
                             SARS_COV_2_IGA_PLUS_IGM_SERPL_QL_IA("95125-1",
                                 "SARS-CoV-2 IgA+IgM SerPl Ql IA"),
                             SARS_COV_2_IGG_DBS_QL_IA("94761-4", "SARS-CoV-2 IgG DBS Ql IA"),
                             SARS_COV_2_IGG_SERPL_QL_IA("94563-4", "SARS-CoV-2 IgG SerPl Ql IA"),
                             SARS_COV_2_IGG_SERPLBLD_QL_IA_RAPID("94507-1",
                                 "SARS-CoV-2 IgG SerPlBld Ql IA.rapid"),
                             SARS_COV_2_IGG_SERPL_IA_ACNC("94505-5",
                                 "SARS-CoV-2 IgG SerPl IA-aCnc"),
                             SARS_COV_2_IGG_PLUS_IGM_PNL_SERPLBLD_IA_RAPID("94503-0",
                                 "SARS-CoV-2 IgG+IgM Pnl SerPlBld IA.rapid"),
                             SARS_COV_2_IGG_PLUS_IGM_SERPL_QL_IA("94547-7",
                                 "SARS-CoV-2 IgG+IgM SerPl Ql IA"),
                             SARS_COV_2_IGM_SERPL_QL_IA("94564-2", "SARS-CoV-2 IgM SerPl Ql IA"),
                             SARS_COV_2_IGM_SERPLBLD_QL_IA_RAPID("94508-9",
                                 "SARS-CoV-2 IgM SerPlBld Ql IA.rapid"),
                             SARS_COV_2_IGM_SERPL_IA_ACNC("94506-3",
                                 "SARS-CoV-2 IgM SerPl IA-aCnc"),
                             SARS_COV_2_N_GENE_CT_XXX_QN_NAA_PLUS_PROBE("94510-5",
                                 "SARS-CoV-2 N gene Ct XXX Qn NAA+probe"),
                             SARS_COV_2_N_GENE_CT_XXX_QN_NAA_N1("94311-8",
                                 "SARS-CoV-2 N gene Ct XXX Qn NAA N1"),
                             SARS_COV_2_N_GENE_CT_XXX_QN_NAA_N2("94312-6",
                                 "SARS-CoV-2 N gene Ct XXX Qn NAA N2"),
                             SARS_COV_2_N_GENE_NPH_QL_NAA_PLUS_PROBE("94760-6",
                                 "SARS-CoV-2 N gene Nph Ql NAA+probe"),
                             SARS_COV_2_N_GENE_RESP_QL_NAA_PLUS_PROBE("94533-7",
                                 "SARS-CoV-2 N gene Resp Ql NAA+probe"),
                             SARS_COV_2_N_GENE_RESP_QL_NAA_N1("94756-4",
                                 "SARS-CoV-2 N gene Resp Ql NAA N1"),
                             SARS_COV_2_N_GENE_RESP_QL_NAA_N2("94757-2",
                                 "SARS-CoV-2 N gene Resp Ql NAA N2"),
                             SARS_COV_2_N_GENE_SERPL_QL_NAA_PLUS_PROBE("94766-3",
                                 "SARS-CoV-2 N gene SerPl Ql NAA+probe"),
                             SARS_COV_2_N_GENE_XXX_QL_NAA_PLUS_PROBE("94316-7",
                                 "SARS-CoV-2 N gene XXX Ql NAA+probe"),
                             SARS_COV_2_N_GENE_XXX_QL_NAA_N1("94307-6",
                                 "SARS-CoV-2 N gene XXX Ql NAA N1"),
                             SARS_COV_2_N_GENE_XXX_QL_NAA_N2("94308-4",
                                 "SARS-CoV-2 N gene XXX Ql NAA N2"),
                             SARS_COV_2_ORF1AB_CT_RESP_QN_NAA_PLUS_PROBE("94644-2",
                                 "SARS-CoV-2 ORF1ab Ct Resp Qn NAA+probe"),
                             SARS_COV_2_ORF1AB_CT_XXX_QN_NAA_PLUS_PROBE("94511-3",
                                 "SARS-CoV-2 ORF1ab Ct XXX Qn NAA+probe"),
                             SARS_COV_2_ORF1AB_RESP_QL_NAA_PLUS_PROBE("94559-2",
                                 "SARS-CoV-2 ORF1ab Resp Ql NAA+probe"),
                             SARS_COV_2_ORF1AB_XXX_QL_NAA_PLUS_PROBE("94639-2",
                                 "SARS-CoV-2 ORF1ab XXX Ql NAA+probe"),
                             SARS_COV_2_RDRP_CT_RESP_QN_NAA_PLUS_PROBE("94646-7",
                                 "SARS-CoV-2 RdRp Ct Resp Qn NAA+probe"),
                             SARS_COV_2_RDRP_CT_XXX_QN_NAA_PLUS_PROBE("94645-9",
                                 "SARS-CoV-2 RdRp Ct XXX Qn NAA+probe"),
                             SARS_COV_2_RDRP_RESP_QL_NAA_PLUS_PROBE("94534-5",
                                 "SARS-CoV-2 RdRp Resp Ql NAA+probe"),
                             SARS_COV_2_RDRP_XXX_QL_NAA_PLUS_PROBE("94314-2",
                                 "SARS-CoV-2 RdRp XXX Ql NAA+probe"),
                             SARS_COV_2_RNA_CT_RESP_QN_NAA_PLUS_PROBE("94745-7",
                                 "SARS-CoV-2 RNA Ct Resp Qn NAA+probe"),
                             SARS_COV_2_RNA_CT_XXX_QN_NAA_PLUS_PROBE("94746-5",
                                 "SARS-CoV-2 RNA Ct XXX Qn NAA+probe"),
                             SARS_COV_2_RNA_XXX_NAA_PLUS_PROBE_LOG_POUND_("94819-0",
                                 "SARS-CoV-2 RNA XXX NAA+probe-Log#"),
                             SARS_COV_2_RNA_NPH_QL_NAA_PLUS_NON_PROBE("94565-9",
                                 "SARS-CoV-2 RNA Nph Ql NAA+non-probe"),
                             SARS_COV_2_RNA_NPH_QL_NAA_PLUS_PROBE("94759-8",
                                 "SARS-CoV-2 RNA Nph Ql NAA+probe"),
                             SARS_COV_2_RNA_RESP_QL_NAA_PLUS_PROBE("94500-6",
                                 "SARS-CoV-2 RNA Resp Ql NAA+probe"),
                             SARS_COV_2_RNA_SAL_QL_NAA_PLUS_PROBE("94845-5",
                                 "SARS-CoV-2 RNA Sal Ql NAA+probe"),
                             SARS_COV_2_RNA_SAL_QL_SEQ("94822-4", "SARS-CoV-2 RNA Sal Ql Seq"),
                             SARS_COV_2_RNA_SERPL_QL_NAA_PLUS_PROBE("94660-8",
                                 "SARS-CoV-2 RNA SerPl Ql NAA+probe"),
                             SARS_COV_2_RNA_XXX_QL_NAA_PLUS_PROBE("94309-2",
                                 "SARS-CoV-2 RNA XXX Ql NAA+probe"),
                             SARS_COV_2_RNA_PNL_RESP_NAA_PLUS_PROBE("94531-1",
                                 "SARS-CoV-2 RNA Pnl Resp NAA+probe"),
                             SARS_COV_2_RNA_PNL_XXX_NAA_PLUS_PROBE("94306-8",
                                 "SARS-CoV-2 RNA Pnl XXX NAA+probe"),
                             SARS_COV_2_S_GENE_CT_RESP_QN_NAA_PLUS_PROBE("94642-6",
                                 "SARS-CoV-2 S gene Ct Resp Qn NAA+probe"),
                             SARS_COV_2_S_GENE_CT_XXX_QN_NAA_PLUS_PROBE("94643-4",
                                 "SARS-CoV-2 S gene Ct XXX Qn NAA+probe"),
                             SARS_COV_2_S_GENE_RESP_QL_NAA_PLUS_PROBE("94640-0",
                                 "SARS-CoV-2 S gene Resp Ql NAA+probe"),
                             SARS_COV_2_S_GENE_SERPL_QL_NAA_PLUS_PROBE("94767-1",
                                 "SARS-CoV-2 S gene SerPl Ql NAA+probe"),
                             SARS_COV_2_S_GENE_XXX_QL_NAA_PLUS_PROBE("94641-8",
                                 "SARS-CoV-2 S gene XXX Ql NAA+probe"),
                             SARS_COV_2_GENOME_ISLT_SEQ("94764-8", "SARS-CoV-2 genome Islt-Seq"),
                             SARS_COV_PLUS_SARS_COV_2_AG_RESP_QL_IA_RAPID("95209-3",
                                 "SARS-CoV+SARS-CoV-2 Ag Resp Ql IA.rapid"),
                             SARS_LIKE_COV_N_CT_XXX_QN_NAA_PLUS_PROBE("94313-4",
                                 "SARS-like CoV N Ct XXX Qn NAA+probe"),
                             SARS_LIKE_COV_N_XXX_QL_NAA_PLUS_PROBE("94310-0",
                                 "SARS-like CoV N XXX Ql NAA+probe"),
                             SARS_REL_COV_RNA_RESP_QL_NAA_PLUS_PROBE("94502-2",
                                 "SARS-rel CoV RNA Resp Ql NAA+probe"),
                             SARSR_COV_RNA_XXX_QL_NAA_PLUS_PROBE("94647-5",
                                 "SARSr-CoV RNA XXX Ql NAA+probe"),
                             SARS_REL_COV_PLUS_MERS_RNA_RESP_QL_NAA_PLUS_PROBE("94532-9",
                                 "SARS-rel CoV+MERS RNA Resp Ql NAA+probe"),


                             TPG_PAND("TPG_PAND", "Priority Group - Pandemic"),
                             TPG_POP_GRP("TPG_POP_GRP", "Priority Group - Population Group"),
                             TPG_TIER("TPG_TIER", "Priority Group - Tier"),

  ;

  private static Set<String> suppressIdentifierCodeSet = new HashSet<>();

  public static Set<String> getSuppressIdentifierCodeSet() {
    return suppressIdentifierCodeSet;
  }

  static {
    DATE_VACCINE_INFORMATION_STATEMENT_PUBLISHED.suppress = true;
    DATE_VACCINE_INFORMATION_STATEMENT_PRESENTED.suppress = true;
    VACCINE_TYPE_VACCINE_GROUP_OR_FAMILY.suppress = true;
    VACCINE_FUNDING_SOURCE.suppress = true;
    DOSE_NUMBER_IN_SERIES.suppress = true;
    VACCINES_DUE_NEXT.suppress = true;
    DATE_VACCINE_DUE.suppress = true;
    EARLIEST_DATE_TO_GIVE.suppress = true;
    REASON_APPLIED_BY_FORECAST_LOGIC_TO_PROJECT_THIS_VACCINE.suppress = true;
    COMPONENT_VACCINE_TYPE.suppress = true;
    LATEST_DATE_NEXT_DOSE_MAY_BE_GIVEN.suppress = true;
    DATE_DOSE_IS_OVERDUE.suppress = true;
    IMMUNIZATION_SCHEDULE_USED.suppress = true;
    IMMUNIZATION_SERIES_NAME.suppress = true;
    DOSE_VALIDITY.suppress = true;
    NUMBER_OF_DOSES_IN_PRIMARY_SERIES.suppress = true;
    STATUS_IN_IMMUNIZATION_SERIES.suppress = true;
    VACCINE_FUNDING_PROGRAM_ELIGIBILITY_CATEGORY.suppress = true;
    DOCUMENT_TYPE.suppress = true;

    for (LoincIdentifier oi : values()) {
      if (oi.suppress) {
        suppressIdentifierCodeSet.add(oi.identifierCode);
      }
    }
  }


  private boolean suppress = false;
  private String identifierCode;
  private String identifierLabel;

  private LoincIdentifier(String identifierCode, String identifierLabel) {
    this.identifierCode = identifierCode;
    this.identifierLabel = identifierLabel;
  }

  public boolean isSuppress() {
    return suppress;
  }

  public void setSuppress(boolean suppress) {
    this.suppress = suppress;
  }

  public String getIdentifierCode() {
    return identifierCode;
  }

  public void setIdentifierCode(String identifierCode) {
    this.identifierCode = identifierCode;
  }

  public String getIdentifierLabel() {
    return identifierLabel;
  }

  public void setIdentifierLabel(String identifierLabel) {
    this.identifierLabel = identifierLabel;
  }

}
