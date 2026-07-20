package ke.skyworld.mbanking.cbs.dynamics;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.co.skyworld.smp.utility_items.security.Encryption;
import ke.co.skyworld.smp.utility_items.security.HashUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static ke.co.skyworld.smp.query_manager.SystemTables.TBL_SYSTEM_PARAMETERS;


/**
 <PARAMETERS>
 <URL>http://192.168.0.71:7047/BC230/WS/TELEPOST%20DT%20SACCO/Codeunit/MobileBankingEngine</URL>
 <POST_DEPOSITS_URL>http://192.168.0.71:7047/BC230/WS/TELEPOST%20DT%20SACCO/Codeunit/AUPaybillAutomations</POST_DEPOSITS_URL>
 <POST_LOANS_CHECK_URL>http://192.168.0.71:7047/BC230/WS/TELEPOST%20DT%20SACCO/Codeunit/LoanSweeping</POST_LOANS_CHECK_URL>
 <SOAP_ACTION>urn:microsoft-dynamics-schemas/codeunit/MobileBankingEngine:ProcessRequest</SOAP_ACTION>
 <POST_DEPOSITS_SOAP_ACTION>urn:microsoft-dynamics-schemas/codeunit/AUPaybillAutomations:FnPostPaybillTransaction</POST_DEPOSITS_SOAP_ACTION>
 <POST_LOANS_CHECK_SOAP_ACTION>urn:microsoft-dynamics-schemas/codeunit/LoanSweeping:SweepDefaultedLoans</POST_LOANS_CHECK_SOAP_ACTION>
 <XMNLS_EXTENSION>123</XMNLS_EXTENSION>
 <AUTH_PARAMETERS>
 <SSL_CERTIFICATES ENABLED="NO"/>
 <DOMAIN>.</DOMAIN>
 <USER>TELEPOST\SKYWORLD</USER>
 <PASSWORD TYPE="CLEARTEXT"></PASSWORD>
 </AUTH_PARAMETERS>
 </PARAMETERS>
 */
public class DynamicsCBSParams {

    private static String theSOAPURL;
    private static String thePostDepositsURL;
    private static String theCheckLoansURL;
    private static String theSOAPAction;
    private static String thePostDepositsSOAPAction;
    private static String theLoansCheckSOAPAction;
    private static boolean doSSLValidation;
    private static String theDomain;
    private static String theUser;
    private static String thePassword;

    /*These two are used in case we want to log the request / response*/
    private static boolean theLogRequest;
    private static boolean theLogResponse;
    private static boolean theCallServiceLogResponse;
    private static boolean theCallServiceLogRequest;




    private DynamicsCBSParams(){}

