package org.immregistries.iis.kernal.servlet;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;

@SuppressWarnings("serial")
public class VciServlet extends HttpServlet {

  public static final Map<String, String> GENDER_MAP;

  static {
    Map<String, String> aMap = new HashMap<>();
    aMap.put("F", "female");
    aMap.put("M", "male");
    aMap.put("U", "unknown");
    GENDER_MAP = Collections.unmodifiableMap(aMap);
  }

  public static final String PARAM_MESSAGE = "MESSAGEDATA";
  public static final String CONVERSION_STEP = "conversionStep";
  public static final String EXAMPLE_RSP =
      "MSH|^~\\&||IIS Sandbox v0.4.1|||20210327104950-0600||RSP^K11^RSP_K11|16168637904173|P|2.5.1|||NE|NE|||||Z32^CDCPHINVS\r\n"
          + "MSA|AA|1616863788100.1\r\n"
          + "QAK|1616863788100.1|OK|Z34^Request a Complete Immunization History^CDCPHINVS\r\n"
          + "QPD|Z34^Request Immunization History^CDCPHINVS|1616863788100.1|O26S1^^^AIRA-TEST^MR|WestmorelandAIRA^AbigaleAIRA^Hemangi^^^^L|GalvestonAIRA^AlumitAIRA^^^^^M|20170317|F|1049 Daterland Ave^^Coloma^MI^49039^USA^P|^PRN^PH^^^269^5713805|||||\r\n"
          + "PID|1||CVM7PU8NTVR6^^^IIS^SR~O26S1^^^AIRA-TEST^MR||WestmorelandAIRA^AbigaleAIRA^Hemangi^^^^L|GalvestonAIRA^^^^^^M|20170317|F|||1049 Daterland Ave^^Coloma^MI^49039^USA^P||^PRN^PH^^^269^5713805||||||||||||\r\n"
          + "NK1|1|HoggAIRA^AlumitAIRA^^^^^L|MTH^Mother^HL70063\r\n"
          + "ORC|RE|11533^IIS|O26S1.3^AART Primary\r\n"
          + "RXA|0|1|20210327||03^MMR^CVX|0.5|mL^milliliters^UCUM||00^New immunization record^NIP001||||||U1747GW||MSD^Merck and Co., Inc.^MVX|||CP|A\r\n"
          + "RXR|C38299^Subcutaneous^NCIT|LA^Left Upper Arm^HL70163\r\n"
          + "OBX|1|CE|30956-7^Vaccine type^LN|1|06^06^CVX||||||F\r\n"
          + "OBX|2|CE|59781-5^Dose validity^LN|1|Y^Y^99107||||||F\r\n"
          + "OBX|3|CE|30956-7^Vaccine type^LN|2|05^05^CVX||||||F\r\n"
          + "OBX|4|CE|59781-5^Dose validity^LN|2|Y^Y^99107||||||F\r\n"
          + "OBX|5|CE|30956-7^Vaccine type^LN|3|07^07^CVX||||||F\r\n"
          + "OBX|6|CE|59781-5^Dose validity^LN|3|Y^Y^99107||||||F\r\n"
          + "OBX|7|CE|64994-7^Vaccine funding program eligibility category^LN|4|V05^VFC eligible - Federally Qualified Health Center Patient (under-insured)^HL70064||||||F|||20210327|||VXC40^Eligibility captured at the immunization level^CDCPHINVS\r\n"
          + "OBX|8|CE|30956-7^Vaccine Type^LN|4|03^MMR^CVX||||||F||||||\r\n"
          + "OBX|9|TS|29768-9^Date vaccine information statement published^LN|4|20120420||||||F||||||\r\n"
          + "OBX|10|TS|29769-7^Date vaccine information statement presented^LN|4|20210327||||||F||||||\r\n"
          + "ORC|RE||9999^IIS\r\n";

  private Map<String, Object> fhirImm = new HashMap<>();
  private Map<String, Object> fhirPatient = new HashMap<>();
  private static SessionFactory factory;

