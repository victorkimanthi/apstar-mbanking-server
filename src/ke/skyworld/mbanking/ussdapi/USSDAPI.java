package ke.skyworld.mbanking.ussdapi;

import ke.co.skyworld.smp.query_manager.SystemTables;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.core.MBankingUtils;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.pesa.PESA;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.lib.mbanking.pesa.PESAProcessor;
import ke.skyworld.lib.mbanking.ussd.USSDLocalParameters;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.cbs.ChannelService;
import ke.skyworld.mbanking.mappapi.MAPPAPIDB;
import ke.skyworld.mbanking.mbankingapi.MBankingAPI;
import ke.skyworld.mbanking.nav.NavisionAgency;
import ke.skyworld.mbanking.pesaapi.*;
import ke.skyworld.mbanking.ussdapplication.AppConstants;
import ke.skyworld.mbanking.ussdapplication.GeneralMenus;
import ke.skyworld.sp.manager.SPManagerConstants;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

import static ke.skyworld.mbanking.ussdapi.APIUtils.*;
import static ke.skyworld.mbanking.ussdapplication.AppConstants.strAppID;

public class USSDAPI {

    private static String strTrailerMessage = "\n\nQueries? Please visit one of our branches or contact us for assistance.";

    public static String getTrailerMessage() {
        String strTrailerMessage;
        try {
            String strTrailerMessageXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strTrailerMessageXML);

            String strNewlines = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/TRAILER_MESSAGE/@NEWLINES");
            int intNewLines = Integer.parseInt(strNewlines);

            strTrailerMessage = "";
            for (int i = 0; i < intNewLines; i++) {
                strTrailerMessage = strTrailerMessage + "\n";
            }

            strTrailerMessage += XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/TRAILER_MESSAGE");

        } catch (Exception e) {
            e.printStackTrace();
            strTrailerMessage = "\n\nQueries? Please visit one of our branches or contact us for assistance.";
        }

