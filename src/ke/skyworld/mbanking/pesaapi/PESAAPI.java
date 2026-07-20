package ke.skyworld.mbanking.pesaapi;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.core.MBankingXMLFactory;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.pesa.*;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.mbankingapi.MBankingAPI;
import ke.skyworld.mbanking.mbankingapi.MemberRegistrationUtil;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import ke.skyworld.mbanking.ussdapplication.AppConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static ke.skyworld.mbanking.ussdapi.APIUtils.fnSendSMS;

public class PESAAPI {
    public static void confirmPESA_IN(PESA thePESAIN, PESAINResponse thePESAINResponse) {
        String strResponse = "";
        try {


            String strMobileNumber = thePESAIN.getInitiatorIdentifier();



         /*   Set<String> allowedNumbers = new HashSet<>();
            allowedNumbers.add("254720259655");
            allowedNumbers.add("254729566788");
            allowedNumbers.add("254720416494");
            allowedNumbers.add("254722832021");
            allowedNumbers.add("254722793859");
            allowedNumbers.add("254726589392");
            allowedNumbers.add("254728432445");
            allowedNumbers.add("254713000249");
            allowedNumbers.add("254716304210");
            allowedNumbers.add("254729692224");
            allowedNumbers.add("254114041681");
            allowedNumbers.add("254726958265");
            allowedNumbers.add("254722378923");
            allowedNumbers.add("254723782649");

            if (!allowedNumbers.contains(strMobileNumber)) {
                thePESAINResponse.setResponseCode(103);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
                thePESAINResponse.setResponseDescription("CBS RESPONSE: Error processing transaction");
                return;
            }*/


            thePESAINResponse.setCategory("MPESA_C2B_DEPOSIT");

            String entryCode = UUID.randomUUID().toString().toUpperCase();
            String transactionID = thePESAIN.getSourceReference();
            String transaction = "Paybill";
            String description = "Paybill - " + thePESAIN.getSenderIdentifier() + " " + thePESAIN.getSenderName();

            if (thePESAIN.getCommand().equals("PesaLink")) {
                transaction = "Bank Deposit";
                description = "PL|IN|" + thePESAIN.getSourceIdentifier() + "|" + thePESAIN.getBeneficiaryIdentifier();
            }

            String strOriginatorID = thePESAIN.getOriginatorID();

            String strBeneficiaryAccount = thePESAIN.getReceiverAccount();
            if (strBeneficiaryAccount.isEmpty()) {
                strBeneficiaryAccount = thePESAIN.getSourceIdentifier();
            }

            if (thePESAIN.getInitiatorIdentifier().length() > 20) {
                System.out.println("The initiator identifier is too long. Truncated to 20 characters");
                thePESAIN.setInitiatorIdentifier(thePESAIN.getInitiatorIdentifier().substring(0, 20));
                System.out.println("The initiator identifier is too long. Truncated to 20 characters");
                System.out.println("The initiator identifier is now: " + thePESAIN.getInitiatorIdentifier());
            }

            USSDAPIConstants.StandardReturnVal standardReturnVal = USSDAPIConstants.StandardReturnVal.ERROR;

            System.out.println("strBeneficiaryAccount: " + strBeneficiaryAccount);

            if (strBeneficiaryAccount.startsWith("REG:")) {
                String strMemberRegistrationOriginatorID = strBeneficiaryAccount.substring(4);
                System.out.println("The transaction is related to a member registration record [" + strMemberRegistrationOriginatorID + "]! Processing");
                boolean blUpdateStatus = MemberRegistrationUtil.updateRegistrationRecord(strMemberRegistrationOriginatorID, strOriginatorID, thePESAIN.getTransactionAmount());
                if (blUpdateStatus) {
                    standardReturnVal = USSDAPIConstants.StandardReturnVal.SUCCESS;
                } else {
                    standardReturnVal = USSDAPIConstants.StandardReturnVal.NOT_FOUND;
                }
            } else {
                String theTransactionDestinationRefForFailure = UUID.randomUUID().toString().toUpperCase();


                TransactionWrapper<FlexicoreHashMap> mobileMoneyDepositWrapper = CBSAPI.mobileMoneyDeposit(thePESAIN, theTransactionDestinationRefForFailure);

                FlexicoreHashMap mobileMoneyDepositMap = mobileMoneyDepositWrapper.getSingleRecord();

                standardReturnVal = mobileMoneyDepositMap.getValue("cbs_api_return_val");
                String cbsResponseMessage = mobileMoneyDepositMap.getStringValue("cbs_response_message");

                //default to 103 if cbs has error and stop processing
                if (mobileMoneyDepositWrapper.hasErrors()) {
                    System.out.println("CBS RESPONSE: Error processing transaction: " + cbsResponseMessage);
                    thePESAINResponse.setResponseCode(103);
                    thePESAINResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
                    thePESAINResponse.setResponseDescription("CBS RESPONSE: Error processing transaction");
                    return;
                }

                thePESAINResponse.setResponseCode(102);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                thePESAINResponse.setResponseDescription("CBS RESPONSE: Transaction received successfully");



               /* if (standardReturnVal == USSDAPIConstants.StandardReturnVal.SUCCESS) {

                    String cbsTransactionReference = mobileMoneyDepositMap.getStringValue("cbs_transaction_reference");
                    thePESAINResponse.setBeneficiaryApplication("CBS");
                    thePESAINResponse.setBeneficiaryReference(cbsTransactionReference);
                }

                if (standardReturnVal == USSDAPIConstants.StandardReturnVal.BUFFER_SAVE_SUCCESS) {
                    thePESAINResponse.setBeneficiaryApplication("CBS");
                    thePESAINResponse.setBeneficiaryReference(theTransactionDestinationRefForFailure);
                }

                if (standardReturnVal == USSDAPIConstants.StandardReturnVal.SUCCESS || standardReturnVal == USSDAPIConstants.StandardReturnVal.BUFFER_SAVE_SUCCESS) {

                    String strFormattedDateTime = Utils.formatDate(thePESAIN.getPesaDateCreated(), "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                    String strMSG = "Your M-Pesa Deposit of KES " + Utils.formatDouble(thePESAIN.getTransactionAmount(), "#,##0.00") +
                            " to Paybill " + thePESAIN.getReceiverIdentifier() + " (" + thePESAIN.getReceiverName() + ") for account " + thePESAIN.getReceiverAccount() + " was received successfully on " + strFormattedDateTime + ". M-Pesa Ref: " + thePESAIN.getOriginatorID();

                    //Requested by SACCO not to send.
                *//*int intMSGSent = fnSendSMS(thePESAIN.getSourceIdentifier(), strMSG, "YES", MSGConstants.MSGMode.SAF, 210, "MPESA_DEPOSIT", "USSD", "MBANKING_SERVER",
                        thePESAIN.getOriginatorID(), thePESAIN.getSourceReference());*//*

                }
            }

            if (standardReturnVal == USSDAPIConstants.StandardReturnVal.SUCCESS
                    || standardReturnVal == USSDAPIConstants.StandardReturnVal.BUFFER_SAVE_SUCCESS
                    || standardReturnVal == USSDAPIConstants.StandardReturnVal.INVALID_ACCOUNT) {

                thePESAINResponse.setResponseCode(102);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                thePESAINResponse.setResponseDescription("CBS RESPONSE: Transaction received successfully");

            } else if (standardReturnVal.equals(USSDAPIConstants.StandardReturnVal.DUPLICATE)) {
                thePESAINResponse.setResponseCode(102);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                thePESAINResponse.setResponseDescription("CBS RESPONSE: Transaction already received successfully.");
            } else {
                thePESAINResponse.setResponseCode(103);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
                thePESAINResponse.setResponseDescription("CBS RESPONSE: Error processing transaction");
            }*/

            }

        } catch (Exception e) {

            System.err.println("PESAAPI.confirmPESA_IN() ERROR : " + e.getMessage());
            e.printStackTrace();

            thePESAINResponse.setResponseCode(103);
            thePESAINResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
            thePESAINResponse.setResponseDescription("System Exception error confirmPESA_IN(). Transaction NOT Accepted");
        }
    }