  public static Session getDataSession() {
    if (factory == null) {
      factory = new AnnotationConfiguration().configure().buildSessionFactory();
    }
    return factory.openSession();
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    HttpSession session = req.getSession(true);
    resp.setContentType("text/html");
    PrintWriter out = new PrintWriter(resp.getOutputStream());
    try {
      String message = req.getParameter(PARAM_MESSAGE);
      String conversionStep = req.getParameter(CONVERSION_STEP);
      if ("fhirResources".equals(conversionStep)) {
        HapiContext context = new DefaultHapiContext();
        Parser p = context.getGenericParser();
        Message hapiMsg = p.parse(message);
        Terser terser = new Terser(hapiMsg);
        String bd = terser.get("/.PID-7");
        String birthDate = bd.substring(0, 4) + "-" + bd.substring(4, 6) + "-" + bd.substring(6, 8);
        String sex = terser.get("/.PID-8");
        String gender = GENDER_MAP.getOrDefault(sex, "other");
        String familyName = terser.get("/.PID-5-1");
        String firstName = terser.get("/.PID-5-2");
        String middleName = terser.get("/.PID-5-3");
        String cvxCode = terser.get("/.RXA-5-1");
        String im = terser.get("/.RXA-3");
        String immDate = im.substring(0, 4) + "-" + im.substring(4, 6) + "-" + im.substring(6, 8);
        String lotNumber = terser.get("/.RXA-15");

        fhirPatient.put("resourceType", "Patient");
        fhirPatient.put("gender", gender);
        fhirPatient.put("birthDate", birthDate);

        Map<String, Object> nameObj = new HashMap<>();
        nameObj.put("family", familyName);
        nameObj.put("given", Arrays.asList(firstName, middleName));

        fhirPatient.put("name", Arrays.asList(nameObj));

        ObjectMapper mapper = new ObjectMapper();
        message = mapper.writeValueAsString(fhirPatient);

        fhirImm.put("resourceType", "Immunization");
        fhirImm.put("status", "completed");

        Map<String, Object> codingObj = new HashMap<>();
        codingObj.put("system", "http://hl7.org/fhir/sid/cvx");
        codingObj.put("code", cvxCode);

        Map<String, Object> vaccineCodeObj = new HashMap<>();
        vaccineCodeObj.put("coding", Arrays.asList(codingObj));
        fhirImm.put("vaccineCode", vaccineCodeObj);

        Map<String, Object> patientObj = new HashMap<>();
        patientObj.put("reference", "resource:0");

        fhirImm.put("patient", patientObj);
        fhirImm.put("occurrenceDateTime", immDate);
        fhirImm.put("lotNumber", lotNumber);

        message += "\n" + mapper.writeValueAsString(fhirImm);

      } else if ("verifiableCredential".equals(conversionStep)) {

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> verifiableCredential = new HashMap<>();
        verifiableCredential.put("iss", "https://florence.immregistries.org/issuer");
        verifiableCredential.put("nbf", System.currentTimeMillis() / 1000L);

        Map<String, Object> vcObj = new HashMap<>();
        vcObj.put("@context", Arrays.asList("https://www.w3.org/2018/credentials/v1"));
        vcObj.put(
            "type", Arrays.asList("VerifiableCredential", "https://smarthealth.cards#health-card"));

        Map<String, Object> csObj = new HashMap<>();
        csObj.put("fhirVersion", "4.0.1");

        Map<String, Object> fhirBundleObj = new HashMap<>();
        fhirBundleObj.put("resourceType", "Bundle");
        fhirBundleObj.put("type", "collection");

        ArrayList<Object> fhirList = new ArrayList<>();
        Map<String, Object> entryObj0 = new HashMap<>();
        entryObj0.put("fullUrl", "resource:0");
        entryObj0.put("resource", fhirPatient);

        Map<String, Object> entryObj1 = new HashMap<>();
        entryObj1.put("fullUrl", "resource:1");
        entryObj1.put("resource", fhirImm);

        fhirList.add(entryObj0);
        fhirList.add(entryObj1);

        fhirBundleObj.put("entry", fhirList);
        csObj.put("fhirBundle", fhirBundleObj);
        vcObj.put("credentialSubject", csObj);
        verifiableCredential.put("vc", vcObj);

        message = mapper.writeValueAsString(verifiableCredential);
      }
      HomeServlet.doHeader(out, session);
      out.println("    <h2>VCI Demonstration</h2>");
      out.println("    <form action=\"vciDemo\" method=\"POST\">");
      if (conversionStep == null) {
        out.println("      <h3>Response (RSP) Message</h3>");
        out.println(
            "      <textarea class=\"w3-input\" name=\""
                + PARAM_MESSAGE
                + "\" rows=\"15\" cols=\"160\">"
                + EXAMPLE_RSP
                + "</textarea></td>");
        out.println("    <div class=\"w3-container w3-half w3-margin-top\">");
        out.println("    <div class=\"w3-container w3-card-4\">");
        out.println(
            "      <input class=\"w3-button w3-section w3-teal w3-ripple\" type=\"submit\" name=\""
                + CONVERSION_STEP
                + "\" value=\"fhirResources\"/>");
        out.println("    </div>");
      } else if ("fhirResources".equals(conversionStep)) {
        out.println("      <h3>FHIR Resources</h3>");
        out.println(
            "      <textarea class=\"w3-input\" name=\""
                + PARAM_MESSAGE
                + "\" rows=\"15\" cols=\"160\">"
                + message
                + "</textarea></td>");
        out.println("    <div class=\"w3-container w3-half w3-margin-top\">");
        out.println(
            "      <input class=\"w3-button w3-section w3-teal w3-ripple\" type=\"submit\" name=\""
                + CONVERSION_STEP
                + "\" value=\"verifiableCredential\"/>");
        out.println("    </div>");
      } else {
        out.println("      <h3>Verifiable Credential</h3>");
        out.println(
            "      <textarea class=\"w3-input\" name=\""
                + PARAM_MESSAGE
                + "\" rows=\"15\" cols=\"160\">"
                + message
                + "</textarea></td>");
        out.println("    <div class=\"w3-container w3-half w3-margin-top\">");
        out.println(
            "      <input class=\"w3-button w3-section w3-teal w3-ripple\" type=\"submit\" name=\""
                + CONVERSION_STEP
                + "\" value=\"Generate JWS (not implemented)\"/>");
      }

      out.println("    </div>");
      out.println("    </form>");
      HomeServlet.doFooter(out, session);
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    out.flush();
    out.close();
  }
}
