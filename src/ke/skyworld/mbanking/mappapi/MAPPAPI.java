package ke.skyworld.mbanking.mappapi;

import ke.co.skyworld.smp.authentication_manager.MobileBankingCryptography;
import ke.co.skyworld.smp.permissions.FlexicorePermissionsReader;
import ke.co.skyworld.smp.permissions.SystemApplicationCodes;
import ke.co.skyworld.smp.query_manager.SystemTables;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_manager.query.QueryBuilder;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.query_repository.CommutationRepository;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.Misc;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.co.skyworld.smp.utility_items.file_utils.FileOps;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.core.MBankingUtils;
import ke.skyworld.lib.mbanking.email.EMail;
import ke.skyworld.lib.mbanking.email.EMailConstants;
import ke.skyworld.lib.mbanking.mapp.MAPPConstants;
import ke.skyworld.lib.mbanking.mapp.MAPPRequest;
import ke.skyworld.lib.mbanking.mapp.MAPPResponse;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.pesa.PESA;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.pesa.PESAProcessor;
import ke.skyworld.lib.mbanking.register.MemberRegisterResponse;
import ke.skyworld.lib.mbanking.register.RegisterConstants;
import ke.skyworld.lib.mbanking.register.RegisterProcessor;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Crypto;
import ke.skyworld.lib.mbanking.utils.InMemoryCache;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.cbs.ChannelService;
import ke.skyworld.mbanking.cbs.ApStarCBS;
import ke.skyworld.mbanking.channelutils.EmailMessaging;
import ke.skyworld.mbanking.channelutils.EmailTemplates;
import ke.skyworld.mbanking.mbankingapi.MBankingAPI;
import ke.skyworld.mbanking.mbankingapi.MBankingAPIUtils;
import ke.skyworld.mbanking.pesaapi.PESAAPI;
import ke.skyworld.mbanking.pesaapi.PESAAPIConstants;
import ke.skyworld.mbanking.pesaapi.PesaParam;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import ke.skyworld.mbanking.ussdapplication.AppConstants;
import ke.skyworld.mbanking.ussdapplication.AppUtils;
import ke.skyworld.sp.manager.SPManager;
import ke.skyworld.sp.manager.SPManagerConstants;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ke.co.skyworld.smp.query_manager.SystemTables.*;
import static ke.co.skyworld.smp.query_manager.SystemTables.TBL_HELP_AND_SUPPORT_READ_RECEIPTS;
import static ke.skyworld.lib.mbanking.mapp.MAPPConstants.ResponseAction.*;
import static ke.skyworld.lib.mbanking.mapp.MAPPConstants.ResponseStatus.*;
import static ke.skyworld.lib.mbanking.mapp.MAPPConstants.ResponsesDataType.*;
import static ke.skyworld.mbanking.mappapi.MAPPAPIConstants.strIOSTESTOTP;
import static ke.skyworld.mbanking.mappapi.MAPPAPIConstants.strIOSTESTUSERNAME;
import static ke.skyworld.mbanking.ussdapi.APIUtils.*;
import static ke.skyworld.mbanking.ussdapi.USSDAPIConstants.StandardReturnVal.INVALID_APP_ID;;
import static ke.skyworld.mbanking.ussdapplication.DividendPayslipMenus.saveBase64PDF;

//TODO: QUEUE SMS AND RUN THE API CALLS IN A THREAD INSTEAD OF DIRECTLY ON THE APP, EXCEPT FOR BALANCE ENQUIRY AND MINI-STATEMENT

//TODO: SMS on Loan Payment via MPESA

public class MAPPAPI {

    boolean blGroupBankingEnabled = false;

    private MAPPResponse setMAPPResponse(Node theRepsonseMSG, MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = new MAPPResponse();

        try {
            String strDateTime = MBankingDB.getDBDateTime();
            theMAPPResponse.setMessagesVersion("1.01");
            theMAPPResponse.setMessagesDateTime(strDateTime);
            theMAPPResponse.setSessionID(theMAPPRequest.getSessionID());
            theMAPPResponse.setMAPPType(theMAPPRequest.getMAPPType());

            theMAPPResponse.setMSG(theRepsonseMSG);
            theMAPPResponse.setDateCreated(strDateTime);
            theMAPPResponse.setIntegrityHash("");
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".setMAPPResponse() ERROR : " + e.getMessage());
            e.printStackTrace();
        }


        return theMAPPResponse;
    }

    private void generateResponseMSGNode(Document doc, Element theElementData, MAPPRequest theMAPPRequest, MAPPConstants.ResponseAction theAction, MAPPConstants.ResponseStatus theStatus, String theCharge, String theTitle, MAPPConstants.ResponsesDataType theDataType) {
        MAPPResponse theMAPPResponse = new MAPPResponse();

        try {
            /*
            <MSG SESSION_ID='123121' TYPE='MOBILE_BANKING' ACTION='END' STATUS='FAILED' CHARGE='NO'>
                <TITLE>Login Failed</TITLE>
                <DATA TYPE='TEXT'>Invalid Mobile Number or PIN</DATA>
            </MSG>
             */
            //TEST

            Element elMSG = doc.createElement("MSG");
            doc.appendChild(elMSG);

            // set attribute SESSION_ID to MSG element
            Attr attrSessionID = doc.createAttribute("SESSION_ID");
            attrSessionID.setValue(Long.toString(theMAPPRequest.getSessionID()));
            elMSG.setAttributeNode(attrSessionID);

            // set attribute TYPE to MSG element
            Attr attrType = doc.createAttribute("TYPE");
            attrType.setValue(theMAPPRequest.getMAPPType().getValue());
            elMSG.setAttributeNode(attrType);

            // set attribute ACTION to MSG element
            Attr attrAction = doc.createAttribute("ACTION");
            attrAction.setValue(theAction.getValue());
            elMSG.setAttributeNode(attrAction);

            // set attribute STATUS to MSG element
            Attr attrStatus = doc.createAttribute("STATUS");
            attrStatus.setValue(theStatus.getValue());
            elMSG.setAttributeNode(attrStatus);

            // set attribute CHARGE to MSG element
            Attr attrCharge = doc.createAttribute("CHARGE");
            attrCharge.setValue(theCharge);
            elMSG.setAttributeNode(attrCharge);

            // set Element TITLE to MSG element
            Element elTitle = doc.createElement("TITLE");
            elTitle.setTextContent(theTitle);
            elMSG.appendChild(elTitle);

            // set Element TYPE to MSG element
            elMSG.appendChild(theElementData);

            // set attribute CHARGE to MSG element
            Attr attrDataType = doc.createAttribute("TYPE");
            attrDataType.setValue(theDataType.getValue());
            theElementData.setAttributeNode(attrDataType);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".generateResponseMSGNode() ERROR : " + e.getMessage());
            e.printStackTrace();
        }
    }

    static String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
    }

    String getUserFullName(MAPPRequest theMAPPRequest, String strUserPhoneNumber) {
        String strAccountName = "";

        TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES, "full_name",
                new FilterPredicate("primary_mobile_number = :primary_mobile_number"),
                new FlexicoreHashMap().addQueryArgument(":primary_mobile_number", strUserPhoneNumber));

        if (signatoryDetailsWrapper.hasErrors()) {
            return "";
        }

        FlexicoreHashMap signatoryDetailsMap = signatoryDetailsWrapper.getSingleRecord();

        if (signatoryDetailsMap != null && !signatoryDetailsMap.isEmpty()) {
            strAccountName = signatoryDetailsMap.getStringValue("full_name");
            return Utils.toTitleCase(strAccountName);
        }

        return strAccountName;
    }

    public MAPPResponse userLogin(MAPPRequest theMAPPRequest, MAPPAPIConstants.OTP_TYPE theOTPType, boolean checkIfPhoneHasChanged) {

        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strVersion = theMAPPRequest.getVersion();
            String strMessagesVersion = theMAPPRequest.getMessagesVersion();
            String strAppID = theMAPPRequest.getAppID();

            //System.out.println(strAppID);
            Node ndRequestMSG = theMAPPRequest.getMSG();





           /* if(!(strUsername.equals("254720259655") || strUsername.equals("254729566788")
                    || strUsername.equals("254720416494") || strUsername.equals("254722832021")
                    || strUsername.equals("254722793859") || strUsername.equals("254726589392")
                    || strUsername.equals("254728432445") || strUsername.equals("254713000249")
                    || strUsername.equals("254722378923") || strUsername.equals("254726958265")
                    || strUsername.equals("254114041681") || strUsername.equals("254713000249")
                    ||(strUsername.equals("254723782649"))
                    || strUsername.equals("254716304210") || strUsername.equals("254729692224"))) {
                String strTitle = "Service Under Maintenance";
                String strDescription = "The service is currently under maintenance. Please try again later.";

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                // Root element - MSG
                Document doc = docBuilder.newDocument();

                String strCharge = "NO";
                MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;
                Element elData = doc.createElement("DATA");

                Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
                elDescription.setTextContent(strDescription);
                elData.appendChild(elDescription);
                generateResponseMSGNode(doc, elData, theMAPPRequest, CON, ERROR, strCharge, strTitle, enDataType);
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                return setMAPPResponse(ndResponseMSG, theMAPPRequest);
            }*/

            String strNotificationID = configXPath.evaluate("NOTIFICATION_ID", ndRequestMSG).trim();
            if (theOTPType == MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL) {
                strPassword = configXPath.evaluate("PASSWORD", ndRequestMSG).trim();
            }

            System.out.println(XmlUtils.convertNodeToStr(ndRequestMSG));

            String strMAPPVersionFromUser = configXPath.evaluate("BUILD_NUMBER", ndRequestMSG).trim();

            boolean blOTPVerificationRequired = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.GENERATION).isEnabled();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Mobile Banking";
            String strDescription = "Welcome to Mobile Banking. Please visit your nearest branch to activate your account for mobile banking.";

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            String strLoginStatus = "ERROR";
            String strLoginAttemptMessage = "Sorry, this service is not available at the moment. Please try again later. If the problem persist kindly contact us for assistance.";

            Element elData = doc.createElement("DATA");

            String strMemberFullName = "";
            String strMemberGender = "MALE";

            String strSettingsXML = SystemParameters.getParameter("MBANKING_SERVICES_MANAGEMENT");
            Document docSettingsXML = XmlUtils.parseXml(strSettingsXML);


            String strOrganizationMbankingSettings = SystemParameters.getParameter("ORGANIZATION_MBANKING_SETTINGS");
            Document docOrganizationSettingsXML = XmlUtils.parseXml(strOrganizationMbankingSettings);


            String strMobileAppServiceStatus = XmlUtils.getTagValue(docSettingsXML, "/MBANKING_SERVICES/MAPP/@STATUS");
            String strMobileAppDisplayMessage = XmlUtils.getTagValue(docSettingsXML, "/MBANKING_SERVICES/MAPP/@MESSAGE");

            String strCharge = "NO";

            //1. CHECK IF MOBILE BANKING IS ENABLED
            if (!strMobileAppServiceStatus.equalsIgnoreCase("ACTIVE")) {
                strTitle = "Service Under Maintenance";
                strDescription = strMobileAppDisplayMessage;

                Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
                elDescription.setTextContent(strDescription);
                elData.appendChild(elDescription);
                generateResponseMSGNode(doc, elData, theMAPPRequest, CON, ERROR, strCharge, strTitle, enDataType);
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                return setMAPPResponse(ndResponseMSG, theMAPPRequest);
            }

            //2. CHECK IF MOBILE BANKING APP IS LATEST VERSION

            String strCorrectMAPPVersion = XmlUtils.getTagValue(docSettingsXML, "/MBANKING_SERVICES/@MAPP_VERSION");

            if (!strMAPPVersionFromUser.matches("\\d+")) {
                strMAPPVersionFromUser = "0";
            }

            int intMAPPVersionFromUser;

            try {
                intMAPPVersionFromUser = Integer.parseInt(strMAPPVersionFromUser);
            } catch (Exception e) {
                intMAPPVersionFromUser = 0;
            }
            System.out.println("MAPP Version From User: " + intMAPPVersionFromUser);
            System.out.println("Correct MAPP Version: " + strCorrectMAPPVersion);

            if (intMAPPVersionFromUser < Integer.parseInt(strCorrectMAPPVersion) && theMAPPRequest.getAction().equalsIgnoreCase("LOGIN")) {

                strTitle = AppConstants.strSACCOProductName + " Update";
                strDescription = "A new version of " + AppConstants.strSACCOProductName + " has been released. Please update on the Play Store or App Store before proceeding.";

                Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
                elDescription.setTextContent(strDescription);
                elData.appendChild(elDescription);
                generateResponseMSGNode(doc, elData, theMAPPRequest, END, ERROR, strCharge, strTitle, enDataType);
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                return setMAPPResponse(ndResponseMSG, theMAPPRequest);
            }

            //3. PROCEED TO LOG IN

            TransactionWrapper<FlexicoreHashMap> userLoginWrapper = CBSAPI.userLogin(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword, "APP_ID", strAppID,
                    USSDAPIConstants.MobileChannel.MOBILE_APP);

            FlexicoreHashMap userLoginMap = userLoginWrapper.getSingleRecord();

            if (userLoginWrapper.hasErrors()) {

                USSDAPIConstants.StandardReturnVal theReturnVal = userLoginMap.getValue("cbs_api_return_val");
                strTitle = userLoginMap.getValue("title");
                strDescription = userLoginMap.getValue("display_message");

                if (theReturnVal != INVALID_APP_ID) {
                    Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
                    elDescription.setTextContent(strDescription);
                    elData.appendChild(elDescription);
                    generateResponseMSGNode(doc, elData, theMAPPRequest, CON, ERROR, strCharge, strTitle, enDataType);
                    Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                    return setMAPPResponse(ndResponseMSG, theMAPPRequest);
                } else {

                    FlexicoreHashMap mobileBankingDetailsMap = userLoginMap.getFlexicoreHashMap("mobile_register_details");
                    FlexicoreHashMap signatoryDetailsMap = userLoginMap.getFlexicoreHashMap("signatory_details");

                    strMemberFullName = signatoryDetailsMap.getStringValueOrIfNull("full_name", "");
                    strMemberGender = signatoryDetailsMap.getStringValueOrIfNull("gender", "").equalsIgnoreCase("F") ? "FEMALE" : "MALE";

                    strTitle = "Mobile App is Not Activated";
                    strDescription = "Your Mobile App is not activated. Tap 'ACTIVATE' below to activate the Mobile App.";

                    Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
                    elDescription.setTextContent(strDescription);
                    elData.appendChild(elDescription);

                    String strActivationInstructions = "" +
                            "To retrieve your mobile app activation code:" +
                            "<br/>1. Dial <b>" + AppConstants.strSACCOUSSDCode + "</b>" +
                            "<br/>2. Enter your Mobile Banking PIN" +
                            "<br/>3. Select <b>'My Account'</b>" +
                            "<br/>4. Select <b>'Mobile App'</b>" +
                            "<br/>5. Select <b>'ACTIVATE Mobile App'</b>" +
                            "<br/>6. Select <b>'Yes'</b>" +
                            "<br/>7. Wait for an SMS with the mobile app activation code" +
                            "<br/>8. Enter the activation code below then press <b>'Activate'</b>";

                    Element elActivationInstructions = doc.createElement("ACTIVATION_INSTRUCTIONS");
                    elActivationInstructions.setTextContent(strActivationInstructions);
                    elData.appendChild(elActivationInstructions);

                    String strMemberName = strMemberFullName.split(" ")[0];

                    Element elMemberData = doc.createElement("MEMBER_DATA");
                    elMemberData.setAttribute("NAME", strMemberName);
                    elMemberData.setAttribute("FULL_NAME", strMemberFullName);
                    elMemberData.setAttribute("GENDER", strMemberGender);
                    elData.appendChild(elMemberData);

                    String strPrivacyStatementLink = XmlUtils.getTagValue(docOrganizationSettingsXML, "/MBANKING_SETTINGS/PRIVACY_STATEMENT");
                    Element elPrivacyStatement = doc.createElement("PRIVACY_STATEMENT");
                    elPrivacyStatement.setTextContent(strPrivacyStatementLink);
                    elData.appendChild(elPrivacyStatement);

                    generateResponseMSGNode(doc, elData, theMAPPRequest, CHALLENGE_LOGIN, SUCCESS, strCharge, strTitle, enDataType);
                    Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                    return setMAPPResponse(ndResponseMSG, theMAPPRequest);
                }
            }


            FlexicoreHashMap mobileBankingDetailsMap = userLoginMap.getFlexicoreHashMap("mobile_register_details");
            FlexicoreHashMap signatoryDetailsMap = userLoginMap.getFlexicoreHashMap("signatory_details");

            strMemberFullName = signatoryDetailsMap.getStringValueOrIfNull("full_name", "");
            strMemberGender = signatoryDetailsMap.getStringValueOrIfNull("gender", "").equalsIgnoreCase("F") ? "FEMALE" : "MALE";

            String strMemberName = strMemberFullName.split(" ")[0];

            Element elMemberData = doc.createElement("MEMBER_DATA");
            elMemberData.setAttribute("NAME", strMemberName);
            elMemberData.setAttribute("FULL_NAME", strMemberFullName);
            elMemberData.setAttribute("GENDER", strMemberGender);
            elData.appendChild(elMemberData);

            String strAcceptedTermsAndConditions = mobileBankingDetailsMap.getStringValue("accepted_terms_and_conditions");
            if (strAcceptedTermsAndConditions.equalsIgnoreCase("NO")) {

                strTitle = "Privacy Statement";
                strDescription = XmlUtils.getTagValue(docOrganizationSettingsXML, "/MBANKING_SETTINGS/PRIVACY_STATEMENT");

                System.out.println(XmlUtils.convertNodeToStr(docOrganizationSettingsXML));

                Element elDescription = doc.createElement("PRIVACY_STATEMENT");
                elDescription.setTextContent(strDescription);
                elData.appendChild(elDescription);
                generateResponseMSGNode(doc, elData, theMAPPRequest, ACCEPT_TERMS_AND_CONDITIONS, SUCCESS, strCharge, strTitle, enDataType);
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));

                return setMAPPResponse(ndResponseMSG, theMAPPRequest);
            }

            strTitle = "Login Successful";
            strDescription = "The login was successful";

            if (blOTPVerificationRequired) {
                generateOTP(theMAPPRequest);
            }

            Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
            elDescription.setTextContent(strDescription);
            elData.appendChild(elDescription);

            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, SUCCESS, strCharge, strTitle, enDataType);
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            return setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();

            Document doc = XmlUtils.createNewDocument();
            Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
            Element elData = doc.createElement("DATA");

            elDescription.setTextContent("Error occurred while processing your request. If the problem persists please contact your organization for further assistance");
            elData.appendChild(elDescription);
            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, ERROR, "NO", "ERROR", TEXT);
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
            return setMAPPResponse(ndResponseMSG, theMAPPRequest);
        }
    }

    public MAPPResponse acceptTermsAndConditions(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            boolean blAddDataAction = false;

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            //String strActivationCode = configXPath.evaluate("TERMS_AND_CONDITIONS", ndRequestMSG).trim();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Error";
            String strDescription = "An error occurred. Please try again after a few minutes.";

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = ERROR;

            Element elData = doc.createElement("DATA");

            TransactionWrapper<FlexicoreHashMap> currentUserDetailsWrapper = CBSAPI.getCurrentUserDetails(getTraceID(theMAPPRequest), "MSISDN",
                    strUsername, "APP_ID", strAppID);

            FlexicoreHashMap userDetailsMap = currentUserDetailsWrapper.getSingleRecord();

            String strMemberFullName = "";
            String strMemberGender = "MALE";

            if (currentUserDetailsWrapper.hasErrors()) {
                strTitle = userDetailsMap.getStringValue("title");
                strDescription = userDetailsMap.getStringValue("display_message");
                USSDAPIConstants.Condition endSession = userDetailsMap.getValue("end_session");

                if (endSession == USSDAPIConstants.Condition.YES) {
                    enResponseAction = END;
                } else {
                    enResponseAction = CON;
                }

                enResponseStatus = ERROR;

            } else {

                FlexicoreHashMap signatoryMap = userDetailsMap.getFlexicoreHashMap("signatory_details");
                FlexicoreHashMap mobileBankingMap = userDetailsMap.getFlexicoreHashMap("mobile_register_details");

                strMemberFullName = signatoryMap.getStringValueOrIfNull("full_name", "");
                strMemberGender = signatoryMap.getStringValueOrIfNull("gender", "").equalsIgnoreCase("F") ? "FEMALE" : "MALE";

                TransactionWrapper<FlexicoreHashMap> acceptTermsAndConditionsWrapper = CBSAPI.acceptTermsAndConditions(mobileBankingMap, USSDAPIConstants.MobileChannel.MOBILE_APP);

                if (acceptTermsAndConditionsWrapper.hasErrors()) {
                    strTitle = "Privacy Statement";
                    strDescription = "An error occurred. Please try again after a few minutes.";
                    enResponseAction = END;
                    enResponseStatus = ERROR;
                } else {

                    String strAppIdentifier = mobileBankingMap.getStringValue("app_identifier");

                    if (strAppIdentifier == null || strAppIdentifier.isEmpty() || !strAppIdentifier.equalsIgnoreCase(strAppID)) {

                        strTitle = "Mobile App is Not Activated";
                        String strActivationInstructions = "" +
                                "To retrieve your mobile app activation code:" +
                                "<br/>1. Dial <b>" + AppConstants.strSACCOUSSDCode + "</b>" +
                                "<br/>2. Enter your Mobile Banking PIN" +
                                "<br/>3. Select <b>'My Account'</b>" +
                                "<br/>4. Select <b>'Mobile App'</b>" +
                                "<br/>5. Select <b>'ACTIVATE Mobile App'</b>" +
                                "<br/>6. Select <b>'Yes'</b>" +
                                "<br/>7. Wait for an SMS with the mobile app activation code" +
                                "<br/>8. Enter the activation code below then press <b>'Activate'</b>";

                        strDescription = "Your Mobile App is not activated. Tap 'ACTIVATE' below to activate the Mobile App.";

                        Element elActivationInstructions = doc.createElement("ACTIVATION_INSTRUCTIONS");
                        elActivationInstructions.setTextContent(strActivationInstructions);
                        elData.appendChild(elActivationInstructions);

                        enResponseAction = MAPPConstants.ResponseAction.CHALLENGE_LOGIN;
                        enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                    } else {
                        strTitle = "Privacy Statement";
                        strDescription = "You have accepted Privacy Statement successfully";

                        enResponseAction = CON;
                        enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                    }
                }
            }

            String strCharge = "NO";

            Element elDescription = doc.createElement("LOGIN_RESPONSE_DESCRIPTION");
            elDescription.setTextContent(strDescription);
            elData.appendChild(elDescription);

            String strMemberName = strMemberFullName.split(" ")[0];

            Element elMemberData = doc.createElement("MEMBER_DATA");
            elMemberData.setAttribute("NAME", strMemberName);
            elMemberData.setAttribute("FULL_NAME", strMemberFullName);
            elMemberData.setAttribute("GENDER", strMemberGender);
            elData.appendChild(elMemberData);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public APIUtils.OTP checkOTPRequirement(MAPPRequest theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE theOtpCheckStage) {
        boolean blRval = false;
        APIUtils.OTP otp = new APIUtils.OTP(0, 0, "", "", false);
        otp.setEnabled(false);

        Node ndRequestMSG;
        XPath configXPath;
        Node ndOTP;
        try {
            ndRequestMSG = theMAPPRequest.getMSG();
            configXPath = XPathFactory.newInstance().newXPath();

            String strOTPID = "";
            int intOTPTTL = 0;
            String strOTPTTL = "";
            int intOTPLength = 0;
            String strOTPLength = "";

            ndOTP = (Node) configXPath.evaluate("OTP", ndRequestMSG, XPathConstants.NODE);
            if (ndOTP != null) {
                strOTPID = configXPath.evaluate("@ID", ndOTP).trim();
                otp.setId(strOTPID);
                strOTPTTL = configXPath.evaluate("@TTL", ndOTP).trim();
                if (strOTPTTL != null && !strOTPTTL.equals("")) {
                    intOTPTTL = Integer.parseInt(strOTPTTL);
                    otp.setTtl(intOTPTTL);
                }
                strOTPLength = configXPath.evaluate("@LENGTH", ndOTP).trim();
                if (strOTPLength != null && !strOTPLength.equals("")) {
                    intOTPLength = Integer.parseInt(strOTPLength);
                    otp.setLength(intOTPLength);
                }
            }

            if (theOtpCheckStage == MAPPAPIConstants.OTP_CHECK_STAGE.GENERATION) {
                if (ndOTP != null && intOTPTTL != 0 && intOTPLength != 0) {
                    otp.setEnabled(true);
                }
            } else if (theOtpCheckStage == MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION) {
                if (ndOTP != null) {
                    otp.setEnabled(true);
                }
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        } finally {
            ndRequestMSG = null;
            configXPath = null;
            ndOTP = null;
        }
        return otp;
    }

    public MAPPResponse validateOTP(MAPPRequest theMAPPRequest, MAPPAPIConstants.OTP_TYPE theOTPType) {

        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            boolean blAddDataAction = false;

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            String strActivationCode = configXPath.evaluate("OTP", ndRequestMSG).trim();


            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Error";
            String strDescription = "An error occurred. Please try again after a few minutes.";

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            String strStartKey = "";
            strStartKey = (String) InMemoryCache.retrieve(strUsername + strActivationCode);

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = ERROR;

            Element elData = doc.createElement("DATA");

//TODO: To be removed on Live
            //check if ios/playstore testing
            if (strUsername.equals(strIOSTESTUSERNAME)) {
                System.out.println("Validating OTP...IOS");
                strTitle = "OTP Validation Successful";
                strDescription = "Your OTP validation was successful";

                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                String strCharge = "NO";
                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, TEXT);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
                return theMAPPResponse;

            }


            TransactionWrapper<FlexicoreHashMap> otpValidationWrapper = CBSAPI.validateOTP(getTraceID(theMAPPRequest), "MSISDN",
                    strUsername, "APP_ID", strAppID, theOTPType, strActivationCode);

            FlexicoreHashMap otpValidationMap = otpValidationWrapper.getSingleRecord();

            if (otpValidationWrapper.hasErrors()) {
                strTitle = otpValidationMap.getStringValue("title");
                strDescription = otpValidationMap.getStringValue("display_message");
                USSDAPIConstants.Condition endSession = otpValidationMap.getValue("end_session");

                if (endSession == USSDAPIConstants.Condition.YES) {
                    enResponseAction = END;
                } else {
                    enResponseAction = CON;
                }

                enResponseStatus = ERROR;

            } else {

                FlexicoreHashMap mobileBankingMap = otpValidationMap.getFlexicoreHashMap("mobile_register_details");

                String strUserAccountStatus;
                if (theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) {

                    TransactionWrapper<FlexicoreHashMap> activateMobileAppWrapper = CBSAPI.activateMobileApp(getTraceID(theMAPPRequest), "MSISDN", strUsername,
                            "APP_ID", strAppID, mobileBankingMap);

                    if (activateMobileAppWrapper.hasErrors()) {
                        strTitle = "Activation Failed";
                        strDescription = "An error occurred. Please try again after a few minutes.";

                        enResponseAction = END;
                        enResponseStatus = ERROR;
                    } else {
                        strTitle = "Activation Successful";
                        strDescription = "Mobile app account activation was successful";

                        enResponseAction = CON;
                        enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                    }

                } else {
                    strTitle = "OTP Validation Successful";
                    strDescription = "Your OTP validation was successful";

                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                }
            }

            String strCharge = "NO";
            elData.setTextContent(strDescription);

            if (blAddDataAction) {
                elData.setAttribute("ACTION", "REQUEST_OTP");
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse getDividendPayslip(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;

            String strEntryCode = UUID.randomUUID().toString().toUpperCase();

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
            String strMAPPSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());


            String strDateNow = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            String strMemberName = "";

            String strEmailAddress = configXPath.evaluate("EMAIL_ADDRESS", ndRequestMSG).trim();
            String strYear = configXPath.evaluate("YEAR", ndRequestMSG).trim();

            if (strYear == "") {
                //set it to current year -1
                int previousYear = LocalDate.now().getYear() - 1;
                System.out.println("Previous Year: " + previousYear);
                strYear = String.valueOf(previousYear);

            }
            String strfinalYear = strYear;


            String strTraceId = theMAPPRequest.getTraceID();

            String strSessionId = String.valueOf(theMAPPRequest.getSessionID());

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm:ss a");
            LocalDateTime now = LocalDateTime.now();
            String strDate = dtf.format(now);

            String strFullName = getUserFullName(theMAPPRequest, theMAPPRequest.getUsername());
            String strPhoneNumber = theMAPPRequest.getUsername();


            Thread worker = new Thread(() -> {

                //call get Dividend Payslip APi
                TransactionWrapper<FlexicoreHashMap> flexicoreHashMapTransactionWrapper = ApStarCBS.getDividendPayslipReport("MSISDN", theMAPPRequest.getUsername(), strfinalYear, strEmailAddress);
                FlexicoreHashMap fxhashmapStatus = flexicoreHashMapTransactionWrapper.getSingleRecord();
                FlexicoreHashMap getChargesMap = fxhashmapStatus.getFlexicoreHashMap("response_payload");
                String strBase64ReportData = getChargesMap.getStringValue("data");


                String theFileName = saveBase64PDF(strBase64ReportData, strEmailAddress);

                String[] filenameArr = theFileName.split("/");


                HashMap<String, String> attachmentsMap = new HashMap<>();
                attachmentsMap.put(filenameArr[filenameArr.length - 1], theFileName);

//                                strFormattedStartDate = DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "dd MMM yyyy");
//                                strFormattedEndDate = DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "dd MMM yyyy");

                String emailTemplate = AccountStatements.getDividendPayslipHtml();

                emailTemplate = emailTemplate
                        .replace("[YEAR]", strfinalYear)
                        .replace("[FULL_NAME]", strFullName)
                        .replace("[PHONE_NUMBER]", strPhoneNumber);

                EmailMessaging.sendEmail(strEmailAddress, "Dividend Payslip", emailTemplate, "DIVIDEND", attachmentsMap);


            });
            worker.start();


            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.ERROR;

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            strTitle = "Dividend Payslip";
            //strResponseText = strFileBase64;
            enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
            strCharge = "YES";

            Element elData = doc.createElement("DATA");

            elData.setTextContent("Dear member, your request for dividend payslip has been received successfully.<br/>An email has been sent to <b>" + strEmailAddress + "</b>");

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }


    public MAPPResponse getBankAccounts(MAPPRequest theMAPPRequest, MAPPAPIConstants.AccountType theAccountType, String theAction) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

         /*   if (theAccountType.getValue().equals("FOSA")) {
                bFOSA = true;
            }
*/
            //Accounts HashMap
            /*{5-04-00010-02=Salary Acc (5-04-00010-02), 4-61-90010-01=Micro-cred (4-61-90010-01)}*/
            LinkedHashMap<String, String> accounts = getMemberAccountsList(theMAPPRequest, theAccountType);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Member Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            Element elAccounts = doc.createElement("ACCOUNTS");
            elData.appendChild(elAccounts);


            for (String accountNumber : accounts.keySet()) {
                String strAccountName = accounts.get(accountNumber);

                Element elAccount = doc.createElement("ACCOUNT");
                elAccount.setTextContent(strAccountName);
                elAccounts.appendChild(elAccount);

                // set attribute NO to ACCOUNT element
                Attr attrNO = doc.createAttribute("NO");
                attrNO.setValue(accountNumber);
                elAccount.setAttributeNode(attrNO);
            }

            if (theAction.equalsIgnoreCase("GET_TRANSACTION_ACCOUNTS_AND_DEPOSIT_SERVICES")) {
                Element elServices = doc.createElement("SERVICES");
                elData.appendChild(elServices);

                //create element SERVICE and append to element SERVICES
                Element elServiceMpesa = doc.createElement("SERVICE");
                elServiceMpesa.setAttribute("ID", "MPESA");
                elServiceMpesa.setTextContent("Safaricom M-PESA");
                elServices.appendChild(elServiceMpesa);

                String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.DEPOSIT).getMinimum();
                String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.DEPOSIT).getMaximum();

                //create element AMOUNT_LIMITS and append to element DATA
                Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                Element elMinAmount = doc.createElement("MIN_AMOUNT");
                elMinAmount.setTextContent(String.valueOf(strMin));
                Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                elMaxAmount.setTextContent(String.valueOf(strMax));
                elWithdrawalLimits.appendChild(elMinAmount);
                elWithdrawalLimits.appendChild(elMaxAmount);
                elData.appendChild(elWithdrawalLimits);

                Element elToAccountTypes = doc.createElement("TO_ACCOUNT_TYPES");

                Element elAccountTypeMy = doc.createElement("ACCOUNT_TYPE");
                elAccountTypeMy.setTextContent("MY Account");
                elAccountTypeMy.setAttribute("TYPE_ID", "MY_ACCOUNT");
                elToAccountTypes.appendChild(elAccountTypeMy);

                Element elAccountTypeOther = doc.createElement("ACCOUNT_TYPE");
                elAccountTypeOther.setTextContent("OTHER Account");
                elAccountTypeOther.setAttribute("TYPE_ID", "OTHER_ACCOUNT");
                elToAccountTypes.appendChild(elAccountTypeOther);

                elData.appendChild(elToAccountTypes);

            }
            /*Start of Account Statement Duration Changes*/
            else {
                /*Prerequisites*/
                /*Add the following block of xml code to mapp client parameters XML under */
                /*OTHER_DETAILS / CUSTOM_PARAMETERS / SERVICE_CONFIGS*/

                /*<CONFIGURATION>
                    <ACCOUNT_STATEMENT>
                        <STATEMENT_PERIODS>
                            <PERIOD NAME="CUSTOM" LABEL="Custom Period" STATUS="ACTIVE" START_DATE="MONTH_START" END_DATE="MONTH_END" MAXIMUM_TRANSACTIONS="100"/>
                            <PERIOD NAME="1WEEK" LABEL="Past 1 Week" STATUS="ACTIVE" START_DATE="TODAY-7D" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="50"/>
                            <PERIOD NAME="2WEEKS" LABEL="Past 2 Weeks" STATUS="ACTIVE" START_DATE="TODAY-14D" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="75"/>
                            <PERIOD NAME="1MONTHS" LABEL="Past 1 Month" STATUS="ACTIVE" START_DATE="TODAY-30D" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="100"/>
                            <PERIOD NAME="3MONTHS" LABEL="Past 3 Months" STATUS="ACTIVE" START_DATE="TODAY-90D" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="250"/>
                            <PERIOD NAME="6MONTHS" LABEL="Past 6 Months" STATUS="ACTIVE" START_DATE="TODAY-183D" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="500"/>
                            <PERIOD NAME="YTD" LABEL="This Year To Date" STATUS="ACTIVE" START_DATE="TODAY-YTD" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="750"/>
                            <PERIOD NAME="1YEAR" LABEL="Past 1 Year" STATUS="ACTIVE" START_DATE="TODAY-365D" END_DATE="TODAY" MAXIMUM_TRANSACTIONS="1000"/>
                        </STATEMENT_PERIODS>
                    </ACCOUNT_STATEMENT>
                </CONFIGURATION>*/
                Element elStatementConfiguration = doc.createElement("STATEMENT_CONFIGURATION");
                elStatementConfiguration.setAttribute("DEFAULT", "CUSTOM");

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());

                /*Added the function below under APIUtils*/
                LinkedList<HashMap<String, String>> llHmStatementPeriods = APIUtils.getStatementPeriods(MBankingConstants.ApplicationType.MAPP);

                llHmStatementPeriods.forEach(hmStatementPeriods -> {
                    String strName = hmStatementPeriods.get("NAME");
                    String strLabel = hmStatementPeriods.get("LABEL");
                    String strStartDate = hmStatementPeriods.get("START_DATE");
                    String strEndDate = hmStatementPeriods.get("END_DATE");
                    String strMaximumTransactions = hmStatementPeriods.get("MAXIMUM_TRANSACTIONS");

                    long lnEndDate = System.currentTimeMillis();
                    long lnStartDate = lnEndDate;
                    long lnMillisecondsInDay = 86400000;

                    if (strStartDate.matches("(TODAY-)+(\\d{1,})+(D)") && strEndDate.matches("^TODAY$")) {
                        String strDays = "";

                        Pattern ptPattern = Pattern.compile("(?!TODAY)(-)\\d{1,}(?=D)");
                        Matcher mtMatcher = ptPattern.matcher(strStartDate);
                        if (mtMatcher.find()) {
                            strDays = mtMatcher.group();
                            long lnDays = Long.parseLong(strDays);
                            lnStartDate = lnEndDate + (lnDays * lnMillisecondsInDay);
                        }
                    }

                    if (strStartDate.matches("^MONTH_START$") && strEndDate.matches("^MONTH_END$")) {
                        LocalDate ldToday = LocalDate.now();
                        lnStartDate = ldToday.withDayOfMonth(1).toEpochDay() * lnMillisecondsInDay;
                        lnEndDate = ldToday.withDayOfMonth(ldToday.lengthOfMonth()).toEpochDay() * lnMillisecondsInDay;
                    }

                    if (strStartDate.matches("^TODAY-YTD$") && strEndDate.matches("^TODAY$")) {
                        LocalDate ldToday = LocalDate.now();
                        lnStartDate = ldToday.withDayOfYear(1).toEpochDay() * lnMillisecondsInDay;
                        lnEndDate = ldToday.toEpochDay() * lnMillisecondsInDay;
                    }

                    strStartDate = String.valueOf(lnStartDate);
                    strEndDate = String.valueOf(lnEndDate);

                    Element elStatementPeriod = doc.createElement("PERIOD");
                    elStatementPeriod.setAttribute("LABEL", strLabel);
                    elStatementPeriod.setAttribute("NAME", strName);
                    elStatementPeriod.setAttribute("START_DATE", strStartDate);
                    elStatementPeriod.setAttribute("END_DATE", strEndDate);
                    elStatementPeriod.setAttribute("MAXIMUM_TRANSACTIONS", strMaximumTransactions);
                    elStatementConfiguration.appendChild(elStatementPeriod);
                });

                elData.appendChild(elStatementConfiguration);
            }
            /*Start of Account Statement Duration Changes*/

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            System.out.println("RESPONSE\n***************************************\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getWithdrawalAccounts(MAPPRequest theMAPPRequest, MAPPAPIConstants.AccountType theAccountType) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

            if (theAccountType.getValue().equals("FOSA")) {
                bFOSA = true;
            }

            //Accounts HashMap
            /*{Salary Acc (5-04-00010-02)=5-04-00010-02, Micro-cred (4-61-90010-01)=4-61-90010-01}*/
            LinkedHashMap<String, String> accounts = getMemberAccountsList(theMAPPRequest, theAccountType);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Withdrawal Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (accounts != null && !accounts.isEmpty()) {

                Element elAccounts = doc.createElement("ACCOUNTS");
                elData.appendChild(elAccounts);

                for (String accountNumber : accounts.keySet()) {
                    String strAccountName = accounts.get(accountNumber);

                    Element elAccount = doc.createElement("ACCOUNT");
                    elAccount.setTextContent(strAccountName);
                    elAccounts.appendChild(elAccount);

                    // set attribute NO to ACCOUNT element
                    Attr attrNO = doc.createAttribute("NO");
                    attrNO.setValue(accountNumber);
                    elAccount.setAttributeNode(attrNO);
                }


                double dblUtilityETopUplMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.CASH_WITHDRAWAL).getMinimum());
                double dblUtilityETopUplMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.CASH_WITHDRAWAL).getMaximum());

                //create element AMOUNT_LIMITS and append to element DATA
                Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                Element elMinAmount = doc.createElement("MIN_AMOUNT");
                elMinAmount.setTextContent(String.valueOf(dblUtilityETopUplMin));
                Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                elMaxAmount.setTextContent(String.valueOf(dblUtilityETopUplMax));
                elWithdrawalLimits.appendChild(elMinAmount);
                elWithdrawalLimits.appendChild(elMaxAmount);
                elData.appendChild(elWithdrawalLimits);

            } else {

                enResponseStatus = ERROR;
                enDataType = TEXT;
                String strDescription = "Sorry, you don't have any ACTIVE withdrawable accounts to perform the request.";
                elData.setTextContent(strDescription);
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse getPaySlipDetails(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();


            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Dividend PaySlip Years";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");


            int intCurrentYear = LocalDate.now().getYear() - 2;

            String strDisplayYear;
            String strValueYear;

            Element elAccounts = doc.createElement("YEARS");
            elData.appendChild(elAccounts);
            for (int i = 1; i <= 1; i++) {
                strDisplayYear = String.valueOf(intCurrentYear);
                strValueYear = strDisplayYear;
                Element elAccount = doc.createElement("YEAR");
                elAccount.setTextContent(strDisplayYear);
                elAccounts.appendChild(elAccount);

                // set attribute NO to ACCOUNT element
                Attr attrNO = doc.createAttribute("VALUE");
                attrNO.setValue(strValueYear);
                elAccount.setAttributeNode(attrNO);

                intCurrentYear--;
            }
            elData.appendChild(elAccounts);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getWithdrawalAccountsAndMobileMoneyServices(MAPPRequest theMAPPRequest, MAPPAPIConstants.AccountType theAccountType) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;


            //Accounts HashMap
            /*{Salary Acc (5-04-00010-02)=5-04-00010-02, Micro-cred (4-61-90010-01)=4-61-90010-01}*/
            LinkedHashMap<String, String> accounts = getMemberAccountsList(theMAPPRequest, theAccountType);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Withdrawal Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            //create ELEMENT DATA
            Element elData = doc.createElement("DATA");

            if (accounts != null && !accounts.isEmpty()) {

                //ceate element ACCOUNTS_AND_SERVICES and append to DATA
                Element elAccountsAndServices = doc.createElement("ACCOUNTS_AND_SERVICES");
                elData.appendChild(elAccountsAndServices);

                //create element ACCOUNTS and append to element ACCOUNTS_AND_SERVICES
                Element elAccounts = doc.createElement("ACCOUNTS");
                elAccountsAndServices.appendChild(elAccounts);

                //create element SERVICES and append to element ACCOUNTS_AND_SERVICES
                Element elServices = doc.createElement("SERVICES");
                elAccountsAndServices.appendChild(elServices);

                for (String accountNumber : accounts.keySet()) {
                    String strAccountName = accounts.get(accountNumber);

                    Element elAccount = doc.createElement("ACCOUNT");
                    elAccount.setAttribute("NO", accountNumber);
                    elAccount.setTextContent(strAccountName);
                    elAccounts.appendChild(elAccount);
                }

                //create element SERVICE and append to element SERVICES
                Element elServiceMpesa = doc.createElement("SERVICE");
                elServiceMpesa.setAttribute("ID", "MPESA");
                elServiceMpesa.setTextContent("Safaricom M-PESA");
                elServices.appendChild(elServiceMpesa);

                //Airtel Money
                //create element SERVICE and append to element SERVICES
                /*Element elServiceAirtelMoney = doc.createElement("SERVICE");
                elServiceAirtelMoney.setAttribute("ID", "AIRTEL");
                elServiceAirtelMoney.setTextContent("Airtel Money");
                elServices.appendChild(elServiceAirtelMoney);

                //Equitel Money
                //create element SERVICE and append to element SERVICES
                Element elServiceEquitelMoney = doc.createElement("SERVICE");
                elServiceEquitelMoney.setAttribute("ID", "TKASH");
                elServiceEquitelMoney.setTextContent("Telkom T-kash");
                elServices.appendChild(elServiceEquitelMoney);

                //ATM Withdrawal
                //create element SERVICE and append to element SERVICES
                Element elServiceATM = doc.createElement("SERVICE");
                elServiceATM.setAttribute("ID", "SACCO-ATM");
                elServiceATM.setTextContent("AppStar ATM");
                elServices.appendChild(elServiceATM);

                //ATM Withdrawal
                //create element SERVICE and append to element SERVICES
                elServiceATM = doc.createElement("SERVICE");
                elServiceATM.setAttribute("ID", "COOP-ATM");
                elServiceATM.setTextContent("Co-operative Bank ATM");
                elServices.appendChild(elServiceATM);

                //Agent Withdrawal
                //create element SERVICE and append to element SERVICES
                Element elServiceAgent = doc.createElement("SERVICE");
                elServiceAgent.setAttribute("ID", "SACCO-AGENT");
                elServiceAgent.setTextContent("Apstar Agent");
                elServices.appendChild(elServiceAgent);

                //Agent Withdrawal
                //create element SERVICE and append to element SERVICES
                elServiceAgent = doc.createElement("SERVICE");
                elServiceAgent.setAttribute("ID", "COOP-AGENT");
                elServiceAgent.setTextContent("Co-op Kwa Jirani Agent");
                elServices.appendChild(elServiceAgent);*/


                double dblWithdrawalMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.CASH_WITHDRAWAL).getMinimum());
                double dblWithdrawalMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.CASH_WITHDRAWAL).getMaximum());

                //create element AMOUNT_LIMITS and append to element DATA
                Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                Element elMinAmount = doc.createElement("MIN_AMOUNT");
                elMinAmount.setTextContent(String.valueOf(dblWithdrawalMin));
                Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                elMaxAmount.setTextContent(String.valueOf(dblWithdrawalMax));
                elWithdrawalLimits.appendChild(elMinAmount);
                elWithdrawalLimits.appendChild(elMaxAmount);
                elData.appendChild(elWithdrawalLimits);


                Element elOtherNumberSetup = doc.createElement("OTHER_NO_SETUP");
                elOtherNumberSetup.setTextContent("ACTIVE");
                elData.appendChild(elOtherNumberSetup);


            } else {

                enResponseStatus = ERROR;
                enDataType = TEXT;
                String strDescription = "Sorry, you don't have any ACTIVE withdrawable accounts to perform the request.";
                elData.setTextContent(strDescription);

            }
            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);


            System.out.println("\n\nGET WITHDRAWABLE ACCOUNTS: \n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));

            System.out.println("\n");


            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getWithdrawalAccountsAndBanks(MAPPRequest theMAPPRequest, MAPPAPIConstants.AccountType theAccountType) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

            if (theAccountType.getValue().equals("FOSA")) {
                bFOSA = true;
            }

            //Accounts HashMap
            /*{5-04-00010-02=Salary Acc (5-04-00010-02), 4-61-90010-01=Micro-cred (4-61-90010-01)}*/
            LinkedHashMap<String, String> accounts = getMemberAccountsList(theMAPPRequest, theAccountType);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Withdrawal Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            //create ELEMENT DATA
            Element elData = doc.createElement("DATA");


            if (accounts != null && !accounts.isEmpty()) {


                //ceate element ACCOUNTS_AND_SERVICES and append to DATA
                Element elAccountsAndServices = doc.createElement("ACCOUNTS_AND_BANKS");
                elData.appendChild(elAccountsAndServices);

                //create element ACCOUNTS and append to element ACCOUNTS_AND_SERVICES
                Element elAccounts = doc.createElement("ACCOUNTS");
                elAccountsAndServices.appendChild(elAccounts);

                //create element SERVICES and append to element ACCOUNTS_AND_SERVICES
                Element elBanks = doc.createElement("BANKS");
                elAccountsAndServices.appendChild(elBanks);


                for (String accountNumber : accounts.keySet()) {
                    String strAccountName = accounts.get(accountNumber);

                    Element elAccount = doc.createElement("ACCOUNT");
                    elAccount.setAttribute("NO", accountNumber);
                    elAccount.setTextContent(strAccountName);
                    elAccounts.appendChild(elAccount);
                }

                LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.BANK_SHORT_CODE);
                for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                    Element elBank2 = doc.createElement("BANK");
                    elBank2.setAttribute("PAYBILL_NO", serviceProviderAccount.getProviderAccountIdentifier());
                    elBank2.setTextContent(serviceProviderAccount.getProviderAccountLongTag());
                    elBanks.appendChild(elBank2);
                }

                String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                SPManager spManager = new SPManager(strIntegritySecret);
                String strAccounts = spManager.getAllUserAccountsByProviders(SPManagerConstants.ProviderAccountType.BANK_SHORT_CODE, SPManagerConstants.UserIdentifierType.MSISDN, strUsername);
                strAccounts = strAccounts.replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
                strAccounts = trimXML(strAccounts);

                if (!strAccounts.equals("<ACCOUNTS/>")) {
                    InputSource sourceForPaybillAccounts = new InputSource(new StringReader(strAccounts));
                    DocumentBuilderFactory builderFactoryForPaybillAccounts = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builderForPaybillAccounts = builderFactoryForPaybillAccounts.newDocumentBuilder();
                    Document xmlDocumentForPaybillAccounts = builderForPaybillAccounts.parse(sourceForPaybillAccounts);
                    XPath configXPathForPaybillAccounts = XPathFactory.newInstance().newXPath();

                    NodeList nlPayBillAccounts = ((NodeList) configXPathForPaybillAccounts.evaluate("/ACCOUNTS/ACCOUNT", xmlDocumentForPaybillAccounts, XPathConstants.NODESET));

                    Element elAccountsForPaybill = doc.createElement("ACCOUNTS_FOR_PAYBILL");
                    for (int i = 0; i < nlPayBillAccounts.getLength(); i++) {
                        Element elSingleAccountsForPaybill = doc.createElement("PAYBILL_ACCOUNT");
                        elSingleAccountsForPaybill.setAttribute("NAME", nlPayBillAccounts.item(i).getAttributes().getNamedItem("NAME").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("NUMBER", nlPayBillAccounts.item(i).getAttributes().getNamedItem("NUMBER").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("TYPE", nlPayBillAccounts.item(i).getAttributes().getNamedItem("PROVIDER_ACCOUNT_IDENTIFIER").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("PROVIDER_ACCOUNT_CODE", nlPayBillAccounts.item(i).getAttributes().getNamedItem("PROVIDER_ACCOUNT_CODE").getTextContent());
                        elAccountsForPaybill.appendChild(elSingleAccountsForPaybill);
                    }
                    elAccountsAndServices.appendChild(elAccountsForPaybill);
                }

                String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                //create element AMOUNT_LIMITS and append to element DATA
                Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                Element elMinAmount = doc.createElement("MIN_AMOUNT");
                elMinAmount.setTextContent(String.valueOf(strMin));
                Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                elMaxAmount.setTextContent(String.valueOf(strMax));
                elWithdrawalLimits.appendChild(elMinAmount);
                elWithdrawalLimits.appendChild(elMaxAmount);
                elData.appendChild(elWithdrawalLimits);

            } else {

                enResponseStatus = ERROR;
                enDataType = TEXT;
                String strDescription = "Sorry, you don't have any ACTIVE withdrawable accounts to perform the request.";
                elData.setTextContent(strDescription);

            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);


            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getPesalinkAccountsAndBanks(MAPPRequest theMAPPRequest, MAPPAPIConstants.AccountType theAccountType) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

            if (theAccountType.getValue().equals("FOSA")) {
                bFOSA = true;
            }

            //Accounts HashMap
            /*{5-04-00010-02=Salary Acc (5-04-00010-02), 4-61-90010-01=Micro-cred (4-61-90010-01)}*/
            LinkedHashMap<String, String> accounts = getMemberAccountsList(theMAPPRequest, theAccountType);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Withdrawal Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            //create ELEMENT DATA
            Element elData = doc.createElement("DATA");


            if (accounts != null && !accounts.isEmpty()) {


                //ceate element ACCOUNTS_AND_SERVICES and append to DATA
                Element elAccountsAndServices = doc.createElement("ACCOUNTS_AND_BANKS");
                elData.appendChild(elAccountsAndServices);

                //create element ACCOUNTS and append to element ACCOUNTS_AND_SERVICES
                Element elAccounts = doc.createElement("ACCOUNTS");
                elAccountsAndServices.appendChild(elAccounts);

                //create element SERVICES and append to element ACCOUNTS_AND_SERVICES
                Element elBanks = doc.createElement("BANKS");
                elAccountsAndServices.appendChild(elBanks);


                for (String accountNumber : accounts.keySet()) {
                    String strAccountName = accounts.get(accountNumber);

                    Element elAccount = doc.createElement("ACCOUNT");
                    elAccount.setAttribute("NO", accountNumber);
                    elAccount.setTextContent(strAccountName);
                    elAccounts.appendChild(elAccount);
                }

                LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.BANK_CODE);
                for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                    Element elBank2 = doc.createElement("BANK");
                    elBank2.setAttribute("PAYBILL_NO", serviceProviderAccount.getProviderAccountIdentifier());
                    elBank2.setTextContent(serviceProviderAccount.getProviderAccountLongTag());
                    elBanks.appendChild(elBank2);
                }

                String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                SPManager spManager = new SPManager(strIntegritySecret);
                String strAccounts = spManager.getAllUserAccountsByProviders(SPManagerConstants.ProviderAccountType.BANK_SHORT_CODE, SPManagerConstants.UserIdentifierType.MSISDN, strUsername);
                strAccounts = strAccounts.replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
                strAccounts = trimXML(strAccounts);

                if (!strAccounts.equals("<ACCOUNTS/>")) {
                    InputSource sourceForPaybillAccounts = new InputSource(new StringReader(strAccounts));
                    DocumentBuilderFactory builderFactoryForPaybillAccounts = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builderForPaybillAccounts = builderFactoryForPaybillAccounts.newDocumentBuilder();
                    Document xmlDocumentForPaybillAccounts = builderForPaybillAccounts.parse(sourceForPaybillAccounts);
                    XPath configXPathForPaybillAccounts = XPathFactory.newInstance().newXPath();

                    NodeList nlPayBillAccounts = ((NodeList) configXPathForPaybillAccounts.evaluate("/ACCOUNTS/ACCOUNT", xmlDocumentForPaybillAccounts, XPathConstants.NODESET));

                    Element elAccountsForPaybill = doc.createElement("ACCOUNTS_FOR_PAYBILL");
                    for (int i = 0; i < nlPayBillAccounts.getLength(); i++) {
                        Element elSingleAccountsForPaybill = doc.createElement("PAYBILL_ACCOUNT");
                        elSingleAccountsForPaybill.setAttribute("NAME", nlPayBillAccounts.item(i).getAttributes().getNamedItem("NAME").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("NUMBER", nlPayBillAccounts.item(i).getAttributes().getNamedItem("NUMBER").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("TYPE", nlPayBillAccounts.item(i).getAttributes().getNamedItem("PROVIDER_ACCOUNT_IDENTIFIER").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("PROVIDER_ACCOUNT_CODE", nlPayBillAccounts.item(i).getAttributes().getNamedItem("PROVIDER_ACCOUNT_CODE").getTextContent());

                        elAccountsForPaybill.appendChild(elSingleAccountsForPaybill);
                    }
                    elAccountsAndServices.appendChild(elAccountsForPaybill);
                }

                String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                //create element AMOUNT_LIMITS and append to element DATA
                Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                Element elMinAmount = doc.createElement("MIN_AMOUNT");
                elMinAmount.setTextContent(String.valueOf(strMin));
                Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                elMaxAmount.setTextContent(String.valueOf(strMax));
                elWithdrawalLimits.appendChild(elMinAmount);
                elWithdrawalLimits.appendChild(elMaxAmount);
                elData.appendChild(elWithdrawalLimits);

            } else {

                enResponseStatus = ERROR;
                enDataType = TEXT;
                String strDescription = "Sorry, you don't have any ACTIVE withdrawable accounts to perform the request.";
                elData.setTextContent(strDescription);

            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);


            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse getWithdrawalAccountsAndPaybillServices(MAPPRequest theMAPPRequest, MAPPAPIConstants.AccountType theAccountType) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

            if (theAccountType.getValue().equals("FOSA")) {
                bFOSA = true;
            }

            //Accounts HashMap
            /*{Salary Acc (5-04-00010-02)=5-04-00010-02, Micro-cred (4-61-90010-01)=4-61-90010-01}*/
            LinkedHashMap<String, String> accounts = getMemberAccountsList(theMAPPRequest, theAccountType);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Withdrawal Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            //create ELEMENT DATA
            Element elData = doc.createElement("DATA");

            if (accounts != null && !accounts.isEmpty()) {

                //ceate element ACCOUNTS_AND_SERVICES and append to DATA
                Element elAccountsAndServices = doc.createElement("ACCOUNTS_AND_PAYBILL_SERVICES");
                elData.appendChild(elAccountsAndServices);

                //create element ACCOUNTS and append to element ACCOUNTS_AND_SERVICES
                Element elAccounts = doc.createElement("ACCOUNTS");
                elAccountsAndServices.appendChild(elAccounts);

                //create element SERVICES and append to element ACCOUNTS_AND_SERVICES
                Element elServices = doc.createElement("PAYBILL_SERVICES");
                elAccountsAndServices.appendChild(elServices);

                for (String accountNumber : accounts.keySet()) {
                    String strAccountName = accounts.get(accountNumber);

                    Element elAccount = doc.createElement("ACCOUNT");
                    elAccount.setAttribute("NO", accountNumber);
                    elAccount.setTextContent(strAccountName);
                    elAccounts.appendChild(elAccount);
                }

                //create element SERVICE and append to element SERVICES
                LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.UTILITY_CODE);
                Element elService;
                for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                    elService = doc.createElement("SERVICE");
                    elService.setAttribute("PAYBILL_NO", serviceProviderAccount.getProviderAccountIdentifier());
                    elService.setAttribute("REF_NAME", serviceProviderAccount.getProviderAccountTypeTag());
                    elService.setTextContent(serviceProviderAccount.getProviderAccountName());
                    elServices.appendChild(elService);
                }

                String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                SPManager spManager = new SPManager(strIntegritySecret);
                String strAccounts = spManager.getAllUserAccountsByProviders(SPManagerConstants.ProviderAccountType.UTILITY_CODE, SPManagerConstants.UserIdentifierType.MSISDN, strUsername);
                strAccounts = strAccounts.replaceAll("\\<\\?xml(.+?)\\?\\>", "").trim();
                strAccounts = trimXML(strAccounts);

                if (!strAccounts.equals("<ACCOUNTS/>")) {
                    InputSource sourceForPaybillAccounts = new InputSource(new StringReader(strAccounts));
                    DocumentBuilderFactory builderFactoryForPaybillAccounts = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builderForPaybillAccounts = builderFactoryForPaybillAccounts.newDocumentBuilder();
                    Document xmlDocumentForPaybillAccounts = builderForPaybillAccounts.parse(sourceForPaybillAccounts);
                    XPath configXPathForPaybillAccounts = XPathFactory.newInstance().newXPath();

                    NodeList nlPayBillAccounts = ((NodeList) configXPathForPaybillAccounts.evaluate("/ACCOUNTS/ACCOUNT", xmlDocumentForPaybillAccounts, XPathConstants.NODESET));

                    Element elAccountsForPaybill = doc.createElement("ACCOUNTS_FOR_PAYBILL");
                    for (int i = 0; i < nlPayBillAccounts.getLength(); i++) {
                        Element elSingleAccountsForPaybill = doc.createElement("PAYBILL_ACCOUNT");
                        elSingleAccountsForPaybill.setAttribute("NAME", nlPayBillAccounts.item(i).getAttributes().getNamedItem("NAME").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("NUMBER", nlPayBillAccounts.item(i).getAttributes().getNamedItem("NUMBER").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("TYPE", nlPayBillAccounts.item(i).getAttributes().getNamedItem("PROVIDER_ACCOUNT_IDENTIFIER").getTextContent());
                        elSingleAccountsForPaybill.setAttribute("PROVIDER_ACCOUNT_CODE", nlPayBillAccounts.item(i).getAttributes().getNamedItem("PROVIDER_ACCOUNT_CODE").getTextContent());
                        elAccountsForPaybill.appendChild(elSingleAccountsForPaybill);
                    }
                    elAccountsAndServices.appendChild(elAccountsForPaybill);
                }


                String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.PAY_BILL).getMinimum();
                String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.PAY_BILL).getMaximum();

                //create element AMOUNT_LIMITS and append to element DATA
                Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                Element elMinAmount = doc.createElement("MIN_AMOUNT");
                elMinAmount.setTextContent(String.valueOf(strMin));
                Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                elMaxAmount.setTextContent(String.valueOf(strMax));
                elWithdrawalLimits.appendChild(elMinAmount);
                elWithdrawalLimits.appendChild(elMaxAmount);
                elData.appendChild(elWithdrawalLimits);

            } else {

                enResponseStatus = ERROR;
                enDataType = TEXT;
                String strDescription = "Sorry, you don't have any ACTIVE withdrawable accounts to perform the request.";
                elData.setTextContent(strDescription);

            }


            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse validateAccountNumber(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("validateAccountNumber");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strAccountNo = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();
            //String strAccountNo = "5-22-002201";

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            strTitle = "";
            strResponseText = "";
            strCharge = "YES";
            enResponseAction = CON;
            enResponseStatus = ERROR;

            TransactionWrapper<FlexicoreHashMap> validateAccountNumberWrapper = CBSAPI.validateAccountNumber(
                    strUsername,
                    "MSISDN",
                    strUsername,
                    strAccountNo);

            if (validateAccountNumberWrapper.hasErrors()) {
                enResponseStatus = ERROR;

                strTitle = "Error Occurred";
                strResponseText = "Sorry, an error occurred while processing your request";

            } else {

                FlexicoreHashMap accountDetailsResultMap = validateAccountNumberWrapper.getSingleRecord();
                String requestStatus = accountDetailsResultMap.getStringValue("request_status");
                FlexicoreHashMap accountDetailsResponseMap = accountDetailsResultMap.getFlexicoreHashMap("response_payload");
                String canDeposit = accountDetailsResponseMap.getStringValue("can_deposit");

                if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
                    enResponseStatus = ERROR;

                    strTitle = "Account Not Found";
                    strResponseText = "Sorry, account " + strAccountNo + " not found";

                } else if (!canDeposit.equalsIgnoreCase("YES")) {
                    enResponseStatus = ERROR;

                    strTitle = "Invalid Account";
                    strResponseText = "Sorry, account " + strAccountNo + " does not allow deposits";
                } else {

                    FlexicoreHashMap memberDetailsMap = accountDetailsResponseMap.getFlexicoreHashMap("member_details");

                    String fullName = memberDetailsMap.getStringValueOrIfNull("full_name", "");
                    String[] fullNameArr = fullName.split(" ");
                    fullName = fullNameArr[0];

                    String strMobileNumber = memberDetailsMap.getStringValueOrIfNull("mobile_number", "");

                    strMobileNumber = AppUtils.maskPhoneNumber(strMobileNumber);

                    enResponseStatus = SUCCESS;

                    strTitle = "Account Found";
                    strResponseText = "Name: " + fullName + " - Mobile: " + strMobileNumber;
                }
            }


            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            /*System.out.println("THE RESPONSE\n");
            System.out.println("---------------------------------------");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG)+"\n");*/


            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse mobileMoneyWithdrawal(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {

                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = String.valueOf(theMAPPRequest.getAppID());

                long lnSessionID = theMAPPRequest.getSessionID();

                String strTraceID = getTraceID(theMAPPRequest);

                String strMAPPSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;

                String strMemberName = getUserFullName(theMAPPRequest, strUsername).trim();

                String strSourceAccount = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();
                String strServiceId = configXPath.evaluate("SERVICE_ID", ndRequestMSG).trim();

                String[] strSourceArr = strSourceAccount.split(Pattern.quote("||"));

                strSourceAccount = strSourceArr[0];
                String strSourceAccountName = strMemberName;
                if (strSourceArr.length > 1) {
                    strSourceAccountName = strSourceArr[1];
                }

                String strRecipientMobileNumber = configXPath.evaluate("MOBILE_NO", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
                String strAgentNumber = configXPath.evaluate("AGENT_NUMBER", ndRequestMSG).trim();
                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                String strReceiverName = strMemberName;
                if (strRecipientMobileNumber.equalsIgnoreCase(strUsername)) {
                    strReceiverName = strMemberName.trim();
                } else {
                    strReceiverName = strRecipientMobileNumber.trim();
                }

                MAPPConstants.ResponseStatus enResponseStatus = ERROR;
                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                strRecipientMobileNumber = APIUtils.sanitizePhoneNumber(strRecipientMobileNumber);

                /*if (strRecipientMobileNumber.equalsIgnoreCase("INVALID_MOBILE_NUMBER")) {
                    strTitle = "ERROR: Withdrawal Failed";
                    strResponseText = "The format of the mobile number you entered is invalid (" + strEnteredMobileNumber + ")</br>Please use the format 07XX XXX XXX";
                    enResponseAction = MAPPConstants.ResponseAction.CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                } else {

                }*/

                MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccount, RegisterConstants.MemberRegisterType.BLACKLIST);

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strDatetime = MBankingDB.getDBDateTime().trim();
                String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
                strFormattedDateTime = DateTime.convertStringToDateToString(DateTime.getCurrentDateTime(), "yyyy-MM-dd HH:mm:ss.SSSSSS", "dd-MMM-yy' at 'hh:mm aaa");

                // Services:
                // MPESA
                // AIRTEL
                // TKASH
                // SACCO-ATM
                // COOP-ATM
                // SACCO-AGENT
                // COOP-AGENT

                String strServiceName = "";

                if (strServiceId.equalsIgnoreCase("MPESA")) {
                    if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                        strTitle = "ERROR: Cash Withdrawal";
                        strResponseText = "Sorry, an error occurred while processing your request.\n\nERR_ACCBL300";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;

                    } else {

                        double dblWithdrawalMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.CASH_WITHDRAWAL).getMinimum());
                        double dblWithdrawalMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.CASH_WITHDRAWAL).getMaximum());

                        double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccount, "MPESA_WITHDRAWAL");

                        dblWithdrawalMax = Math.min(dblWithdrawalMax, dblDailyLimitRemainingAmount);

                        if (!strAmount.matches("^[1-9][0-9]*$")) {
                            strTitle = "ERROR: Cash Withdrawal";
                            strResponseText = "Please enter a valid amount for withdrawal";
                            enResponseAction = CON;
                            enResponseStatus = ERROR;
                        } else if (Double.parseDouble(strAmount) < dblWithdrawalMin) {
                            strTitle = "ERROR: Cash Withdrawal";
                            strResponseText = "Minimum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMin), "#,##0.00");
                            enResponseAction = CON;
                            enResponseStatus = ERROR;
                        } else if (dblDailyLimitRemainingAmount <= 0) {
                            strTitle = "ERROR: Cash Withdrawal";
                            strResponseText = "Sorry, the remaining amount you can transact today is KES 0.";
                            enResponseAction = CON;
                            enResponseStatus = ERROR;
                        } else if (Double.parseDouble(strAmount) > dblWithdrawalMax) {
                            strTitle = "ERROR: Cash Withdrawal";
                            strResponseText = "Maximum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMax), "#,##0.00");
                            enResponseAction = CON;
                            enResponseStatus = ERROR;
                        } else {
                            PESA pesa = new PESA();
                            //String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();

                            String strTransaction = "Withdrawal Request";

                            String strTransactionDescription = "CW|M-Pesa|" + strUsername + " - " + strMemberName;
                            strTransactionDescription = PESAAPI.shortenName(strTransactionDescription).trim();


                            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2C);

                            long getProductID = Long.parseLong(pesaParam.getProductId());

                            int intPriority = 200;
                            String strCategory = "MPESA_WITHDRAWAL";
                            String strAPICategory = "MPESA_WITHDRAWAL";

                            String strSenderIdentifier = pesaParam.getSenderIdentifier();
                            String strSenderAccount = pesaParam.getSenderAccount();
                            String strSenderName = pesaParam.getSenderName();

                            // String strOriginatorID = strSenderIdentifier+"-"+UUID.randomUUID().toString().toUpperCase();
                            String strOriginatorID = UUID.randomUUID().toString();

                            pesa.setOriginatorID(strOriginatorID);
                            pesa.setProductID(getProductID);

                            pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
                            pesa.setPESAAction(PESAConstants.PESAAction.B2C);
                            pesa.setCommand("BusinessPayment");
                            pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);

                            pesa.setPESAStatusCode(10);
                            pesa.setPESAStatusName("QUEUED");
                            pesa.setPESAStatusDescription("New PESA");
                            pesa.setPESAStatusDate(strDatetime);

                            pesa.setInitiatorType("MSISDN");
                            pesa.setInitiatorIdentifier(strUsername);
                            pesa.setInitiatorAccount(strUsername);
                            pesa.setInitiatorName(strMemberName);
                            pesa.setInitiatorReference(strTraceID);
                            pesa.setInitiatorApplication("MAPP");
                            pesa.setInitiatorOtherDetails("<DATA/>");

                            pesa.setSourceType("ACCOUNT_NO");
                            pesa.setSourceIdentifier(strSourceAccount);
                            pesa.setSourceAccount(strSourceAccount);
                            pesa.setSourceName(strSourceAccountName);
                            //deferred to below
                            //pesa.setSourceReference("987654321");
                            pesa.setSourceApplication("CBS");
                            pesa.setSourceOtherDetails("<DATA/>");

                            pesa.setSenderType("SHORT_CODE");
                            pesa.setSenderIdentifier(strSenderIdentifier);
                            pesa.setSenderAccount(strSenderAccount);
                            pesa.setSenderName(strSenderName);
                            pesa.setSenderOtherDetails("<DATA/>");

                            pesa.setReceiverType("MSISDN");
                            pesa.setReceiverIdentifier(strRecipientMobileNumber);
                            pesa.setReceiverAccount(strRecipientMobileNumber);
                            pesa.setReceiverName(strReceiverName);
                            pesa.setReceiverOtherDetails("<DATA/>");

                            pesa.setBeneficiaryType("MSISDN");
                            pesa.setBeneficiaryIdentifier(strRecipientMobileNumber);
                            pesa.setBeneficiaryAccount(strRecipientMobileNumber);
                            pesa.setBeneficiaryName(strReceiverName);
                            pesa.setBeneficiaryOtherDetails("<DATA/>");

                            pesa.setBatchReference(strOriginatorID);
                            pesa.setCorrelationReference(strTraceID);
                            pesa.setCorrelationApplication("MAPP");
                            pesa.setTransactionCurrency("KES");
                            pesa.setTransactionAmount(Double.parseDouble(strAmount));
                            pesa.setTransactionRemark(strTransactionDescription);
                            pesa.setCategory(strCategory);

                            pesa.setPriority(200);
                            pesa.setSendCount(0);

                            pesa.setSchedulePesa(PESAConstants.Condition.NO);
                            pesa.setPesaDateScheduled(strDatetime);
                            pesa.setPesaDateCreated(strDatetime);
                            pesa.setPESAXMLData("<DATA/>");

                        /*TransactionWrapper<FlexicoreHashMap> mobileMoneyWithdrawalWrapper = CBSAPI.mobileMoneyWithdrawal(strOriginatorID, "MSISDN", strUsername,
                                "APP_ID", strAppID, strSourceAccount, strRecipientMobileNumber, strAmount);*/

                            TransactionWrapper<FlexicoreHashMap> mobileMoneyWithdrawalWrapper = CBSAPI.mobileMoneyWithdrawal(
                                    strUsername,
                                    "MSISDN",
                                    strUsername,
                                    "APP_ID",
                                    strAppID,
                                    pesa.getOriginatorID(),
                                    String.valueOf(pesa.getProductID()),
                                    pesa.getPESAType().getValue(),
                                    pesa.getPESAAction().getValue(),
                                    pesa.getCommand(),
                                    new FlexicoreHashMap()
                                            .putValue("identifier_type", pesa.getInitiatorType())
                                            .putValue("identifier", pesa.getInitiatorIdentifier())
                                            .putValue("account", pesa.getInitiatorAccount())
                                            .putValue("name", pesa.getInitiatorName())
                                            .putValue("reference", pesa.getInitiatorReference())
                                            .putValue("other_details", pesa.getInitiatorOtherDetails()),

                                    new FlexicoreHashMap()
                                            .putValue("identifier_type", pesa.getSourceType())
                                            .putValue("identifier", pesa.getSourceIdentifier())
                                            .putValue("account", pesa.getSourceAccount())
                                            .putValue("name", pesa.getSourceName())
                                            .putValue("reference", pesa.getSourceReference())
                                            .putValue("other_details", pesa.getSourceOtherDetails()),

                                    new FlexicoreHashMap()
                                            .putValue("identifier_type", pesa.getSenderType())
                                            .putValue("identifier", pesa.getSenderIdentifier())
                                            .putValue("account", pesa.getSenderAccount())
                                            .putValue("name", pesa.getSenderName())
                                            .putValue("reference", pesa.getSenderReference())
                                            .putValue("other_details", pesa.getSenderOtherDetails()),

                                    new FlexicoreHashMap()
                                            .putValue("identifier_type", pesa.getReceiverType())
                                            .putValue("identifier", pesa.getReceiverIdentifier())
                                            .putValue("account", pesa.getReceiverAccount())
                                            .putValue("name", pesa.getReceiverName())
                                            .putValue("reference", pesa.getReceiverReference())
                                            .putValue("other_details", pesa.getReceiverOtherDetails()),

                                    new FlexicoreHashMap()
                                            .putValue("identifier_type", pesa.getBeneficiaryType())
                                            .putValue("identifier", pesa.getBeneficiaryIdentifier())
                                            .putValue("account", pesa.getBeneficiaryAccount())
                                            .putValue("name", pesa.getBeneficiaryName())
                                            .putValue("reference", pesa.getBeneficiaryReference())
                                            .putValue("other_details", pesa.getBeneficiaryOtherDetails()),

                                    pesa.getTransactionAmount(),
                                    strCategory,
                                    pesa.getTransactionRemark(),
                                    strTraceID,
                                    "MAPP",
                                    "MBANKING");

                            FlexicoreHashMap mobileMoneyWithdrawalMap = mobileMoneyWithdrawalWrapper.getSingleRecord();

                            CBSAPI.SMSMSG cbsMSG = mobileMoneyWithdrawalMap.getValue("msg_object");

                            if (mobileMoneyWithdrawalWrapper.hasErrors()) {
                                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theMAPPRequest);
                                strTitle = "ERROR: Withdrawal Failed";
                                strResponseText = mobileMoneyWithdrawalMap.getStringValue("display_message");
                                enResponseStatus = FAILED;
                                enResponseAction = MAPPConstants.ResponseAction.CON;
                            } else {


                                String strSourceReference = mobileMoneyWithdrawalMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                                pesa.setSourceReference(strSourceReference);

                                String strMSG = "";

                                strAmount = Utils.formatAmount(strAmount);
                                if (PESAProcessor.sendPESA(pesa) > 0) {
                                    //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

                                /*strMSG = "Dear member, your M-PESA Withdrawal request of KES " + strAmount + " to " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been received successfully. Kindly wait as it is being processed.";
                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);*/

                                    strCharge = "YES";
                                    strTitle = "Request for Withdrawal";
                                    strResponseText = "Your request to withdraw <b>KES " + strAmount + "</b> has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                    enResponseAction = CON;

                                } else {

                                    String strRefKey = UUID.randomUUID().toString();

                                    TransactionWrapper<FlexicoreHashMap> reversalCashWithdrawalWrapper =

                                            CBSAPI.reverseMobileMoneyWithdrawal(
                                                    strUsername,
                                                    "MSISDN",
                                                    strUsername,
                                                    pesa.getOriginatorID(),
                                                    pesa.getBeneficiaryType(),
                                                    pesa.getBeneficiaryIdentifier(),
                                                    pesa.getBeneficiaryName(),
                                                    pesa.getBeneficiaryOtherDetails(),
                                                    "",
                                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                                    if (!reversalCashWithdrawalWrapper.hasErrors()) {
                                        strMSG = "Dear member, your M-PESA Withdrawal of KES " + strFormattedAmount + " to " + strRecipientMobileNumber + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                                    } else {
                                        strMSG = "Dear member, your M-PESA Withdrawal of KES " + strFormattedAmount + " to " + strRecipientMobileNumber + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                                    }

                                    sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);

                                    // sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                                    enResponseStatus = FAILED;
                                    enResponseAction = CON;
                                }
                            }
                        }
                    }
                } else if (strServiceId.equalsIgnoreCase("AIRTEL") || strServiceId.equalsIgnoreCase("TKASH")) {
                    if (strServiceId.equalsIgnoreCase("AIRTEL")) {
                        strServiceName = "Airtel Money";
                    } else if (strServiceId.equalsIgnoreCase("TKASH")) {
                        strServiceName = "Telkom T-kash";
                    }

                    String strMSG = "Dear member, your request to withdraw KES " + strAmount + " via " + strServiceName + " on " + strFormattedDateTime + " has been received successfully. Kindly wait as it is being processed.";
                    sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, "CASH_WITHDRAWAL", theMAPPRequest);

                    strCharge = "YES";
                    strTitle = "Request for Withdrawal via ATM";
                    strResponseText = "Your request to withdraw <b>KES " + strAmount + "</b> via <b>" + strServiceName + "</b> has been received successfully.<br/>Kindly wait shortly as it is being processed";

                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                } else if (strServiceId.equalsIgnoreCase("SACCO-ATM") || strServiceId.equalsIgnoreCase("COOP-ATM") || strServiceId.equalsIgnoreCase("SACCO-AGENT") || strServiceId.equalsIgnoreCase("COOP-AGENT")) {
                    if (strServiceId.equalsIgnoreCase("SACCO-ATM")) {
                        strServiceName = "AppStar ATM";
                    } else if (strServiceId.equalsIgnoreCase("COOP-ATM")) {
                        strServiceName = "Co-operative Bank ATM";
                    } else if (strServiceId.equalsIgnoreCase("SACCO-AGENT")) {
                        strServiceName = "AppStar Agent";
                    } else if (strServiceId.equalsIgnoreCase("COOP-AGENT")) {
                        strServiceName = "Co-op Kwa Jirani Agent";
                    }

                    int intTTL = 300;
                    int intOTPLength = 6;

                    if (strServiceId.equalsIgnoreCase("SACCO-ATM") || strServiceId.equalsIgnoreCase("COOP-ATM")) {
                        intOTPLength = 9;
                    }

                    String strOneTImePIN = Utils.generateRandomString(intOTPLength);

                    InMemoryCache.remove(strUsername + strOneTImePIN);
                    InMemoryCache.store(strUsername + strOneTImePIN, strOneTImePIN, intTTL);


                    SimpleDateFormat sdSimpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
                    Timestamp tsCurrentTimestamp = new Timestamp(System.currentTimeMillis());
                    Timestamp tsCurrentTimestampPlusTime = new Timestamp(System.currentTimeMillis() + (intTTL * 1000));

                    String strTimeGenerated = sdSimpleDateFormat.format(tsCurrentTimestamp);
                    String strExpiryDate = sdSimpleDateFormat.format(tsCurrentTimestampPlusTime);

                    String strMSG = "Dear member, your request to withdraw KES " + strAmount + " via " + strServiceName +
                            ((strServiceId.equalsIgnoreCase("SACCO-AGENT") || strServiceId.equalsIgnoreCase("COOP-AGENT")) ? (" No. " + strAgentNumber) : "") +
                            " has been received successfully.\n" +
                            "Your Transaction Authentication Number (TAN) is " + strOneTImePIN + ".\n" +
                            "This TAN is valid up to " + strExpiryDate + ".\n" +
                            ((strServiceId.equalsIgnoreCase("SACCO-AGENT") || strServiceId.equalsIgnoreCase("COOP-AGENT")) ? ("Kindly provide this TAN to the " + strServiceName + ".\n") : ("Kindly use this TAN on the ATM.\n")) +
                            "\n" +
                            "Date Generated: " + strTimeGenerated + "\n" +
                            "Ref: " + strMAPPSessionId;
                    sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, "CASH_WITHDRAWAL", theMAPPRequest);

                    strCharge = "YES";
                    strTitle = "Request for Withdrawal via Agent";
                    strResponseText = "Your request to withdraw <b>KES " + strAmount + "</b> via <b>" + strServiceName + "</b> has been received successfully.<br/>Kindly wait shortly as it is being processed";

                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                }

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getCharges(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = String.valueOf(theMAPPRequest.getAppID());

            long lnSessionID = theMAPPRequest.getSessionID();

            String strTraceID = getTraceID(theMAPPRequest);

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document doc = docBuilder.newDocument();
            String strChargeAction = configXPath.evaluate("CHARGE_ACTION", ndRequestMSG).trim();

            System.out.println("strChargeAction: " + strChargeAction);


            switch (strChargeAction) {
                case "Mpesa Withdrawal": {
                    strChargeAction = "MPESA_WITHDRAWAL";
                    break;
                }
                case "Mpesa Deposit": {
                    strChargeAction = "DEPOSIT";
                    break;
                }
                case "Utility Payment": {
                    strChargeAction = "BILL_PAYMENT";
                    break;
                }
                case "Loan Repayment": {
                    strChargeAction = "DEPOSIT";
                    break;
                }
                case "Balance Enquiry": {
                    strChargeAction = "SINGLE_ACCOUNT_BALANCE_ENQUIRY";
                    break;
                }
                case "Mini-Statement": {
                    strChargeAction = "ACCOUNT_FULL_STATEMENT";
                    break;
                }
                case "Loan Disbursement": {
                    strChargeAction = "LOAN_APPLICATION";
                    break;
                }
                case "Loan Application": {
                    strChargeAction = "LOAN_APPLICATION";
                    break;
                }
                case "Account Transfer": {
                    strChargeAction = "IFT_ACCOUNT_TO_ACCOUNT";
                    break;
                }

                case "Pay Loan From Account": {
                    strChargeAction = "IFT_LOAN_REPAYMENT";
                    break;
                }
                case "Paybill": {
                    strChargeAction = "BILL_PAYMENT";
                    break;
                }
                case "Mobile App Login": {
                    strChargeAction = "DEPOSIT";
                    break;
                }
                case "Pesalink Transfer": {
                    strChargeAction = "BANK_TRANSFER";
                    break;
                }

                case "Airtime": {
                    strChargeAction = "AIRTIME_PURCHASE";
                    break;
                }
                case "Bank Deposit": {
                    strChargeAction = "DEPOSIT";
                    break;
                }
                case "Bank Agent Deposit": {
                    strChargeAction = "DEPOSIT";
                    break;
                }
                case "Bank Transfer": {
                    strChargeAction = "BANK_TRANSFER";
                    break;
                }
                case "ATM Withdrawal": {
                    strChargeAction = "DEPOSIT";
                    break;
                }


            }


            System.out.println("strChargeAction 2: " + strChargeAction);


            String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
            strAmount = strAmount.replace(",", "");
            Double dblAmount = Double.parseDouble(strAmount);

            System.out.println("strAmount: " + strAmount + " dblAmount: " + dblAmount);

            String strTitle = "";
            String strResponseText = "";
            String strCharge = "NO";

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, strChargeAction,
                    dblAmount);

            if (chargesWrapper.hasErrors()) {
                strResponseText = "";
            } else {
                strResponseText = "KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, SUCCESS, strCharge, strTitle, TEXT);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);


        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse buyAirtime(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                String strUsername = theMAPPRequest.getUsername();
                String strAppID = String.valueOf(theMAPPRequest.getAppID());
                String strPassword = theMAPPRequest.getPassword();

                String strTraceID = getTraceID(theMAPPRequest);

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
                // String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;
                String strMemberName = getUserFullName(theMAPPRequest, strUsername).trim();

                String strSourceAccountNo = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();

                String[] strSourceArr = strSourceAccountNo.split(Pattern.quote("||"));

                strSourceAccountNo = strSourceArr[0];
                String strSourceAccountName = strMemberName;
                if (strSourceArr.length > 1) {
                    strSourceAccountName = strSourceArr[1];
                }

                String strRecipientMobileNumber = configXPath.evaluate("MOBILE_NO", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));


                String strReceiverName = strMemberName;
                if (strRecipientMobileNumber.equalsIgnoreCase(strUsername)) {
                    strReceiverName = strMemberName;
                } else {
                    strReceiverName = strRecipientMobileNumber;
                }

                strRecipientMobileNumber = APIUtils.sanitizePhoneNumber(strRecipientMobileNumber);

                MAPPConstants.ResponseStatus enResponseStatus = ERROR;
                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                MemberRegisterResponse registerResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo,
                        RegisterConstants.MemberRegisterType.BLACKLIST);

                if (registerResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {

                    strTitle = "ERROR: Buy Airtime";
                    strResponseText = "Sorry, an error occurred while processing your request.\n\nERR_ACCBL300";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;

                } else {

                    double dblUtilityETopUpMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.AIRTIME_PURCHASE).getMinimum());
                    double dblUtilityETopUpMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.AIRTIME_PURCHASE).getMaximum());

                    double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "AIRTIME_PURCHASE");

                    dblUtilityETopUpMax = Math.min(dblUtilityETopUpMax, dblDailyLimitRemainingAmount);

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        strTitle = "ERROR: Buy Airtime";
                        strResponseText = "Please enter a valid amount for airtime purchase";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) < dblUtilityETopUpMin) {
                        strTitle = "ERROR: Buy Airtime";
                        strResponseText = "Minimum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblUtilityETopUpMin), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (dblDailyLimitRemainingAmount <= 0) {
                        strTitle = "ERROR: Buy Airtime";
                        strResponseText = "Sorry, the remaining amount you can transact today is KES 0.";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) > dblUtilityETopUpMax) {
                        strTitle = "ERROR: Buy Airtime";
                        strResponseText = "Maximum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblUtilityETopUpMax), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else {
                        PESA pesa = new PESA();

                        String strDatetime = MBankingDB.getDBDateTime().trim();
                        //String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();

                        String strTransaction = "Airtime Request";

                        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.AIRTIME);

                        long getProductID = Long.parseLong(pesaParam.getProductId());
                        String strCategory = "AIRTIME_PURCHASE";
                        String strAPICategory = "AIRTIME_PURCHASE";

                        String strSenderIdentifier = pesaParam.getSenderIdentifier();
                        String strSenderAccount = pesaParam.getSenderAccount();
                        String strSenderName = pesaParam.getSenderName();

                        int intPriority = 200;

                        String strOriginatorID = UUID.randomUUID().toString();

                        pesa.setOriginatorID(strOriginatorID);
                        pesa.setProductID(getProductID);

                        pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
                        pesa.setPESAAction(PESAConstants.PESAAction.B2C);
                        pesa.setCommand("E-TOPUP");
                        pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);

                        pesa.setPESAStatusCode(10);
                        pesa.setPESAStatusName("QUEUED");
                        pesa.setPESAStatusDescription("New PESA");
                        pesa.setPESAStatusDate(strDatetime);

                        pesa.setInitiatorType("MSISDN");
                        pesa.setInitiatorIdentifier(strUsername);
                        pesa.setInitiatorAccount(strUsername);
                        pesa.setInitiatorName(strMemberName);
                        pesa.setInitiatorReference(strTraceID);
                        pesa.setInitiatorApplication("MAPP");
                        pesa.setInitiatorOtherDetails("<DATA/>");

                        pesa.setSourceType("ACCOUNT_NO");
                        pesa.setSourceIdentifier(strSourceAccountNo);
                        pesa.setSourceAccount(strSourceAccountNo);
                        pesa.setSourceName(strSourceAccountName);
                        //deferred to after CBS call below
                        //pesa.setSourceReference();
                        pesa.setSourceApplication("CBS");
                        pesa.setSourceOtherDetails("<DATA/>");

                        pesa.setSenderType("SKY_CODE");
                        pesa.setSenderIdentifier(strSenderIdentifier);
                        pesa.setSenderAccount(strSenderAccount);
                        pesa.setSenderName(strSenderName);
                        pesa.setSenderOtherDetails("<DATA/>");

                        pesa.setReceiverType("MSISDN");
                        pesa.setReceiverIdentifier(strRecipientMobileNumber);
                        pesa.setReceiverAccount(strRecipientMobileNumber);
                        pesa.setReceiverName(strReceiverName);
                        pesa.setReceiverOtherDetails("<DATA/>");

                        pesa.setBeneficiaryType("MSISDN");
                        pesa.setBeneficiaryIdentifier(strRecipientMobileNumber);
                        pesa.setBeneficiaryAccount(strRecipientMobileNumber);
                        pesa.setBeneficiaryName(strReceiverName);
                        pesa.setBeneficiaryOtherDetails("<DATA/>");

                        pesa.setBatchReference(strOriginatorID);
                        pesa.setCorrelationReference(strTraceID);
                        pesa.setCorrelationApplication("MAPP");
                        pesa.setTransactionCurrency("KES");
                        pesa.setTransactionAmount(Double.parseDouble(strAmount));
                        pesa.setTransactionRemark("Airtime Purchase by " + strUsername + " to " + strRecipientMobileNumber);
                        pesa.setCategory(strCategory);

                        pesa.setPriority(200);
                        pesa.setSendCount(0);

                        pesa.setSchedulePesa(PESAConstants.Condition.NO);
                        pesa.setPesaDateScheduled(strDatetime);
                        pesa.setPesaDateCreated(strDatetime);
                        pesa.setPESAXMLData("<DATA/>");


                        TransactionWrapper<FlexicoreHashMap> buyAirtimeWrapper = CBSAPI.buyAirtime(
                                strUsername,
                                "MSISDN",
                                strUsername,
                                "APP_ID",
                                strAppID,
                                pesa.getOriginatorID(),
                                String.valueOf(pesa.getProductID()),
                                pesa.getPESAType().getValue(),
                                pesa.getPESAAction().getValue(),
                                pesa.getCommand(),
                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getInitiatorType())
                                        .putValue("identifier", pesa.getInitiatorIdentifier())
                                        .putValue("account", pesa.getInitiatorAccount())
                                        .putValue("name", pesa.getInitiatorName())
                                        .putValue("reference", pesa.getInitiatorReference())
                                        .putValue("other_details", pesa.getInitiatorOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getSourceType())
                                        .putValue("identifier", pesa.getSourceIdentifier())
                                        .putValue("account", pesa.getSourceAccount())
                                        .putValue("name", pesa.getSourceName())
                                        .putValue("reference", pesa.getSourceReference())
                                        .putValue("other_details", pesa.getSourceOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getSenderType())
                                        .putValue("identifier", pesa.getSenderIdentifier())
                                        .putValue("account", pesa.getSenderAccount())
                                        .putValue("name", pesa.getSenderName())
                                        .putValue("reference", pesa.getSenderReference())
                                        .putValue("other_details", pesa.getSenderOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getReceiverType())
                                        .putValue("identifier", pesa.getReceiverIdentifier())
                                        .putValue("account", pesa.getReceiverAccount())
                                        .putValue("name", pesa.getReceiverName())
                                        .putValue("reference", pesa.getReceiverReference())
                                        .putValue("other_details", pesa.getReceiverOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getBeneficiaryType())
                                        .putValue("identifier", pesa.getBeneficiaryIdentifier())
                                        .putValue("account", pesa.getBeneficiaryAccount())
                                        .putValue("name", pesa.getBeneficiaryName())
                                        .putValue("reference", pesa.getBeneficiaryReference())
                                        .putValue("other_details", pesa.getBeneficiaryOtherDetails()),

                                pesa.getTransactionAmount(),
                                strCategory,
                                pesa.getTransactionRemark(),
                                strTraceID,
                                "MAPP",
                                "MBANKING");

                        FlexicoreHashMap buyAirtimeMap = buyAirtimeWrapper.getSingleRecord();

                        CBSAPI.SMSMSG cbsMSG = buyAirtimeMap.getValue("msg_object");

                        if (buyAirtimeWrapper.hasErrors()) {
                            sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theMAPPRequest);

                            strTitle = "ERROR: Airtime Purchase Failed";
                            strResponseText = "An error occurred processing your request. Please try again after a few minutes.";

                            enResponseStatus = FAILED;
                            enResponseAction = CON;
                        } else {

                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                            String strSourceReference = buyAirtimeMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                            pesa.setSourceReference(strSourceReference);

                            String strMSG = "";

                            strAmount = Utils.formatAmount(strAmount);

                            if (PESAProcessor.sendPESA(pesa) > 0) {
                                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

                                /*strMSG = "Dear member, your Airtime Purchase request of KES " + strAmount + " to " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been received successfully. Kindly wait as it is being processed.";

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);*/

                                strCharge = "YES";
                                strTitle = "Request for Airtime Top-up";
                                strResponseText = "Your request to top up airtime of <b>KES " + strAmount + "</b><br/>For :<b>+" + pesa.getBeneficiaryIdentifier() + "</b> has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                enResponseAction = CON;

                            } else {

                                String strRefKey = UUID.randomUUID().toString();

                                TransactionWrapper<FlexicoreHashMap> reversalCashWithdrawalWrapper =
                                        CBSAPI.reverseMobileMoneyWithdrawal(
                                                strUsername,
                                                "MSISDN",
                                                strUsername,
                                                pesa.getOriginatorID(),
                                                pesa.getBeneficiaryType(),
                                                pesa.getBeneficiaryIdentifier(),
                                                pesa.getBeneficiaryName(),
                                                pesa.getBeneficiaryOtherDetails(),
                                                "",
                                                DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                                if (!reversalCashWithdrawalWrapper.hasErrors()) {
                                    strMSG = "Dear member, your Airtime Purchase request of KES " + strAmount + " to " + strRecipientMobileNumber + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                                } else {
                                    strMSG = "Dear member, your Airtime Purchase request of KES " + strAmount + " to " + strRecipientMobileNumber + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                                }

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);

                                // sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                                enResponseStatus = FAILED;
                                enResponseAction = CON;
                            }
                        }
                    }
                }

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse payBill(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = String.valueOf(theMAPPRequest.getAppID());

                String strTraceID = getTraceID(theMAPPRequest);

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
                //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;
                String strMemberName = getUserFullName(theMAPPRequest, strUsername);


                String strSourceAccountNo = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();

                String[] strSourceArr = strSourceAccountNo.split(Pattern.quote("||"));

                strSourceAccountNo = strSourceArr[0];
                String strSourceAccountName = strMemberName;
                if (strSourceArr.length > 1) {
                    strSourceAccountName = strSourceArr[1];
                }

                String strPaybillNo = configXPath.evaluate("PAYBILL_NO", ndRequestMSG).trim();
                String strBillAccountNumber = configXPath.evaluate("BILL_ACCOUNT_NO", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
                String strType = configXPath.evaluate("TYPE", ndRequestMSG).trim();

                String strTransactionType = "";
                if (strType.equalsIgnoreCase("BUSINESS_NUMBER")) {
                    strTransactionType = "Pay Bill Transaction";
                } else {
                    strTransactionType = "Buy Goods Transaction";
                }
                String strTransactionDescription = "B2B|Bill Payment to " + strPaybillNo + " for " + strBillAccountNumber;
                strTransactionDescription = PESAAPI.shortenName(strTransactionDescription);

                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                MAPPConstants.ResponseStatus enResponseStatus = ERROR;
                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo, RegisterConstants.MemberRegisterType.BLACKLIST);
                if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                    strTitle = "ERROR: Pay Bill";
                    strResponseText = "Sorry, an error occurred while processing your request.\n\nERR_ACCBL300";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;

                } else {

                    double dblWithdrawalMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.PAY_BILL).getMinimum());
                    double dblWithdrawalMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.PAY_BILL).getMaximum());

                    double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BILL_PAYMENT");

                    dblWithdrawalMax = Math.min(dblWithdrawalMax, dblDailyLimitRemainingAmount);

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        strTitle = "ERROR: " + strTransactionType;
                        strResponseText = "Please enter a valid amount for withdrawal";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) < dblWithdrawalMin) {
                        strTitle = "ERROR: " + strTransactionType;
                        strResponseText = "Minimum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMin), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (dblDailyLimitRemainingAmount <= 0) {
                        strTitle = "ERROR: " + strTransactionType;
                        strResponseText = "Sorry, the remaining amount you can transact today is KES 0.";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) > dblWithdrawalMax) {
                        strTitle = "ERROR: " + strTransactionType;
                        strResponseText = "Maximum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMax), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else {
                        PESA pesa = new PESA();

                        String strDatetime = MBankingDB.getDBDateTime().trim();
                        //String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();

                        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2B);

                        long getProductID = Long.parseLong(pesaParam.getProductId());
                        int intPriority = 200;
                        String strCategory = "BILL_PAYMENT";
                        String strAPICategory = "BILL_PAYMENT";

                        String strSenderIdentifier = pesaParam.getSenderIdentifier();
                        String strSenderAccount = pesaParam.getSenderAccount();
                        String strSenderName = pesaParam.getSenderName();

                        String strOriginatorID = UUID.randomUUID().toString();

                        pesa.setOriginatorID(strOriginatorID);
                        pesa.setProductID(getProductID);

                        pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
                        pesa.setPESAAction(PESAConstants.PESAAction.B2B);
                        pesa.setCommand("BusinessPayBill");
                        pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);

                        pesa.setPESAStatusCode(10);
                        pesa.setPESAStatusName("QUEUED");
                        pesa.setPESAStatusDescription("New PESA");
                        pesa.setPESAStatusDate(strDatetime);

                        pesa.setInitiatorType("MSISDN");
                        pesa.setInitiatorIdentifier(strUsername);
                        pesa.setInitiatorAccount(strUsername);
                        pesa.setInitiatorName(strMemberName);
                        pesa.setInitiatorReference(strTraceID);
                        pesa.setInitiatorApplication("MAPP");
                        pesa.setInitiatorOtherDetails("<DATA/>");

                        pesa.setSourceType("ACCOUNT_NO");
                        pesa.setSourceIdentifier(strSourceAccountNo);
                        pesa.setSourceAccount(strSourceAccountNo);
                        pesa.setSourceName(strSourceAccountName);
                        //deferred to below
                        //pesa.setSourceReference("987654321");
                        pesa.setSourceApplication("CBS");
                        pesa.setSourceOtherDetails("<DATA/>");

                        pesa.setSenderType("SHORT_CODE");
                        pesa.setSenderIdentifier(strSenderIdentifier);
                        pesa.setSenderAccount(strSenderAccount);
                        pesa.setSenderName(strSenderName);
                        pesa.setSenderOtherDetails("<DATA/>");

                        pesa.setReceiverType("SHORT_CODE");
                        pesa.setReceiverIdentifier(strPaybillNo);
                        pesa.setReceiverAccount(strBillAccountNumber);
                        pesa.setReceiverName(strPaybillNo);
                        pesa.setReceiverOtherDetails("<DATA/>");

                        pesa.setBeneficiaryType("MSISDN");
                        pesa.setBeneficiaryIdentifier(strUsername);
                        pesa.setBeneficiaryAccount(strUsername);
                        pesa.setBeneficiaryName(strBillAccountNumber);
                        pesa.setBeneficiaryOtherDetails("<DATA/>");

                        pesa.setBatchReference(strOriginatorID);
                        pesa.setCorrelationReference(strTraceID);
                        pesa.setCorrelationApplication("MAPP");
                        pesa.setTransactionCurrency("KES");
                        pesa.setTransactionAmount(Double.parseDouble(strAmount));
                        pesa.setTransactionRemark(strTransactionDescription);
                        pesa.setCategory(strCategory);

                        pesa.setPriority(200);
                        pesa.setSendCount(0);

                        pesa.setSchedulePesa(PESAConstants.Condition.NO);
                        pesa.setPesaDateScheduled(strDatetime);
                        pesa.setPesaDateCreated(strDatetime);
                        pesa.setPESAXMLData("<DATA/>");

                        TransactionWrapper<FlexicoreHashMap> utilityPaymentWrapper = CBSAPI.utilitiesPayment(
                                strUsername,
                                "MSISDN",
                                strUsername,
                                "APP_ID",
                                strAppID,
                                pesa.getOriginatorID(),
                                String.valueOf(pesa.getProductID()),
                                pesa.getPESAType().getValue(),
                                pesa.getPESAAction().getValue(),
                                pesa.getCommand(),
                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getInitiatorType())
                                        .putValue("identifier", pesa.getInitiatorIdentifier())
                                        .putValue("account", pesa.getInitiatorAccount())
                                        .putValue("name", pesa.getInitiatorName())
                                        .putValue("reference", pesa.getInitiatorReference())
                                        .putValue("other_details", pesa.getInitiatorOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getSourceType())
                                        .putValue("identifier", pesa.getSourceIdentifier())
                                        .putValue("account", pesa.getSourceAccount())
                                        .putValue("name", pesa.getSourceName())
                                        .putValue("reference", pesa.getSourceReference())
                                        .putValue("other_details", pesa.getSourceOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getSenderType())
                                        .putValue("identifier", pesa.getSenderIdentifier())
                                        .putValue("account", pesa.getSenderAccount())
                                        .putValue("name", pesa.getSenderName())
                                        .putValue("reference", pesa.getSenderReference())
                                        .putValue("other_details", pesa.getSenderOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getReceiverType())
                                        .putValue("identifier", pesa.getReceiverIdentifier())
                                        .putValue("account", pesa.getReceiverAccount())
                                        .putValue("name", pesa.getReceiverName())
                                        .putValue("reference", pesa.getReceiverReference())
                                        .putValue("other_details", pesa.getReceiverOtherDetails()),

                                new FlexicoreHashMap()
                                        .putValue("identifier_type", pesa.getBeneficiaryType())
                                        .putValue("identifier", pesa.getBeneficiaryIdentifier())
                                        .putValue("account", pesa.getBeneficiaryAccount())
                                        .putValue("name", pesa.getBeneficiaryName())
                                        .putValue("reference", pesa.getBeneficiaryReference())
                                        .putValue("other_details", pesa.getBeneficiaryOtherDetails()),

                                pesa.getTransactionAmount(),
                                strCategory,
                                pesa.getTransactionRemark(),
                                strTraceID,
                                "MAPP",
                                "MBANKING");

                        FlexicoreHashMap utilityPaymentMap = utilityPaymentWrapper.getSingleRecord();

                        CBSAPI.SMSMSG cbsMSG = utilityPaymentMap.getValue("msg_object");

                        if (utilityPaymentWrapper.hasErrors()) {
                            //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theMAPPRequest);

                            strTitle = "ERROR: " + strTransactionType + " Failed";
                            strResponseText = "An error occurred processing your request. Please try again after a few minutes.";
                            strResponseText = utilityPaymentMap.getStringValue("display_message");

                            enResponseStatus = FAILED;
                            enResponseAction = CON;
                        } else {

                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                            String strSourceReference = utilityPaymentMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                            pesa.setSourceReference(strSourceReference);

                            String strMSG = "";

                            strAmount = Utils.formatAmount(strAmount);

                            if (PESAProcessor.sendPESA(pesa) > 0) {
                                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

                                /*strMSG = "Dear member, your Bill Payment request of KES " + strAmount + " to " + pesa.getReceiverName() + ", beneficiary " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been received successfully. Kindly wait as it is being processed.";

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);*/

                                strCharge = "YES";
                                strTitle = strTransactionType;
                                strResponseText = "Your payment of <b>KES " + strAmount + "</b> has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                enResponseAction = CON;

                            } else {

                                String strRefKey = UUID.randomUUID().toString();

                                TransactionWrapper<FlexicoreHashMap> reversalCashWithdrawalWrapper =
                                        CBSAPI.reverseMobileMoneyWithdrawal(
                                                strUsername,
                                                "MSISDN",
                                                strUsername,
                                                pesa.getOriginatorID(),
                                                pesa.getBeneficiaryType(),
                                                pesa.getBeneficiaryIdentifier(),
                                                pesa.getBeneficiaryName(),
                                                pesa.getBeneficiaryOtherDetails(),
                                                "",
                                                DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                                if (!reversalCashWithdrawalWrapper.hasErrors()) {
                                    if (strType.equalsIgnoreCase("BUSINESS_NUMBER")) {
                                        strMSG = "Dear member, your " + strTransactionType + " of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                                    } else {
                                        strMSG = "Dear member, your " + strTransactionType + " of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                                    }
                                } else {
                                    if (strType.equalsIgnoreCase("BUSINESS_NUMBER")) {
                                        strMSG = "Dear member, your " + strTransactionType + " of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                                    } else {
                                        strMSG = "Dear member, your " + strTransactionType + " of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                                    }
                                }

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);

                                enResponseStatus = FAILED;
                                enResponseAction = CON;
                            }
                        }
                    }
                }

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse lipaNa(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            String strUsername = theMAPPRequest.getUsername();

            String strTraceID = getTraceID(theMAPPRequest);

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document doc = docBuilder.newDocument();

            String strAccountNumber = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();
            String strShopNumber = configXPath.evaluate("SHOP_NUMBER", ndRequestMSG).trim();
            String strShopName = configXPath.evaluate("SHOP_NAME", ndRequestMSG).trim();
            String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");

            String strMAPPSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
            String strDatetime = MBankingDB.getDBDateTime().trim();
            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

            String strTitle = "Request Successful";
            String strResponseText = "Dear member, your request to pay <b>KES " + strFormattedAmount + "</b> to <b>Shop No. " + strShopNumber + "</b> has been received successfully. Please wait shortly as it is being processed.";
            String strCharge = "NO";

            String strSMSToBuyer = "Dear member, your payment of KES " + strFormattedAmount + " from A/C " + strAccountNumber + " to Shop No. " + strShopNumber + " " + strShopName +
                    " has been completed successfully.\n\n" +
                    "Date: " + strFormattedDateTime + "\n" +
                    "Ref: " + strMAPPSessionId;

            sendSMS(strUsername, strSMSToBuyer, MSGConstants.MSGMode.SAF, 210, "LIPA_NA", theMAPPRequest);

            String strSMSToShopOwner = "Dear Vincent, ISAAC has just made a payment of KES " + strFormattedAmount + " to your Shop No. " + strShopNumber +
                    "\n\n" +
                    "Date: " + strFormattedDateTime + "\n" +
                    "Ref: " + strMAPPSessionId;

            // fnSendSMS("254790491947", strSMSToShopOwner, "YES", MSGConstants.MSGMode.SAF, 210, "LIPA_NA", "MAPP", "MBANKING_SERVER", strMAPPSessionId + "O", UUID.randomUUID().toString());


            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, SUCCESS, strCharge, strTitle, TEXT);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);


        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse bankTransferViaB2B(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = String.valueOf(theMAPPRequest.getAppID());

                String strTraceID = getTraceID(theMAPPRequest);

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
                //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;
                String strMemberName = getUserFullName(theMAPPRequest, strUsername).trim();

                String strSourceAccountNo = configXPath.evaluate("FROM_ACCOUNT_NO", ndRequestMSG).trim();

                String[] strSourceArr = strSourceAccountNo.split(Pattern.quote("||"));

                strSourceAccountNo = strSourceArr[0];
                String strSourceAccountName = strMemberName;
                if (strSourceArr.length > 1) {
                    strSourceAccountName = strSourceArr[1];
                }

                String strBank = configXPath.evaluate("BANK", ndRequestMSG).trim();
                String strBankName = configXPath.evaluate("BANK_NAME", ndRequestMSG).trim();
                String strReceiverBankAccountNumber = configXPath.evaluate("BANK_ACCOUNT_NO", ndRequestMSG).trim();
                String strReceiverBankAccountName = configXPath.evaluate("BANK_ACCOUNT_NAME", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();

                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                MAPPConstants.ResponseStatus enResponseStatus = ERROR;
                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo, RegisterConstants.MemberRegisterType.BLACKLIST);
                if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                    strTitle = "ERROR: Bank Transfer";
                    strResponseText = "Sorry, an error occurred while processing your request.\n\nERR_ACCBL300";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;

                } else {
                    double dblWithdrawalMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum());
                    double dblWithdrawalMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum());

                    double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                    dblWithdrawalMax = Math.min(dblWithdrawalMax, dblDailyLimitRemainingAmount);

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Please enter a valid amount for withdrawal";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) < dblWithdrawalMin) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Minimum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMin), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (dblDailyLimitRemainingAmount <= 0) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Sorry, the remaining amount you can transact today is KES 0.";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) > dblWithdrawalMax) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Maximum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMax), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else {
                        PESA pesa = new PESA();

                        String strDatetime = MBankingDB.getDBDateTime().trim();
                        //String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();

                        String strTransaction = "Bank Transfer Request";

                        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2B);

                        long getProductID = Long.parseLong(pesaParam.getProductId());
                        int intPriority = 200;
                        String strCategory = "BANK_TRANSFER";
                        String strAPICategory = "BANK_TRANSFER";

                        String strSenderIdentifier = pesaParam.getSenderIdentifier();
                        String strSenderAccount = pesaParam.getSenderAccount();
                        String strSenderName = pesaParam.getSenderName();

                        String strTransactionDescription = "BT|B2B|" + strBankName + "|" + strReceiverBankAccountNumber + "|" + strReceiverBankAccountName;
                        strTransactionDescription = PESAAPI.shortenName(strTransactionDescription).trim();


                        String strOriginatorID = UUID.randomUUID().toString();

                        pesa.setOriginatorID(strOriginatorID);
                        pesa.setProductID(getProductID);

                        pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
                        pesa.setPESAAction(PESAConstants.PESAAction.B2B);
                        pesa.setCommand("BusinessPayBill");
                        pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);

                        pesa.setPESAStatusCode(10);
                        pesa.setPESAStatusName("QUEUED");
                        pesa.setPESAStatusDescription("New PESA");
                        pesa.setPESAStatusDate(strDatetime);

                        pesa.setInitiatorType("MSISDN");
                        pesa.setInitiatorIdentifier(strUsername);
                        pesa.setInitiatorAccount(strUsername);
                        pesa.setInitiatorName(strMemberName);
                        pesa.setInitiatorReference(strTraceID);
                        pesa.setInitiatorApplication("MAPP");
                        pesa.setInitiatorOtherDetails("<DATA/>");

                        pesa.setSourceType("ACCOUNT_NO");
                        pesa.setSourceIdentifier(strSourceAccountNo);
                        pesa.setSourceAccount(strSourceAccountNo);
                        pesa.setSourceName(strSourceAccountName);
                        //deferred to below
                        //pesa.setSourceReference("987654321");
                        pesa.setSourceApplication("CBS");
                        pesa.setSourceOtherDetails("<DATA/>");

                        pesa.setSenderType("SHORT_CODE");
                        pesa.setSenderIdentifier(strSenderIdentifier);
                        pesa.setSenderAccount(strSenderAccount);
                        pesa.setSenderName(strSenderName);
                        pesa.setSenderOtherDetails("<DATA/>");

                        pesa.setReceiverType("SHORT_CODE");
                        pesa.setReceiverIdentifier(strBank);
                        pesa.setReceiverAccount(strReceiverBankAccountNumber);
                        pesa.setReceiverName(strBankName);
                        pesa.setReceiverOtherDetails("<DATA/>");

                        pesa.setBeneficiaryType("MSISDN");
                        pesa.setBeneficiaryIdentifier(strUsername);
                        pesa.setBeneficiaryAccount(strUsername);
                        pesa.setBeneficiaryName(strReceiverBankAccountName);
                        pesa.setBeneficiaryOtherDetails("<DATA/>");

                        pesa.setBatchReference(strOriginatorID);
                        pesa.setCorrelationReference(strTraceID);
                        pesa.setCorrelationApplication("MAPP");
                        pesa.setTransactionCurrency("KES");
                        pesa.setTransactionAmount(Double.parseDouble(strAmount));
                        pesa.setTransactionRemark(strTransactionDescription);
                        pesa.setCategory(strCategory);

                        pesa.setPriority(200);
                        pesa.setSendCount(0);

                        pesa.setSchedulePesa(PESAConstants.Condition.NO);
                        pesa.setPesaDateScheduled(strDatetime);
                        pesa.setPesaDateCreated(strDatetime);
                        pesa.setPESAXMLData("<DATA/>");

                        TransactionWrapper<FlexicoreHashMap> bankTransferWrapper =
                                CBSAPI.bankTransferViaB2B(
                                        strUsername,
                                        "MSISDN",
                                        strUsername,
                                        "APP_ID",
                                        strAppID,
                                        pesa.getOriginatorID(),
                                        String.valueOf(pesa.getProductID()),
                                        pesa.getPESAType().getValue(),
                                        pesa.getPESAAction().getValue(),
                                        pesa.getCommand(),
                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getInitiatorType())
                                                .putValue("identifier", pesa.getInitiatorIdentifier())
                                                .putValue("account", pesa.getInitiatorAccount())
                                                .putValue("name", pesa.getInitiatorName())
                                                .putValue("reference", pesa.getInitiatorReference())
                                                .putValue("other_details", pesa.getInitiatorOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getSourceType())
                                                .putValue("identifier", pesa.getSourceIdentifier())
                                                .putValue("account", pesa.getSourceAccount())
                                                .putValue("name", pesa.getSourceName())
                                                .putValue("reference", pesa.getSourceReference())
                                                .putValue("other_details", pesa.getSourceOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getSenderType())
                                                .putValue("identifier", pesa.getSenderIdentifier())
                                                .putValue("account", pesa.getSenderAccount())
                                                .putValue("name", pesa.getSenderName())
                                                .putValue("reference", pesa.getSenderReference())
                                                .putValue("other_details", pesa.getSenderOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getReceiverType())
                                                .putValue("identifier", pesa.getReceiverIdentifier())
                                                .putValue("account", pesa.getReceiverAccount())
                                                .putValue("name", pesa.getReceiverName())
                                                .putValue("reference", pesa.getReceiverReference())
                                                .putValue("other_details", pesa.getReceiverOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getBeneficiaryType())
                                                .putValue("identifier", pesa.getBeneficiaryIdentifier())
                                                .putValue("account", pesa.getBeneficiaryAccount())
                                                .putValue("name", pesa.getBeneficiaryName())
                                                .putValue("reference", pesa.getBeneficiaryReference())
                                                .putValue("other_details", pesa.getBeneficiaryOtherDetails()),

                                        pesa.getTransactionAmount(),
                                        strCategory,
                                        pesa.getTransactionRemark(),
                                        strTraceID,
                                        "MAPP",
                                        "MBANKING");

                        FlexicoreHashMap bankTransferMap = bankTransferWrapper.getSingleRecord();

                        CBSAPI.SMSMSG cbsMSG = bankTransferMap.getValue("msg_object");

                        if (bankTransferWrapper.hasErrors()) {
                            //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theMAPPRequest);

                            strTitle = "ERROR: Bank Transfer Failed";
                            //strResponseText = "An error occurred processing your request. Please try again after a few minutes.";

                            strResponseText = bankTransferMap.getStringValue("display_message");

                            enResponseStatus = FAILED;
                            enResponseAction = CON;
                        } else {

                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
                            strFormattedDateTime = DateTime.convertStringToDateToString(DateTime.getCurrentDateTime(), "yyyy-MM-dd HH:mm:ss.SSSSSS", "dd-MMM-yy' at 'hh:mm aaa");

                            String strSourceReference = bankTransferMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                            pesa.setSourceReference(strSourceReference);

                            String strMSG = "";

                            strAmount = Utils.formatAmount(strAmount);

                            if (PESAProcessor.sendPESA(pesa) > 0) {
                                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

                                strCharge = "YES";
                                /*strMSG = "Dear member, your Bank Transfer request of KES " + strAmount + " to " + strBankName + " - " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been received successfully. Kindly wait as it is being processed.";

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);*/

                                strTitle = "Bank Transfer";
                                strResponseText = "Your request to transfer <b>KES " + strAmount + "</b> to has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                enResponseAction = CON;

                            } else {

                                String strRefKey = UUID.randomUUID().toString();

                                TransactionWrapper<FlexicoreHashMap> reversalWrapper =
                                        CBSAPI.reverseMobileMoneyWithdrawal(
                                                strUsername,
                                                "MSISDN",
                                                strUsername,
                                                pesa.getOriginatorID(),
                                                pesa.getBeneficiaryType(),
                                                pesa.getBeneficiaryIdentifier(),
                                                pesa.getBeneficiaryName(),
                                                pesa.getBeneficiaryOtherDetails(),
                                                "",
                                                DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                                if (!reversalWrapper.hasErrors()) {
                                    strMSG = "Dear member, your Bank Transfer request of KES KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                                } else {
                                    strMSG = "Dear member, your Bank Transfer request of KES KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                                }

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);

                                enResponseStatus = FAILED;
                                enResponseAction = CON;
                            }
                        }
                    }
                }

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse bankTransferViaPESALINK(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            System.out.println("OTP " + otp);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();
                System.out.println("Action " + strAction);

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }
            System.out.println("Here");

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = String.valueOf(theMAPPRequest.getAppID());

                String strTraceID = getTraceID(theMAPPRequest);

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
                //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;
                String strMemberName = getUserFullName(theMAPPRequest, strUsername).trim();

                String strSourceAccountNo = configXPath.evaluate("FROM_ACCOUNT_NO", ndRequestMSG).trim();

                String[] strSourceArr = strSourceAccountNo.split(Pattern.quote("||"));

                strSourceAccountNo = strSourceArr[0];
                String strSourceAccountName = strMemberName;
                if (strSourceArr.length > 1) {
                    strSourceAccountName = strSourceArr[1].trim();
                }

                String strBank = configXPath.evaluate("BANK", ndRequestMSG).trim();
                String strBankName = configXPath.evaluate("BANK_NAME", ndRequestMSG).trim();
                String strReceiverBankAccountNumber = configXPath.evaluate("BANK_ACCOUNT_NO", ndRequestMSG).trim();
                String strReceiverBankAccountName = configXPath.evaluate("BANK_ACCOUNT_NAME", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();


                MAPPConstants.ResponseStatus enResponseStatus = ERROR;
                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo, RegisterConstants.MemberRegisterType.BLACKLIST);


                String strCategory = "PESALINK_TRANSFER";
                String strBankOtherDetails = "";
                String strBankCode = "";
                String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();
                String strMAPPSessionID = fnModifyMAPPSessionID(theMAPPRequest);
                System.out.println("MAPPSessionID " + strMAPPSessionID);
                System.out.println("Bank " + strBank);


                LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.BANK_CODE);
                for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                    System.out.println("***********************************");
                    System.out.println("Provider Account " + serviceProviderAccount.getProviderAccountIdentifier());
                    System.out.println("Provider Branch Code " + serviceProviderAccount.getProviderBranchCode());
                    String strProviderIdentifier = serviceProviderAccount.getProviderAccountIdentifier();
                    if (strProviderIdentifier.equals(strBank)) {
                        strBankCode = serviceProviderAccount.getProviderAccountIdentifier();
                        String strBranchCode = serviceProviderAccount.getProviderBranchCode();
                        strBankOtherDetails = "<DATA><BANK_CODE>" + strBankCode + "</BANK_CODE><BRANCH_CODE>" + strBranchCode + "</BRANCH_CODE></DATA>";
                        System.out.println("Bank Code " + strBankCode);
                        System.out.println("Branch Code " + strBranchCode);
                        System.out.println("***********************************");


                        break;
                    }
                }

                System.out.println("Source Reference " + strMAPPSessionID);


                String strTransaction = "Pesalink Transfer Request";
                String strRemark = "PL|BT|" + strBankCode + "|" + strReceiverBankAccountNumber + "|" + strUsername + "|" + strMemberName;
                strRemark = PESAAPI.shortenName(strRemark);


                if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {

                    strTitle = "ERROR: Bank Transfer";
                    strResponseText = "Sorry, an error occurred while processing your request.\n\nERR_ACCBL300";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;

                } else {
                    double dblWithdrawalMin = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum());
                    double dblWithdrawalMax = Double.parseDouble(getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum());

                    double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                    dblWithdrawalMax = Math.min(dblWithdrawalMax, dblDailyLimitRemainingAmount);

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Please enter a valid amount for withdrawal";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) < dblWithdrawalMin) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Minimum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMin), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (dblDailyLimitRemainingAmount <= 0) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Sorry, the remaining amount you can transact today is KES 0.";
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else if (Double.parseDouble(strAmount) > dblWithdrawalMax) {
                        strTitle = "ERROR: Bank Transfer";
                        strResponseText = "Maximum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMax), "#,##0.00");
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    } else {
                        PESA pesa = new PESA();

                        String strDatetime = MBankingDB.getDBDateTime().trim();
                        //String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();


                        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.FAMILY_BANK_PESALINK);


                        if (pesaParam == null) {
                            System.out.println("Pesa Param is Null");
                        }
                        System.out.println("**************************");
                        System.out.println("Pesa param details");
                        System.out.println("Prod ID " + pesaParam.getProductId());
                        System.out.println("Pesa Param Identifier " + pesaParam.getSenderIdentifier());
                        System.out.println("Pesa Param Account " + pesaParam.getSenderAccount());
                        System.out.println("Pesa Param Name " + pesaParam.getSenderName());


                        long getProductID = Long.parseLong(pesaParam.getProductId());
                        int intPriority = 200;


                        String strSenderIdentifier = pesaParam.getSenderIdentifier();
                        String strSenderAccount = pesaParam.getSenderAccount();
                        String strSenderName = pesaParam.getSenderName();

                        String strOriginatorID = UUID.randomUUID().toString();

                        pesa.setOriginatorID(strOriginatorID);
                        pesa.setProductID(getProductID);

                        pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
                        pesa.setPESAAction(PESAConstants.PESAAction.B2B);
                        pesa.setCommand("PESALINK");
                        pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);


                        pesa.setInitiatorType("MSISDN");
                        pesa.setInitiatorIdentifier(strUsername);
                        pesa.setInitiatorAccount(strUsername);
                        pesa.setInitiatorName(strMemberName);
                        pesa.setInitiatorReference(strTraceID);
                        pesa.setInitiatorApplication("MAPP");
                        pesa.setInitiatorOtherDetails("<DATA/>");

                        pesa.setSourceType("ACCOUNT_NO");
                        pesa.setSourceIdentifier(strSourceAccountNo);
                        pesa.setSourceAccount(strSourceAccountNo);
                        pesa.setSourceName(strSourceAccountName);
                        //deferred to below
                        pesa.setSourceReference(strMAPPSessionID);
                        System.out.println("Source Reference  at Set" + strMAPPSessionID);
                        pesa.setSourceApplication("CBS");
                        pesa.setSourceOtherDetails("<DATA><BANK_CODE>" + AppConstants.strSACCOName + "</BANK_CODE><BRANCH_CODE>" + AppConstants.strSACCOName + "</BRANCH_CODE></DATA>");

                        pesa.setSenderType("ACCOUNT_NO");
                        pesa.setSenderIdentifier(strSenderIdentifier);
                        pesa.setSenderAccount(strSenderAccount);
                        pesa.setSenderName(strSenderName);
                        pesa.setSenderOtherDetails("<DATA/>");

                        pesa.setReceiverType("ACCOUNT_NO");
                        pesa.setReceiverIdentifier(strReceiverBankAccountNumber);
                        pesa.setReceiverAccount(strReceiverBankAccountNumber);
                        pesa.setReceiverName(strReceiverBankAccountName);
                        pesa.setReceiverOtherDetails(strBankOtherDetails);

                        pesa.setBeneficiaryType("ACCOUNT_NO");
                        pesa.setBeneficiaryIdentifier(strReceiverBankAccountNumber);
                        pesa.setBeneficiaryAccount(strReceiverBankAccountNumber);
                        pesa.setBeneficiaryName(strReceiverBankAccountName);
                        pesa.setBeneficiaryOtherDetails(strBankOtherDetails);

                        pesa.setBatchReference(strGUID);
                        pesa.setCorrelationReference(strTraceID);
                        pesa.setCorrelationApplication("MAPP");
                        pesa.setTransactionCurrency("KES");
                        pesa.setTransactionAmount(Double.parseDouble(strAmount));
                        pesa.setTransactionRemark(strRemark);
                        pesa.setCategory("PESALINK_TRANSFER");

                        pesa.setPriority(200);
                        pesa.setSendCount(0);

                        pesa.setSchedulePesa(PESAConstants.Condition.NO);
                        pesa.setPesaDateScheduled(strDatetime);
                        pesa.setPesaDateCreated(strDatetime);
                        pesa.setPESAXMLData("<DATA/>");

                        pesa.setPESAStatusCode(10);
                        pesa.setPESAStatusName("QUEUED");
                        pesa.setPESAStatusDescription("New PESA");
                        pesa.setPESAStatusDate(strDatetime);

                        System.out.println("Secret Key " + PESALocalParameters.getIntegritySecret());

                        TransactionWrapper<FlexicoreHashMap> bankTransferWrapper =
                                CBSAPI.bankTransferViaB2B(
                                        strUsername,
                                        "MSISDN",
                                        strUsername,
                                        "APP_ID",
                                        strAppID,
                                        pesa.getOriginatorID(),
                                        String.valueOf(pesa.getProductID()),
                                        pesa.getPESAType().getValue(),
                                        pesa.getPESAAction().getValue(),
                                        pesa.getCommand(),
                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getInitiatorType())
                                                .putValue("identifier", pesa.getInitiatorIdentifier())
                                                .putValue("account", pesa.getInitiatorAccount())
                                                .putValue("name", pesa.getInitiatorName())
                                                .putValue("reference", pesa.getInitiatorReference())
                                                .putValue("other_details", pesa.getInitiatorOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getSourceType())
                                                .putValue("identifier", pesa.getSourceIdentifier())
                                                .putValue("account", pesa.getSourceAccount())
                                                .putValue("name", pesa.getSourceName())
                                                .putValue("reference", pesa.getSourceReference())
                                                .putValue("other_details", pesa.getSourceOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getSenderType())
                                                .putValue("identifier", pesa.getSenderIdentifier())
                                                .putValue("account", pesa.getSenderAccount())
                                                .putValue("name", pesa.getSenderName())
                                                .putValue("reference", pesa.getSenderReference())
                                                .putValue("other_details", pesa.getSenderOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getReceiverType())
                                                .putValue("identifier", pesa.getReceiverIdentifier())
                                                .putValue("account", pesa.getReceiverAccount())
                                                .putValue("name", pesa.getReceiverName())
                                                .putValue("reference", pesa.getReceiverReference())
                                                .putValue("other_details", pesa.getReceiverOtherDetails()),

                                        new FlexicoreHashMap()
                                                .putValue("identifier_type", pesa.getBeneficiaryType())
                                                .putValue("identifier", pesa.getBeneficiaryIdentifier())
                                                .putValue("account", pesa.getBeneficiaryAccount())
                                                .putValue("name", pesa.getBeneficiaryName())
                                                .putValue("reference", pesa.getBeneficiaryReference())
                                                .putValue("other_details", pesa.getBeneficiaryOtherDetails()),

                                        pesa.getTransactionAmount(),
                                        strCategory,
                                        pesa.getTransactionRemark(),
                                        strTraceID,
                                        "MAPP",
                                        "MBANKING");

                        FlexicoreHashMap bankTransferMap = bankTransferWrapper.getSingleRecord();

                        CBSAPI.SMSMSG cbsMSG = bankTransferMap.getValue("msg_object");

                        if (bankTransferWrapper.hasErrors()) {
                            System.out.println(
                                    "Sending pesa failed"
                            );

                            //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theMAPPRequest);

                            strTitle = "ERROR: Bank Transfer Failed";
                            strResponseText = bankTransferMap.getStringValue("display_message");
                            //strResponseText = "An error occurred processing your request. Please try again after a few minutes.";

                            enResponseStatus = FAILED;
                            enResponseAction = CON;
                        } else {

                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                            String strSourceReference = bankTransferMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                            // pesa.setSourceReference(strSourceReference);

                            String strMSG = "";

                            strAmount = Utils.formatAmount(strAmount);

                            if (PESAProcessor.sendPESA(pesa) > 0) {
                                System.out.println("Source Reference at Sending" + strSourceReference);
                                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

                                strCharge = "YES";
                                //*strMSG = "Dear member, your Bank Transfer request of KES " + strAmount + " to " + strBankName + " - " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been received successfully. Kindly wait as it is being processed.";

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);

                                strTitle = "Bank Transfer";
                                strResponseText = "Your request to transfer <b>KES " + strAmount + "</b> to has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                enResponseAction = CON;

                            } else {

                                String strRefKey = UUID.randomUUID().toString();

                                TransactionWrapper<FlexicoreHashMap> reversalWrapper =
                                        CBSAPI.reverseMobileMoneyWithdrawal(
                                                strUsername,
                                                "MSISDN",
                                                strUsername,
                                                pesa.getOriginatorID(),
                                                pesa.getBeneficiaryType(),
                                                pesa.getBeneficiaryIdentifier(),
                                                pesa.getBeneficiaryName(),
                                                pesa.getBeneficiaryOtherDetails(),
                                                "",
                                                DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                                if (!reversalWrapper.hasErrors()) {
                                    strMSG = "Dear member, your Bank Transfer request of KES KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                                } else {
                                    strMSG = "Dear member, your Bank Transfer request of KES KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                                }

                                sendSMS(strUsername, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theMAPPRequest);

                                enResponseStatus = FAILED;
                                enResponseAction = CON;
                            }
                        }
                    }
                }

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            System.out.println("StackTrace");
            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse bankTransferViaPesaLink(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            ke.skyworld.mbanking.mappapi.MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = ke.skyworld.mbanking.mappapi.MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, ke.skyworld.mbanking.mappapi.MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, ke.skyworld.mbanking.mappapi.MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = ke.skyworld.mbanking.mappapi.MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == ke.skyworld.mbanking.mappapi.MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();


                String strTraceID = theMAPPRequest.getTraceID();

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
                String strMAPPSessionID = fnModifyMAPPSessionID(theMAPPRequest);

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;

                String strFromAccountNo = configXPath.evaluate("FROM_ACCOUNT_NO", ndRequestMSG).trim();
                String strBank = configXPath.evaluate("BANK", ndRequestMSG).trim();
                String strBankName = configXPath.evaluate("BANK_NAME", ndRequestMSG).trim();
                String strReceiverBankAccountNumber = configXPath.evaluate("BANK_ACCOUNT_NO", ndRequestMSG).trim();
                String strReceiverBankAccountName = configXPath.evaluate("BANK_ACCOUNT_NAME", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();


                /*      builder.element("FROM_ACCOUNT_NO", nest: strAccountFrom);
                          builder.element("BANK", nest: saSelectedServiceAccount.accountType);
                          builder.element("BANK_NAME", nest: SecondSpinnerData.mdSpinnerModelValue.value);
                          builder.element("BANK_ACCOUNT_NO", nest: strAccountTo);
                          builder.element("BANK_ACCOUNT_NAME", nest: saSelectedServiceAccount.accountName);
                          builder.element("AMOUNT", nest: Sanitarium.sanitize(strAmount));*/

                String strBankOtherDetails = "";
                String strBankCode = "";

                LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.BANK_CODE);

                for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                    String strProviderIdentifier = serviceProviderAccount.getProviderAccountIdentifier();
                    if (strProviderIdentifier.equals(strBank)) {
                        strBankCode = serviceProviderAccount.getProviderAccountIdentifier();
                        String strBranchCode = serviceProviderAccount.getProviderBranchCode();
                        strBankOtherDetails = "<DATA><BANK_CODE>" + strBankCode + "</BANK_CODE><BRANCH_CODE>" + strBranchCode + "</BRANCH_CODE></DATA>";
                        break;
                    }
                }

                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                double dblWithdrawalMin = Double.parseDouble(getParam(ke.skyworld.mbanking.mappapi.MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum());
                double dblWithdrawalMax = Double.parseDouble(getParam(ke.skyworld.mbanking.mappapi.MAPPAPIConstants.MAPP_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum());

                if (!strAmount.matches("^[1-9][0-9]*$")) {
                    strTitle = "ERROR: Bank Transfer";
                    strResponseText = "Please enter a valid amount for withdrawal";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                } else if (Double.parseDouble(strAmount) < dblWithdrawalMin) {
                    strTitle = "ERROR: Bank Transfer";
                    strResponseText = "Minimum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMin), "#,###.##");
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                } else if (Double.parseDouble(strAmount) > dblWithdrawalMax) {
                    strTitle = "ERROR: Bank Transfer";
                    strResponseText = "Maximum amount allowed is KES " + Utils.formatDouble(String.valueOf(dblWithdrawalMax), "#,###.##");
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                } else {
                    String strDate = MBankingDB.getDBDateTime().trim();
                    String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();

                    String strTransaction = "Bank Transfer Request";
                    String strTransactionDescription = "PL|BT|" + strBankCode + "|" + strReceiverBankAccountNumber;
                    strTransactionDescription = PESAAPI.shortenName(strTransactionDescription);


                    PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.FAMILY_BANK_PESALINK);


                    long getProductID = Long.parseLong(pesaParam.getProductId());

                    String strSenderIdentifier = pesaParam.getSenderIdentifier();
                    String strSenderAccount = pesaParam.getSenderAccount();
                    String strSenderName = pesaParam.getSenderName();

                    PESA pesa = new PESA();

                    pesa.setOriginatorID(strGUID);
                    pesa.setProductID(getProductID);
                    pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
                    pesa.setPESAAction(PESAConstants.PESAAction.B2B);
                    pesa.setCommand("PESALINK");
                    pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);
                    //pesa.setChargeProposed(null);

                    pesa.setInitiatorType("MSISDN");
                    pesa.setInitiatorIdentifier(strUsername);
                    pesa.setInitiatorAccount(strUsername);
                    //pesa.setInitiatorName(""); - Set after getting name from CBS
                    pesa.setInitiatorReference(strTraceID);
                    pesa.setInitiatorApplication("MAPP");
                    pesa.setInitiatorOtherDetails("<DATA/>");

                    pesa.setSourceType("ACCOUNT_NO");
                    pesa.setSourceIdentifier(strFromAccountNo);
                    pesa.setSourceAccount(strFromAccountNo);
                    //pesa.setSourceName(""); - Set after getting name from CBS
                    pesa.setSourceReference(strMAPPSessionID);
                    pesa.setSourceApplication("CBS");
                    pesa.setSourceOtherDetails("<DATA><BANK_CODE>" + AppConstants.strSACCOName + "</BANK_CODE><BRANCH_CODE>" + AppConstants.strSACCOName + "</BRANCH_CODE></DATA>");

                    pesa.setSenderType("ACCOUNT_NO");
                    pesa.setSenderIdentifier(strSenderIdentifier);
                    pesa.setSenderAccount(strSenderAccount);
                    pesa.setSenderName(strSenderName);
                    pesa.setSenderOtherDetails("<DATA/>");

                    pesa.setReceiverType("ACCOUNT_NO");
                    pesa.setReceiverIdentifier(strReceiverBankAccountNumber);
                    pesa.setReceiverAccount(strReceiverBankAccountNumber);
                    pesa.setReceiverName(strReceiverBankAccountName);
                    pesa.setReceiverOtherDetails(strBankOtherDetails);

                    pesa.setBeneficiaryType("ACCOUNT_NO");
                    pesa.setBeneficiaryIdentifier(strReceiverBankAccountNumber);
                    pesa.setBeneficiaryAccount(strReceiverBankAccountNumber);
                    pesa.setBeneficiaryName(strReceiverBankAccountName);
                    pesa.setBeneficiaryOtherDetails(strBankOtherDetails);

                    pesa.setBatchReference(strGUID);
                    pesa.setCorrelationReference(strTraceID);
                    pesa.setCorrelationApplication("MAPP");
                    pesa.setTransactionCurrency("KES");
                    pesa.setTransactionAmount(Double.parseDouble(strAmount));
                    pesa.setTransactionRemark(strTransactionDescription);
                    pesa.setCategory("PESALINK_TRANSFER");

                    pesa.setPriority(200);
                    pesa.setSendCount(0);

                    pesa.setSchedulePesa(PESAConstants.Condition.NO);
                    pesa.setPesaDateScheduled(strDate);
                    pesa.setPesaDateCreated(strDate);
                    pesa.setPESAXMLData("<DATA/>");

                    pesa.setPESAStatusCode(10);
                    pesa.setPESAStatusName("QUEUED");
                    pesa.setPESAStatusDescription("New PESA");
                    pesa.setPESAStatusDate(strDate);

                    String strWithdrawalStatus = "SUCCESS%&:IAN WAMBUA MUTUAcharges: 90.96";
                    //Navision.getPort().insertMpesaTransaction(strGUID, strMAPPSessionID, strTransaction, strTransactionDescription, strFromAccountNo, bdAmount, strUsername, strPassword, "MAPP", strMAPPSessionID, "MBANKING", strBankName);
                    String[] arrWithdrawalStatus = strWithdrawalStatus.split("%&:");
                    System.out.println("NAV Request Result Pesa Link:" + strWithdrawalStatus);
                    switch (arrWithdrawalStatus[0]) {
                        case "SUCCESS": {
                            String strMemberName = arrWithdrawalStatus[1].split("charges")[0].trim().trim();
                            pesa.setSourceName(strMemberName);
                            pesa.setInitiatorName(strMemberName);
                            String strRemark = "PL|BT|" + strBankCode + "|" + strReceiverBankAccountNumber + "|" + strUsername + "|" + strMemberName;
                            strRemark = PESAAPI.shortenName(strRemark);
                            pesa.setTransactionRemark(strRemark);

                            if (PESAProcessor.sendPESA(pesa) > 0) {
                                strAmount = Utils.formatAmount(strAmount);
                                strCharge = "YES";
                                strTitle = "Bank Transfer";
                                strResponseText = "Your request to transfer <b>KES " + strAmount + "</b> to has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                enResponseAction = CON;
                            } else {
                                boolean isReversed = true;
                                //Navision.getPort().reverseWithdrawalRequest(strGUID);
                                System.out.println("Reversal Status: " + isReversed);
                                enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                                enResponseAction = CON;
                            }
                            break;
                        }

                        /*case "SUCCESS": {
                            String strMemberName = arrWithdrawalStatus[1].trim();
                            pesa.setSourceName(strMemberName);
                            pesa.setInitiatorName(strMemberName);

                            String strRemark = "PL|BT|" + strBankCode + "|" + strReceiverBankAccountNumber + "|" + strUsername + "|" + strMemberName;
                            strRemark = PESAAPI.shortenName(strRemark);
                            pesa.setTransactionRemark(strRemark);
                            if (PESAProcessor.sendPESA(pesa) > 0) {
                                strAmount = Utils.formatAmount(strAmount);
                                strCharge = "YES";
                                strTitle = "Bank Transfer";
                                strResponseText = "Your request to transfer <b>KES " + strAmount + "</b> to has been received successfully.<br/>Kindly wait shortly as it is being processed";

                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                enResponseAction = CON;
                            } else {
                                enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                                enResponseAction = CON;

                                Navision.getPort().reverseWithdrawalRequest(strGUID);
                            }
                            break;
                        }*/
                        case "INCORRECT_PIN": {
                            strTitle = "ERROR: Incorrect PIN";
                            strResponseText = "You have entered an incorrect user PIN, please try again";

                            enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                            enResponseAction = CON;
                            break;
                        }
                        case "INVALID_ACCOUNT": {
                            strTitle = "ERROR: Invalid Account";
                            strResponseText = "You have selected an invalid account number, please try again";

                            enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                            enResponseAction = CON;
                            break;
                        }
                        case "INSUFFICIENT_BAL": {
                            strTitle = "ERROR: Insufficient Balance";
                            strResponseText = "You have insufficient balance to complete this request, please try again";

                            enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                            enResponseAction = CON;
                            break;
                        }
                        case "ACCOUNT_NOT_ACTIVE": {
                            strTitle = "ERROR: Account Not Active";
                            strResponseText = "Your account is inactive at the moment, please contact us or visit your nearest branch to get assistance";

                            enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                            enResponseAction = MAPPConstants.ResponseAction.END;
                            break;
                        }
                        case "TRANSACTION_EXISTS": {
                            strTitle = "ERROR: Withdrawal Failed";
                            strResponseText = "An error occurred processing your request. Please try again after a few minutes.";

                            enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                            enResponseAction = MAPPConstants.ResponseAction.END;
                            break;
                        }
                        case "BLOCKED": {
                            strTitle = "ERROR: Account Blocked";
                            strResponseText = "Your account is blocked at the moment, please contact us or visit your nearest branch to get assistance";

                            enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                            enResponseAction = MAPPConstants.ResponseAction.END;
                            break;
                        }
                        default: {
                            System.err.println("DEFAULT ON SWITCH -> " + this.getClass().getSimpleName() + "." + new Object() {
                            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + strWithdrawalStatus);
                            strTitle = "ERROR: Bank Transfer Failed";
                            strResponseText = "An error occurred processing your request. Please try again after a few minutes.";
                        }
                    }
                }

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }


    public MAPPResponse depositMoney(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_C2B);
            String strSender = pesaParam.getSenderIdentifier();

            /*
            <MSG SESSION_ID='12234' ORG_ID='12' TYPE='MOBILE_BANKING' ACTION='DEPOSIT_MONEY' VERSION='1.01'>"+
                <AMOUNT ACCOUNT_NO='1234567890'>1000</AMOUNT>
            </MSG>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strAccountNo = configXPath.evaluate("AMOUNT/@ACCOUNT_NO", ndRequestMSG).trim();
            String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
            BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

            double lnAmount = Utils.stringToDouble(strAmount);

            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

            boolean blPesaStkPushStatus = false;

            PESAAPI thePESAAPI = new PESAAPI();

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);
            String strTraceID = getTraceID(theMAPPRequest);

            blPesaStkPushStatus = thePESAAPI.pesa_C2B_Request(
                    strUsername,
                    strMemberName,
                    strTraceID,
                    "MAPP",
                    strAccountNo,
                    strMemberName,
                    "MBANKING_SERVER",
                    strTraceID,
                    strUsername,
                    strMemberName,
                    strAccountNo,
                    lnAmount,
                    "DEPOSIT");

            String strResponseText = "";
            String strTitle = "";
            String strCharge = "NO";

            if (blPesaStkPushStatus) {
                strTitle = "Deposit Request";
                strResponseText = "You will be prompted by M-PESA for payment<br/>Paybill no: <b>" + strSender + "</b><br/>" + "A/C: <b>" + strAccountNo + "</b><br/>" + "Amount: <b>KES " + strAmount + "</b>";
            } else {
                strTitle = "ERROR: Deposit Request";
                strResponseText = "Use the details below to pay via M-PESA<br/>Paybill no: <b>" + strSender + "</b><br/>" + "A/C: <b>" + strAccountNo + "</b><br/>" + "Amount: <b>KES " + strAmount + "</b>";

                enResponseAction = CON;
                enResponseStatus = FAILED;
            }

            //End USSD.

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse fundsTransfer(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("fundsTransfer");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                //Request
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = theMAPPRequest.getAppID();

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                // Root element - MSG
                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;

                MAPPConstants.ResponseAction enResponseAction = CON;
                MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                System.out.println("THE REQUEST: ");
                System.out.println(XmlUtils.convertNodeToStr(ndRequestMSG));

                String strFromAccountNo = configXPath.evaluate("FROM_ACCOUNT_NO", ndRequestMSG).trim();

                String[] strSourceArr = strFromAccountNo.split(Pattern.quote("||"));
                strFromAccountNo = strSourceArr[0];

                String strToAccountNo = configXPath.evaluate("TO_ACCOUNT_NO", ndRequestMSG).trim();
                String strToOption = configXPath.evaluate("TRANSFER_OPTION", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
                //String strAccountNo = strToAccountNo;

                //TODO: Ask Isaac for best way

                /*if(!(strToOption.equals("Account") || strToOption.equals("Account Number"))){
                    HashMap<Object, Object> accountDetails = getUserDetails(theMAPPRequest, strToOption, strAccountNo);

                    HashMap<String, HashMap <String, String>>  hmIFTDestAccounts = (HashMap<String, HashMap <String, String>>) accountDetails.get("accounts");
                    HashMap<String, String>  hmMemberDetails = (HashMap<String, String>) accountDetails.get("user_details");

                    if (hmMemberDetails != null && !hmMemberDetails.isEmpty()) {
                        strAccountNo = hmIFTDestAccounts.entrySet().iterator().next().getValue().get("number");
                    }
                }*/


                String strDestination = "ACCOUNT";

                if (strToOption.equals("ID Number")) {
                    strDestination = "CUSTOMER_NO";
                } else if (strToOption.equals("Mobile Number")) {
                    strDestination = "MSISDN";
                }

                //END

                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
                String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

                String strTransactionReference = strTransactionID;
                String strSourceAccount = strFromAccountNo;
                //String strDestinationAccount = strAccountNo;

                String strTraceID = getTraceID(theMAPPRequest);
                String strTransactionDescription = "Internal Funds Transfer. Source A/C: " + strSourceAccount + " - Destination A/C: " + strToAccountNo;

                String strTitle = "";
                String strResponseText = "";
                String strCharge = "NO";

                String strOriginatorId = UUID.randomUUID().toString();
                TransactionWrapper<FlexicoreHashMap> internalFundsTransferWrapper = CBSAPI.internalFundsTransfer(
                        strUsername,
                        "MSISDN",
                        strUsername,
                        "APP_ID",
                        strAppID,
                        strOriginatorId,
                        strSourceAccount,
                        strToAccountNo,
                        Double.parseDouble(strAmount),
                        strTransactionDescription,
                        theMAPPRequest.getTraceID(),
                        "MAPP",
                        "MBANKING");

                FlexicoreHashMap internalFundsTransferMap = internalFundsTransferWrapper.getSingleRecord();

                CBSAPI.SMSMSG cbsMSG = internalFundsTransferMap.getValue("msg_object");
                sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "INTERNAL_FUNDS_TRANSFER", theMAPPRequest);

                String strMemberName = getUserFullName(theMAPPRequest, strUsername);

                ChannelService channelService = new ChannelService();
                channelService.setOriginatorId(strOriginatorId);
                channelService.setTransactionCategory("INTERNAL_FUNDS_TRANSFER");

                if (internalFundsTransferWrapper.hasErrors()) {

                    strTitle = "ERROR: Internal Funds Transfer";

                    System.out.println("Internal Funds Transfer Error: " + internalFundsTransferMap.getStringValue("display_message"));
                    strResponseText = internalFundsTransferMap.getStringValueOrIfNull("display_message", "An error occurred. Please try again after a few minutes.");

                    enResponseStatus = ERROR;

                    channelService.setTransactionStatusCode(104);
                    channelService.setTransactionStatusName("FAILED");
                    channelService.setTransactionStatusDescription(internalFundsTransferMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
                } else {

                    strTitle = "Transaction Accepted";
                    strResponseText = "Your funds transfer has been received successfully. Kindly wait as it is being processed.";
                    strCharge = "YES";

                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Transaction Received Successfully");
                    channelService.setBeneficiaryReference(internalFundsTransferMap.getStringValue("cbs_transaction_reference"));
                    channelService.setSourceReference(internalFundsTransferMap.getStringValue("cbs_transaction_reference"));
                }
                channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

                channelService.setInitiatorType("MSISDN");
                channelService.setInitiatorIdentifier(strUsername);
                channelService.setInitiatorAccount(strUsername);
                channelService.setInitiatorName(strMemberName);
                channelService.setInitiatorReference(theMAPPRequest.getTraceID());
                channelService.setInitiatorApplication("MAPP");
                channelService.setInitiatorOtherDetails("<DATA/>");

                channelService.setSourceType("ACCOUNT_NO");
                channelService.setSourceIdentifier(strSourceAccount);
                channelService.setSourceAccount(strSourceAccount);
                channelService.setSourceName(strSourceAccount);
                channelService.setSourceApplication("CBS");
                channelService.setSourceOtherDetails("<DATA/>");

                channelService.setBeneficiaryType("ACCOUNT_NO");
                channelService.setBeneficiaryIdentifier(strToAccountNo);
                channelService.setBeneficiaryAccount(strToAccountNo);
                channelService.setBeneficiaryName(strToAccountNo);
                channelService.setBeneficiaryApplication("CBS");
                channelService.setBeneficiaryOtherDetails("<DATA/>");

                channelService.setTransactionCurrency("KES");
                channelService.setTransactionAmount(Double.parseDouble(strAmount));

                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.IFT_ACCOUNT_TO_ACCOUNT.getValue(),
                        Double.parseDouble(strAmount));

                if (chargesWrapper.hasErrors()) {
                    channelService.setTransactionCharge(0.00);
                    channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

                } else {
                    channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                    channelService.setTransactionOtherDetails("<DATA/>");
                }

                channelService.setTransactionRemark(strTransactionDescription);
                ChannelService.insertService(channelService);

                enResponseAction = CON;

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getTransferAccounts(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

            //Accounts HashMap
            /*{Salary Acc (5-04-00010-02)=5-04-00010-02, Micro-cred (4-61-90010-01)=4-61-90010-01}*/
            LinkedHashMap<String, String> fromAccounts = getMemberAccountsList(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE_IFT);
            LinkedHashMap<String, String> toAccounts = getMemberAccountsList(theMAPPRequest, MAPPAPIConstants.AccountType.DEPOSIT_IFT);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Transfer Accounts";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            Element elFromAccounts = doc.createElement("FROM_ACCOUNTS");
            elData.appendChild(elFromAccounts);

            Element elToAccountTypes = doc.createElement("TO_ACCOUNT_TYPES");
            Element elAccountTypeMy = doc.createElement("ACCOUNT_TYPE");
            elAccountTypeMy.setTextContent("MY Account");
            elAccountTypeMy.setAttribute("TYPE_ID", "MY_ACCOUNT");
            elToAccountTypes.appendChild(elAccountTypeMy);

            Element elAccountTypeOther = doc.createElement("ACCOUNT_TYPE");
            elAccountTypeOther.setTextContent("OTHER Account");
            elAccountTypeOther.setAttribute("TYPE_ID", "OTHER_ACCOUNT");
            elToAccountTypes.appendChild(elAccountTypeOther);

            elData.appendChild(elToAccountTypes);

            Element elToAccounts = doc.createElement("TO_ACCOUNTS");
            elData.appendChild(elToAccounts);

            for (String accountNumber : fromAccounts.keySet()) {

                String strAccountName = fromAccounts.get(accountNumber);

                Element elAccount = doc.createElement("FROM_ACCOUNT");
                elAccount.setTextContent(strAccountName);
                elFromAccounts.appendChild(elAccount);

                // set attribute NO to ACCOUNT element
                Attr attrNO = doc.createAttribute("NO");
                attrNO.setValue(accountNumber);
                elAccount.setAttributeNode(attrNO);
            }

            for (String accountNumber : toAccounts.keySet()) {
                String strAccountName = toAccounts.get(accountNumber);

                Element elAccount = doc.createElement("TO_ACCOUNT");
                elAccount.setTextContent(strAccountName);
                elToAccounts.appendChild(elAccount);

                // set attribute NO to ACCOUNT element
                Attr attrNO = doc.createAttribute("NO");
                attrNO.setValue(accountNumber);
                elAccount.setAttributeNode(attrNO);
            }

            //Option for Transfer to Other Account
            /*Element elOtherAccount = doc.createElement("TO_ACCOUNT");
            elOtherAccount.setTextContent("OTHER Account");
            elOtherAccount.setAttribute("NO", "OTHER");
            elToAccounts.appendChild(elOtherAccount);*/

            //Option for Transfer to M-PESA
            /*Element elMpesaAccount = doc.createElement("TO_ACCOUNT");
            elMpesaAccount.setTextContent("Withdraw to M-Pesa");
            elMpesaAccount.setAttribute("NO", "MPESA");
            elToAccounts.appendChild(elMpesaAccount);*/

            String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.INTERNAL_FUNDS_TRANSFER).getMinimum();
            String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.INTERNAL_FUNDS_TRANSFER).getMaximum();

            //create element AMOUNT_LIMITS and append to element DATA
            Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
            Element elMinAmount = doc.createElement("MIN_AMOUNT");
            elMinAmount.setTextContent(String.valueOf(strMin));
            Element elMaxAmount = doc.createElement("MAX_AMOUNT");
            elMaxAmount.setTextContent(String.valueOf(strMax));
            elWithdrawalLimits.appendChild(elMinAmount);
            elWithdrawalLimits.appendChild(elMaxAmount);
            elData.appendChild(elWithdrawalLimits);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse accountBalanceEnquiry(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='ACCOUNT_BALANCE' VERSION='1.01'>
                      <ACCOUNT_NO>123456</ACCOUNT_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;

            String strAccountNo = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();

            MAPPConstants.ResponseStatus enResponseStatus = ERROR;

            String strProduct = "";
            String strDate = "";
            String strBookBalance = "";
            String strAvailableBalance = "";

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

           /* TransactionWrapper<FlexicoreHashMap> balanceEnquiryChargesWrapper = CBSAPI.accountBalanceEnquiryCharges(UUID.randomUUID().toString(), strUsername, strAccountNo);

            if (balanceEnquiryChargesWrapper.hasErrors()) {
                strTitle = "ERROR: Account Balance";
                strResponseText = "An error occurred. Please try again after a few minutes.";

                enResponseStatus = ERROR;
            } else {


            }
            */

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);
            String strSessionID = fnModifyMAPPSessionID(theMAPPRequest);

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.accountBalanceEnquirySINGLE(strUsername, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, strSessionID);

            FlexicoreHashMap accountBalanceResultMap = accountBalanceEnquiryWrapper.getSingleRecord();

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue());

            String strAccountName = "";

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                strTitle = "ERROR: Account Balance";
                strResponseText = "An error occurred. Please try again after a few minutes.";

                enResponseStatus = ERROR;

                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(accountBalanceResultMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
                strAccountName = strAccountNo;

            } else {

                //FlexicoreHashMap accountBalanceMap = accountBalanceResultMap.getFlexicoreHashMap("account_balance");
                strAccountName = accountBalanceResultMap.getStringValueOrIfNull("account_label", "").trim();

                String strAccountBalance = accountBalanceResultMap.getStringValueOrIfNull("account_balance", "0").trim();

                strAccountBalance = Utils.formatDouble(strAccountBalance, "#,##0.00");

                strTitle = strAccountName;
                strResponseText = "Your account balance for <b>" + strAccountName + " </b> is: <b>KES " + strAccountBalance + "</b>";
                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                strCharge = "YES";

                CBSAPI.SMSMSG cbsMSG = accountBalanceResultMap.getValue("msg_object");

                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "BALANCE_ENQUIRY", theMAPPRequest);

                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Balance Enquiry Completed Successfully");
            }

            elData.setTextContent(strResponseText);

            channelService.setBeneficiaryReference("");
            channelService.setSourceReference("");
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strUsername);
            channelService.setInitiatorAccount(strUsername);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theMAPPRequest.getTraceID());
            channelService.setInitiatorApplication("MAPP");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNo);
            channelService.setSourceAccount(strAccountNo);
            channelService.setSourceName(strAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strUsername);
            channelService.setBeneficiaryAccount(strUsername);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Balance Enquiry for A/C: " + strAccountNo);
            ChannelService.insertService(channelService);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse accountBalanceEnquiryALL(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='ACCOUNT_BALANCE' VERSION='1.01'>
                      <ACCOUNT_NO>123456</ACCOUNT_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;

            //String strAccountNo = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();

            MAPPConstants.ResponseStatus enResponseStatus = ERROR;

            String strProduct = "";
            String strDate = "";
            String strBookBalance = "";
            String strAvailableBalance = "";

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

           /* TransactionWrapper<FlexicoreHashMap> balanceEnquiryChargesWrapper = CBSAPI.accountBalanceEnquiryCharges(UUID.randomUUID().toString(), strUsername, strAccountNo);

            if (balanceEnquiryChargesWrapper.hasErrors()) {
                strTitle = "ERROR: Account Balance";
                strResponseText = "An error occurred. Please try again after a few minutes.";

                enResponseStatus = ERROR;
            } else {


            }
            */

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.accountBalanceEnquiry(strUsername, "MSISDN", strUsername, "APP_ID", strAppID, "ALL");

            FlexicoreHashMap accountBalanceResultMap = accountBalanceEnquiryWrapper.getSingleRecord();

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue());

            //String strAccountName = "";

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                strTitle = "ERROR: Account Balance";
                strResponseText = "An error occurred. Please try again after a few minutes.";

                enResponseStatus = ERROR;

                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(accountBalanceResultMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

            } else {


                FlexicoreArrayList accountsList = accountBalanceResultMap.getFlexicoreArrayList("payload");

                StringBuilder accountsMSGBuilder = new StringBuilder();

                // Element elLoans = doc.createElement("LOANS");

                for (FlexicoreHashMap accountsMap : accountsList) {
                    //String strAccountName = accountsMap.getStringValueOrIfNull("loan_type_name", "").trim();

                    String strAccountName = accountsMap.getStringValueOrIfNull("account_type_name", "").trim();
                    String strAccountNumber = accountsMap.getStringValueOrIfNull("account_number", "").trim();
                    String strAccountWithdrawableBalance = accountsMap.getStringValueOrIfNull("account_balance", "0").trim();

                    double dbAccountBalance = Utils.stringToDouble(strAccountWithdrawableBalance.replace("-", ""));

                    if (dbAccountBalance <= 0) {
                        continue;
                    }

                    strAccountWithdrawableBalance = Utils.formatDouble(strAccountWithdrawableBalance, "#,##0.00");

                    accountsMSGBuilder.append("<div style='text-align: left;'>Name: <b>" + strAccountName + "-" + strAccountNumber + "</b></div>");
                    accountsMSGBuilder.append("<div style='text-align: left;'>Balance : <b style='color: #3C795B;'>KES " + strAccountWithdrawableBalance + "</b><div><br/>");
                }


                strTitle = "Account Balances";
                strResponseText = accountsMSGBuilder.toString();
                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                strCharge = "YES";

                CBSAPI.SMSMSG cbsMSG = accountBalanceResultMap.getValue("msg_object");

                sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "BALANCE_ENQUIRY", theMAPPRequest);

                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Balance Enquiry Completed Successfully");
            }

            elData.setTextContent(strResponseText);

            channelService.setBeneficiaryReference("");
            channelService.setSourceReference("");
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strUsername);
            channelService.setInitiatorAccount(strUsername);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theMAPPRequest.getTraceID());
            channelService.setInitiatorApplication("MAPP");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier("ALL_ACCOUNTS");
            channelService.setSourceAccount("ALL_ACCOUNTS");
            channelService.setSourceName("ALL_ACCOUNTS");
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strUsername);
            channelService.setBeneficiaryAccount(strUsername);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Balance Enquiry");
            ChannelService.insertService(channelService);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse accountStatement(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            // strPassword = APIUtils.hashPIN(strPassword, strUsername);
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strAccountNo = configXPath.evaluate("ACCOUNT_NO", theMAPPRequest.getMSG()).trim();
            String strStartDate = configXPath.evaluate("FROM", theMAPPRequest.getMSG()).trim();
            String strEndDate = configXPath.evaluate("TO", theMAPPRequest.getMSG()).trim();

            /*Start of Duration Change*/

            int intMaximumTransactionCount = 100;
            String strMaximumTransactionCount = "";

            try {
                strMaximumTransactionCount = configXPath.evaluate("MAXIMUM_TRANSACTION_COUNT", theMAPPRequest.getMSG()).trim();
            } catch (Exception ignored) {
            }

            if (!strMaximumTransactionCount.equals("")) {
                intMaximumTransactionCount = Integer.parseInt(strMaximumTransactionCount);
            }

            strMaximumTransactionCount = "6";

            intMaximumTransactionCount = Integer.parseInt("100");

            /*if (!strMaximumTransactionCount.equals("")) {
                intMaximumTransactionCount = Integer.parseInt("6");
            }

            intMaximumTransactionCount = Integer.parseInt("6");*/

            /*End of Duration Change*/

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());


            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Account Statement";

            Element elData = doc.createElement("DATA");
            String strCharge = "NO";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TABLE;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountFullStatement(strUsername, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, "100", strStartDate + " 00:00:00", strEndDate + " 23:59:59");

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.ACCOUNT_FULL_STATEMENT.getValue());

            if (miniStatementWrapper.hasErrors()) {
                strTitle = "Error: Account Statement Failed";
                elData.setTextContent("An error occurred while processing your request. Please try again in a few minutes");
                enResponseStatus = ERROR;

                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(miniStatementMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

            } else {
                FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");
                if (allTransactionsList.isEmpty()) {
                    enResponseStatus = FAILED;
                    strCharge = "NO";
                    strTitle = "Error: No Statements Found";
                    elData.setTextContent("You do not have any statements within this time period");

                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("You do not have any statements within this time period");

                } else {

                    String strAvailableBalance = miniStatementMap.getStringValue("account_available_balance");

                    strAvailableBalance = Utils.formatDouble(strAvailableBalance, "#,##0.00");

                    Element elBalance = doc.createElement("BALANCE");
                    elBalance.setTextContent(strAvailableBalance);
                    elData.appendChild(elBalance);

                    Element elAccountNo = doc.createElement("ACCOUNTNO");
                    elAccountNo.setTextContent(strAccountNo);
                    elData.appendChild(elAccountNo);

                    Element elAccountName = doc.createElement("NAME");
                    elAccountName.setTextContent(miniStatementMap.getStringValue("account_name"));
                    elData.appendChild(elAccountName);

                    Element elTable = doc.createElement("TABLE");
                    elData.appendChild(elTable);

                    Element elTrHeading = doc.createElement("TR");
                    elTable.appendChild(elTrHeading);

                    Element elThHeading1 = doc.createElement("TH");
                    elThHeading1.setTextContent("Description");
                    elTrHeading.appendChild(elThHeading1);

                    Element elThHeading2 = doc.createElement("TH");
                    elThHeading2.setTextContent("Amount");
                    elTrHeading.appendChild(elThHeading2);

                    Element elThHeading3 = doc.createElement("TH");
                    elThHeading3.setTextContent("Date");
                    elTrHeading.appendChild(elThHeading3);

                    Element elThHeading4 = doc.createElement("TH");
                    elThHeading4.setTextContent("Reference");
                    elTrHeading.appendChild(elThHeading4);

                    Element elThHeading5 = doc.createElement("TH");
                    elThHeading5.setTextContent("Running Bal");
                    elTrHeading.appendChild(elThHeading5);

                    int i = 0;
                    for (FlexicoreHashMap transactionMap : allTransactionsList) {
                        String strMSGTransactionReference = transactionMap.getStringValue("transaction_reference");
                        String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("transaction_date_time");
                        String strMSGFormattedTransactionAmount = transactionMap.getStringValue("transaction_amount");
                        String strTransactionType = transactionMap.getStringValue("transaction_type");
                        String strMSGTransactionDescription = transactionMap.getStringValueOrIfNull("transaction_description", "");
                        String strMSGRunningBalance = transactionMap.getStringValue("running_balance");

                        //strMSGRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                        Element elTrBody = doc.createElement("TR");
                        elTable.appendChild(elTrBody);

                        Element elTDBody1 = doc.createElement("TD");
                        elTDBody1.setTextContent(strMSGTransactionDescription);
                        elTrBody.appendChild(elTDBody1);

                        Element elTDBody2 = doc.createElement("TD");
                        elTDBody2.setTextContent(strMSGFormattedTransactionAmount);
                        elTrBody.appendChild(elTDBody2);

                        Element elTDBody3 = doc.createElement("TD");
                        elTDBody3.setTextContent(strMSGFormattedTransactionDateTime);
                        elTrBody.appendChild(elTDBody3);

                        Element elTDBody4 = doc.createElement("TD");
                        elTDBody4.setTextContent(strMSGTransactionReference);
                        elTrBody.appendChild(elTDBody4);

                        Element elTDBody5 = doc.createElement("TD");
                        elTDBody5.setTextContent(strMSGRunningBalance);
                        elTrBody.appendChild(elTDBody5);

                        if (i >= intMaximumTransactionCount) {
                            break;
                        }

                        i++;
                    }
                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Account Statement Generated Successfully");

                }
            }

            channelService.setBeneficiaryReference("");
            channelService.setSourceReference("");
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strUsername);
            channelService.setInitiatorAccount(strUsername);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theMAPPRequest.getTraceID());
            channelService.setInitiatorApplication("MAPP");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNo);
            channelService.setSourceAccount(strAccountNo);
            channelService.setSourceName(strAccountNo);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strUsername);
            channelService.setBeneficiaryAccount(strUsername);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.ACCOUNT_FULL_STATEMENT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Account Full Statement for A/C: " + strAccountNo);
            ChannelService.insertService(channelService);


             /*
             //Response from NAV is:
            <Accounts>
                <Account>
                    <AccNo>5000000127000</AccNo>
                    <AccName>FOSA Savings Accounts 00</AccName>
                </Account>
                <Account>
                    <AccNo>5000000127001</AccNo>
                    <AccName>FOSA Savings Accounts 01</AccName>
                </Account>
            </Accounts>
             */


            // Root element - MSG

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);


            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

  /*  public MAPPResponse accountStatementBase64(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            // strPassword = APIUtils.hashPIN(strPassword, strUsername);
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strAccountNo = configXPath.evaluate("ACCOUNT_NO", theMAPPRequest.getMSG()).trim();
            String strStartDate = configXPath.evaluate("FROM", theMAPPRequest.getMSG()).trim();
            String strEndDate = configXPath.evaluate("TO", theMAPPRequest.getMSG()).trim();

            *//*Start of Duration Change*//*
            int intMaximumTransactionCount = 100;
            String strMaximumTransactionCount = "";

            try {
                strMaximumTransactionCount = configXPath.evaluate("MAXIMUM_TRANSACTION_COUNT", theMAPPRequest.getMSG()).trim();
            } catch (Exception ignored) {
            }

            if (!strMaximumTransactionCount.equals("")) {
                intMaximumTransactionCount = Integer.parseInt(strMaximumTransactionCount);
            }

            strMaximumTransactionCount = "6";

            intMaximumTransactionCount = Integer.parseInt("6");

            *//*End of Duration Change*//*

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountMiniStatement(strTransactionID, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, strMaximumTransactionCount, "0001-01-01T00:00:00", "0001-01-01T00:00:00");

            *//* TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountMiniStatement(strTransactionID, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, strMaximumTransactionCount, strStartDate + "T00:00:00", strEndDate + "T11:59:59");

            *//*

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Account Statement";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            Element elData = doc.createElement("DATA");
            String strCharge = "NO";

            if (miniStatementWrapper.hasErrors()) {
                strTitle = "Error: Account Statement Failed";
                elData.setTextContent("An error occurred while processing your request. Please try again in a few minutes");

            } else {
                FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");
                if (allTransactionsList.isEmpty()) {
                    enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                    strCharge = "NO";
                    strTitle = "Error: No Statements Found";
                    elData.setTextContent("You do not have any statements within this time period");
                } else {

                    String theAccountStatement = AccountStatements.getAccountStatementHTML();

                    String strFormattedPeriod = DateTime.convertStringToDateToString(strStartDate, "yyyy-MM-dd", "dd MMM yyyy");
                    strFormattedPeriod = strFormattedPeriod + " to ";
                    strFormattedPeriod = strFormattedPeriod + DateTime.convertStringToDateToString(strEndDate, "yyyy-MM-dd", "dd MMM yyyy");

                    theAccountStatement = theAccountStatement.replace("[STATEMENT_PERIOD]", Misc.escapeHtmlEntity(strFormattedPeriod));

                    String strAvailableBalance = miniStatementMap.getStringValue("account_available_balance");

                    strAvailableBalance = Utils.formatDouble(strAvailableBalance, "#,##0.00");

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_BALANCE]", "KES " + strAvailableBalance);

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_NAME]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_name")));

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_NUMBER]", Misc.escapeHtmlEntity(strAccountNo));

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_HOLDER]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_holder")));

                    StringBuilder builder = new StringBuilder();

                    double dblTotalPaidIn = 0;
                    double dblTotalPaidOut = 0;

                    int size = allTransactionsList.size();
                    for (int index = size - 1; index >= 0; index--) {
                        FlexicoreHashMap transactionMap = allTransactionsList.get(index);

                        String strMSGTransactionReference = transactionMap.getStringValue("reference");
                        String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("raw_date");
                        String strAmount = transactionMap.getStringValue("amount");
                        String strMSGTransactionDescription = transactionMap.getStringValue("description");
                        String strMSGTransactionComments = transactionMap.getStringValue("comments");
                        String strMSGRunningBalance = transactionMap.getStringValue("running_balance");
                        String strDebitCredit = transactionMap.getStringValue("debit_credit");

                        strAmount = strAmount.replace("-", "");

                        strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd'T'HH:mm:ss", "dd MMM yyyy");

                        builder.append("<tr>\n" +
                                "                <td class='statement-header-acc-stmnt-date'>" + Misc.escapeHtmlEntity(strMSGFormattedTransactionDateTime) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-description'>" + Misc.escapeHtmlEntity(strMSGTransactionDescription + " " + strMSGTransactionComments) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-paid-in'>" +
                                (strDebitCredit.equalsIgnoreCase("C") ? Utils.formatDouble(strAmount, "#,##0.00") : "") + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-paid-out'>" +
                                (strDebitCredit.equalsIgnoreCase("D") ? Utils.formatDouble(strAmount, "#,##0.00") : "") + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-balance'>" + Utils.formatDouble(strMSGRunningBalance, "#,##0.00") + "</td>\n" +
                                "            </tr>");

                        if (strDebitCredit.equalsIgnoreCase("C")) {
                            dblTotalPaidIn += Double.parseDouble(strAmount);
                        } else {
                            dblTotalPaidOut += Double.parseDouble(strAmount);
                        }
                    }

                    theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_IN]", "KES " + Utils.formatDouble(dblTotalPaidIn, "#,##0.00"));
                    theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_OUT]", "KES " + Utils.formatDouble(dblTotalPaidOut, "#,##0.00"));
                    theAccountStatement = theAccountStatement.replace("[THE_ACCOUNT_STATEMENT_DETAILS]", builder.toString());
                    theAccountStatement = AccountStatements.generateAccountStatementPDF(theAccountStatement, strAccountNo);

                    elData.setTextContent(theAccountStatement);
                }
            }

             *//*
             //Response from NAV is:
            <Accounts>
                <Account>
                    <AccNo>5000000127000</AccNo>
                    <AccName>FOSA Savings Accounts 00</AccName>
                </Account>
                <Account>
                    <AccNo>5000000127001</AccNo>
                    <AccName>FOSA Savings Accounts 01</AccName>
                </Account>
            </Accounts>
             *//*


            // Root element - MSG

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }
*/

    /*public MAPPResponse accountStatementBase64(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            // strPassword = APIUtils.hashPIN(strPassword, strUsername);
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strAccountNo = configXPath.evaluate("ACCOUNT_NO", theMAPPRequest.getMSG()).trim();
            String strStartDate = configXPath.evaluate("FROM", theMAPPRequest.getMSG()).trim();
            String strEndDate = configXPath.evaluate("TO", theMAPPRequest.getMSG()).trim();

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

           *//* TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountMiniStatement(strTransactionID, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, strMaximumTransactionCount, "0001-01-01T00:00:00", "0001-01-01T00:00:00");*//*

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Account Statement";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            Element elData = doc.createElement("DATA");
            String strCharge = "NO";

            TransactionWrapper<FlexicoreHashMap> wrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    TBL_CUSTOMER_REGISTER_SIGNATORIES,
                    new FilterPredicate("primary_mobile_number = :primary_mobile_number"),
                    new FlexicoreHashMap().addQueryArgument(":primary_mobile_number", strUsername));

            FlexicoreHashMap signatoryDetailsMap = wrapper.getSingleRecord();

            String primaryEmailAddress = signatoryDetailsMap.getStringValue("primary_email_address");

            if(primaryEmailAddress == null){

                enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                strCharge = "NO";
                strTitle = "Error: Missing Email Address";
                elData.setTextContent("You do not have an email address to send the statement to. Please contact us to update your email address.");

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);
                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

            }else{

                elData.setTextContent("Your account statement request has been received and is being processed. The statement will be sent to email address: "+ primaryEmailAddress);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

                new Thread(()->{

                    TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountMiniStatement(strTransactionID, "MSISDN", strUsername,
                            "APP_ID", strAppID, strAccountNo, "0", strStartDate + "T00:00:00", strEndDate + "T11:59:59");

                    FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

                    if (miniStatementWrapper.hasErrors()) {
                        String strMsg = "Dear member, sorry an error occurred while processing your account statement request. Please try again later.";
                        sendSMS(strUsername, strMsg, MSGConstants.MSGMode.SAF, 210, "ACCOUNT_STATEMENT", theMAPPRequest);

                    } else {
                        FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");

                        String theAccountStatement = AccountStatements.getAccountStatementHTML();

                        String strFormattedPeriod = DateTime.convertStringToDateToString(strStartDate, "yyyy-MM-dd", "dd MMM yyyy");
                        strFormattedPeriod = strFormattedPeriod + " to ";
                        strFormattedPeriod = strFormattedPeriod + DateTime.convertStringToDateToString(strEndDate, "yyyy-MM-dd", "dd MMM yyyy");

                        theAccountStatement = theAccountStatement.replace("[STATEMENT_PERIOD]", Misc.escapeHtmlEntity(strFormattedPeriod));

                        String strAvailableBalance = miniStatementMap.getStringValue("account_available_balance");

                        strAvailableBalance = Utils.formatDouble(strAvailableBalance, "#,##0.00");

                        theAccountStatement = theAccountStatement.replace("[ACCOUNT_BALANCE]", "KES " + strAvailableBalance);

                        theAccountStatement = theAccountStatement.replace("[ACCOUNT_NAME]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_name")));

                        theAccountStatement = theAccountStatement.replace("[ACCOUNT_NUMBER]", Misc.escapeHtmlEntity(strAccountNo));

                        theAccountStatement = theAccountStatement.replace("[ACCOUNT_HOLDER]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_holder")));

                        StringBuilder builder = new StringBuilder();

                        double dblTotalPaidIn = 0;
                        double dblTotalPaidOut = 0;

                        int size = allTransactionsList.size();

                        if (size > 0) {
                            for (int index = size - 1; index >= 0; index--) {
                                FlexicoreHashMap transactionMap = allTransactionsList.get(index);

                                String strMSGTransactionReference = transactionMap.getStringValue("reference");
                                String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("raw_date");
                                String strAmount = transactionMap.getStringValue("amount");
                                String strMSGTransactionDescription = transactionMap.getStringValue("description");
                                String strMSGTransactionComments = transactionMap.getStringValue("comments");
                                String strMSGRunningBalance = transactionMap.getStringValue("running_balance");
                                String strDebitCredit = transactionMap.getStringValue("debit_credit");

                                strAmount = strAmount.replace("-", "");

                                strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd'T'HH:mm:ss", "dd MMM yyyy");

                                builder.append("<tr>\n" +
                                        "                <td class='statement-header-acc-stmnt-date'>" + Misc.escapeHtmlEntity(strMSGFormattedTransactionDateTime) + "</td>\n" +
                                        "                <td class='statement-header-acc-stmnt-description'>" + Misc.escapeHtmlEntity(strMSGTransactionDescription + " " + strMSGTransactionComments) + "</td>\n" +
                                        "                <td class='statement-header-acc-stmnt-paid-in'>" +
                                        (strDebitCredit.equalsIgnoreCase("C") ? Utils.formatDouble(strAmount, "#,##0.00") : "") + "</td>\n" +
                                        "                <td class='statement-header-acc-stmnt-paid-out'>" +
                                        (strDebitCredit.equalsIgnoreCase("D") ? Utils.formatDouble(strAmount, "#,##0.00") : "") + "</td>\n" +
                                        "                <td class='statement-header-acc-stmnt-balance'>" + Utils.formatDouble(strMSGRunningBalance, "#,##0.00") + "</td>\n" +
                                        "            </tr>");

                                if (strDebitCredit.equalsIgnoreCase("C")) {
                                    dblTotalPaidIn += Double.parseDouble(strAmount);
                                } else {
                                    dblTotalPaidOut += Double.parseDouble(strAmount);
                                }
                            }
                        }

                        theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_IN]", "KES " + Utils.formatDouble(dblTotalPaidIn, "#,##0.00"));
                        theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_OUT]", "KES " + Utils.formatDouble(dblTotalPaidOut, "#,##0.00"));
                        theAccountStatement = theAccountStatement.replace("[THE_ACCOUNT_STATEMENT_DETAILS]", builder.toString());
                        String strFilePath = AccountStatements.generateAccountStatementPDF(theAccountStatement, strAccountNo, signatoryDetailsMap.getStringValue("primary_identity_no"));


                    }

                }).start();

            }

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    */

    public MAPPResponse accountStatementBase64(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            // strPassword = APIUtils.hashPIN(strPassword, strUsername);
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strAccountNo = configXPath.evaluate("ACCOUNT_NO", theMAPPRequest.getMSG()).trim();
            String strStartDate = configXPath.evaluate("FROM", theMAPPRequest.getMSG()).trim();
            String strEndDate = configXPath.evaluate("TO", theMAPPRequest.getMSG()).trim();

            /*Start of Duration Change*/
            int intMaximumTransactionCount = 100;
            String strMaximumTransactionCount = "";

            try {
                strMaximumTransactionCount = configXPath.evaluate("MAXIMUM_TRANSACTION_COUNT", theMAPPRequest.getMSG()).trim();
            } catch (Exception ignored) {
            }

            if (!strMaximumTransactionCount.equals("")) {
                intMaximumTransactionCount = Integer.parseInt(strMaximumTransactionCount);
            }

            strMaximumTransactionCount = "6";

            intMaximumTransactionCount = Integer.parseInt("100");

            /*End of Duration Change*/

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

           /* TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountMiniStatement(strTransactionID, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, strMaximumTransactionCount, "0001-01-01T00:00:00", "0001-01-01T00:00:00");
*/

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountFullStatement(strUsername, "MSISDN", strUsername,
                    "APP_ID", strAppID, strAccountNo, "100", strStartDate + " 00:00:00", strEndDate + " 23:59:59");

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Account Statement";

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            Element elData = doc.createElement("DATA");
            String strCharge = "NO";


            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.ACCOUNT_FULL_STATEMENT.getValue());

            String strUSSDSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
            String strDatetime = MBankingDB.getDBDateTime().trim();
            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");


            if (miniStatementWrapper.hasErrors()) {
                strTitle = "Error: Account Statement Failed";
                elData.setTextContent("An error occurred while processing your request. Please try again in a few minutes");

                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(miniStatementMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

            } else {
                FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");
                if (allTransactionsList.isEmpty()) {
                    enResponseStatus = FAILED;
                    strCharge = "NO";
                    strTitle = "Error: No Statements Found";
                    elData.setTextContent("You do not have any statements within this time period");


                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("You do not have any statements within this time period");

                } else {

                    String theAccountStatement = AccountStatements.getAccountStatementHTML();

                    String strFormattedPeriod = DateTime.convertStringToDateToString(strStartDate, "yyyy-MM-dd", "dd MMM yyyy");
                    strFormattedPeriod = strFormattedPeriod + " to ";
                    strFormattedPeriod = strFormattedPeriod + DateTime.convertStringToDateToString(strEndDate, "yyyy-MM-dd", "dd MMM yyyy");

                    theAccountStatement = theAccountStatement.replace("[STATEMENT_PERIOD]", Misc.escapeHtmlEntity(strFormattedPeriod));

                    String strAvailableBalance = miniStatementMap.getStringValue("account_available_balance");

                    strAvailableBalance = Utils.formatDouble(strAvailableBalance, "#,##0.00");

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_BALANCE]", "KES " + strAvailableBalance);

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_NAME]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_name")));
                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_NUMBER]", Misc.escapeHtmlEntity(strAccountNo));
                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_HOLDER]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_holder")));

                    StringBuilder builder = new StringBuilder();

                    double dblTotalPaidIn = 0;
                    double dblTotalPaidOut = 0;

                    int i = 0;
                    int size = allTransactionsList.size();
                    for (int index = size - 1; index >= 0; index--) {
                        FlexicoreHashMap transactionMap = allTransactionsList.get(index);

                        String strMSGTransactionReference = transactionMap.getStringValue("transaction_reference");
                        String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("transaction_date_time");
                        String strMSGFormattedTransactionAmount = transactionMap.getStringValue("transaction_amount");
                        String strMSGTransactionDescription = transactionMap.getStringValueOrIfNull("transaction_description", "");
                        String strMSGRunningBalance = transactionMap.getStringValue("running_balance");

                        String strDebitCredit;

                        if (strMSGFormattedTransactionAmount.trim().startsWith("-")) {
                            strDebitCredit = "D";
                        } else {
                            strDebitCredit = "C";
                        }

                        strMSGFormattedTransactionAmount = strMSGFormattedTransactionAmount.replace("-", "");


                        //strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd'T'HH:mm:ss", "dd MMM yyyy");

                        builder.append("<tr>\n" +
                                "<td class='statement-header-acc-stmnt-date'>" + Misc.escapeHtmlEntity(strMSGFormattedTransactionDateTime) + "</td>\n" +
                                "<td class='statement-header-acc-stmnt-description'>" + Misc.escapeHtmlEntity(strMSGTransactionDescription) + "</td>\n" +
                                "<td class='statement-header-acc-stmnt-paid-in font-jetbrains-mono" + (strDebitCredit.equalsIgnoreCase("C") ? " statement-paid-in-value" : "") + "'>" +
                                (strDebitCredit.equalsIgnoreCase("C") ? Utils.formatDouble(strMSGFormattedTransactionAmount, "#,##0.00") : "") + "</td>\n" +
                                "<td class='statement-header-acc-stmnt-paid-out font-jetbrains-mono" + (strDebitCredit.equalsIgnoreCase("D") ? " statement-paid-out-value" : "") + "'>" +
                                (strDebitCredit.equalsIgnoreCase("D") ? Utils.formatDouble(strMSGFormattedTransactionAmount, "#,##0.00") : "") + "</td>\n" +
                                "<td class='statement-header-acc-stmnt-balance font-jetbrains-mono font-weight-bold text-align-right'>" + Utils.formatDouble(strMSGRunningBalance, "#,##0.00") + "</td>\n" +
                                "</tr>");

                        if (strDebitCredit.equalsIgnoreCase("C")) {
                            dblTotalPaidIn += Double.parseDouble(strMSGFormattedTransactionAmount);
                        } else {
                            dblTotalPaidOut += Double.parseDouble(strMSGFormattedTransactionAmount);
                        }


                        if (i >= intMaximumTransactionCount) {
                            break;
                        }

                        i++;
                    }

                    theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_IN]", "KES " + Utils.formatDouble(dblTotalPaidIn, "#,##0.00"));
                    theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_OUT]", "KES " + Utils.formatDouble(dblTotalPaidOut, "#,##0.00"));
                    theAccountStatement = theAccountStatement.replace("[DATE_GENERATED]", strFormattedDateTime);
                    theAccountStatement = theAccountStatement.replace("[TRANSACTION_REFERENCE]", strUSSDSessionId);
                    theAccountStatement = theAccountStatement.replace("[THE_ACCOUNT_STATEMENT_DETAILS]", builder.toString());
                    theAccountStatement = AccountStatements.generateAccountStatementPDF(theAccountStatement, strAccountNo);

                    elData.setTextContent(theAccountStatement);


                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Account Statement Generated Successfully");

                }
            }


            channelService.setBeneficiaryReference("");
            channelService.setSourceReference("");
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strUsername);
            channelService.setInitiatorAccount(strUsername);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theMAPPRequest.getTraceID());
            channelService.setInitiatorApplication("MAPP");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNo);
            channelService.setSourceAccount(strAccountNo);
            channelService.setSourceName(strAccountNo);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strUsername);
            channelService.setBeneficiaryAccount(strUsername);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.ACCOUNT_FULL_STATEMENT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Account Full Statement for A/C: " + strAccountNo);
            ChannelService.insertService(channelService);


             /*
             //Response from NAV is:
            <Accounts>
                <Account>
                    <AccNo>5000000127000</AccNo>
                    <AccName>FOSA Savings Accounts 00</AccName>
                </Account>
                <Account>
                    <AccNo>5000000127001</AccNo>
                    <AccName>FOSA Savings Accounts 01</AccName>
                </Account>
            </Accounts>
             */


            // Root element - MSG

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse changePassword(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strNewPassword = configXPath.evaluate("NEW_PASSWORD", ndRequestMSG).trim();

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap mobileBankingDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("mobile_register_details");

            String previousPasswords = mobileBankingDetailsMap.getStringValueOrIfNull("previous_pins", "<PREVIOUS_PINS/>");

            Document docPrevPasswords = XmlUtils.parseXml(previousPasswords);
            NodeList allprevPasswordsList = null;

            try {
                allprevPasswordsList = XmlUtils.getNodesFromXpath(docPrevPasswords, "/PREVIOUS_PINS/PIN");
            } catch (Exception e) {
                e.printStackTrace();
            }

            boolean hasUsedPinBefore = false;

            if (allprevPasswordsList != null) {

                int intMin = 0;

                if (allprevPasswordsList.getLength() > 5) {
                    intMin = 5;
                }

                for (int i = allprevPasswordsList.getLength() - 1; i >= intMin; i--) {
                    Node node = allprevPasswordsList.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }

                    Element element = (Element) node;
                    if (element.getTextContent().equalsIgnoreCase(MobileBankingCryptography.hashPIN(strUsername, strNewPassword))) {
                        hasUsedPinBefore = true;
                        break;
                    }
                }
            }

            if (hasUsedPinBefore) {
                strTitle = "Change Password Failed";
                strResponseText = "Please provide a new password that you have not used before for your mobile banking account.";

                enResponseAction = CON;
                enResponseStatus = ERROR;

            } else {

                TransactionWrapper<FlexicoreHashMap> changePINWrapper = CBSAPI.changeUserPIN(strTransactionID, "MSISDN", strUsername, strPassword, strNewPassword, "APP_ID", strAppID, USSDAPIConstants.MobileChannel.MOBILE_APP);
                FlexicoreHashMap changePINMap = changePINWrapper.getSingleRecord();
                System.out.println("*************************");
                System.out.println(changePINMap);
                System.out.println("*************************");
                if (changePINWrapper.hasErrors()) {
                    USSDAPIConstants.Condition endSession = changePINMap.getValue("end_session");
                    strTitle = changePINMap.getStringValue("display_message");
                    strResponseText = changePINMap.getStringValue("title");

                    if (endSession == USSDAPIConstants.Condition.YES) {
                        enResponseAction = MAPPConstants.ResponseAction.END;
                        enResponseStatus = ERROR;
                    } else {
                        enResponseAction = CON;
                        enResponseStatus = ERROR;
                    }

                } else {
                    strTitle = "Password Changed Successfully";
                    strResponseText = "Your password has been changed successfully. You will be redirected to the login page.";
                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                }
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);


            /*System.out.println("\n\nTHE CHANGE PASSWORD RESPONSE\n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));*/

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".changePassword() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getLoanTypes(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            TransactionWrapper<FlexicoreHashMap> getLoanTypesWrapper = CBSAPI.getLoanTypes(strUsername, "MSISDN", strUsername);
            FlexicoreHashMap getLoanTypesMap = getLoanTypesWrapper.getSingleRecord();


            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loans";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (getLoanTypesWrapper.hasErrors()) {
                USSDAPIConstants.Condition endSession = getLoanTypesMap.getValue("end_session");
                String strResponse = getLoanTypesMap.getStringValue("display_message");

                elData.setTextContent(strResponse);
                enResponseStatus = FAILED;

            } else {

                FlexicoreArrayList mobileEnabledLoansList = getLoanTypesMap.getFlexicoreArrayList("payload");

                if (mobileEnabledLoansList != null && !mobileEnabledLoansList.isEmpty()) {
                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("LOANS");
                    elData.appendChild(elLoans);

                    for (FlexicoreHashMap flexicoreHashMap : mobileEnabledLoansList) {
                        String strLoanTypeName = flexicoreHashMap.getStringValue("loan_type_name");
                        String strLoanTypeID = flexicoreHashMap.getStringValue("loan_type_id");
                        String strLoanTypeLabel = flexicoreHashMap.getStringValue("loan_type_name");
                        String strLoanTypeMin = flexicoreHashMap.getStringValue("loan_type_min_amount");
                        String strLoanTypeMax = flexicoreHashMap.getStringValue("loan_type_max_amount");


                        Element elLoan = doc.createElement("LOAN_TYPE");
                        elLoan.setTextContent(strLoanTypeName);
                        elLoan.setAttribute("ID", strLoanTypeID);
                        elLoan.setAttribute("REQUIRES_GUARANTORS", (strLoanTypeID == "88" || strLoanTypeID == "D88") ? "TRUE" : "FALSE");
                        elLoan.setAttribute("MIN_AMOUNT", strLoanTypeMin);
                        elLoan.setAttribute("MAX_AMOUNT", strLoanTypeMax);

                        elLoans.appendChild(elLoan);
                    }

                    //Adding Loan Purposes
                    Element elementloanPurpose = doc.createElement("LOAN_PURPOSES");
                    elData.appendChild(elementloanPurpose);
                    //Hardcode for now

                    Element elementloanPurpose1 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose1.setTextContent("Agriculture");
                    elementloanPurpose1.setAttribute("CODE", "AGRICULTURE");
                    elementloanPurpose.appendChild(elementloanPurpose1);
                    Element elementloanPurpose2 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose2.setTextContent("Trade");
                    elementloanPurpose2.setAttribute("CODE", "TRADE");
                    elementloanPurpose.appendChild(elementloanPurpose2);
                    Element elementloanPurpose3 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose3.setTextContent("Manufacturing");
                    elementloanPurpose3.setAttribute("CODE", "MANUFACTURING");
                    elementloanPurpose.appendChild(elementloanPurpose3);
                    Element elementloanPurpose4 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose4.setTextContent("Education");
                    elementloanPurpose4.setAttribute("CODE", "EDUCATION");
                    elementloanPurpose.appendChild(elementloanPurpose4);
                    Element elementloanPurpose5 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose5.setTextContent("Health");
                    elementloanPurpose5.setAttribute("CODE", "HEALTH");
                    elementloanPurpose.appendChild(elementloanPurpose5);
                    Element elementloanPurpose6 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose6.setTextContent("Housing");
                    elementloanPurpose6.setAttribute("CODE", "HOUSING");
                    elementloanPurpose.appendChild(elementloanPurpose6);
                    Element elementloanPurpose7 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose7.setTextContent("Finance");
                    elementloanPurpose7.setAttribute("CODE", "FINANCE");
                    elementloanPurpose.appendChild(elementloanPurpose7);
                    Element elementloanPurpose8 = doc.createElement("LOAN_PURPOSE");
                    elementloanPurpose8.setTextContent("Consumption");
                    elementloanPurpose8.setAttribute("CODE", "CONSUMPTION");
                    elementloanPurpose.appendChild(elementloanPurpose8);


                } else {
                    elData.setTextContent("No Loans Found");
                    enResponseStatus = FAILED;
                }
            }

            String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.APPLY_LOAN).getMinimum();
            String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.APPLY_LOAN).getMaximum();

            //create element AMOUNT_LIMITS and append to element DATA
            Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
            Element elMinAmount = doc.createElement("MIN_AMOUNT");
            elMinAmount.setTextContent(String.valueOf(strMin));
            Element elMaxAmount = doc.createElement("MAX_AMOUNT");
            elMaxAmount.setTextContent(String.valueOf(strMax));
            elWithdrawalLimits.appendChild(elMinAmount);
            elWithdrawalLimits.appendChild(elMaxAmount);
            elData.appendChild(elWithdrawalLimits);


            System.out.println("\n\nTHE LOAN TYPE REQUEST:::\n\n");
            System.out.println(XmlUtils.convertNodeToStr(elData));

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    /*public MAPPResponse getMerchants(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

//            TransactionWrapper<FlexicoreHashMap> getLoanTypesWrapper = CBSAPI.getMerchants(strUsername, "MSISDN", strUsername);
//            FlexicoreHashMap getLoanTypesMap = getLoanTypesWrapper.getSingleRecord();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loans";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

//            if (getLoanTypesWrapper.hasErrors()) {
//                USSDAPIConstants.Condition endSession = getLoanTypesMap.getValue("end_session");
//                String strResponse = getLoanTypesMap.getStringValue("display_message");
//
//                elData.setTextContent(strResponse);
//                enResponseStatus = FAILED;
//
//            } else {

//                FlexicoreArrayList mobileEnabledLoansList = getLoanTypesMap.getFlexicoreArrayList("payload");
            FlexicoreArrayList mobileEnabledLoansList = new FlexicoreArrayList();
            FlexicoreHashMap map = new FlexicoreHashMap();
            map.put("merchant_id", 1);
            map.put("merchant_name", "Hot Point");

            mobileEnabledLoansList.add(map);
            map.clear();
            map.put("merchant_id", 2);
            map.put("merchant_name", "Samsung Electronics");
            mobileEnabledLoansList.add(map);

            if (mobileEnabledLoansList != null && !mobileEnabledLoansList.isEmpty()) {
                enDataType = MAPPConstants.ResponsesDataType.LIST;

                Element elLoans = doc.createElement("MERCHANTS");
                elData.appendChild(elLoans);

                for (FlexicoreHashMap flexicoreHashMap : mobileEnabledLoansList) {
                    String strMerchantId = flexicoreHashMap.getStringValue("merchant_id");
                    String strMerchantName = flexicoreHashMap.getStringValue("merchant_name");

                    Element elLoan = doc.createElement("MERCHANT");
                    elLoan.setTextContent(strMerchantName);
                    elLoan.setAttribute("ID", strMerchantId);
                    elLoans.appendChild(elLoan);
                }

            } else {
                elData.setTextContent("No merchants Found");
                enResponseStatus = FAILED;
            }
//            }

            System.out.println("\n\nTHE merchant TYPE REQUEST:::\n\n");
            System.out.println(XmlUtils.convertNodeToStr(elData));

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }*/

    public MAPPResponse getMerchants(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            //Request
            String strUsername = theMAPPRequest.getUsername();

            TransactionWrapper<FlexicoreHashMap> getMerchantsWrapper = CBSAPI.getMerchants(strUsername, "MSISDN", strUsername);
            FlexicoreHashMap getMerchantsMap = getMerchantsWrapper.getSingleRecord();

            System.out.println("getMerchantsMap:"+getMerchantsMap);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Merchants";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (getMerchantsWrapper.hasErrors()) {
                String strResponse = getMerchantsMap.getStringValue("display_message");

                elData.setTextContent(strResponse);
                enResponseStatus = FAILED;

            } else {

                FlexicoreArrayList merchantsList = getMerchantsMap.getFlexicoreArrayList("payload");
                System.out.println("merchantsList:"+merchantsList);

                if (merchantsList != null && !merchantsList.isEmpty()) {
                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("MERCHANTS");
                    elData.appendChild(elLoans);

                    for (FlexicoreHashMap flexicoreHashMap : merchantsList) {
                        String strMerchantId = flexicoreHashMap.getStringValue("merchant_id");
                        String strMerchantName = flexicoreHashMap.getStringValue("merchant");

                        Element elLoan = doc.createElement("MERCHANT");
                        elLoan.setTextContent(strMerchantName);
                        elLoan.setAttribute("ID", strMerchantId);
                        elLoans.appendChild(elLoan);
                    }

                } else {
                    elData.setTextContent("Sorry! There are no sellers found.");
                    enResponseStatus = FAILED;
                }
            }

            System.out.println("\n\nTHE merchant TYPE REQUEST:::\n\n");
            System.out.println(XmlUtils.convertNodeToStr(elData));

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

   /* public MAPPResponse getMerchantProducts(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

//            TransactionWrapper<FlexicoreHashMap> getLoanTypesWrapper = CBSAPI.getMerchants(strUsername, "MSISDN", strUsername);
//            FlexicoreHashMap getLoanTypesMap = getLoanTypesWrapper.getSingleRecord();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loans";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            //TODO:REMOVE
            Node ndRequestMSG = theMAPPRequest.getMSG();
            String strLoanProductID = configXPath.evaluate("LOAN_PRODUCT_ID", ndRequestMSG).trim();
            String strMerchantId = configXPath.evaluate("MERCHANT_ID", ndRequestMSG).trim();
            //TODO:REMOVE

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

//            if (getLoanTypesWrapper.hasErrors()) {
//                USSDAPIConstants.Condition endSession = getLoanTypesMap.getValue("end_session");
//                String strResponse = getLoanTypesMap.getStringValue("display_message");
//
//                elData.setTextContent(strResponse);
//                enResponseStatus = FAILED;
//
//            } else {

//                FlexicoreArrayList mobileEnabledLoansList = getLoanTypesMap.getFlexicoreArrayList("payload");
            FlexicoreArrayList mobileEnabledLoansList = new FlexicoreArrayList();
            FlexicoreArrayList mobileEnabledLoansList2 = new FlexicoreArrayList();
            FlexicoreHashMap map = new FlexicoreHashMap();
            FlexicoreHashMap map2 = new FlexicoreHashMap();
            map.put("product_id", 1);
            map.put("product_name", "Microwave hot point");
            map.put("amount", "10500");

            mobileEnabledLoansList.add(map);
            map.clear();
            map.put("product_id", 2);
            map.put("product_name", "Samsung Fridge Electronics");
            map.put("amount", "50000");
            mobileEnabledLoansList.add(map);

            map2.put("product_id", 3);
            map2.put("product_name", "Mika Washing machine");
            map2.put("amount", "12500");
            mobileEnabledLoansList2.add(map2);

            map2.clear();
            map2.put("product_id", 4);
            map2.put("product_name", "Cooker Aliyons");
            map2.put("amount", "32000");
            mobileEnabledLoansList2.add(map2);

            if (strMerchantId.equals("1")) {
                if (mobileEnabledLoansList != null && !mobileEnabledLoansList.isEmpty()) {
                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("PRODUCTS");
                    elData.appendChild(elLoans);

                    for (FlexicoreHashMap flexicoreHashMap : mobileEnabledLoansList) {
                        String strProductId = flexicoreHashMap.getStringValue("product_id");
                        String strProductName = flexicoreHashMap.getStringValue("product_name");
                        String strAmount = flexicoreHashMap.getStringValue("amount");

                        Element elLoan = doc.createElement("PRODUCT");
                        elLoan.setTextContent(strProductName);
                        elLoan.setAttribute("ID", strProductId);
                        elLoan.setAttribute("AMOUNT", strAmount);
                        elLoans.appendChild(elLoan);
                    }

                } else {
                    elData.setTextContent("No merchants  products Found");
                    enResponseStatus = FAILED;
                }
            } else {
                if (mobileEnabledLoansList2 != null && !mobileEnabledLoansList2.isEmpty()) {
                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("PRODUCTS");
                    elData.appendChild(elLoans);

                    for (FlexicoreHashMap flexicoreHashMap : mobileEnabledLoansList2) {
                        String strProductId = flexicoreHashMap.getStringValue("product_id");
                        String strProductName = flexicoreHashMap.getStringValue("product_name");
                        String strAmount = flexicoreHashMap.getStringValue("amount");

                        Element elLoan = doc.createElement("PRODUCT");
                        elLoan.setTextContent(strProductName);
                        elLoan.setAttribute("ID", strProductId);
                        elLoan.setAttribute("AMOUNT", strAmount);
                        elLoans.appendChild(elLoan);
                    }
                } else {
                    elData.setTextContent("No merchants  products Found");
                    enResponseStatus = FAILED;
                }
            }

//            }

            System.out.println("\n\nTHE merchant PRODUCT TYPE REQUEST:::\n\n");
            System.out.println(XmlUtils.convertNodeToStr(elData));

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }*/

    public MAPPResponse getMerchantProductsV1(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Merchant Products";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            //TODO:REMOVE
            Node ndRequestMSG = theMAPPRequest.getMSG();
            String strLoanProductID = configXPath.evaluate("LOAN_PRODUCT_ID", ndRequestMSG).trim();
            String strMerchantId = configXPath.evaluate("MERCHANT_ID", ndRequestMSG).trim();
            //TODO:REMOVE

            TransactionWrapper<FlexicoreHashMap> getMerchantProductsWrapper = CBSAPI.getMerchantProducts(strUsername, "MSISDN", strUsername,strMerchantId);
            FlexicoreHashMap getMerchantProductsMap = getMerchantProductsWrapper.getSingleRecord();

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (getMerchantProductsWrapper.hasErrors()) {
                USSDAPIConstants.Condition endSession = getMerchantProductsMap.getValue("end_session");
                String strResponse = getMerchantProductsMap.getStringValue("display_message");

                elData.setTextContent(strResponse);
                enResponseStatus = FAILED;

            } else {

                FlexicoreArrayList merchantProductsList = getMerchantProductsMap.getFlexicoreArrayList("payload");

                if (merchantProductsList != null && !merchantProductsList.isEmpty()) {
                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("PRODUCTS");
                    elData.appendChild(elLoans);

                         for (FlexicoreHashMap flexicoreHashMap : merchantProductsList) {
                        String strProductId = flexicoreHashMap.getStringValue("product_id");
                        String strProductName = flexicoreHashMap.getStringValue("product_name");
                        String strProductImage = flexicoreHashMap.getStringValue("image_link");
                        String strProductDescription = flexicoreHashMap.getStringValue("description");
                        String strAmount = flexicoreHashMap.getStringValue("amount");

                        Element elLoan = doc.createElement("PRODUCT");
                        elLoan.setTextContent(strProductName);
                        elLoan.setAttribute("ID", strProductId);
                        elLoan.setAttribute("AMOUNT", strAmount);
                        elLoan.setAttribute("IMAGE_URL", strProductImage);
                        elLoan.setAttribute("DESCRIPTION", strProductDescription);
                        elLoans.appendChild(elLoan);
                    }

                   /* for (FlexicoreHashMap flexicoreHashMap : merchantProductsList) {
                        String strProductId = flexicoreHashMap.getStringValue("product_id");
                        String strProductName = flexicoreHashMap.getStringValue("product_name");
                        String strProductImage = flexicoreHashMap.getStringValue("image_link");
                        String strProductDescription = flexicoreHashMap.getStringValue("description");
                        String strAmount = flexicoreHashMap.getStringValue("amount");

                        Element elLoan = doc.createElement("PRODUCT");
                        elLoan.setTextContent(strProductName);
                        elLoan.setAttribute("ID", strProductId);
                        elLoan.setAttribute("AMOUNT", strAmount);
                        elLoan.setAttribute("IMAGE_URL", strProductImage);
                        elLoan.setAttribute("DESCRIPTION", strProductDescription);
                        elLoans.appendChild(elLoan);
                    }*/

                } else {
                    elData.setTextContent("Sorry! There are no products listed for sale from this seller.");
                    enResponseStatus = FAILED;
                }
            }

            System.out.println("\n\nTHE merchant PRODUCT TYPE REQUEST:::\n\n");
            System.out.println(XmlUtils.convertNodeToStr(elData));

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getMerchantProducts(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Merchant Products";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            //TODO:REMOVE
            Node ndRequestMSG = theMAPPRequest.getMSG();
            String strLoanProductID = configXPath.evaluate("LOAN_PRODUCT_ID", ndRequestMSG).trim();
            String strMerchantId = configXPath.evaluate("MERCHANT_ID", ndRequestMSG).trim();
            //TODO:REMOVE

            TransactionWrapper<FlexicoreHashMap> getMerchantProductsWrapper = CBSAPI.getMerchantProducts(strUsername, "MSISDN", strUsername,strMerchantId);
            FlexicoreHashMap getMerchantProductsMap = getMerchantProductsWrapper.getSingleRecord();

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (getMerchantProductsWrapper.hasErrors()) {
                USSDAPIConstants.Condition endSession = getMerchantProductsMap.getValue("end_session");
                String strResponse = getMerchantProductsMap.getStringValue("display_message");

                elData.setTextContent(strResponse);
                enResponseStatus = FAILED;

            } else {

                FlexicoreArrayList merchantProductsList = getMerchantProductsMap.getFlexicoreArrayList("payload");

                if (merchantProductsList != null && !merchantProductsList.isEmpty()) {
                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elProducts = doc.createElement("PRODUCTS");
                    elData.appendChild(elProducts);

                    for (FlexicoreHashMap flexicoreHashMap : merchantProductsList) {
                        String strProductId = flexicoreHashMap.getStringValue("product_id");
                        String strProductName = flexicoreHashMap.getStringValue("product_name");
                        String strProductImageURL = flexicoreHashMap.getStringValue("image_link");
                        String strProductDescription = flexicoreHashMap.getStringValue("description");
                        String strActualAmount = flexicoreHashMap.getStringValue("actual_amount");
                        String strDiscountedAmount = flexicoreHashMap.getStringValue("discounted_amount");

                        Element elProduct = doc.createElement("PRODUCT");
                        Element elDescription = doc.createElement("DESCRIPTION");
                        Element elImageURL = doc.createElement("IMAGE_URL");
                        Element elAmount = doc.createElement("ACTUAL_AMOUNT");
                        Element elDiscountedAmount = doc.createElement("DISCOUNTED_AMOUNT");
                        Element elProductName = doc.createElement("NAME");

                        /// decode product description
                    byte [] decodedBytesDescription = Base64.getDecoder().decode(strProductDescription);
                    strProductDescription = new String(decodedBytesDescription);

                        elDescription.setTextContent(strProductDescription);
                        elImageURL.setTextContent(strProductImageURL);
                        elAmount.setTextContent(strActualAmount);
                        elDiscountedAmount.setTextContent(strDiscountedAmount);
                        elProductName.setTextContent(strProductName);
                        elProduct.appendChild(elDescription);
                        elProduct.appendChild(elImageURL);
                        elProduct.appendChild(elAmount);
                        elProduct.appendChild(elDiscountedAmount);
                        elProduct.appendChild(elProductName);
                        elProduct.setAttribute("ID", strProductId);
                        elProducts.appendChild(elProduct);
                    }

                /*    for (FlexicoreHashMap flexicoreHashMap : merchantProductsList) {
                        String strProductId = flexicoreHashMap.getStringValue("product_id");
                        String strProductName = flexicoreHashMap.getStringValue("product_name");
                        String strProductImage = flexicoreHashMap.getStringValue("image_link");
                        String strProductDescription = flexicoreHashMap.getStringValue("description");
                        String strAmount = flexicoreHashMap.getStringValue("amount");

                        Element elLoan = doc.createElement("PRODUCT");
                        elLoan.setTextContent(strProductName);
                        elLoan.setAttribute("ID", strProductId);
                        elLoan.setAttribute("AMOUNT", strAmount);
                        elLoan.setAttribute("IMAGE_URL", strProductImage);
                        elLoan.setAttribute("DESCRIPTION", strProductDescription);
                        elLoans.appendChild(elLoan);
                    }*/

                } else {
                    elData.setTextContent("Sorry! There are no products listed for sale from this seller.");
                    enResponseStatus = FAILED;
                }
            }

            System.out.println("\n\nTHE merchant PRODUCT TYPE REQUEST:::\n\n");
            System.out.println(XmlUtils.convertNodeToStr(elData));

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getMemberLoans(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        String strUsername = theMAPPRequest.getUsername();
        String strPassword = theMAPPRequest.getPassword();
        // strPassword = APIUtils.hashPIN(strPassword, strUsername);
        String strAppID = theMAPPRequest.getAppID();
        long lnSessionID = theMAPPRequest.getSessionID();

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> customerLoanAccountsWrapper = CBSAPI.getCustomerLoanAccounts(strUsername, "MSISDN", strUsername);

            FlexicoreHashMap customerLoanAccountsMap = customerLoanAccountsWrapper.getSingleRecord();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loans";

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (customerLoanAccountsWrapper.hasErrors()) {
                USSDAPIConstants.Condition endSession = customerLoanAccountsMap.getValue("end_session");
                String strResponse = customerLoanAccountsMap.getStringValue("display_message");

                elData.setTextContent(strResponse);
                enResponseStatus = FAILED;

            } else {

                FlexicoreArrayList accountsList = customerLoanAccountsMap.getFlexicoreArrayList("payload");

                if (accountsList != null && !accountsList.isEmpty()) {

                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("LOANS");
                    elData.appendChild(elLoans);

                    for (FlexicoreHashMap accountMap : accountsList) {

                        String strAccountName = accountMap.getStringValue("loan_type_name").trim();
                        String strAccountNumber = accountMap.getStringValue("loan_serial_number").trim();
                        String strAccountBalance = accountMap.getStringValue("loan_balance").trim();
                        String strInterestBalance = accountMap.getStringValue("interest_amount").trim();

                        double dblAccountBalance = Double.parseDouble(strAccountBalance);
                        double dblInterestBalance = Double.parseDouble(strInterestBalance);

                        if (dblAccountBalance + dblInterestBalance <= 0.00) {
                            continue;
                        }

                        //String strAccountLabel = accountMap.getStringValue("account_name").trim();

                        //strAccountBalance = strAccountBalance.replaceFirst("-", "");

                      /*  String strLoanNo = loansInService.get(loanTypeCode).get("id");
                        String strLoanName = loansInService.get(loanTypeCode).get("type");
                        String strLoanBalance = loansInService.get(loanTypeCode).get("balance");
*/
                        Element elLoan = doc.createElement("LOAN");
                        elLoan.setTextContent(strAccountName);
                        elLoan.setAttribute("SERIAL_NO", strAccountNumber);
                        elLoan.setAttribute("NAME", strAccountName);
                        elLoan.setAttribute("AMOUNT", strAccountBalance);
                        elLoan.setAttribute("CHANGE_AMOUNT", "YES");
                        elLoan.setAttribute("BALANCE", strAccountBalance);
                        elLoan.setAttribute("INTEREST", strInterestBalance);
                        //elLoan.setAttribute("ACCOUNT_CD", strAccountCd);

                        elLoans.appendChild(elLoan);

                    }
                } else {
                    elData.setTextContent("No Loans Found");
                    enResponseStatus = FAILED;
                }
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            System.out.println("\n\nTHE LOAN ACCOUNTS RESPONSE\n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getMemberLoansWithPaymentDetails(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            //todo: Add sample HashMap as documentation
            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> customerLoanAccountsWrapper = CBSAPI.getCustomerLoanAccounts(strUsername, "MSISDN", strUsername);


            FlexicoreHashMap customerLoanAccountsMap = customerLoanAccountsWrapper.getSingleRecord();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loans";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");


            if (customerLoanAccountsWrapper.hasErrors()) {
                USSDAPIConstants.Condition endSession = customerLoanAccountsMap.getValue("end_session");
                String strResponse = customerLoanAccountsMap.getStringValue("display_message");

                elData.setTextContent("Sorry, an error occurred while processing your request");
                enResponseStatus = FAILED;

            } else {

                FlexicoreArrayList accountsList = customerLoanAccountsMap.getFlexicoreArrayList("payload");

                if (accountsList != null && !accountsList.isEmpty()) {

                    enDataType = MAPPConstants.ResponsesDataType.LIST;

                    Element elLoans = doc.createElement("LOANS");
                    elData.appendChild(elLoans);

                    for (FlexicoreHashMap accountMap : accountsList) {

                        String strAccountName = accountMap.getStringValue("loan_type_name").trim();
                        String strAccountNumber = accountMap.getStringValue("loan_serial_number").trim();
                        String strAccountBalance = accountMap.getStringValue("loan_balance").trim();

                        String strInterestBalance = accountMap.getStringValue("interest_amount").trim();

                        double dblAccountBalance = Double.parseDouble(strAccountBalance);
                        double dblInterestBalance = Double.parseDouble(strInterestBalance);

                        if (dblAccountBalance + dblInterestBalance <= 0.00) {
                            continue;
                        }

                      /*  String strLoanNo = loansInService.get(loanTypeCode).get("id");
                        String strLoanName = loansInService.get(loanTypeCode).get("type");
                        String strLoanBalance = loansInService.get(loanTypeCode).get("balance");
*/
                        Element elLoan = doc.createElement("LOAN");
                        elLoan.setTextContent(strAccountName);
                        elLoan.setAttribute("SERIAL_NO", strAccountNumber);
                        elLoan.setAttribute("NAME", strAccountName);
                        elLoan.setAttribute("AMOUNT", strAccountBalance);
                        elLoan.setAttribute("CHANGE_AMOUNT", "YES");
                        elLoan.setAttribute("BALANCE", strAccountBalance);
                        elLoan.setAttribute("INTEREST", strInterestBalance);

                        elLoans.appendChild(elLoan);
                    }

                    Element elRepaymentOptions = doc.createElement("REPAYMENT_OPTIONS");
                    //if it is not enabled then the default repayment option is savings account
                    elRepaymentOptions.setAttribute("ENABLED", "TRUE");


                    Element elRepaymentOption2 = doc.createElement("OPTION");
                    elRepaymentOption2.setAttribute("VALUE", "MPESA");
                    elRepaymentOption2.setAttribute("TYPE", "MPESA");
                    elRepaymentOption2.setTextContent("Safaricom M-Pesa");
                    elRepaymentOptions.appendChild(elRepaymentOption2);

                    LinkedHashMap<String, String> FOSAAccounts = getMemberAccountsList(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE);

                    for (String account : FOSAAccounts.keySet()) {
                        Element elRepaymentOption1 = doc.createElement("OPTION");
                        elRepaymentOption1.setAttribute("VALUE", account);
                        elRepaymentOption1.setAttribute("TYPE", "ACCOUNT");
                        elRepaymentOption1.setTextContent("Savings Account");
                        elRepaymentOptions.appendChild(elRepaymentOption1);
                    }

                /*Element elRepaymentOption1 = doc.createElement("OPTION");
                elRepaymentOption1.setAttribute("VALUE", "SAVINGS_ACCOUNT");
                elRepaymentOption1.setAttribute("TYPE", "ACCOUNT");
                elRepaymentOption1.setTextContent("Savings Account");
                elRepaymentOptions.appendChild(elRepaymentOption1);*/

                    elData.appendChild(elRepaymentOptions);

                    String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.PAY_LOAN).getMinimum();
                    String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.PAY_LOAN).getMaximum();

                    //create element AMOUNT_LIMITS and append to element DATA
                    Element elWithdrawalLimits = doc.createElement("AMOUNT_LIMITS");
                    Element elMinAmount = doc.createElement("MIN_AMOUNT");
                    elMinAmount.setTextContent(String.valueOf(strMin));
                    Element elMaxAmount = doc.createElement("MAX_AMOUNT");
                    elMaxAmount.setTextContent(String.valueOf(strMax));
                    elWithdrawalLimits.appendChild(elMinAmount);
                    elWithdrawalLimits.appendChild(elMaxAmount);
                    elData.appendChild(elWithdrawalLimits);

                } else {
                    elData.setTextContent("No Loans Found");
                    enResponseStatus = FAILED;
                }
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            System.out.println("\n\nTHE LOAN ACCOUNTS RESPONSE\n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse loanBalanceEnquiry(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='LOAN_BALANCE' VERSION='1.01'>
                      <LOAN_NO>123456</LOAN_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();

            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            System.out.println("\n\n" + XmlUtils.convertNodeToStr(ndRequestMSG) + "\n\n");

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", ndRequestMSG).trim();

            //String strLoanNo = configXPath.evaluate("LOAN/@SERIAL_NO", ndRequestMSG).trim();
            //String strLoanName = configXPath.evaluate("LOAN/@NAME", ndRequestMSG).trim();
            //String strLoanCd = configXPath.evaluate("LOAN/OTHER_DETAILS/ACCOUNT_CD", ndRequestMSG).trim();
            String strCharge = "NO";
            String strLoanBalance = "";

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.loanBalanceEnquiry(strUsername,
                    "MSISDN", strUsername, "APP_ID", strAppID, strLoanNo);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();

            String strTitle = "";
            String strResponseText = "";

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                strTitle = "ERROR: Loan Balance";
                strResponseText = "An error occurred. Please try again after a few minutes.";
                enResponseStatus = ERROR;
                enResponseAction = CON;
            } else {

                String strAccountName = accountBalanceMap.getStringValueOrIfNull("loan_name", "").trim();
                String strAccountSerialNumber = accountBalanceMap.getStringValueOrIfNull("loan_serial_number", "").trim();

                String strAccountBalance = accountBalanceMap.getStringValueOrIfNull("loan_balance", "0").trim();
                String strAccountInterestAmount = accountBalanceMap.getStringValueOrIfNull("interest_amount", "0").trim();

                double dblLoanBalance = Double.parseDouble(strAccountBalance);

                double dblLoanInterestBalance = Double.parseDouble(strAccountInterestAmount);

                double dblTotalLoanBalance = dblLoanBalance;
                if (strAccountInterestAmount.contains("-")) {
                    dblLoanInterestBalance = 0.00;
                } else {
                    dblTotalLoanBalance = dblLoanBalance + dblLoanInterestBalance;
                }

                strAccountBalance = Utils.formatDouble(strAccountBalance, "#,##0.00");
                strAccountInterestAmount = Utils.formatDouble(strAccountInterestAmount, "#,##0.00");

                strTitle = "Loan Balance";
                strResponseText = "Loan: <b>" + strAccountName + "-" + strAccountSerialNumber + "</b><br/> " +
                        "Balance: <b>KES " + strAccountBalance + "</b> <br/>" +
                        "Interest Balance: <b>KES " + strAccountInterestAmount + "</b> <br/>" +
                        "Total: <b>KES " + Utils.formatDouble(dblTotalLoanBalance, "#,##0.00") + "</b>"
                ;
                strCharge = "YES";

                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_BALANCE_ENQUIRY", theMAPPRequest);

                String strOriginatorId = UUID.randomUUID().toString();

                ChannelService channelService = new ChannelService();
                channelService.setOriginatorId(strOriginatorId);
                channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_BALANCE_ENQUIRY.getValue());

                if (accountBalanceEnquiryWrapper.hasErrors()) {
                    channelService.setTransactionStatusCode(104);
                    channelService.setTransactionStatusName("FAILED");
                    channelService.setTransactionStatusDescription(accountBalanceMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
                } else {
                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Loan Balance Enquiry Completed Successfully");
                    channelService.setBeneficiaryReference("");
                    channelService.setSourceReference("");
                }
                channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

                channelService.setInitiatorType("MSISDN");
                channelService.setInitiatorIdentifier(strUsername);
                channelService.setInitiatorAccount(strUsername);
                channelService.setInitiatorName(strMemberName);
                channelService.setInitiatorReference(theMAPPRequest.getTraceID());
                channelService.setInitiatorApplication("USSD");
                channelService.setInitiatorOtherDetails("<DATA/>");

                channelService.setSourceType("ACCOUNT_NO");
                channelService.setSourceIdentifier(strLoanNo);
                channelService.setSourceAccount(strLoanNo);
                channelService.setSourceName(strLoanNo);
                channelService.setSourceApplication("CBS");
                channelService.setSourceOtherDetails("<DATA/>");

                channelService.setBeneficiaryType("MSISDN");
                channelService.setBeneficiaryIdentifier(strUsername);
                channelService.setBeneficiaryAccount(strUsername);
                channelService.setBeneficiaryName(strMemberName);
                channelService.setBeneficiaryApplication("CBS");
                channelService.setBeneficiaryOtherDetails("<DATA/>");

                channelService.setTransactionCurrency("KES");
                channelService.setTransactionAmount(0.00);

                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.LOAN_BALANCE_ENQUIRY.getValue(),
                        0.00);

                if (chargesWrapper.hasErrors()) {
                    channelService.setTransactionCharge(0.00);
                    channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

                } else {
                    channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                    channelService.setTransactionOtherDetails("<DATA/>");
                }

                channelService.setTransactionRemark("Loan Balance Enquiry for A/C: " + strLoanNo);
                ChannelService.insertService(channelService);
            }


             /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <MSG SESSION_ID='123121' TYPE='MOBILE_BANKING' ACTION='CON' STATUS='SUCCESS' CHARGE='YES'>
                    <TITLE>Loan Balance</TITLE>
                    <DATA TYPE='TEXT'>Your loan balance is KES 5,100.00</DATA>
                </MSG>
            </MESSAGES>
             */

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse loanBalanceEnquiryALL(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='LOAN_BALANCE' VERSION='1.01'>
                      <LOAN_NO>123456</LOAN_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();

            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            System.out.println("\n\n" + XmlUtils.convertNodeToStr(ndRequestMSG) + "\n\n");

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", ndRequestMSG).trim();

            //String strLoanNo = configXPath.evaluate("LOAN/@SERIAL_NO", ndRequestMSG).trim();
            //String strLoanName = configXPath.evaluate("LOAN/@NAME", ndRequestMSG).trim();
            //String strLoanCd = configXPath.evaluate("LOAN/OTHER_DETAILS/ACCOUNT_CD", ndRequestMSG).trim();
            String strCharge = "NO";
            String strLoanBalance = "";

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.loanBalanceEnquiry(strUsername,
                    "MSISDN", strUsername, "APP_ID", strAppID, strLoanNo);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();

            String strTitle = "";
            String strResponseText = "";

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                strTitle = "ERROR: Loan Balance";
                strResponseText = "An error occurred. Please try again after a few minutes.";
                enResponseStatus = ERROR;
                enResponseAction = CON;
            } else {

                CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

                FlexicoreArrayList loansList = accountBalanceMap.getFlexicoreArrayList("payload");

                StringBuilder accountsMSGBuilder = new StringBuilder();

                // Element elLoans = doc.createElement("LOANS");

                for (FlexicoreHashMap loanAccountMap : loansList) {
                    //String strAccountName = loanAccountMap.getStringValueOrIfNull("loan_type_name", "").trim();
                    String strAccountName = loanAccountMap.getStringValueOrIfNull("loan_type_name", "").trim();
                    String strAccountSerialNumber = loanAccountMap.getStringValueOrIfNull("loan_serial_number", "").trim();
                    String strAccountBalance = loanAccountMap.getStringValueOrIfNull("loan_balance", "0").trim();
                    String strInterestBalance = loanAccountMap.getStringValueOrIfNull("interest_amount", "0").trim();
                    strAccountBalance = Utils.formatDouble(strAccountBalance, "#,##0.00");
                    strInterestBalance = Utils.formatDouble(strInterestBalance, "#,##0.00");

                    accountsMSGBuilder.append("<div style='text-align: left;'>Name: <b>" + strAccountName + "-" + strAccountSerialNumber + "</b></div>");
                    accountsMSGBuilder.append("<div style='text-align: left;'>Balance: <b>KES " + strAccountBalance + "</b><div>");
                    accountsMSGBuilder.append("<div style='text-align: left;'>Interest: <b style='color: #3C795B;'>KES " + strInterestBalance + "</b><div><br/>");
                }

                strTitle = "Loan Balances";
                //strResponseText = "Your loan balance is: <b>KES " + accountBalanceMap.getStringValue("loan_balance") + "</b>";
                strResponseText = accountsMSGBuilder.toString();
                strCharge = "YES";

                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_BALANCE_ENQUIRY", theMAPPRequest);

                String strOriginatorId = UUID.randomUUID().toString();

                ChannelService channelService = new ChannelService();
                channelService.setOriginatorId(strOriginatorId);
                channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_BALANCE_ENQUIRY.getValue());

                if (accountBalanceEnquiryWrapper.hasErrors()) {
                    channelService.setTransactionStatusCode(104);
                    channelService.setTransactionStatusName("FAILED");
                    channelService.setTransactionStatusDescription(accountBalanceMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
                } else {
                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Loan Balance Enquiry Completed Successfully");
                    channelService.setBeneficiaryReference("");
                    channelService.setSourceReference("");
                }
                channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

                channelService.setInitiatorType("MSISDN");
                channelService.setInitiatorIdentifier(strUsername);
                channelService.setInitiatorAccount(strUsername);
                channelService.setInitiatorName(strMemberName);
                channelService.setInitiatorReference(theMAPPRequest.getTraceID());
                channelService.setInitiatorApplication("USSD");
                channelService.setInitiatorOtherDetails("<DATA/>");

                channelService.setSourceType("ACCOUNT_NO");
                channelService.setSourceIdentifier("ALL_LOANS");
                channelService.setSourceAccount("ALL_LOANS");
                channelService.setSourceName("ALL_LOANS");
                channelService.setSourceApplication("CBS");
                channelService.setSourceOtherDetails("<DATA/>");

                channelService.setBeneficiaryType("MSISDN");
                channelService.setBeneficiaryIdentifier(strUsername);
                channelService.setBeneficiaryAccount(strUsername);
                channelService.setBeneficiaryName(strMemberName);
                channelService.setBeneficiaryApplication("CBS");
                channelService.setBeneficiaryOtherDetails("<DATA/>");

                channelService.setTransactionCurrency("KES");
                channelService.setTransactionAmount(0.00);

                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.LOAN_BALANCE_ENQUIRY.getValue(),
                        0.00);

                if (chargesWrapper.hasErrors()) {
                    channelService.setTransactionCharge(0.00);
                    channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

                } else {
                    channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                    channelService.setTransactionOtherDetails("<DATA/>");
                }

                channelService.setTransactionRemark("Loan Balance Enquiry");
                ChannelService.insertService(channelService);
            }


             /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <MSG SESSION_ID='123121' TYPE='MOBILE_BANKING' ACTION='CON' STATUS='SUCCESS' CHARGE='YES'>
                    <TITLE>Loan Balance</TITLE>
                    <DATA TYPE='TEXT'>Your loan balance is KES 5,100.00</DATA>
                </MSG>
            </MESSAGES>
             */

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse applyLoan(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("applyLoan");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                //Request
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = theMAPPRequest.getAppID();
                long lnSessionID = theMAPPRequest.getSessionID();
                String strGUID = UUID.randomUUID().toString();

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                // Root element - MSG
                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;
                MAPPConstants.ResponseAction enResponseAction = CON;
                MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                System.out.println("\n\n");
                System.out.println(XmlUtils.convertNodeToStr(ndRequestMSG));

                /*String strLoanID = configXPath.evaluate("LOAN_TYPE/@PRODUCT_ID", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();*/

                String strLoanID = configXPath.evaluate("LOAN_TYPE_ID", ndRequestMSG).trim();
                String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
                String strMerchantId = configXPath.evaluate("MERCHANT_ID", ndRequestMSG).trim();
                String strProductId = configXPath.evaluate("PRODUCT_ID", ndRequestMSG).trim();

                //todo: remove
                if(strAmount.isEmpty()){
                    strAmount = "16500";
                }

                if(strProductId.isEmpty()){
                    strProductId = "PRDWD34569";
                }

                //String strProductID = configXPath.evaluate("PRODUCT_ID", ndRequestMSG).trim();
                //String strProductName = configXPath.evaluate("LOAN_TYPE/OTHER_DETAILS/PRODUCT_NAME", ndRequestMSG).trim();

                /*String strTheLoanType = configXPath.evaluate("APPLICATION_PARAMS/@TYPE", ndRequestMSG).trim();
                String strTheLoanToCloseSno = configXPath.evaluate("APPLICATION_PARAMS/LOAN_TO_CLOSE_SNO", ndRequestMSG).trim();
                String strTheLoanToCloseAccountCd = configXPath.evaluate("APPLICATION_PARAMS/LOAN_TO_CLOSE_SNO/@ACCOUNT_CD", ndRequestMSG).trim();*/

                String strLoanApplicationMaximum = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.APPLY_LOAN).getMaximum();

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());

                String strEntryNo = UUID.randomUUID().toString().toUpperCase();
                BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                String strTitle = "";
                String strResponseText = "";

                String strCharge = "NO";

                String strLoanApplicationStatus = "ERROR";
                String strLoanApplicationStatusDescription = "ERROR";

                String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
                String strRequestApplication = "MBANKING_SERVER";
                String strSourceApplication = "MAPP";
                String strTransactionDateTime = APIUtils.getCurrentDateTime();

                String strOriginatorId = UUID.randomUUID().toString();


                String strMemberName = getUserFullName(theMAPPRequest, strUsername);

                    /*TransactionWrapper<FlexicoreHashMap> checkLoanLimitWrapper = CBSAPI.checkLoanLimit(strUsername,
                            "MSISDN", strUsername, "APP_ID", strAppID, strLoanNo);*/


                TransactionWrapper<FlexicoreHashMap> loanApplicationWrapper = CBSAPI.loanApplication(strUsername,
                        "MSISDN", strUsername, "APP_ID", strAppID, strLoanID, Double.parseDouble(strAmount),"1",strMerchantId,strProductId,strOriginatorId,
                        "MAPP", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));


                FlexicoreHashMap loanApplicationMap = loanApplicationWrapper.getSingleRecord();
                CBSAPI.SMSMSG cbsMSG = loanApplicationMap.getValue("msg_object");

                sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_APPLICATION", theMAPPRequest);

                ChannelService channelService = new ChannelService();
                channelService.setOriginatorId(strOriginatorId);
                channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_APPLICATION.getValue());

                if (loanApplicationWrapper.hasErrors()) {
                    channelService.setTransactionStatusCode(104);
                    channelService.setTransactionStatusName("FAILED");
                    channelService.setTransactionStatusDescription(loanApplicationMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

                    System.err.println("MAPPAPI.applyLoan() - Response " + loanApplicationMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

                    strTitle = loanApplicationMap.getStringValueOrIfNull("title", "Unknown error occurred");
                    strResponseText = loanApplicationMap.getStringValueOrIfNull("display_message", "Unknown error occurred");
                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = FAILED;


                } else {
                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Loan Application Completed Successfully");
                    channelService.setBeneficiaryReference("");
                    channelService.setSourceReference("");

                    strTitle = "Loan Application Successful";
                    strResponseText = "Your loan application was completed successfully.";
                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                }
                channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

                channelService.setInitiatorType("MSISDN");
                channelService.setInitiatorIdentifier(strUsername);
                channelService.setInitiatorAccount(strUsername);
                channelService.setInitiatorName(strMemberName);
                channelService.setInitiatorReference(theMAPPRequest.getTraceID());
                channelService.setInitiatorApplication("USSD");
                channelService.setInitiatorOtherDetails("<DATA/>");

                channelService.setSourceType("ACCOUNT_NO");
                channelService.setSourceIdentifier(strLoanID);
                channelService.setSourceAccount(strLoanID);
                channelService.setSourceName(strLoanID);
                channelService.setSourceApplication("CBS");
                channelService.setSourceOtherDetails("<DATA/>");

                channelService.setBeneficiaryType("MSISDN");
                channelService.setBeneficiaryIdentifier(strUsername);
                channelService.setBeneficiaryAccount(strUsername);
                channelService.setBeneficiaryName(strMemberName);
                channelService.setBeneficiaryApplication("CBS");
                channelService.setBeneficiaryOtherDetails("<DATA/>");

                channelService.setTransactionCurrency("KES");
                channelService.setTransactionAmount(Double.parseDouble(strAmount));

                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.LOAN_APPLICATION.getValue(),
                        Double.parseDouble(strAmount));

                if (chargesWrapper.hasErrors()) {
                    channelService.setTransactionCharge(0.00);
                    channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

                } else {
                    channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                    channelService.setTransactionOtherDetails("<DATA/>");
                }

                channelService.setTransactionRemark("Loan Application");
                ChannelService.insertService(channelService);


                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }


    public MAPPResponse applyLoanOLD(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("applyLoan");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;
            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strLoanType = configXPath.evaluate("LOAN/TYPE", ndRequestMSG).trim();
            String strLoanDuration = configXPath.evaluate("LOAN/DURATION", ndRequestMSG).trim();
            String strLoanPurpose = configXPath.evaluate("LOAN/PURPOSE", ndRequestMSG).trim();
            String strLoanBranch = configXPath.evaluate("LOAN/BRANCH", ndRequestMSG).trim();
            String strAmount = configXPath.evaluate("LOAN/AMOUNT", ndRequestMSG).trim();
            String strPayslipPIN = configXPath.evaluate("LOAN/PAYSLIP_PIN", ndRequestMSG).trim();

            NodeList nlGuarantors = ((NodeList) configXPath.evaluate("GUARANTORS/GUARANTOR", ndRequestMSG, XPathConstants.NODESET));

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            int intLoanDuration = 0;

            String strMAPPSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
            String strDatetime = MBankingDB.getDBDateTime().trim();
            String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");

            String strLoanApplicationResponse = "SUCCESS";

            switch (strLoanApplicationResponse) {
                case "SUCCESS": {
                    strTitle = "Request Received Successfully";
                    strResponseText = "Your loan application request was received successfully. You will receive an SMS once the loan has been approved.";
                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                    if (nlGuarantors != null) {
                        for (int i = 0; i < nlGuarantors.getLength(); i++) {
                            String strName = configXPath.evaluate("@NAME", nlGuarantors.item(i)).trim();
                            String strMobileNumber = configXPath.evaluate("@MOBILE_NUMBER", nlGuarantors.item(i)).trim();

                            new Thread(() -> {
                                String strMSG = "Dear " + strName.split(" ")[0] + ", ISAAC is requesting your guarantorship for his Development Loan of KES " + strFormattedAmount + " please log in to the mobile application or dial *882*1# to action this request.\n\n" +
                                        "Date: " + strFormattedDateTime + "\n" +
                                        "Ref: " + strMAPPSessionId + "\n";

                                fnSendSMS(strMobileNumber, strMSG, "YES", MSGConstants.MSGMode.EXPRESS, 200, "ONE_TIME_PASSWORD", "MAPP", "MBANKING_SERVER", UUID.randomUUID().toString(), UUID.randomUUID().toString());
                            }).start();
                        }
                    }

                    String strMSG = "Dear member, your application for Development Loan of KES " + strFormattedAmount + " has been received successfully, kindly wait as it is being processed.\n\n" +
                            "Date: " + strFormattedDateTime + "\n" +
                            "Ref: " + strMAPPSessionId + "\n";

                    fnSendSMS(strUsername, strMSG, "YES", MSGConstants.MSGMode.EXPRESS, 200, "ONE_TIME_PASSWORD", "MAPP", "MBANKING_SERVER", UUID.randomUUID().toString(), UUID.randomUUID().toString());

                    break;
                }
                case "INCORRECT_PIN": {
                    strTitle = "Incorrect PIN";
                    strResponseText = "Error, the PIN you have entered as current PIN is incorrect, please try again";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                    break;
                }
                case "LOAN_APPLICATION_EXISTS": {
                    strTitle = "Loan Already Exists";
                    strResponseText = "The loan you applied for already exists, please repay the current loan to apply for another one.";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                    break;
                }
                default: {
                    enResponseAction = MAPPConstants.ResponseAction.END;
                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                    strTitle = "ERROR: Apply Loan";
                    strResponseText = "An error occurred. Please try again after a few minutes.";
                }
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse checkLoanLimit(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("checkLoanLimit");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.VERIFICATION);
            if (otp.isEnabled()) {
                mrOTPVerificationMappResponse = validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);

                String strAction = configXPath.evaluate("@ACTION", mrOTPVerificationMappResponse.getMSG()).trim();
                String strStatus = configXPath.evaluate("@STATUS", mrOTPVerificationMappResponse.getMSG()).trim();

                if (!strAction.equals("CON") || !strStatus.equals("SUCCESS")) {
                    otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.ERROR;
                }
            }

            if (otpVerificationStatus == MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS) {
                //Request
                String strUsername = theMAPPRequest.getUsername();
                String strPassword = theMAPPRequest.getPassword();
                String strAppID = theMAPPRequest.getAppID();
                long lnSessionID = theMAPPRequest.getSessionID();
                String strGUID = UUID.randomUUID().toString();

                Node ndRequestMSG = theMAPPRequest.getMSG();

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                // Root element - MSG
                Document doc = docBuilder.newDocument();

                MAPPConstants.ResponsesDataType enDataType = TEXT;
                MAPPConstants.ResponseAction enResponseAction = CON;
                MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                System.out.println("\n\n");
                System.out.println(XmlUtils.convertNodeToStr(ndRequestMSG));

                String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", ndRequestMSG).trim();
                String strLoanName = configXPath.evaluate("LOAN_SERIAL_NO", ndRequestMSG).trim();

                String strLoanApplicationMaximum = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.APPLY_LOAN).getMaximum();

                String strSessionID = String.valueOf(theMAPPRequest.getSessionID());

                String strEntryNo = UUID.randomUUID().toString().toUpperCase();
                // BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

                String strTitle = "";
                String strResponseText = "";

                String strCharge = "NO";

                String strLoanApplicationStatus = "ERROR";
                String strLoanApplicationStatusDescription = "ERROR";

                String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
                String strRequestApplication = "MBANKING_SERVER";
                String strSourceApplication = "MAPP";
                String strTransactionDateTime = APIUtils.getCurrentDateTime();

                // Thread worker = new Thread(() -> {

                String strMemberName = getUserFullName(theMAPPRequest, strUsername);

                TransactionWrapper<FlexicoreHashMap> checkLoanLimitWrapper = CBSAPI.checkLoanLimit(strUsername,
                        "MSISDN", strUsername, "APP_ID", strAppID, strLoanNo,"0");

                FlexicoreHashMap checkLoanLimitMap = checkLoanLimitWrapper.getSingleRecord();
                CBSAPI.SMSMSG cbsMSG = checkLoanLimitMap.getValue("msg_object");

                //sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "CHECK_LOAN_LIMIT", theMAPPRequest);

                String strOriginatorId = UUID.randomUUID().toString();

                ChannelService channelService = new ChannelService();
                channelService.setOriginatorId(strOriginatorId);
                channelService.setTransactionCategory(AppConstants.ChargeServices.CHECK_LOAN_LIMIT.getValue());

                if (checkLoanLimitWrapper.hasErrors()) {
                    channelService.setTransactionStatusCode(104);
                    channelService.setTransactionStatusName("FAILED");
                    channelService.setTransactionStatusDescription(checkLoanLimitMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

                    System.err.println("MAPPAPI.checkLoanLimit() - Response " + checkLoanLimitMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

                } else {
                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Loan Qualification Check Completed Successfully");
                    channelService.setBeneficiaryReference("");
                    channelService.setSourceReference("");
                }
                channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

                channelService.setInitiatorType("MSISDN");
                channelService.setInitiatorIdentifier(strUsername);
                channelService.setInitiatorAccount(strUsername);
                channelService.setInitiatorName(strMemberName);
                channelService.setInitiatorReference(theMAPPRequest.getTraceID());
                channelService.setInitiatorApplication("USSD");
                channelService.setInitiatorOtherDetails("<DATA/>");

                channelService.setSourceType("ACCOUNT_NO");
                channelService.setSourceIdentifier(strLoanNo);
                channelService.setSourceAccount(strLoanNo);
                channelService.setSourceName(strLoanName);
                channelService.setSourceApplication("CBS");
                channelService.setSourceOtherDetails("<DATA/>");

                channelService.setBeneficiaryType("MSISDN");
                channelService.setBeneficiaryIdentifier(strUsername);
                channelService.setBeneficiaryAccount(strUsername);
                channelService.setBeneficiaryName(strMemberName);
                channelService.setBeneficiaryApplication("CBS");
                channelService.setBeneficiaryOtherDetails("<DATA/>");

                channelService.setTransactionCurrency("KES");
                channelService.setTransactionAmount(0.00);

                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.CHECK_LOAN_LIMIT.getValue(),
                        0.00);

                if (chargesWrapper.hasErrors()) {
                    channelService.setTransactionCharge(0.00);
                    channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

                } else {
                    channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                    channelService.setTransactionOtherDetails("<DATA/>");
                }

                channelService.setTransactionRemark("Loan Qualification Check for Loan: " + strLoanName);
                ChannelService.insertService(channelService);

                //});
                //.start();

                FlexicoreHashMap loanLimitMap = checkLoanLimitMap.getFlexicoreHashMap("payload");

                String eligibleAmount = loanLimitMap.getStringValue("eligible_amount");
                String reason = loanLimitMap.getStringValueOrIfNull("reason", "");

                String strFormattedAmount = Utils.formatDouble(eligibleAmount, "#,##0.00");

                String strResponse = "Eligible Amount: KES " + strFormattedAmount + "<br/>";

                if (!reason.isBlank()) {
                    strResponse = strResponse + "Reason:<br/>" + reason;
                }

                strTitle = "Loan Qualification - " + strLoanName;
                strResponseText = strResponse;
                strCharge = "YES";
                enResponseAction = CON;
                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                Element elData = doc.createElement("DATA");
                elData.setTextContent(strResponseText);

                generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                //Response
                Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            } else {
                theMAPPResponse = mrOTPVerificationMappResponse;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse loanQualificationAmount(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("checkLoanLimit");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();
            String strGUID = UUID.randomUUID().toString();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.OBJECT;
            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            System.out.println("\n\nTHE LOAN LIMIT REQUEST\n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndRequestMSG));

            String strLoanID = configXPath.evaluate("LOAN_TYPE/@ID", ndRequestMSG).trim();

            String strLoanAmount = configXPath.evaluate("LOAN_TYPE/@AMOUNT", ndRequestMSG).trim();
            String strLoanDuration = configXPath.evaluate("LOAN_TYPE/@DURATION", ndRequestMSG).trim();

            if(strLoanAmount.isEmpty()) {
                strLoanAmount = "0";
            }

            //String strLoanID = configXPath.evaluate("LOAN_TYPE/@PRODUCT_ID", ndRequestMSG).trim();
            //String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
            //String strProductID = configXPath.evaluate("PRODUCT_ID", ndRequestMSG).trim();

            //String strProductName = configXPath.evaluate("LOAN_TYPE/OTHER_DETAILS/PRODUCT_NAME", ndRequestMSG).trim();

            //String strLoanApplicationMaximum = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.APPLY_LOAN).getMaximum();
            //String strLoanApplicationMinimum = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.APPLY_LOAN).getMinimum();

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());

            String strEntryNo = UUID.randomUUID().toString().toUpperCase();
            // BigDecimal bdAmount = BigDecimal.valueOf(Double.parseDouble(strAmount));

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            String strLoanApplicationStatus = "ERROR";
            String strLoanApplicationStatusDescription = "ERROR";

            Element elData = doc.createElement("DATA");

            if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_APPLICATION)) {

                Element elQualification = doc.createElement("QUALIFICATION");
                elData.appendChild(elQualification);

                Element elAmount = doc.createElement("AMOUNT");
                elQualification.appendChild(elAmount);

                Element elMinimum = doc.createElement("MINIMUM");
                elAmount.appendChild(elMinimum);

                Element elMaximum = doc.createElement("MAXIMUM");
                elAmount.appendChild(elMaximum);

                Element elNarration = doc.createElement("NARRATION");
                elQualification.appendChild(elNarration);

                Element elQualificationParams = doc.createElement("APPLICATION_PARAMS");
                elData.appendChild(elQualificationParams);

                strTitle = "Loan Application";
                strCharge = "NO";
                enResponseAction = CON;
                enResponseStatus = SUCCESS;

                String strEligibleAmount = "0.00";

                elMinimum.setTextContent("0.00");

                elMaximum.setTextContent(strEligibleAmount);

//                elQualificationParams.setAttribute("TYPE", "NEW_LOAN");

                String strNarration = AppConstants.strServiceUnavailable;
                elNarration.setTextContent(strNarration);

            } else {

                String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
                String strRequestApplication = "MBANKING_SERVER";
                String strSourceApplication = "MAPP";
                String strTransactionDateTime = APIUtils.getCurrentDateTime();

                TransactionWrapper<FlexicoreHashMap> loanQualificationWrapper = CBSAPI.checkLoanLimit(strUsername,
                        "MSISDN", strUsername, "APP_ID", strAppID, strLoanID,strLoanAmount);

                /*TransactionWrapper<FlexicoreHashMap> loanQualificationWrapper = CBSAPI.loanQualificationCheck2(strTransactionID, "MSISDN", strUsername,
                        "APP_ID", strAppID, getDefaultCustomerIdentifier(theMAPPRequest), strLoanID, strProductName);*/

                FlexicoreHashMap loanQualificationMap = loanQualificationWrapper.getSingleRecord();

                if (loanQualificationWrapper.hasErrors()) {

                    USSDAPIConstants.StandardReturnVal returnVal = loanQualificationMap.getValue("cbs_api_return_val");
                    USSDAPIConstants.Condition condition = loanQualificationMap.getValue("end_session");
                    strResponseText = loanQualificationMap.getStringValue("display_message");
                    strTitle = "Error";

                    strCharge = "NO";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

//                elData.setTextContent(strResponseText);

                    Element elQualification = doc.createElement("QUALIFICATION");
                    elData.appendChild(elQualification);

                    Element elAmount = doc.createElement("AMOUNT");
                    elQualification.appendChild(elAmount);

                    Element elMinimum = doc.createElement("MINIMUM");
                    elAmount.appendChild(elMinimum);

                    Element elMaximum = doc.createElement("MAXIMUM");
                    elAmount.appendChild(elMaximum);

                    Element elNarration = doc.createElement("NARRATION");
                    elQualification.appendChild(elNarration);

                    elMinimum.setTextContent("0");

                    elMaximum.setTextContent("0");

                    elNarration.setTextContent(strResponseText);

                    Element elQualificationParams = doc.createElement("APPLICATION_PARAMS");
                    elData.appendChild(elQualificationParams);

                } else {

                    Element elQualification = doc.createElement("QUALIFICATION");
                    elData.appendChild(elQualification);

                    Element elAmount = doc.createElement("AMOUNT");
                    elQualification.appendChild(elAmount);

                    Element elMinimum = doc.createElement("MINIMUM");
                    elAmount.appendChild(elMinimum);

                    Element elMaximum = doc.createElement("MAXIMUM");
                    elAmount.appendChild(elMaximum);

                    Element elNarration = doc.createElement("NARRATION");
                    elQualification.appendChild(elNarration);

                    Element elQualificationParams = doc.createElement("APPLICATION_PARAMS");
                    elData.appendChild(elQualificationParams);

                    strTitle = "Loan Eligibility";
                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                    FlexicoreHashMap loanLimitMap = loanQualificationMap.getFlexicoreHashMap("payload");

//                    String strEligibleAmount = loanLimitMap.getStringValue("eligible_amount");
                    //comment
                    String strEligibleAmount = "30000";
                    String strMinAmount = loanLimitMap.getStringValue("loan_type_min_amount");
                    String strMaxAmount = loanLimitMap.getStringValue("loan_type_max_amount");
//                    String strMaxAmount = "30000";

                    double dblEligibleAmount = Double.parseDouble(strEligibleAmount);
                    double dblMinimumApplicable = Double.parseDouble(strMinAmount);
                    double dblMaximumApplicable = Double.parseDouble(strMaxAmount);

                    double dblMaxCanApply = Math.min(dblEligibleAmount, dblMaximumApplicable);

                    elMinimum.setTextContent(strMinAmount);

                    elMaximum.setTextContent(strEligibleAmount);

                    String reason = loanLimitMap.getStringValueOrIfNull("reason", "");

                    String strFormattedAmount = Utils.formatDouble(strEligibleAmount, "#,##0.00");

                    String strNarration = "Min: KES " + Utils.formatDouble(strMinAmount, "#,##0.00") + " Max: KES " + Utils.formatDouble(dblMaxCanApply, "#,##0.00") + ".";

                    //String strResponse = "Eligible Amount: KES " + strFormattedAmount + "<br/>";

                    if (!reason.isBlank()) {
                        strNarration = reason;
                    }

                    elNarration.setTextContent(strNarration);
                }
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            System.out.println("\n\nTHE LOAN LIMIT RESPONSE\n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse loanRepayment(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("loanRepayment");
            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_C2B);
            String strSender = pesaParam.getSenderIdentifier();

            /*
            <?xml version='1.0'?>
<MESSAGES DATE_TIME="2022-08-03 12:22:58" VERSION="1.01">
    <LOGIN USERNAME="254706405989" PASSWORD="1111" APP_ID="ff25cf80-12fc-11ed-bcb1-3d78b62710b5" />
    <MSG SESSION_ID="3066751" SESSION_KEY="cbd098f6-130c-11ed-a749-def46c66082d" PRODUCT_ID="45" TYPE="MOBILE_BANKING" ACTION="PAY_LOAN" PARAMETERS_VERSION="1.0004">
        <LOAN AMOUNT="4000.00" BALANCE="4000.00" CHANGE_AMOUNT="YES" NAME="M-LOAN" SERIAL_NO="FS000481888">
            <OTHER_DETAILS>
                <ACCOUNT_CD>73</ACCOUNT_CD>
                <PRODUCT_NAME>M-LOAN</PRODUCT_NAME>
            </OTHER_DETAILS>
        </LOAN>
        <AMOUNT>40</AMOUNT>
        <REPAYMENT_OPTION>ACCOUNT</REPAYMENT_OPTION>
        <ACCOUNT_NO>315114379002</ACCOUNT_NO>
    </MSG>
</MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            Node ndRequestMSG = theMAPPRequest.getMSG();

            System.out.println();
            System.out.println(MBankingAPIUtils.serializeXMLDocNode(ndRequestMSG));
            System.out.println();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            /*String strLoanId =  configXPath.evaluate("AMOUNT/@LOAN_SERIAL_NO", ndRequestMSG).trim();
            String strAmount =  configXPath.evaluate("AMOUNT", ndRequestMSG).trim();
            String strLoanCD =  configXPath.evaluate("ACCOUNT_CD", ndRequestMSG).trim();
            String strProductName =  configXPath.evaluate("ACCOUNT_CD", ndRequestMSG).trim();*/

           /* String strLoanNo = configXPath.evaluate("AMOUNT/@LOAN_SERIAL_NO", ndRequestMSG).trim();
            String strLoanName = configXPath.evaluate("OTHER_DETAILS/PRODUCT_NAME", ndRequestMSG).trim();
            String strLoanCd = configXPath.evaluate("OTHER_DETAILS/ACCOUNT_CD", ndRequestMSG).trim();*/


            /*<MSG ACTION="PAY_LOAN" PARAMETERS_VERSION="1.000204" PRODUCT_ID="4" SEQ="8" SERVER_ID="100201" SESSION_ID="27017231" SESSION_KEY="810cf71c-ab30-11ef-bc4b-f2a2b77b3e48" TRACE_ID="810d1c11-ab30-11ef-bc4b-f2a2b77b3e48" TYPE="MOBILE_BANKING">
            <AMOUNT LOAN_SERIAL_NO="BLN110593">10</AMOUNT><REPAYMENT_OPTION TYPE="MPESA">MPESA</REPAYMENT_OPTION></MSG>
             * */

            String strLoanNo = configXPath.evaluate("AMOUNT/@LOAN_SERIAL_NO", theMAPPRequest.getMSG()).trim();
            String strAmount = configXPath.evaluate("AMOUNT", ndRequestMSG).trim();

            strAmount = strAmount.replace(",", "");
            String strRepaymentOption = configXPath.evaluate("REPAYMENT_OPTION", ndRequestMSG).trim();

            String strRepaymentAccountNo = configXPath.evaluate("ACCOUNT_NO", ndRequestMSG).trim();


            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            switch (strRepaymentOption) {
                case "MPESA": {

                    String strResponseText = "";
                    String strTitle = "";
                    String strCharge = "NO";

                    String strMin = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.DEPOSIT).getMinimum();
                    String strMax = getParam(MAPPAPIConstants.MAPP_PARAM_TYPE.DEPOSIT).getMaximum();

                    double dblAmount = Double.parseDouble(strAmount);
                    double dblMax = Double.parseDouble(strMax);

                    if (dblAmount > dblMax) {

                        strTitle = "ERROR: Deposit Request";
                        strResponseText = "You can only make a maximum payment of KES " + Utils.formatDouble(strMax, "#,##0.00") + " per transaction using M-PESA option.";

                        enResponseAction = CON;
                        enResponseStatus = FAILED;

                    } else {

                        String strReceiver = strUsername;
                        String strReceiverDetails = strReceiver;


                        double lnAmount = Utils.stringToDouble(strAmount);

                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        boolean blPesaStkPushStatus = false;

                        PESAAPI thePESAAPI = new PESAAPI();

                        String strTraceID = getTraceID(theMAPPRequest);

                        blPesaStkPushStatus = thePESAAPI.pesa_C2B_Request(
                                strUsername,
                                strMemberName,
                                strTraceID,
                                "MAPP",
                                strLoanNo,
                                strLoanNo,
                                "MBANKING_SERVER",
                                strTraceID,
                                strUsername,
                                strMemberName,
                                strLoanNo,
                                lnAmount,
                                "LOAN_REPAYMENT");

                        if (blPesaStkPushStatus) {
                            strTitle = "Deposit Request";
                            strResponseText = "You will be prompted by M-PESA for payment<br/>Paybill no: <b>" + strSender + "</b><br/>" + "A/C: <b>" + strLoanNo + "</b><br/>" + "Amount: <b>KES " + strAmount + "</b>";
                        } else {
                            strTitle = "ERROR: Deposit Request";
                            strResponseText = "Use the details below to pay via M-PESA<br/>Paybill no: <b>" + strSender + "</b><br/>" + "A/C: <b>" + strLoanNo + "</b><br/>" + "Amount: <b>KES " + strAmount + "</b>";

                            enResponseAction = CON;
                            enResponseStatus = FAILED;
                        }
                    }

                    //End USSD.

                    Element elData = doc.createElement("DATA");
                    elData.setTextContent(strResponseText);

                    generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                    //Response
                    Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                    theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
                    break;
                }
                //case "Savings Account": {
                default: {

                    String strTransactionReference = strTransactionID;
                    String strDestinationAccount = strLoanNo;
                    String strSourceAccount = strRepaymentOption;

                    String[] strSourceArr = strSourceAccount.split(Pattern.quote("||"));
                    strSourceAccount = strSourceArr[0];

                    String strTraceID = getTraceID(theMAPPRequest);

                    String strTransactionDescription = "Loan Repayment. Source A/C: " + strSourceAccount + " - Destination A/C: " + strDestinationAccount;

                   /* String strAction = "IFT_ACCOUNT_TO_ACCOUNT";

                    HashMap<String,String> hmRVal = CBSAPI.internalFundsTransfer(strTraceID, "MSISDN", strUsername, strPassword,"APP_ID", strAppID,
                            strTransactionReference, strSourceAccount, strDestinationAccount, strAmount, strTransactionID,
                            "MBANKING_SERVER", "MAPP", strTransactionDescription, MBankingDB.getDBDateTime(), strAction);*/


                    String strOriginatorId = UUID.randomUUID().toString();
                    TransactionWrapper<FlexicoreHashMap> loanPaymentViaSavingsWrapper = CBSAPI.loanPaymentViaSavings(
                            strUsername,
                            "MSISDN",
                            strUsername,
                            "APP_ID",
                            strAppID,
                            strOriginatorId,
                            strSourceAccount,
                            strLoanNo,
                            Double.parseDouble(strAmount),
                            strTransactionDescription,
                            theMAPPRequest.getTraceID(),
                            "MAPP",
                            "MBANKING");


                    FlexicoreHashMap loanPaymentViaSavingMap = loanPaymentViaSavingsWrapper.getSingleRecord();

                    String strTitle = "";
                    String strResponseText = "";

                    CBSAPI.SMSMSG cbsMSG = loanPaymentViaSavingMap.getValue("msg_object");

                    String strCharge = "NO";


                    ChannelService channelService = new ChannelService();
                    channelService.setOriginatorId(strOriginatorId);
                    channelService.setTransactionCategory("LOAN_PAYMENT_VIA_SAVINGS");

                    if (loanPaymentViaSavingsWrapper.hasErrors()) {
                        strTitle = loanPaymentViaSavingMap.getStringValue("title");
                        strResponseText = loanPaymentViaSavingMap.getStringValue("display_message");
                        enResponseAction = CON;
                        enResponseStatus = FAILED;

                        channelService.setTransactionStatusCode(104);
                        channelService.setTransactionStatusName("FAILED");
                        channelService.setTransactionStatusDescription(loanPaymentViaSavingMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));


                    } else {
                        strTitle = "Transaction Accepted";
                        strResponseText = "Your Loan Payment request has been completed successfully.";
                        strCharge = "YES";
                        enResponseAction = CON;
                        enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

                        channelService.setTransactionStatusCode(102);
                        channelService.setTransactionStatusName("SUCCESS");
                        channelService.setTransactionStatusDescription("Transaction Completed Successfully");
                        channelService.setBeneficiaryReference(loanPaymentViaSavingMap.getStringValue("cbs_transaction_reference"));
                        channelService.setSourceReference(loanPaymentViaSavingMap.getStringValue("cbs_transaction_reference"));

                    }

                    if (cbsMSG != null) {
                        sendSMS(strUsername, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_PAYMENT", theMAPPRequest);
                    }

                    channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

                    channelService.setInitiatorType("MSISDN");
                    channelService.setInitiatorIdentifier(strUsername);
                    channelService.setInitiatorAccount(strUsername);
                    channelService.setInitiatorName(strMemberName);
                    channelService.setInitiatorReference(theMAPPRequest.getTraceID());
                    channelService.setInitiatorApplication("MAPP");
                    channelService.setInitiatorOtherDetails("<DATA/>");

                    channelService.setSourceType("ACCOUNT_NO");
                    channelService.setSourceIdentifier(strSourceAccount);
                    channelService.setSourceAccount(strSourceAccount);
                    channelService.setSourceName(strSourceAccount);
                    channelService.setSourceApplication("CBS");
                    channelService.setSourceOtherDetails("<DATA/>");

                    channelService.setBeneficiaryType("ACCOUNT_NO");
                    channelService.setBeneficiaryIdentifier(strLoanNo);
                    channelService.setBeneficiaryAccount(strLoanNo);
                    channelService.setBeneficiaryName(strLoanNo);
                    channelService.setBeneficiaryApplication("CBS");
                    channelService.setBeneficiaryOtherDetails("<DATA/>");

                    channelService.setTransactionCurrency("KES");
                    channelService.setTransactionAmount(Double.parseDouble(strAmount));

                    TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.IFT_LOAN_REPAYMENT.getValue(),
                            Double.parseDouble(strAmount));

                    if (chargesWrapper.hasErrors()) {
                        channelService.setTransactionCharge(0.00);
                        channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

                    } else {
                        channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                        channelService.setTransactionOtherDetails("<DATA/>");
                    }

                    channelService.setTransactionRemark(strTransactionDescription);
                    ChannelService.insertService(channelService);

                    Element elData = doc.createElement("DATA");
                    elData.setTextContent(strResponseText);


                          /*  String strRequestStatus = hmRVal.get("transaction_status");
                            String strRequestStatusDescription = hmRVal.get("transaction_status_description");
                            String strFundsTransferStatus = strRequestStatus;

                            String strTitle = "";
                            String strResponseText = "";

                            String strCharge = "NO";

                            switch (strFundsTransferStatus) {
                                case "SUCCESS": {
                                    strTitle= "Transaction Accepted";
                                    strResponseText = "Your loan repayment request has been accepted successfully. Kindly wait as it is being processed";
                                    strCharge = "YES";
                                    enResponseAction = CON;
                                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                    break;
                                }
                                case "ERROR": {
                                    strTitle= "Transaction Error";
                                    strResponseText = "An error occurred while making your request for funds transfer. Please try again.";
                                    enResponseAction = CON;
                                    enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                                    break;
                                }
                                case "INSUFFICIENT_BAL": {
                                    strTitle= "Insufficient Balance";
                                    strResponseText = "Error, you do not have sufficient balance in your account to complete this request";
                                    enResponseAction = CON;
                                    enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                                    break;
                                }
                                case "ACCOUNT_NOT_FOUND":
                                case "ACC_NOT_FOUND": {
                                    strTitle= "Account Not Found";
                                    strResponseText = "Error, your account could not be found, please try again";
                                    enResponseAction = MAPPConstants.ResponseAction.END;
                                    enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
                                    break;
                                }
                                default: {
                                    enResponseAction = MAPPConstants.ResponseAction.END;
                                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                                    strTitle= "ERROR: Loan Repayment";
                                    strResponseText = "An error occurred. Please try again after a few minutes.";
                                }
                            }

                            Element elData = doc.createElement("DATA");
                            elData.setTextContent(strResponseText);*/

                    generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

                    //Response
                    Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

                        /*System.out.println("\n\nLOAN PAYMENT RESPONSE\n");
                        System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));*/

                    theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
                    break;
                }
            }


        } catch (Exception e) {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            e.printStackTrace();

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse loanStatement(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();
            String statementType = "FULL_STATEMENT";

            String strTrailerMessageXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strTrailerMessageXML);

            String strNumberOfEntries = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/LOAN_STATEMENT_ENTRIES");

            int intMaximumTransactionCount = Integer.parseInt(strNumberOfEntries);


            /*System.out.println("\n\nTHE LOAN STATEMENT REQUEST\n\n");
            System.out.println(XmlUtils.convertNodeToStr(theMAPPRequest.getMSG()));*/


            String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", theMAPPRequest.getMSG()).trim();

            System.out.println(strLoanNo);

            /*String strLoanName = configXPath.evaluate("OTHER_DETAILS/PRODUCT_NAME", theMAPPRequest.getMSG()).trim();
            String strLoanCd = configXPath.evaluate("OTHER_DETAILS/ACCOUNT_CD", theMAPPRequest.getMSG()).trim();
            */

            //String strLoanNo = configXPath.evaluate("LOAN/@SERIAL_NO", theMAPPRequest.getMSG()).trim();
            //String strLoanName = configXPath.evaluate("LOAN/OTHER_DETAILS/PRODUCT_NAME", theMAPPRequest.getMSG()).trim();

            String strStartDate = configXPath.evaluate("FROM", theMAPPRequest.getMSG()).trim();
            String strEndDate = configXPath.evaluate("TO", theMAPPRequest.getMSG()).trim();

            String strLoanMinistatementStatus = "ERROR";

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.getLoanFullStatement(strUsername, "MSISDN", strUsername,
                    "APP_ID", strAppID, strLoanNo, "100",
                    strStartDate + " 00:00:00", strEndDate + " 23:59:59");

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_FULL_STATEMENT.getValue());


            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Loan Statement";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TABLE;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (miniStatementWrapper.hasErrors()) {
                strTitle = "Error: Loan Statement Failed";
                elData.setTextContent("An error occurred while processing your request. Please try again in a few minutes");

                enResponseStatus = ERROR;

                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(miniStatementMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));


            } else {
                FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");
                if (allTransactionsList.isEmpty()) {
                    enResponseStatus = FAILED;
                    strCharge = "NO";
                    strTitle = "Error: No Statement Found";
                    elData.setTextContent("You do not have any loan transactions within this time period");

                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("You do not have any statements within this time period");


                } else {

                    String strLoanBalance = miniStatementMap.getStringValue("account_available_balance");

                    strLoanBalance = Utils.formatDouble(strLoanBalance, "#,##0.00");

                    Element elBalance = doc.createElement("BALANCE");
                    elBalance.setTextContent(strLoanBalance);
                    elData.appendChild(elBalance);

                    Element elAccountNo = doc.createElement("ACCOUNTNO");
                    elAccountNo.setTextContent(strLoanNo);
                    elData.appendChild(elAccountNo);

                    Element elAccountName = doc.createElement("NAME");
                    elAccountName.setTextContent(miniStatementMap.getStringValue("account_name"));
                    elData.appendChild(elAccountName);

                    Element elTable = doc.createElement("TABLE");
                    elData.appendChild(elTable);

                    Element elTrHeading = doc.createElement("TR");
                    elTable.appendChild(elTrHeading);

                    Element elThHeading1 = doc.createElement("TH");
                    elThHeading1.setTextContent("Description");
                    elTrHeading.appendChild(elThHeading1);

                    Element elThHeading2 = doc.createElement("TH");
                    elThHeading2.setTextContent("Amount");
                    elTrHeading.appendChild(elThHeading2);

                    Element elThHeading3 = doc.createElement("TH");
                    elThHeading3.setTextContent("Date");
                    elTrHeading.appendChild(elThHeading3);

                    /*Element elThHeading4 = doc.createElement("TH");
                    elThHeading4.setTextContent("Ref");
                    elTrHeading.appendChild(elThHeading4);*/

                    Element elThHeading5 = doc.createElement("TH");
                    elThHeading5.setTextContent("Balance");
                    elTrHeading.appendChild(elThHeading5);

                    int i = 0;
                    for (FlexicoreHashMap transactionMap : allTransactionsList) {
                        //String strMSGTransactionReference = transactionMap.getStringValue("reference");

                        String strMSGTransactionReference = transactionMap.getStringValue("transaction_reference");
                        String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("transaction_date_time");
                        // strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                        String strMSGTransactionAmount = transactionMap.getStringValue("transaction_amount");

                        String strMSGTransactionDescription = transactionMap.getStringValueOrIfNull("transaction_description", "");
                        String strMSGRunningBalance = transactionMap.getStringValue("running_balance");
                        //String strMSGIntRunningBalance = transactionMap.getStringValue("int_running_balance").trim();

                        //strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");
                        //strMSGRunningBalance = strMSGRunningBalance.replace("-", "");
                        //strMSGIntRunningBalance = strMSGIntRunningBalance.replace("-", "");

                        String strMSGFormattedTransactionAmount = Utils.formatDouble(strMSGTransactionAmount, "#,##0.00");
                        String strMSGFormattedRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                        //strMSGRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                        Element elTrBody = doc.createElement("TR");
                        elTable.appendChild(elTrBody);

                        Element elTDBody1 = doc.createElement("TD");
                        elTDBody1.setTextContent(strMSGTransactionDescription);
                        elTrBody.appendChild(elTDBody1);

                        Element elTDBody2 = doc.createElement("TD");
                        elTDBody2.setTextContent(strMSGFormattedTransactionAmount);
                        elTrBody.appendChild(elTDBody2);

                        Element elTDBody3 = doc.createElement("TD");
                        elTDBody3.setTextContent(strMSGFormattedTransactionDateTime);
                        elTrBody.appendChild(elTDBody3);

                        /*Element elTDBody4 = doc.createElement("TD");
                        elTDBody4.setTextContent("lkjhgfdsdfghjk");
                        elTrBody.appendChild(elTDBody4);*/

                        Element elTDBody5 = doc.createElement("TD");
                        elTDBody5.setTextContent(strMSGFormattedRunningBalance);
                        elTrBody.appendChild(elTDBody5);

                        if (i >= intMaximumTransactionCount) {
                            break;
                        }

                        i++;
                    }

                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Loan Statement Generated Successfully");
                }
            }

            channelService.setBeneficiaryReference("");
            channelService.setSourceReference("");
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strUsername);
            channelService.setInitiatorAccount(strUsername);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theMAPPRequest.getTraceID());
            channelService.setInitiatorApplication("MAPP");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strLoanNo);
            channelService.setSourceAccount(strLoanNo);
            channelService.setSourceName(strLoanNo);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strUsername);
            channelService.setBeneficiaryAccount(strUsername);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.LOAN_FULL_STATEMENT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Loan Full Statement for A/C: " + strLoanNo);
            ChannelService.insertService(channelService);

           /* if (strLoanMinistatementStatus.equals("SUCCESS")) {
                String strLoanBalance = "KES "+Utils.formatDouble(hmLoanStatementDetails.get("loan_balance"), "#,##0.00");
                if (hmLoanStatementTransactions != null && !hmLoanStatementTransactions.isEmpty()) {
                    Element elBalance = doc.createElement("BALANCE");
                    elBalance.setTextContent(strLoanBalance);
                    elData.appendChild(elBalance);

                    Element elTable = doc.createElement("TABLE");
                    elData.appendChild(elTable);


                    Element elTrHeading = doc.createElement("TR");
                    elTable.appendChild(elTrHeading);

                    Element elThHeading1 = doc.createElement("TH");
                    elThHeading1.setTextContent("Description");
                    elTrHeading.appendChild(elThHeading1);

                    Element elThHeading2 = doc.createElement("TH");
                    elThHeading2.setTextContent("Amount");
                    elTrHeading.appendChild(elThHeading2);

                    Element elThHeading3 = doc.createElement("TH");
                    elThHeading3.setTextContent("Date");
                    elTrHeading.appendChild(elThHeading3);

                    Element elThHeading4 = doc.createElement("TH");
                    elThHeading4.setTextContent("Ref");
                    elTrHeading.appendChild(elThHeading4);

                    Element elThHeading5 = doc.createElement("TH");
                    elThHeading5.setTextContent("Balance");
                    elTrHeading.appendChild(elThHeading5);

                    for (String index : hmLoanStatementTransactions.keySet()) {
                        HashMap<String, String> hmTransaction = hmLoanStatementTransactions.get(index);
                        String strDate = hmTransaction.get("transaction_date_time");
                        String strDesc = hmTransaction.get("transaction_description");
                        String strAmount = "KES "+Utils.formatDouble(hmTransaction.get("transaction_amount"), "#,##0.00");
                        String strReference = hmTransaction.get("transaction_reference");
                        String strBalance = "KES "+Utils.formatDouble(hmTransaction.get("running_balance"), "#,##0.00");

                        Element elTrBody = doc.createElement("TR");
                        elTable.appendChild(elTrBody);

                        Element elTDBody1 = doc.createElement("TD");
                        elTDBody1.setTextContent(strDesc);
                        elTrBody.appendChild(elTDBody1);

                        Element elTDBody2 = doc.createElement("TD");
                        elTDBody2.setTextContent(strAmount);
                        elTrBody.appendChild(elTDBody2);

                        Element elTDBody3 = doc.createElement("TD");
                        elTDBody3.setTextContent(strDate);
                        elTrBody.appendChild(elTDBody3);

                        Element elTDBody4 = doc.createElement("TD");
                        elTDBody4.setTextContent(strReference);
                        elTrBody.appendChild(elTDBody4);

                        Element elTDBody5 = doc.createElement("TD");
                        elTDBody5.setTextContent(strBalance);
                        elTrBody.appendChild(elTDBody5);
                    }
                } else {
                    strCharge = "NO";
                    strTitle = "No Statements Found";
                    elData.setTextContent("No loan transactions found for the specified loan");
                }
            } else {
                strCharge = "NO";
                strTitle= "ERROR: Loan Statement";
                elData.setTextContent("An error occurred. Please try again after a few minutes.");
                enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
            }*/

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse loanStatementBase64(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();
            int intMaxNumberOfTransactions = 100;
            String statementType = "FULL_STATEMENT";

            //String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", theMAPPRequest.getMSG()).trim();

            String strTrailerMessageXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strTrailerMessageXML);

            String strNumberOfEntries = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/LOAN_STATEMENT_ENTRIES");

            int intMaximumTransactionCount = Integer.parseInt(strNumberOfEntries);

            //String strLoanNo = configXPath.evaluate("LOAN/@SERIAL_NO", theMAPPRequest.getMSG()).trim();
            String strLoanNo = configXPath.evaluate("ACCOUNT_NO", theMAPPRequest.getMSG()).trim();

            //String strLoanNo = configXPath.evaluate("LOAN/@SERIAL_NO", theMAPPRequest.getMSG()).trim();
            //String strLoanName = configXPath.evaluate("LOAN/OTHER_DETAILS/PRODUCT_NAME", theMAPPRequest.getMSG()).trim();
            //String strLoanCd = configXPath.evaluate("LOAN/OTHER_DETAILS/ACCOUNT_CD", theMAPPRequest.getMSG()).trim();

            String strStartDate = configXPath.evaluate("FROM", theMAPPRequest.getMSG()).trim();
            String strEndDate = configXPath.evaluate("TO", theMAPPRequest.getMSG()).trim();

            String strLoanMinistatementStatus = "ERROR";

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.getLoanFullStatement(strUsername, "MSISDN", strUsername,
                    "APP_ID", strAppID, strLoanNo, "100",
                    strStartDate + " 00:00:00", strEndDate + " 23:59:59");

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

            String strMemberName = getUserFullName(theMAPPRequest, strUsername);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_FULL_STATEMENT.getValue());


            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strTitle = "Loan Statement";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TABLE;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            if (miniStatementWrapper.hasErrors()) {
                strTitle = "Error: Loan Statement Failed";
                elData.setTextContent("An error occurred while processing your request. Please try again in a few minutes");

                enResponseStatus = ERROR;

                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(miniStatementMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));

            } else {
                FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");
                if (allTransactionsList.isEmpty()) {
                    enResponseStatus = FAILED;
                    strCharge = "NO";
                    strTitle = "Error: No Statement Found";
                    elData.setTextContent("You do not have any loan transactions within this time period");

                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("You do not have any statements within this time period");

                } else {

                    String theAccountStatement = AccountStatements.getLoanStatementHTML();

                    String strFormattedPeriod = DateTime.convertStringToDateToString(strStartDate, "yyyy-MM-dd", "dd MMM yyyy");
                    strFormattedPeriod = strFormattedPeriod + " to ";
                    strFormattedPeriod = strFormattedPeriod + DateTime.convertStringToDateToString(strEndDate, "yyyy-MM-dd", "dd MMM yyyy");

                    theAccountStatement = theAccountStatement.replace("[STATEMENT_PERIOD]", Misc.escapeHtmlEntity(strFormattedPeriod));

                    String strAvailableBalance = miniStatementMap.getStringValue("account_available_balance");

                    strAvailableBalance = Utils.formatDouble(strAvailableBalance, "#,##0.00");

                    theAccountStatement = theAccountStatement.replace("[LOAN_BALANCE]", "KES " + strAvailableBalance);

                    theAccountStatement = theAccountStatement.replace("[LOAN_NAME]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_name")));

                    theAccountStatement = theAccountStatement.replace("[LOAN_NUMBER]", Misc.escapeHtmlEntity(strLoanNo));

                    theAccountStatement = theAccountStatement.replace("[LOAN_CUSTOMER_NAME]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_holder")));

                    StringBuilder builder = new StringBuilder();

                    int i = 0;

                    int size = allTransactionsList.size();

                    int endIndex = Math.min(size, intMaximumTransactionCount);

                    List<FlexicoreHashMap> tempTransactionsList = allTransactionsList.subList(0, endIndex);

                    for (int index = tempTransactionsList.size() - 1; index >= 0; index--) {
                        FlexicoreHashMap transactionMap = tempTransactionsList.get(index);

                        //String strAmount = transactionMap.getStringValue("amount");

                        String strMSGTransactionReference = transactionMap.getStringValue("transaction_reference");
                        String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("transaction_date_time");
                        //strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                        String strMSGTransactionAmount = transactionMap.getStringValue("transaction_amount").replace(",", "");

                        //strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");

                        String strMSGTransactionDescription = transactionMap.getStringValueOrIfNull("transaction_description", "");
                        String strMSGRunningBalance = transactionMap.getStringValue("running_balance");
                        //String strMSGIntRunningBalance = transactionMap.getStringValue("int_running_balance").trim();

                        // strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");
                        //strMSGRunningBalance = strMSGRunningBalance.replace("-", "");
                        //strMSGIntRunningBalance = strMSGIntRunningBalance.replace("-", "");

                        //strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd MMM yyyy");

                        builder.append("<tr>\n" +
                                "                <td class='statement-header-acc-stmnt-date'>" + Misc.escapeHtmlEntity(strMSGFormattedTransactionDateTime) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-description'>" + Misc.escapeHtmlEntity(strMSGTransactionDescription) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-amount'>" + Utils.formatDouble(strMSGTransactionAmount, "#,##0.00") + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-balance'>" + Utils.formatDouble(strMSGRunningBalance, "#,##0.00") + "</td>\n" +
                                "            </tr>");

                    }

                    //List<FlexicoreHashMap> tempTransactionsList = allTransactionsList.subList(startIndex, size);

                   /* for (int index = allTransactionsList.size() - 1; index >= 0; index--) {
                        FlexicoreHashMap transactionMap = allTransactionsList.get(index);

                        String strMSGFormattedTransactionDateTime = transactionMap.getStringValue("raw_date");
                        String strAmount = transactionMap.getStringValue("amount");
                        String strMSGTransactionDescription = transactionMap.getStringValue("description");
                        String strMSGRunningBalance = transactionMap.getStringValue("running_balance");

                        strAmount = strAmount.replace("-", "");

                        strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGFormattedTransactionDateTime, "yyyy-MM-dd'T'HH:mm:ss", "dd MMM yyyy");

                        builder.append("<tr>\n" +
                                "                <td class='statement-header-acc-stmnt-date'>" + Misc.escapeHtmlEntity(strMSGFormattedTransactionDateTime) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-description'>" + Misc.escapeHtmlEntity(strMSGTransactionDescription) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-amount'>" + Utils.formatDouble(strAmount, "#,##0.00") + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-balance'>" + Utils.formatDouble(strMSGRunningBalance, "#,##0.00") + "</td>\n" +
                                "            </tr>");

                    }*/


                    theAccountStatement = theAccountStatement.replace("[THE_LOAN_STATEMENT_DETAILS]", builder.toString());
                    theAccountStatement = AccountStatements.generateAccountStatementPDF(theAccountStatement, strLoanNo);
                    elData.setTextContent(theAccountStatement);


                    channelService.setTransactionStatusCode(102);
                    channelService.setTransactionStatusName("SUCCESS");
                    channelService.setTransactionStatusDescription("Loan Statement Generated Successfully");

                }
            }


            channelService.setBeneficiaryReference("");
            channelService.setSourceReference("");
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strUsername);
            channelService.setInitiatorAccount(strUsername);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theMAPPRequest.getTraceID());
            channelService.setInitiatorApplication("MAPP");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strLoanNo);
            channelService.setSourceAccount(strLoanNo);
            channelService.setSourceName(strLoanNo);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strUsername);
            channelService.setBeneficiaryAccount(strUsername);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strUsername, "MSISDN", strUsername, AppConstants.ChargeServices.LOAN_FULL_STATEMENT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Loan Full Statement for A/C: " + strLoanNo);
            ChannelService.insertService(channelService);


            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            /*System.out.println("\n\nTHE LOAN BASE64\n\n");
            System.out.println(XmlUtils.convertNodeToStr(ndResponseMSG));*/

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse mandateNotActive(MAPPRequest theMAPPRequest, String strTitle) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='ACCOUNT_BALANCE' VERSION='1.01'>
                      <ACCOUNT_NO>123456</ACCOUNT_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strCharge = "NO";
            String strResponseText = AppConstants.strServiceUnavailable;

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, FAILED, strCharge, strTitle, TEXT);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse serviceOnMaintenance(MAPPRequest theMAPPRequest, String strTitle, String strMessage) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='ACCOUNT_BALANCE' VERSION='1.01'>
                      <ACCOUNT_NO>123456</ACCOUNT_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strCharge = "NO";
            //String strResponseText = "Sorry, this service is on maintenance. Please try again later";

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strMessage);

            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, FAILED, strCharge, strTitle, TEXT);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }


    public MAPPResponse loanGuarantors(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", theMAPPRequest.getMSG()).trim();


            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());

            String strLoansXML = "";
                    /* "<Loan>\n" +
                    "        <Security>\n" +
                    "            <Name>William Ochomo</Name>\n" +
                    "            <AmountGuaranteed>5000</AmountGuaranteed>\n" +
                    "            <LoanNo>FLN037</LoanNo>\n" +
                    "            <MobileNo>0723782649</MobileNo>\n" +
                    "            <CurrentCommitment>5000</CurrentCommitment>\n" +
                    "            <Type>Pending</Type>\n" +
                    "        </Security>\n" +
                    "        <Security>\n" +
                    "            <Name>Vincent Murithi</Name>\n" +
                    "            <AmountGuaranteed>5000</AmountGuaranteed>\n" +
                    "            <LoanNo>FLN037</LoanNo>\n" +
                    "            <MobileNo>0790491947</MobileNo>\n" +
                    "            <CurrentCommitment>5000</CurrentCommitment>\n" +
                    "            <Type>Pending</Type>\n" +
                    "        </Security>\n" +
                    "</Loan>";*/

            System.out.println("NAV Returned: " + strLoansXML);

            InputSource source = new InputSource(new StringReader(strLoansXML));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);

            NodeList nlTransactions = ((NodeList) configXPath.evaluate("Loan/Security", xmlDocument, XPathConstants.NODESET));

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loan Guarantors";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TABLE;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            /*    String strLoanBalanceXML = "";

             *//* "<Loans>\n" +
                    "    <Product>\n" +
                    "        <LoanNo>LN12345</LoanNo>\n" +
                    "        <LoanBalance>10000</LoanBalance>\n" +
                    "    </Product>\n" +
                    "    <Product>\n" +
                    "        <LoanNo>LN67890</LoanNo>\n" +
                    "        <LoanBalance>10000</LoanBalance>\n" +
                    "    </Product>\n" +
                    "</Loans>";*//*
            InputSource sourceForBalance = new InputSource(new StringReader(strLoanBalanceXML));
            DocumentBuilderFactory builderFactoryForBalance = DocumentBuilderFactory.newInstance();
            DocumentBuilder builderForBalance = builderFactoryForBalance.newDocumentBuilder();
            Document xmlDocumentForBalance = builderForBalance.parse(sourceForBalance);

            NodeList nlLoans = ((NodeList) configXPath.evaluate("Loans/Product", xmlDocumentForBalance, XPathConstants.NODESET));

            String strLoanBalance = "";

            for (int i = 0; i < nlLoans.getLength(); i++) {
                String strLoanId = configXPath.evaluate("LoanNo", nlLoans.item(i)).trim();
                String strLoanBalanceForLoan = configXPath.evaluate("LoanBalance", nlLoans.item(i)).trim();

                if (strLoanId.equals(strLoanNo)) {
                    strLoanBalance = strLoanBalanceForLoan;
                    break;
                }
            }

            Element elBalance = doc.createElement("BALANCE");
            elBalance.setTextContent(strLoanBalance);
            elData.appendChild(elBalance);

            Element elTable = doc.createElement("TABLE");
            elData.appendChild(elTable);


            Element elTrHeading = doc.createElement("TR");
            elTable.appendChild(elTrHeading);

            Element elThHeading1 = doc.createElement("TH");
            elThHeading1.setTextContent("Name");
            elTrHeading.appendChild(elThHeading1);

            Element elThHeading2 = doc.createElement("TH");
            elThHeading2.setTextContent("Amount Guaranteed");
            elTrHeading.appendChild(elThHeading2);

            Element elThHeading4 = doc.createElement("TH");
            elThHeading4.setTextContent("Mobile No");
            elTrHeading.appendChild(elThHeading4);

            Element elThHeading3 = doc.createElement("TH");
            elThHeading3.setTextContent("Loan No");
            elTrHeading.appendChild(elThHeading3);

            Element elThHeading5 = doc.createElement("TH");
            elThHeading5.setTextContent("Current Commitment");
            elTrHeading.appendChild(elThHeading5);

            Element elThHeading6 = doc.createElement("TH");
            elThHeading6.setTextContent("Status");
            elTrHeading.appendChild(elThHeading6);

            for (int i = 0; i < nlTransactions.getLength(); i++) {
                String strName = configXPath.evaluate("Name", nlTransactions.item(i)).trim();
                String strAmountGuaranteed = configXPath.evaluate("AmountGuaranteed", nlTransactions.item(i)).trim();
                String strLoanNumber = configXPath.evaluate("LoanNo", nlTransactions.item(i)).trim();
                String strMobileNo = configXPath.evaluate("MobileNo", nlTransactions.item(i)).trim();
                String strCurrentCommitment = configXPath.evaluate("CurrentCommitment", nlTransactions.item(i)).trim();
                String strType = configXPath.evaluate("Type", nlTransactions.item(i)).trim();


                Element elTrBody = doc.createElement("TR");
                elTable.appendChild(elTrBody);

                Element elTDBody1 = doc.createElement("TD");
                elTDBody1.setTextContent(strName);
                elTrBody.appendChild(elTDBody1);

                Element elTDBody2 = doc.createElement("TD");
                elTDBody2.setTextContent("KES " + strAmountGuaranteed);
                elTrBody.appendChild(elTDBody2);

                Element elTDBody4 = doc.createElement("TD");
                elTDBody4.setTextContent(strMobileNo);
                elTrBody.appendChild(elTDBody4);

                Element elTDBody3 = doc.createElement("TD");
                elTDBody3.setTextContent(strLoanNumber);
                elTrBody.appendChild(elTDBody3);

                Element elTDBody5 = doc.createElement("TD");
                elTDBody5.setTextContent("KES " + strCurrentCommitment);
                elTrBody.appendChild(elTDBody5);

                Element elTDBody6 = doc.createElement("TD");
                elTDBody6.setTextContent(strType);
                elTrBody.appendChild(elTDBody6);
            }*/


            enResponseStatus = FAILED;
            strCharge = "NO";
            strTitle = "Error: No Guarantors Found";
            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse addLoanGuarantors(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            NodeList nlGuarantors = ((NodeList) configXPath.evaluate("LOAN_AND_GUARANTORS/GUARANTORS/GUARANTOR", ndRequestMSG, XPathConstants.NODESET));
            String strLoanEntryNumber = configXPath.evaluate("LOAN_AND_GUARANTORS/LOAN_ENTRY_NO", ndRequestMSG).trim();

            boolean blErrorOccured = false;

            for (int i = 0; i < nlGuarantors.getLength(); i++) {
                String strPhoneNumber = APIUtils.sanitizePhoneNumber(nlGuarantors.item(i).getTextContent().trim());
                String strAdGuarantorResponse = "SUCCESS";

                String strMAPPSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
                String strDatetime = MBankingDB.getDBDateTime().trim();
                String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
                String strFormattedAmount = Utils.formatDouble("10000", "#,##0.00");

                new Thread(() -> {
                    String strMSG = "Dear Moses, ISAAC is requesting your guarantorship for his Development Loan of KES " + strFormattedAmount + " please log in to the mobile application or dial *882*1# to action this request.\n\n" +
                            "Date: " + strFormattedDateTime + "\n" +
                            "Ref: " + strMAPPSessionId + "\n";

                    fnSendSMS(strPhoneNumber, strMSG, "YES", MSGConstants.MSGMode.EXPRESS, 200, "ONE_TIME_PASSWORD", "MAPP", "MBANKING_SERVER", UUID.randomUUID().toString(), UUID.randomUUID().toString());
                }).start();
            }

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loans";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";
            String strResponseText = "An error occurred. Please try again after a few minutes.";

            if (!blErrorOccured) {
                strTitle = "Guarantors Added Successfully";
                strResponseText = "You loan guarantors have been added successfully. Please contact the guarantors so that they can approve guarantorship.";
                strCharge = "YES";
                enResponseAction = CON;
                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
            } else {
                enResponseAction = CON;
                enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                strTitle = "ERROR: Add Loan Guarantors";
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);


            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse loansGuaranteed(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());


            String strLoansXML = "";

                    /*"<Security>" +
                    "<Loan>" +
                    "<LoanNo>FLN036</LoanNo>" +
                    "<Loanee>William Ochomo</Loanee>" +
                    "<MobileNo>254723782649</MobileNo>" +
                    "<LoanType>Development Loan</LoanType>" +
                    "<IssuedDate>08 July 2024</IssuedDate>" +
                    "<EndDate>08 July 2027</EndDate>" +
                    "<Status>Performing</Status>" +
                    "<LoanAmount>KES 24,000</LoanAmount>" +
                    "<Installments>48</Installments>" +
                    "<LoanBalance>KES 24,000</LoanBalance>" +
                    "<DefaultedAmount>0</DefaultedAmount>" +
                    "<AmountGuaranteed>KES 8,000</AmountGuaranteed>" +
                    "</Loan>" +
                    "</Security>";*/
            if (theMAPPRequest.getAction().equalsIgnoreCase("LOAN_GUARANTORSHIP_REQUESTS")) {
                strLoansXML = "";

            /*"<Loans>" +
                        "<Loan>" +
                        "<LoanNo>FLN036</LoanNo>" +
                        "<Loanee>William Ochomo</Loanee>" +
                        "<MobileNo>254723782649</MobileNo>" +
                        "<LoanType>Development Loan</LoanType>" +
                        "<IssuedDate>08 July 2024</IssuedDate>" +
                        "<EndDate>08 July 2027</EndDate>" +
                        "<Status>Performing</Status>" +
                        "<LoanAmount>KES 24,000</LoanAmount>" +
                        "<Installments>48</Installments>" +
                        "<AmountGuaranteed>KES 8,000</AmountGuaranteed>" +
                        "</Loan>" +
                        "</Loans>";*/
            }

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Loan Guaranteed";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TABLE;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");


            enResponseStatus = FAILED;
            strCharge = "NO";
            strTitle = "Error: No Loan Found";

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
            //break here



           /* if (strLoansXML.equalsIgnoreCase("") || strLoansXML.equalsIgnoreCase("NULL")) {
                if (theMAPPRequest.getAction().equalsIgnoreCase("LOANS_GUARANTEED")) {
                    elData.setTextContent("There were no loan found");
                } else if (theMAPPRequest.getAction().equalsIgnoreCase("LOAN_GUARANTORSHIP_REQUESTS")) {
                    elData.setTextContent("There were no loan guarantorship requests found");
                }
                enResponseStatus = MAPPConstants.ResponseStatus.FAILED;
            } else {
                InputSource source = new InputSource(new StringReader(strLoansXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);

                NodeList nlTransactions = ((NodeList) configXPath.evaluate("/", xmlDocument, XPathConstants.NODESET));
                NodeList nlTransaction = ((NodeList) configXPath.evaluate("/", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();
                if (theMAPPRequest.getAction().equalsIgnoreCase("LOANS_GUARANTEED")) {
                    nlTransactions = ((NodeList) configXPath.evaluate("Security/Loan", xmlDocument, XPathConstants.NODESET));
                    nlTransaction = ((NodeList) configXPath.evaluate("Security/Loan", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();
                } else if (theMAPPRequest.getAction().equalsIgnoreCase("LOAN_GUARANTORSHIP_REQUESTS")) {
                    nlTransactions = ((NodeList) configXPath.evaluate("Loans/Loan", xmlDocument, XPathConstants.NODESET));
                    nlTransaction = ((NodeList) configXPath.evaluate("Loans/Loan", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();
                }
            *//*<Security>
                <Loan>
                    <LoanNo>BLN-50367</LoanNo>
                    <Loanee>Abdalla Said Aden</Loanee>
                    <MobileNo>+254725683351</MobileNo>
                    <LoanType>Development Loan</LoanType>
                    <GuarantorType>Guarantor</GuarantorType>
                    <IssuedDate>02/24/16</IssuedDate>
                    <EndDate>04/24/20</EndDate>
                    <Status>Performing</Status>
                    <LoanAmount>300,000</LoanAmount>
                    <Installments>48</Installments>
                    <LoanBalance>148,770</LoanBalance>
                    <DefaultedAmount>0</DefaultedAmount>
                    <AmountGuaranteed>0</AmountGuaranteed>
                    <CurrentCommitment>0</CurrentCommitment>
                </Loan>
            </Security>*//*

                Element elTable = doc.createElement("TABLE");
                elData.appendChild(elTable);


                Element elTrHeading = doc.createElement("TR");
                elTable.appendChild(elTrHeading);

                for (int k = 0; k < nlTransaction.getLength(); k++) {
                    String strHeadingName = nlTransactions.item(0).getChildNodes().item(k).getNodeName();
                    strHeadingName = splitCamelCase(strHeadingName);
                    Element elThHeading1 = doc.createElement("TH");
                    elThHeading1.setTextContent(strHeadingName);
                    elTrHeading.appendChild(elThHeading1);
                }

                for (int i = 0; i < nlTransactions.getLength(); i++) {
                    Element elTrBody = doc.createElement("TR");
                    elTable.appendChild(elTrBody);

                    for (int j = 0; j < nlTransactions.item(i).getChildNodes().getLength(); j++) {
                        String strBodyValue = "";

                        if (theMAPPRequest.getAction().equalsIgnoreCase("LOANS_GUARANTEED")) {
                            strBodyValue = ((NodeList) configXPath.evaluate("Security/Loan", xmlDocument, XPathConstants.NODESET)).item(i).getChildNodes().item(j).getTextContent();//.item(j).getNodeValue();
                        } else if (theMAPPRequest.getAction().equalsIgnoreCase("LOAN_GUARANTORSHIP_REQUESTS")) {
                            strBodyValue = ((NodeList) configXPath.evaluate("Loans/Loan", xmlDocument, XPathConstants.NODESET)).item(i).getChildNodes().item(j).getTextContent();//.item(j).getNodeValue();
                        }

                        Element elTDBody1 = doc.createElement("TD");
                        elTDBody1.setTextContent(strBodyValue);
                        elTrBody.appendChild(elTDBody1);
                    }
                }
            }*/

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            // Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse updateLoanGuarantorStatus(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strLoanNo = configXPath.evaluate("LOAN_SERIAL_NO", ndRequestMSG).trim();
            String strStatus = configXPath.evaluate("STATUS", ndRequestMSG).trim();

            System.out.println("strStatus: " + strStatus);

            String strNavResponse = "SUCCESS";

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            if (strNavResponse.equals("SUCCESS")) {
                strTitle = strStatus.equals("APPROVED") ? "Guarantorship Approved" : "Guarantorship Rejected";
                strResponseText = strStatus.equals("APPROVED") ? "Your request to <b>approve</b> loan guarantorship was received successfully" : "Your request to <b>reject</b> loan guarantorship was received successfully";
                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                strCharge = "YES";

                String strMAPPSessionId = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
                String strDatetime = MBankingDB.getDBDateTime().trim();
                String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");


                new Thread(() -> {
                    String strSMSToLoanee = "Dear William, ISAAC KIPTOO has agreed to guarantee KES 8,000 on your Development Loan, Serial No. FLN036, Amount: KES 24,000.\n" +
                            "Current Status: 1 has Accepted, 2 are Pending.\n" +
                            "Accepted Amount: KES 8,000, Pending Amount: KES 16,000.\n\n" +
                            "Date: " + strFormattedDateTime + "\n" +
                            "Ref: " + strMAPPSessionId;

                    fnSendSMS("254723782649", strSMSToLoanee, "YES", MSGConstants.MSGMode.EXPRESS, 200, "GUARANTORSHIP", "MAPP", "MBANKING_SERVER", UUID.randomUUID().toString(), UUID.randomUUID().toString());
                }).start();

                new Thread(() -> {
                    String strSMSToGuarantor = "Dear Isaac, you have agreed to guarantee WILLIAM OCHOMO KES 8,000 for his Development Loan, Loan No. FLN036\n" +
                            "In case of any enquiries regarding this guarantorship, Kindly contact the SACCO.\n\n" +
                            "Date: " + strFormattedDateTime + "\n" +
                            "Ref: " + strMAPPSessionId;

                    fnSendSMS(strUsername, strSMSToGuarantor, "YES", MSGConstants.MSGMode.EXPRESS, 200, "GUARANTORSHIP", "MAPP", "MBANKING_SERVER", UUID.randomUUID().toString(), UUID.randomUUID().toString());
                }).start();
            } else {
                strTitle = "ERROR";
                strResponseText = "An error occurred. Please try again after a few minutes.";
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }


    public MAPPResponse getHelpAndSupportCategories(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            FlexicoreArrayList supportCategories = CBSAPI.getHelpAndSupportCategories();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Support Categories";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            Element elCategories = doc.createElement("CATEGORIES");
            elData.appendChild(elCategories);

            for (FlexicoreHashMap flexicoreHashMap : supportCategories) {
                Element elCategory = doc.createElement("CATEGORY");
                elCategory.setAttribute("CATEGORY_ID", flexicoreHashMap.getStringValue("category_id"));
                elCategory.setTextContent(flexicoreHashMap.getStringValue("category_name"));
                elCategories.appendChild(elCategory);
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getSupportRequests(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            String strStatus = configXPath.evaluate("STATUS", ndRequestMSG).trim();
            String strDateFrom = configXPath.evaluate("DATE_CREATED/FROM", ndRequestMSG).trim();
            String strDateTo = configXPath.evaluate("DATE_CREATED/TO", ndRequestMSG).trim();

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap signatoryDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("signatory_details");

            FlexicoreArrayList supportRequestsList = CBSAPI.getSupportRequestsList(signatoryDetailsMap.getStringValue("signatory_id"),
                    strStatus, strDateFrom, strDateTo);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Support Requests";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            Element elRequests = doc.createElement("REQUESTS");
            elData.appendChild(elRequests);

            for (FlexicoreHashMap flexicoreHashMap : supportRequestsList) {
                Element elRequest = doc.createElement("REQUEST");

                String status = flexicoreHashMap.getStringValue("status");
                switch (status) {
                    case "OPEN", "UNASSIGNED", "PENDING" -> status = "OPEN";
                }

                elRequest.setAttribute("SUPPORT_ID", flexicoreHashMap.getStringValue("support_id"));
                elRequest.setAttribute("SUPPORT_REFERENCE", flexicoreHashMap.getStringValue("support_reference"));
                elRequest.setAttribute("SUBJECT", flexicoreHashMap.getStringValue("subject"));
                elRequest.setAttribute("STATUS", status);
                elRequest.setAttribute("CATEGORY", flexicoreHashMap.getStringValue("category_name"));
                elRequest.setAttribute("DATE_CREATED", flexicoreHashMap.getStringValue("date_created"));

                elRequests.appendChild(elRequest);
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getSingleSupportRequest(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            String strSupportId = configXPath.evaluate("SUPPORT_ID", ndRequestMSG).trim();

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap signatoryDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("signatory_details");

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Support Requests";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            getSupportRequest(doc, elData, strSupportId);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse getAttachment(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            String strAttachmentId = configXPath.evaluate("ATTACHMENT_ID", ndRequestMSG).trim();

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap signatoryDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("signatory_details");

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Support Attachment";

            MAPPConstants.ResponsesDataType enDataType = OBJECT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            FlexicoreHashMap attachmentMap = Repository.selectWhere(StringRefs.SENTINEL, TBL_HELP_AND_SUPPORT_REQUEST_INFO_ATTACHMENTS,
                    new FilterPredicate("attachment_id = :attachment_id"),
                    new FlexicoreHashMap().addQueryArgument(":attachment_id", strAttachmentId)).getSingleRecord();

            Element elAttachment = doc.createElement("ATTACHMENT");
            elData.appendChild(elAttachment);

            String fileName = attachmentMap.getStringValue("filename");
            String temporaryFilename = attachmentMap.getStringValue("temporary_filename");

            attachmentMap.removeColumn("temporary_filename");

            String strBase64 = FileOps.readFile(MAPPAPIConstants.getAttachmentsFolder() + temporaryFilename);

            elAttachment.setTextContent(strBase64);
            elAttachment.setAttribute("FILENAME", fileName);
            elAttachment.setAttribute("ATTACHMENT_ID", strAttachmentId);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse createSupportRequestItem(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String requestType = configXPath.evaluate("REQUEST_TYPE", ndRequestMSG).trim();

            switch (requestType) {
                case "NEW_REQUEST" -> theMAPPResponse = createNewRequestInfo(theMAPPRequest);
                case "NEW_COMMENT" -> theMAPPResponse = createNewRequestComment(theMAPPRequest);
            }

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".createSupportRequestItem() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse createNewRequestInfo(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            Element elData = doc.createElement("DATA");

            String strCategoryId = configXPath.evaluate("CATEGORY_ID", ndRequestMSG).trim();
            String strSubject = configXPath.evaluate("SUBJECT", ndRequestMSG).trim();
            String strRequestInfo = configXPath.evaluate("REQUEST_INFO", ndRequestMSG).trim();

            NodeList nlAttachments = ((NodeList) configXPath.evaluate("FILES/FILE", ndRequestMSG, XPathConstants.NODESET));

            FlexicoreArrayList allAttachmentsList = new FlexicoreArrayList();

            if (nlAttachments != null) {
                int nLength = nlAttachments.getLength();

                for (int index = 0; index < nLength; index++) {
                    Node node = nlAttachments.item(index);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element element = (Element) node;

                    Element elFileName = (Element) element.getElementsByTagName("FILENAME").item(0);
                    Element elFileData = (Element) element.getElementsByTagName("DATA").item(0);
                    String strFileName = elFileName.getTextContent();

                    String fileExtension = FileOps.getFileExtension(strFileName);

                    String strContextFileName = strFileName.split("\\.")[0];

                    strContextFileName = strContextFileName + "-" + DateTime.getCurrentDateTime("yyMMddHHmmssSSS");
                    String strBase64 = elFileData.getTextContent();

                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(MAPPAPIConstants.getAttachmentsFolder() + strContextFileName + ".txt"), StandardCharsets.UTF_8))) {
                        writer.write(strBase64);
                    }

                    FlexicoreHashMap attachmentMap = new FlexicoreHashMap();
                    attachmentMap.putValue("filename", strContextFileName + "." + fileExtension);
                    attachmentMap.putValue("temporary_filename", strContextFileName + ".txt");
                    attachmentMap.putValue("date_created", DateTime.getCurrentDateTime());
                    attachmentMap.putValue("date_modified", DateTime.getCurrentDateTime());

                    allAttachmentsList.addNewRecord(attachmentMap);
                }
            }

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap signatoryDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("signatory_details");

            FlexicoreHashMap supportRequestMap = new FlexicoreHashMap();

            supportRequestMap.putValue("signatory_id", signatoryDetailsMap.getStringValue("signatory_id"));
            supportRequestMap.putValue("category_id", strCategoryId);
            supportRequestMap.putValue("subject", strSubject);
            supportRequestMap.putValue("request_channel", "MOBILE_BANKING");
            supportRequestMap.putValue("status", "UNASSIGNED");
            supportRequestMap.putValue("status_date", DateTime.getCurrentDateTime());
            supportRequestMap.putValue("support_reference", CBSAPI.getNextSupportReference());
            supportRequestMap.putValue("date_created", DateTime.getCurrentDateTime());
            supportRequestMap.putValue("date_modified", DateTime.getCurrentDateTime());

            CommutationRepository commutationRepository = new CommutationRepository(StringRefs.SENTINEL);

            TransactionWrapper<FlexicoreHashMap> supportWrapper = commutationRepository.insertAutoIncremented(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT, supportRequestMap);

            String supportId = "-1";

            if (supportWrapper.hasErrors()) {
                strTitle = "Error Occurred";
                strResponseText = "An error occurred while submitting your support request";

                enResponseAction = CON;
                enResponseStatus = ERROR;
            } else {

                supportRequestMap = supportWrapper.getSingleRecord();
                supportId = supportRequestMap.getStringValue("support_id");

                FlexicoreHashMap requestInfoMap = new FlexicoreHashMap();
                requestInfoMap.putValue("request_comments", strRequestInfo);
                requestInfoMap.putValue("user_reference_type", "CUSTOMER_SIGNATORY");
                requestInfoMap.putValue("user_reference_value", signatoryDetailsMap.getStringValue("signatory_id"));
                requestInfoMap.putValue("support_id", supportId);
                requestInfoMap.putValue("date_created", DateTime.getCurrentDateTime());
                requestInfoMap.putValue("date_modified", DateTime.getCurrentDateTime());

                requestInfoMap = commutationRepository.insertAutoIncremented(StringRefs.SENTINEL,
                        TBL_HELP_AND_SUPPORT_REQUEST_INFO, requestInfoMap).getSingleRecord();

                String requestInfoId = requestInfoMap.getStringValue("request_info_id");

                for (FlexicoreHashMap attachmentMap : allAttachmentsList) {
                    attachmentMap.putValue("request_info_id", requestInfoId);

                    commutationRepository.insertAutoIncremented(StringRefs.SENTINEL,
                            TBL_HELP_AND_SUPPORT_REQUEST_INFO_ATTACHMENTS, attachmentMap);
                }

                commutationRepository.update(StringRefs.SENTINEL,
                        TBL_HELP_AND_SUPPORT,
                        new FlexicoreHashMap()
                                .putValue("last_request_info_id", requestInfoId)
                                .putValue("date_modified", DateTime.getCurrentDateTime())
                        ,
                        new FilterPredicate("support_id = :support_id"),
                        new FlexicoreHashMap().addQueryArgument(":support_id", supportId));


                strTitle = "Success";
                strResponseText = "Request Created Successfully";

                enResponseAction = CON;
                enResponseStatus = SUCCESS;

            }

            commutationRepository.commit();

            if (enResponseStatus == SUCCESS) {

                FlexicoreHashMap finalSupportRequestMap = supportRequestMap;
                new Thread(() -> {
                    try {

                        FlexicoreHashMap categoryMap = Repository.selectWhere(StringRefs.SENTINEL,
                                TBL_HELP_AND_SUPPORT_CATEGORIES,
                                new FilterPredicate("category_id = :category_id"),
                                new FlexicoreHashMap().addQueryArgument(":category_id", strCategoryId)).getSingleRecord();

                        FlexicoreHashMap userIdFilterPredicateAndQuery = FlexicorePermissionsReader.getUsersForApplication(StringRefs.SENTINEL,
                                SystemApplicationCodes.APP_CODE_HELP_AND_SUPPORT_ASSIGN_USERS, 0);

                        FilterPredicate filterPredicate = (FilterPredicate) userIdFilterPredicateAndQuery.get("filterPredicate");
                        FlexicoreHashMap queryArguments = (FlexicoreHashMap) userIdFilterPredicateAndQuery.get("queryArguments");

                        if (queryArguments.isEmpty()) {
                            filterPredicate = new FilterPredicate("user_id IS NULL AND account_type != 'VENDOR'");
                        }

                        filterPredicate.and().notEqualTo("account_type", ":account_type");
                        queryArguments.addQueryArgument(":account_type", "VENDOR");

                        TransactionWrapper<FlexicoreArrayList> wrapper = Repository.selectWhere(StringRefs.SENTINEL, TBL_USER_ACCOUNTS, """
                                        user_id,
                                        username,
                                        surname,
                                        first_name,
                                        other_names,
                                        identification_type,
                                        identification,
                                        primary_phone_number,
                                        primary_email_address,
                                        staff_number,
                                        account_status,
                                        branch_code,
                                        branch""",
                                filterPredicate, queryArguments);

                        if (!wrapper.hasErrors()) {
                            FlexicoreArrayList allUsersList = wrapper.getData();

                            if (allUsersList != null) {

                                for (FlexicoreHashMap userMap : allUsersList) {
                                    String recipientEmailAddress = userMap.getStringValue("primary_email_address");

                                    String strEmail = EmailTemplates.helpAndSupportNewRequestTemplate();
                                    strEmail = strEmail.replace("[FULL_NAME]", userMap.getStringValue("first_name"));
                                    strEmail = strEmail.replace("[MEMBER_FULL_NAME]", signatoryDetailsMap.getStringValue("full_name"));
                                    strEmail = strEmail.replace("[DATE_CREATED]",
                                            DateTime.convertStringToDateToString(finalSupportRequestMap.getStringValue("date_created"),
                                                    DateTime.DEFAULT_DATE_TIME_FORMAT, "dd-MMM-yyyy HH:mm"));

                                    strEmail = strEmail.replace("[SUPPORT_REFERENCE]", finalSupportRequestMap.getStringValue("support_reference"));

                                    strEmail = strEmail.replace("[REQUEST_CATEGORY]", categoryMap.getStringValue("category_name"));
                                    strEmail = strEmail.replace("[SUBJECT]", finalSupportRequestMap.getStringValue("subject"));
                                    strEmail = strEmail.replace("[REQUEST_INFO]", strRequestInfo);

                                    EmailMessaging.sendEmail(recipientEmailAddress, "New Help And Support Request by " + signatoryDetailsMap.getStringValue("full_name"), strEmail, "HELP_AND_SUPPORT");
                                }
                            }
                        }

                    } catch (Exception e) {

                    }

                }).start();

            }

            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".createNewRequestInfo() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse createNewRequestComment(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            Element elData = doc.createElement("DATA");

            String strSupportId = configXPath.evaluate("SUPPORT_ID", ndRequestMSG).trim();
            String strRequestInfo = configXPath.evaluate("REQUEST_INFO", ndRequestMSG).trim();

            NodeList nlAttachments = ((NodeList) configXPath.evaluate("FILES/FILE", ndRequestMSG, XPathConstants.NODESET));

            FlexicoreArrayList allAttachmentsList = new FlexicoreArrayList();

            if (nlAttachments != null) {
                int nLength = nlAttachments.getLength();

                for (int index = 0; index < nLength; index++) {
                    Node node = nlAttachments.item(index);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element element = (Element) node;

                    Element elFileName = (Element) element.getElementsByTagName("FILENAME").item(0);
                    Element elFileData = (Element) element.getElementsByTagName("DATA").item(0);
                    String strFileName = elFileName.getTextContent();
                    String fileExtension = FileOps.getFileExtension(strFileName);

                    String strContextFileName = strFileName.split("\\.")[0];

                    strContextFileName = strContextFileName + "-" + DateTime.getCurrentDateTime("yyMMddHHmmssSSS");
                    String strBase64 = elFileData.getTextContent();

                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(MAPPAPIConstants.getAttachmentsFolder() + strContextFileName + ".txt"), StandardCharsets.UTF_8))) {
                        writer.write(strBase64);
                    }

                    FlexicoreHashMap attachmentMap = new FlexicoreHashMap();
                    attachmentMap.putValue("filename", strContextFileName + "." + fileExtension);
                    attachmentMap.putValue("temporary_filename", strContextFileName + ".txt");
                    attachmentMap.putValue("date_created", DateTime.getCurrentDateTime());
                    attachmentMap.putValue("date_modified", DateTime.getCurrentDateTime());

                    allAttachmentsList.addNewRecord(attachmentMap);
                }
            }

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap signatoryDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("signatory_details");

            CommutationRepository commutationRepository = new CommutationRepository(StringRefs.SENTINEL);

            //String supportId = "-1";

            QueryBuilder queryBuilderSupport = new QueryBuilder()
                    .select()
                    .selectColumn("has.*, " +
                            "hasc.category_code," +
                            " hasc.category_name"
                    )
                    .from()
                    .joinPhrase(TBL_HELP_AND_SUPPORT + " has\n" +
                            "         LEFT JOIN " + TBL_HELP_AND_SUPPORT_CATEGORIES + " hasc ON has.category_id = hasc.category_id")
                    .where("has.support_id = :support_id");


            FlexicoreHashMap supportRequestMap = Repository.joinSelectQuery(StringRefs.SENTINEL,
                    queryBuilderSupport,
                    new FlexicoreHashMap().addQueryArgument(":support_id", strSupportId)).getSingleRecord();

            FlexicoreHashMap requestInfoMap = new FlexicoreHashMap();
            requestInfoMap.putValue("request_comments", strRequestInfo);
            requestInfoMap.putValue("user_reference_type", "CUSTOMER_SIGNATORY");
            requestInfoMap.putValue("user_reference_value", signatoryDetailsMap.getStringValue("signatory_id"));
            requestInfoMap.putValue("support_id", strSupportId);
            requestInfoMap.putValue("date_created", DateTime.getCurrentDateTime());
            requestInfoMap.putValue("date_modified", DateTime.getCurrentDateTime());

            requestInfoMap = commutationRepository.insertAutoIncremented(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT_REQUEST_INFO, requestInfoMap).getSingleRecord();

            String requestInfoId = requestInfoMap.getStringValue("request_info_id");

            for (FlexicoreHashMap attachmentMap : allAttachmentsList) {
                attachmentMap.putValue("request_info_id", requestInfoId);

                commutationRepository.insertAutoIncremented(StringRefs.SENTINEL, TBL_HELP_AND_SUPPORT_REQUEST_INFO_ATTACHMENTS, attachmentMap);
            }

            strTitle = "Success";
            strResponseText = "Comment Submitted Successfully";

            commutationRepository.update(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT,
                    new FlexicoreHashMap()
                            .putValue("last_request_info_id", requestInfoId)
                            .putValue("date_modified", DateTime.getCurrentDateTime())
                    ,
                    new FilterPredicate("support_id = :support_id"),
                    new FlexicoreHashMap().addQueryArgument(":support_id", strSupportId));


            commutationRepository.commit();

            FlexicoreHashMap finalRequestInfoMap = requestInfoMap;
            new Thread(() -> {
                try {

                    FlexicoreHashMap userIdFilterPredicateAndQuery = FlexicorePermissionsReader.getUsersForApplication(StringRefs.SENTINEL,
                            SystemApplicationCodes.APP_CODE_HELP_AND_SUPPORT_ASSIGN_USERS, 0);

                    FilterPredicate filterPredicate = (FilterPredicate) userIdFilterPredicateAndQuery.get("filterPredicate");
                    FlexicoreHashMap queryArguments = (FlexicoreHashMap) userIdFilterPredicateAndQuery.get("queryArguments");

                    if (queryArguments.isEmpty()) {
                        filterPredicate = new FilterPredicate("user_id IS NULL AND account_type != 'VENDOR'");
                    }

                    filterPredicate.and().notEqualTo("account_type", ":account_type");
                    queryArguments.addQueryArgument(":account_type", "VENDOR");

                    TransactionWrapper<FlexicoreArrayList> wrapper = Repository.selectWhere(StringRefs.SENTINEL, TBL_USER_ACCOUNTS, """
                                    user_id,
                                    username,
                                    surname,
                                    first_name,
                                    other_names,
                                    identification_type,
                                    identification,
                                    primary_phone_number,
                                    primary_email_address,
                                    staff_number,
                                    account_status,
                                    branch_code,
                                    branch""",
                            filterPredicate, queryArguments);

                    QueryBuilder queryBuilderAssignedUsers = new QueryBuilder()
                            .select()
                            .selectColumn("""
                                    ua.user_id,
                                    ua.username,
                                    ua.surname,
                                    ua.first_name,
                                    ua.other_names,
                                    ua.identification_type,
                                    ua.identification,
                                    ua.primary_phone_number,
                                    ua.primary_email_address,
                                    ua.staff_number,
                                    ua.account_status,
                                    ua.branch_code,
                                    ua.branch""")
                            .from()
                            .joinPhrase(TBL_HELP_AND_SUPPORT_ASSIGNEES + " hasa\n" +
                                    "         LEFT JOIN " + TBL_USER_ACCOUNTS + " ua ON hasa.user_id = ua.user_id")
                            .where("hasa.support_id = :support_id");

                    FlexicoreArrayList assignedUsersList = (FlexicoreArrayList) Repository.joinSelectQuery(StringRefs.SENTINEL, queryBuilderAssignedUsers,
                            new FlexicoreHashMap()
                                    .addQueryArgument(":support_id", strSupportId)).displayQueriesExecuted().getData();

                    if (assignedUsersList == null) {
                        assignedUsersList = new FlexicoreArrayList();
                    }

                    List<String> sentEmailAddressesList = new ArrayList<>();

                    if (!wrapper.hasErrors()) {
                        FlexicoreArrayList allUsersList = wrapper.getData();

                        if (allUsersList == null) {
                            allUsersList = new FlexicoreArrayList();
                        }

                        for (FlexicoreHashMap userMap : allUsersList) {
                            String recipientEmailAddress = userMap.getStringValue("primary_email_address");

                            String strEmail = EmailTemplates.helpAndSupportNewCommentsTemplate();
                            strEmail = strEmail.replace("[FULL_NAME]", userMap.getStringValue("first_name"));
                            strEmail = strEmail.replace("[MEMBER_FULL_NAME]", signatoryDetailsMap.getStringValue("full_name"));
                            strEmail = strEmail.replace("[SUPPORT_REFERENCE]", supportRequestMap.getStringValue("support_reference"));
                            strEmail = strEmail.replace("[DATE_CREATED]",
                                    DateTime.convertStringToDateToString(finalRequestInfoMap.getStringValue("date_created"),
                                            DateTime.DEFAULT_DATE_TIME_FORMAT, "dd-MMM-yyyy HH:mm"));

                            strEmail = strEmail.replace("[REQUEST_CATEGORY]", supportRequestMap.getStringValue("category_name"));
                            strEmail = strEmail.replace("[SUBJECT]", supportRequestMap.getStringValue("subject"));
                            strEmail = strEmail.replace("[REQUEST_INFO]", strRequestInfo);

                            EmailMessaging.sendEmail(recipientEmailAddress, "Support Request #" + supportRequestMap.getStringValue("support_reference") + " New Comments", strEmail, "HELP_AND_SUPPORT");

                            sentEmailAddressesList.add(recipientEmailAddress);
                        }

                        for (FlexicoreHashMap userMap : assignedUsersList) {
                            String recipientEmailAddress = userMap.getStringValue("primary_email_address");

                            if (sentEmailAddressesList.contains(recipientEmailAddress)) {
                                continue;
                            }

                            String strEmail = EmailTemplates.helpAndSupportNewCommentsTemplate();
                            strEmail = strEmail.replace("[FULL_NAME]", userMap.getStringValue("first_name"));
                            strEmail = strEmail.replace("[MEMBER_FULL_NAME]", signatoryDetailsMap.getStringValue("full_name"));
                            strEmail = strEmail.replace("[SUPPORT_REFERENCE]", supportRequestMap.getStringValue("support_reference"));
                            strEmail = strEmail.replace("[DATE_CREATED]",
                                    DateTime.convertStringToDateToString(supportRequestMap.getStringValue("date_created"),
                                            DateTime.DEFAULT_DATE_TIME_FORMAT, "dd-MMM-yyyy HH:mm"));

                            strEmail = strEmail.replace("[REQUEST_CATEGORY]", supportRequestMap.getStringValue("category_name"));
                            strEmail = strEmail.replace("[SUBJECT]", supportRequestMap.getStringValue("subject"));
                            strEmail = strEmail.replace("[REQUEST_INFO]", strRequestInfo);

                            EmailMessaging.sendEmail(recipientEmailAddress, "Support Request #" + supportRequestMap.getStringValue("support_reference") + " New Comments", strEmail, "HELP_AND_SUPPORT");
                            sentEmailAddressesList.add(recipientEmailAddress);
                        }
                    }

                } catch (Exception e) {

                }

            }).start();


            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".createNewRequestInfo() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse closeSupportRequest(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            Element elData = doc.createElement("DATA");

            String strSupportId = configXPath.evaluate("SUPPORT_ID", ndRequestMSG).trim();

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = CBSAPI.getCurrentUserDetails(UUID.randomUUID().toString(), "MSISDN", strUsername, "APP_ID", strAppID);
            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();

            FlexicoreHashMap signatoryDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("signatory_details");

            FlexicoreHashMap flexicoreHashMap = CBSAPI.closeSupportRequest(signatoryDetailsMap.getStringValue("signatory_id"), strSupportId);

            if (flexicoreHashMap.isEmpty()) {
                strTitle = "Error Occurred";
                strResponseText = "Error occurred while closing request.";
            } else {
                strTitle = "Success";
                strResponseText = "Request Closed Successfully";
            }

            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".closeSupportRequest() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    private void getSupportRequest(Document doc, Element elData, String strSupportId) {
        QueryBuilder queryBuilderSupport = new QueryBuilder()
                .select()
                .selectColumn("has.*, hasc.category_code, hasc.category_name")
                .from()
                .joinPhrase(TBL_HELP_AND_SUPPORT + " has\n" +
                        "         LEFT JOIN " + TBL_HELP_AND_SUPPORT_CATEGORIES + " hasc ON has.category_id = hasc.category_id")
                .where("has.support_id = :support_id");

        FlexicoreHashMap supportMap = Repository.joinSelectQuery(StringRefs.SENTINEL, queryBuilderSupport,
                new FlexicoreHashMap()
                        .addQueryArgument(":support_id", strSupportId)).getSingleRecord();

        String strSignatoryId = supportMap.getStringValue("signatory_id");

        FlexicoreHashMap signatoryDetails = Repository.selectWhere(StringRefs.SENTINEL, TBL_CUSTOMER_REGISTER_SIGNATORIES,
                new FilterPredicate("signatory_id = :signatory_id"),
                new FlexicoreHashMap().addQueryArgument(":signatory_id", strSignatoryId)).getSingleRecord();

        supportMap.putValue("signatory_details", signatoryDetails);

        String closedByUserId = supportMap.getStringValue("closed_by_user_id");

        if (closedByUserId != null) {
            FlexicoreHashMap closedByUserAccountDetails = Repository.selectWhere(StringRefs.SENTINEL, TBL_USER_ACCOUNTS,
                    """
                            user_id,
                            username,
                            surname,
                            first_name,
                            other_names,
                            identification_type,
                            identification,
                            authentication_profile_code,
                            primary_phone_number,
                            primary_email_address,
                            staff_number,
                            account_status,
                            branch_code,
                            branch""",
                    new FilterPredicate("user_id = :user_id"),
                    new FlexicoreHashMap().addQueryArgument(":user_id", closedByUserId)).getSingleRecord();

            supportMap.putValue("closed_by_user_details", closedByUserAccountDetails);
        } else {
            supportMap.putValue("closed_by_user_details", null);
        }

        Element elSupportId = doc.createElement("SUPPORT_ID");
        elSupportId.setTextContent(supportMap.getStringValue("support_id"));
        elData.appendChild(elSupportId);

        Element elSupportReference = doc.createElement("SUPPORT_REFERENCE");
        elSupportReference.setTextContent(supportMap.getStringValue("support_reference"));
        elData.appendChild(elSupportReference);

        Element elSubject = doc.createElement("SUBJECT");
        elSubject.setTextContent(supportMap.getStringValue("subject"));
        elData.appendChild(elSubject);

        Element elStatus = doc.createElement("STATUS");

        String status = supportMap.getStringValue("status");
        switch (status) {
            case "OPEN", "UNASSIGNED", "PENDING" -> status = "OPEN";
        }
        elStatus.setTextContent(status);
        elData.appendChild(elStatus);

        Element elCategory = doc.createElement("CATEGORY");
        elCategory.setTextContent(supportMap.getStringValue("category_name"));
        elCategory.setAttribute("ID", supportMap.getStringValue("category_id"));
        elData.appendChild(elCategory);

        Element elDateCreated = doc.createElement("DATE_CREATED");
        elDateCreated.setTextContent(supportMap.getStringValue("date_created"));
        elData.appendChild(elDateCreated);

        Element elRequestInfos = doc.createElement("REQUEST_INFOS");
        elData.appendChild(elRequestInfos);

        TransactionWrapper<FlexicoreArrayList> wrapperRequestInfo = Repository.selectWhereOrderBy(StringRefs.SENTINEL,
                TBL_HELP_AND_SUPPORT_REQUEST_INFO,
                new FilterPredicate("support_id = :support_id"),
                "request_info_id ASC",
                new FlexicoreHashMap().addQueryArgument(":support_id", strSupportId));

        FlexicoreArrayList requestInfoList = wrapperRequestInfo.getData();

        List<String> allRequestInfoIds = new ArrayList<>();

        for (FlexicoreHashMap requestInfoMap : requestInfoList) {

            Element elRequestInfo = doc.createElement("REQUEST_INFO");
            elRequestInfos.appendChild(elRequestInfo);

            String userReferenceType = requestInfoMap.getStringValue("user_reference_type");
            String userReferenceValue = requestInfoMap.getStringValue("user_reference_value");

            switch (userReferenceType) {
                case "CUSTOMER_SIGNATORY" -> {
                    FlexicoreHashMap commenterDetails = Repository.selectWhere(StringRefs.SENTINEL, TBL_CUSTOMER_REGISTER_SIGNATORIES,
                            new FilterPredicate("signatory_id = :signatory_id"),
                            new FlexicoreHashMap().addQueryArgument(":signatory_id", userReferenceValue)).getSingleRecord();

                    elRequestInfo.setAttribute("COMMENTER", "MEMBER");
                    elRequestInfo.setAttribute("FIRST_NAME", "You");
                }

                case "USER_ACCOUNT" -> {
                    FlexicoreHashMap commenterDetails = Repository.selectWhere(StringRefs.SENTINEL, TBL_USER_ACCOUNTS,
                            """
                                    user_id,
                                    username,
                                    surname,
                                    first_name,
                                    other_names,
                                    identification_type,
                                    identification,
                                    authentication_profile_code,
                                    primary_phone_number,
                                    primary_email_address,
                                    staff_number,
                                    account_status,
                                    branch_code,
                                    branch""",
                            new FilterPredicate("user_id = :user_id"),
                            new FlexicoreHashMap().addQueryArgument(":user_id", userReferenceValue)).getSingleRecord();

                    elRequestInfo.setAttribute("COMMENTER", "STAFF");
                    elRequestInfo.setAttribute("FIRST_NAME", commenterDetails.getStringValue("first_name"));
                }

                default -> new FlexicoreHashMap();
            }

            String strRequestInfoId = requestInfoMap.getStringValue("request_info_id");

            allRequestInfoIds.add(strRequestInfoId);

            elRequestInfo.setAttribute("REQUEST_INFO_ID", strRequestInfoId);
            elRequestInfo.setAttribute("DATE_CREATED", requestInfoMap.getStringValue("date_created"));

            Element elRequestInfo_Info = doc.createElement("INFO");
            elRequestInfo.appendChild(elRequestInfo_Info);

            elRequestInfo_Info.setTextContent(requestInfoMap.getStringValue("request_comments"));

            Element elAttachmentsList = doc.createElement("ATTACHMENTS");
            elRequestInfo.appendChild(elAttachmentsList);

            FlexicoreArrayList allAttachmentsList = (FlexicoreArrayList) Repository.selectWhere(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT_REQUEST_INFO_ATTACHMENTS,
                    new FilterPredicate("request_info_id = :request_info_id"),
                    new FlexicoreHashMap().addQueryArgument(":request_info_id", strRequestInfoId)).getData();

            if (allAttachmentsList != null && !allAttachmentsList.isEmpty()) {
                for (FlexicoreHashMap attachmentMap : allAttachmentsList) {
                    String fileName = attachmentMap.getStringValue("filename");
                    String temporaryFilename = attachmentMap.getStringValue("temporary_filename");

                    Element elAttachment = doc.createElement("ATTACHMENT");
                    elAttachmentsList.appendChild(elAttachment);

                    elAttachment.setAttribute("FILENAME", fileName);
                    elAttachment.setAttribute("ATTACHMENT_ID", attachmentMap.getStringValue("attachment_id"));
                }
            }
        }

        for (String requestInfoId : allRequestInfoIds) {
            if (!Repository.exists(StringRefs.SENTINEL, TBL_HELP_AND_SUPPORT_READ_RECEIPTS,
                    new FilterPredicate("request_info_id = :request_info_id AND " +
                            " user_reference_type = :user_reference_type AND user_reference_value = :user_reference_value"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":request_info_id", requestInfoId)
                            .addQueryArgument(":user_reference_type", "CUSTOMER_SIGNATORY")
                            .addQueryArgument(":user_reference_value", strSignatoryId)
            )) {

                Repository.insertAutoIncremented(StringRefs.SENTINEL,
                        TBL_HELP_AND_SUPPORT_READ_RECEIPTS,
                        new FlexicoreHashMap()
                                .putValue("request_info_id", requestInfoId)
                                .putValue("user_reference_type", "CUSTOMER_SIGNATORY")
                                .putValue("user_reference_value", strSignatoryId)
                                .putValue("date_created", DateTime.getCurrentDateTime())
                                .putValue("date_modified", DateTime.getCurrentDateTime())
                );
            }
        }
    }

    public MAPPResponse getOtherServices(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Other Services";

            MAPPConstants.ResponsesDataType enDataType = LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");


            String strSettingsXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strSettingsXML);

            NodeList nlChannels = XmlUtils.getNodesFromXpath(document, "/MBANKING_SETTINGS/OTHER_SERVICES/SERVICE");

            Element elChannels = doc.createElement("SERVICES");
            elData.appendChild(elChannels);

            if (nlChannels != null) {
                int nLength = nlChannels.getLength();

                for (int i = 0; i < nLength; i++) {
                    Node node = nlChannels.item(i);
                    if (node.getNodeType() != Node.ELEMENT_NODE) continue;

                    Element element = (Element) node;

                    String strLabel = element.getAttribute("LABEL");
                    String strURL = element.getAttribute("URL");

                    Element elChannel = doc.createElement("SERVICE");
                    elChannels.appendChild(elChannel);

                    elChannel.setAttribute("LABEL", strLabel);
                    elChannel.setAttribute("URL", strURL);

                }
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

            System.out.println("\n\nGET_OTHER_SERVICES:\n THE RESPONSE: " + XmlUtils.convertNodeToStr(ndResponseMSG) + " \n\n");

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }


  /*  public MAPPResponse validateOTP(MAPPRequest theMAPPRequest, MAPPAPIConstants.OTP_TYPE theOTPType) {

        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            boolean blAddDataAction = false;

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            String strActivationCode = configXPath.evaluate("OTP", ndRequestMSG).trim();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "Error";
            String strDescription = "An error occurred. Please try again after a few minutes.";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            String strStartKey = "";
            strStartKey = (String) InMemoryCache.retrieve(strUsername + strActivationCode);

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.ERROR;

            Element elData = doc.createElement("DATA");


            //Get OTP details from DB
            HashMap<String, String> hmAuthSecurityRVal = CBSAPI.getAuthSecurityParameters(getTraceID(theMAPPRequest), "MSISDN",
                    strUsername, strPassword, "APP_ID", strAppID, "OTP");

            if (!hmAuthSecurityRVal.isEmpty() && hmAuthSecurityRVal.get("request_status").equals("SUCCESS")) {
                String dbOTPFlag = hmAuthSecurityRVal.get("auth_flag");
                String strUserLoginAttemptAction = hmAuthSecurityRVal.get("auth_action");
                String strDBOTPAttempts = hmAuthSecurityRVal.get("auth_attempts");
                int dbOTPAttempts = Integer.parseInt(strDBOTPAttempts);
                String strDbOTPActionValidDate = hmAuthSecurityRVal.get("auth_action_valid_date");
                Date dbOTPActionValidDate = null;
                if(strDbOTPActionValidDate != null && !strDbOTPActionValidDate.isEmpty()){
                    APIUtils.convertDateStringToDate(strDbOTPActionValidDate);
                }

                //Increase otp attempts
                int otpAttempts = dbOTPAttempts + 1;

                boolean blIncorrectOTP = false;

                if (strUserLoginAttemptAction.equalsIgnoreCase("SUSPEND")) {
                    strTitle = "OTP Validation Suspended";
                */
    /*String strTryAgainIn = "Please try again in " + APIUtils.millisToLongDHMS(dblDuration);

                strDescription = "Sorry, your account is SUSPENDED from validating one time password. " + strTryAgainIn;*//*
                    strDescription = "Sorry, your account has been SUSPENDED from validating one time password.";
                    enResponseAction = MAPPConstants.ResponseAction.END;
                    enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
                } else {
                    if (strActivationCode.equalsIgnoreCase(strStartKey)) {
                        String strUserAccountStatus;
                        if (theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) {
                            HashMap<String, String> hmActivateMAPP = CBSAPI.activateMobileApp(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword, strAppID);

                            if (!hmActivateMAPP.isEmpty()) {
                                strUserAccountStatus = hmActivateMAPP.get("mobile_app_activation_status");
                            } else {
                                strUserAccountStatus = "ERROR";
                            }
                        } else {
                            strUserAccountStatus = "SUCCESS";
                        }

                        switch (strUserAccountStatus) {
                            case "SUCCESS": {
                                strTitle = "Activation Successful";
                                strDescription = "Mobile app account activation was successful";
                                if (theOTPType == MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL) {
                                    strTitle = "OTP Validation Successful";
                                    strDescription = "Your OTP validation was successful";
                                }
                                enResponseAction = CON;
                                enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                                //Reset OTP details in Database
                                HashMap<String,String> hmRValAuth = CBSAPI.setAuthSecurityParameters(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword,"APP_ID", strAppID,
                                        "OTP",  "NONE", null, null, APIUtils.getCurrentDateTime());
                                InMemoryCache.remove(strUsername);
                                InMemoryCache.remove(strUsername + strActivationCode);
                                break;
                            }
                            case "BLOCKED": {
                                strTitle = "Account Blocked";
                                strDescription = "Your account is blocked, please visit you nearest SACCO branch for assistance.";
                                break;
                            }
                            case "NOT_FOUND": {
                                strTitle = "Account Not Found";
                                strDescription = "An error occurred. Please try again after a few minutes.";
                                break;
                            }
                            default: {
                                strTitle = "Activation Failed";
                                if (theOTPType == MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL) {
                                    strTitle = "OTP Validation Failed";
                                }
                                strDescription = "An error occurred. Please try again after a few minutes.";
                                break;
                            }
                        }
                    } else {
                        //Set OTP Attempts
                        CBSAPI.setAuthSecurityParameters(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword,"APP_ID", strAppID,
                                "OTP", otpAttempts,  "NONE", null, null, APIUtils.getCurrentDateTime());

                        strTitle = "Incorrect Activation Code";
                        strDescription = "The activation code you entered is either incorrect or has expired. Please confirm the activation code and try again.";

                        if (theOTPType == MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL) {
                            strTitle = "Incorrect One Time Password";
                            strDescription = "You entered an incorrect/expired One Time Password";
                        }

                        HashMap<String, String> hmMSGPlaceholders = new HashMap<>();
                        hmMSGPlaceholders.put("[MOBILE_NUMBER]", strUsername);
                        hmMSGPlaceholders.put("[OTP_ATTEMPTS]", String.valueOf(otpAttempts));
                        hmMSGPlaceholders.put("[FIRST_NAME]", getUserFullName(theMAPPRequest, strUsername));

                        String xml = MAPPLocalParameters.getClientXMLParameters();
                        HashMap<String, HashMap<String, String>> authenticationAttemptsAction = MBankingXMLFactory.getAuthenticationAttemptsAction(otpAttempts,
                                hmMSGPlaceholders, xml, MBankingConstants.AuthType.OTP);

                        HashMap<String, String> currentAuthenticationAttemptsAction = authenticationAttemptsAction.get("CURRENT_ATTEMPT");
                        HashMap<String, String> futureAuthenticationAttemptsAction = authenticationAttemptsAction.get("NEXT_ATTEMPT");

                        String endSession = "NO";

                        if (!currentAuthenticationAttemptsAction.isEmpty()) {

                            String resetOTP = currentAuthenticationAttemptsAction.get("RESET_OTP");
                            String otpAction = currentAuthenticationAttemptsAction.get("ACTION");
                            String otpActionTag = currentAuthenticationAttemptsAction.get("NAME");

                            //Check action
                            switch (otpAction) {
                                case "SUSPEND": {
                                    enResponseAction = MAPPConstants.ResponseAction.END;
                                    int otpActionDuration = Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION"));
                                    String otpActionDurationUnit = currentAuthenticationAttemptsAction.get("UNIT");
                                    otpActionDuration = APIUtils.convertToSeconds(otpActionDuration, otpActionDurationUnit);
                                    Date otpActionValidDate = APIUtils.add(otpActionDuration, Calendar.SECOND);
                                    String strOTPActionValidDate = APIUtils.convertDateToDateString(otpActionValidDate);

                                    if (resetOTP.equals("YES")) {
                                        //remove OTP
                                        InMemoryCache.remove(strUsername);
                                    }

                                    //Persist Action to DB
                                    String friendlyActionDuration = currentAuthenticationAttemptsAction.get("DURATION") + " " + otpActionDurationUnit + "(S)";
                                    CBSAPI.setAuthSecurityParameters(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword,"APP_ID", strAppID,
                                            "OTP", otpAttempts,  otpAction, strOTPActionValidDate, otpActionTag, APIUtils.getCurrentDateTime());

                                    //Override Incorrect PIN message
                                    strTitle = "Account Suspended";
                                    String strTryAgainIn = "Please try again in " + friendlyActionDuration;
                                    strDescription = "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " mobile banking services. " + strTryAgainIn;
                                    endSession = "YES";
                                    break;
                                }

                                case "LOCK": {
                                    enResponseAction = MAPPConstants.ResponseAction.END;
                                    if (resetOTP.equals("YES")) {
                                        //remove OTP
                                        InMemoryCache.remove(strUsername);
                                    }

                                    //Persist Action to DB
                                    CBSAPI.setAuthSecurityParameters(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword,"APP_ID", strAppID,
                                            "OTP", otpAttempts,  otpAction, null, otpActionTag, APIUtils.getCurrentDateTime());

                                    //Override Incorrect PIN message
                                    strTitle = "Account Locked";
                                    strDescription = "Your mobile banking account has been LOCKED. Please visit one of our branches for assistance or contact us.";
                                    endSession = "YES";
                                    break;
                                }

                                default: {
                                    //Persist Action to DB
                                    CBSAPI.setAuthSecurityParameters(getTraceID(theMAPPRequest), "MSISDN", strUsername, strPassword,"APP_ID", strAppID,
                                            "OTP", otpAttempts,  otpAction, null, otpActionTag, APIUtils.getCurrentDateTime());

                                    if (resetOTP.equals("YES")) {
                                        elData.setAttribute("ACTION", "REQUEST_OTP");
                                    }
                                }
                            }
                        }

                        //Check future action
                        if (!futureAuthenticationAttemptsAction.isEmpty()) {
                            String futureOTPAction = futureAuthenticationAttemptsAction.get("ACTION");
                            String futureOTPActionDurationUnit = futureAuthenticationAttemptsAction.get("UNIT");
                            String friendlyFutureActionDuration = futureAuthenticationAttemptsAction.get("DURATION") + " " + futureOTPActionDurationUnit + "(S)";
                            String attemptsRemainingToFutureOTPAction = futureAuthenticationAttemptsAction.get("ATTEMPTS_REMAINING");

                            String currentOTPAction = currentAuthenticationAttemptsAction.get("ACTION");
                            if (currentOTPAction == null) currentOTPAction = "NONE";
                            String resetOTP = currentAuthenticationAttemptsAction.get("RESET_OTP");

                            //Override Incorrect PIN message
                            if (futureOTPAction.equals("SUSPEND") && !currentOTPAction.equals("SUSPEND")) {
                                strTitle = ((theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) ? "Incorrect Activation Code" : "Incorrect One Time Password");

                                if (endSession.equals("NO")) {
                                    strDescription = "You have " + attemptsRemainingToFutureOTPAction + " attempt(s) before your mobile banking account is SUSPENDED for " + friendlyFutureActionDuration + ".";
                                }
                            } else if (futureOTPAction.equals("LOCK") && !currentOTPAction.equals("LOCK")) {
                                strTitle = ((theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) ? "Incorrect Activation Code" : "Incorrect One Time Password");

                                if (endSession.equals("NO")) {
                                    strDescription = "You have " + attemptsRemainingToFutureOTPAction + " attempt(s) before your mobile banking account is LOCKED.";
                                }
                            }
                        }
                    }
                }
            }

            String strCharge = "NO";
            elData.setTextContent(strDescription);

            if (blAddDataAction) {
                elData.setAttribute("ACTION", "REQUEST_OTP");
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }
*/

    /*public MAPPResponse generateOTP(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strSessionID = String.valueOf(lnSessionID);
            String strTraceID = getTraceID(theMAPPRequest);

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.GENERATION);

            int intOTPTTL = 0;
            int intOTPLength = 0;
            String strOTPID = "";

            if (otp.isEnabled()) {
                intOTPTTL = otp.getTtl();
                intOTPLength = otp.getLength();
                strOTPID = otp.getId();
            }

            String strAppSignature = configXPath.evaluate("APP_SIGNATURE", ndRequestMSG).trim();
            if (strAppSignature == null) {
                strAppSignature = "";
            }

            String strOneTImePIN = Utils.generateRandomString(intOTPLength);

            InMemoryCache.remove(strUsername + strOneTImePIN);
            InMemoryCache.store(strUsername + strOneTImePIN, strOneTImePIN, intOTPTTL);


            SimpleDateFormat sdSimpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            Timestamp tsCurrentTimestamp = new Timestamp(System.currentTimeMillis());
            Timestamp tsCurrentTimestampPlusTime = new Timestamp(System.currentTimeMillis() + (intOTPTTL * 1000));

            String strTimeGenerated = sdSimpleDateFormat.format(tsCurrentTimestamp);
            String strExpiryDate = sdSimpleDateFormat.format(tsCurrentTimestampPlusTime);


            String strMSG = "Dear Member,\n" + strOneTImePIN + " is your One Time Password(OTP) generated at " + strTimeGenerated + ". This OTP is valid up to " + strExpiryDate + ".\n" + strOTPID + (!strAppSignature.equals("") ? ("\n" + strAppSignature) : "");

            String strCharge = "YES";

            //TODO: CHANGE TO EXPRESS SMS ON LIVE
            int intMSGSent = fnSendSMS(strUsername, strMSG, "YES", MSGConstants.MSGMode.SAF, 200, "ONE_TIME_PASSWORD", "MAPP", "MBANKING_SERVER", strSessionID, strTraceID);
          //  int intMSGSent = fnSendSMS(strUsername, strMSG, "YES", MSGConstants.MSGMode.EXPRESS, 200, "ONE_TIME_PASSWORD", "MAPP", "MBANKING_SERVER", strSessionID, strTraceID);

            String strTitle = "OTP Generated and Sent Successfully";
            String strResponseText = "Your One Time Password was generated and sent successfully.";

            if (intMSGSent <= 0) {
                strTitle = "OTP Generation Failed";
                strResponseText = "There was an error sending your One Time Password. Please try again";
                strCharge = "NO";
                enResponseAction = CON;
                enResponseStatus = MAPPConstants.ResponseStatus.ERROR;
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }
*/
    public MAPPResponse generateOTP(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            long lnSessionID = theMAPPRequest.getSessionID();

            String strSessionID = String.valueOf(lnSessionID);
            String strTraceID = theMAPPRequest.getTraceID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            APIUtils.OTP otp = checkOTPRequirement(theMAPPRequest, MAPPAPIConstants.OTP_CHECK_STAGE.GENERATION);

            int intOTPTTL = 0;
            int intOTPLength = 0;
            String strOTPID = "";

            if (otp.isEnabled()) {
                intOTPTTL = otp.getTtl();
                intOTPLength = otp.getLength();
                strOTPID = otp.getId();
            }

            String strAppSignature = configXPath.evaluate("APP_SIGNATURE", ndRequestMSG).trim();
            if (strAppSignature == null) {
                strAppSignature = "";
            }

            String strOneTImePIN = Utils.generateRandomString(intOTPLength);
//            String strOneTImePIN = "123456";

            //MAPPAPIDB.fnDeleteOTPData(strUsername);

//todo OTP Remove on LIVE
            //check if ios/Appstore Testing
            if (strUsername.equals(strIOSTESTUSERNAME)) {
                strOneTImePIN = strIOSTESTOTP;
            } else {
                MAPPAPIDB.fnInsertOTPData(strUsername, strOneTImePIN, intOTPTTL);
            }

            MAPPAPIDB.fnInsertOTPData(strUsername, strOneTImePIN, intOTPTTL);

            SimpleDateFormat sdSimpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            Timestamp tsCurrentTimestamp = new Timestamp(System.currentTimeMillis());
            Timestamp tsCurrentTimestampPlusTime = new Timestamp(System.currentTimeMillis() + (intOTPTTL * 1000));

            String strTimeGenerated = sdSimpleDateFormat.format(tsCurrentTimestamp);
            String strExpiryDate = sdSimpleDateFormat.format(tsCurrentTimestampPlusTime);

            String strMSG = "Dear Member,\n" + strOneTImePIN + " is your One Time Password(OTP) generated at " + strTimeGenerated + ". This OTP is valid up to " + strExpiryDate + ".\n" + strOTPID + (!strAppSignature.equals("") ? ("\n" + strAppSignature) : "");

            System.out.println("OTP: " + strOneTImePIN);

            String strCharge = "YES";

            int intMSGSent = fnSendSMS(strUsername, strMSG, "YES", MSGConstants.MSGMode.EXPRESS, 200, "ONE_TIME_PASSWORD", "MAPP", "MBANKING_SERVER", strSessionID, strTraceID);

            String strTitle = "OTP Generated and Sent Successfully";
            String strResponseText = "Your One Time Password was generated and sent successfully.";

            if (intMSGSent <= 0) {
                strTitle = "OTP Generation Failed";
                strResponseText = "There was an error sending your One Time Password. Please try again";
                strCharge = "NO";
                enResponseAction = CON;
                enResponseStatus = ERROR;
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse activateMobileAppWithKYC(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + ":" + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            String strActivationCode = configXPath.evaluate("ACTIVATION_CODE", ndRequestMSG).trim();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "";
            String strDescription = "";

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction;
            MAPPConstants.ResponseStatus enResponseStatus;

            // String strUserAccountStatus = "ERROR";
            String strUserAccountStatus = "SUCCESS";
/*
            HashMap<String, String> hmActivateMAPP = CBSAPI.activateMobileAppWithKYC(getTraceID(theMAPPRequest), "MSISDN", strUsername,
                    strPassword, strAppID, RegisterConstants.IdentityType.NATIONAL_ID.getValue(), strActivationCode);

            if (!hmActivateMAPP.isEmpty()) {
                strUserAccountStatus = hmActivateMAPP.get("mobile_app_activation_status");
            } else {
                strUserAccountStatus = "ERROR";
            }*/

            switch (strUserAccountStatus) {
                case "SUCCESS": {
                    strTitle = "Activation Successful";
                    strDescription = "Mobile app account activation was successful";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                    break;
                }
                case "ERROR": {
                    strTitle = "Account Blocked";
                    strDescription = "Your account is blocked, please visit you nearest SACCO branch for assistance.";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;
                    break;
                }
                case "INVALID_ACCOUNT": {
                    strTitle = "Incorrect ID Number";
                    strDescription = "The ID Number you entered is incorrect or has expired. Please confirm the activation code and try again.";
                    enResponseAction = CON;
                    enResponseStatus = FAILED;
                    break;
                }
                case "NOT_FOUND": {
                    strTitle = "Account Not Found";
                    strDescription = "An error occurred. Please try again after a few minutes.";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;
                    break;
                }
                default: {
                    strTitle = "Activation Failed";
                    strDescription = "An error occurred. Please try again after a few minutes.";
                    enResponseAction = CON;
                    enResponseStatus = ERROR;
                    break;
                }
            }

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strDescription);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    //FIXME: NOT Implemented
    public MAPPResponse registerMember(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("registerMember");
            XPath configXPath = XPathFactory.newInstance().newXPath();

            MAPPResponse mrOTPVerificationMappResponse = null;
            MAPPAPIConstants.OTP_VERIFICATION_STATUS otpVerificationStatus = MAPPAPIConstants.OTP_VERIFICATION_STATUS.SUCCESS;

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();

            Crypto crypto = new Crypto();
            strPassword = crypto.hash("MD5", strPassword);

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;
            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strName = configXPath.evaluate("NAME", ndRequestMSG).trim();
            String strPhoneNumber = configXPath.evaluate("PHONE_NUMBER", ndRequestMSG).trim();
            String strNationalIDNumber = configXPath.evaluate("NATIONAL_ID_NUMBER", ndRequestMSG).trim();
            String strDateOfBirth = configXPath.evaluate("DATE_OF_BIRTH", ndRequestMSG).trim();

            String strSessionID = String.valueOf(theMAPPRequest.getSessionID());
            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            DateFormat format = new SimpleDateFormat("yyyy-MM-dd");

            Date dtMemberDateOfBirth = format.parse(strDateOfBirth);

            GregorianCalendar calMemberDateOfBirth = new GregorianCalendar();
            calMemberDateOfBirth.setTime(dtMemberDateOfBirth);
            XMLGregorianCalendar xmlGregCalMemberDateOfBirth = DatatypeFactory.newInstance().newXMLGregorianCalendar(calMemberDateOfBirth);

            //todo - Implement Integration to CBS
            //String strNewMemberRegistrationStatus = Navision.getPort().registerVirtualMember(strName, strNationalIDNumber, strPhoneNumber, xmlGregCalMemberDateOfBirth, strUsername, strEntryNumber);
            String strNewMemberRegistrationStatus = "SUCCESS";
            switch (strNewMemberRegistrationStatus) {
                case "SUCCESS": {
                    NodeList nlMemberImages = ((NodeList) configXPath.evaluate("PASSPORT_SIZE_IMAGES/IMAGE", ndRequestMSG, XPathConstants.NODESET));
                    NodeList nlNationalIDImages = ((NodeList) configXPath.evaluate("NATIONAL_ID_IMAGES/IMAGE", ndRequestMSG, XPathConstants.NODESET));

                    //todo - Implement Integration to CBS
                    //String strImagesPath = Navision.getPort().getVirtualMemberRegistrationImagesPath();
                    String strImagesPath = "/tmp";

                    for (int i = 0; i < nlMemberImages.getLength(); i++) {
                        String strImageName = configXPath.evaluate("@NAME", nlMemberImages.item(i)).trim();
                        String strImageType = configXPath.evaluate("@TYPE", nlMemberImages.item(i)).trim();
                        String strImageData = configXPath.evaluate("@DATA", nlMemberImages.item(i)).trim();

                        String strImagesPathForPhotographs = strImagesPath + "\\photographs\\" + strImageName + "." + strImageType;
                        APIUtils.fnCreateFileFromBase64(strImageData, strImagesPathForPhotographs);
                        //todo - Implement Integration to CBS
                        //Navision.getPort().updateVirtualMemberRegistration(strImageName, strImagesPathForPhotographs.replace("\\\\", "\\"), strEntryNumber, "Member Photographs");
                    }

                    for (int i = 0; i < nlNationalIDImages.getLength(); i++) {
                        String strImageName = configXPath.evaluate("@NAME", nlNationalIDImages.item(i)).trim();
                        String strImageType = configXPath.evaluate("@TYPE", nlNationalIDImages.item(i)).trim();
                        String strImageData = configXPath.evaluate("@DATA", nlNationalIDImages.item(i)).trim();

                        String strImagesPathForIDs = strImagesPath + "\\ids\\" + strImageName + "." + strImageType;
                        APIUtils.fnCreateFileFromBase64(strImageData, strImagesPathForIDs);
                        //todo - Implement Integration to CBS
                        //Navision.getPort().updateVirtualMemberRegistration(strImageName, strImagesPathForIDs.replace("\\\\", "\\"), strEntryNumber, "National ID");
                    }

                    strTitle = "Request Received Successfully";
                    strResponseText = "Your member registration was received successfully.";
                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                    break;
                }
                case "ERROR": {
                    strTitle = "ERROR: Register New Member";
                    strResponseText = "An error occurred. Please try again after a few minutes.";
                    enResponseAction = CON;
                    enResponseStatus = FAILED;
                    break;
                }
                default: {
                    enResponseAction = MAPPConstants.ResponseAction.END;
                    enResponseStatus = ERROR;
                    strTitle = "ERROR: Register New Member";
                    strResponseText = "An error occurred. Please try again after a few minutes.";
                }
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    //FIXME: NOT Implemented
    public MAPPResponse getHomePageAddons(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("getHomePageAddons");

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;
            MAPPConstants.ResponseAction enResponseAction;
            MAPPConstants.ResponseStatus enResponseStatus;

            String strTitle;
            String strResponseText;

            String strCharge = "NO";

            String strNewMemberRegistrationStatus = "SUCCESS";
            Element elData = doc.createElement("DATA");

            switch (strNewMemberRegistrationStatus) {
                case "SUCCESS": {
                    strTitle = "Request Received Successfully";

                    elData.setAttribute("TYPE", "ELEMENT");


                    Element elAddons = doc.createElement("ADD_ONS");
                    elData.appendChild(elAddons);

                    {
                        Element elAddon = doc.createElement("ADD_ON");
                        elAddon.setAttribute("NAME", "HOME");
                        elAddon.setAttribute("TAB", "HOME");
                        elAddons.appendChild(elAddon);
                        Element elCards = doc.createElement("CARDS");
                        elAddon.appendChild(elCards);
                        Element elCard = createCardElement(doc, "AGM Announcement", "We will be having an AGM on 4th January 2021. Kindly plan to attend.", MAPPAPIConstants.CardValueType.TEXT, 16);
                        elCards.appendChild(elCard);
                        Element elButtons = doc.createElement("BUTTONS");
                        elCard.appendChild(elButtons);
                        Element elButton = doc.createElement("BUTTON");
                        elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.CONTACT_US.getValue());
                        elButtons.appendChild(elButton);
                    }

                    {
                        Element elAddon = doc.createElement("ADD_ON");
                        elAddon.setAttribute("NAME", "TRANSACT");
                        elAddon.setAttribute("TAB", "TRANSACT");
                        elAddons.appendChild(elAddon);
                        Element elCards = doc.createElement("CARDS");
                        elAddon.appendChild(elCards);
                        Element elCard = createCardElement(doc, "Launch of B2B Services", "We have launched Bank to Bank transfer services and you can now send money from SACCO to Bank.", MAPPAPIConstants.CardValueType.TEXT, 16);
                        elCards.appendChild(elCard);
                        Element elButtons = doc.createElement("BUTTONS");
                        elCard.appendChild(elButtons);
                        Element elButton = doc.createElement("BUTTON");
                        elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.BANK_TRANSFER.getValue());
                        elButtons.appendChild(elButton);
                    }

                    {
                        Element elAddon = doc.createElement("ADD_ON");
                        elAddon.setAttribute("NAME", "ACCOUNTS");
                        elAddon.setAttribute("TAB", "MY_ACCOUNT");
                        elAddons.appendChild(elAddon);
                        Element elCards = doc.createElement("CARDS");
                        elAddon.appendChild(elCards);
                        {
                            Element elCard = createCardElement(doc, "Total FOSA Accounts", "12345", MAPPAPIConstants.CardValueType.CURRENCY, 20);
                            elCards.appendChild(elCard);
                        }
                        {
                            Element elCard = createCardElement(doc, "Total BOSA Accounts", "5000", MAPPAPIConstants.CardValueType.CURRENCY, 20);
                            elCards.appendChild(elCard);
                        }
                        Element elList = doc.createElement("LIST");
                        {
                            elList.setAttribute("TYPE", "ACCOUNTS");
                            elAddon.appendChild(elList);
                            Element elCategories = doc.createElement("CATEGORIES");
                            elList.appendChild(elCategories);
                            {
                                Element elCategory = doc.createElement("CATEGORY");
                                elCategories.appendChild(elCategory);
                                elCategory.setAttribute("LABEL", "All Accounts");
                                elCategory.setAttribute("NAME", "ALL");
                            }
                            {
                                Element elCategory = doc.createElement("CATEGORY");
                                elCategories.appendChild(elCategory);
                                elCategory.setAttribute("LABEL", "BOSA");
                                elCategory.setAttribute("NAME", "BOSA");
                            }
                            {
                                Element elCategory = doc.createElement("CATEGORY");
                                elCategories.appendChild(elCategory);
                                elCategory.setAttribute("LABEL", "FOSA");
                                elCategory.setAttribute("NAME", "FOSA");
                            }
                        }
                        {
                            Element elItems = doc.createElement("ITEMS");
                            elList.appendChild(elItems);
                            elItems.setAttribute("LABEL", "Savings Accounts");
                            elItems.setAttribute("CATEGORIES", "ALL,FOSA");
                            Element elItem = createItemElement(doc, "6100487005678", "23893", MAPPAPIConstants.CardValueType.CURRENCY);
                            elItems.appendChild(elItem);
                            Element elButtons = doc.createElement("BUTTONS");
                            elItems.appendChild(elButtons);
                            Element elButton = doc.createElement("BUTTON");
                            elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.ACCOUNT_STATEMENT.getValue());
                            elButtons.appendChild(elButton);
                        }
                        {
                            Element elItems = doc.createElement("ITEMS");
                            elList.appendChild(elItems);
                            elItems.setAttribute("LABEL", "Deposit Contribution");
                            elItems.setAttribute("CATEGORIES", "ALL,BOSA");
                            Element elItem = createItemElement(doc, "6100487005678", "456655", MAPPAPIConstants.CardValueType.CURRENCY);
                            elItems.appendChild(elItem);
                            Element elButtons = doc.createElement("BUTTONS");
                            elItems.appendChild(elButtons);
                            Element elButton = doc.createElement("BUTTON");
                            elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.ACCOUNT_STATEMENT.getValue());
                            elButtons.appendChild(elButton);
                        }
                        {
                            Element elItems = doc.createElement("ITEMS");
                            elList.appendChild(elItems);
                            elItems.setAttribute("LABEL", "Shares");
                            elItems.setAttribute("CATEGORIES", "ALL,BOSA");
                            Element elItem = createItemElement(doc, "6100487005678", "563456", MAPPAPIConstants.CardValueType.CURRENCY);
                            elItems.appendChild(elItem);
                            Element elButtons = doc.createElement("BUTTONS");
                            elItems.appendChild(elButtons);
                            Element elButton = doc.createElement("BUTTON");
                            elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.ACCOUNT_STATEMENT.getValue());
                            elButtons.appendChild(elButton);
                        }
                    }

                    {
                        Element elAddon = doc.createElement("ADD_ON");
                        elAddon.setAttribute("NAME", "LOANS");
                        elAddon.setAttribute("TAB", "LOANS");
                        elAddons.appendChild(elAddon);
                        Element elCards = doc.createElement("CARDS");
                        elAddon.appendChild(elCards);
                        {
                            Element elCard = createCardElement(doc, "Total Outstanding Loans", "12345", MAPPAPIConstants.CardValueType.CURRENCY, 20);
                            elCards.appendChild(elCard);
                        }
                        {
                            Element elCard = createCardElement(doc, "Total Guaranteed Loans", "5000", MAPPAPIConstants.CardValueType.CURRENCY, 20);
                            elCards.appendChild(elCard);
                        }
                        Element elList = doc.createElement("LIST");
                        {
                            elList.setAttribute("TYPE", "LOAND");
                            elAddon.appendChild(elList);
                            Element elCategories = doc.createElement("CATEGORIES");
                            elList.appendChild(elCategories);
                            {
                                Element elCategory = doc.createElement("CATEGORY");
                                elCategories.appendChild(elCategory);
                                elCategory.setAttribute("LABEL", "My Loans");
                                elCategory.setAttribute("NAME", "MY_LOANS");
                            }
                            {
                                Element elCategory = doc.createElement("CATEGORY");
                                elCategories.appendChild(elCategory);
                                elCategory.setAttribute("LABEL", "Guaranteed Loans");
                                elCategory.setAttribute("NAME", "GUARANTEED_LOANS");
                            }
                        }
                        {
                            Element elItems = doc.createElement("ITEMS");
                            elList.appendChild(elItems);
                            elItems.setAttribute("LABEL", "Normal Loan");
                            elItems.setAttribute("CATEGORIES", "MY_LOANS");
                            {
                                Element elItem = createItemElement(doc, "Loan Type", "Normal Loan", MAPPAPIConstants.CardValueType.TEXT);
                                elItems.appendChild(elItem);
                            }
                            {
                                Element elItem = createItemElement(doc, "Loan Number", "LN893892", MAPPAPIConstants.CardValueType.TEXT);
                                elItems.appendChild(elItem);
                            }
                            {
                                Element elItem = createItemElement(doc, "Balance", "30000", MAPPAPIConstants.CardValueType.CURRENCY);
                                elItems.appendChild(elItem);
                            }
                            {
                                Element elItem = createItemElement(doc, "Installments", "3000", MAPPAPIConstants.CardValueType.CURRENCY);
                                elItems.appendChild(elItem);
                            }
                            Element elButtons = doc.createElement("BUTTONS");
                            elItems.appendChild(elButtons);
                            {
                                Element elButton = doc.createElement("BUTTON");
                                elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.PAY_LOAN.getValue());
                                elButtons.appendChild(elButton);
                            }
                            {
                                Element elButton = doc.createElement("BUTTON");
                                elButton.setAttribute("SERVICE", MAPPAPIConstants.MAPPService.LOAN_STATEMENT.getValue());
                                elButtons.appendChild(elButton);
                            }
                        }
                        {
                            Element elItems = doc.createElement("ITEMS");
                            elList.appendChild(elItems);
                            elItems.setAttribute("LABEL", "Normal Loan");
                            elItems.setAttribute("CATEGORIES", "GUARANTEED_LOANS");
                            {
                                Element elItem = createItemElement(doc, "Loan Type", "Normal Loan", MAPPAPIConstants.CardValueType.TEXT);
                                elItems.appendChild(elItem);
                            }
                            {
                                Element elItem = createItemElement(doc, "Loan Number", "LN893892", MAPPAPIConstants.CardValueType.TEXT);
                                elItems.appendChild(elItem);
                            }
                            {
                                Element elItem = createItemElement(doc, "Balance", "30000", MAPPAPIConstants.CardValueType.CURRENCY);
                                elItems.appendChild(elItem);
                            }
                            {
                                Element elItem = createItemElement(doc, "Installments", "3000", MAPPAPIConstants.CardValueType.CURRENCY);
                                elItems.appendChild(elItem);
                            }
                        }
                    }


                    strCharge = "YES";
                    enResponseAction = CON;
                    enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;
                    break;
                }
                case "ERROR": {
                    strTitle = "ERROR";
                    strResponseText = "An error occurred. Please try again after a few minutes.";
                    elData.setTextContent(strResponseText);
                    enResponseAction = CON;
                    enResponseStatus = FAILED;
                    break;
                }
                default: {
                    enResponseAction = CON;
                    enResponseStatus = ERROR;
                    strTitle = "ERROR";
                    strResponseText = "An error occurred. Please try again after a few minutes.";
                    elData.setTextContent(strResponseText);
                }
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    Element createCardElement(Document theDocument, String theLabel, String theValue, MAPPAPIConstants.CardValueType theType, float theFontSize) {
        Element rVal = theDocument.createElement("CARD");
        try {
            rVal.setAttribute("LABEL", theLabel);
            rVal.setAttribute("VALUE", theValue);
            rVal.setAttribute("TYPE", theType.getValue());
            rVal.setAttribute("FONT_SIZE", String.valueOf(theFontSize));
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    Element createItemElement(Document theDocument, String theLabel, String theValue, MAPPAPIConstants.CardValueType theType) {
        Element rVal = theDocument.createElement("ITEM");
        try {
            rVal.setAttribute("LABEL", theLabel);
            rVal.setAttribute("VALUE", theValue);
            rVal.setAttribute("TYPE", theType.getValue());
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    //FIXME: NOT Implemented
    public MAPPResponse getATMCards(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            Crypto crypto = new Crypto();
            strPassword = crypto.hash("MD5", strPassword);
            String strAppID = theMAPPRequest.getAppID();

            long lnSessionID = theMAPPRequest.getSessionID();

            boolean bFOSA = false;

            String strCardsXML = "" +
                    "<ATM_CARDS>" +
                    "<CARD><ID>9235********7239</ID><NAME>9235********7239</NAME></CARD>" +
                    "<CARD><ID>3249********8079</ID><NAME>3249********8079</NAME></CARD>" +
                    "</ATM_CARDS>";

             /*
             //Response from CBS is:
                <ATM_CARDS>
                    <CARD><ID>01</ID><NAME>9235808234587239</NAME></CARD>
                    <CARD><ID>02</ID><NAME>3249058234598079</NAME></CARD>
                </ATM_CARDS>
             */

            InputSource source = new InputSource(new StringReader(strCardsXML));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);

            NodeList nlAccounts = ((NodeList) configXPath.evaluate("/ATM_CARDS", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();

            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <MSG SESSION_ID='123121' TYPE='MOBILE_BANKING' ACTION='CON' STATUS='SUCCESS' CHARGE='NO'>
                    <TITLE>Withdrawal Accounts</TITLE>
                    <DATA TYPE='LIST'>
                        <CARDS>
                            <CARD ID='123456' NAME='123456' />
                            <CARD ID='123457' NAME='123457' />
                        </CARDS>
                    </DATA>
                </MSG>
            </MESSAGES>
            */

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            String strTitle = "ATM Cards";

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.LIST;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");

            Element elAccounts = doc.createElement("CARDS");
            elData.appendChild(elAccounts);

            for (int i = 0; i < nlAccounts.getLength(); i++) {
                String strAccountNo = configXPath.evaluate("ID", nlAccounts.item(i)).trim();
                String strAccountName = configXPath.evaluate("NAME", nlAccounts.item(i)).trim();

                Element elAccount = doc.createElement("CARD");
                elAccounts.appendChild(elAccount);

                elAccount.setAttribute("ID", strAccountNo);
                elAccount.setAttribute("NAME", strAccountName);
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public static String trimXML(String input) {
        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringBuffer result = new StringBuffer();
        try {
            String line;
            while ((line = reader.readLine()) != null)
                result.append(line.trim());
            return result.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //FIXME: NOT Implemented
    public MAPPResponse disableATMCard(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        try {

            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                 <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='LOAN_BALANCE' VERSION='1.01'>
                      <LOAN_NO>123456</LOAN_NO>
                </MSG>
            </MESSAGES>
            */
            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strATMCardID = configXPath.evaluate("ATM_CARD_ID", ndRequestMSG).trim();
            String strAction = configXPath.evaluate("ACTION", ndRequestMSG).trim();

            String strResponse = "SUCCESS";

            String strTitle = "";
            String strResponseText = "";

            if (strResponse.equals("SUCCESS")) {
                strTitle = "ATM Card Disabled";
                strResponseText = "Your request to disable ATM card " + strATMCardID + " was received successfully. You will receive an SMS confirmation shortly";
            } else {
                strTitle = "ERROR: Disable ATM Card";
                strResponseText = "An error occurred. Please try again after a few minutes.";
                enResponseStatus = ERROR;
                enResponseAction = MAPPConstants.ResponseAction.CON;

            }

             /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <MSG SESSION_ID='123121' TYPE='MOBILE_BANKING' ACTION='CON' STATUS='SUCCESS' CHARGE='YES'>
                    <TITLE>Loan Balance</TITLE>
                    <DATA TYPE='TEXT'>Your loan balance is KES 5,100.00</DATA>
                </MSG>
            </MESSAGES>
             */

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, "YES", strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse encryptText(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");
            /*
            <MESSAGES DATETIME='2014-08-25 22:19:53.0' VERSION='1.01'>
                <LOGIN USERNAME='254721913958' PASSWORD=' 246c15fe971deb81c499281dbe86c1846bb2f336500efb88a8d4f99b66f52b39' IMEI='123456789012345'/>
                <MSG SESSION_ID='123121' ORG_ID='123' TYPE='MOBILE_BANKING' ACTION='INTER_ACCOUNT_TRANSFER' VERSION='1.01'>
                    <FROM_ACCOUNT_NO>123456</FROM_ACCOUNT_NO>
                    <TO_ACCOUNT_NO>654321</TO_ACCOUNT_NO>
                    <TRANSFER_OPTION>ID Number</TRANSFER_OPTION>
                    <AMOUNT>2000</AMOUNT>
                </MSG>
            </MESSAGES>
            */

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            Crypto crypto = new Crypto();
            strPassword = crypto.hash("MD5", strPassword);
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strClearText = configXPath.evaluate("CLEARTEXT", ndRequestMSG).trim();
            String strTimestamp = configXPath.evaluate("TIMESTAMP", ndRequestMSG).trim();

            String strEncryptedText = strClearText;

            strEncryptedText = crypto.encrypt(APIUtils.ENCRYPTION_KEY + strTimestamp, strClearText);

            String strTitle = strTitle = "Text Encrypted Successfully";
            String strResponseText = strResponseText = "Text was encrypted successfully.";

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            Element elEncrypted = doc.createElement("ENCRYPTED");
            elEncrypted.setTextContent(strEncryptedText);
            elData.appendChild(elEncrypted);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".changePassword() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse decryptText(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            Crypto crypto = new Crypto();
            strPassword = crypto.hash("MD5", strPassword);
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strEncrypted = configXPath.evaluate("ENCRYPTED", ndRequestMSG).trim();
            String strTimestamp = configXPath.evaluate("TIMESTAMP", ndRequestMSG).trim();

            String strDecryptedText = strEncrypted;

            strDecryptedText = crypto.decrypt(APIUtils.ENCRYPTION_KEY + strTimestamp, strEncrypted);

            String strTitle = strTitle = "Text Encrypted Successfully";
            String strResponseText = strResponseText = "Text was encrypted successfully.";

            String strCharge = "NO";

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            String[] arStrDecryptedText = strDecryptedText.split("\\|");
            //FUNDS_TRANSFER|254722554433|JOHN DOE|100.00|1593884595611
            //QR_CODE_TYPE|PHONE_NO|FULL_NAME|AMOUNT|ACCOUNT
            //Amount should not have commas

            String strName = arStrDecryptedText[2];
            String strAccountNumber = "";
            String strAccountName = " ";
            String strPhoneNumber = arStrDecryptedText[1];
            String strAmount = arStrDecryptedText[3];
            Element elAccountDetails = null;

            String strType = arStrDecryptedText[0];
            switch (strType) {
                case "CASH_WITHDRAWAL":
                case "BUY_AIRTIME": {
                    elAccountDetails = getAccountElement(theMAPPRequest, strPhoneNumber, "Mobile", doc, "ENCRYPTION");
                    break;
                }
                case "DEPOSIT_MONEY":
                case "FUNDS_TRANSFER": {
                    strAccountNumber = arStrDecryptedText[4];
                    elAccountDetails = getAccountElement(theMAPPRequest, strAccountNumber, "ACCOUNT", doc, "ENCRYPTION");
                    break;
                }
                default: {
                    elAccountDetails = getAccountElement(theMAPPRequest, strPhoneNumber, "Mobile", doc, "ENCRYPTION");
                    break;
                }
            }

            if (elAccountDetails != null) {
                strName = elAccountDetails.getAttribute("NAME");
                strAccountNumber = elAccountDetails.getAttribute("ACCOUNT_NO");
                strAccountName = elAccountDetails.getAttribute("ACCOUNT_NAME");
                strPhoneNumber = elAccountDetails.getAttribute("PHONE_NO");
            }

            strDecryptedText = strType + "|" + strPhoneNumber + "|" + strName + "|" + strAmount + "|" + strAccountNumber + "|" + strAccountName;

            Element elEncrypted = doc.createElement("DECRYPTED");
            elEncrypted.setTextContent(strDecryptedText);
            elData.appendChild(elEncrypted);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".decryptText() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return theMAPPResponse;
    }

    public MAPPResponse addOrDeleteUtilityAndPaybillAccount(MAPPRequest theMAPPRequest, String theAction) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "()");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            //Request
            String strUsername = theMAPPRequest.getUsername();
            String strPassword = theMAPPRequest.getPassword();
            String strAppID = theMAPPRequest.getAppID();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = TEXT;

            MAPPConstants.ResponseAction enResponseAction = CON;
            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strProviderAccountCode = configXPath.evaluate("PROVIDER_ACCOUNT_CODE", ndRequestMSG).trim();
            String strName = configXPath.evaluate("ACCOUNT_NAME", ndRequestMSG).trim();
            String strNumber = configXPath.evaluate("ACCOUNT_NUMBER", ndRequestMSG).trim();

            String strServiceProviderID = null;
            String strIntegritySecret = PESALocalParameters.getIntegritySecret();
            LinkedList<LinkedHashMap<String, String>> linkedHashMapLinkedList = new SPManager(strIntegritySecret).getSPAccounts(SPManagerConstants.Condition.YES, SPManagerConstants.Condition.YES, SPManagerConstants.Condition.YES, SPManagerConstants.Condition.YES);

            for (LinkedHashMap<String, String> stringStringLinkedHashMap : linkedHashMapLinkedList) {
                if (stringStringLinkedHashMap.get("provider_account_identifier").equalsIgnoreCase(strProviderAccountCode)) {
                    strServiceProviderID = stringStringLinkedHashMap.get("provider_account_code");
                    break;
                }
            }

            boolean blFundsTransferStatus;

            if (theAction.equalsIgnoreCase("ADD")) {
                long lnFundsTransferStatus = new SPManager(strIntegritySecret).createUserSavedAccount(SPManagerConstants.UserIdentifierType.MSISDN, strUsername, strServiceProviderID, SPManagerConstants.AccountIdentifierType.ACCOUNT_NO, strNumber, strName);
                blFundsTransferStatus = lnFundsTransferStatus > 0;
            } else {
                blFundsTransferStatus = new SPManager(strIntegritySecret).removeUserSavedAccount(SPManagerConstants.UserIdentifierType.MSISDN, strUsername, strServiceProviderID, SPManagerConstants.AccountIdentifierType.ACCOUNT_NO, strNumber);
            }

            String strTitle = "";
            String strResponseText = "";

            String strCharge = "NO";

            if (blFundsTransferStatus) {
                strTitle = "Success";
                strResponseText = "Success.";
                strCharge = "YES";
            } else {
                strTitle = "Error";
                strResponseText = "Error";
                enResponseStatus = FAILED;
            }

            Element elData = doc.createElement("DATA");
            elData.setTextContent(strResponseText);

            generateResponseMSGNode(doc, elData, theMAPPRequest, enResponseAction, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".changePassword() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public MAPPResponse getMemberName(MAPPRequest theMAPPRequest) {
        MAPPResponse theMAPPResponse = null;

        try {
            System.out.println("getMemberName");

            XPath configXPath = XPathFactory.newInstance().newXPath();

            Node ndRequestMSG = theMAPPRequest.getMSG();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Root element - MSG
            Document doc = docBuilder.newDocument();

            MAPPConstants.ResponsesDataType enDataType = MAPPConstants.ResponsesDataType.OBJECT;

            MAPPConstants.ResponseStatus enResponseStatus = MAPPConstants.ResponseStatus.SUCCESS;

            String strIdentifierType = configXPath.evaluate("OPTION", ndRequestMSG).trim();
            String strIdentifier = configXPath.evaluate("ACCOUNT", ndRequestMSG).trim();


            if (strIdentifierType.equals("ID Number")) {
                strIdentifierType = "NATIONAL_ID";
            } else if (strIdentifierType.equals("Account Number")) {
                strIdentifierType = "ACCOUNT";
            } else if (strIdentifierType.equals("Member Number")) {
                strIdentifierType = "CUSTOMER_NO";
            } else if (strIdentifierType.equals("Mobile Number")) {
                strIdentifierType = "MSISDN";
                strIdentifier = APIUtils.sanitizePhoneNumber(strIdentifier);
            }

            String strTitle = "Account Details";

            String strCharge = "NO";
            Element elData = doc.createElement("DATA");

            TransactionWrapper<FlexicoreHashMap> getMemberDetailsWrapper = ApStarCBS.getMemberDetails(strIdentifierType, strIdentifier);
            FlexicoreHashMap userDetailsMap = getMemberDetailsWrapper.getSingleRecord();

            if (!getMemberDetailsWrapper.hasErrors()) {
                Element elAccountDetails = doc.createElement("ACCOUNT");
                elAccountDetails.setAttribute("STATUS", "FOUND");
                elAccountDetails.setAttribute("ACCOUNT_NO", userDetailsMap.getStringValue("main_savings_account_no"));
                elAccountDetails.setAttribute("ACCOUNT_NAME", userDetailsMap.getStringValue("main_savings_account_no"));
                elAccountDetails.setAttribute("NAME", userDetailsMap.getStringValue("full_name"));
                elAccountDetails.setAttribute("MEMBER_NO", userDetailsMap.getStringValue("identifier"));
                elAccountDetails.setAttribute("PHONE_NO", userDetailsMap.getStringValue("primary_mobile_number"));

                elData.appendChild(elAccountDetails);
            } else {
                String requestStatus = userDetailsMap.getStringValue("request_status");
                FlexicoreHashMap fundsTransferMap = userDetailsMap.getFlexicoreHashMap("response_payload");
                String strErrorMessage = fundsTransferMap.getStringValue("error_message");

                enResponseStatus = ERROR;
                enDataType = TEXT;
                strTitle = requestStatus;
                elData.setTextContent(strErrorMessage);
            }

            generateResponseMSGNode(doc, elData, theMAPPRequest, CON, enResponseStatus, strCharge, strTitle, enDataType);

            //Response
            Node ndResponseMSG = doc.getElementsByTagName("MSG").item(0);

            theMAPPResponse = setMAPPResponse(ndResponseMSG, theMAPPRequest);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }

        return theMAPPResponse;
    }

    public Element getAccountElement(MAPPRequest theMAPPRequest, String theAccount, String theSource, Document doc, String theCategory) {
        try {
            //theSource (from MAPPAPI) -> Mobile / ID Number / Account Number / Member Number
            //theSource (to XTremeAPI) -> MEMBER_NUMBER / ID_NUMBER / ACCOUNT_NUMBER / MOBILE_NUMBER
            switch (theSource) {
                case "ID": {
                    theSource = "NATIONAL_ID";
                    break;
                }

                case "ACCOUNT": {
                    theSource = "ACCOUNT_NUMBER";
                    break;
                }

                case "Member Number": {
                    theSource = "MEMBER_NUMBER";
                    break;
                }

                case "Mobile":
                default: {
                    theSource = "MSISDN";
                    break;
                }
            }

            HashMap<String, String> hmMemberDetails = getUserDetails(theMAPPRequest, theSource, theAccount);

           /* HashMap<String, HashMap<String, String>> hmIFTDestAccounts = (HashMap<String, HashMap<String, String>>) accountDetails.get("accounts");
            HashMap<String, String> hmMemberDetails = (HashMap<String, String>) accountDetails.get("user_details");*/

            Element elPesaOtherDetails = null;

            String strAccountNo = "";
            String strAccountType = "";
            String strAccountName = "";
            String strName = "";
            String strAccountMemberNo = "";
            String strPhoneNo = "";
            String strIDNumber = "";
            String strAccountStatus = "NOT_FOUND";

            if (hmMemberDetails != null && !hmMemberDetails.isEmpty()) {
                strAccountStatus = "FOUND";
                strAccountNo = hmMemberDetails.get("number");
                strAccountType = hmMemberDetails.get("type_name");
                strAccountMemberNo = hmMemberDetails.get("member_number");
                strAccountName = hmMemberDetails.get("full_name");
                strPhoneNo = hmMemberDetails.get("identifier");
                strIDNumber = hmMemberDetails.get("identity");
                strAccountName = Utils.toTitleCase(strAccountName);
            }

            if (theCategory != null) {
                if (theCategory.equals("VALIDATE_PESA_IN")) {
                    elPesaOtherDetails = doc.createElement("PESA_OTHER_DETAILS");

                    Element elKYCDetails = doc.createElement("KYC_DETAILS");
                    elPesaOtherDetails.appendChild(elKYCDetails);

                    Element elKYCResponse = doc.createElement("RESPONSE");
                    elKYCDetails.appendChild(elKYCResponse);

                    Element elKYC = doc.createElement("KYC");
                    elKYC.setAttribute("TYPE", theSource);
                    elKYCResponse.appendChild(elKYC);

                    Element elIdentifier = doc.createElement("IDENTIFIER");
                    elIdentifier.setTextContent(theAccount);
                    elKYC.appendChild(elIdentifier);

                    Element elAccount = doc.createElement("ACCOUNT");
                    elAccount.setTextContent(strAccountNo);
                    elKYC.appendChild(elAccount);

                    Element elName = doc.createElement("NAME");
                    elName.setTextContent(strAccountName);
                    elKYC.appendChild(elName);

                    Element elOtherDetails = doc.createElement("OTHER_DETAILS");
                    elKYC.appendChild(elOtherDetails);
                } else {
                    elPesaOtherDetails = doc.createElement("ACCOUNT");
                    elPesaOtherDetails.setAttribute("STATUS", strAccountStatus);
                    elPesaOtherDetails.setAttribute("ACCOUNT_NO", strAccountNo);
                    elPesaOtherDetails.setAttribute("ACCOUNT_NAME", strAccountType);
                    elPesaOtherDetails.setAttribute("NAME", strAccountName);
                    elPesaOtherDetails.setAttribute("MEMBER_NO", strAccountMemberNo);
                    elPesaOtherDetails.setAttribute("PHONE_NO", strPhoneNo);
                }
            }
            return elPesaOtherDetails;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getResponseStatus(String strXML) {
        String strStatus = "";
        try {
            if (!strXML.equals("")) {
                InputSource source = new InputSource(new StringReader(strXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                NodeList nlResponse = ((NodeList) configXPath.evaluate("/Response", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();

                strStatus = nlResponse.item(0).getTextContent();
            }
        } catch (Exception e) {
            System.err.println("PESAAPI.getResponseStatus() ERROR : " + e.getMessage());
        }
        return strStatus;
    }

    public MAPPAmountLimitParam getParam(MAPPAPIConstants.MAPP_PARAM_TYPE theMAPPParamType) {
        MAPPAmountLimitParam rVal = new MAPPAmountLimitParam();
        try {
            String strMAPPParamType = "OTHER_DETAILS/CUSTOM_PARAMETERS/SERVICE_CONFIGS/AMOUNT_LIMITS";

            switch (theMAPPParamType) {
                case CASH_WITHDRAWAL: {
                    strMAPPParamType += "/CASH_WITHDRAWAL";
                    break;
                }
                case AIRTIME_PURCHASE: {
                    strMAPPParamType += "/AIRTIME_PURCHASE";
                    break;
                }
                case PAY_BILL: {
                    strMAPPParamType += "/PAY_BILL";
                    break;
                }
                case EXTERNAL_FUNDS_TRANSFER: {
                    strMAPPParamType += "/EXTERNAL_FUNDS_TRANSFER";
                    break;
                }
                case INTERNAL_FUNDS_TRANSFER: {
                    strMAPPParamType += "/INTERNAL_FUNDS_TRANSFER";
                    break;
                }
                case DEPOSIT: {
                    strMAPPParamType += "/DEPOSIT";
                    break;
                }
                case APPLY_LOAN: {
                    strMAPPParamType += "/APPLY_LOAN";
                    break;
                }
                case PAY_LOAN: {
                    strMAPPParamType += "/PAY_LOAN";
                    break;
                }
            }

            String strMinimum = MBankingAPI.getValueFromLocalParams(MBankingConstants.ApplicationType.MAPP, strMAPPParamType + "/MIN_AMOUNT");
            String strMaximum = MBankingAPI.getValueFromLocalParams(MBankingConstants.ApplicationType.MAPP, strMAPPParamType + "/MAX_AMOUNT");

            rVal.setMinimum(strMinimum);
            rVal.setMaximum(strMaximum);
        } catch (Exception e) {
            System.err.println("MAPPAPI.getParam() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    public HashMap<String, String> getUserDetails(MAPPRequest theMAPPRequest, String identifierType, String identifier) {
        HashMap<Object, Object> hmRVal = null;
        try {
            String strMobileNumber = String.valueOf(theMAPPRequest.getUsername());
            String strAppID = String.valueOf(theMAPPRequest.getAppID());
            String strPassword = theMAPPRequest.getPassword();

            if (identifierType.equalsIgnoreCase("Mobile No") || identifierType.equals("MSISDN")) {
                identifierType = "MSISDN";
            } else if (identifierType.equalsIgnoreCase("ID Number") || identifierType.equals("ID") || identifierType.equals("NATIONAL_ID")) {
                identifierType = "NATIONAL_ID";
            } else if (identifierType.equalsIgnoreCase("Member Number") || identifierType.equals("MEMBER_NUMBER")) {
                identifierType = "MEMBER_NUMBER";
            } else if (identifierType.equalsIgnoreCase("Account Number") || identifierType.equals("Account") || identifierType.equals("ACCOUNT") || identifierType.equals("ACCOUNT_NUMBER")) {
                identifierType = "ACCOUNT_NUMBER";
            } else {
                identifierType = "MSISDN";
            }

            TransactionWrapper<FlexicoreHashMap> getUserDetailsWrapper = CBSAPI.validateAccountNumber(theMAPPRequest.getUsername(), identifierType, identifier, identifier);
            if (!getUserDetailsWrapper.hasErrors()) {
                FlexicoreHashMap userDetailsMap = getUserDetailsWrapper.getSingleRecord();

                HashMap<String, String> userDetailsHashMap = new HashMap<>();

                userDetailsHashMap.put("number", userDetailsMap.getStringValue("acc_no"));
                userDetailsHashMap.put("type_name", userDetailsMap.getStringValue("ac_label"));
                userDetailsHashMap.put("member_number", userDetailsMap.getStringValue("cust_id"));
                userDetailsHashMap.put("full_name", userDetailsMap.getStringValue("cust_name"));
                userDetailsHashMap.put("identifier", userDetailsMap.getStringValue("pri_mobile_no"));
                userDetailsHashMap.put("identity", userDetailsMap.getStringValue("cust_id_no"));

                return userDetailsHashMap;
            }

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

/*
    public TransactionWrapper<FlexicoreHashMap> getUserDetails(MAPPRequest theMAPPRequest, String identifierType, String identifier) {
        try {
            String strMobileNumber = String.valueOf(theMAPPRequest.getUsername());
            String strAppID = String.valueOf(theMAPPRequest.getAppID());
            String strPassword = theMAPPRequest.getPassword();

            if (identifierType.equalsIgnoreCase("Mobile No") || identifierType.equals("MSISDN")) {
                identifierType = "MSISDN";
            } else if (identifierType.equalsIgnoreCase("ID Number") || identifierType.equals("ID") || identifierType.equals("NATIONAL_ID")) {
                identifierType = "NATIONAL_ID";
            } else if (identifierType.equalsIgnoreCase("Member Number") || identifierType.equals("MEMBER_NUMBER")) {
                identifierType = "CUSTOMER_NO";
            } else if (identifierType.equalsIgnoreCase("Account Number") || identifierType.equals("Account") || identifierType.equals("ACCOUNT") || identifierType.equals("ACCOUNT_NUMBER")) {
                identifierType = "ACCOUNT_NUMBER";
            } else {
                identifierType = "MSISDN";
            }

            return CBSAPI.getUserDetails(getTraceID(theMAPPRequest), "MSISDN", strMobileNumber, "APP_ID", strAppID, identifierType, identifier);



        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
            return resultWrapper;
        }
    }
*/

    public String getTraceID(MAPPRequest theMAPPRequest) {
        //return theMAPPRequest.getTraceID(); //+APIUtils.getCurrentDate("yyyyMMddHHmmssSSS");
        return UUID.randomUUID().toString().toLowerCase();
    }

    public LinkedHashMap<String, String> getMemberAccountsList(MAPPRequest mappRequest, MAPPAPIConstants.AccountType theAccountType) {

        LinkedHashMap<String, String> accountsMap = new LinkedHashMap<>();

        String strUsername = mappRequest.getUsername();
        String strAppId = mappRequest.getAppID();

        try {

            String theCustomerIdentifier = getDefaultCustomerIdentifier(mappRequest);
            //todo remove on live
            if (strUsername.equalsIgnoreCase("254723902802")) {
                theCustomerIdentifier = "0058646";
            }

            if (theCustomerIdentifier == null) {
                return accountsMap;
            }

            MAPPAPIConstants.AccountType theAccountTypeMain = theAccountType;

            if (theAccountType == MAPPAPIConstants.AccountType.WITHDRAWABLE_IFT) {
                theAccountTypeMain = MAPPAPIConstants.AccountType.WITHDRAWABLE;
            } else if (theAccountType == MAPPAPIConstants.AccountType.DEPOSIT_IFT) {
                theAccountTypeMain = MAPPAPIConstants.AccountType.DEPOSIT;
            }

            TransactionWrapper<FlexicoreHashMap> accountsListWrapper = CBSAPI.getCustomerAccounts(strUsername, "CUSTOMER_NO", theCustomerIdentifier, theAccountTypeMain.getValue());

            if (accountsListWrapper.hasErrors()) {
                System.err.println("MAPPAPI.LinkedHashMap<String, String> getAccountsListWithType() - ERROR:  " + accountsListWrapper.getErrors());
            } else {

                FlexicoreArrayList accountsList = accountsListWrapper.getSingleRecord().getValue("payload");

                System.out.println("ACCOUNTS FETCHED");
                System.out.println("--------------------------------------------------");

                for (FlexicoreHashMap accountMap : accountsList) {

                    String strAccountStatus = accountMap.getStringValue("account_status").trim();
                    String strAccountNumber = accountMap.getStringValue("account_number").trim();
                    String strAccountLabel = accountMap.getStringValue("account_label").trim();
                    String strAccountBookBalance = accountMap.getStringValue("account_balance").trim();
                    String strCanDeposit = accountMap.getStringValue("can_deposit").trim();
                    String strCanWithdraw = accountMap.getStringValue("can_withdraw").trim();
                    String strCanDepositIft = accountMap.getStringValue("can_deposit_ift").trim();
                    String strCanWithdrawIft = accountMap.getStringValue("can_withdraw_ift").trim();

                    if (theAccountType == MAPPAPIConstants.AccountType.DEPOSIT) {
                        if (!strCanDeposit.equalsIgnoreCase("YES")) continue;

                    } else if (theAccountType == MAPPAPIConstants.AccountType.DEPOSIT_IFT) {
                        if (!strCanDepositIft.equalsIgnoreCase("YES")) continue;
                    } else if (theAccountType == MAPPAPIConstants.AccountType.WITHDRAWABLE) {

                        if (!strCanWithdraw.equalsIgnoreCase("YES")) {
                            continue;
                        }

                        if (!strAccountStatus.equalsIgnoreCase("ACTIVE")) {
                            continue;
                        }
                    } else if (theAccountType == MAPPAPIConstants.AccountType.WITHDRAWABLE_IFT) {

                        if (!strCanWithdrawIft.equalsIgnoreCase("YES")) {
                            continue;
                        }

                        if (!strAccountStatus.equalsIgnoreCase("ACTIVE")) {
                            continue;
                        }
                    }


                   /* if (isFundsTransfter && !strCanDeposit.equalsIgnoreCase("YES")) {
                        continue;
                    }*/

                    System.out.println(strAccountNumber + " - " + strAccountLabel);
                    accountsMap.put(strAccountNumber, strAccountLabel);
                }
            }

        } catch (Exception e) {
            System.err.println("MAPPAPI.getAccountsListWithType() - ERROR" + e.getMessage() + "\n");
            e.printStackTrace();
        }

        System.out.println();
        System.out.println("ACCOUNTS TO DISPLAY");
        System.out.println("--------------------------------------------------");
        accountsMap.forEach((strAccountNumber, strAccountLabel) -> System.out.println(strAccountNumber + " - " + strAccountLabel));

        return accountsMap;
    }

    public String getDefaultCustomerIdentifier(MAPPRequest mappRequest) {

        String strUsername = mappRequest.getUsername();
        String strAppId = mappRequest.getAppID();

        try {

            TransactionWrapper<FlexicoreHashMap> signatoryCustomersListWrapper = CBSAPI.getSignatoryCustomersList(getTraceID(mappRequest), "MSISDN", strUsername,
                    "APP_ID", strAppId);

            if (signatoryCustomersListWrapper.hasErrors()) {
                System.err.println("MAPPAPI.getDefaultCustomerIdentifier() - ERROR:  " + signatoryCustomersListWrapper.getErrors());
            } else {

                FlexicoreArrayList customersList = signatoryCustomersListWrapper.getSingleRecord().getValue("payload");

                if (customersList.isEmpty()) {
                    return null;
                }
                return customersList.getRecord(0).getStringValue("identifier");
            }

        } catch (Exception e) {
            System.err.println("MAPPAPI.getDefaultCustomerIdentifier() - ERROR" + e.getMessage() + "\n");
            e.printStackTrace();
        }

        return null;
    }

    public void sendSMS(String theMobileNo, String theMSG, MSGConstants.MSGMode theMode, int thePriority, String theCategory, MAPPRequest theMAPPRequest) {
        try {
            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
            String strTraceID = theMAPPRequest.getTraceID();
            fnSendSMS(theMobileNo, theMSG, "YES", theMode, thePriority, theCategory, "MAPP", "MBANKING_SERVER", strTransactionID, strTraceID);
        } catch (Exception e) {
            System.err.println("USSDAPI.sendSMS() ERROR : " + e.getMessage());
        }
    }

    public static void MAPPRequestSimulation_() throws Exception {
        MAPPRequest theMAPPRequest = new MAPPRequest();
        theMAPPRequest.setMessagesVersion("1.00");
        theMAPPRequest.setMessagesDateTime(DateTime.getCurrentDateTime());
        theMAPPRequest.setUsername("254714443500");
        theMAPPRequest.setPassword("");
        theMAPPRequest.setAppID(UUID.randomUUID().toString());
        theMAPPRequest.setTraceID(UUID.randomUUID().toString());
        theMAPPRequest.setServerID(0);
        theMAPPRequest.setSessionID(0);
        theMAPPRequest.setSequence(0);
        theMAPPRequest.setProductID(0);
        theMAPPRequest.setMAPPType(MAPPConstants.MAPPType.MOBILE_BANKING);
        theMAPPRequest.setAction("GET_MERCHANT_PRODUCTS");
        theMAPPRequest.setVersion("1.00");
        theMAPPRequest.setDateCreated(DateTime.getCurrentDateTime());
        theMAPPRequest.setIntegrityHash(UUID.randomUUID().toString());

       /* String strRequestBody = """
                <MESSAGES>
                      <MSG ACTION="MEMBER_EXIT_APPLICATION">
                            <EXIT_REASON>Poor Services</EXIT_REASON>
                           <NARRATION>test Data</NARRATION>
                      </MSG>
                </MESSAGES>
                """;*/

     /*   String strRequestBody = """
                <MESSAGES>
                      <MSG ACTION="GET_INITIAL_MEMBER_INFO"/>
                </MESSAGES>
                """;*/

        String strRequestBody = """
                <MESSAGES>
                <MSG ACTION="GET_MERCHANT_PRODUCTS" PARAMETERS_VERSION="1.20019" PRODUCT_ID="1" SEQ="30" SERVER_ID="100201" SESSION_ID="293516969" SESSION_KEY="f0733750-d88f-41f8-9047-a467b89610c5" TRACE_ID="81b7ebca-ffa0-469b-b867-8a10efdfdde6" TYPE="MOBILE_BANKING">
                <LOAN_PRODUCT_ID>1</LOAN_PRODUCT_ID>
                <MERCHANT_ID>0076163</MERCHANT_ID>
                </MSG>
                </MESSAGES>
                """;

        Document requestBody = XmlUtils.parseXml(strRequestBody);
        XPath configXPath = XPathFactory.newInstance().newXPath();
        Node ndMSG = (Node) configXPath.evaluate("/MESSAGES/MSG", requestBody, XPathConstants.NODE);

        theMAPPRequest.setMSG(ndMSG);

        System.out.println("Calling mapprequestsimulation 1");
        MAPPResponse theMAPPResponse = new MAPPAPIProcessor().processMAPPAPI(theMAPPRequest);
        System.out.println("THE RESPONSE BODY: \n" + XmlUtils.convertNodeToStr(theMAPPResponse.getMSG()));
    }

}