    public static void validatePESA_IN(PESA thePESAIN, PESAINResponse thePESAINResponse) {
        String strResponse = "";
        try {

            System.out.println("PESAAPI.validatePESA_IN()");
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getCategory() : " + thePESAIN.getCategory());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getCommand() : " + thePESAIN.getCommand());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getOriginatorID() : " + thePESAIN.getOriginatorID());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getBeneficiaryAccount() : " + thePESAIN.getBeneficiaryAccount());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getBeneficiaryIdentifier() : " + thePESAIN.getBeneficiaryIdentifier());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getBeneficiaryName() : " + thePESAIN.getBeneficiaryName());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getBeneficiaryOtherDetails() : " + thePESAIN.getBeneficiaryOtherDetails());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getCategory() : " + thePESAIN.getCategory());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getCommand() : " + thePESAIN.getCommand());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getOriginatorID() : " + thePESAIN.getOriginatorID());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getReceiverAccount() : " + thePESAIN.getReceiverAccount());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getReceiverIdentifier() : " + thePESAIN.getReceiverIdentifier());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getReceiverName() : " + thePESAIN.getReceiverName());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getReceiverOtherDetails() : " + thePESAIN.getReceiverOtherDetails());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getSourceIdentifier() : " + thePESAIN.getSourceIdentifier());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getSourceName() : " + thePESAIN.getSourceName());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getSourceOtherDetails() : " + thePESAIN.getSourceOtherDetails());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getSourceReference() : " + thePESAIN.getSourceReference());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getSourceType() : " + thePESAIN.getSourceType());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getTransactionAmount() : " + thePESAIN.getTransactionAmount());
            System.out.println("PESAAPI.validatePESA_IN() - thePESAIN.getTransactionCurrency() : " + thePESAIN.getTransactionCurrency());
            System.out.println("Pesa In XML " + thePESAIN.getPESAXMLData());


            thePESAINResponse.setCategory("C2B_VALIDATE");

            String strDestinationReference = UUID.randomUUID().toString().toUpperCase();
            String strSourceType = thePESAIN.getSourceType();
            String strSourceIdentifier = thePESAIN.getSourceIdentifier();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strSourceOnCBS = "Mobile";

            if (strSourceType.equals("NATIONAL_ID")) {
                strSourceOnCBS = "ID";
            } else if (strSourceType.equals("ACCOUNT")) {
                strSourceOnCBS = "ACCOUNT";
            }

            Element elAccountElement = getValidate_PESA_IN_Element(strSourceIdentifier, strSourceOnCBS, doc);

            if (elAccountElement != null) {
                doc.appendChild(elAccountElement);
                String strXMLData = fnTransformXMLDocument(doc);
                thePESAINResponse.setOtherDetails(strXMLData);

                thePESAINResponse.setResponseCode(304);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                thePESAINResponse.setResponseDescription("Account Found - Validation Accepted.");
            } else {
                thePESAINResponse.setOtherDetails("<OTHER_DETAILS/>");

                thePESAINResponse.setResponseCode(104);
                thePESAINResponse.setResponseName(PESAConstants.PESAResponse.FAILED.getValue());

                thePESAINResponse.setResponseDescription("Account NOT Found - Validation NOT Accepted.");
            }
            thePESAINResponse.setBeneficiaryApplication("MBANKING_SERVER");
            thePESAINResponse.setBeneficiaryReference(strDestinationReference);
        } catch (Exception e) {
            System.err.println("PESAAPI.validatePESA_IN() ERROR : " + e.getMessage());

            thePESAINResponse.setResponseCode(103);
            thePESAINResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
            thePESAINResponse.setResponseDescription("System Exception error validatePESA_IN(): " + e.getMessage() + ". Transaction NOT Accepted");
        }
    }

    public static void confirmPESA_OUT(PESA thePESAOUT, RequestPESAResponse theRequestPESAResponse) {
        String strResponse = "";
        try {
            theRequestPESAResponse.setCategory("CONFIRM_B2C");

            //todo - Implement Integration to CBS
            //String strTransactionStatus = Navision.getPort().insertMpesaTransaction(entryCode, transactionID, transaction, description, accountNo, BigDecimal.valueOf(amount), phoneNo, "", "USSD", transactionID, "MBANKING");
            String strTransactionStatus = "SUCCESS";

            String strDestinationReference = UUID.randomUUID().toString().toLowerCase(); //todo: Get this from CBS
            String strSourceType = thePESAOUT.getSourceType();
            String strSourceIdentifier = thePESAOUT.getSourceIdentifier();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strSourceOnCBS = "Mobile";

            if (strSourceType.equals("NATIONAL_ID")) {
                strSourceOnCBS = "ID";
            } else if (strSourceType.equals("ACCOUNT")) {
                strSourceOnCBS = "ACCOUNT";
            }

            Element elPesaOtherDetails = getValidate_PESA_OUT_Element(strSourceIdentifier, strSourceOnCBS, doc);

            if (elPesaOtherDetails != null) {

                String strProviderResponse = "ERROR";
                if (strTransactionStatus.equalsIgnoreCase("SUCCESS") ||
                        strTransactionStatus.equalsIgnoreCase("TRANSACTION_EXISTS")) {

                    /*
                        OTP Amount Expiry Date
                        111222 500 2021-05-05 12:30:00
                        222333 100 2021-05-05 12:45:00
                        333444 800 2021-05-05 13:00:00
                        333444 800 2021-05-05 13:15:00
                     */

                    /*
                    <OTHER_DETAILS>
                        <PESA_OTHER_DETAILS>
                            <PASS_KEY_DETAILS>
                                <PASS_KEY>34567</PASS_KEY>
                            </PASS_KEY_DETAILS>
                            <KYC_DETAILS/>
                            <PROVIDER_OTHER_DETAILS/>
                        </PESA_OTHER_DETAILS>
                    </OTHER_DETAILS>

                    APPROVED - Everything is OK. OTP is Valid & OTP Amount is <= Amount requested & Balance Sufficient
                    INVALID_OTP - OTP is Wrong or OTP Amount > Amount Requested
                    EXPIRED_OTP - OTP has expired (You can use INVALID OTP)
                    INSUFFICIENT_BALANCE - Amount Requested is <= Amount tied to OTP but account does not have enough funds
                    UNKNOWN_IDENTIFIER - The Beneficiary (Mostly MSISDN) is NOT found
                    INVALID_IDENTIFIER_TYPE - The Beneficiary Identifier Type is NOT MSISDN/ACCOUNT_NO/ID_NUMBER
                    ERROR - Error
                     */

                    double lnAmount = thePESAOUT.getTransactionAmount();
                    String strPassKey = MBankingXMLFactory.getXPathValueFromXMLString("/PESA_OTHER_DETAILS/PASS_KEY_DETAILS/PASS_KEY", thePESAOUT.getPESAXMLData());
                    String strBeneficiary = thePESAOUT.getBeneficiaryIdentifier();

                    double lnApprovedAmount = 500;
                    String strApprovedPassKey = "111222";
                    String strApprovedBeneficiary = "254706405989";

                    String strOTPTime = "2021-05-11 15:30:00";
                    String strCurrentDateTime = MBankingDB.getDBDateTime();

                    if ((strOTPTime.compareTo(strCurrentDateTime) >= 0) && (lnAmount <= lnApprovedAmount) && (strPassKey.equals(strApprovedPassKey)) && (strBeneficiary.equals(strApprovedBeneficiary))) {

                        theRequestPESAResponse.setResponseCode(102);
                        theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());

                        strProviderResponse = "APPROVED";
                        theRequestPESAResponse.setResponseDescription(strProviderResponse);

                    } else if (lnAmount > lnApprovedAmount) {

                        theRequestPESAResponse.setResponseCode(103);
                        theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());

                        strProviderResponse = "INSUFFICIENT_BALANCE";
                        theRequestPESAResponse.setResponseDescription(strProviderResponse);
                    } else if (!strPassKey.equals(strApprovedPassKey)) {
                        theRequestPESAResponse.setResponseCode(103);
                        theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());

                        strProviderResponse = "INVALID_OTP";
                        theRequestPESAResponse.setResponseDescription(strProviderResponse);
                    } else if (!strBeneficiary.equals(strApprovedBeneficiary)) {
                        theRequestPESAResponse.setResponseCode(103);
                        theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());

                        strProviderResponse = "UNKNOWN_IDENTIFIER";
                        theRequestPESAResponse.setResponseDescription(strProviderResponse);
                    } else if (strCurrentDateTime.compareTo(strOTPTime) > 0) { //If Current time is past strOTPTime
                        theRequestPESAResponse.setResponseCode(103);
                        theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());

                        strProviderResponse = "EXPIRED_OTP";
                        theRequestPESAResponse.setResponseDescription(strProviderResponse);
                    } else {
                        theRequestPESAResponse.setResponseCode(103);
                        theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());

                        strProviderResponse = "ERROR";
                        theRequestPESAResponse.setResponseDescription(strProviderResponse);
                    }

                } else {
                    theRequestPESAResponse.setResponseCode(103);
                    theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());

                    strProviderResponse = "ERROR";
                    theRequestPESAResponse.setResponseDescription("CBS RESPONSE: " + strTransactionStatus);
                }

                Element elProviderResponse = doc.createElement("PROVIDER_RESPONSE");
                elProviderResponse.setTextContent(strProviderResponse);
                elPesaOtherDetails.appendChild(elProviderResponse);

                doc.appendChild(elPesaOtherDetails);
                String strXMLData = fnTransformXMLDocument(doc);
                theRequestPESAResponse.setOtherDetails(strXMLData);

            } else {
                theRequestPESAResponse.setOtherDetails("<OTHER_DETAILS/>");

                theRequestPESAResponse.setResponseCode(104);
                theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.FAILED.getValue());
                theRequestPESAResponse.setResponseDescription("Account NOT Found - Validation NOT Accepted.");
            }

            theRequestPESAResponse.setBeneficiaryApplication("MBANKING_SERVER");
            theRequestPESAResponse.setBeneficiaryReference(strDestinationReference);

        } catch (Exception e) {
            System.err.println("PESAAPI.confirmPESA_OUT() ERROR : " + e.getMessage());

            theRequestPESAResponse.setResponseCode(103);
            theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
            theRequestPESAResponse.setResponseDescription("System Exception error confirmPESA_IN(): " + e.getMessage() + ". Transaction NOT Accepted");
        }
    }

    public static void validatePESA_OUT(PESA thePESAOUT, RequestPESAResponse theRequestPESAResponse) {
        String strResponse = "";
        try {
            theRequestPESAResponse.setCategory("B2C_VALIDATE");

            String strDestinationReference = UUID.randomUUID().toString().toUpperCase(); //todo: Get this from CBS
            String strSourceType = thePESAOUT.getSourceType();
            String strSourceIdentifier = thePESAOUT.getSourceIdentifier();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            String strSourceOnCBS = "Mobile";

            if (strSourceType.equals("NATIONAL_ID")) {
                strSourceOnCBS = "ID";
            } else if (strSourceType.equals("ACCOUNT")) {
                strSourceOnCBS = "ACCOUNT";
            }

            Element elAccountElement = getValidate_PESA_OUT_Element(strSourceIdentifier, strSourceOnCBS, doc);

            if (elAccountElement != null) {
                doc.appendChild(elAccountElement);
                String strXMLData = fnTransformXMLDocument(doc);
                theRequestPESAResponse.setOtherDetails(strXMLData);

                theRequestPESAResponse.setResponseCode(102);
                theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                theRequestPESAResponse.setResponseDescription("Account Found - Validation Accepted.");
            } else {

                theRequestPESAResponse.setOtherDetails("<OTHER_DETAILS/>");

                theRequestPESAResponse.setResponseCode(104);
                theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.FAILED.getValue());
                theRequestPESAResponse.setResponseDescription("Account NOT Found - Validation NOT Accepted.");
            }

            theRequestPESAResponse.setBeneficiaryApplication("MBANKING_SERVER");
            theRequestPESAResponse.setBeneficiaryReference(strDestinationReference);

        } catch (Exception e) {
            System.err.println("PESAAPI.validatePESA_OUT() ERROR : " + e.getMessage());

            theRequestPESAResponse.setResponseCode(103);
            theRequestPESAResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
            theRequestPESAResponse.setResponseDescription("System Exception error validatePESA_OUT(): " + e.getMessage() + ". Transaction NOT Accepted");
        }
    }

    public static void processPESAResult(PESAResult thePESAResult, PESAResultResponse thePESAResultResponse) {

        try {

            String strPesaType = "";

            String strOriginatorID = thePESAResult.getOriginatorID();

            PESA pesa = PESADB.getPESAByOriginatorID(PESAConstants.PESAType.PESA_OUT, strOriginatorID);

            String strCategory = pesa.getCategory();

            String strDescription = "";
            String strDescriptionPrefix = "";


            switch (strCategory) {
                case "MPESA_WITHDRAWAL":
                    strPesaType = "Mpesa Withdrawal";
                    strDescriptionPrefix = "M-Pesa W/D";
                    break;
                case "AIRTIME_PURCHASE":
                    strPesaType = "Airtime Purchase";
                    strDescriptionPrefix = "Airtime Purchase";
                    break;
                case "BILL_PAYMENT":
                    strPesaType = "Utility Payment";
                    strDescriptionPrefix = "Utility Payment";
                    break;
                case "BANK_TRANSFER":
                    strPesaType = "Bank Transfer";
                    strDescriptionPrefix = "Bank Transfer";
                    break;
                case "PAYBILL_TRANSFER":
                    strPesaType = "Paybill Transfer";
                    strDescriptionPrefix = "Paybill Transfer";
                    break;
                case "TILL_PAYMENT":
                    strPesaType = "Buy Goods and Services";
                    strDescriptionPrefix = "Buy Goods and Services";
                    break;


                default:
                    strPesaType = "Mpesa Withdrawal";
                    strDescriptionPrefix = "M-Pesa W/D";
            }

            String strReceiverType = "MSISDN";
            String strReceiverIdentifier = "";
            String strReceiverAccount = "";
            String strReceiverName = "";


            if (thePESAResult.getResultCode() == PESAConstants.PESAStatusCode.CONFIRMED.getValue()) {
                //Get MPESA Result Name
                if (thePESAResult.getPESAType().equals("PESA_OUT")) {

                    switch (strCategory) {
                        case "MPESA_WITHDRAWAL":
                        case "BILL_PAYMENT":
                        case "BANK_TRANSFER":
                        case "PAYBILL_TRANSFER":
                        case "TILL_PAYMENT":
                            HashMap<String, String> hmPesaResultName = PESAXMLFactory.getPESAResultReceiverDetails(thePESAResult.getOtherDetails());
                            strReceiverType = hmPesaResultName.get("RECEIVER_TYPE");
                            strReceiverIdentifier = hmPesaResultName.get("RECEIVER_IDENTIFIER");
                            strReceiverAccount = hmPesaResultName.get("RECEIVER_ACCOUNT");
                            strReceiverName = hmPesaResultName.get("RECEIVER_NAME");
                            strDescription = strPesaType + " to " + strReceiverName;
                            break;
                        default:
                            strDescription = strDescriptionPrefix;
                    }

                    strDescription = shortenName(strDescription);
                }

                String strFormattedPesaResultDateTime = Utils.formatDate(thePESAResult.getDateCreated(), "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                TransactionWrapper<FlexicoreHashMap> mobileMoneyResultWrapper = CBSAPI.mobileMoneyResult(
                        pesa.getInitiatorIdentifier(),
                        "MSISDN",
                        pesa.getInitiatorIdentifier(),
                        thePESAResult.getOriginatorID(),
                        pesa.getBeneficiaryType(),
                        pesa.getBeneficiaryIdentifier(),
                        pesa.getBeneficiaryName(),
                        pesa.getBeneficiaryOtherDetails(),
                        thePESAResult.getBeneficiaryReference(),
                        thePESAResult.getDateCreated());

                FlexicoreHashMap mobileMoneyResultMap = mobileMoneyResultWrapper.getSingleRecord();

                String strResultTransactionStatus = mobileMoneyResultMap.getStringValue("cbs_api_return_val");
                String cbsResponseMessage = mobileMoneyResultMap.getStringValue("cbs_response_message");

                if (strResultTransactionStatus.equals("SUCCESS")) {
                    thePESAResultResponse.setResponseCode(102);
                    thePESAResultResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                    thePESAResultResponse.setResponseDescription("SUCCESS");
                } else if (strResultTransactionStatus.equals("TRANSACTION_EXISTS")) {
                    thePESAResultResponse.setResponseCode(102);
                    thePESAResultResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                    thePESAResultResponse.setResponseDescription("TRANSACTION_EXISTS");
                } else {
                    thePESAResultResponse.setResponseCode(103);
                    thePESAResultResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
                    thePESAResultResponse.setResponseDescription("CBS RESPONSE: " + cbsResponseMessage);

                    //Status Code 104: FAILED
                }

                if (thePESAResult.getPESAType().equals("PESA_OUT")) {
                    switch (strResultTransactionStatus) {
                        case "SUCCESS", "TRANSACTION_EXISTS" -> {
                            String strMSG = "";

                            String strFormattedDateTime = Utils.formatDate(pesa.getPesaDateCreated(), "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm");
                            strFormattedDateTime = DateTime.convertStringToDateToString(DateTime.getCurrentDateTime(), "yyyy-MM-dd HH:mm:ss.SSSSSS", "dd-MMM-yy' at 'hh:mm aaa");

                            strFormattedDateTime = Utils.formatDate(thePESAResult.getDateCreated(), "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm");
                            String strFormattedAmount = Utils.formatDouble(pesa.getTransactionAmount(), "#,##0.00");

                            switch (strCategory) {
                                case "MPESA_WITHDRAWAL" ->
                                        strMSG = "Dear member, your Cash Withdrawal request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " on " + strFormattedDateTime + " has been completed successfully. M-PESA Ref: " + thePESAResult.getBeneficiaryReference();
                                case "BILL_PAYMENT" ->
                                        strMSG = "Dear member, your Bill Payment request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been completed successfully. M-PESA Ref: " + thePESAResult.getBeneficiaryReference();
                                case "AIRTIME_PURCHASE" ->
                                        strMSG = "Dear member, your Airtime Purchase request of KES " + strFormattedAmount + " for " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been completed successfully.";
                                case "BANK_TRANSFER" ->
                                        strMSG = "Dear member, your Bank Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been completed successfully. M-PESA Ref: " + thePESAResult.getBeneficiaryReference();
                                case "PAYBILL_TRANSFER" ->
                                        strMSG = "Dear member, your Paybill Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been completed successfully. M-PESA Ref: " + thePESAResult.getBeneficiaryReference();
                                case "TILL_PAYMENT" ->
                                        strMSG = "Dear member, your Buy Goods and Services request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been completed successfully. M-PESA Ref: " + thePESAResult.getBeneficiaryReference();
                            }

                            /*fnSendSMS(pesa.getInitiatorIdentifier(),
                                    strMSG, "YES", MSGConstants.MSGMode.SAF, 210, strCategory, pesa.getInitiatorApplication(), "MBANKING_SERVER", pesa.getOriginatorID(), pesa.getSourceReference());*/

                            if (!pesa.getInitiatorIdentifier().equalsIgnoreCase(pesa.getBeneficiaryIdentifier())) {
                                switch (strCategory) {
                                    case "MPESA_WITHDRAWAL" -> {
                                        strMSG = "You have received KES " + strFormattedAmount + " from " + pesa.getInitiatorName().toUpperCase() + " " + pesa.getInitiatorIdentifier() + " on " + strFormattedDateTime + ". M-PESA Ref: " + thePESAResult.getBeneficiaryReference();
                                        fnSendSMS(pesa.getBeneficiaryIdentifier(),
                                                strMSG, "YES", MSGConstants.MSGMode.SAF, 210, strCategory, pesa.getInitiatorApplication(), "MBANKING_SERVER", UUID.randomUUID().toString(), pesa.getSourceReference());
                                    }
                                }
                            }
                        }
                    }
                }

            } else {

                TransactionWrapper<FlexicoreHashMap> reverseMobileMoneyWrapper = CBSAPI.reverseMobileMoneyWithdrawal(
                        pesa.getInitiatorIdentifier(),
                        "MSISDN",
                        pesa.getInitiatorIdentifier(),
                        pesa.getOriginatorID(),
                        pesa.getBeneficiaryType(),
                        pesa.getBeneficiaryIdentifier(),
                        pesa.getBeneficiaryName(),
                        pesa.getBeneficiaryOtherDetails(),
                        "",
                        DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                String strFormattedDateTime = Utils.formatDate(pesa.getPesaDateCreated(), "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm");
                strFormattedDateTime = DateTime.convertStringToDateToString(DateTime.getCurrentDateTime(), "yyyy-MM-dd HH:mm:ss.SSSSSS", "dd-MMM-yy' at 'hh:mm aaa");

                String strFormattedAmount = Utils.formatDouble(pesa.getTransactionAmount(), "#,##0.00");

                if (!reverseMobileMoneyWrapper.hasErrors()) {
                    thePESAResultResponse.setResponseCode(102);
                    thePESAResultResponse.setResponseName(PESAConstants.PESAResponse.SUCCESS.getValue());
                    thePESAResultResponse.setResponseDescription("Reversal Succeeded");

                    String strMSG = "";

                    switch (strCategory) {
                        case "MPESA_WITHDRAWAL" ->
                                strMSG = "Dear member, your Cash Withdrawal request of KES " + strFormattedAmount + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                        case "BILL_PAYMENT" ->
                                strMSG = "Dear member, your Bill Payment request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                        case "AIRTIME_PURCHASE" ->
                                strMSG = "Dear member, your Airtime Purchase request of KES " + strFormattedAmount + " for " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                        case "BANK_TRANSFER" ->
                                strMSG = "Dear member, your Bank Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                        case "PAYBILL_TRANSFER" ->
                                strMSG = "Dear member, your Paybill Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                        case "TILL_PAYMENT" ->
                                strMSG = "Dear member, your Buy Goods and Services request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    }

                    fnSendSMS(pesa.getInitiatorIdentifier(),
                            strMSG, "YES", MSGConstants.MSGMode.SAF, 210, strCategory + "_REVERSAL", pesa.getInitiatorApplication(), "MBANKING_SERVER", pesa.getOriginatorID(), pesa.getSourceReference());

                } else {
                    thePESAResultResponse.setResponseCode(103);
                    thePESAResultResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
                    thePESAResultResponse.setResponseDescription("Transaction reversal failed");

                    String strMSG = "";

                    switch (strCategory) {
                        case "MPESA_WITHDRAWAL" ->
                                strMSG = "Dear member, your Cash Withdrawal request of KES " + strFormattedAmount + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                        case "BILL_PAYMENT" ->
                                strMSG = "Dear member, your Bill Payment request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                        case "AIRTIME_PURCHASE" ->
                                strMSG = "Dear member, your Airtime Purchase request of KES " + strFormattedAmount + " for " + pesa.getBeneficiaryIdentifier() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                        case "BANK_TRANSFER" ->
                                strMSG = "Dear member, your Bank Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                        case "PAYBILL_TRANSFER" ->
                                strMSG = "Dear member, your Paybill Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                        case "TILL_PAYMENT" ->
                                strMSG = "Dear member, your Buy Goods and Services request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";


                    }

                    fnSendSMS(pesa.getInitiatorIdentifier(),
                            strMSG, "YES", MSGConstants.MSGMode.SAF, 210, strCategory + "_REVERSAL", pesa.getInitiatorApplication(), "MBANKING_SERVER", pesa.getOriginatorID(), pesa.getSourceReference());

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("PESAAPI.processPESAResult() ERROR : " + e.getMessage());
            thePESAResultResponse.setResponseCode(103);
            thePESAResultResponse.setResponseName(PESAConstants.PESAResponse.ERROR.getValue());
            thePESAResultResponse.setResponseDescription("System Exception error: " + e.getMessage() + ". Transaction NOT Accepted");
        }
    }

    public static PesaParam getPesaParam(MBankingConstants.ApplicationType theApplicationType, PESAAPIConstants.PESA_PARAM_TYPE thePesaParamType) {
        PesaParam rVal = new PesaParam();
        try {
            String strPesaParamType = "OTHER_DETAILS/CUSTOM_PARAMETERS/PAYMENT_CHANNELS";

            switch (thePesaParamType) {
                case MPESA_B2C: {
                    strPesaParamType += "/SAFARICOM/MPESA_B2C";
                    break;
                }
                case MPESA_C2B: {
                    strPesaParamType += "/SAFARICOM/MPESA_C2B";
                    break;
                }
                case MPESA_B2B: {
                    strPesaParamType += "/SAFARICOM/MPESA_B2B";
                    break;
                }
                case FAMILY_BANK_PESALINK: {
                    strPesaParamType += "/FAMILY_BANK/PESALINK";
                    break;
                }
                case AIRTIME: {
                    strPesaParamType += "/GLOBAL/AIRTIME";
                    break;
                }
            }

            String strProductId = MBankingAPI.getValueFromLocalParams(theApplicationType, strPesaParamType + "/PRODUCT_ID");
            String strSenderIdentifier = MBankingAPI.getValueFromLocalParams(theApplicationType, strPesaParamType + "/SENDER_IDENTIFIER");
            String strSenderAccount = MBankingAPI.getValueFromLocalParams(theApplicationType, strPesaParamType + "/SENDER_ACCOUNT");
            String strSenderName = MBankingAPI.getValueFromLocalParams(theApplicationType, strPesaParamType + "/SENDER_NAME");

            rVal.setProductId(strProductId);
            rVal.setSenderIdentifier(strSenderIdentifier);
            rVal.setSenderAccount(strSenderAccount);
            rVal.setSenderName(strSenderName);
        } catch (Exception e) {
            System.err.println("PESADB.getPesaParam() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    private static String getBeneficiaryDetailsFromDescription(String strXML) {
        String strRval = "";
        try {
            if (!strXML.equals("")) {
                InputSource source = new InputSource(new StringReader(strXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                strRval = configXPath.evaluate("/OTHER_DETAILS/ReceiverName", xmlDocument, XPathConstants.STRING).toString();
            }
        } catch (Exception e) {
            System.err.println("PESAAPI.getBeneficiaryDetailsFromDescription() ERROR : " + e.getMessage());
        }
        return strRval;
    }

    public static String shortenName(String theDescription) {
        StringBuilder rValPre = new StringBuilder();
        StringBuilder rVal = new StringBuilder();
        if (theDescription.length() > 50) {
            for (int i = 0; i < theDescription.split(" ").length - 1; i++) {
                rValPre.append(theDescription.split(" ")[i]);
                rValPre.append(" ");
                if (rValPre.toString().trim().length() < 50) {
                    rVal.append(theDescription.split(" ")[i]);
                    rVal.append(" ");
                } else {
                    break;
                }
            }
        } else {
            return theDescription;
        }

        return rVal.toString().trim();
    }

    public boolean pesa_C2B_Request(String theInitiatorMobileNumber,
                                    String theInitiatorName,
                                    String theInitiatorTraceId,
                                    String theInitiatorApplication,
                                    String theSourceAccount,
                                    String theSourceAccountName,
                                    String theSourceApplication,
                                    String theSourceReference,
                                    String theBeneficiaryMobileNumber,
                                    String theBeneficiaryName,
                                    String theReceiverAccount,
                                    double theAmount,
                                    String theCategory) {

        boolean bRVal = false;

        PESA thePESA = new PESA();

        try {
            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_C2B);

            long lnProductID = Long.parseLong(pesaParam.getProductId());
            String strSender = pesaParam.getSenderIdentifier();
            String strSenderDetails = pesaParam.getSenderName();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strPesaCommand = "CustomerPayBillOnline";
            String strDateTime = MBankingDB.getDBDateTime().trim();

            String strOriginatorID = UUID.randomUUID().toString();

            thePESA.setOriginatorID(strOriginatorID);
            thePESA.setProductID(lnProductID);

            thePESA.setPESAType(PESAConstants.PESAType.PESA_IN);
            thePESA.setPESAAction(PESAConstants.PESAAction.C2B);
            thePESA.setCommand(strPesaCommand);
            thePESA.setSensitivity(PESAConstants.Sensitivity.NORMAL);

            thePESA.setInitiatorType("MSISDN");
            thePESA.setInitiatorIdentifier(theInitiatorMobileNumber);
            thePESA.setInitiatorAccount(theInitiatorMobileNumber);
            thePESA.setInitiatorName(theInitiatorName);
            thePESA.setInitiatorReference(theInitiatorTraceId);
            thePESA.setInitiatorApplication(theInitiatorApplication);
            thePESA.setInitiatorOtherDetails("<DATA/>");

            thePESA.setSourceType("ACCOUNT_NO");
            thePESA.setSourceIdentifier(theSourceAccount);
            thePESA.setSourceAccount(theSourceAccount);
            thePESA.setSourceName(theSourceAccountName);
            thePESA.setSourceReference(theSourceReference);
            thePESA.setSourceApplication(theSourceApplication);
            thePESA.setSourceOtherDetails("<DATA/>");

            thePESA.setSenderType("SHORT_CODE");
            thePESA.setSenderIdentifier(strSender);
            thePESA.setSenderAccount(strSender);
            thePESA.setSenderName(strSenderDetails);
            thePESA.setSenderOtherDetails("<DATA/>");

            thePESA.setReceiverType("SHORT_CODE");
            thePESA.setReceiverIdentifier(strSender);
            thePESA.setReceiverAccount(theReceiverAccount);
            thePESA.setReceiverName(strSenderDetails);
            thePESA.setReceiverOtherDetails("<DATA/>");

            thePESA.setBeneficiaryType("MSISDN");
            thePESA.setBeneficiaryIdentifier(theBeneficiaryMobileNumber);
            thePESA.setBeneficiaryAccount(theBeneficiaryMobileNumber);
            thePESA.setBeneficiaryName(theBeneficiaryName);
            thePESA.setBeneficiaryOtherDetails("<DATA/>");

            thePESA.setBatchReference(strOriginatorID);
            thePESA.setCorrelationReference(theInitiatorTraceId);
            thePESA.setCorrelationApplication(theInitiatorApplication);
            thePESA.setTransactionCurrency("KES");
            thePESA.setTransactionAmount(theAmount);
            thePESA.setTransactionRemark("C2B Payment Request by " + strSenderDetails + " to " + theBeneficiaryMobileNumber);
            thePESA.setCategory(theCategory);

            thePESA.setPriority(200);
            thePESA.setSendCount(0);

            thePESA.setSchedulePesa(PESAConstants.Condition.NO);
            thePESA.setPesaDateScheduled(strDateTime);
            thePESA.setPesaDateCreated(strDateTime);
            thePESA.setPESAXMLData("<DATA/>");

            System.out.println("\n\n*******************************************************");
            System.out.println("            DETAILS FROM processPESA_IN()");
            System.out.println("*******************************************************");
            System.out.println("Originator ID                  :" + thePESA.getOriginatorID() + "|");
            System.out.println("PESA ID                        :" + thePESA.getPESAID() + "|");
            System.out.println("Server ID                      :" + thePESA.getServerID() + "|");
            System.out.println("Product ID                     :" + thePESA.getProductID() + "|");
            System.out.println("PESA Type                      :" + thePESA.getPESAType().toString() + "|");
            System.out.println("PESA Action                    :" + thePESA.getPESAAction().toString() + "|");

            System.out.println("Initiator Type                 :" + thePESA.getInitiatorType() + "|");
            System.out.println("Initiator Identifier           :" + thePESA.getInitiatorIdentifier() + "|");
            System.out.println("Initiator Account              :" + thePESA.getInitiatorAccount() + "|");
            System.out.println("Initiator Name                 :" + thePESA.getInitiatorName() + "|");
            System.out.println("Initiator Reference            :" + thePESA.getInitiatorReference() + "|");
            System.out.println("Initiator Application          :" + thePESA.getInitiatorApplication() + "|");
            System.out.println("Initiator Other Details        :" + thePESA.getInitiatorOtherDetails() + "|");

            System.out.println("Source Type                    :" + thePESA.getSourceType() + "|");
            System.out.println("Source Identifier              :" + thePESA.getSourceIdentifier() + "|");
            System.out.println("Source Account                 :" + thePESA.getSourceAccount() + "|");
            System.out.println("Source Name                    :" + thePESA.getSourceName() + "|");
            System.out.println("Source Reference               :" + thePESA.getSourceReference() + "|");
            System.out.println("Source Application             :" + thePESA.getSourceApplication() + "|");
            System.out.println("Source Other Details           :" + thePESA.getSourceOtherDetails() + "|");

            System.out.println("Sender Type                    :" + thePESA.getSenderType() + "|");
            System.out.println("Sender Identifier              :" + thePESA.getSenderIdentifier() + "|");
            System.out.println("Sender Account                 :" + thePESA.getSenderAccount() + "|");
            System.out.println("Sender Name                    :" + thePESA.getSenderName() + "|");
            System.out.println("Sender Other Details           :" + thePESA.getSenderOtherDetails() + "|");
            System.out.println("Receiver Type                  :" + thePESA.getReceiverType() + "|");
            System.out.println("Receiver Identifier            :" + thePESA.getReceiverIdentifier() + "|");
            System.out.println("Receiver Account               :" + thePESA.getReceiverAccount() + "|");
            System.out.println("Receiver Name                  :" + thePESA.getReceiverName() + "|");
            System.out.println("Receiver Other Details         :" + thePESA.getReceiverOtherDetails() + "|");
            System.out.println("Beneficiary Type               :" + thePESA.getBeneficiaryType() + "|");
            System.out.println("Beneficiary Identifier         :" + thePESA.getBeneficiaryIdentifier() + "|");
            System.out.println("Beneficiary Account            :" + thePESA.getBeneficiaryAccount() + "|");
            System.out.println("Beneficiary Name               :" + thePESA.getBeneficiaryName() + "|");
            System.out.println("Beneficiary Other Details      :" + thePESA.getBeneficiaryOtherDetails() + "|");

            System.out.println("Batch Reference                :" + thePESA.getBatchReference() + "|");
            System.out.println("Correlation Reference          :" + thePESA.getCorrelationReference() + "|");
            System.out.println("Correlation Application        :" + thePESA.getCorrelationApplication() + "|");

            System.out.println("Transaction Currency           :" + thePESA.getTransactionCurrency() + "|");
            System.out.println("Transaction Amount             :" + thePESA.getTransactionAmount() + "|");
            System.out.println("Transaction Remark             :" + thePESA.getTransactionRemark() + "|");

            System.out.println("Command                        :" + thePESA.getCommand() + "|");
            System.out.println("Sensitivity                    :" + thePESA.getSensitivity() + "|");
            System.out.println("Category                       :" + thePESA.getCategory() + "|");
            System.out.println("Priority                       :" + thePESA.getPriority() + "|");
            System.out.println("Send Count                     :" + thePESA.getSendCount() + "|");
            System.out.println("PESA XML Data                  :" + thePESA.getPESAXMLData() + "|");
            //System.out.println("Send Integrity Hash            :" + thePESA.getSendIntegrityHash()+"|);
            System.out.println("Schedule Pesa                  :" + thePESA.getSchedulePesa() + "|");
            System.out.println("Date Scheduled                 :" + thePESA.getPesaDateScheduled() + "|");
            System.out.println("General Flag                   :" + thePESA.getGeneralFlag() + "|");
            System.out.println("Transaction Date               :" + thePESA.getPesaDateCreated() + "|");
            System.out.println("\n\n*******************************************************");
            System.out.println("            DETAILS FROM processPESA_IN()");
            System.out.println("*******************************************************");

            //bRVal = PESAProcessor.sendC2BPaymentRequest(thePESA);

            PESAINRequestResponse thePESAINRequestResponse = PESAProcessor.sendPESAINPaymentRequest(thePESA);

            if (thePESAINRequestResponse.getResponseCode() == 500 || thePESAINRequestResponse.getResponseCode() == 102) {
                bRVal = true;
            }

            System.out.println("thePESAINRequestResponse.getResponseCode() : " + thePESAINRequestResponse.getResponseCode());
            System.out.println("thePESAINRequestResponse.getResponseName() : " + thePESAINRequestResponse.getResponseName());
            System.out.println("thePESAINRequestResponse.getResponseDescription(): " + thePESAINRequestResponse.getResponseDescription());
        } catch (Exception e) {
            System.err.println("PESAAPI.pesa_C2B_Request() ERROR : " + e.getMessage());
        }

        return bRVal;
    }

    public static String fnTransformXMLDocument(Document xmlDocument) {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();

            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(xmlDocument), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Element getValidate_PESA_IN_Element(String theAccount, String theSource, Document doc) {
        try {
            //todo - Implement Integration to CBS
            //String strAccountNumberXML = Navision.getPort().getAccountTransferRecipientXML(theAccount, theSource);
            String strAccountNumberXML = "<Account><AccountNo>5000000800000</AccountNo><AccountName>JAMES BOND</AccountName><Name>JAMES BOND</Name><MemberNo>0000800</MemberNo><PhoneNo>+254706405989</PhoneNo></Account>";
            /*
            <Account>
                <AccountNo>5000000800000</AccountNo>
                <AccountName>JAMES BOND</AccountName>
                <Name>JAMES BOND</Name>
                <MemberNo>0000800</MemberNo>
                <PhoneNo>+254706405989</PhoneNo>
            </Account>
             */

            /*
            <OTHER_DETAILS>
                <PESA_OTHER_DETAILS>
                    <KYC_DETAILS>
                        <RESPONSE>
                            <KYC TYPE="ACCOUNT_NO">
                                <IDENTIFIER>10101010101</IDENTIFIER>
                                <ACCOUNT>10101010101</ACCOUNT>
                                <NAME>Peter Jones</NAME>
                                <OTHER_DETAILS>DETAILS</OTHER_DETAILS>
                            </KYC>
                        </RESPONSE>
                    </KYC_DETAILS>
                </PESA_OTHER_DETAILS>
            </OTHER_DETAILS>
             */


            Element elPesaOtherDetails = null;

            String strAccountNo = "";
            String strAccountType = "";
            String strAccountName = "";
            String strAccountMemberNo = "";
            String strPhoneNo = "";
            String strAccountStatus = "NOT_FOUND";

            if (!strAccountNumberXML.equals("")) {
                InputSource source = new InputSource(new StringReader(strAccountNumberXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                strAccountNo = configXPath.evaluate("Account/AccountNo", xmlDocument, XPathConstants.STRING).toString();
                strAccountType = configXPath.evaluate("Account/AccountName", xmlDocument, XPathConstants.STRING).toString();
                strAccountName = configXPath.evaluate("Account/Name", xmlDocument, XPathConstants.STRING).toString();
                strAccountMemberNo = configXPath.evaluate("Account/MemberNo", xmlDocument, XPathConstants.STRING).toString();
                strPhoneNo = configXPath.evaluate("Account/PhoneNo", xmlDocument, XPathConstants.STRING).toString();
                strAccountName = Utils.toTitleCase(strAccountName);
                strAccountStatus = "FOUND";

                String strBeneficiaryType = "";
                if (theSource.equals("Mobile")) {
                    strBeneficiaryType = "MSISDN";
                } else if (theSource.equals("ID")) {
                    strBeneficiaryType = "NATIONAL_ID";
                }

                elPesaOtherDetails = doc.createElement("PESA_OTHER_DETAILS");

                Element elKYCDetails = doc.createElement("KYC_DETAILS");
                elPesaOtherDetails.appendChild(elKYCDetails);

                Element elKYCResponse = doc.createElement("RESPONSE");
                elKYCDetails.appendChild(elKYCResponse);

                Element elKYC = doc.createElement("KYC");
                elKYC.setAttribute("TYPE", strBeneficiaryType);
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
            }
            return elPesaOtherDetails;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Element getValidate_PESA_OUT_Element(String theAccount, String theSource, Document doc) {
        try {
            //todo - Implement Integration to CBS
            //String strAccountNumberXML = Navision.getPort().getAccountTransferRecipientXML(theAccount, theSource);
            String strAccountNumberXML = "<Account><AccountNo>5000000800000</AccountNo><AccountName>JAMES BOND</AccountName><Name>JAMES BOND</Name><MemberNo>0000800</MemberNo><PhoneNo>+254706405989</PhoneNo></Account>";
            /*
            <Account>
                <AccountNo>5000000800000</AccountNo>
                <AccountName>JAMES BOND</AccountName>
                <Name>JAMES BOND</Name>
                <MemberNo>0000800</MemberNo>
                <PhoneNo>+254706405989</PhoneNo>
            </Account>
             */

            /*

            <OTHER_DETAILS>
                <PESA_OTHER_DETAILS>
                    <KYC_DETAILS>
                        <RESPONSE>
                            <KYC TYPE="MSISDN">
                                <IDENTIFIER>254720000000</IDENTIFIER>
                                <ACCOUNT>254720000000</ACCOUNT>
                                <NAME>Peter Jones</NAME>
                                <OTHER_DETAILS/>
                            </KYC>
                            <KYC TYPE="NATIONAL_ID">
                                <IDENTIFIER>1232131131</IDENTIFIER>
                                <NAME>Peter Jones</NAME>
                                <OTHER_DETAILS/>
                            </KYC>
                            <KYC TYPE="ACCOUNT_NO">
                                <IDENTIFIER>10101010101</IDENTIFIER>
                                <ACCOUNT>10101010101</ACCOUNT>
                                <NAME>Peter Jones</NAME>
                                <OTHER_DETAILS/>
                            </KYC>
                        </RESPONSE>
                    </KYC_DETAILS>
                </PESA_OTHER_DETAILS>
            </OTHER_DETAILS>
             */


            Element elPesaOtherDetails = null;

            String strAccountNo = "";
            String strAccountType = "";
            String strAccountName = "";
            String strAccountMemberNo = "";
            String strPhoneNo = "";
            String strAccountStatus = "NOT_FOUND";

            if (!strAccountNumberXML.equals("")) {
                InputSource source = new InputSource(new StringReader(strAccountNumberXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                strAccountNo = configXPath.evaluate("Account/AccountNo", xmlDocument, XPathConstants.STRING).toString();
                strAccountType = configXPath.evaluate("Account/AccountName", xmlDocument, XPathConstants.STRING).toString();
                strAccountName = configXPath.evaluate("Account/Name", xmlDocument, XPathConstants.STRING).toString();
                strAccountMemberNo = configXPath.evaluate("Account/MemberNo", xmlDocument, XPathConstants.STRING).toString();
                strPhoneNo = configXPath.evaluate("Account/PhoneNo", xmlDocument, XPathConstants.STRING).toString();
                strAccountName = Utils.toTitleCase(strAccountName);
                strAccountStatus = "FOUND";

                String strBeneficiaryType = "";
                if (theSource.equals("Mobile")) {
                    strBeneficiaryType = "MSISDN";
                } else if (theSource.equals("ID")) {
                    strBeneficiaryType = "NATIONAL_ID";
                }

                elPesaOtherDetails = doc.createElement("PESA_OTHER_DETAILS");

                Element elKYCDetails = doc.createElement("KYC_DETAILS");
                elPesaOtherDetails.appendChild(elKYCDetails);

                Element elKYCResponse = doc.createElement("RESPONSE");
                elKYCDetails.appendChild(elKYCResponse);

                Element elKYC = doc.createElement("KYC");
                elKYC.setAttribute("TYPE", strBeneficiaryType);
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
            }
            return elPesaOtherDetails;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