    public static void initialize() throws Exception {
        String DYNAMICS_CBS_PARAMETERS = "DYNAMICS_CBS_PARAMETERS";
        
        FlexicoreHashMap parametersMap = SystemParameters.getParameterMap(DYNAMICS_CBS_PARAMETERS);

        if(parametersMap == null
                || parametersMap.isEmpty()
                || parametersMap.getStringValue("parameter_value")==null
                || parametersMap.getStringValue("parameter_value").trim().isEmpty()){
            throw new IllegalStateException("Missing System Parameter '"+DYNAMICS_CBS_PARAMETERS +"'");
        }

        /*if(HashUtils.isRecordIntegrityViolated(parametersMap)){
            throw new IllegalStateException("Corrupt Details found for System Parameter: "+ DYNAMICS_CBS_PARAMETERS);
        }*/

        String strProfitsCBSParameters = parametersMap.getStringValue("parameter_value");
        System.out.println("DynamicsCBSParams.initialize() strProfitsCBSParameters: " + strProfitsCBSParameters);

        Document document = XmlUtils.parseXml(strProfitsCBSParameters);
        if(document ==null){
            throw new RuntimeException("Unable to convert Parameter Value for "+DYNAMICS_CBS_PARAMETERS+" to XML document.");
        }

        Element elSOAPURL = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/URL");
        Element elPostDepositsURL = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/POST_DEPOSITS_URL");
        Element elCheckLoans = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/POST_LOANS_CHECK_URL");
        Element elSOAPAction = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/SOAP_ACTION");
        Element elPostDepositsSOAPAction = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/POST_DEPOSITS_SOAP_ACTION");
        Element elPostLoansCheckAction = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/POST_LOANS_CHECK_SOAP_ACTION");
        Element elSSLCertificates = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/AUTH_PARAMETERS/SSL_CERTIFICATES");
        Element elDomain = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/AUTH_PARAMETERS/DOMAIN");
        Element elUser = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/AUTH_PARAMETERS/USER");
        Element elPassword = XmlUtils.getElementNodeFromXpath(document, "/PARAMETERS/AUTH_PARAMETERS/PASSWORD");

        theSOAPURL = elSOAPURL.getTextContent();
        thePostDepositsURL = elPostDepositsURL.getTextContent();
        theSOAPAction = elSOAPAction.getTextContent();
        thePostDepositsSOAPAction = elPostDepositsSOAPAction.getTextContent();
        theCheckLoansURL = elCheckLoans.getTextContent();
        theLoansCheckSOAPAction = elPostLoansCheckAction.getTextContent();
        theDomain = elDomain.getTextContent();
        theUser = elUser.getTextContent();
        thePassword = elPassword.getTextContent();
        doSSLValidation = elSSLCertificates.getAttribute("ENABLED").equalsIgnoreCase("YES");

        String strPasswordType = elPassword.getAttribute("TYPE");

        if(strPasswordType.equalsIgnoreCase("ENCRYPTED")){
            thePassword = Encryption.decrypt(elPassword.getTextContent());
        }else{
            thePassword = elPassword.getTextContent();

            FlexicoreHashMap tempUpdateMap = new FlexicoreHashMap();
            tempUpdateMap.putValue("/PARAMETERS/AUTH_PARAMETERS/PASSWORD/@TYPE","ENCRYPTED");
            tempUpdateMap.putValue("/PARAMETERS/AUTH_PARAMETERS/PASSWORD",Encryption.encrypt(thePassword));
            String strUpdatedParamValue = XmlUtils.updateXMLTags(document, tempUpdateMap);

            FlexicoreHashMap updateMap = new FlexicoreHashMap();
            updateMap.putValue("parameter_value", strUpdatedParamValue);
            updateMap.putValue("date_modified", DateTime.getCurrentDateTime());

            parametersMap.copyFrom(updateMap);
            String integrityHash = HashUtils.calculateIntegrityHash(parametersMap);

            updateMap.putValue("integrity_hash", integrityHash);

            Repository.update(StringRefs.SENTINEL, TBL_SYSTEM_PARAMETERS,
                    updateMap,
                    new FilterPredicate("parameter_id = :parameter_id"),
                    new FlexicoreHashMap().addQueryArgument(":parameter_id", parametersMap.getStringValue("parameter_id"))
            );
        }

        try (InputStream isPropertiesFile = new FileInputStream("props.properties")) {
            Properties ptProperties = new Properties();

            ptProperties.load(isPropertiesFile);

            String strLogRequest = ptProperties.getProperty("dynamics.cbs.request.logs.display");
            String strLogResponse = ptProperties.getProperty("dynamics.cbs.response.logs.display");
            String strLogCallServiceReq = ptProperties.getProperty("dynamics.cbs.callservice.request.logs.display");
            String strLogCallServiceRes = ptProperties.getProperty("dynamics.cbs.callservice.response.logs.display");

            theLogRequest = strLogRequest.equalsIgnoreCase("true");
            theLogResponse = strLogResponse.equalsIgnoreCase("true");
            theCallServiceLogRequest = strLogCallServiceReq.equalsIgnoreCase("true");
            theCallServiceLogResponse = strLogCallServiceRes.equalsIgnoreCase("true");

        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    public static String getSOAPURL() {
        return theSOAPURL;
    }

    public static String getPostDepositsURL() {
        return thePostDepositsURL;
    }

    public static String getSOAPAction() {
        return theSOAPAction;
    }

    public static String getPostDepositsSOAPAction() {
        return thePostDepositsSOAPAction;
    }

    public static String getCheckLoansURL() {
        return theCheckLoansURL;
    }

    public static String getLoansCheckSOAPAction() {
        return theLoansCheckSOAPAction;
    }
    public static boolean getSSLValidation() {
        return doSSLValidation;
    }

    public static String getDomain() {
        return theDomain;
    }

    public static String getUser() {
        return theUser;
    }

    public static String getPassword() {
        return thePassword;
    }

    public static boolean isLogRequestEnabled() {
        return theLogRequest;
    }

    public static boolean isLogResponseEnabled() {
        return theLogResponse;
    }

    public static boolean isCallServiceLogRequestEnabled() {
        return theCallServiceLogRequest;
    }

    public static boolean isCallServiceLogResponseEnabled() {
        return theCallServiceLogResponse;
    }
}