        return strTrailerMessage;
    }

    public USSDAPI() {
    }

    public TransactionWrapper<FlexicoreHashMap> checkUser(USSDRequest theUSSDRequest) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());


            return CBSAPI.checkUser(strReferenceKey, "MSISDN", strMobileNumber, "IMSI", strSIMID);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> getCurrentUserDetails(USSDRequest theUSSDRequest) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.getCurrentUserDetails(strReferenceKey, "MSISDN", strMobileNumber, "IMSI", strSIMID);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> userLogin(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());


            return CBSAPI.userLogin(strReferenceKey, "MSISDN", strMobileNumber, strPIN, "IMSI", strSIMID, USSDAPIConstants.MobileChannel.USSD);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> setPIN(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());
            String strNewPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SET_PIN_CONFIRM_PIN.name());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.setUserPIN(strReferenceKey, "MSISDN", strMobileNumber, strNewPIN, "IMSI", strSIMID);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> changeUserPIN(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.CHANGE_PIN_CURRENT_PIN.name());
            String strNewPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.CHANGE_PIN_NEW_PIN.name());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.changeUserPIN(strReferenceKey, "MSISDN", strMobileNumber, strPIN, strNewPIN, "IMSI", strSIMID, USSDAPIConstants.MobileChannel.USSD);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> isCorrectPIN(USSDRequest theUSSDRequest, String thePIN) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());


            return CBSAPI.isCorrectPIN(strReferenceKey, "MSISDN", strMobileNumber, thePIN, "IMSI", strSIMID, USSDAPIConstants.MobileChannel.USSD);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> isValidKYCDetails(USSDRequest theUSSDRequest, String thePrimaryIdentityNo) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.isValidKYCDetails(strReferenceKey, "MSISDN", strMobileNumber, "IMSI", strSIMID, "NATIONAL_ID", thePrimaryIdentityNo, USSDAPIConstants.MobileChannel.USSD);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> acceptTermsAndConditions(USSDRequest theUSSDRequest, FlexicoreHashMap mobileBankingMap) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            return CBSAPI.acceptTermsAndConditions(mobileBankingMap, USSDAPIConstants.MobileChannel.USSD);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> getSignatoryCustomersList(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.getSignatoryCustomersList(strReferenceKey, "MSISDN", strMobileNumber, "IMSI", strSIMID);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> getCustomerAccounts(USSDRequest theUSSDRequest, String theIdentifierType, String theIdentifier, String theAccountType) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.getCustomerAccounts(strMobileNumber, theIdentifierType, theIdentifier, theAccountType);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    /*public TransactionWrapper<FlexicoreHashMap> accountBalanceEnquirySINGLE(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountName = hmFromAccountNoDetails.get("ac_name");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.accountBalanceEnquiry(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strAccountNumber);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

            sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "BALANCE_ENQUIRY", theUSSDRequest);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue());

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(accountBalanceMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Balance Enquiry Completed Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNumber);
            channelService.setSourceAccount(strAccountNumber);
            channelService.setSourceName(strAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Balance Enquiry for A/C: "+strAccountName);
            ChannelService.insertService(channelService);

            return accountBalanceEnquiryWrapper;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "BALANCE_ENQUIRY", theUSSDRequest);

        }

        return resultWrapper;
    }*/

    public TransactionWrapper<FlexicoreHashMap> accountsBalanceEnquiry(USSDRequest theUSSDRequest, String theAccountType) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.accountBalanceEnquiry(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, theAccountType);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

            sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "BALANCE_ENQUIRY", theUSSDRequest);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue());

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(accountBalanceMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Balance Enquiry Completed Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(theAccountType); //BULK ACCOUNTS, SO JUST PUT HERE THE TYPE INSTEAD
            channelService.setSourceAccount(theAccountType);
            channelService.setSourceName(theAccountType);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Balance Enquiry for A/C: " + theAccountType);
            ChannelService.insertService(channelService);

            return accountBalanceEnquiryWrapper;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "BALANCE_ENQUIRY", theUSSDRequest);

        }

        return resultWrapper;
    }


    public TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiry(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountName = hmFromAccountNoDetails.get("ac_name");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());
            String strUSSDSessionID = fnModifyUSSDSessionID(theUSSDRequest);

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.accountBalanceEnquirySINGLE(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strAccountNumber, strUSSDSessionID);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

            sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "BALANCE_ENQUIRY", theUSSDRequest);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue());

            if (accountBalanceEnquiryWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(accountBalanceMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Balance Enquiry Completed Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNumber);
            channelService.setSourceAccount(strAccountNumber);
            channelService.setSourceName(strAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Balance Enquiry for A/C: " + strAccountName);
            ChannelService.insertService(channelService);

            return accountBalanceEnquiryWrapper;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "BALANCE_ENQUIRY", theUSSDRequest);

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> accountMiniStatement(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountName = hmFromAccountNoDetails.get("ac_name");

            String strTrailerMessageXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strTrailerMessageXML);

            String strNumberOfEntries = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/USSD_MINI_STATEMENT_ENTRIES");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountMiniStatement(strMobileNumber, "MSISDN", strMobileNumber,
                    "IMSI", strSIMID, strAccountNumber, strNumberOfEntries);

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = miniStatementMap.getValue("msg_object");

            String strSMS = "";
            int nLength = cbsMSG.getMessage().length();

            if (nLength > 950) {
                strSMS = cbsMSG.getMessage().substring(0, 950);
            } else {
                strSMS = cbsMSG.getMessage();
            }

            sendSMS(strMobileNumber, strSMS, cbsMSG.getMode(), cbsMSG.getPriority(), "MINI_STATEMENT", theUSSDRequest);


            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.ACCOUNT_MINI_STATEMENT.getValue());

            if (miniStatementWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(miniStatementMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Balance Enquiry Completed Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNumber);
            channelService.setSourceAccount(strAccountNumber);
            channelService.setSourceName(strAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.ACCOUNT_MINI_STATEMENT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Account Mini-Statement for A/C: " + strAccountName);
            ChannelService.insertService(channelService);

            return miniStatementWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Mini Statement request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Mini Statement request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "MINI_STATEMENT", theUSSDRequest);

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> getCustomerDetailsFromCBS(USSDRequest theUSSDRequest, String theCustomerIdentifierType, String theCustomerIdentifier) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.getCustomerDetailsFromCBS(strMobileNumber, theCustomerIdentifierType, theCustomerIdentifier);
            //return new TransactionWrapper<>();

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> internalFundsTransfer(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        String strCategory = "INTERNAL_FUNDS_TRANSFER";


        try {

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();


            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
            String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
            String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

            String strToAccountNumber = "";
            String strToAccountName = "";

            String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OPTION.name());

            if (strOption.equalsIgnoreCase("OTHER_ACCOUNT")) {
                strToAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT.name());

                String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());

                if (strToOption.equalsIgnoreCase("Account Number")) {
                    strToAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT.name());
                    strToAccountName = strToAccountNumber;
                } else {
                    HashMap<String, String> hmToAccountNoDetails = Utils.toHashMap(strToAccountNumber);
                    strToAccountNumber = hmToAccountNoDetails.get("ac_no");
                    strToAccountName = hmToAccountNoDetails.get("ac_name");
                }

            } else {

                String strToIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER.name());
                String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT.name());

                HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);
                strToAccountNumber = hmToAccountDetails.get("ac_no");
                strToAccountName = hmToAccountDetails.get("ac_name");
            }

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT.name());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            String strOriginatorId = UUID.randomUUID().toString();
            String strTransactionDescription = "Source A/C: " + strFromAccountNumber + " - Destination A/C: " + strToAccountNumber;
            TransactionWrapper<FlexicoreHashMap> internalFundsTransferWrapper = CBSAPI.internalFundsTransfer(
                    strMobileNumber,
                    "MSISDN",
                    strMobileNumber,
                    "IMSI",
                    strSIMID,
                    strOriginatorId,
                    strFromAccountNumber,
                    strToAccountNumber,
                    Double.parseDouble(strAmount),
                    strTransactionDescription,
                    theUSSDRequest.getUSSDTraceID().substring(0, 10),
                    "USSD",
                    "MBANKING");

            FlexicoreHashMap internalFundsTransferMap = internalFundsTransferWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = internalFundsTransferMap.getValue("msg_object");

            sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory("INTERNAL_FUNDS_TRANSFER");

            if (internalFundsTransferWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(internalFundsTransferMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Transaction Completed Successfully");
                channelService.setBeneficiaryReference(internalFundsTransferMap.getStringValue("cbs_transaction_reference"));
                channelService.setSourceReference(internalFundsTransferMap.getStringValue("cbs_transaction_reference"));
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strFromAccountNumber);
            channelService.setSourceAccount(strFromAccountNumber);
            channelService.setSourceName(strFromAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("ACCOUNT_NO");
            channelService.setBeneficiaryIdentifier(strToAccountNumber);
            channelService.setBeneficiaryAccount(strToAccountNumber);
            channelService.setBeneficiaryName(strToAccountName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(Double.parseDouble(strAmount));

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.IFT_ACCOUNT_TO_ACCOUNT.getValue(),
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

            return internalFundsTransferWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> validateFundsTransferAccount(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT.name());
            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.validateAccountNumber(strMobileNumber, strReferenceKey, strMobileNumber, strAccountNumber);

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> mobileMoneyWithdrawal(USSDRequest theUSSDRequest) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "MPESA_WITHDRAWAL";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {
            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strMobileNumberTo = strMobileNumberFrom.trim();

            // String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            String strTraceID = theUSSDRequest.getUSSDTraceID();

            String strMemberName = getUserFullName(strMobileNumber).trim();

            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            int intPriority = 200;

            String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());
            String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO_OPTION.name());

            APIUtils.WithdrawalChannel withdrawalChannel = APIUtils.getWithdrawalChannel(strOption);
            if (withdrawalChannel != null) {
                if (withdrawalChannel.hasWithdrawalToOtherNumberEnabled()) {
                    if (strToOption != null) {
                        if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                            strMobileNumberTo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO.name());
                        }
                    }
                }
            }

            strMobileNumberTo = APIUtils.sanitizePhoneNumber(strMobileNumberTo);

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");


            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_AMOUNT.name());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_PIN.name());

            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2C);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName().trim();
            String strTransactionDescription = "CW|M-Pesa|" + strMobileNumber + " - " + strMemberName;
            strTransactionDescription = PESAAPI.shortenName(strTransactionDescription);
            strTransactionDescription = strTransactionDescription.trim();

            String strSourceName = strSourceAccountName;
            String strReceiverName = strSourceAccountName.trim();
            String strBeneficiaryName = strSourceAccountName;

            if (strToOption != null) {
                if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                    strSourceName = strMobileNumberTo;
                    strReceiverName = strMobileNumberTo;
                    strBeneficiaryName = strMobileNumberTo;
                    strTransactionDescription = "CW|M-Pesa|" + strMobileNumber + " - " + strMemberName + " to " + strMobileNumberTo;

                }
            }

            PESA pesa = new PESA();

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
            pesa.setPESAStatusDate(strDateTime);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
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

            pesa.setReceiverType("MSISDN");
            pesa.setReceiverIdentifier(strMobileNumberTo);
            pesa.setReceiverAccount(strMobileNumberTo);
            pesa.setReceiverName(strReceiverName);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumberTo);
            pesa.setBeneficiaryAccount(strMobileNumberTo);
            pesa.setBeneficiaryName(strReceiverName);
            pesa.setBeneficiaryOtherDetails("<DATA/>");


            if (strToOption != null) {
                if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                    pesa.setReceiverName(strMobileNumberTo);
                    pesa.setBeneficiaryName(strMobileNumberTo);
                } else {
                    pesa.setReceiverName(strMemberName);
                    pesa.setBeneficiaryName(strMemberName);
                }
            } else {
                pesa.setReceiverName(strMemberName);
                pesa.setBeneficiaryName(strMemberName);
            }

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));
            pesa.setTransactionRemark(strTransactionDescription);
            //pesa.setTransactionRemark("CW|M-Pesa" + strMobileNumber + " - " + strMemberName + " to " + strMobileNumberTo);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setPESAXMLData("<DATA/>");

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);

            TransactionWrapper<FlexicoreHashMap> mobileMoneyWithdrawalWrapper = CBSAPI.mobileMoneyWithdrawal(
                    strMobileNumber,
                    "MSISDN",
                    strMobileNumber,
                    "IMSI",
                    strSIMID,
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
                    "USSD",
                    "MBANKING");

            FlexicoreHashMap mobileMoneyWithdrawalMap = mobileMoneyWithdrawalWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG;
            if (mobileMoneyWithdrawalMap.getValue("msg_object") instanceof CBSAPI.SMSMSG) {
                cbsMSG = mobileMoneyWithdrawalMap.getValue("msg_object");
            } else {
                cbsMSG = new CBSAPI.SMSMSG();
                cbsMSG.setMessage(String.valueOf(mobileMoneyWithdrawalMap.get("msg_object")));
            }

            if (mobileMoneyWithdrawalWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = mobileMoneyWithdrawalMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    TransactionWrapper<FlexicoreHashMap> reversalCashWithdrawalWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reversalCashWithdrawalWrapper.hasErrors()) {
                        strMSG = "Dear member, your M-PESA Withdrawal of KES " + strFormattedAmount + " to " + strMobileNumberTo + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";
                    } else {
                        strMSG = "Dear member, your M-PESA Withdrawal of KES " + strFormattedAmount + " to " + strMobileNumberTo + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reversalCashWithdrawalWrapper.getSingleRecord().putValue("display_message", strMSG);

                    return reversalCashWithdrawalWrapper;
                }
            }

            mobileMoneyWithdrawalWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return mobileMoneyWithdrawalWrapper;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Cash Withdrawal request. Please try again later." + getTrailerMessage()))
            ;

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());



			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Cash Withdrawal request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> airtimePurchaseEtopupModel(USSDRequest theUSSDRequest) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "AIRTIME_PURCHASE";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {

            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO_OPTION.name());

            String strMobileNoTo = strMobileNumberFrom;
            if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                strMobileNoTo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO.name());
            }

            String strMemberName = getUserFullName(strMobileNumber).trim();

            //String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            String strTraceID = theUSSDRequest.getUSSDTraceID();
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            int intPriority = 200;

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_ACCOUNT.name());
            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_AMOUNT.name());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_PIN.name());

            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.AIRTIME);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();

            PESA pesa = new PESA();

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
            pesa.setPESAStatusDate(strDateTime);


            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
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
            pesa.setReceiverIdentifier(strMobileNumberFrom);
            pesa.setReceiverAccount(strMobileNumberFrom);
            pesa.setReceiverName(strMemberName);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumberFrom);
            pesa.setBeneficiaryAccount(strMobileNumberFrom);
            pesa.setBeneficiaryName(strMemberName);
            pesa.setBeneficiaryOtherDetails("<DATA/>");

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));
            pesa.setTransactionRemark("Airtime Purchase by " + strMobileNumberFrom);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);
            pesa.setPESAXMLData("<DATA/>");

            TransactionWrapper<FlexicoreHashMap> buyAirtimeWrapper =
                    CBSAPI.buyAirtime(
                            strMobileNumber,
                            "MSISDN",
                            strMobileNumber,
                            "IMSI",
                            strSIMID,
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
                            "USSD",
                            "MBANKING");


            FlexicoreHashMap buyAirtimeMap = buyAirtimeWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = buyAirtimeMap.getValue("msg_object");

            if (buyAirtimeWrapper.hasErrors()) {
                // sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = buyAirtimeMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reverseBuyAirtimeWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reverseBuyAirtimeWrapper.hasErrors()) {
                        strMSG = "Dear member, your Airtime Purchase of KES " + strFormattedAmount + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Airtime Purchase of KES " + strFormattedAmount + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reverseBuyAirtimeWrapper.getSingleRecord().putValue("display_message", strMSG);

                    return reverseBuyAirtimeWrapper;
                }
            }

            buyAirtimeWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return buyAirtimeWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Airtime Purchase request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Airtime Purchase request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> airtimePurchase(USSDRequest theUSSDRequest) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "AIRTIME_PURCHASE";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {

            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strMobileNumberTo = strMobileNumberFrom;

            String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO_OPTION.name());

            String strMobileNoTo = strMobileNumberFrom;
            if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                strMobileNoTo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO.name());
            }

            String strMNOOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_MNO_OPTION.name());
            switch (strMNOOption) {
                case "SAFARICOM" -> strMobileNoTo = "SAF" + strMobileNoTo;
                case "AIRTEL" -> strMobileNoTo = "AIRT" + strMobileNoTo;
                case "TELKOM" -> strMobileNoTo = "TELK" + strMobileNoTo;
            }

            String strMemberName = getUserFullName(strMobileNumber).trim();
            String strTraceID = theUSSDRequest.getUSSDTraceID();

            //String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            int intPriority = 200;

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_ACCOUNT.name());
            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");


			/*HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
			String strSourceAccountNo = hmAccountDetails.get("number");
			String strSourceAccountName = hmAccountDetails.get("name");
			String strSourceAccountTypeName = hmAccountDetails.get("type_name");
			String strSourceAccountLabel = hmAccountDetails.get("label");*/

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_AMOUNT.name());

            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.AIRTIME);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();

            PESA pesa = new PESA();

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
            pesa.setPESAStatusDate(strDateTime);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
            pesa.setInitiatorOtherDetails("<DATA/>");

            pesa.setSourceType("ACCOUNT_NO");
            pesa.setSourceIdentifier(strSourceAccountNo);
            pesa.setSourceAccount(strSourceAccountNo);
            pesa.setSourceName(strSourceAccountName);
            //deferred to after CBS call below
            //pesa.setSourceReference();
            pesa.setSourceApplication("CBS");
            pesa.setSourceOtherDetails("<DATA/>");

            pesa.setSenderType("SHORT_CODE");
            pesa.setSenderIdentifier(strSenderIdentifier);
            pesa.setSenderAccount(strSenderAccount);
            pesa.setSenderName(strSenderName);
            pesa.setSenderOtherDetails("<DATA/>");

            pesa.setReceiverType("MSISDN");
            pesa.setReceiverIdentifier(strMobileNoTo);
            pesa.setReceiverAccount(strMobileNoTo);
            pesa.setReceiverName(strMobileNumberTo);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumberTo);
            pesa.setBeneficiaryAccount(strMobileNumberTo);
            pesa.setBeneficiaryName(strMobileNumberTo);
            pesa.setBeneficiaryOtherDetails("<DATA/>");

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));
            pesa.setTransactionRemark("Airtime Purchase by " + strMobileNumber);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);
            pesa.setPESAXMLData("<DATA/>");

            TransactionWrapper<FlexicoreHashMap> utilityPaymentWrapper = CBSAPI.utilitiesPayment(
                    strMobileNumber,
                    "MSISDN",
                    strMobileNumber,
                    "IMSI",
                    strSIMID,
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
                    "USSD",
                    "MBANKING");

            FlexicoreHashMap utilityPaymentMap = utilityPaymentWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = utilityPaymentMap.getValue("msg_object");

            if (utilityPaymentWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = utilityPaymentMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reverseUtilityPaymentWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reverseUtilityPaymentWrapper.hasErrors()) {
                        strMSG = "Dear member, your Bill Payment of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Bill Payment of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reverseUtilityPaymentWrapper.getSingleRecord().putValue("display_message", strMSG);


                    return reverseUtilityPaymentWrapper;
                }
            }

            utilityPaymentWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return utilityPaymentWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Utility Payment request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Utility Payment request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }


    public TransactionWrapper<FlexicoreHashMap> utilityPayment(USSDRequest theUSSDRequest) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "BILL_PAYMENT";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {

            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strMobileNumberTo = strMobileNumberFrom;

            String strMemberName = getUserFullName(strMobileNumber).trim();
            String strTraceID = theUSSDRequest.getUSSDTraceID();

            //String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            int intPriority = 200;

            String strBillAccountNumberHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT.name());
            HashMap<String, String> hmAccount = Utils.toHashMap(strBillAccountNumberHashMap);
            String strBillerAccount = hmAccount.get("ACCOUNT_IDENTIFIER");
            String strBillerAccountToName = hmAccount.get("ACCOUNT_NAME");


            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT.name());

            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

			/*HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
			String strSourceAccountNo = hmAccountDetails.get("number");
			String strSourceAccountName = hmAccountDetails.get("name");
			String strSourceAccountTypeName = hmAccountDetails.get("type_name");
			String strSourceAccountLabel = hmAccountDetails.get("label");*/

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_AMOUNT.name());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_PIN.name());

            String strUtilityProviderAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.UTILITIES_MENU.name());

            HashMap<String, String> hmUtilityAccountDetails = Utils.toHashMap(strUtilityProviderAccountDetails);

            String strToSPProviderAccountCode = hmUtilityAccountDetails.get("code");
            String strToAccountIdentifier = hmUtilityAccountDetails.get("identifier");
            String strToAccountType = hmUtilityAccountDetails.get("type");
            String strToAccountNaming = hmUtilityAccountDetails.get("type_tag");
            String strBillerName = hmUtilityAccountDetails.get("long_tag");

            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2B);

            long getProductID = Long.parseLong(pesaParam.getProductId());
            String strTransactionDescription = "B2B Bill Payment to " + strBillerName;
            strTransactionDescription = PESAAPI.shortenName(strTransactionDescription);

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();

            PESA pesa = new PESA();

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
            pesa.setPESAStatusDate(strDateTime);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
            pesa.setInitiatorOtherDetails("<DATA/>");

            pesa.setSourceType("ACCOUNT_NO");
            pesa.setSourceIdentifier(strSourceAccountNo);
            pesa.setSourceAccount(strSourceAccountNo);
            pesa.setSourceName(strSourceAccountName);
            //deferred to after CBS call below
            //pesa.setSourceReference();
            pesa.setSourceApplication("CBS");
            pesa.setSourceOtherDetails("<DATA/>");

            pesa.setSenderType("SHORT_CODE");
            pesa.setSenderIdentifier(strSenderIdentifier);
            pesa.setSenderAccount(strSenderAccount);
            pesa.setSenderName(strSenderName);
            pesa.setSenderOtherDetails("<DATA/>");

            pesa.setReceiverType("SHORT_CODE");
            pesa.setReceiverIdentifier(strToAccountIdentifier);
            pesa.setReceiverAccount(strBillerAccount);
            pesa.setReceiverName(strBillerName);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumber);
            pesa.setBeneficiaryAccount(strMobileNumber);
            pesa.setBeneficiaryName(strBillerAccountToName);
            pesa.setBeneficiaryOtherDetails("<DATA/>");

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));
            pesa.setTransactionRemark(strTransactionDescription);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);
            pesa.setPESAXMLData("<DATA/>");

            TransactionWrapper<FlexicoreHashMap> utilityPaymentWrapper = CBSAPI.utilitiesPayment(
                    strMobileNumber,
                    "MSISDN",
                    strMobileNumber,
                    "IMSI",
                    strSIMID,
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
                    "USSD",
                    "MBANKING");

            FlexicoreHashMap utilityPaymentMap = utilityPaymentWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = utilityPaymentMap.getValue("msg_object");

            if (utilityPaymentWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = utilityPaymentMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reverseUtilityPaymentWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reverseUtilityPaymentWrapper.hasErrors()) {
                        strMSG = "Dear member, your Bill Payment of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Bill Payment of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reverseUtilityPaymentWrapper.getSingleRecord().putValue("display_message", strMSG);


                    return reverseUtilityPaymentWrapper;
                }
            }

            utilityPaymentWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return utilityPaymentWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Utility Payment request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Utility Payment request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> bankTransferViaB2B(USSDRequest theUSSDRequest, PESAConstants.PESAType thePESAType) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "BANK_TRANSFER";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {

            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strMobileNumberTo = strMobileNumberFrom;

            //String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            String strMemberName = getUserFullName(strMobileNumber).trim();
            String strTraceID = theUSSDRequest.getUSSDTraceID();

            int intPriority = 200;

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());

            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

            String strToBankAccountNoHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

            HashMap<String, String> hmAccount = Utils.toHashMap(strToBankAccountNoHashMap);
            String strBankAccountToName = hmAccount.get("ACCOUNT_NAME");
            String strBankAccountTo = hmAccount.get("ACCOUNT_IDENTIFIER");

			/*HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
			String strSourceAccountNo = hmAccountDetails.get("number");
			String strSourceAccountName = hmAccountDetails.get("name");
			String strSourceAccountTypeName = hmAccountDetails.get("type_name");
			String strSourceAccountLabel = hmAccountDetails.get("label");*/

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN.name());

            String strUtilityProviderAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.UTILITIES_MENU.name());

            String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
            String strToAccountType = hmBankAccountDetails.get("type");
            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
            String strToBankName = hmBankAccountDetails.get("long_tag");

            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2B);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();

            PESA pesa = new PESA();

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
            pesa.setPESAStatusDate(strDateTime);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
            pesa.setInitiatorOtherDetails("<DATA/>");

            pesa.setSourceType("ACCOUNT_NO");
            pesa.setSourceIdentifier(strSourceAccountNo);
            pesa.setSourceAccount(strSourceAccountNo);
            pesa.setSourceName(strSourceAccountName);
            //deferred to after CBS call below
            //pesa.setSourceReference();
            pesa.setSourceApplication("CBS");
            pesa.setSourceOtherDetails("<DATA/>");

            pesa.setSenderType("SHORT_CODE");
            pesa.setSenderIdentifier(strSenderIdentifier);
            pesa.setSenderAccount(strSenderAccount);
            pesa.setSenderName(strSenderName);
            pesa.setSenderOtherDetails("<DATA/>");

            pesa.setReceiverType("SHORT_CODE");
            pesa.setReceiverIdentifier(strToAccountIdentifier);
            pesa.setReceiverAccount(strBankAccountTo);
            pesa.setReceiverName(strToBankName);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumber);
            pesa.setBeneficiaryAccount(strMobileNumber);
            pesa.setBeneficiaryName(strBankAccountToName);
            pesa.setBeneficiaryOtherDetails("<DATA/>");

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));

            //BT|B2B|KCB|1336451041|KCB|Kevin
            //pesa.setTransactionRemark("B2B Bank transfer to " + strToBankName + " A/C " + strBankAccountTo);
            pesa.setTransactionRemark("BT|B2B|" + strToBankName + "|" + strBankAccountTo + "|" + strBankAccountToName);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);
            pesa.setPESAXMLData("<DATA/>");

            TransactionWrapper<FlexicoreHashMap> bankTransferWrapper =
                    CBSAPI.bankTransferViaB2B(
                            strMobileNumber,
                            "MSISDN",
                            strMobileNumber,
                            "IMSI",
                            strSIMID,
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
                            "USSD",
                            "MBANKING");

            FlexicoreHashMap bankTransferMap = bankTransferWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = bankTransferMap.getValue("msg_object");

            if (bankTransferWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = bankTransferMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reversalWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reversalWrapper.hasErrors()) {
                        strMSG = "Dear member, your Bank Transfer of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Bank Transfer of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reversalWrapper.getSingleRecord().putValue("display_message", strMSG);

                    return reversalWrapper;
                }
            }

            bankTransferWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return bankTransferWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Bank Transfer request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Utility Payment request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }


    public TransactionWrapper<FlexicoreHashMap> bankTransferViaPesaLink(USSDRequest theUSSDRequest, PESAConstants.PESAType thePESAType) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            String strDate = MBankingDB.getDBDateTime().trim();
            String strGUID = MBankingDB.getDB_GUID().toUpperCase().trim();

            String strTraceID = theUSSDRequest.getUSSDTraceID();

            String strUSSDSessionID = String.valueOf(theUSSDRequest.getUSSDSessionID());

            int intPriority = 200;


            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());

            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");
            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strToBankAccountNoHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

            HashMap<String, String> hmAccount = Utils.toHashMap(strToBankAccountNoHashMap);
            String strBankAccountToName = hmAccount.get("ACCOUNT_NAME");
            String strBankAccountToIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");


            String strServiceProviderIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());

            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strServiceProviderIdentifier);

            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
            String strToAccountType = hmBankAccountDetails.get("type");
            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
            String strToBankName = hmBankAccountDetails.get("long_tag");
            System.out.println("*************************************************************");
            System.out.println("strServiceProviderIdentifier : " + strServiceProviderIdentifier);
            System.out.println("*************************************************************");


            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());


            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());


            LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.BANK_CODE);

            String strBankOtherDetails = "";
            String strBankName = "";
            String strBankCode = "";

            for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                String strProviderIdentifier = serviceProviderAccount.getProviderAccountIdentifier();
                if (strProviderIdentifier.equals(strToAccountIdentifier)) {
                    strBankCode = strProviderIdentifier;
                    String strProviderBranchCode = serviceProviderAccount.getProviderBranchCode();
                    strBankName = serviceProviderAccount.getProviderAccountLongTag();
                    strBankOtherDetails = "<DATA><BANK_CODE>" + strProviderIdentifier + "</BANK_CODE><BRANCH_CODE>" + strProviderBranchCode + "</BRANCH_CODE></DATA>";

                    System.out.println("strBankCode : " + strBankCode);
                    System.out.println("strBankName : " + strBankName);
                    System.out.println("strBankOtherDetails : " + strBankOtherDetails);
                    break;

                }
            }

            String strTransaction = "Pesalink Transfer Request";


            String strTransactionDescription = "PL|BT|" + strBankCode + "|" + strToSPProviderAccountCode + "|" + strMobileNumber + "|" + strMemberName;
            strTransactionDescription = PESAAPI.shortenName(strTransactionDescription);


            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.FAMILY_BANK_PESALINK);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strDateTime = MBankingDB.getDBDateTime();

            String strCategory = "PESALINK_TRANSFER";

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();


            PESA pesa = new PESA();

            pesa.setOriginatorID(strGUID);
            pesa.setProductID(getProductID);
            pesa.setPESAType(thePESAType);
            pesa.setPESAAction(PESAConstants.PESAAction.B2B);
            pesa.setCommand("PESALINK");
            pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);
            //pesa.setChargeProposed(null);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
            pesa.setInitiatorOtherDetails("<DATA/>");

            pesa.setSourceType("ACCOUNT_NO");
            pesa.setSourceIdentifier(strSourceAccountNo);
            pesa.setSourceAccount(strSourceAccountNo);
            pesa.setSourceName(strSourceAccountName);
            pesa.setSourceReference(strUSSDSessionID);
            pesa.setSourceApplication("CBS");
            pesa.setSourceOtherDetails("<DATA><BANK_CODE>" + AppConstants.strSACCOName + "</BANK_CODE><BRANCH_CODE>" + AppConstants.strSACCOName + "</BRANCH_CODE></DATA>");

            pesa.setSenderType("ACCOUNT_NO");
            pesa.setSenderIdentifier(strSenderIdentifier);
            pesa.setSenderAccount(strSenderAccount);
            pesa.setSenderName(strSenderName);
            pesa.setSenderOtherDetails("<DATA/>");

            pesa.setReceiverType("ACCOUNT_NO");
            pesa.setReceiverIdentifier(strBankAccountToIdentifier);
            pesa.setReceiverAccount(strBankAccountToIdentifier);
            pesa.setReceiverName(strBankAccountToName);
            pesa.setReceiverOtherDetails(strBankOtherDetails);

            pesa.setBeneficiaryType("ACCOUNT_NO");
            pesa.setBeneficiaryIdentifier(strBankAccountToIdentifier);
            pesa.setBeneficiaryAccount(strBankAccountToIdentifier);
            pesa.setBeneficiaryName(strBankAccountToName);
            pesa.setBeneficiaryOtherDetails(strBankOtherDetails);

            pesa.setBatchReference(strGUID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
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

            TransactionWrapper<FlexicoreHashMap> bankTransferWrapper =
                    CBSAPI.bankTransferViaB2B(
                            strMobileNumber,
                            "MSISDN",
                            strMobileNumber,
                            "IMSI",
                            strSIMID,
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
                            "USSD",
                            "MBANKING");

            FlexicoreHashMap bankTransferMap = bankTransferWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = bankTransferMap.getValue("msg_object");

            if (bankTransferWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = bankTransferMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reversalWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reversalWrapper.hasErrors()) {
                        strMSG = "Dear member, your Bank Transfer of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Bank Transfer of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + " - " + pesa.getReceiverName() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reversalWrapper.getSingleRecord().putValue("display_message", strMSG);

                    return reversalWrapper;
                }
            }

            bankTransferWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return bankTransferWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Bank Transfer request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Utility Payment request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }


    public TransactionWrapper<FlexicoreHashMap> paybillTransferViaB2B(USSDRequest theUSSDRequest, PESAConstants.PESAType thePESAType) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "PAYBILL_TRANSFER";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {

            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strMobileNumberTo = strMobileNumberFrom;

            //String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            String strMemberName = getUserFullName(strMobileNumber).trim();
            String strTraceID = theUSSDRequest.getUSSDTraceID();

            int intPriority = 200;

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT.name());

            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

            String strPaybillAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER.name());

            String strBankAccountToName = strPaybillAccountNumber;
            String strBankAccountTo = strPaybillAccountNumber;

			/*HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
			String strSourceAccountNo = hmAccountDetails.get("number");
			String strSourceAccountName = hmAccountDetails.get("name");
			String strSourceAccountTypeName = hmAccountDetails.get("type_name");
			String strSourceAccountLabel = hmAccountDetails.get("label");*/

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT.name());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_PIN.name());

            String strPaybillCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CODE.name());

            String strToAccountIdentifier = strPaybillCode;
            String strToBankName = strPaybillCode;


            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2B);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();

            String strTransactionDescription = "BT|B2B|" + strToBankName + "|" + strBankAccountTo + "|" + strBankAccountToName;
            strTransactionDescription = PESAAPI.shortenName(strTransactionDescription);


            PESA pesa = new PESA();

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
            pesa.setPESAStatusDate(strDateTime);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
            pesa.setInitiatorOtherDetails("<DATA/>");

            pesa.setSourceType("ACCOUNT_NO");
            pesa.setSourceIdentifier(strSourceAccountNo);
            pesa.setSourceAccount(strSourceAccountNo);
            pesa.setSourceName(strSourceAccountName);
            //deferred to after CBS call below
            //pesa.setSourceReference();
            pesa.setSourceApplication("CBS");
            pesa.setSourceOtherDetails("<DATA/>");

            pesa.setSenderType("SHORT_CODE");
            pesa.setSenderIdentifier(strSenderIdentifier);
            pesa.setSenderAccount(strSenderAccount);
            pesa.setSenderName(strSenderName);
            pesa.setSenderOtherDetails("<DATA/>");

            pesa.setReceiverType("SHORT_CODE");
            pesa.setReceiverIdentifier(strToAccountIdentifier);
            pesa.setReceiverAccount(strBankAccountTo);
            pesa.setReceiverName(strToBankName);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumber);
            pesa.setBeneficiaryAccount(strMobileNumber);
            pesa.setBeneficiaryName(strBankAccountToName);
            pesa.setBeneficiaryOtherDetails("<DATA/>");

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));
            //pesa.setTransactionRemark("B2B Paybill transfer to " + strToBankName + " A/C " + strBankAccountTo);
            pesa.setTransactionRemark(strTransactionDescription);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);
            pesa.setPESAXMLData("<DATA/>");

            TransactionWrapper<FlexicoreHashMap> bankTransferWrapper =
                    CBSAPI.bankTransferViaB2B(
                            strMobileNumber,
                            "MSISDN",
                            strMobileNumber,
                            "IMSI",
                            strSIMID,
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
                            "USSD",
                            "MBANKING");

            FlexicoreHashMap bankTransferMap = bankTransferWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = bankTransferMap.getValue("msg_object");

            if (bankTransferWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = bankTransferMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reversalWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reversalWrapper.hasErrors()) {
                        strMSG = "Dear member, your Paybill Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Paybill Transfer request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reversalWrapper.getSingleRecord().putValue("display_message", strMSG);

                    return reversalWrapper;
                }
            }

            bankTransferWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return bankTransferWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Paybill Transfer request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Utility Payment request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> tillTransferViaB2B(USSDRequest theUSSDRequest, PESAConstants.PESAType thePESAType) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();
        String strCategory = "TILL_PAYMENT";

        //USSDAPIConstants.TransactionReturnVal rVal = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {

            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMobileNumberFrom = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strMobileNumberTo = strMobileNumberFrom;

            //String strOriginatorID = theUSSDRequest.getUSSDTraceID();
            //String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            String strMemberName = getUserFullName(strMobileNumber).trim();
            String strTraceID = theUSSDRequest.getUSSDTraceID();

            int intPriority = 200;

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT.name());

            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

            String strTillAccountNumber = "";

            String strBankAccountToName = strTillAccountNumber;
            String strBankAccountTo = strTillAccountNumber;

			/*HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
			String strSourceAccountNo = hmAccountDetails.get("number");
			String strSourceAccountName = hmAccountDetails.get("name");
			String strSourceAccountTypeName = hmAccountDetails.get("type_name");
			String strSourceAccountLabel = hmAccountDetails.get("label");*/

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT.name());
            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_PIN.name());

            String strTillCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CODE.name());

            String strToAccountIdentifier = strTillCode;
            String strToBankName = strTillCode;

            PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_B2B);

            long getProductID = Long.parseLong(pesaParam.getProductId());

            String strSenderIdentifier = pesaParam.getSenderIdentifier();
            String strSenderAccount = pesaParam.getSenderAccount();
            String strSenderName = pesaParam.getSenderName();

            PESA pesa = new PESA();

            String strOriginatorID = UUID.randomUUID().toString();

            pesa.setOriginatorID(strOriginatorID);
            pesa.setProductID(getProductID);

            pesa.setPESAType(PESAConstants.PESAType.PESA_OUT);
            pesa.setPESAAction(PESAConstants.PESAAction.B2B);
            pesa.setCommand("BusinessBuyGoods");
            pesa.setSensitivity(PESAConstants.Sensitivity.NORMAL);

            pesa.setPESAStatusCode(10);
            pesa.setPESAStatusName("QUEUED");
            pesa.setPESAStatusDescription("New PESA");
            pesa.setPESAStatusDate(strDateTime);

            pesa.setInitiatorType("MSISDN");
            pesa.setInitiatorIdentifier(strMobileNumber);
            pesa.setInitiatorAccount(strMobileNumber);
            pesa.setInitiatorName(strMemberName);
            pesa.setInitiatorReference(strTraceID);
            pesa.setInitiatorApplication("USSD");
            pesa.setInitiatorOtherDetails("<DATA/>");

            pesa.setSourceType("ACCOUNT_NO");
            pesa.setSourceIdentifier(strSourceAccountNo);
            pesa.setSourceAccount(strSourceAccountNo);
            pesa.setSourceName(strSourceAccountName);
            //deferred to after CBS call below
            //pesa.setSourceReference();
            pesa.setSourceApplication("CBS");
            pesa.setSourceOtherDetails("<DATA/>");

            pesa.setSenderType("SHORT_CODE");
            pesa.setSenderIdentifier(strSenderIdentifier);
            pesa.setSenderAccount(strSenderAccount);
            pesa.setSenderName(strSenderName);
            pesa.setSenderOtherDetails("<DATA/>");

            pesa.setReceiverType("SHORT_CODE");
            pesa.setReceiverIdentifier(strToAccountIdentifier);
            pesa.setReceiverAccount(strMobileNumber);
            pesa.setReceiverName(strToBankName);
            pesa.setReceiverOtherDetails("<DATA/>");

            pesa.setBeneficiaryType("MSISDN");
            pesa.setBeneficiaryIdentifier(strMobileNumber);
            pesa.setBeneficiaryAccount(strMobileNumber);
            pesa.setBeneficiaryName(strMobileNumber);
            pesa.setBeneficiaryOtherDetails("<DATA/>");

            pesa.setBatchReference(strOriginatorID);
            pesa.setCorrelationReference(strTraceID);
            pesa.setCorrelationApplication("USSD");
            pesa.setTransactionCurrency("KES");
            pesa.setTransactionAmount(Double.parseDouble(strAmount));
            pesa.setTransactionRemark("B2B Buy Goods and Services to " + strToBankName + " A/C " + strBankAccountTo);
            pesa.setCategory(strCategory);

            pesa.setPriority(200);
            pesa.setSendCount(0);

            pesa.setSchedulePesa(PESAConstants.Condition.NO);
            pesa.setPesaDateScheduled(strDateTime);
            pesa.setPesaDateCreated(strDateTime);
            pesa.setPESAXMLData("<DATA/>");

            TransactionWrapper<FlexicoreHashMap> bankTransferWrapper =
                    CBSAPI.bankTransferViaB2B(
                            strMobileNumber,
                            "MSISDN",
                            strMobileNumber,
                            "IMSI",
                            strSIMID,
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
                            "USSD",
                            "MBANKING");

            FlexicoreHashMap bankTransferMap = bankTransferWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = bankTransferMap.getValue("msg_object");

            if (bankTransferWrapper.hasErrors()) {
                //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
            } else {

                String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                String strSourceReference = bankTransferMap.getFlexicoreHashMap("response_payload").getStringValue("transaction_reference");
                pesa.setSourceReference(strSourceReference);

                if (PESAProcessor.sendPESA(pesa) > 0) {
                    //Substituted with the one for results
                    //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);
                } else {

                    String strRefKey = UUID.randomUUID().toString();

                    TransactionWrapper<FlexicoreHashMap> reversalWrapper =
                            CBSAPI.reverseMobileMoneyWithdrawal(
                                    strMobileNumber,
                                    "MSISDN",
                                    strMobileNumber,
                                    pesa.getOriginatorID(),
                                    pesa.getBeneficiaryType(),
                                    pesa.getBeneficiaryIdentifier(),
                                    pesa.getBeneficiaryName(),
                                    pesa.getBeneficiaryOtherDetails(),
                                    "",
                                    DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

                    String strMSG;
                    if (!reversalWrapper.hasErrors()) {
                        strMSG = "Dear member, your Buy Goods and Services request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " has been REVERSED. Dial " + AppConstants.strSACCOUSSDCode + " to check your balance.";

                    } else {
                        strMSG = "Dear member, your Buy Goods and Services request of KES " + strFormattedAmount + " to " + pesa.getReceiverIdentifier() + ", A/C " + pesa.getReceiverAccount() + " on " + strFormattedDateTime + " REVERSAL FAILED. Please contact the SACCO for assistance.";
                    }

                    //sendSMS(strMobileNumber, strMSG, MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);

                    reversalWrapper.getSingleRecord().putValue("display_message", strMSG);

                    return reversalWrapper;
                }
            }

            bankTransferWrapper.getSingleRecord().putValue("display_message", cbsMSG.getMessage());

            return bankTransferWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());

            e.printStackTrace();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Buy Goods and Services request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

			/*sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Utility Payment request. Please try again later."+getTrailerMessage(),
					MSGConstants.MSGMode.SAF, 210, strCategory, theUSSDRequest);*/

        }

        return resultWrapper;
    }


    public TransactionWrapper<FlexicoreHashMap> loanBalanceEnquiryALL(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strAccountNoDetails = "";
            strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountCd = hmFromAccountNoDetails.get("ac_cd");
            String strAccountName = hmFromAccountNoDetails.get("ac_name");
            String strAccountLabel = hmFromAccountNoDetails.get("ac_label");

            String strAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            /*TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.loanBalanceEnquiry(strReferenceKey, "MSISDN", strMobileNumber,
                    "IMSI", strSIMID, strAccountNumber, strAccountCd);

            if (!isClearing) {
                FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
                CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

                sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_BALANCE_ENQUIRY", theUSSDRequest);

            }*/

            String strMemberName = getUserFullName(strMobileNumber).trim();

            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.loanBalanceEnquiry(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strAccountNumber);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

            sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_BALANCE_ENQUIRY", theUSSDRequest);

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
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier("ALL_LOANS");
            channelService.setSourceAccount("ALL_LOANS");
            channelService.setSourceName("ALL_LOANS");
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.LOAN_BALANCE_ENQUIRY.getValue(),
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

            return accountBalanceEnquiryWrapper;


        } catch (Exception e) {
            e.printStackTrace();

            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "LOAN_BALANCE_ENQUIRY", theUSSDRequest);
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> loanBalanceEnquiry(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strAccountNoDetails = "";
            /*if (isClearing) {
                strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
            } else {
            }*/
            strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountCd = hmFromAccountNoDetails.get("ac_cd");
            //String strAccountName = hmFromAccountNoDetails.get("ac_name");
            String strAccountLabel = hmFromAccountNoDetails.get("ac_label");

            String strAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            /*TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.loanBalanceEnquiry(strReferenceKey, "MSISDN", strMobileNumber,
                    "IMSI", strSIMID, strAccountNumber, strAccountCd);

            if (!isClearing) {
                FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
                CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

                sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_BALANCE_ENQUIRY", theUSSDRequest);

            }*/


            String strMemberName = getUserFullName(strMobileNumber).trim();


            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = CBSAPI.loanBalanceEnquiry(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strAccountNumber);

            FlexicoreHashMap accountBalanceMap = accountBalanceEnquiryWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

            sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_BALANCE_ENQUIRY", theUSSDRequest);

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
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNumber);
            channelService.setSourceAccount(strAccountNumber);
            channelService.setSourceName(strAccountLabel);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.LOAN_BALANCE_ENQUIRY.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Loan Balance Enquiry for A/C: " + strAccountLabel);
            ChannelService.insertService(channelService);

            return accountBalanceEnquiryWrapper;
        } catch (Exception e) {
            e.printStackTrace();

            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "LOAN_BALANCE_ENQUIRY", theUSSDRequest);
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> loanApplication(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strLoanApplicationDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
            String strLoanAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT.name());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strLoanApplicationDetails);
            String strProductID = hmFromAccountNoDetails.get("id");
            String theProductName = hmFromAccountNoDetails.get("name");
            String theProductLabel = hmFromAccountNoDetails.get("label");

            String strOriginatorId = UUID.randomUUID().toString();

            //String theCustomerIdentifier = getDefaultCustomerIdentifier(theUSSDRequest);

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> loanApplicationWrapper = CBSAPI.loanApplication(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strProductID,Double.parseDouble(strLoanAmount),
                    "","","",strOriginatorId,
                    "USSD", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"));

            FlexicoreHashMap accountBalanceMap = loanApplicationWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = accountBalanceMap.getValue("msg_object");

            //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_APPLICATION", theUSSDRequest);

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_APPLICATION.getValue());

            if (loanApplicationWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(loanApplicationWrapper.getSingleRecord().getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Loan Application Completed Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strProductID);
            channelService.setSourceAccount(strProductID);
            channelService.setSourceName(theProductName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(Double.parseDouble(strLoanAmount));

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.LOAN_APPLICATION.getValue(),
                    Double.parseDouble(strLoanAmount));

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Loan Application for A/C: " + theProductName);
            ChannelService.insertService(channelService);

            return loanApplicationWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Application request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Loan Application request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "LOAN_APPLICATION", theUSSDRequest);

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> loanQualificationCheck(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strLoanApplicationDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_QUALIFICATION_TYPE.name());
            // String strLoanAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_QUALIFICATION_AMOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strLoanApplicationDetails);
            String strProductID = hmFromAccountNoDetails.get("id");
            String theProductName = hmFromAccountNoDetails.get("name");
            String theProductLabel = hmFromAccountNoDetails.get("label");

            String theCustomerIdentifier = getDefaultCustomerIdentifier(theUSSDRequest);

            //String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> checkLoanLimitWrapper = CBSAPI.checkLoanLimit(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strProductID,"0");

            FlexicoreHashMap checkLoanLimitMap = checkLoanLimitWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = checkLoanLimitMap.getValue("msg_object");

            //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), "CHECK_LOAN_LIMIT", theUSSDRequest);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.CHECK_LOAN_LIMIT.getValue());

            if (checkLoanLimitWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(checkLoanLimitMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Loan Qualification Check Completed Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strProductID);
            channelService.setSourceAccount(strProductID);
            channelService.setSourceName(theProductName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.CHECK_LOAN_LIMIT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Loan Qualification Check for A/C: " + theProductName);
            ChannelService.insertService(channelService);

            return checkLoanLimitWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Limit request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Loan Limit request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "CHECK_LOAN_LIMIT", theUSSDRequest);

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> loanQualificationCheckForLoanApplication(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strLoanApplicationDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
            // String strLoanAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_QUALIFICATION_AMOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strLoanApplicationDetails);
            String strProductID = hmFromAccountNoDetails.get("id");
            String theProductName = hmFromAccountNoDetails.get("name");
            String theProductLabel = hmFromAccountNoDetails.get("label");

            String theCustomerIdentifier = getDefaultCustomerIdentifier(theUSSDRequest);

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.checkLoanLimit(strMobileNumber,
                    "MSISDN", strMobileNumber, "IMSI", strSIMID, strProductID,"0");

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Limit request. Please try again later." + getTrailerMessage()));

        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> getCustomerLoanAccounts(USSDRequest theUSSDRequest, String theCustomerIdentifierType, String theCustomerIdentifier) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            return CBSAPI.getCustomerLoanAccounts(strMobileNumber, theCustomerIdentifierType, theCustomerIdentifier);
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }

    public TransactionWrapper<FlexicoreHashMap> loanPaymentViaSavings(USSDRequest theUSSDRequest, boolean isClearing, String strAmount) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        String strCategory = "LOAN_PAYMENT";

        try {

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strFromAccountName = hmFromAccountNoDetails.get("ac_label");

            //String strToIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER.name());

            String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());

            HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);

            String strLoanNumber = hmToAccountDetails.get("ac_no");
            String strLoanName = hmToAccountDetails.get("ac_name");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strOriginatorId = UUID.randomUUID().toString();

            String strTransactionDescription = "Source A/C: " + strFromAccountNumber + " - Loan A/C: " + strLoanName;
            TransactionWrapper<FlexicoreHashMap> loanPaymentViaSavingsWrapper = CBSAPI.loanPaymentViaSavings(
                    strMobileNumber,
                    "MSISDN",
                    strMobileNumber,
                    "IMSI",
                    strSIMID,
                    strOriginatorId,
                    strFromAccountNumber,
                    strLoanNumber,
                    Double.parseDouble(strAmount),
                    strTransactionDescription,
                    theUSSDRequest.getUSSDTraceID(),
                    "USSD",
                    "MBANKING");

            FlexicoreHashMap loanPaymentViaSavingsMap = loanPaymentViaSavingsWrapper.getSingleRecord();

            CBSAPI.SMSMSG cbsMSG = loanPaymentViaSavingsMap.getValue("msg_object");

            //sendSMS(strMobileNumber, cbsMSG.getMessage(), cbsMSG.getMode(), cbsMSG.getPriority(), strCategory, theUSSDRequest);

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory("LOAN_PAYMENT_VIA_SAVINGS");

            if (loanPaymentViaSavingsWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(loanPaymentViaSavingsMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Transaction Completed Successfully");
                channelService.setBeneficiaryReference(loanPaymentViaSavingsMap.getStringValue("cbs_transaction_reference"));
                channelService.setSourceReference(loanPaymentViaSavingsMap.getStringValue("cbs_transaction_reference"));
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strFromAccountNumber);
            channelService.setSourceAccount(strFromAccountNumber);
            channelService.setSourceName(strFromAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("ACCOUNT_NO");
            channelService.setBeneficiaryIdentifier(strLoanNumber);
            channelService.setBeneficiaryAccount(strLoanNumber);
            channelService.setBeneficiaryName(strLoanName);
            channelService.setBeneficiaryApplication("CBS");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(Double.parseDouble(strAmount));

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.IFT_LOAN_REPAYMENT.getValue(),
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

            return loanPaymentViaSavingsWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }

        return resultWrapper;
    }


    public TransactionWrapper<FlexicoreHashMap> loanMiniStatement(USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strMemberName = getUserFullName(strMobileNumber).trim();

            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountName = hmFromAccountNoDetails.get("ac_name");

            String strTrailerMessageXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strTrailerMessageXML);

            String strNumberOfEntries = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/USSD_MINI_STATEMENT_ENTRIES");

            String strReferenceKey = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.getLoanMiniStatement(strMobileNumber, "MSISDN", strMobileNumber,
                    "IMSI", strSIMID, strAccountNumber, strNumberOfEntries);

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();
            CBSAPI.SMSMSG cbsMSG = miniStatementMap.getValue("msg_object");

            String strSMS = "";
            int nLength = cbsMSG.getMessage().length();

            if (nLength > 950) {
                strSMS = cbsMSG.getMessage().substring(0, 950);
            } else {
                strSMS = cbsMSG.getMessage();
            }

            sendSMS(strMobileNumber, strSMS, cbsMSG.getMode(), cbsMSG.getPriority(), "LOAN_MINI_STATEMENT", theUSSDRequest);

            String strOriginatorId = UUID.randomUUID().toString();

            ChannelService channelService = new ChannelService();
            channelService.setOriginatorId(strOriginatorId);
            channelService.setTransactionCategory(AppConstants.ChargeServices.LOAN_MINI_STATEMENT.getValue());

            if (miniStatementWrapper.hasErrors()) {
                channelService.setTransactionStatusCode(104);
                channelService.setTransactionStatusName("FAILED");
                channelService.setTransactionStatusDescription(miniStatementMap.getStringValueOrIfNull("cbs_api_error_message", "Unknown error occurred"));
            } else {
                channelService.setTransactionStatusCode(102);
                channelService.setTransactionStatusName("SUCCESS");
                channelService.setTransactionStatusDescription("Loan Mini Statement Generated Successfully");
                channelService.setBeneficiaryReference("");
                channelService.setSourceReference("");
            }
            channelService.setTransactionStatusDate(DateTime.getCurrentDateTime());

            channelService.setInitiatorType("MSISDN");
            channelService.setInitiatorIdentifier(strMobileNumber);
            channelService.setInitiatorAccount(strMobileNumber);
            channelService.setInitiatorName(strMemberName);
            channelService.setInitiatorReference(theUSSDRequest.getUSSDTraceID());
            channelService.setInitiatorApplication("USSD");
            channelService.setInitiatorOtherDetails("<DATA/>");

            channelService.setSourceType("ACCOUNT_NO");
            channelService.setSourceIdentifier(strAccountNumber);
            channelService.setSourceAccount(strAccountNumber);
            channelService.setSourceName(strAccountName);
            channelService.setSourceApplication("CBS");
            channelService.setSourceOtherDetails("<DATA/>");

            channelService.setBeneficiaryType("MSISDN");
            channelService.setBeneficiaryIdentifier(strMobileNumber);
            channelService.setBeneficiaryAccount(strMobileNumber);
            channelService.setBeneficiaryName(strMemberName);
            channelService.setBeneficiaryApplication("MSISDN");
            channelService.setBeneficiaryOtherDetails("<DATA/>");

            channelService.setTransactionCurrency("KES");
            channelService.setTransactionAmount(0.00);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNumber, "MSISDN", strMobileNumber, AppConstants.ChargeServices.LOAN_MINI_STATEMENT.getValue(),
                    0.00);

            if (chargesWrapper.hasErrors()) {
                channelService.setTransactionCharge(0.00);
                channelService.setTransactionOtherDetails(chargesWrapper.getSingleRecord().getStringValue("cbs_api_error_message"));

            } else {
                channelService.setTransactionCharge(Double.parseDouble(chargesWrapper.getSingleRecord().getStringValue("charge_amount")));
                channelService.setTransactionOtherDetails("<DATA/>");
            }

            channelService.setTransactionRemark("Loan Mini-Statement for A/C: " + strAccountName);
            ChannelService.insertService(channelService);

            return miniStatementWrapper;

        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Mini Statement request. Please try again later." + getTrailerMessage()));

            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            sendSMS(strMobileNumber, "Sorry, an error occurred while processing your Loan Mini Statement request. Please try again later." + getTrailerMessage(),
                    MSGConstants.MSGMode.SAF, 210, "LOAN_MINI_STATEMENT", theUSSDRequest);

        }

        return resultWrapper;
    }


    public void sendSMS(String theMobileNo, String theMSG, MSGConstants.MSGMode theMode, int thePriority, String theCategory, USSDRequest theUSSDRequest) {
        try {
            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());
            String strTraceID = theUSSDRequest.getUSSDTraceID();
            fnSendSMS(theMobileNo, theMSG, "YES", theMode, thePriority, theCategory, "USSD", "MBANKING_SERVER", strTransactionID, strTraceID);
        } catch (Exception e) {
            System.err.println("USSDAPI.sendSMS() ERROR : " + e.getMessage());
        }
    }

    public static USSDAmountLimitParam getAmountLimitCustomParameters(MBankingConstants.ApplicationType theApplicationType, USSDAPIConstants.USSD_PARAM_TYPE theUSSDParamType) {
        USSDAmountLimitParam rVal = new USSDAmountLimitParam();
        try {
            String strUSSDParamType = "OTHER_DETAILS/CUSTOM_PARAMETERS/SERVICE_CONFIGS/AMOUNT_LIMITS";

            switch (theUSSDParamType) {
                case CASH_WITHDRAWAL: {
                    strUSSDParamType += "/CASH_WITHDRAWAL";
                    break;
                }
                case AIRTIME_PURCHASE: {
                    strUSSDParamType += "/AIRTIME_PURCHASE";
                    break;
                }
                case PAY_BILL: {
                    strUSSDParamType += "/PAY_BILL";
                    break;
                }
                case EXTERNAL_FUNDS_TRANSFER: {
                    strUSSDParamType += "/EXTERNAL_FUNDS_TRANSFER";
                    break;
                }
                case INTERNAL_FUNDS_TRANSFER: {
                    strUSSDParamType += "/INTERNAL_FUNDS_TRANSFER";
                    break;
                }
                case DEPOSIT: {
                    strUSSDParamType += "/DEPOSIT";
                    break;
                }
                case APPLY_LOAN: {
                    strUSSDParamType += "/APPLY_LOAN";
                    break;
                }
                case PAY_LOAN: {
                    strUSSDParamType += "/PAY_LOAN";
                    break;
                }
            }

            String strMinimum = MBankingAPI.getValueFromLocalParams(theApplicationType, strUSSDParamType + "/MIN_AMOUNT");
            String strMaximum = MBankingAPI.getValueFromLocalParams(theApplicationType, strUSSDParamType + "/MAX_AMOUNT");

            rVal.setMinimum(strMinimum);
            rVal.setMaximum(strMaximum);
        } catch (Exception e) {
            System.err.println("USSDAPI.getAmountLimitCustomParameters() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    public String generateAndSendOTP(String theMobileNo, String theSessionID, USSDRequest theUSSDRequest) {
        String rVal = "";
        try {
            String strMAPPConfigXML = USSDLocalParameters.getClientXMLParameters();

            InputSource source = new InputSource(new StringReader(strMAPPConfigXML));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);
            XPath configXPath = XPathFactory.newInstance().newXPath();

            String strLength = configXPath.evaluate("/OTHER_DETAILS/CUSTOM_PARAMETERS/MAPP_ACTIVATION_CODE/@LENGTH", xmlDocument, XPathConstants.STRING).toString();
            String strTTL = configXPath.evaluate("/OTHER_DETAILS/CUSTOM_PARAMETERS/MAPP_ACTIVATION_CODE/@TTL", xmlDocument, XPathConstants.STRING).toString();

            int intLength = Integer.parseInt(strLength);
            long lnTTL = Integer.parseInt(strTTL);

            long lnTTLMinutes = lnTTL / 60;

            //String strMobileAppStartKey = Utils.generateRandomString(intLength);
            String strMobileAppStartKey;

            if (theMobileNo.equalsIgnoreCase("254790491947")) {
                strMobileAppStartKey = "123456";
            } else {
                strMobileAppStartKey = Utils.generateRandomString(intLength);
            }

            //InMemoryCache.store(theMobileNo+strMobileAppStartKey, strMobileAppStartKey, lnTTL);

            MAPPAPIDB.fnInsertOTPData(theMobileNo, strMobileAppStartKey, lnTTL);

            SimpleDateFormat sdSimpleDateFormat = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            Timestamp tsCurrentTimestamp = new Timestamp(System.currentTimeMillis());
            Timestamp tsCurrentTimestampPlusTime = new Timestamp(System.currentTimeMillis() + (lnTTL * 1000));

            String strTimeGenerated = sdSimpleDateFormat.format(tsCurrentTimestamp);
            String strExpiryDate = sdSimpleDateFormat.format(tsCurrentTimestampPlusTime);


            String strMSG = "Dear Member,\n" + strMobileAppStartKey + " is your mobile app activation code generated at " + strTimeGenerated + ". This activation code is valid up to " + strExpiryDate + ".\n" + strAppID;

            String strCategory = "MAPP_ACTIVATION";

            sendSMS(theMobileNo, strMSG, MSGConstants.MSGMode.SAF, 200, strCategory, theUSSDRequest);
            rVal = "Your " + intLength + " digit Mobile App Activation Code has been sent to you via SMS. Complete your Mobile App Activation within " + lnTTLMinutes + " minutes.";
        } catch (Exception e) {
            System.err.println("USSDAPI.generateAndSendOTP() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    public USSDAPIConstants.TransactionReturnVal deactivateMobileApp(USSDRequest theUSSDRequest) {
        USSDAPIConstants.TransactionReturnVal rval = USSDAPIConstants.TransactionReturnVal.ERROR;
        try {
            String strDateTime = MBankingDB.getDBDateTime();
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

            String strTransactionID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());

            String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());

            TransactionWrapper<FlexicoreHashMap> deactivateMobileAppWrapper = CBSAPI.deactivateMobileApp(strTransactionID, "MSISDN", strMobileNumber, "IMSI", strSIMID);

            if (deactivateMobileAppWrapper.hasErrors()) {
                rval = USSDAPIConstants.TransactionReturnVal.ERROR;

            } else {
                rval = USSDAPIConstants.TransactionReturnVal.SUCCESS;
            }
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + ".deactivateMobileApp() ERROR : " + e.getMessage());
        }

        return rval;
    }

    public String getDefaultCustomerIdentifier(USSDRequest theUSSDRequest) {

        String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
        String strSIMID = String.valueOf(theUSSDRequest.getUSSDIMSI());

        try {

            TransactionWrapper<FlexicoreHashMap> signatoryCustomersListWrapper = CBSAPI.getSignatoryCustomersList(theUSSDRequest.getUSSDTraceID(),
                    "MSISDN", strMobileNumber, "IMSI", strSIMID);

            if (signatoryCustomersListWrapper.hasErrors()) {
                System.err.println("USSDAPI.getDefaultCustomerIdentifier() - ERROR:  " + signatoryCustomersListWrapper.getErrors());
            } else {

                FlexicoreArrayList customersList = signatoryCustomersListWrapper.getSingleRecord().getValue("payload");

                if (customersList.isEmpty()) {
                    return null;
                }
                return customersList.getRecord(0).getStringValue("identifier");
            }

        } catch (Exception e) {
            System.err.println("USSDAPI.getDefaultCustomerIdentifier() - ERROR" + e.getMessage() + "\n");
            e.printStackTrace();
        }

        return null;
    }

    public TransactionWrapper<?> insertOrUpdateLoanDetails(FlexicoreHashMap payload, String strMobileNo) {
        List<String> duplicate = new ArrayList<>();
        duplicate.add("application_details");
        duplicate.add("date_created");

        TransactionWrapper<FlexicoreHashMap> twrapper = Repository.selectWhere(StringRefs.SENTINEL,
                "tmp.tmp_loan_application_details", new FilterPredicate("mobile_number = :mobile_number"),
                new FlexicoreHashMap().addQueryArgument(":mobile_number", strMobileNo));

        if (twrapper.hasErrors()) {
            return new TransactionWrapper<>();
        }

        FlexicoreHashMap flexicoreHashMap = twrapper.getSingleRecord();
        if (flexicoreHashMap == null || flexicoreHashMap.isEmpty()) {
            return Repository.insertAutoIncremented(StringRefs.SENTINEL,
                    "tmp.tmp_loan_application_details", payload);
        } else {
            return Repository.update(StringRefs.SENTINEL,
                    "tmp.tmp_loan_application_details", payload,
                    new FilterPredicate("mobile_number = :mobile_number"),
                    new FlexicoreHashMap().addQueryArgument(":mobile_number", strMobileNo)
            );
        }
    }

    public TransactionWrapper<FlexicoreHashMap> getTMPLoanDetails(String strMobileNo) {
        return Repository.selectWhere(StringRefs.SENTINEL, "tmp.tmp_loan_application_details", new FilterPredicate("mobile_number = :mobile_number"),
                new FlexicoreHashMap().addQueryArgument(":mobile_number", strMobileNo));
    }

    public String getUserFullName(String strUserPhoneNumber) {
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


    public String getUserFirstName(String strUserPhoneNumber) {
        String strName = getUserFullName(strUserPhoneNumber);
        if (strName.isEmpty()) {
            return "Member";
        } else {
            return strName.split(" ")[0];
        }
    }

    public String getMemberNumber(USSDRequest theUSSDRequest) {
        try {
            TransactionWrapper<FlexicoreHashMap> customersListWrapper = getSignatoryCustomersList(theUSSDRequest);
            if (customersListWrapper.hasErrors()) {
                return ":!:member_no_error:?:";
            }

            FlexicoreArrayList customersList = customersListWrapper.getSingleRecord().getValue("payload");
            if (customersList.isEmpty()) {
                return ":!:no_member_no:?:";
            }

            return customersList.get(0).getStringValue("identifier");
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            return ":!:member_exception:?:";
        }
    }


    //get customer number
    public String getCustomerNumber(USSDRequest theUSSDRequest) {
        try {
            TransactionWrapper<FlexicoreHashMap> customersListWrapper = getSignatoryCustomersList(theUSSDRequest);
            if (customersListWrapper.hasErrors()) {
                return ":!:customer_no_error:?:";
            }

            FlexicoreArrayList customersList = customersListWrapper.getSingleRecord().getValue("payload");
            if (customersList.isEmpty()) {
                return ":!:no_customer_no:?:";
            }

            return customersList.get(0).getStringValue("identifier");
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            return ":!:customer_exception:?:";
        }
    }

    //get main account number
    public String getMainAccountNumber(USSDRequest theUSSDRequest) {
        try {
            TransactionWrapper<FlexicoreHashMap> customersListWrapper = getSignatoryCustomersList(theUSSDRequest);
            if (customersListWrapper.hasErrors()) {
                return ":!:main_account_no_error:?:";
            }

            FlexicoreArrayList customersList = customersListWrapper.getSingleRecord().getValue("payload");
            if (customersList.isEmpty()) {
                return ":!:no_main_account_no:?:";
            }

            return customersList.get(0).getStringValue("account_number");
        } catch (Exception e) {
            System.err.println(this.getClass().getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            return ":!:main_account_no_exception:?:";
        }
    }


    public static void hashAgencyPINsOnNAV() {
        try {
            //System.out.println("hashPINsOnNAV started");
            String strClearTextPINXML = NavisionAgency.getUnhashedPINs();

            //System.out.println(strClearTextPINXML);

            if (!strClearTextPINXML.equals("ERROR")) {
                InputSource source = new InputSource(new StringReader(strClearTextPINXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                NodeList ndAccounts = ((NodeList) configXPath.evaluate("/ACCOUNTS", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();

                double lnStartTime = (double) System.currentTimeMillis();
                for (int i = 0; i < ndAccounts.getLength(); i++) {
                    String strUsername = ndAccounts.item(i).getAttributes().getNamedItem("USERNAME").getTextContent();
                    String strAgentCode = ndAccounts.item(i).getAttributes().getNamedItem("AGENT_CODE").getTextContent();
                    String strPIN = ndAccounts.item(i).getAttributes().getNamedItem("PASSWORD").getTextContent();

                    System.out.println("PIN Hash Count: " + (i + 1));
                    //System.out.println("Account Number: " + strAccountNumber);
                    //System.out.println("Phone Number: " + strPhoneNumber);
                    //System.out.println("Cleartext PIN: " + strPIN);

                    String strHashedPIN = hashAgentPIN(strPIN, strUsername);
                    //System.out.println("Hashed PIN: " + strHashedPIN);

                    String strResult = NavisionAgency.setHashedPIN(strUsername, strAgentCode, strHashedPIN);
                    //System.out.println("RESULT: " + strResult + "\n");
                }
                double lnEndTime = (double) System.currentTimeMillis();
                double lnTimeTaken = (lnEndTime - lnStartTime) / 1000;
                //System.out.println("Finished Task In " + lnTimeTaken + " Seconds");
            }
        } catch (Exception e) {
            System.err.println("USSDAPI.hashAgencyPINsOnNAV() ERROR : " + e.getMessage());
            //hashPINsOnNAV();
        } finally {
        }
    }


    public static String formatXMLGregorianCalendarToString(XMLGregorianCalendar xmlGregorianCalendar) {
        if (xmlGregorianCalendar == null) {
            return null;
        }
        Date date = xmlGregorianCalendar.toGregorianCalendar().getTime();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return dateFormat.format(date);
    }


    public HashMap<String, String> verifyBusinessShortCode(USSDRequest theUSSDRequest) {

        String strShortCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE.name());

        switch (strShortCode) {
            case "221221" -> {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("STATUS", "SUCCESS");
                hashMap.put("BUSINESS_SHORT_CODE", strShortCode);
                hashMap.put("BUSINESS_NAME", "Makini Enterprises");
                return hashMap;
            }

            case "001100" -> {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("STATUS", "SUCCESS");
                hashMap.put("BUSINESS_SHORT_CODE", strShortCode);
                hashMap.put("BUSINESS_NAME", "Tawi Supermarket");
                return hashMap;
            }

            case "700100" -> {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("STATUS", "SUCCESS");
                hashMap.put("BUSINESS_SHORT_CODE", strShortCode);
                hashMap.put("BUSINESS_NAME", "Maendeleo Furniture");
                return hashMap;
            }

            case "353353" -> {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("STATUS", "SUCCESS");
                hashMap.put("BUSINESS_SHORT_CODE", strShortCode);
                hashMap.put("BUSINESS_NAME", "James and Sons Mechanics");
                return hashMap;
            }

            default -> {
                HashMap<String, String> hashMap = new HashMap<>();
                hashMap.put("STATUS", "SHORT_CODE_NOT_FOUND");
                return hashMap;
            }
        }
    }
}
