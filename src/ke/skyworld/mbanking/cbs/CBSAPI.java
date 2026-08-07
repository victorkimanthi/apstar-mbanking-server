package ke.skyworld.mbanking.cbs;

import ke.co.skyworld.smp.authentication_manager.InternetBankingCryptography;
import ke.co.skyworld.smp.authentication_manager.MobileBankingCryptography;
import ke.co.skyworld.smp.authentication_manager.SMPCryptography;
import ke.co.skyworld.smp.comm_channels_manager.EmailTemplates;
import ke.co.skyworld.smp.query_manager.SystemTables;
import ke.co.skyworld.smp.permissions.SystemApplicationCodes;

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
import ke.co.skyworld.smp.utility_items.memory.JvmManager;
import ke.co.skyworld.smp.utility_items.security.HashUtils;
import ke.co.skyworld.smp.utility_items.security.Passwords;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingXMLFactory;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.pesa.PESA;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.channelutils.EmailMessaging;
import ke.skyworld.mbanking.channelutils.Messaging;
import ke.skyworld.mbanking.mappapi.MAPPAPIConstants;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import ke.skyworld.mbanking.ussdapplication.AppConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.net.ssl.HttpsURLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static ke.co.skyworld.smp.query_manager.SystemTables.*;
import static ke.skyworld.mbanking.ussdapi.APIUtils.fnModifyUSSDSessionID;
import static ke.skyworld.mbanking.ussdapi.APIUtils.fnSendSMS;

public class CBSAPI {

    private static final Random RANDOM = new Random();

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

    public static TransactionWrapper<FlexicoreHashMap> checkUser(String theReferenceKey, String theIdentifierType, String theIdentifier, String theDeviceIdentifierType, String theDeviceIdentifier) {
        return checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier, false);
    }

    public static TransactionWrapper<FlexicoreHashMap> checkUser(String theReferenceKey, String theIdentifierType, String theIdentifier, String theDeviceIdentifierType, String theDeviceIdentifier, boolean isActivation) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            FilterPredicate signatoryFilterPredicate = null;
            FlexicoreHashMap queryArguments = new FlexicoreHashMap();

            if ("MSISDN".equals(theIdentifierType)) {
                signatoryFilterPredicate = new FilterPredicate("primary_mobile_number = :primary_mobile_number");
                queryArguments.addQueryArgument(":primary_mobile_number", theIdentifier);
            } else {
                signatoryFilterPredicate = new FilterPredicate("primary_identity_type = :primary_identity_type AND primary_identity_no = :primary_identity_no");
                queryArguments.addQueryArgument(":primary_identity_type", theIdentifierType);
                queryArguments.addQueryArgument(":primary_identity_no", theIdentifier);
            }

            TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES, signatoryFilterPredicate, queryArguments);

            if (signatoryDetailsWrapper.hasErrors()) {
                signatoryDetailsWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);

                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later.")
                        .putValue("title", "An error occurred"));

                return signatoryDetailsWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = signatoryDetailsWrapper.getSingleRecord();

            if (signatoryDetailsMap == null || signatoryDetailsMap.isEmpty()) {
                signatoryDetailsWrapper.setHasErrors(true);
                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.NOT_FOUND)
                        .putValue("display_message", "Sorry, you are not registered to use " + AppConstants.strMobileBankingName + "." + getTrailerMessage())
                        .putValue("title", "Account not found"));

                return signatoryDetailsWrapper;
            }

            /*System.out.println("ORIGI HASH: "+ signatoryDetailsMap.getStringValue("integrity_hash"));
            System.out.println("NEW HASH: "+ MobileBankingCryptography.calculateIntegrityHash(signatoryDetailsMap));*/

            /*if (MobileBankingCryptography.isRecordIntegrityViolated(signatoryDetailsMap)) {
                System.err.println("CBSAPI.checkUser() - Corrupt Signatory Details found where Identifier Type = '" + theIdentifierType + "' and Identifier = '" + theIdentifier + "'");

                signatoryDetailsWrapper.setHasErrors(true);
                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INTEGRITY_VIOLATION_SIGNATORY)
                        .putValue("display_message", "Sorry, an error occurred while processing your request.\n\nERR_SIG500" + getTrailerMessage())
                        .putValue("title", "An error occurred"));

                return signatoryDetailsWrapper;
            }*/

            String strSignatoryId = signatoryDetailsMap.getStringValue("signatory_id");

            TransactionWrapper<FlexicoreHashMap> mobileMappingDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_MOBILE_BANKING_REGISTER,
                    new FilterPredicate("signatory_id = :signatory_id"),
                    new FlexicoreHashMap().addQueryArgument(":signatory_id", strSignatoryId));

            if (mobileMappingDetailsWrapper.hasErrors()) {
                mobileMappingDetailsWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);

                mobileMappingDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later.")
                        .putValue("title", "An error occurred"));

                return mobileMappingDetailsWrapper;
            }

            FlexicoreHashMap mobileBankingDetailsMap = mobileMappingDetailsWrapper.getSingleRecord();

            if (mobileBankingDetailsMap == null || mobileBankingDetailsMap.isEmpty()) {
                mobileMappingDetailsWrapper.setHasErrors(true);
                mobileMappingDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.NOT_FOUND)
                        .putValue("display_message", "Sorry, you are not registered to use " + AppConstants.strMobileBankingName + "." + getTrailerMessage())
                        .putValue("title", "An error occurred"));

                return signatoryDetailsWrapper;
            }

            String strPrimaryMobileNumber = signatoryDetailsMap.getStringValue("primary_mobile_number");
            String strMobileNumber = mobileBankingDetailsMap.getStringValue("mobile_number");

            if (!strPrimaryMobileNumber.equalsIgnoreCase(strMobileNumber)) {
                System.err.println("CBSAPI.checkUser() - Corrupt Signatory Mobile Channel Details found where Identifier Type = '" + theIdentifierType + "' and Identifier = '" + theIdentifier + "'");

                mobileMappingDetailsWrapper.setHasErrors(true);
                mobileMappingDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INTEGRITY_VIOLATION_MOBILE)
                        .putValue("display_message", "Sorry, an error occurred while processing your request.\n\nERR_MOBCH500" + getTrailerMessage())
                        .putValue("title", "An error occurred"));

                return mobileMappingDetailsWrapper;
            }

            String signatoryStatus = signatoryDetailsMap.getStringValue("status");

            if (!signatoryStatus.equalsIgnoreCase("ACTIVE") && !signatoryStatus.equalsIgnoreCase("NEW")) {
                signatoryStatus = signatoryStatus.toUpperCase();

                String strErrCode = "";

                switch (signatoryStatus) {
//                    case "NEW" -> strErrCode = "\n\nERR_CUST100";
                    case "DORMANT" -> strErrCode = "\n\nERR_CUST150";
                    case "CLOSED" -> strErrCode = "\n\nERR_CUST200";
                    case "DEFAULTED" -> strErrCode = "\n\nERR_CUST250";
                }

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.valueOf(signatoryStatus))
                        .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + "." + strErrCode + getTrailerMessage())
                        .putValue("title", "Login Failed"));

                return resultWrapper;
            }

            String mobileBankingStatus = mobileBankingDetailsMap.getStringValue("status");
            String mobileBankingStatusStartDate = mobileBankingDetailsMap.getStringValue("status_start_date");
            String mobileBankingStatusEndDate = mobileBankingDetailsMap.getStringValue("status_end_date");

            if (mobileBankingStatus.equalsIgnoreCase("BLOCKED")) {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.BLOCKED)
                        .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB100" + getTrailerMessage())
                        .putValue("title", "Login Failed"));
                return resultWrapper;
            }

            if (mobileBankingStatusStartDate != null && !mobileBankingStatusStartDate.isBlank()
                && mobileBankingStatusEndDate != null && !mobileBankingStatusEndDate.isBlank()) {

                LocalDateTime startDateTime = LocalDateTime.parse(mobileBankingStatusStartDate, DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));
                LocalDateTime endDateTime = LocalDateTime.parse(mobileBankingStatusEndDate, DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));
                LocalDateTime currDateTime = LocalDateTime.now();

                boolean isStatusStillApplicable = currDateTime.isAfter(startDateTime) && currDateTime.isBefore(endDateTime);

                String strUserFriendlyEndDate = DateTime.convertStringToDateToString(mobileBankingStatusEndDate, DateTime.DEFAULT_DATE_TIME_FORMAT, "yyyy-MM-dd HH:mm");

                if (isStatusStillApplicable) {
                    mobileBankingStatus = mobileBankingStatus.toUpperCase();
                    switch (mobileBankingStatus) {
                        case "SUSPENDED": {
                            resultWrapper.setHasErrors(true);
                            resultWrapper.setData(new FlexicoreHashMap()
                                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                    .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB150" + getTrailerMessage())
                                    .putValue("title", "Login Failed"));

                            return resultWrapper;
                        }
                        case "INACTIVE": {
                            resultWrapper.setHasErrors(true);
                            resultWrapper.setData(new FlexicoreHashMap()
                                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INACTIVE)
                                    .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB200" + getTrailerMessage())
                                    .putValue("title", "Login Failed"));
                            return resultWrapper;
                        }
                    }
                } else {
                    mobileBankingStatus = mobileBankingStatus.toUpperCase();
                    /*if ("ACTIVE" .equals(mobileBankingStatus)) {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account ACTIVE period has EXPIRED and cannot use " + AppConstants.strMobileBankingName + " services." + getTrailerMessage())
                                .putValue("title", "Account Status Expiry"));
                        return resultWrapper;
                    }else*/

                    {

                        FlexicoreHashMap updateMap = new FlexicoreHashMap();
                        updateMap.putValue("status", "ACTIVE");
                        updateMap.putValue("status_start_date", null);
                        updateMap.putValue("status_end_date", null);
                        updateMap.putValue("date_modified", DateTime.getCurrentDateTime());

                        mobileBankingDetailsMap.copyFrom(updateMap);

                        String strIntegrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingDetailsMap);
                        updateMap.putValue("integrity_hash", strIntegrityHash);

                        Repository.update(StringRefs.SENTINEL,
                                TBL_MOBILE_BANKING_REGISTER,
                                updateMap,
                                new FilterPredicate("mobile_register_id = :mobile_register_id"),
                                new FlexicoreHashMap().addQueryArgument(":mobile_register_id", mobileBankingDetailsMap.getStringValue("mobile_register_id")));
                    }
                }
            } else {
                mobileBankingStatus = mobileBankingStatus.toUpperCase();
                switch (mobileBankingStatus) {
                    case "SUSPENDED": {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB150" + getTrailerMessage())
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }
                    case "INACTIVE": {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INACTIVE)
                                .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB200" + getTrailerMessage())
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }
                }
            }

            String loginAuthAction = mobileBankingDetailsMap.getStringValue("login_auth_action");
            String loginAuthActionValidDate = mobileBankingDetailsMap.getStringValue("login_auth_action_valid_date");

            Date currentDate = DateTime.getCurrentJavaUtilDateTime();

            switch (loginAuthAction) {
                case "SUSPEND": {

                    if (loginAuthActionValidDate == null || loginAuthActionValidDate.isBlank()) {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " due to many Login Attempts." + getTrailerMessage())
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }

                    Date dateLoginAuthActionValidDate = DateTime.convertDateStringToDate(loginAuthActionValidDate);

                    if (currentDate.before(dateLoginAuthActionValidDate)) {
                        Date dtNow = new Date();
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " due to many Login Attempts. " +
                                                             "Please try again in " + DateTime.getPrettyDateTimeDifferenceRoundedUp(dtNow, dateLoginAuthActionValidDate))
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }
                    break;
                }
                case "LOCK": {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.LOCKED)
                            .putValue("display_message", "Sorry, your " + AppConstants.strSACCOName + " account is LOCKED due to many Login Attempts." + getTrailerMessage())
                            .putValue("title", "Login Failed"));
                    return resultWrapper;
                }
            }

            String otpAuthAction = mobileBankingDetailsMap.getStringValue("otp_auth_action");
            String otpAuthActionValidDate = mobileBankingDetailsMap.getStringValue("otp_auth_action_valid_date");

            currentDate = DateTime.getCurrentJavaUtilDateTime();

            switch (otpAuthAction) {
                case "SUSPEND": {
                    if (otpAuthActionValidDate == null || otpAuthActionValidDate.isBlank()) {

                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " due to many OTP Attempts." + getTrailerMessage())
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }

                    Date dateOtpAuthActionValidDate = DateTime.convertDateStringToDate(otpAuthActionValidDate);

                    if (currentDate.before(dateOtpAuthActionValidDate)) {
                        Date dtNow = new Date();

                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " due to many OTP Attempts." +
                                                             " Please try again in " + DateTime.getPrettyDateTimeDifferenceRoundedUp(dtNow, dateOtpAuthActionValidDate))
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }
                    break;
                }
                case "LOCK": {

                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.LOCKED)
                            .putValue("display_message", "Sorry, your " + AppConstants.strSACCOName + " account is LOCKED due to many OTP Attempts." + getTrailerMessage())
                            .putValue("title", "Login Failed"));
                    return resultWrapper;
                }
            }

            String kycAuthAction = mobileBankingDetailsMap.getStringValue("kyc_auth_action");
            String kycAuthActionValidDate = mobileBankingDetailsMap.getStringValue("kyc_auth_action_valid_date");

            currentDate = DateTime.getCurrentJavaUtilDateTime();

            switch (kycAuthAction) {
                case "SUSPEND": {

                    if (kycAuthActionValidDate == null || kycAuthActionValidDate.isBlank()) {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " due to many National ID Attempts." + getTrailerMessage())
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }

                    Date dateLoginAuthActionValidDate = DateTime.convertDateStringToDate(kycAuthActionValidDate);

                    if (currentDate.before(dateLoginAuthActionValidDate)) {
                        Date dtNow = new Date();
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUSPENDED)
                                .putValue("display_message", "Sorry, your account is SUSPENDED from using " + AppConstants.strSACCOName + " due to many National ID Attempts. " +
                                                             "Please try again in " + DateTime.getPrettyDateTimeDifferenceRoundedUp(dtNow, dateLoginAuthActionValidDate))
                                .putValue("title", "Login Failed"));
                        return resultWrapper;
                    }
                    break;
                }
                case "LOCK": {

                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.LOCKED)
                            .putValue("display_message", "Sorry, your " + AppConstants.strSACCOName + " account is LOCKED due to many National ID Attempts.")
                            .putValue("title", "Login Failed"));
                    return resultWrapper;
                }
            }

            try {
                String strLastLogInDate = mobileBankingDetailsMap.getStringValue("last_login_date");
                String strStatus = mobileBankingDetailsMap.getStringValue("status");
                String strStatusDate = mobileBankingDetailsMap.getStringValue("status_date");

                if (strLastLogInDate != null) {
                    String strXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                    Document document = XmlUtils.parseXml(strXML);

                    String strTimeUnits = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/ACCOUNT_MAX_INACTIVE_PERIOD/@UNIT");
                    String strPeriod = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/ACCOUNT_MAX_INACTIVE_PERIOD");

                    Date dateLastLogin = DateTime.convertDateStringToDate(strLastLogInDate);
                    Date dateStatusDate = DateTime.convertDateStringToDate(strStatusDate);

                    Date expirationDate = DateTime.add(dateLastLogin, Integer.parseInt(strPeriod), DateTime.getPeriodUnit(strTimeUnits));
                    Date futureStatusDate = DateTime.add(dateStatusDate, Integer.parseInt(strPeriod), DateTime.getPeriodUnit(strTimeUnits));

                    Date dtCurrDate = new Date();

                    /*System.out.println("CURRENT DATE:     "+ dtCurrDate);
                    System.out.println("EXPIRATION DATE:  "+ expirationDate);
                    System.out.println("FUTURE DATE:      "+ futureStatusDate);*/

                    if (dtCurrDate.after(expirationDate) && dtCurrDate.after(futureStatusDate)) {

                        FlexicoreHashMap updateMap = new FlexicoreHashMap();
                        updateMap.putValue("status", "INACTIVE");
                        updateMap.putValue("status_start_date", null);
                        updateMap.putValue("status_end_date", null);
                        updateMap.putValue("date_modified", DateTime.getCurrentDateTime());

                        mobileBankingDetailsMap.copyFrom(updateMap);

                        String strIntegrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingDetailsMap);
                        updateMap.putValue("integrity_hash", strIntegrityHash);

                        Repository.update(StringRefs.SENTINEL,
                                TBL_MOBILE_BANKING_REGISTER,
                                updateMap,
                                new FilterPredicate("mobile_register_id = :mobile_register_id"),
                                new FlexicoreHashMap().addQueryArgument(":mobile_register_id", mobileBankingDetailsMap.getStringValue("mobile_register_id")));

                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INACTIVE)
                                .putValue("display_message", "Sorry, your account cannot use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB200" + getTrailerMessage())
                                .putValue("title", "Account is Inactive"));
                        return resultWrapper;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (theDeviceIdentifier != null && !theDeviceIdentifier.isBlank()) {
                if (theDeviceIdentifierType.equalsIgnoreCase("IMSI") || theDeviceIdentifierType.equalsIgnoreCase("ICCID")) {
                    String strSimIdentifier = mobileBankingDetailsMap.getStringValue("sim_identifier");
                    if (strSimIdentifier != null && !strSimIdentifier.equalsIgnoreCase(theDeviceIdentifier)) {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INVALID_IMSI)
                                .putValue("display_message", "Sorry, your SIM Card is not allowed to use " + AppConstants.strMobileBankingName + ".\n\nERR_MOB300" + getTrailerMessage())
                        );
                        return resultWrapper;
                    }
                } else if (theDeviceIdentifierType.equalsIgnoreCase("IMEI") || theDeviceIdentifierType.equalsIgnoreCase("APP_ID")) {
                    String strAppIdentifier = mobileBankingDetailsMap.getStringValue("app_identifier");

                    /*if (strAppIdentifier == null || strAppIdentifier.isBlank()) {
                        resultWrapper.setHasErrors(true);
                        resultWrapper.setData(new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INVALID_APP_ID)
                                .putValue("display_message", "Sorry, your Mobile Phone is not allowed to use " + AppConstants.strMobileBankingName + " services." + getTrailerMessage())
                                .putValue("title", "Mobile App not Activated"));
                        return resultWrapper;
                    }*/

                    if (!isActivation) {
                        if (strAppIdentifier == null || !strAppIdentifier.equalsIgnoreCase(theDeviceIdentifier)) {
                            resultWrapper.setHasErrors(true);
                            resultWrapper.setData(new FlexicoreHashMap()
                                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INVALID_APP_ID)
                                    .putValue("signatory_details", signatoryDetailsMap)
                                    .putValue("mobile_register_details", mobileBankingDetailsMap)
                                    .putValue("display_message", "Sorry, your Mobile Phone is not allowed to use " + AppConstants.strMobileBankingName + "." + getTrailerMessage())
                                    .putValue("title", "Mobile App not Activated")
                            );

                            return resultWrapper;
                        }
                    }
                }
            } else {

                if ((theDeviceIdentifierType.equalsIgnoreCase("IMEI") || theDeviceIdentifierType.equalsIgnoreCase("APP_ID")) && !isActivation) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INVALID_APP_ID)
                            .putValue("signatory_details", signatoryDetailsMap)
                            .putValue("mobile_register_details", mobileBankingDetailsMap)
                            .putValue("display_message", "Sorry, your Mobile Phone is not allowed to use " + AppConstants.strMobileBankingName + "." + getTrailerMessage())
                            .putValue("title", "Mobile App not Activated")
                    );

                    return resultWrapper;
                }
            }

            FlexicoreHashMap userMobileBankingDetailsMap = new FlexicoreHashMap();
            userMobileBankingDetailsMap.putValue("signatory_details", signatoryDetailsMap);
            userMobileBankingDetailsMap.putValue("mobile_register_details", mobileBankingDetailsMap);

            resultWrapper.setData(userMobileBankingDetailsMap);

        } catch (Exception e) {
            System.err.println("CBSAPI.checkUser(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "An error occurred"));

        }

        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getUserDetails(String theReferenceKey, String theIdentifierType, String theIdentifier, String theDeviceIdentifierType, String theDeviceIdentifier) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            FilterPredicate signatoryFilterPredicate = null;
            FlexicoreHashMap queryArguments = new FlexicoreHashMap();

            if ("MSISDN".equals(theIdentifierType)) {
                signatoryFilterPredicate = new FilterPredicate("primary_mobile_number = :primary_mobile_number");
                queryArguments.addQueryArgument(":primary_mobile_number", theIdentifier);
            } else {
                signatoryFilterPredicate = new FilterPredicate("primary_identity_type = :primary_identity_type AND primary_identity_no = :primary_identity_no");
                queryArguments.addQueryArgument(":primary_identity_type", theIdentifierType);
                queryArguments.addQueryArgument(":primary_identity_no", theIdentifier);
            }

            TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES, signatoryFilterPredicate, queryArguments);

            if (signatoryDetailsWrapper.hasErrors()) {
                signatoryDetailsWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);

                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later.")
                        .putValue("title", "An error occurred"));

                return signatoryDetailsWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = signatoryDetailsWrapper.getSingleRecord();

            if (signatoryDetailsMap == null || signatoryDetailsMap.isEmpty()) {
                signatoryDetailsWrapper.setHasErrors(true);
                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.NOT_FOUND)
                        .putValue("display_message", "Sorry, you are not registered to use " + AppConstants.strMobileBankingName + ".")
                        .putValue("title", "Account not found"));

                return signatoryDetailsWrapper;
            }
            return signatoryDetailsWrapper;
        }catch (Exception e){
            e.printStackTrace();
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getCurrentUserDetails(String theReferenceKey, String theIdentifierType, String theIdentifier, String theDeviceIdentifierType, String theDeviceIdentifier) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            FilterPredicate signatoryFilterPredicate = null;
            FlexicoreHashMap queryArguments = new FlexicoreHashMap();

            if ("MSISDN".equals(theIdentifierType)) {
                signatoryFilterPredicate = new FilterPredicate("primary_mobile_number = :primary_mobile_number");
                queryArguments.addQueryArgument(":primary_mobile_number", theIdentifier);
            } else {
                signatoryFilterPredicate = new FilterPredicate("primary_identity_type = :primary_identity_type AND primary_identity_no = :primary_identity_no");
                queryArguments.addQueryArgument(":primary_identity_type", theIdentifierType);
                queryArguments.addQueryArgument(":primary_identity_no", theIdentifier);
            }

            TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES, signatoryFilterPredicate, queryArguments);

            if (signatoryDetailsWrapper.displayQueriesExecuted().hasErrors()) {
                signatoryDetailsWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);

                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "An error occurred"));

                return signatoryDetailsWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = signatoryDetailsWrapper.getSingleRecord();

            if (signatoryDetailsMap == null || signatoryDetailsMap.isEmpty()) {
                signatoryDetailsWrapper.setHasErrors(true);
                signatoryDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.NOT_FOUND)
                        .putValue("display_message", "Sorry, you are not registered to use " + AppConstants.strMobileBankingName + "." + getTrailerMessage())
                        .putValue("title", "Account not found"));

                return signatoryDetailsWrapper;
            }

            String strSignatoryId = signatoryDetailsMap.getStringValue("signatory_id");

            TransactionWrapper<FlexicoreHashMap> mobileMappingDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_MOBILE_BANKING_REGISTER,
                    new FilterPredicate("signatory_id = :signatory_id"),
                    new FlexicoreHashMap().addQueryArgument(":signatory_id", strSignatoryId));

            if (mobileMappingDetailsWrapper.hasErrors()) {
                mobileMappingDetailsWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);

                mobileMappingDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "An error occurred"));

                return mobileMappingDetailsWrapper;
            }

            FlexicoreHashMap mobileBankingDetailsMap = mobileMappingDetailsWrapper.getSingleRecord();

            if (mobileBankingDetailsMap == null || mobileBankingDetailsMap.isEmpty()) {
                mobileMappingDetailsWrapper.setHasErrors(true);
                mobileMappingDetailsWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.NOT_FOUND)
                        .putValue("display_message", "Sorry, you are not registered to use " + AppConstants.strMobileBankingName + "." + getTrailerMessage())
                        .putValue("title", "An error occurred"));

                return signatoryDetailsWrapper;
            }

            FlexicoreHashMap userMobileBankingDetailsMap = new FlexicoreHashMap();
            userMobileBankingDetailsMap.putValue("signatory_details", signatoryDetailsMap);
            userMobileBankingDetailsMap.putValue("mobile_register_details", mobileBankingDetailsMap);

            resultWrapper.setData(userMobileBankingDetailsMap);

        } catch (Exception e) {
            System.err.println("CBSAPI.getCurrentUserDetails(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "An error occurred"));

        }

        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> userLogin(String theReferenceKey, String theIdentifierType, String theIdentifier, String thePIN, String theDeviceIdentifierType, String theDeviceIdentifier, USSDAPIConstants.MobileChannel theDevice) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                if (theDevice == USSDAPIConstants.MobileChannel.MOBILE_APP) {

                    if (!checkUserResultMap.getStringValueOrIfNull("cbs_api_return_val", "")
                            .equalsIgnoreCase("INVALID_APP_ID")) {

                        return checkUserResultMapWrapper;
                    }
                } else {
                    return checkUserResultMapWrapper;
                }
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            String strProperPIN = mobileBankingMap.getStringValue("pin");

            thePIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), thePIN);

            if (thePIN.equalsIgnoreCase(strProperPIN)) {
                String strPinSettings = SystemParameters.getParameter("PIN_PARAMETERS");
                Document document = XmlUtils.parseXml(strPinSettings);

                Element elPinSettings = XmlUtils.getElementNodeFromXpath(document, "/PIN_PARAMETERS/PIN[@TYPE='" + mobileBankingMap.getStringValue("pin_status") + "']");

                if (mobileBankingMap.getStringValue("pin_status").equalsIgnoreCase("ACTIVE")) {
                    String strExpiry = elPinSettings.getAttribute("EXPIRY");
                    String strExpiryTimeunit = elPinSettings.getAttribute("EXPIRY_TIMEUNIT");

                    Date datePinSetDate = DateTime.convertDateStringToDate(mobileBankingMap.getStringValue("pin_set_date"));

                    Date expirationDate = DateTime.add(datePinSetDate, Integer.parseInt(strExpiry), DateTime.getPeriodUnit(strExpiryTimeunit));

                    if (new Date().after(expirationDate)) {
                        resultWrapper.setHasErrors(true);

                        FlexicoreHashMap resultMap = new FlexicoreHashMap().putValue("end_session",
                                        USSDAPIConstants.Condition.YES)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.EXPIRED_PIN);

                        if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                            resultMap.putValue("display_message", "Your current PIN has expired. Please contact the SACCO to have your PIN reset.");
                        } else {
                            resultMap.putValue("display_message", "Your current Password has expired. Please contact the SACCO to have your PIN reset.");
                            resultMap.putValue("title", "Password Expired");
                        }

                        resultWrapper.setData(resultMap);

                        return resultWrapper;
                    }
                }

                theUpdateLoginParamsMap.put("login_attempts", 0);
                theUpdateLoginParamsMap.put("login_auth_action", "NONE");
                theUpdateLoginParamsMap.put("login_auth_action_valid_date", null);
                theUpdateLoginParamsMap.put("login_auth_flag", null);
                theUpdateLoginParamsMap.put("last_login_date", DateTime.getCurrentDateTime());
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                if (theDeviceIdentifier != null && !theDeviceIdentifier.isBlank()) {
                    if (theDeviceIdentifierType.equalsIgnoreCase("IMSI") || theDeviceIdentifierType.equalsIgnoreCase("ICCID")) {
                        String strSimIdentifier = mobileBankingMap.getStringValue("sim_identifier");

                        if (strSimIdentifier == null || strSimIdentifier.isBlank()) {
                            theUpdateLoginParamsMap.put("sim_identifier", theDeviceIdentifier);
                            theUpdateLoginParamsMap.put("sim_identifier_set_date", DateTime.getCurrentDateTime());
                        }

                    } /*else if (theDeviceIdentifierType.equalsIgnoreCase("IMEI") || theDeviceIdentifierType.equalsIgnoreCase("APP_ID")) {
                        String strAppIdentifier = mobileBankingMap.getStringValue("app_identifier");

                        if (strAppIdentifier == null || strAppIdentifier.isBlank()) {
                            theUpdateLoginParamsMap.put("app_identifier", theDeviceIdentifier);
                            theUpdateLoginParamsMap.put("app_identifier_set_date", DateTime.getCurrentDateTime());
                        }
                    }*/
                }

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                            .putValue("title", "Error"));
                }

                resultWrapper.setData(checkUserResultMap);

                if (theDevice == USSDAPIConstants.MobileChannel.MOBILE_APP) {
                    if (checkUserResultMap.getStringValueOrIfNull("cbs_api_return_val", "").equalsIgnoreCase("INVALID_APP_ID")) {
                        return checkUserResultMapWrapper;
                    }
                }

                return resultWrapper;
            } else {

                FlexicoreHashMap resultAuthMap = new FlexicoreHashMap();

                int loginAttempts = Integer.parseInt(mobileBankingMap.getStringValue("login_attempts"));
                loginAttempts = loginAttempts + 1;

                String strMessage;

                String strTitle = "Invalid Credentials";

                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                    strMessage = "{Sorry the PIN provided is NOT correct}\nPlease enter your PIN to proceed:";
                } else {
                    strMessage = "You have entered an incorrect username or password, please try again.";
                }

                USSDAPIConstants.Condition endSession = USSDAPIConstants.Condition.NO;

                resultAuthMap.putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INCORRECT_PIN)
                        .putValue("display_message", strMessage)
                        .putValue("title", strTitle);

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(resultAuthMap);

                String strFirstName = signatoryDetailsMap.getStringValue("full_name").trim().split("\\s")[0];

                HashMap<String, String> hmMSGPlaceholders = new HashMap<>();
                hmMSGPlaceholders.put("[MOBILE_NUMBER]", signatoryDetailsMap.getStringValue("primary_mobile_number"));
                hmMSGPlaceholders.put("[LOGIN_ATTEMPTS]", String.valueOf(loginAttempts));
                //hmMSGPlaceholders.put("[FIRST_NAME]", strFirstName);
                hmMSGPlaceholders.put("[FIRST_NAME]", "Member");

                String strPasswordAttemptParameters = SystemParameters.getParameter("MBANKING_AUTH_ATTEMPTS");

                HashMap<String, HashMap<String, String>> authenticationAttemptsAction = MBankingXMLFactory.getAuthenticationAttemptsAction(
                        loginAttempts,
                        hmMSGPlaceholders,
                        strPasswordAttemptParameters,
                        MBankingConstants.AuthType.PASSWORD);

                HashMap<String, String> currentAuthenticationAttemptsAction = authenticationAttemptsAction.get("CURRENT_ATTEMPT");
                HashMap<String, String> futureAuthenticationAttemptsAction = authenticationAttemptsAction.get("NEXT_ATTEMPT");

                //Check if action is needed
                if (!currentAuthenticationAttemptsAction.isEmpty()) {
                    String loginAction = currentAuthenticationAttemptsAction.get("ACTION");
                    String loginActionTag = currentAuthenticationAttemptsAction.get("NAME");

                    //Check action
                    switch (loginAction) {
                        case "SUSPEND": {
                            int loginActionDuration = Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION"));
                            String loginActionDurationUnit = currentAuthenticationAttemptsAction.get("UNIT");
                            loginActionDuration = DateTime.convertToSeconds(loginActionDuration, loginActionDurationUnit);
                            Date loginActionValidDate = DateTime.add(loginActionDuration, Calendar.SECOND);
                            String strLoginActionValidDate = DateTime.convertDateToDateString(loginActionValidDate);

                            theUpdateLoginParamsMap.put("login_attempts", loginAttempts);
                            theUpdateLoginParamsMap.put("login_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("login_auth_action_valid_date", strLoginActionValidDate);
                            theUpdateLoginParamsMap.put("login_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                            String friendlyActionDuration = currentAuthenticationAttemptsAction.get("DURATION") + " " + loginActionDurationUnit;
                            if (Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION")) != 1)
                                friendlyActionDuration = friendlyActionDuration + "S";

                            strTitle = "Account Suspended";
                            strMessage = "Your " + AppConstants.strMobileBankingName + " account has been SUSPENDED for " + friendlyActionDuration + " due to many Login Attempts. Please try again later.";
                            endSession = USSDAPIConstants.Condition.YES;

                            break;
                        }
                        case "LOCK": {

                            theUpdateLoginParamsMap.put("login_attempts", loginAttempts);
                            theUpdateLoginParamsMap.put("login_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("login_auth_action_valid_date", null);
                            theUpdateLoginParamsMap.put("login_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                            strTitle = "Account Locked";
                            strMessage = "Your " + AppConstants.strMobileBankingName + " account has been LOCKED due to many Login Attempts." + getTrailerMessage();
                            endSession = USSDAPIConstants.Condition.YES;

                            break;
                        }
                        default: {
                            theUpdateLoginParamsMap.put("login_attempts", loginAttempts);
                            theUpdateLoginParamsMap.put("login_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("login_auth_action_valid_date", null);
                            theUpdateLoginParamsMap.put("login_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());
                            endSession = USSDAPIConstants.Condition.YES;
                            break;
                        }
                    }
                }

                String currentLoginAction = currentAuthenticationAttemptsAction.get("ACTION");
                if (currentLoginAction == null) currentLoginAction = "NONE";

                if (!currentLoginAction.equals("LOCK")) {

                    if (!currentLoginAction.equals("SUSPEND")) {

                        //Check future action
                        if (!futureAuthenticationAttemptsAction.isEmpty()) {
                            String futureLoginAction = futureAuthenticationAttemptsAction.get("ACTION");
                            String futureLoginActionDurationUnit = futureAuthenticationAttemptsAction.get("UNIT");
                            String friendlyFutureActionDuration = futureAuthenticationAttemptsAction.get("DURATION") + " " + futureLoginActionDurationUnit;

                            String attemptsRemainingToFutureLoginAction = futureAuthenticationAttemptsAction.get("ATTEMPTS_REMAINING");

                            //Override Incorrect PIN message
                            if (futureLoginAction.equals("SUSPEND") && !currentLoginAction.equals("SUSPEND")) {
                                int intFutureActionDuration = Integer.parseInt(futureAuthenticationAttemptsAction.get("DURATION"));
                                if (intFutureActionDuration != 1)
                                    friendlyFutureActionDuration = friendlyFutureActionDuration + "S";

                                int intAttemptsRemainingToFutureLoginAction = Integer.parseInt(attemptsRemainingToFutureLoginAction);
                                String strAttempts = "attempt";
                                if (intAttemptsRemainingToFutureLoginAction != 1) strAttempts = strAttempts + "s";

                                strTitle = "Invalid Credentials";

                                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                                    strMessage = "{Sorry the PIN provided is NOT correct}\nYou have " + attemptsRemainingToFutureLoginAction + " attempt(s) before your " + AppConstants.strMobileBankingName + " account is SUSPENDED for " + friendlyFutureActionDuration + ".\nPlease enter your PIN:";
                                } else {
                                    strMessage = "You have " + attemptsRemainingToFutureLoginAction + " " + strAttempts + " before your " + AppConstants.strMobileBankingName + " account is SUSPENDED for " + friendlyFutureActionDuration + ".";
                                }

                                endSession = USSDAPIConstants.Condition.NO;

                            } else if (futureLoginAction.equals("LOCK")) {
                                int intAttemptsRemainingToFutureLoginAction = Integer.parseInt(attemptsRemainingToFutureLoginAction);
                                String strAttempts = "attempt";
                                if (intAttemptsRemainingToFutureLoginAction != 1) strAttempts = strAttempts + "s";

                                strTitle = "Invalid Credentials";
                                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                                    strMessage = "{Sorry the PIN provided is NOT correct}\nYou have " + attemptsRemainingToFutureLoginAction + " attempt(s) before your " + AppConstants.strMobileBankingName + " account is LOCKED.\nPlease enter your PIN:";

                                } else {
                                    strMessage = "You have " + attemptsRemainingToFutureLoginAction + " " + strAttempts + " before your " + AppConstants.strMobileBankingName + " account is LOCKED.";
                                }

                                endSession = USSDAPIConstants.Condition.NO;
                            }
                        }
                    }
                } else {

                    String primaryEmailAddress = signatoryDetailsMap.getStringValue("primary_email_address");

                    if (primaryEmailAddress != null && !primaryEmailAddress.isBlank()) {

                        int finalLoginAttempts = loginAttempts;
                        new Thread(() -> {
                            String strEmail = EmailTemplates.mobileBankingAccountLockedTemplate();
                            strEmail = strEmail.replace("[FULL_NAME]", signatoryDetailsMap.getStringValue("full_name"));
                            strEmail = strEmail.replace("[ATTEMPT_TYPE]", "Login");
                            strEmail = strEmail.replace("[ATTEMPTS]", String.valueOf(finalLoginAttempts));
                            strEmail = strEmail.replace("[PHONE_NUMBER]", signatoryDetailsMap.getStringValue("primary_mobile_number"));

                            EmailMessaging.sendEmail(primaryEmailAddress, "" + AppConstants.strMobileBankingName + " Account LOCKED", strEmail, "ACCOUNT_LOCKED");
                        }).start();
                    }
                }

                theUpdateLoginParamsMap.put("login_attempts", loginAttempts);
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                resultAuthMap.putValue("end_session", endSession);
                resultAuthMap.putValue("display_message", strMessage);
                resultAuthMap.putValue("title", strTitle);

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultAuthMap
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                            .putValue("title", "Error");

                }

                JvmManager.gc(checkUserResultMap, checkUserResultMapWrapper);

                return resultWrapper;
            }

        } catch (Exception e) {
            System.err.println("CBSAPI.userLogin(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "Error"));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> validateOTP(String theReferenceKey,
                                                                   String theIdentifierType,
                                                                   String theIdentifier,
                                                                   String theDeviceIdentifierType,
                                                                   String theDeviceIdentifier,
                                                                   MAPPAPIConstants.OTP_TYPE theOTPType,
                                                                   String theOTP) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier, true);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {

               /* USSDAPIConstants.StandardReturnVal theReturnVal = checkUserResultMapWrapper.getSingleRecord().getValue("cbs_api_return_val");

                System.out.println("theReturnVal:  "+theReturnVal);
                System.out.println("theOTPType:  "+theOTPType.name());


                if (theReturnVal == INVALID_APP_ID && theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) {
                    //continue
                }else{
                    return checkUserResultMapWrapper;
                }*/

                return checkUserResultMapWrapper;
            }


            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            String strProperOTP = mobileBankingMap.getStringValue("otp");
            String strOTExpiryDate = mobileBankingMap.getStringValueOrIfNull("otp_expiry_date", "1976-01-01 00:00:00.000000");

            boolean isValidOTP = false;
            if (strProperOTP != null) {

                LocalDateTime currDateTime = LocalDateTime.now();
                LocalDateTime expiryDateTime = LocalDateTime.parse(strOTExpiryDate, DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));

                theOTP = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theOTP);

                isValidOTP = currDateTime.isBefore(expiryDateTime) && theOTP.equalsIgnoreCase(strProperOTP);
            }

            if (isValidOTP) {
                theUpdateLoginParamsMap.put("otp", null);
                theUpdateLoginParamsMap.put("otp_expiry_date", null);
                theUpdateLoginParamsMap.put("otp_attempts", 0);
                theUpdateLoginParamsMap.put("otp_auth_action", "NONE");
                theUpdateLoginParamsMap.put("otp_auth_action_valid_date", null);
                theUpdateLoginParamsMap.put("otp_auth_flag", null);
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
                }

                resultWrapper.setData(checkUserResultMap);

                return resultWrapper;
            } else {

                FlexicoreHashMap resultAuthMap = new FlexicoreHashMap();

                int otpAttempts = Integer.parseInt(mobileBankingMap.getStringValue("otp_attempts"));
                otpAttempts = otpAttempts + 1;


                String strTitle = "Incorrect Activation Code";
                String strDescription = "The activation code you entered is either incorrect or has expired. Please confirm the activation code and try again.";

                if (theOTPType == MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL) {
                    strTitle = "Incorrect One Time Password";
                    strDescription = "You entered an incorrect/expired One Time Password";
                }

                USSDAPIConstants.Condition endSession = USSDAPIConstants.Condition.NO;

                resultAuthMap.putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INCORRECT_OTP)
                        .putValue("display_message", strDescription)
                        .putValue("title", strTitle);

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(resultAuthMap);

                String strFirstName = signatoryDetailsMap.getStringValue("full_name").trim();

                HashMap<String, String> hmMSGPlaceholders = new HashMap<>();
                hmMSGPlaceholders.put("[MOBILE_NUMBER]", signatoryDetailsMap.getStringValue("primary_mobile_number"));
                hmMSGPlaceholders.put("[OTP_ATTEMPTS]", String.valueOf(otpAttempts));
                //hmMSGPlaceholders.put("[FIRST_NAME]", strFirstName);
                hmMSGPlaceholders.put("[FIRST_NAME]", strFirstName);

                String strOTPAttemptParameters = SystemParameters.getParameter("MBANKING_AUTH_ATTEMPTS");

                HashMap<String, HashMap<String, String>> authenticationAttemptsAction = MBankingXMLFactory.getAuthenticationAttemptsAction(
                        otpAttempts,
                        hmMSGPlaceholders,
                        strOTPAttemptParameters,
                        MBankingConstants.AuthType.OTP);

                HashMap<String, String> currentAuthenticationAttemptsAction = authenticationAttemptsAction.get("CURRENT_ATTEMPT");
                HashMap<String, String> futureAuthenticationAttemptsAction = authenticationAttemptsAction.get("NEXT_ATTEMPT");

                //Check if action is needed
                if (!currentAuthenticationAttemptsAction.isEmpty()) {
                    String loginAction = currentAuthenticationAttemptsAction.get("ACTION");
                    String loginActionTag = currentAuthenticationAttemptsAction.get("NAME");
                    String resetOTP = currentAuthenticationAttemptsAction.get("RESET_OTP");

                    //Check action
                    switch (loginAction) {
                        case "SUSPEND": {
                            int loginActionDuration = Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION"));
                            String loginActionDurationUnit = currentAuthenticationAttemptsAction.get("UNIT");
                            loginActionDuration = DateTime.convertToSeconds(loginActionDuration, loginActionDurationUnit);
                            Date loginActionValidDate = DateTime.add(loginActionDuration, Calendar.SECOND);
                            String strLoginActionValidDate = DateTime.convertDateToDateString(loginActionValidDate);

                            if (resetOTP.equalsIgnoreCase("YES")) {
                                theUpdateLoginParamsMap.put("otp", null);
                                theUpdateLoginParamsMap.put("otp_expiry_date", null);
                            }

                            theUpdateLoginParamsMap.put("otp_attempts", otpAttempts);
                            theUpdateLoginParamsMap.put("otp_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("otp_auth_action_valid_date", strLoginActionValidDate);
                            theUpdateLoginParamsMap.put("otp_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                            String friendlyActionDuration = currentAuthenticationAttemptsAction.get("DURATION") + " " + loginActionDurationUnit;
                            if (Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION")) != 1)
                                friendlyActionDuration = friendlyActionDuration + "S";

                            strTitle = "Account Suspended";
                            strDescription = "Your " + AppConstants.strMobileBankingName + " account has been SUSPENDED for " + friendlyActionDuration + " due to many OTP Attempts." + getTrailerMessage();
                            endSession = USSDAPIConstants.Condition.YES;

                            break;
                        }
                        case "LOCK": {

                            if (resetOTP.equalsIgnoreCase("YES")) {
                                theUpdateLoginParamsMap.put("otp", null);
                                theUpdateLoginParamsMap.put("otp_expiry_date", null);
                            }

                            theUpdateLoginParamsMap.put("otp_attempts", otpAttempts);
                            theUpdateLoginParamsMap.put("otp_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("otp_auth_action_valid_date", null);
                            theUpdateLoginParamsMap.put("otp_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                            strTitle = "Account Locked";
                            strDescription = "Your " + AppConstants.strMobileBankingName + " account has been LOCKED due to many OTP Attempts." + getTrailerMessage();
                            endSession = USSDAPIConstants.Condition.YES;

                            break;
                        }
                        default: {
                            theUpdateLoginParamsMap.put("otp_attempts", otpAttempts);
                            theUpdateLoginParamsMap.put("otp_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("otp_auth_action_valid_date", null);
                            theUpdateLoginParamsMap.put("otp_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());
                            endSession = USSDAPIConstants.Condition.YES;
                            break;
                        }
                    }
                }

                String currentLoginAction = currentAuthenticationAttemptsAction.get("ACTION");
                if (currentLoginAction == null) currentLoginAction = "NONE";

                if (!currentLoginAction.equals("LOCK")) {

                    if (!currentLoginAction.equals("SUSPEND")) {

                        //Check future action
                        if (!futureAuthenticationAttemptsAction.isEmpty()) {
                            String futureLoginAction = futureAuthenticationAttemptsAction.get("ACTION");
                            String futureLoginActionDurationUnit = futureAuthenticationAttemptsAction.get("UNIT");
                            String friendlyFutureActionDuration = futureAuthenticationAttemptsAction.get("DURATION") + " " + futureLoginActionDurationUnit;

                            String attemptsRemainingToFutureLoginAction = futureAuthenticationAttemptsAction.get("ATTEMPTS_REMAINING");

                            //Override Incorrect PIN message
                            if (futureLoginAction.equals("SUSPEND") && !currentLoginAction.equals("SUSPEND")) {
                                int intFutureActionDuration = Integer.parseInt(futureAuthenticationAttemptsAction.get("DURATION"));
                                if (intFutureActionDuration != 1)
                                    friendlyFutureActionDuration = friendlyFutureActionDuration + "S";

                                int intAttemptsRemainingToFutureLoginAction = Integer.parseInt(attemptsRemainingToFutureLoginAction);
                                String strAttempts = "attempt";
                                if (intAttemptsRemainingToFutureLoginAction != 1) strAttempts = strAttempts + "s";

                                strTitle = ((theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) ? "Incorrect Activation Code" : "Incorrect One Time Password");

                                strDescription = "You have " + attemptsRemainingToFutureLoginAction + " " + strAttempts + " before your " + AppConstants.strMobileBankingName + " account is SUSPENDED for " + friendlyFutureActionDuration + ".";

                                endSession = USSDAPIConstants.Condition.NO;

                            } else if (futureLoginAction.equals("LOCK")) {
                                int intAttemptsRemainingToFutureLoginAction = Integer.parseInt(attemptsRemainingToFutureLoginAction);
                                String strAttempts = "attempt";
                                if (intAttemptsRemainingToFutureLoginAction != 1) strAttempts = strAttempts + "s";

                                strTitle = ((theOTPType == MAPPAPIConstants.OTP_TYPE.ACTIVATION) ? "Incorrect Activation Code" : "Incorrect One Time Password");
                                strDescription = "You have " + attemptsRemainingToFutureLoginAction + " " + strAttempts + " before your " + AppConstants.strMobileBankingName + " account is LOCKED.";

                                endSession = USSDAPIConstants.Condition.NO;
                            }
                        }
                    }
                } else {
                    String primaryEmailAddress = signatoryDetailsMap.getStringValue("primary_email_address");

                    if (primaryEmailAddress != null && !primaryEmailAddress.isBlank()) {

                        int finalOtpAttempts = otpAttempts;
                        new Thread(() -> {
                            String strEmail = EmailTemplates.mobileBankingAccountLockedTemplate();
                            strEmail = strEmail.replace("[FULL_NAME]", signatoryDetailsMap.getStringValue("full_name"));
                            strEmail = strEmail.replace("[ATTEMPT_TYPE]", "OTP");
                            strEmail = strEmail.replace("[ATTEMPTS]", String.valueOf(finalOtpAttempts));
                            strEmail = strEmail.replace("[PHONE_NUMBER]", signatoryDetailsMap.getStringValue("primary_mobile_number"));

                            EmailMessaging.sendEmail(primaryEmailAddress, "" + AppConstants.strMobileBankingName + " Account LOCKED", strEmail, "ACCOUNT_LOCKED");
                        }).start();
                    }
                }

                theUpdateLoginParamsMap.put("otp_attempts", otpAttempts);
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                resultAuthMap.putValue("end_session", endSession);
                resultAuthMap.putValue("display_message", strDescription);
                resultAuthMap.putValue("title", strTitle);

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultAuthMap
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                            .putValue("title", "Error");
                }

                resultAuthMap.putValue("mobile_register_details", mobileBankingMap);

                JvmManager.gc(checkUserResultMap, checkUserResultMapWrapper);

                return resultWrapper;
            }


        } catch (Exception e) {
            System.err.println("CBSAPI.validateOTP(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "Error"));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> isCorrectPIN(String theReferenceKey, String theIdentifierType, String theIdentifier,
                                                                    String thePIN, String theDeviceIdentifierType,
                                                                    String theDeviceIdentifier, USSDAPIConstants.MobileChannel theDevice) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            String strProperPIN = mobileBankingMap.getStringValue("pin");
            thePIN = MobileBankingCryptography.hashPIN(theIdentifier, thePIN);

            if (thePIN.equalsIgnoreCase(strProperPIN)) {
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.Condition.YES));

            } else {
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.Condition.NO));
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.isCorrectPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> isValidKYCDetails(String theReferenceKey, String theIdentifierType,
                                                                         String theIdentifier,
                                                                         String theDeviceIdentifierType,
                                                                         String theDeviceIdentifier, String thePrimaryIdentifierType,
                                                                         String thePrimaryIdentifier,
                                                                         USSDAPIConstants.MobileChannel theDevice) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            String primaryIdentityNo = signatoryMap.getStringValue("primary_identity_no");

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            if (primaryIdentityNo.equalsIgnoreCase(thePrimaryIdentifier)) {

                theUpdateLoginParamsMap.put("kyc_attempts", 0);
                theUpdateLoginParamsMap.put("kyc_auth_action", "NONE");
                theUpdateLoginParamsMap.put("kyc_auth_action_valid_date", null);
                theUpdateLoginParamsMap.put("kyc_auth_flag", null);
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                            .putValue("title", "Error"));

                    return resultWrapper;
                }

                resultWrapper.setHasErrors(false);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.Condition.YES));

                return resultWrapper;

            } else {

                FlexicoreHashMap resultAuthMap = new FlexicoreHashMap();

                int kycAttempts = Integer.parseInt(mobileBankingMap.getStringValue("kyc_attempts"));
                kycAttempts = kycAttempts + 1;

                String strMessage;

                String strTitle = "Invalid ID Number";

                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                    strMessage = "{Incorrect ID Number Provided}\nEnter your National ID:";
                } else {
                    strMessage = "You have entered an incorrect National ID, please try again.";
                }

                USSDAPIConstants.Condition endSession = USSDAPIConstants.Condition.NO;

                resultAuthMap.putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INCORRECT_PIN)
                        .putValue("display_message", strMessage)
                        .putValue("title", strTitle);

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(resultAuthMap);

                String strFirstName = signatoryMap.getStringValue("full_name").trim().split("\\s")[0];

                HashMap<String, String> hmMSGPlaceholders = new HashMap<>();
                hmMSGPlaceholders.put("[MOBILE_NUMBER]", signatoryMap.getStringValue("primary_mobile_number"));
                hmMSGPlaceholders.put("[LOGIN_ATTEMPTS]", String.valueOf(kycAttempts));
                //hmMSGPlaceholders.put("[FIRST_NAME]", strFirstName);
                hmMSGPlaceholders.put("[FIRST_NAME]", "Member");

                String strPasswordAttemptParameters = SystemParameters.getParameter("MBANKING_AUTH_ATTEMPTS");

                HashMap<String, HashMap<String, String>> authenticationAttemptsAction = MBankingXMLFactory.getAuthenticationAttemptsAction(
                        kycAttempts,
                        hmMSGPlaceholders,
                        strPasswordAttemptParameters,
                        MBankingConstants.AuthType.PASSWORD);

                HashMap<String, String> currentAuthenticationAttemptsAction = authenticationAttemptsAction.get("CURRENT_ATTEMPT");
                HashMap<String, String> futureAuthenticationAttemptsAction = authenticationAttemptsAction.get("NEXT_ATTEMPT");

                //Check if action is needed
                if (!currentAuthenticationAttemptsAction.isEmpty()) {
                    String loginAction = currentAuthenticationAttemptsAction.get("ACTION");
                    String loginActionTag = currentAuthenticationAttemptsAction.get("NAME");

                    //Check action
                    switch (loginAction) {
                        case "SUSPEND": {
                            int loginActionDuration = Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION"));
                            String loginActionDurationUnit = currentAuthenticationAttemptsAction.get("UNIT");
                            loginActionDuration = DateTime.convertToSeconds(loginActionDuration, loginActionDurationUnit);
                            Date loginActionValidDate = DateTime.add(loginActionDuration, Calendar.SECOND);
                            String strLoginActionValidDate = DateTime.convertDateToDateString(loginActionValidDate);

                            theUpdateLoginParamsMap.put("kyc_attempts", kycAttempts);
                            theUpdateLoginParamsMap.put("kyc_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("kyc_auth_action_valid_date", strLoginActionValidDate);
                            theUpdateLoginParamsMap.put("kyc_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                            String friendlyActionDuration = currentAuthenticationAttemptsAction.get("DURATION") + " " + loginActionDurationUnit;
                            if (Integer.parseInt(currentAuthenticationAttemptsAction.get("DURATION")) != 1)
                                friendlyActionDuration = friendlyActionDuration + "S";

                            strTitle = "Account Suspended";
                            strMessage = "Your " + AppConstants.strMobileBankingName + " account has been SUSPENDED for " + friendlyActionDuration + " due to many National ID Attempts. Please try again later.";
                            endSession = USSDAPIConstants.Condition.YES;

                            break;
                        }
                        case "LOCK": {

                            theUpdateLoginParamsMap.put("kyc_attempts", kycAttempts);
                            theUpdateLoginParamsMap.put("kyc_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("kyc_auth_action_valid_date", null);
                            theUpdateLoginParamsMap.put("kyc_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                            strTitle = "Account Locked";
                            strMessage = "Your " + AppConstants.strMobileBankingName + " account has been LOCKED due to many National ID Attempts." + getTrailerMessage();
                            endSession = USSDAPIConstants.Condition.YES;

                            break;
                        }
                        default: {
                            theUpdateLoginParamsMap.put("kyc_attempts", kycAttempts);
                            theUpdateLoginParamsMap.put("kyc_auth_action", loginAction);
                            theUpdateLoginParamsMap.put("kyc_auth_action_valid_date", null);
                            theUpdateLoginParamsMap.put("kyc_auth_flag", loginActionTag);
                            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());
                            endSession = USSDAPIConstants.Condition.YES;
                            break;
                        }
                    }
                }

                String currentLoginAction = currentAuthenticationAttemptsAction.get("ACTION");
                if (currentLoginAction == null) currentLoginAction = "NONE";

                if (!currentLoginAction.equals("LOCK")) {

                    if (!currentLoginAction.equals("SUSPEND")) {

                        //Check future action
                        if (!futureAuthenticationAttemptsAction.isEmpty()) {
                            String futureLoginAction = futureAuthenticationAttemptsAction.get("ACTION");
                            String futureLoginActionDurationUnit = futureAuthenticationAttemptsAction.get("UNIT");
                            String friendlyFutureActionDuration = futureAuthenticationAttemptsAction.get("DURATION") + " " + futureLoginActionDurationUnit;

                            String attemptsRemainingToFutureLoginAction = futureAuthenticationAttemptsAction.get("ATTEMPTS_REMAINING");

                            //Override Incorrect PIN message
                            if (futureLoginAction.equals("SUSPEND") && !currentLoginAction.equals("SUSPEND")) {
                                int intFutureActionDuration = Integer.parseInt(futureAuthenticationAttemptsAction.get("DURATION"));
                                if (intFutureActionDuration != 1)
                                    friendlyFutureActionDuration = friendlyFutureActionDuration + "S";

                                int intAttemptsRemainingToFutureLoginAction = Integer.parseInt(attemptsRemainingToFutureLoginAction);
                                String strAttempts = "attempt";
                                if (intAttemptsRemainingToFutureLoginAction != 1) strAttempts = strAttempts + "s";

                                strTitle = "Invalid Credentials";

                                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                                    strMessage = "{Incorrect ID Number Provided}\nYou have " + attemptsRemainingToFutureLoginAction + " attempt(s) before your " + AppConstants.strMobileBankingName + " account is SUSPENDED for " + friendlyFutureActionDuration + ".\nPlease enter your National ID:";
                                } else {
                                    strMessage = "You have " + attemptsRemainingToFutureLoginAction + " " + strAttempts + " before your " + AppConstants.strMobileBankingName + " account is SUSPENDED for " + friendlyFutureActionDuration + ".";
                                }

                                endSession = USSDAPIConstants.Condition.NO;

                            } else if (futureLoginAction.equals("LOCK")) {
                                int intAttemptsRemainingToFutureLoginAction = Integer.parseInt(attemptsRemainingToFutureLoginAction);
                                String strAttempts = "attempt";
                                if (intAttemptsRemainingToFutureLoginAction != 1) strAttempts = strAttempts + "s";

                                strTitle = "Invalid ID Number";
                                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                                    strMessage = "{Incorrect ID Number Provided}\nYou have " + attemptsRemainingToFutureLoginAction + " attempt(s) before your " + AppConstants.strMobileBankingName + " account is LOCKED.\nPlease enter your National ID:";

                                } else {
                                    strMessage = "You have " + attemptsRemainingToFutureLoginAction + " " + strAttempts + " before your " + AppConstants.strMobileBankingName + " account is LOCKED.";
                                }

                                endSession = USSDAPIConstants.Condition.NO;
                            }
                        }
                    }

                } else {

                    String primaryEmailAddress = signatoryMap.getStringValue("primary_email_address");

                    if (primaryEmailAddress != null && !primaryEmailAddress.isBlank()) {

                        int finalLoginAttempts = kycAttempts;
                        new Thread(() -> {
                            String strEmail = EmailTemplates.mobileBankingAccountLockedTemplate();
                            strEmail = strEmail.replace("[FULL_NAME]", signatoryMap.getStringValue("full_name"));
                            strEmail = strEmail.replace("[ATTEMPT_TYPE]", "National ID");
                            strEmail = strEmail.replace("[ATTEMPTS]", String.valueOf(finalLoginAttempts));
                            strEmail = strEmail.replace("[PHONE_NUMBER]", signatoryMap.getStringValue("primary_mobile_number"));

                            EmailMessaging.sendEmail(primaryEmailAddress, "" + AppConstants.strMobileBankingName + " Account LOCKED", strEmail, "ACCOUNT_LOCKED");
                        }).start();
                    }
                }

                theUpdateLoginParamsMap.put("kyc_attempts", kycAttempts);
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

                resultAuthMap.putValue("end_session", endSession);
                resultAuthMap.putValue("display_message", strMessage);
                resultAuthMap.putValue("title", strTitle);

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultAuthMap
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                            .putValue("title", "Error");

                }
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.isValidKYCDetails(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    /*public static TransactionWrapper<FlexicoreHashMap> isValidKYCDetails(String theReferenceKey, String theIdentifierType,
                                                                         String theIdentifier,
                                                                         String theDeviceIdentifierType,
                                                                         String theDeviceIdentifier, String thePrimaryIdentifierType,
                                                                         String thePrimaryIdentifier) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            String primaryIdentityNo = signatoryMap.getStringValue("primary_identity_no");

            if (primaryIdentityNo.equalsIgnoreCase(thePrimaryIdentifier)) {




                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.Condition.YES));




            } else {
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.Condition.NO));
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.isValidKYCDetails(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    */


    public static TransactionWrapper<FlexicoreHashMap> changeUserPIN(String theReferenceKey, String theIdentifierType, String theIdentifier,
                                                                     String thePIN,
                                                                     String theNewPIN, String theDeviceIdentifierType,
                                                                     String theDeviceIdentifier, USSDAPIConstants.MobileChannel theDevice) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            String strProperPIN = mobileBankingMap.getStringValue("pin");
            thePIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), thePIN);
            if (thePIN.equalsIgnoreCase(strProperPIN)) {

                if (theNewPIN.equalsIgnoreCase(thePIN)) {

                    String strMessage;
                    if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                        strMessage = "Change PIN\n{Invalid New PIN}\nYour new PIN cannot be the same as your previous PIN.\nPlease enter your New PIN:";
                    } else {
                        strMessage = "Your new password cannot be the same as your previous password.";
                    }

                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.NO)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INVALID_NEW_PIN)
                            .putValue("display_message", strMessage)
                            .putValue("title", "Invalid New Password"))
                    ;

                    return resultWrapper;
                }


                String strNewPIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theNewPIN);

                String previousPins = mobileBankingMap.getStringValueOrIfNull("previous_pins", "<PREVIOUS_PINS/>");

                try {
                    Document docPrevPasswords = XmlUtils.parseXml(previousPins);

                    Element elPassword = docPrevPasswords.createElement("PIN");
                    elPassword.setTextContent(strNewPIN);
                    docPrevPasswords.getDocumentElement().appendChild(elPassword);

                    previousPins = XmlUtils.convertXmlDocToStr(docPrevPasswords);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                theUpdateLoginParamsMap.put("previous_pins", previousPins);

                theUpdateLoginParamsMap.put("pin", strNewPIN);
                theUpdateLoginParamsMap.put("pin_set_date", DateTime.getCurrentDateTime());
                theUpdateLoginParamsMap.put("pin_status", "ACTIVE");
                theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

               /* String strPreviousPinStatus = mobileBankingMap.getStringValue("pin_status");
                if (strPreviousPinStatus.equalsIgnoreCase("RESET")) {
                    theUpdateLoginParamsMap.put("ussd_activation_kyc", "ENABLED");
                    theUpdateLoginParamsMap.put("ussd_activation_kyc_set_date", null);
                    theUpdateLoginParamsMap.put("app_activation_kyc", "ENABLED");
                    theUpdateLoginParamsMap.put("app_activation_kyc_set_date", null);
                }*/

                mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

                String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
                theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> updateWrapper = Repository.update(
                        StringRefs.SENTINEL,
                        TBL_MOBILE_BANKING_REGISTER,
                        theUpdateLoginParamsMap,
                        new FilterPredicate("mobile_register_id = :mobile_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
                );

                if (updateWrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.YES)
                            .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                            .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                            .putValue("title", "ERROR: Change Password"));
                } else {
                    resultWrapper.setData(checkUserResultMap);
                }

                return resultWrapper;
            } else {

                String strMessage;
                if (theDevice == USSDAPIConstants.MobileChannel.USSD) {
                    strMessage = "Change PIN\n{Please enter a valid current PIN}\nEnter your current PIN:";
                } else {
                    strMessage = "Incorrect password. Please enter a valid current password.";
                }

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(
                        new FlexicoreHashMap()
                                .putValue("end_session", USSDAPIConstants.Condition.NO)
                                .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.INCORRECT_PIN)
                                .putValue("display_message", strMessage)
                                .putValue("title", "Incorrect Current Password")
                );

                JvmManager.gc(checkUserResultMap, checkUserResultMapWrapper);
                return resultWrapper;
            }

        } catch (Exception e) {
            System.err.println("CBSAPI.changeUserPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "ERROR: Change Password"));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> setUserPIN(String theReferenceKey, String theIdentifierType, String theIdentifier, String theNewPIN,
                                                                  String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");


            String strNewPIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theNewPIN);


            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
            theUpdateLoginParamsMap.put("pin", strNewPIN);
            theUpdateLoginParamsMap.put("pin_set_date", DateTime.getCurrentDateTime());
            theUpdateLoginParamsMap.put("ussd_activation_kyc", "DISABLED");
            theUpdateLoginParamsMap.put("ussd_activation_kyc_set_date", DateTime.getCurrentDateTime());

            if (mobileBankingMap.getStringValue("ussd_activation_date") == null) {
                theUpdateLoginParamsMap.put("ussd_activation_date", DateTime.getCurrentDateTime());
            }

            if (mobileBankingMap.getStringValue("accepted_terms_and_conditions").equalsIgnoreCase("NO")) {
                theUpdateLoginParamsMap.put("accepted_terms_and_conditions", "YES");
                theUpdateLoginParamsMap.put("channel_accepted_terms_and_conditions", "USSD");
                theUpdateLoginParamsMap.put("date_accepted_terms_and_conditions", DateTime.getCurrentDateTime());
            }

            String previousPins = mobileBankingMap.getStringValueOrIfNull("previous_pins", "<PREVIOUS_PINS/>");

            try {
                Document docPrevPasswords = XmlUtils.parseXml(previousPins);

                Element elPassword = docPrevPasswords.createElement("PIN");
                elPassword.setTextContent(strNewPIN);
                docPrevPasswords.getDocumentElement().appendChild(elPassword);

                previousPins = XmlUtils.convertXmlDocToStr(docPrevPasswords);

            } catch (Exception e) {
                e.printStackTrace();
            }

            theUpdateLoginParamsMap.put("previous_pins", previousPins);

            theUpdateLoginParamsMap.put("pin_status", "ACTIVE");
            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
            } else {
                resultWrapper.setData(checkUserResultMap);
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.setPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> setUserPIN_MIGRATE(String theReferenceKey, String theIdentifierType, String theIdentifier, String theNewPIN,
                                                                          String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");


            String strNewPIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theNewPIN);


            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
            theUpdateLoginParamsMap.put("pin", strNewPIN);
            theUpdateLoginParamsMap.put("pin_set_date", DateTime.getCurrentDateTime());
            theUpdateLoginParamsMap.put("pin_status", "ACTIVE");
            theUpdateLoginParamsMap.put("ussd_activation_kyc", "DISABLED");
            theUpdateLoginParamsMap.put("ussd_activation_kyc_set_date", DateTime.getCurrentDateTime());

            if (mobileBankingMap.getStringValue("ussd_activation_date") == null) {
                theUpdateLoginParamsMap.put("ussd_activation_date", DateTime.getCurrentDateTime());
            }

            if (mobileBankingMap.getStringValue("accepted_terms_and_conditions").equalsIgnoreCase("NO")) {
                theUpdateLoginParamsMap.put("accepted_terms_and_conditions", "YES");
                theUpdateLoginParamsMap.put("channel_accepted_terms_and_conditions", "USSD");
                theUpdateLoginParamsMap.put("date_accepted_terms_and_conditions", DateTime.getCurrentDateTime());
            }

            String previousPins = mobileBankingMap.getStringValueOrIfNull("previous_pins", "<PREVIOUS_PINS/>");

            try {
                Document docPrevPasswords = XmlUtils.parseXml(previousPins);

                Element elPassword = docPrevPasswords.createElement("PIN");
                elPassword.setTextContent(strNewPIN);
                docPrevPasswords.getDocumentElement().appendChild(elPassword);

                previousPins = XmlUtils.convertXmlDocToStr(docPrevPasswords);

            } catch (Exception e) {
                e.printStackTrace();
            }

            theUpdateLoginParamsMap.put("previous_pins", previousPins);

            theUpdateLoginParamsMap.put("pin_status", "ACTIVE");
            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
            } else {
                resultWrapper.setData(checkUserResultMap);
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.setPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> forceKYC_MIGRATE(String theReferenceKey, String theIdentifierType, String theIdentifier, String theNewPIN,
                                                                        String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");


            //String strNewPIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theNewPIN);


            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
            //theUpdateLoginParamsMap.put("pin", strNewPIN);
            //theUpdateLoginParamsMap.put("pin_set_date", DateTime.getCurrentDateTime());
            theUpdateLoginParamsMap.put("pin_status", "RESET");
            theUpdateLoginParamsMap.put("ussd_activation_kyc", "ENABLED");
            theUpdateLoginParamsMap.put("ussd_activation_kyc_set_date", DateTime.getCurrentDateTime());

            if (mobileBankingMap.getStringValue("ussd_activation_date") == null) {
                theUpdateLoginParamsMap.put("ussd_activation_date", DateTime.getCurrentDateTime());
            }

            if (mobileBankingMap.getStringValue("accepted_terms_and_conditions").equalsIgnoreCase("NO")) {
                theUpdateLoginParamsMap.put("accepted_terms_and_conditions", "YES");
                theUpdateLoginParamsMap.put("channel_accepted_terms_and_conditions", "USSD");
                theUpdateLoginParamsMap.put("date_accepted_terms_and_conditions", DateTime.getCurrentDateTime());
            }

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
            } else {
                resultWrapper.setData(checkUserResultMap);
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.setPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }
    public static TransactionWrapper<FlexicoreHashMap> disableKYC_MIGRATE(String theReferenceKey, String theIdentifierType, String theIdentifier, String theNewPIN,
                                                                          String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");


            //String strNewPIN = MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theNewPIN);


            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
            //theUpdateLoginParamsMap.put("pin", strNewPIN);
            //theUpdateLoginParamsMap.put("pin_set_date", DateTime.getCurrentDateTime());
            theUpdateLoginParamsMap.put("pin_status", "ACTIVE");
            theUpdateLoginParamsMap.put("ussd_activation_kyc", "DISABLED");
            theUpdateLoginParamsMap.put("ussd_activation_kyc_set_date", DateTime.getCurrentDateTime());

            if (mobileBankingMap.getStringValue("ussd_activation_date") == null) {
                theUpdateLoginParamsMap.put("ussd_activation_date", DateTime.getCurrentDateTime());
            }

            if (mobileBankingMap.getStringValue("accepted_terms_and_conditions").equalsIgnoreCase("NO")) {
                theUpdateLoginParamsMap.put("accepted_terms_and_conditions", "YES");
                theUpdateLoginParamsMap.put("channel_accepted_terms_and_conditions", "USSD");
                theUpdateLoginParamsMap.put("date_accepted_terms_and_conditions", DateTime.getCurrentDateTime());
            }

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
            } else {
                resultWrapper.setData(checkUserResultMap);
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.setPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> activateMobileApp(String theReferenceKey, String theIdentifierType, String theIdentifier,
                                                                         String theDeviceIdentifierType, String theDeviceIdentifier, FlexicoreHashMap mobileBankingMap) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            theUpdateLoginParamsMap.put("app_identifier", theDeviceIdentifier);
            theUpdateLoginParamsMap.put("app_identifier_set_date", DateTime.getCurrentDateTime());

            theUpdateLoginParamsMap.put("app_activation_kyc", "DISABLED");
            theUpdateLoginParamsMap.put("app_activation_kyc_set_date", DateTime.getCurrentDateTime());
            if (mobileBankingMap.getStringValue("app_activation_date") == null) {
                theUpdateLoginParamsMap.put("app_activation_date", DateTime.getCurrentDateTime());
            }

            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                System.err.println("CBSAPI.activateMobileApp() - Update ERROR: " + updateWrapper.getErrors() + "\n");

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "An error occurred"));
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.activateMobileApp(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "An error occurred"));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> acceptTermsAndConditions(FlexicoreHashMap mobileBankingMap, USSDAPIConstants.MobileChannel theDevice) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            theUpdateLoginParamsMap.put("accepted_terms_and_conditions", "YES");
            theUpdateLoginParamsMap.put("channel_accepted_terms_and_conditions", theDevice.getValue());
            theUpdateLoginParamsMap.put("date_accepted_terms_and_conditions", DateTime.getCurrentDateTime());
            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                System.err.println("CBSAPI.acceptTermsAndConditions() - Update ERROR: " + updateWrapper.getErrors() + "\n");

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "An error occurred"));
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.acceptTermsAndConditions(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "An error occurred"));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> deactivateMobileApp(String theReferenceKey, String theIdentifierType, String theIdentifier,
                                                                           String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();

            theUpdateLoginParamsMap.put("app_identifier", null);
            theUpdateLoginParamsMap.put("app_identifier_set_date", null);

            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                System.err.println("CBSAPI.deactivateMobileApp() - Update ERROR: " + updateWrapper.getErrors() + "\n");

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "An error occurred"));
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.deactivateMobileApp(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("title", "An error occurred"));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getSignatoryCustomersList(String theReferenceKey, String theIdentifierType, String theIdentifier, String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");
            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            QueryBuilder queryBuilder = new QueryBuilder()
                    .select()
                    .selectColumn("cr.*,\n" +
                                  "       mscm.mandates")
                    .from()
                    .joinPhrase(TBL_MOBILE_SIGNATORY_CUSTOMER_MAPPING + " mscm\n" +
                                "   LEFT JOIN " + TBL_CUSTOMER_REGISTER + " cr ON mscm.customer_register_id = cr.customer_register_id")
                    .where("mscm.mobile_register_id = :mobile_register_id");


            TransactionWrapper<FlexicoreArrayList> customersListWrapper = Repository.joinSelectQuery(
                    StringRefs.SENTINEL,
                    queryBuilder,
                    new FlexicoreHashMap().addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (customersListWrapper.hasErrors()) {
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            FlexicoreArrayList customersList = customersListWrapper.getData();

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", customersList));

            return resultWrapper;
        } catch (Exception e) {
            System.err.println("CBSAPI.getSignatoryCustomersList(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getCustomerDetailsFromCBS(String strRequestingMobileNumber,
                                                                          String theCustomerIdentifierType,
                                                                          String theCustomerIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> customerDetailsWrapper = ApStarCBS.getMemberDetails(theCustomerIdentifierType, theCustomerIdentifier);

            if (customerDetailsWrapper.hasErrors() && customerDetailsWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCustomerDetailsFromCBS() - " + customerDetailsWrapper.getErrors() + "\n" + customerDetailsWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(customerDetailsWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (customerDetailsWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry, member not found.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, member not found. Please try again"));

                return resultWrapper;
            }

            resultWrapper.setData(customerDetailsWrapper.getSingleRecord());

            return resultWrapper;
        } catch (Exception e) {
            System.err.println("CBSAPI.getCustomerDetails(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getCustomerAccounts(String strRequestingMobileNumber,
                                                                           String theIdentifierType,
                                                                           String theIdentifier,
                                                                           String theAccountType) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreArrayList> customerAccountsListWrapper = ApStarCBS.getMemberDepositAccounts(theIdentifierType, theIdentifier, theAccountType);

            if (customerAccountsListWrapper.hasErrors() && customerAccountsListWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCustomerAccounts() - " + customerAccountsListWrapper.getErrors() + "\n" + customerAccountsListWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(customerAccountsListWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (customerAccountsListWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry, You do not have Active Accounts.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, You do not have Active Accounts." + getTrailerMessage()));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", customerAccountsListWrapper.getData())
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.getCustomerAccounts(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.addError(e.getMessage());
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> accountBalanceEnquirySINGLE(String strRequestingMobileNumber,
                                                                                   String theIdentifierType,
                                                                                   String theIdentifier,
                                                                                   String theDeviceIdentifierType,
                                                                                   String theDeviceIdentifier,
                                                                                   String theAccountNumber,
    String strSessionID
    ) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                System.err.println("=> Check User Failed for checkUser(..., " + theIdentifierType + ", " + theIdentifier + ", " + theDeviceIdentifierType + ", " + theDeviceIdentifier + ")");

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> accountBalanceWrapper = ApStarCBS.getDepositAccountBalance(theIdentifierType, theIdentifier, theAccountNumber,strSessionID);

            if (accountBalanceWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountBalance() - " + accountBalanceWrapper.getErrors() + "\n" + accountBalanceWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", accountBalanceWrapper.getErrors() + " - " + accountBalanceWrapper.getMessages())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            FlexicoreHashMap accountBalanceResultMap = accountBalanceWrapper.getSingleRecord();
            String requestStatus = accountBalanceResultMap.getStringValue("request_status");

            FlexicoreHashMap accountBalResponseMap = accountBalanceResultMap.getFlexicoreHashMap("response_payload");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountBalance(" + theAccountNumber + ")");
                accountBalanceResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Balance Enquiry request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Balance Enquiry request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + accountBalResponseMap.getStringValue("error_message") + " - " + accountBalResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountBalance(" + theAccountNumber + ")");
                accountBalanceResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + accountBalResponseMap.getStringValue("error_message") + " - " + accountBalResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String strAccountName = accountBalResponseMap.getStringValueOrIfNull("account_label", "").trim();
            String strAccountNumber = accountBalResponseMap.getStringValueOrIfNull("account_number", "").trim();
            String strAccountWithdrawableBalance = accountBalResponseMap.getStringValueOrIfNull("account_balance", "0").trim();
            strAccountWithdrawableBalance = Utils.formatDouble(strAccountWithdrawableBalance, "#,##0.00");


            String strTransactionStatus = accountBalResponseMap.getStringValueOrIfNull("transaction_status", "SUCCESS").trim();
            String strTransactionDescription = accountBalResponseMap.getStringValueOrIfNull("transaction_description", "").trim();
            System.out.println("STR_TRANSACTION_STATUS: " + strTransactionStatus);

            String strMSG = "Dear member, your available Balance for " + strAccountName + " (" + strAccountNumber + ") was KES " + strAccountWithdrawableBalance +" on "+ DateTime.convertStringToDateToString(DateTime.getCurrentDateTime(), "yyyy-MM-dd HH:mm:ss.SSSSSS", "dd-MMM-yy' at 'hh:mm aaa") + ". Ref:" + strSessionID;

            if(strTransactionStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {
                strAccountWithdrawableBalance = "0.00";
                strMSG = "Dear member, your account balance is insufficient to process your Balance Enquiry request";
            }else if(strTransactionStatus.equalsIgnoreCase("INVALID_ACCOUNT")) {
                strMSG = "Dear member, the account number you provided is invalid. Please try again.";
            }

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            accountBalResponseMap.putValue("msg_object", cbsMSG);

            accountBalanceWrapper.setData(accountBalResponseMap);

            return accountBalanceWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.accountBalanceEnquiry(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiry(String strRequestingMobileNumber,
                                                                             String theIdentifierType,
                                                                             String theIdentifier,
                                                                             String theDeviceIdentifierType,
                                                                             String theDeviceIdentifier,
                                                                             String theAccountType) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                System.err.println("=> Check User Failed for checkUser(..., " + theIdentifierType + ", " + theIdentifier + ", " + theDeviceIdentifierType + ", " + theDeviceIdentifier + ")");

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreArrayList> customerAccountsListWrapper = ApStarCBS.getMemberDepositAccounts(theIdentifierType, theIdentifier, theAccountType);

            if (customerAccountsListWrapper.hasErrors() && customerAccountsListWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCustomerAccountsBALANCE_EQUIRY() - " + customerAccountsListWrapper.getErrors() + "\n" + customerAccountsListWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(customerAccountsListWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }






            if (customerAccountsListWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry, the member accounts could not be found.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, the member accounts could not be found." + getTrailerMessage()));

                return resultWrapper;
            }

            FlexicoreArrayList customersList = customerAccountsListWrapper.getData();

            String strAccountDisplay = (theAccountType.equalsIgnoreCase("ALL") ? "" : theAccountType);
            if (strAccountDisplay.equalsIgnoreCase("BOSA")) {
                strAccountDisplay = "Shares And Deposits Accounts";
            }

            StringBuilder accountsMSGBuilder = new StringBuilder("Dear member, your " + strAccountDisplay + " Account Balances are:\n");
            for (FlexicoreHashMap customerAccount : customersList) {
                String strAccountName = customerAccount.getStringValueOrIfNull("account_type_name", "").trim();
                String strAccountNumber = customerAccount.getStringValueOrIfNull("account_number", "").trim();
                String strAccountWithdrawableBalance = customerAccount.getStringValueOrIfNull("account_balance", "0").trim();

                double dbAccountBalance = Utils.stringToDouble(strAccountWithdrawableBalance.replace("-", ""));

                if (dbAccountBalance <= 0) {
                    continue;
                }

                strAccountWithdrawableBalance = Utils.formatDouble(strAccountWithdrawableBalance, "#,##0.00");

                accountsMSGBuilder.append("Name: " + strAccountName + " - " + strAccountNumber + "\n");
                accountsMSGBuilder.append("Bal : KES " + strAccountWithdrawableBalance + "\n\n");
            }

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(accountsMSGBuilder.toString());
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", customersList)
                    .putValue("msg_object", cbsMSG));

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.accountBalanceEnquiry(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Balance Enquiry request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> accountMiniStatement(String strRequestingMobileNumber,
                                                                            String theIdentifierType,
                                                                            String theIdentifier,
                                                                            String theDeviceIdentifierType,
                                                                            String theDeviceIdentifier,
                                                                            String theAccountNumber,
                                                                            String theNumberOfEntries) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Mini-Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> accountMiniStatementListWrapper = ApStarCBS.getAccountMiniStatement(theIdentifierType, theIdentifier, theAccountNumber, theNumberOfEntries);
            //TransactionWrapper<FlexicoreHashMap> accountMiniStatementListWrapper = ProfitsCBS.getAccountMiniStatementTEMP(theAccountNumber);

            if (accountMiniStatementListWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountMiniStatement() - " + accountMiniStatementListWrapper.getErrors() + "\n" + accountMiniStatementListWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Mini-Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Mini-Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", accountMiniStatementListWrapper.getErrors() + " - " + accountMiniStatementListWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap accountMiniStatementMap = accountMiniStatementListWrapper.getSingleRecord();
            FlexicoreHashMap miniStatementResponseMap = accountMiniStatementMap.getFlexicoreHashMap("response_payload");

            String requestStatus = accountMiniStatementMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountMiniStatement(" + theAccountNumber + ")");
                accountMiniStatementMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Account Mini-Statement request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Account Mini-Statement request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + miniStatementResponseMap.getStringValue("error_message") + " - " + miniStatementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountMiniStatement(" + theAccountNumber + ")");
                accountMiniStatementMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Account Mini-Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Account Mini-Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + miniStatementResponseMap.getStringValue("error_message") + " - " + miniStatementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String strAccountName = miniStatementResponseMap.getStringValueOrIfNull("account_name", "").trim();

            String strMSG = "Dear member, your Mini-Statement request has FAILED. Please try again later.";

            FlexicoreArrayList allTransactionsList = miniStatementResponseMap.getFlexicoreArrayList("transactions");
            if (allTransactionsList == null || allTransactionsList.isEmpty()) {
                strMSG = "Dear member, your Mini-Statement request has FAILED. No transaction(s) found for account " + strAccountName + "-" + theAccountNumber + ".";
            } else {

                StringBuilder miniStatementMsg = new StringBuilder("Dear member, your account " + strAccountName + "-" + theAccountNumber + " Mini-Statement:\n");

                for (FlexicoreHashMap transactionMap : allTransactionsList) {

                    String strMSGTransactionReference = transactionMap.getStringValueOrIfNull("transaction_reference", "").trim();
                    String strMSGTransactionDate = transactionMap.getStringValueOrIfNull("transaction_date", "").trim();


                   // String strMSGTransactionTime = transactionMap.getStringValueOrIfNull("transaction_time", "").trim();

                    String strMSGRunningBalance = transactionMap.getStringValue("running_balance").trim();
                    String strMSGTransactionAmount = transactionMap.getStringValue("transaction_amount").trim();
                    String strMSGTransactionComments = transactionMap.getStringValueOrIfNull("transaction_description", "").trim();

                    strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");

                    String strMSGFormattedTransactionAmount = Utils.formatDouble(strMSGTransactionAmount, "#,##0.00");
                    String strMSGFormattedRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                    String strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGTransactionDate, "yyyy-MM-dd", "dd-MMM-yyyy");
                    //strMSGFormattedTransactionDateTime = strMSGFormattedTransactionDateTime + " " + strMSGTransactionTime;

                    miniStatementMsg.append("Ref: ").append(strMSGTransactionReference).append("\n");
                    miniStatementMsg.append("Date: ").append(strMSGFormattedTransactionDateTime).append("\n");
                    miniStatementMsg.append("Amnt: ").append(strMSGFormattedTransactionAmount).append("\n");
                    miniStatementMsg.append("Descr: ").append(strMSGTransactionComments).append("\n");
                    miniStatementMsg.append("Run. Bal: KES ").append(strMSGFormattedRunningBalance).append("\n\n");
                }

                strMSG = miniStatementMsg.toString();
            }

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", allTransactionsList)
                    .putValue("account_name", miniStatementResponseMap.getStringValueOrIfNull("account_name", "").trim())
                    .putValue("account_holder", miniStatementResponseMap.getStringValueOrIfNull("account_holder", "").trim())
                    .putValue("account_available_balance", miniStatementResponseMap.getStringValue("available_balance").trim())
                    .putValue("msg_object", cbsMSG));

            return resultWrapper;
        } catch (Exception e) {

            System.err.println(strRequestingMobileNumber + " => CBSAPI.accountMiniStatement(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Mini Statement request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Mini Statement request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())

            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> accountFullStatement(String strRequestingMobileNumber,
                                                                            String theIdentifierType,
                                                                            String theIdentifier,
                                                                            String theDeviceIdentifierType,
                                                                            String theDeviceIdentifier,
                                                                            String theAccountNumber,
                                                                            String theNumberOfEntries,
                                                                            String theStartDate,
                                                                            String theEndDate) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Account Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> accountFullStatementListWrapper = ApStarCBS.getAccountFullStatement(theIdentifierType, theIdentifier, theAccountNumber,
                    theNumberOfEntries, theStartDate, theEndDate);
            //TransactionWrapper<FlexicoreHashMap> accountFullStatementListWrapper = ProfitsCBS.getAccountMiniStatementTEMP(theAccountNumber);

            if (accountFullStatementListWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountFullStatement() - " + accountFullStatementListWrapper.getErrors() + "\n" + accountFullStatementListWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Account Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Account Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", accountFullStatementListWrapper.getErrors() + " - " + accountFullStatementListWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap accountMiniStatementMap = accountFullStatementListWrapper.getSingleRecord();
            FlexicoreHashMap miniStatementResponseMap = accountMiniStatementMap.getFlexicoreHashMap("response_payload");


            String requestStatus = accountMiniStatementMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountFullStatement(" + theAccountNumber + ")");
                accountMiniStatementMap.printRecordVerticalLabelled();


                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Account Statement request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Account Statement request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + miniStatementResponseMap.getStringValue("error_message") + " - " + miniStatementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountFullStatement(" + theAccountNumber + ")");
                accountMiniStatementMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Account Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Account Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + miniStatementResponseMap.getStringValue("error_message") + " - " + miniStatementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String strAccountName = miniStatementResponseMap.getStringValueOrIfNull("account_name", "").trim();


            String strMSG = "Dear member, your Mini-Statement request has FAILED. Please try again later.";

            FlexicoreArrayList allTransactionsList = miniStatementResponseMap.getFlexicoreArrayList("transactions");
            if (allTransactionsList == null || allTransactionsList.isEmpty()) {
                strMSG = "Dear member, your Statement request has FAILED. No transaction(s) found for account " + strAccountName + "-" + theAccountNumber + ".";
            } else {

                StringBuilder miniStatementMsg = new StringBuilder("Dear member, your account " + strAccountName + "-" + theAccountNumber + " Mini-Statement:\n");

                for (FlexicoreHashMap transactionMap : allTransactionsList) {

                    String strMSGTransactionReference = transactionMap.getStringValueOrIfNull("transaction_reference", "").trim();
                    String strMSGTransactionDate = transactionMap.getStringValueOrIfNull("transaction_date", "").trim();
                    //String strMSGTransactionTime = transactionMap.getStringValueOrIfNull("transaction_time", "").trim();
                    String strTransactionType = transactionMap.getStringValueOrIfNull("transaction_type", "").trim();

                    String strMSGRunningBalance = transactionMap.getStringValue("running_balance").trim();
                    String strMSGTransactionAmount = transactionMap.getStringValue("transaction_amount").trim();
                    String strMSGTransactionComments = transactionMap.getStringValueOrIfNull("transaction_description", "").trim();

                    strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");

                    if (strTransactionType.equalsIgnoreCase("DEBIT")) {
                        strMSGTransactionAmount = "-" + strMSGTransactionAmount;
                    }

                    transactionMap.putValue("transaction_amount", strMSGTransactionAmount);

                    String strMSGFormattedTransactionAmount = Utils.formatDouble(strMSGTransactionAmount, "#,##0.00");
                    String strMSGFormattedRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                    String strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGTransactionDate, "yyyy-MM-dd", "dd MMM yyyy");
                    strMSGFormattedTransactionDateTime = strMSGFormattedTransactionDateTime ;

                    transactionMap.putValue("transaction_date_time", strMSGFormattedTransactionDateTime);

                    miniStatementMsg.append("Ref: ").append(strMSGTransactionReference).append("\n");
                    miniStatementMsg.append("Date: ").append(strMSGFormattedTransactionDateTime).append("\n");
                    miniStatementMsg.append("Amnt: ").append(strMSGFormattedTransactionAmount).append("\n");
                    miniStatementMsg.append("Descr: ").append(strMSGTransactionComments).append("\n");
                    miniStatementMsg.append("Run. Bal: KES ").append(strMSGFormattedRunningBalance).append("\n\n");
                }

                strMSG = miniStatementMsg.toString();
            }

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", allTransactionsList)
                    .putValue("account_name", miniStatementResponseMap.getStringValueOrIfNull("account_name", "").trim())
                    .putValue("account_holder", miniStatementResponseMap.getStringValueOrIfNull("account_holder", "").trim())
                    .putValue("account_available_balance", miniStatementResponseMap.getStringValue("available_balance").trim())
                    .putValue("msg_object", cbsMSG));

            return resultWrapper;
        } catch (Exception e) {

            System.err.println(strRequestingMobileNumber + " => CBSAPI.getAccountFullStatement(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Account Statement request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Account Statement request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> validateAccountNumber(String strRequestingMobileNumber,
                                                                             String theIdentifierType,
                                                                             String theIdentifier,
                                                                             String theAccountNumber) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

           /* TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theReferenceKey, theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }*/

            TransactionWrapper<FlexicoreHashMap> accountDetailsWrapper = ApStarCBS.getAccountDetails(theIdentifierType, theIdentifier, theAccountNumber);

            if (accountDetailsWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountDetails() - " + accountDetailsWrapper.getErrors() + "\n" + accountDetailsWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later.")
                        .putValue("cbs_api_error_message", accountDetailsWrapper.getErrors() + " - " + accountDetailsWrapper.getMessages())
                );

                return resultWrapper;
            }

            FlexicoreHashMap accountDetailsResultMap = accountDetailsWrapper.getSingleRecord();
           /* String requestStatus = accountDetailsResultMap.getStringValue("request_status");

            FlexicoreHashMap accountDetailsResponseMap = accountDetailsResultMap.getFlexicoreHashMap("response_payload");

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getAccountDetails(" + theAccountNumber + ")");
                accountDetailsResultMap.printRecordVerticalLabelled();

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, account "+theAccountNumber+" not found")
                        .putValue("cbs_api_error_message", requestStatus + ": " + accountDetailsResponseMap.getStringValue("error_message") + " - " + accountDetailsResponseMap.getStringValue("devMessage"))
                );

                return resultWrapper;
            }*/

            resultWrapper.setData(accountDetailsResultMap);

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(theIdentifier + " => CBSAPI.validateAccountNumber(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> internalFundsTransfer(String strRequestingMobileNumber,
                                                                             String theIdentifierType,
                                                                             String theIdentifier,
                                                                             String theDeviceIdentifierType,
                                                                             String theDeviceIdentifier,
                                                                             String theOriginatorId,
                                                                             String theSourceAccount,
                                                                             String theDestinationAccount,
                                                                             double theAmount,
                                                                             String theTransactionDescription,
                                                                             String theSourceReference,
                                                                             String theRequestApplication,
                                                                             String theSourceApplication) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Funds Transfer request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);
                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> internalFundsTransferWrapper = ApStarCBS.internalFundsTransfer(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    theSourceAccount,
                    theDestinationAccount,
                    theAmount,
                    theTransactionDescription,
                    theSourceReference,
                    theRequestApplication,
                    theSourceApplication);

            if (internalFundsTransferWrapper.hasErrors()) {
                System.err.println(theIdentifier + " => ApStarCBS.internalFundsTransfer() - " + internalFundsTransferWrapper.getErrors() + "\n" + internalFundsTransferWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Funds Transfer request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "Transaction Error")
                        .putValue("cbs_api_error_message", internalFundsTransferWrapper.getErrors() + " - " + internalFundsTransferWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }


            FlexicoreHashMap internalFundsTransferResultMap = internalFundsTransferWrapper.getSingleRecord();

            internalFundsTransferResultMap.printRecordVerticalLabelled();

            String requestStatus = internalFundsTransferResultMap.getStringValue("request_status");

            FlexicoreHashMap fundsTransferMap = internalFundsTransferResultMap.getFlexicoreHashMap("response_payload");

            System.out.println("THE STATUS:   =>=> " + requestStatus);



            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.internalFundsTransfer(" + theSourceAccount + ")");
                internalFundsTransferResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, the source account " + theSourceAccount + " balance is insufficient to process your Funds Transfer request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", fundsTransferMap.getStringValueOrIfNull("transaction_status_description","Sorry, your account balance is insufficient to process your Funds Transfer request." ))
                        .putValue("cbs_api_error_message", requestStatus + ": " + fundsTransferMap.getStringValue("error_message") + " - " + fundsTransferMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (requestStatus.equalsIgnoreCase("BLOCKED")){
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.internalFundsTransfer(" + theSourceAccount + ")");
                internalFundsTransferResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, the source account " + theSourceAccount + " is blocked. Funds Transfer request cannot be processed.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, the source account " + theSourceAccount + " is blocked. Funds Transfer request cannot be processed." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + fundsTransferMap.getStringValue("error_message") + " - " + fundsTransferMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;

            }


            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.internalFundsTransfer(" + theSourceAccount + ")");
                internalFundsTransferResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();

                /*if (fundsTransferMap.getStringValue("error_message").contains("Failed to determine transaction destination")) {
                    String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");
                    cbsMSG.setMessage("Dear member, your Request for Funds Transfer of KES " + strFormattedAmount + " to account " + theDestinationAccount + " FAILED. Destination Account provided not found.");
                } else {
                    cbsMSG.setMessage("Sorry, an error occurred while processing your Funds Transfer request. Please try again later.");
                }*/

                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Funds Transfer request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + fundsTransferMap.getStringValue("error_message") + " - " + fundsTransferMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            //String strDateTime = MBankingDB.getDBDateTime();
            String strFormattedDateTime = DateTime.getCurrentDateTime("dd-MMM-yyyy HH:mm:ss");

             strFormattedDateTime   =DateTime.convertStringToDateToString(DateTime.getCurrentDateTime(), "yyyy-MM-dd HH:mm:ss.SSSSSS", "dd-MMM-yy' at 'hh:mm aaa");

            String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");

            String strMSG = "Dear member, your Request for Funds Transfer of KES " + strFormattedAmount + " to account " + theDestinationAccount + " on " + strFormattedDateTime + " has been received. Kindly wait as it is being processed.";

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Funds transfer received successfully.")
                    .putValue("title", "Transaction Accepted")
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_transaction_reference", fundsTransferMap.getStringValue("transaction_reference"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(theIdentifier + " => CBSAPI.internalFundsTransfer(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Qualification Check request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("title", "Transaction Error")
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("cbs_api_error_message", e.getMessage())
                    .putValue("msg_object", cbsMSG)

            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getCharges(String strRequestingMobileNumber,
                                                                  String theIdentifierType,
                                                                  String theIdentifier,

                                                                  String theChargeAction,
                                                                  double theAmount
    ) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> getChargesWrapper = ApStarCBS.getCharges(
                    theIdentifierType,
                    theIdentifier,
                    "",
                    theChargeAction,
                    theAmount);

            if (getChargesWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCharges(" + theChargeAction + ", " + theAmount + ") - " + getChargesWrapper.getErrors() + "\n" + getChargesWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later.")
                        .putValue("title", "Transaction Error")
                        .putValue("cbs_api_error_message", getChargesWrapper.getErrors() + " - " + getChargesWrapper.getMessages())
                );

                return getChargesWrapper;
            }

            FlexicoreHashMap getChargesResultMap = getChargesWrapper.getSingleRecord();
            String requestStatus = getChargesResultMap.getStringValue("request_status");

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCharges(" + theChargeAction + ", " + theAmount + ")");
                getChargesResultMap.printRecordVerticalLabelled();

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later.")
                        .putValue("cbs_api_error_message", getChargesResultMap.getStringValue("error_message") + " - " + getChargesResultMap.getStringValue("devMessage"))
                );

                return resultWrapper;
            }
            FlexicoreHashMap getChargesMap = getChargesResultMap.getFlexicoreHashMap("response_payload");

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS.getValue())
                    .putValue("charge_amount", getChargesMap.getValue("charge_amount")));

            return resultWrapper;

        } catch (Exception e) {
            System.out.println(theIdentifier + " => CBSAPI.getCharges(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR.getValue())
                    .putValue("cbs_api_error_message", e.getMessage()));
        }

        return resultWrapper;
    }






    public static TransactionWrapper<FlexicoreHashMap> mobileMoneyResult(
            String strRequestingMobileNumber,
            String theIdentifierType,
            String theIdentifier,
            String theOriginatorId,
            String theBeneficiaryIdentifierType,
            String theBeneficiaryIdentifier,
            String theBeneficiaryName,
            String theBeneficiaryOtherDetails,
            String theBeneficiaryReference,
            String theTransactionDateTime) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> withdrawalResultWrapper = ApStarCBS.withdrawalResult(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    "CONFIRMED",
                    "Transaction Completed Successfully",
                    theBeneficiaryIdentifierType,
                    theBeneficiaryIdentifier,
                    theBeneficiaryName,
                    theBeneficiaryOtherDetails,
                    theBeneficiaryReference,
                    theTransactionDateTime
            );

            if (withdrawalResultWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.mobileMoneyResult() - " + withdrawalResultWrapper.getErrors() + "\n" + withdrawalResultWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Dear member, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                );

                return resultWrapper;
            }

            FlexicoreHashMap withdrawalResultMap = withdrawalResultWrapper.getSingleRecord();
            String requestStatus = withdrawalResultMap.getStringValue("request_status");

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                String errorMessage = withdrawalResultMap.getFlexicoreHashMap("response_payload").getStringValue("error_message");

                if (requestStatus.equalsIgnoreCase("FAILED")) {
                    resultWrapper.setHasErrors(false);
                    resultWrapper.setData(new FlexicoreHashMap()
                            .putValue("end_session", USSDAPIConstants.Condition.NO)
                            .putValue("cbs_api_return_val", "TRANSACTION_EXISTS")
                            .putValue("cbs_response_message", errorMessage)
                            .putValue("display_message", errorMessage + ". Originator ID " + theOriginatorId + "." + getTrailerMessage()));

                    return resultWrapper;
                }

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.mobileMoneyResult(" + theOriginatorId + ")");
                withdrawalResultMap.printRecordVerticalLabelled();

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("cbs_response_message", errorMessage)
                        .putValue("display_message", "Failed to Complete Mobile Money for Originator ID " + theOriginatorId + "." + getTrailerMessage()));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", "SUCCESS")
                    .putValue("cbs_response_message", "Transaction Completed Successfully"));

            //resultWrapper.setData(withdrawalResultMap.getFlexicoreHashMap("response_payload"));
            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.mobileMoneyResult(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG));
        }
        return resultWrapper;
    }


    public static TransactionWrapper<FlexicoreHashMap> mobileMoneyDeposit(PESA thePESAIN, String theTransactionDestinationRefForFailure) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> mobileMoneyDepositWrapper = ApStarCBS.cashDeposit(
                    "MSISDN",
                    thePESAIN.getInitiatorIdentifier(),
                    thePESAIN.getOriginatorID(),
                    String.valueOf(thePESAIN.getProductID()),
                    thePESAIN.getPESAType().getValue(),
                    thePESAIN.getPESAAction().getValue(),
                    thePESAIN.getCommand(),
                    thePESAIN.getSensitivity().getValue(),
                    String.valueOf(thePESAIN.getChargeApplied()),
                    //initiator
                    new FlexicoreHashMap()
                            .putValue("identifier_type", thePESAIN.getInitiatorType())
                            .putValue("identifier", thePESAIN.getInitiatorIdentifier())
                            .putValue("account", thePESAIN.getInitiatorAccount())
                            .putValue("name", thePESAIN.getInitiatorName())
                            .putValue("reference", thePESAIN.getInitiatorReference())
                            .putValue("other_details", thePESAIN.getInitiatorOtherDetails()),
                    //source
                    new FlexicoreHashMap()
                            .putValue("identifier_type", thePESAIN.getSourceType())
                            .putValue("identifier", thePESAIN.getSourceIdentifier())
                            .putValue("account", thePESAIN.getSourceAccount())
                            .putValue("name", thePESAIN.getSourceName())
                            .putValue("reference", thePESAIN.getSourceReference())
                            .putValue("other_details", thePESAIN.getSourceOtherDetails()),
                    //sender
                    new FlexicoreHashMap()
                            .putValue("identifier_type", thePESAIN.getSenderType())
                            .putValue("identifier", thePESAIN.getSenderIdentifier())
                            .putValue("account", thePESAIN.getSenderAccount())
                            .putValue("name", thePESAIN.getSenderName())
                            .putValue("reference", thePESAIN.getSenderReference())
                            .putValue("other_details", thePESAIN.getSenderOtherDetails()),
                    //receiver
                    new FlexicoreHashMap()
                            .putValue("identifier_type", thePESAIN.getReceiverType())
                            .putValue("identifier", thePESAIN.getReceiverIdentifier())
                            .putValue("account", thePESAIN.getReceiverAccount())
                            .putValue("name", thePESAIN.getReceiverName())
                            .putValue("reference", thePESAIN.getReceiverReference())
                            .putValue("other_details", thePESAIN.getReceiverOtherDetails()),

                    //FOR MWALIMU,
                    //beneficiary
                    new FlexicoreHashMap()
                            .putValue("identifier_type", "ACCOUNT_NO")
                            .putValue("identifier", thePESAIN.getReceiverAccount()) //DONE INTENTIONALLY
                            .putValue("account", thePESAIN.getReceiverAccount())
                            .putValue("name", thePESAIN.getBeneficiaryName())
                            .putValue("reference", thePESAIN.getReceiverReference())
                            .putValue("other_details", thePESAIN.getReceiverOtherDetails()),

                    thePESAIN.getTransactionAmount(),
                    thePESAIN.getCategory(),
                    thePESAIN.getTransactionRemark(),
                    thePESAIN.getReceiverReference(),
                    "MBANKING",
                    "MPESA_BROKER");

            if (mobileMoneyDepositWrapper.hasErrors()) {

                System.err.println(thePESAIN.getInitiatorIdentifier() + " => ApStarCBS.mobileMoneyDeposit() - " + mobileMoneyDepositWrapper.getErrors() + "\n" + mobileMoneyDepositWrapper.getMessages());

                USSDAPIConstants.StandardReturnVal standardReturnVal = insertToDepositsReconciliation(thePESAIN, theTransactionDestinationRefForFailure, mobileMoneyDepositWrapper.getErrors());

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", standardReturnVal)
                        .putValue("cbs_response_message", mobileMoneyDepositWrapper.getErrors()));

                return resultWrapper;
            }

            FlexicoreHashMap mobileMoneyDepositMap = mobileMoneyDepositWrapper.getSingleRecord();

            String requestStatus = mobileMoneyDepositMap.getStringValue("request_status");

            FlexicoreHashMap depositMap = mobileMoneyDepositMap.getFlexicoreHashMap("response_payload");

            if (requestStatus.equalsIgnoreCase("DUPLICATE")) {
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                        .putValue("cbs_response_message", "Transaction already exists.")
                        .putValue("cbs_transaction_reference", thePESAIN.getReceiverReference()));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(thePESAIN.getInitiatorIdentifier() + " => ApStarCBS.mobileMoneyDeposit(" + thePESAIN.getBeneficiaryIdentifier() + ", " + thePESAIN.getReceiverAccount() + ")");
                mobileMoneyDepositMap.printRecordVerticalLabelled();

                String strMessage = depositMap.getStringValueOrIfNull("error_message", "") + " - " + depositMap.getStringValueOrIfNull("devMessage", "");

                USSDAPIConstants.StandardReturnVal standardReturnVal = insertToDepositsReconciliation(thePESAIN, theTransactionDestinationRefForFailure, strMessage);

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", standardReturnVal)
                        .putValue("cbs_response_message", strMessage));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("cbs_response_message", "Cash Deposit has been received successfully and is being processed.")
                    .putValue("cbs_transaction_reference", depositMap.getStringValue("transaction_reference")));

            String strFormattedDateTime = Utils.formatDate(thePESAIN.getPesaDateCreated(), "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

            String strMSG = "Dear member, account " + thePESAIN.getReceiverAccount() + " has been credited with amount KES " + Utils.formatDouble(thePESAIN.getTransactionAmount(), "#,##0.00") +
                            ". MPesa Ref: " + thePESAIN.getOriginatorID();

            /*int intMSGSent = fnSendSMS(thePESAIN.getSourceIdentifier(), strMSG, "YES", MSGConstants.MSGMode.SAF, 210, "MPESA_DEPOSIT", "MBANKING_SERVER", "MBANKING_SERVER",
                    thePESAIN.getOriginatorID(), thePESAIN.getSourceReference());*/

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(thePESAIN.getInitiatorIdentifier() + " => CBSAPI.mobileMoneyDeposit(): " + e.getMessage());
            e.printStackTrace();

            USSDAPIConstants.StandardReturnVal standardReturnVal = insertToDepositsReconciliation(thePESAIN, theTransactionDestinationRefForFailure, Misc.stringifyStackTrace(e));

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("cbs_api_return_val", standardReturnVal)
                    .putValue("cbs_response_message", e.getMessage()));
        }
        return resultWrapper;
    }


    /*  public static TransactionWrapper<FlexicoreHashMap> mobileMoneyDeposit(String theReferenceKey,
                                                                            String theMpesaRefNumber,
                                                                            String theAccountNo,
                                                                            String theAmount,
                                                                            PESA thePESAIN) {

          TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

          try {

              TransactionWrapper<FlexicoreHashMap> mobileMoneyDepositWrapper = ProfitsCBS.mobileMoneyDeposit(theReferenceKey, theMpesaRefNumber, theAccountNo, theAmount);

              if (mobileMoneyDepositWrapper.hasErrors()) {
                  System.err.println("ProfitsCBS.mobileMoneyDeposit() - " + mobileMoneyDepositWrapper.getErrors() + "\n" + mobileMoneyDepositWrapper.getMessages());

                  insertToDepositsReconciliation(thePESAIN, mobileMoneyDepositWrapper.getErrors());

                  resultWrapper.setHasErrors(true);
                  resultWrapper.setData(new FlexicoreHashMap()
                          .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                          .putValue("cbs_response_message", mobileMoneyDepositWrapper.getErrors()));

                  return resultWrapper;
              }

              FlexicoreHashMap mobileMoneyDepositMap = mobileMoneyDepositWrapper.getSingleRecord();
              FlexicoreHashMap theResultMap = mobileMoneyDepositMap.getFlexicoreHashMap("Result");

              if (!theResultMap.getStringValue("Type").equalsIgnoreCase("Success")) {

                  System.err.println("ProfitsCBS.mobileMoneyDeposit() - RESPONSE ERROR RESULT:");
                  theResultMap.setTableName(theIdentifier);
                  theResultMap.printRecordVerticalLabelled();
                  System.out.println("\n");

                  String strResultID = theResultMap.getStringValue("Id");
                  String strMessage = theResultMap.getStringValueOrIfNull("Message", "");

                  insertToDepositsReconciliation(thePESAIN, strMessage);

                  resultWrapper.setHasErrors(true);
                  resultWrapper.setData(new FlexicoreHashMap()
                          .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                          .putValue("cbs_response_message", strMessage));

                  return resultWrapper;
              }

              FlexicoreHashMap theTunMap = mobileMoneyDepositMap.getFlexicoreHashMap("Tun");

              resultWrapper.setData(new FlexicoreHashMap()
                      .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                      .putValue("cbs_response_message", "Cash Deposit completed successfully." + getTrailerMessage())
                      .putValue("Tun", theTunMap));

              return resultWrapper;
          } catch (Exception e) {
              System.err.println("CBSAPI.mobileMoneyDeposit(): " + e.getMessage());
              e.printStackTrace();

              insertToDepositsReconciliation(thePESAIN, Misc.stringifyStackTrace(e));

              resultWrapper.setHasErrors(true);
              resultWrapper.setData(new FlexicoreHashMap()
                      .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                      .putValue("cbs_response_message", e.getMessage()));

          }
          return resultWrapper;
      }
  */
    private static USSDAPIConstants.StandardReturnVal insertToDepositsReconciliation(PESA thePESAIN, String theTransactionDestinationRefForFailure, String strMessage) {

        TransactionWrapper<FlexicoreHashMap> selectWrapper = Repository.selectWhere(StringRefs.SENTINEL, TBL_DEPOSITS_RECONCILIATION,
                new FilterPredicate("originator_id = :originator_id"),
                new FlexicoreHashMap().addQueryArgument(":originator_id", thePESAIN.getOriginatorID()));

        if (selectWrapper.hasErrors()) {
            return USSDAPIConstants.StandardReturnVal.ERROR;
        }

        FlexicoreHashMap selectMap = selectWrapper.getSingleRecord();
        if (selectMap != null && !selectMap.isEmpty()) {
            return USSDAPIConstants.StandardReturnVal.DUPLICATE;
        }

        FlexicoreHashMap insertMap = new FlexicoreHashMap();

        insertMap.putValue("originator_id", thePESAIN.getOriginatorID());
        insertMap.putValue("source_type", thePESAIN.getSourceType());
        insertMap.putValue("source_identifier", thePESAIN.getSourceIdentifier());
        insertMap.putValue("source_name", thePESAIN.getSourceName());
        insertMap.putValue("beneficiary_type", thePESAIN.getBeneficiaryType());
        insertMap.putValue("beneficiary_account", thePESAIN.getReceiverAccount());
        insertMap.putValue("currency", thePESAIN.getTransactionCurrency());
        insertMap.putValue("amount", String.valueOf(thePESAIN.getTransactionAmount()));
        insertMap.putValue("status", "UNRECONCILED");
        insertMap.putValue("status_description", "Unreconciled");
        insertMap.putValue("status_date", DateTime.getCurrentDateTime());
        insertMap.putValue("destination_reference", theTransactionDestinationRefForFailure);
        insertMap.putValue("transaction_date", thePESAIN.getPesaDateCreated());
        insertMap.putValue("error_message", strMessage);
        insertMap.putValue("date_created", DateTime.getCurrentDateTime());
        insertMap.putValue("date_modified", DateTime.getCurrentDateTime());

        TransactionWrapper<FlexicoreHashMap> depositsWrapper = Repository.insertAutoIncremented(StringRefs.SENTINEL, TBL_DEPOSITS_RECONCILIATION, insertMap);
        if (!depositsWrapper.hasErrors()) {
            FlexicoreHashMap depositsMap = depositsWrapper.getSingleRecord();

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(depositsMap);

            Repository.update(StringRefs.SENTINEL, TBL_DEPOSITS_RECONCILIATION,
                    new FlexicoreHashMap().addQueryArgument("integrity_hash", integrityHash),
                    new FilterPredicate("deposit_id = :deposit_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":deposit_id", depositsMap.getStringValue("deposit_id")));
        } else {
            return USSDAPIConstants.StandardReturnVal.ERROR;
        }

        return USSDAPIConstants.StandardReturnVal.BUFFER_SAVE_SUCCESS;
    }

    public static TransactionWrapper<FlexicoreHashMap> mobileMoneyWithdrawal(String strRequestingMobileNumber,
                                                                             String theIdentifierType,
                                                                             String theIdentifier,
                                                                             String theDeviceIdentifierType,
                                                                             String theDeviceIdentifier,
                                                                             String theOriginatorId,
                                                                             String theProductId,
                                                                             String thePesaType,
                                                                             String theAction,
                                                                             String theCommand,
                                                                             FlexicoreHashMap theInitiatorDetailsMap,
                                                                             FlexicoreHashMap theSourceDetailsMap,
                                                                             FlexicoreHashMap theSenderDetailsMap,
                                                                             FlexicoreHashMap theReceiverDetailsMap,
                                                                             FlexicoreHashMap theBeneficiaryDetailsMap,
                                                                             double theAmount,
                                                                             String theCategory,
                                                                             String theTransactionDescription,
                                                                             String theSourceReference,
                                                                             String theRequestApplication,
                                                                             String theSourceApplication) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Cash Withdrawal request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> cashWithdrawalWrapper = ApStarCBS.withdrawal(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    theProductId,
                    thePesaType,
                    theAction,
                    theCommand,
                    theInitiatorDetailsMap,
                    theSourceDetailsMap,
                    theSenderDetailsMap,
                    theReceiverDetailsMap,
                    theBeneficiaryDetailsMap,
                    theAmount,
                    theCategory,
                    theTransactionDescription,
                    theSourceReference,
                    theRequestApplication,
                    theSourceApplication,
                    "MPESA");

            if (cashWithdrawalWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.mobileMoneyWithdrawal() - " + cashWithdrawalWrapper.getErrors() + "\n" + cashWithdrawalWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Cash Withdrawal request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Dear member, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap cashWithdrawalResultMap = cashWithdrawalWrapper.getSingleRecord();

            String requestStatus = cashWithdrawalResultMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.mobileMoneyWithdrawal(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                cashWithdrawalResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Cash Withdrawal request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Cash Withdrawal request." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if(requestStatus.equalsIgnoreCase("BLOCKED")){


                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, the source account " + theSourceDetailsMap.getStringValue("account") + " is blocked. Cash Withdrawal request cannot be processed.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("cbs_response_message", "Sorry, the source account " + theSourceDetailsMap.getStringValue("account") + " is blocked.Cash Withdrawal request cannot be processed.")
                        .putValue("display_message", "Sorry, the source account " + theSourceDetailsMap.getStringValue("account") + " is blocked. Cash Withdrawal request cannot be processed." + getTrailerMessage())
                        .putValue("msg_object", "Sorry, the source account " + theSourceDetailsMap.getStringValue("account") + " is blocked. Cash Withdrawal request cannot be processed." + getTrailerMessage()
                        ) );

                return resultWrapper;
            }

            if(requestStatus.equalsIgnoreCase("DUPLICATE")) {

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, Transaction already exists.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);


                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("cbs_response_message", "Transaction already exists.")
                        .putValue("display_message", "Transaction already exists." + getTrailerMessage())
                        .putValue("msg_object", "Transaction already exists." + getTrailerMessage()
                        )
                        );


                return resultWrapper;
            }

            if(requestStatus.equalsIgnoreCase("INVALID_ACCOUNT")){

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, You have an Invalid Account.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("cbs_response_message", "Invalid Account.")
                        .putValue("display_message", "Invalid Account." + getTrailerMessage())
                        .putValue("msg_object", "Invalid Account." + getTrailerMessage()
                        )
                );

                return resultWrapper;
            }

            if(requestStatus.equalsIgnoreCase("WITHDRAWAL_LIMIT_VIOLATION")){
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, you have a Withdrawal Limit Violation.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);
                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("cbs_response_message", "Withdrawal Limit Violation.")
                        .putValue("display_message", "Withdrawal Limit Violation." + getTrailerMessage())
                );

                return resultWrapper;
            }



            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.mobileMoneyWithdrawal(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                cashWithdrawalResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Cash Withdrawal request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Cash Withdrawal request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            FlexicoreHashMap withdrawalMap = cashWithdrawalResultMap.getFlexicoreHashMap("response_payload");

            String strTransactionDateTime = withdrawalMap.getStringValueOrIfNull("transaction_date_time", "").trim();
            String strFormattedDateTime = Utils.formatDate(strTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
            String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");

            String strMSG = "Dear member, your M-PESA Withdrawal Request of KES " + strFormattedAmount + " to " + theBeneficiaryDetailsMap.getStringValue("account") + " has been received and is being processed.";

            /*
                String strMSG = "Dear member, Cash Withdrawal completed Successfully.\n";
                strMSG += "Source A/C: " + theSourceAccountNumber + "\n";
                strMSG += "Mobile: " + theBeneficiaryMobileNo + "\n";
                strMSG += "Amount: KES " + Utils.formatDouble(theAmount, "#,##0.00") + "\n";
                // strMSG += "Date: "+strBeneficiaryName+"";
            */

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getFlexicoreHashMap("signatory_details");

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Cash Withdrawal has been received successfully and is being processed.")
                    .putValue("msg_object", cbsMSG)
                    .putValue("response_payload", withdrawalMap)
                    .putValue("customer_full_name", signatoryDetailsMap.getStringValue("full_name"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.mobileMoneyWithdrawal(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Cash Withdrawal request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> reverseMobileMoneyWithdrawal(
            String strRequestingMobileNumber,
            String theIdentifierType,
            String theIdentifier,
            String theOriginatorId,
            String theBeneficiaryIdentifierType,
            String theBeneficiaryIdentifier,
            String theBeneficiaryName,
            String theBeneficiaryOtherDetails,
            String theBeneficiaryReference,
            String theTransactionDateTime) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> reversalWrapper = ApStarCBS.withdrawalResult(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    "REVERSE_CONFIRMED",
                    "Reversal Request",
                    theBeneficiaryIdentifierType,
                    theBeneficiaryIdentifier,
                    theBeneficiaryName,
                    theBeneficiaryOtherDetails,
                    theBeneficiaryReference,
                    theTransactionDateTime
            );

            if (reversalWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.reverseMobileMoneyWithdrawal() - " + reversalWrapper.getErrors() + "\n" + reversalWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Dear member, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                );

                return resultWrapper;
            }

            FlexicoreHashMap reversalResultMap = reversalWrapper.getSingleRecord();
            String requestStatus = reversalResultMap.getStringValue("request_status");

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.reverseMobileMoneyWithdrawal(" + theOriginatorId + ")");
                reversalResultMap.printRecordVerticalLabelled();

                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Failed to Reverse Mobile Money for Originator ID " + theOriginatorId + "." + getTrailerMessage()));

                return resultWrapper;
            }

            resultWrapper.setData(reversalResultMap.getFlexicoreHashMap("response_payload"));
            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.reverseMobileMoneyWithdrawal(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG));
        }
        return resultWrapper;
    }


    public static TransactionWrapper<FlexicoreHashMap> utilitiesPayment(String strRequestingMobileNumber,
                                                                        String theIdentifierType,
                                                                        String theIdentifier,
                                                                        String theDeviceIdentifierType,
                                                                        String theDeviceIdentifier,
                                                                        String theOriginatorId,
                                                                        String theProductId,
                                                                        String thePesaType,
                                                                        String theAction,
                                                                        String theCommand,
                                                                        FlexicoreHashMap theInitiatorDetailsMap,
                                                                        FlexicoreHashMap theSourceDetailsMap,
                                                                        FlexicoreHashMap theSenderDetailsMap,
                                                                        FlexicoreHashMap theReceiverDetailsMap,
                                                                        FlexicoreHashMap theBeneficiaryDetailsMap,
                                                                        double theAmount,
                                                                        String theCategory,
                                                                        String theTransactionDescription,
                                                                        String theSourceReference,
                                                                        String theRequestApplication,
                                                                        String theSourceApplication) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Utility Payment request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> utilityPaymentWrapper = ApStarCBS.withdrawal(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    theProductId,
                    thePesaType,
                    theAction,
                    theCommand,
                    theInitiatorDetailsMap,
                    theSourceDetailsMap,
                    theSenderDetailsMap,
                    theReceiverDetailsMap,
                    theBeneficiaryDetailsMap,
                    theAmount,
                    theCategory,
                    theTransactionDescription,
                    theSourceReference,
                    theRequestApplication,
                    theSourceApplication,
                    "UTILITY_PAYMENT");

            if (utilityPaymentWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.utilitiesPayment() - " + utilityPaymentWrapper.getErrors() + "\n" + utilityPaymentWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Utility Payment request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Dear member, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap utilityPaymentResultMap = utilityPaymentWrapper.getSingleRecord();

            String requestStatus = utilityPaymentResultMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.utilitiesPayment(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                utilityPaymentResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Utility Payment request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Utility Payment request." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }


            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.utilitiesPayment(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                utilityPaymentResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Utility Payment request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Utility Payment request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            FlexicoreHashMap withdrawalMap = utilityPaymentResultMap.getFlexicoreHashMap("response_payload");

            String strTransactionDateTime = withdrawalMap.getStringValueOrIfNull("transaction_date_time", "").trim();
            String strFormattedDateTime = Utils.formatDate(strTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
            String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");

            String strMSG = "Dear member, your Bill Payment Request of KES " + strFormattedAmount + " to " +
                            theReceiverDetailsMap.getStringValue("name") + " - " +
                            theReceiverDetailsMap.getStringValue("identifier") + ", " +
                            "for account " +
                            theReceiverDetailsMap.getStringValue("account") + " has been received and is being processed.";


            /*
                String strMSG = "Dear member, Cash Withdrawal completed Successfully.\n";
                strMSG += "Source A/C: " + theSourceAccountNumber + "\n";
                strMSG += "Mobile: " + theBeneficiaryMobileNo + "\n";
                strMSG += "Amount: KES " + Utils.formatDouble(theAmount, "#,##0.00") + "\n";
                // strMSG += "Date: "+strBeneficiaryName+"";
            */

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getFlexicoreHashMap("signatory_details");

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Utility Payment has been received successfully and is being processed.")
                    .putValue("msg_object", cbsMSG)
                    .putValue("response_payload", withdrawalMap)
                    .putValue("customer_full_name", signatoryDetailsMap.getStringValue("full_name"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.utilitiesPayment(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Utility Payment request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG));
        }
        return resultWrapper;
    }


    public static TransactionWrapper<FlexicoreHashMap> buyAirtime(String strRequestingMobileNumber,
                                                                  String theIdentifierType,
                                                                  String theIdentifier,
                                                                  String theDeviceIdentifierType,
                                                                  String theDeviceIdentifier,
                                                                  String theOriginatorId,
                                                                  String theProductId,
                                                                  String thePesaType,
                                                                  String theAction,
                                                                  String theCommand,
                                                                  FlexicoreHashMap theInitiatorDetailsMap,
                                                                  FlexicoreHashMap theSourceDetailsMap,
                                                                  FlexicoreHashMap theSenderDetailsMap,
                                                                  FlexicoreHashMap theReceiverDetailsMap,
                                                                  FlexicoreHashMap theBeneficiaryDetailsMap,
                                                                  double theAmount,
                                                                  String theCategory,
                                                                  String theTransactionDescription,
                                                                  String theSourceReference,
                                                                  String theRequestApplication,
                                                                  String theSourceApplication) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Airtime Purchase request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> buyAirtimeWrapper = ApStarCBS.withdrawal(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    theProductId,
                    thePesaType,
                    theAction,
                    theCommand,
                    theInitiatorDetailsMap,
                    theSourceDetailsMap,
                    theSenderDetailsMap,
                    theReceiverDetailsMap,
                    theBeneficiaryDetailsMap,
                    theAmount,
                    theCategory,
                    theTransactionDescription,
                    theSourceReference,
                    theRequestApplication,
                    theSourceApplication,
                    "BUY_AIRTIME");

            if (buyAirtimeWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.buyAirtime() - " + buyAirtimeWrapper.getErrors() + "\n" + buyAirtimeWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Airtime Purchase request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Dear member, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap buyAirtimeResultMap = buyAirtimeWrapper.getSingleRecord();

            String requestStatus = buyAirtimeResultMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.buyAirtime(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                buyAirtimeResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Airtime Purchase request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Airtime Purchase request." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.buyAirtime(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                buyAirtimeResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Airtime Purchase request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Airtime Purchase request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            FlexicoreHashMap buyAirtimeMap = buyAirtimeResultMap.getFlexicoreHashMap("response_payload");

            String strTransactionDateTime = buyAirtimeMap.getStringValueOrIfNull("transaction_date_time", "").trim();
            String strFormattedDateTime = Utils.formatDate(strTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
            String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");

            String strMSG = "Dear member, your Airtime Purchase Request of KES " + Utils.formatDouble(theAmount, "#,##0.00") + " to " +
                            theBeneficiaryDetailsMap.getStringValue("account") + " has been received and is being processed.";


            /*
                String strMSG = "Dear member, Cash Withdrawal completed Successfully.\n";
                strMSG += "Source A/C: " + theSourceAccountNumber + "\n";
                strMSG += "Mobile: " + theBeneficiaryMobileNo + "\n";
                strMSG += "Amount: KES " + Utils.formatDouble(theAmount, "#,##0.00") + "\n";
                // strMSG += "Date: "+strBeneficiaryName+"";
            */

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getFlexicoreHashMap("signatory_details");

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Airtime Purchase has been received successfully and is being processed.")
                    .putValue("msg_object", cbsMSG)
                    .putValue("response_payload", buyAirtimeMap)
                    .putValue("customer_full_name", signatoryDetailsMap.getStringValue("full_name"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.buyAirtime(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Airtime Purchase request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> bankTransferViaB2B(String strRequestingMobileNumber,
                                                                          String theIdentifierType,
                                                                          String theIdentifier,
                                                                          String theDeviceIdentifierType,
                                                                          String theDeviceIdentifier,
                                                                          String theOriginatorId,
                                                                          String theProductId,
                                                                          String thePesaType,
                                                                          String theAction,
                                                                          String theCommand,
                                                                          FlexicoreHashMap theInitiatorDetailsMap,
                                                                          FlexicoreHashMap theSourceDetailsMap,
                                                                          FlexicoreHashMap theSenderDetailsMap,
                                                                          FlexicoreHashMap theReceiverDetailsMap,
                                                                          FlexicoreHashMap theBeneficiaryDetailsMap,
                                                                          double theAmount,
                                                                          String theCategory,
                                                                          String theTransactionDescription,
                                                                          String theSourceReference,
                                                                          String theRequestApplication,
                                                                          String theSourceApplication) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Transfer to Bank request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> bankTransferWrapper = ApStarCBS.withdrawal(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    theProductId,
                    thePesaType,
                    theAction,
                    theCommand,
                    theInitiatorDetailsMap,
                    theSourceDetailsMap,
                    theSenderDetailsMap,
                    theReceiverDetailsMap,
                    theBeneficiaryDetailsMap,
                    theAmount,
                    theCategory,
                    theTransactionDescription,
                    theSourceReference,
                    theRequestApplication,
                    theSourceApplication,
                    "BANK_TRANSFER");

            if (bankTransferWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.bankTransferViaB2B() - " + bankTransferWrapper.getErrors() + "\n" + bankTransferWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Transfer to Bank request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Dear member, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap bankTransferResultMap = bankTransferWrapper.getSingleRecord();

            String requestStatus = bankTransferResultMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.bankTransferViaB2B(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                bankTransferResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Transfer to Bank request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Transfer to Bank request." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.bankTransferViaB2B(" + theOriginatorId + ", " + theSourceDetailsMap.getStringValue("account") + ")");
                bankTransferResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Transfer to Bank request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Transfer to Bank request. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            FlexicoreHashMap bankTransferMap = bankTransferResultMap.getFlexicoreHashMap("response_payload");

            String strTransactionDateTime = bankTransferMap.getStringValueOrIfNull("transaction_date_time", "").trim();
            String strFormattedDateTime = Utils.formatDate(strTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
            String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");

            String strMSG = "Dear member, your Bank Transfer Request of KES " + strFormattedAmount + " to " +
                            theReceiverDetailsMap.getStringValue("name") + " - " +
                            theReceiverDetailsMap.getStringValue("identifier") + ", " +
                            "for account " +
                            theReceiverDetailsMap.getStringValue("account") + " has been received and is being processed.";

            /*
                String strMSG = "Dear member, Cash Withdrawal completed Successfully.\n";
                strMSG += "Source A/C: " + theSourceAccountNumber + "\n";
                strMSG += "Mobile: " + theBeneficiaryMobileNo + "\n";
                strMSG += "Amount: KES " + Utils.formatDouble(theAmount, "#,##0.00") + "\n";
                // strMSG += "Date: "+strBeneficiaryName+"";
            */

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getFlexicoreHashMap("signatory_details");

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Transfer to Bank has been received successfully and is being processed.")
                    .putValue("msg_object", cbsMSG)
                    .putValue("response_payload", bankTransferMap)
                    .putValue("customer_full_name", signatoryDetailsMap.getStringValue("full_name"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.bankTransferViaB2B(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Transfer to Bank request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG));
        }
        return resultWrapper;
    }


    public static TransactionWrapper<FlexicoreHashMap> getCustomerLoanAccounts(String strRequestingMobileNumber,
                                                                               String theIdentifierType,
                                                                               String theIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            TransactionWrapper<FlexicoreArrayList> customerAccountsListWrapper = ApStarCBS.getMemberLoansInService(theIdentifierType, theIdentifier);

            if (customerAccountsListWrapper.hasErrors() && customerAccountsListWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCustomerLoanAccounts() - " + customerAccountsListWrapper.getErrors() + "\n" + customerAccountsListWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(customerAccountsListWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (customerAccountsListWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry, no loans in service were found.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, no loans in service were found."));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", customerAccountsListWrapper.getData())
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.getCustomerLoanAccounts(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.addError(e.getMessage());
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later."));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> loanBalanceEnquiry(String strRequestingMobileNumber,
                                                                          String theIdentifierType,
                                                                          String theIdentifier,
                                                                          String theDeviceIdentifierType,
                                                                          String theDeviceIdentifier,
                                                                          String theAccountNumber) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                System.err.println("=> Check User Failed for checkUser(..., " + theIdentifierType + ", " + theIdentifier + ", " + theDeviceIdentifierType + ", " + theDeviceIdentifier + ")");

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> accountBalanceWrapper = ApStarCBS.getLoanAccountBalance(theIdentifierType, theIdentifier, theAccountNumber);

            if (accountBalanceWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanAccountBalance() - " + accountBalanceWrapper.getErrors() + "\n" + accountBalanceWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", accountBalanceWrapper.getErrors() + " - " + accountBalanceWrapper.getMessages())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            FlexicoreHashMap accountBalanceResultMap = accountBalanceWrapper.getSingleRecord();
            String requestStatus = accountBalanceResultMap.getStringValue("request_status");

            FlexicoreHashMap accountBalResponseMap = accountBalanceResultMap.getFlexicoreHashMap("response_payload");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanAccountBalance(" + theAccountNumber + ")");
                accountBalanceResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Loan Balance Enquiry request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Loan Balance Enquiry request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + accountBalResponseMap.getStringValue("error_message") + " - " + accountBalResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanAccountBalance(" + theAccountNumber + ")");

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + accountBalResponseMap.getStringValue("error_message") + " - " + accountBalResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            /*FlexicoreHashMap accountBalanceMap = accountBalanceResultMap.getFlexicoreHashMap("account_balance");

            if (accountBalanceMap == null || accountBalanceMap.isEmpty()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, your Loan Balance Enquiry for loan '" + theAccountNumber + "' could not be processed. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your Loan Balance Enquiry for loan '" + theAccountNumber + "' could not be processed. Please try again later." + getTrailerMessage())
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }*/

            String strAccountName = accountBalResponseMap.getStringValueOrIfNull("loan_type_name", "").trim();
            String strAccountSerialNumber = accountBalResponseMap.getStringValueOrIfNull("loan_serial_number", "").trim();
            String strAccountBalance = accountBalResponseMap.getStringValueOrIfNull("loan_balance", "0").trim();
            String strAccountInterestAmount = accountBalResponseMap.getStringValueOrIfNull("interest_amount", "0").trim();

            double dblLoanBalance = Double.parseDouble(strAccountBalance);
            double dblLoanInterestBalance = Double.parseDouble(strAccountInterestAmount);

            double dblTotalLoanBalance = dblLoanBalance;


            strAccountBalance = Utils.formatDouble(strAccountBalance, "#,##0.00");
            strAccountInterestAmount = Utils.formatDouble(strAccountInterestAmount, "#,##0.00");

            String strMSG = "Dear member, your Loan Balance for A/C " + strAccountName + "- " + strAccountSerialNumber + " is KES " + Utils.formatDouble(dblTotalLoanBalance, "#,##0.00");

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            accountBalanceResultMap
                    .putValue("msg_object", cbsMSG)
                    .putValue("loan_balance", accountBalResponseMap.getStringValueOrIfNull("loan_balance", "0").trim())
                    .putValue("interest_amount", accountBalResponseMap.getStringValueOrIfNull("interest_amount", "0").trim())
                    .putValue("loan_name", strAccountName)
                    .putValue("loan_serial_number", strAccountSerialNumber)
            ;

            return accountBalanceWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.loanBalanceEnquiry(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }


    /*public static TransactionWrapper<FlexicoreHashMap> loanBalanceEnquiry(String strRequestingMobileNumber,
                                                                          String theIdentifierType,
                                                                          String theIdentifier,
                                                                          String theDeviceIdentifierType,
                                                                          String theDeviceIdentifier)
    {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                System.err.println("=> Check User Failed for checkUser(..., " + theIdentifierType + ", " + theIdentifier + ", " + theDeviceIdentifierType + ", " + theDeviceIdentifier + ")");

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Balance Enquiry request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());

                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreArrayList> loansListWrapper = ApStarCBS.getMemberLoansInService(theIdentifierType, theIdentifier);

            if (loansListWrapper.hasErrors() && loansListWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getCustomerLoanAccountsLOAN_BALANCE() - " + loansListWrapper.getErrors() + "\n" + loansListWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(loansListWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (loansListWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry, no loans in service were found.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, no loans in service were found."));

                return resultWrapper;
            }

            FlexicoreArrayList loansList = loansListWrapper.getData();

            StringBuilder accountsMSGBuilder = new StringBuilder("Dear member, your Loan Balances are:\n");
            for (FlexicoreHashMap loanAccountMap : loansList) {
                //String strAccountName = loanAccountMap.getStringValueOrIfNull("loan_type_name", "").trim();
                String strAccountName = loanAccountMap.getStringValueOrIfNull("loan_type_name", "").trim();
                String strAccountSerialNumber = loanAccountMap.getStringValueOrIfNull("loan_serial_number", "").trim();
                String strAccountBalance = loanAccountMap.getStringValueOrIfNull("loan_balance", "0").trim();
                String strInterestBalance = loanAccountMap.getStringValueOrIfNull("interest_balance", "0").trim();
                strAccountBalance = Utils.formatDouble(strAccountBalance, "#,##0.00");
                strInterestBalance = Utils.formatDouble(strInterestBalance, "#,##0.00");

                accountsMSGBuilder.append("Name: " + strAccountName + "\n");
                accountsMSGBuilder.append("Bal : KES " + strAccountBalance + "\n");
                accountsMSGBuilder.append("Int : KES " + strInterestBalance + "\n\n");
            }


            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(accountsMSGBuilder.toString());
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", loansList)
                    .putValue("msg_object", cbsMSG));

            return resultWrapper;

        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.loanBalanceEnquiry(): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Balance Enquiry request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }


    */


    public static TransactionWrapper<FlexicoreHashMap> getLoanMiniStatement(String strRequestingMobileNumber,
                                                                            String theIdentifierType,
                                                                            String theIdentifier,
                                                                            String theDeviceIdentifierType,
                                                                            String theDeviceIdentifier,
                                                                            String theLoanSerialNumber,
                                                                            String theNumberOfEntries) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> accountStatementListWrapper = ApStarCBS.getLoanMiniStatement(theIdentifierType, theIdentifier, theLoanSerialNumber,
                    theNumberOfEntries);

            if (accountStatementListWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanMiniStatement() - " + accountStatementListWrapper.getErrors() + "\n" + accountStatementListWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", accountStatementListWrapper.getErrors() + " - " + accountStatementListWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap accountStatementMap = accountStatementListWrapper.getSingleRecord();
            FlexicoreHashMap statementResponseMap = accountStatementMap.getFlexicoreHashMap("response_payload");

            String requestStatus = accountStatementMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanMiniStatement(" + theLoanSerialNumber + ")");
                accountStatementMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Loan Mini-Statement request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Loan Mini-Statement request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + statementResponseMap.getStringValue("error_message") + " - " + statementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanMiniStatement(" + theLoanSerialNumber + ")");
                accountStatementMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + statementResponseMap.getStringValue("error_message") + " - " + statementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String strMSG = "Dear member, your Mini-Statement request has FAILED. Please try again later.";

            FlexicoreArrayList allTransactionsList = statementResponseMap.getFlexicoreArrayList("transactions");
            if (allTransactionsList == null || allTransactionsList.isEmpty()) {
                strMSG = "Dear member, your Loan Mini-Statement request has FAILED. No transaction(s) found for account " + theLoanSerialNumber + ".";
            } else {

                StringBuilder miniStatementMsg = new StringBuilder("Dear member, your Loan " + theLoanSerialNumber + " Statement:\n");

                for (FlexicoreHashMap transactionMap : allTransactionsList) {

                    String strMSGTransactionReference = transactionMap.getStringValueOrIfNull("transaction_reference", "").trim();
                    String strMSGTransactionDate = transactionMap.getStringValueOrIfNull("transaction_date", "").trim();
                    //String strMSGTransactionTime = transactionMap.getStringValueOrIfNull("transaction_time", "").trim();
                    String strTransactionType = transactionMap.getStringValueOrIfNull("transaction_type", "").trim();

                    String strMSGRunningBalance = transactionMap.getStringValue("running_balance").trim();
                    String strMSGTransactionAmount = transactionMap.getStringValue("transaction_amount").trim();
                    String strMSGTransactionComments = transactionMap.getStringValueOrIfNull("transaction_description", "").trim();

                    strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");

                    if (strTransactionType.equalsIgnoreCase("CREDIT")) {
                        strMSGTransactionAmount = "-" + strMSGTransactionAmount;
                    }

                    transactionMap.putValue("transaction_amount", strMSGTransactionAmount);


                    //strMSGRunningBalance = strMSGRunningBalance.replace("-", "");
                    //strMSGIntRunningBalance = strMSGIntRunningBalance.replace("-", "");

                    //double totalPayable = Double.parseDouble(strMSGRunningBalance) + Double.parseDouble(strMSGIntRunningBalance);
                    //String strFormattedLoanTotalPayableAmount = Utils.formatDouble(String.valueOf(totalPayable), "#,##0.00");

                    String strMSGFormattedTransactionAmount = Utils.formatDouble(strMSGTransactionAmount, "#,##0.00");
                    String strMSGFormattedRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                    String strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGTransactionDate, "yyyy-MM-dd", "dd-MMM-yyyy");
                    strMSGFormattedTransactionDateTime = strMSGFormattedTransactionDateTime ;

                    //String strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                    transactionMap.putValue("transaction_date_time", strMSGFormattedTransactionDateTime);

                    miniStatementMsg.append("Ref: ").append(strMSGTransactionReference).append("\n");
                    miniStatementMsg.append("Date: ").append(strMSGFormattedTransactionDateTime).append("\n");
                    miniStatementMsg.append("Amnt: ").append(strMSGFormattedTransactionAmount).append("\n");
                    miniStatementMsg.append("Descr: ").append(strMSGTransactionComments).append("\n");
                    miniStatementMsg.append("Run. Bal: KES ").append(strMSGFormattedRunningBalance).append("\n\n");
                }

                strMSG = miniStatementMsg.toString();
            }

            String strRunningBalance = statementResponseMap.getStringValue("loan_balance").trim();
//            String strIntRunningBalance = statementResponseMap.getStringValue("interest_amount").trim();

            //strRunningBalance = strRunningBalance.replace("-", "");
            //strIntRunningBalance = strIntRunningBalance.replace("-", "");

//            double totalPayable = Double.parseDouble(strRunningBalance) + Double.parseDouble(strIntRunningBalance);

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getFlexicoreHashMap("signatory_details");

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", allTransactionsList)
                    .putValue("account_name", statementResponseMap.getStringValueOrIfNull("loan_name", "").trim())
                    .putValue("account_holder", signatoryDetailsMap.getStringValueOrIfNull("full_name", "").trim())
                    .putValue("account_available_balance", strRunningBalance)
                    .putValue("msg_object", cbsMSG));

            return resultWrapper;
        } catch (Exception e) {

            System.err.println(strRequestingMobileNumber + " => CBSAPI.getLoanMiniStatement(" + theLoanSerialNumber + "): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Mini-Statement request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getLoanFullStatement(String strRequestingMobileNumber,
                                                                            String theIdentifierType,
                                                                            String theIdentifier,
                                                                            String theDeviceIdentifierType,
                                                                            String theDeviceIdentifier,
                                                                            String theLoanSerialNumber,
                                                                            String theNumberOfEntries,
                                                                            String theStartDate,
                                                                            String theEndDate) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> accountFullStatementListWrapper = ApStarCBS.getLoanFullStatement(theIdentifierType, theIdentifier, theLoanSerialNumber,
                    theNumberOfEntries, theStartDate, theEndDate);

            if (accountFullStatementListWrapper.hasErrors()) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanFullStatement() - " + accountFullStatementListWrapper.getErrors() + "\n" + accountFullStatementListWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", accountFullStatementListWrapper.getErrors() + " - " + accountFullStatementListWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap accountMiniStatementMap = accountFullStatementListWrapper.getSingleRecord();
            FlexicoreHashMap miniStatementResponseMap = accountMiniStatementMap.getFlexicoreHashMap("response_payload");


            String requestStatus = accountMiniStatementMap.getStringValue("request_status");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanFullStatement(" + theLoanSerialNumber + ")");
                accountMiniStatementMap.printRecordVerticalLabelled();


                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, your account balance is insufficient to process your Loan Statement request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Loan Statement request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + miniStatementResponseMap.getStringValue("error_message") + " - " + miniStatementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanFullStatement(" + theLoanSerialNumber + ")");
                accountMiniStatementMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Statement request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Statement request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + miniStatementResponseMap.getStringValue("error_message") + " - " + miniStatementResponseMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String strMSG = "Dear member, your Mini-Statement request has FAILED. Please try again later.";

            FlexicoreArrayList allTransactionsList = miniStatementResponseMap.getFlexicoreArrayList("transactions");
            if (allTransactionsList == null || allTransactionsList.isEmpty()) {
                strMSG = "Dear member, your Loan Statement request has FAILED. No transaction(s) found for account " + theLoanSerialNumber + ".";
            } else {

                StringBuilder miniStatementMsg = new StringBuilder("Dear member, your Loan " + theLoanSerialNumber + " Statement:\n");

                for (FlexicoreHashMap transactionMap : allTransactionsList) {

                    String strMSGTransactionReference = transactionMap.getStringValueOrIfNull("transaction_reference", "").trim();
                    String strMSGTransactionDate = transactionMap.getStringValueOrIfNull("transaction_date", "").trim();
                    //String strMSGTransactionTime = transactionMap.getStringValueOrIfNull("transaction_time", "").trim();
                    String strTransactionType = transactionMap.getStringValueOrIfNull("transaction_type", "").trim();

                    String strMSGRunningBalance = transactionMap.getStringValue("running_balance").trim();
                    //String strMSGIntRunningBalance = transactionMap.getStringValue("int_running_balance").trim();
                    String strMSGTransactionAmount = transactionMap.getStringValue("transaction_amount").trim();
                    String strMSGTransactionComments = transactionMap.getStringValueOrIfNull("transaction_description", "").trim();

                    strMSGTransactionAmount = strMSGTransactionAmount.replace("-", "");

                    if (strTransactionType.equalsIgnoreCase("DEBIT")) {
                        strMSGTransactionAmount = "-" + strMSGTransactionAmount;
                    }

                    transactionMap.putValue("transaction_amount", strMSGTransactionAmount);

                    //strMSGRunningBalance = strMSGRunningBalance.replace("-", "");
                    //strMSGIntRunningBalance = strMSGIntRunningBalance.replace("-", "");

                    //double totalPayable = Double.parseDouble(strMSGRunningBalance) + Double.parseDouble(strMSGIntRunningBalance);
                    //String strFormattedLoanTotalPayableAmount = Utils.formatDouble(String.valueOf(totalPayable), "#,##0.00");


                    String strMSGFormattedTransactionAmount = Utils.formatDouble(strMSGTransactionAmount, "#,##0.00");
                    String strMSGFormattedRunningBalance = Utils.formatDouble(strMSGRunningBalance, "#,##0.00");

                    //String strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGTransactionDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
                    String strMSGFormattedTransactionDateTime = DateTime.convertStringToDateToString(strMSGTransactionDate, "yyyy-MM-dd", "dd-MMM-yyyy");
                    strMSGFormattedTransactionDateTime = strMSGFormattedTransactionDateTime;

                    transactionMap.putValue("transaction_date_time", strMSGFormattedTransactionDateTime);

                    miniStatementMsg.append("Ref: ").append(strMSGTransactionReference).append("\n");
                    miniStatementMsg.append("Date: ").append(strMSGFormattedTransactionDateTime).append("\n");
                    miniStatementMsg.append("Amnt: ").append(strMSGFormattedTransactionAmount).append("\n");
                    miniStatementMsg.append("Descr: ").append(strMSGTransactionComments).append("\n");
                    miniStatementMsg.append("Run. Bal: KES ").append(strMSGFormattedRunningBalance).append("\n\n");
                }

                strMSG = miniStatementMsg.toString();
            }

            String strRunningBalance = miniStatementResponseMap.getStringValue("loan_balance").trim();
            // String strIntRunningBalance = accountMiniStatementMap.getStringValue("interest_balance").trim();

            //strRunningBalance = strRunningBalance.replace("-", "");
            //strIntRunningBalance = strIntRunningBalance.replace("-", "");

            // double totalPayable = Double.parseDouble(strRunningBalance) + Double.parseDouble(strIntRunningBalance);

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getFlexicoreHashMap("signatory_details");

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", allTransactionsList)
                    .putValue("account_name", miniStatementResponseMap.getStringValueOrIfNull("loan_name", "").trim())
                    .putValue("account_holder", signatoryDetailsMap.getStringValueOrIfNull("full_name", "").trim())
                    .putValue("account_available_balance", strRunningBalance)
                    .putValue("msg_object", cbsMSG));

            return resultWrapper;
        } catch (Exception e) {

            System.err.println(strRequestingMobileNumber + " => CBSAPI.getLoanFullStatement(" + theLoanSerialNumber + "): " + e.getMessage());
            e.printStackTrace();

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Statement request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your Loan Statement request. Please try again later." + getTrailerMessage())
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_api_error_message", e.getMessage())
            );
        }
        return resultWrapper;
    }


    public static TransactionWrapper<FlexicoreHashMap> checkLoanLimit(String strRequestingMobileNumber,
                                                                      String theIdentifierType,
                                                                      String theIdentifier,
                                                                      String theDeviceIdentifierType,
                                                                      String theDeviceIdentifier,
                                                                      String theLoanTypeId,
                                                                      String theLoanAmount) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Loan Qualification Check request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);
                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> checkLoanLimitWrapper = ApStarCBS.checkLoanLimit(
                    theIdentifierType,
                    theIdentifier,
                    theLoanTypeId,theLoanAmount);

            if (checkLoanLimitWrapper.hasErrors()) {
                System.err.println(theIdentifier + " => ApStarCBS.checkLoanLimit() - " + checkLoanLimitWrapper.getErrors() + "\n" + checkLoanLimitWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Loan Qualification Check request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "Transaction Error")
                        .putValue("cbs_api_error_message", checkLoanLimitWrapper.getErrors() + " - " + checkLoanLimitWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap checkLoanLimitResultMap = checkLoanLimitWrapper.getSingleRecord();

            String requestStatus = checkLoanLimitResultMap.getStringValue("request_status");
            FlexicoreHashMap limitMap = checkLoanLimitResultMap.getFlexicoreHashMap("response_payload");

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.checkLoanLimit(" + theLoanTypeId + ")");
                checkLoanLimitResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Qualification Check request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Qualification Check request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + limitMap.getStringValue("error_message") + " - " + limitMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String loanName = limitMap.getStringValue("loan_name");
            String eligibleAmount = limitMap.getStringValue("eligible_amount");

            String strMSG;
            if (Double.parseDouble(eligibleAmount) < 10) {
                String strFormattedAmount = Utils.formatDouble(eligibleAmount, "#,##0.00");
                strMSG = "Dear member, you do not qualify to apply for Loan " + loanName + "";
            } else {
                String strFormattedAmount = Utils.formatDouble(eligibleAmount, "#,##0.00");
                strMSG = "Dear member, you are qualified to apply for Loan " + loanName + ", amount KES " + strFormattedAmount + ".";
            }

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Loan Qualification Check has been received successfully and is being processed.")
                    .putValue("title", "Transaction Accepted")
                    .putValue("msg_object", cbsMSG)
                    .putValue("payload", limitMap)
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(theIdentifier + " => CBSAPI.checkLoanLimit(): " + e.getMessage());
            e.printStackTrace();
            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Qualification Check request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("title", "Transaction Error")
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("cbs_api_error_message", e.getMessage())
                    .putValue("msg_object", cbsMSG)

            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> loanApplication(String strRequestingMobileNumber,
                                                                       String theIdentifierType,
                                                                       String theIdentifier,
                                                                       String theDeviceIdentifierType,
                                                                       String theDeviceIdentifier,
                                                                       String theLoanTypeId,
                                                                       double theAmount,
                                                                       String theLoanDurationId,
                                                                       String theMerchantId,
                                                                       FlexicoreArrayList theItemsList,
                                                                       String theSourceReference,
                                                                       String theRequestApplication,
                                                                       String theTransactionDateTime
    ) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Loan Application request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);
                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> loanApplicationWrapper = ApStarCBS.loanApplication(
                    theIdentifierType,
                    theIdentifier,
                    theLoanTypeId,
                    theAmount,
                    theLoanDurationId,
                    theMerchantId,
                    theItemsList,
                    theSourceReference,
                    theRequestApplication,
                    theTransactionDateTime);

            if (loanApplicationWrapper.hasErrors()) {
                System.err.println(theIdentifier + " => ApStarCBS.loanApplication() - " + loanApplicationWrapper.getErrors() + "\n" + loanApplicationWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Loan Application request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "Transaction Error")
                        .putValue("cbs_api_error_message", loanApplicationWrapper.getErrors() + " - " + loanApplicationWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap loanApplicationResultMap = loanApplicationWrapper.getSingleRecord();

            String requestStatus = loanApplicationResultMap.getStringValue("request_status");
            FlexicoreHashMap applicationMap = loanApplicationResultMap.getFlexicoreHashMap("response_payload");

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.loanApplication(" + theLoanTypeId + ")");
                loanApplicationResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Application request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Application request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + applicationMap.getStringValue("error_message") + " - " + applicationMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String loanName = applicationMap.getStringValue("loan_name");
            String amount = applicationMap.getStringValue("amount");
            /*String eligibleAmount = applicationMap.getStringValue("eligible_amount");

            String strMSG;
            if (Double.parseDouble(eligibleAmount) < 10) {
                String strFormattedAmount = Utils.formatDouble(eligibleAmount, "#,##0.00");
                strMSG = "Dear member, you do not qualify to apply for Loan " + loanName + "";
            } else {
                String strFormattedAmount = Utils.formatDouble(eligibleAmount, "#,##0.00");
                strMSG = "Dear member, you are qualified to apply for Loan " + loanName + ", amount KES " + strFormattedAmount + ".";
            }*/

            String strFormattedAmount = Utils.formatDouble(amount, "#,##0.00");

            String strMSG = "Dear member, your Loan Application has been received successfully and is being processed.";


            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Loan Application received successfully and is being processed.")
                    .putValue("title", "Transaction Accepted")
                    .putValue("msg_object", cbsMSG)
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(theIdentifier + " => CBSAPI.loanApplication(): " + e.getMessage());
            e.printStackTrace();


            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Application request. Please try again later.");
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("title", "Transaction Error")
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("cbs_api_error_message", e.getMessage())
                    .putValue("msg_object", cbsMSG)

            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> loanPaymentViaSavings(String strRequestingMobileNumber,
                                                                             String theIdentifierType,
                                                                             String theIdentifier,
                                                                             String theDeviceIdentifierType,
                                                                             String theDeviceIdentifier,
                                                                             String theOriginatorId,
                                                                             String theSourceAccount,
                                                                             String theDestinationAccount,
                                                                             double theAmount,
                                                                             String theTransactionDescription,
                                                                             String theSourceReference,
                                                                             String theRequestApplication,
                                                                             String theSourceApplication) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(UUID.randomUUID().toString(), theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Loan Payment via Savings Account request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);
                checkUserResultMap.putValue("msg_object", cbsMSG);
                checkUserResultMap.putValue("cbs_api_error_message", checkUserResultMapWrapper.getErrors() + " - " + checkUserResultMapWrapper.getMessages());
                return checkUserResultMapWrapper;
            }

            TransactionWrapper<FlexicoreHashMap> loanPaymentViaSavingsWrapper = ApStarCBS.loanPaymentViaSavings(
                    theIdentifierType,
                    theIdentifier,
                    theOriginatorId,
                    theSourceAccount,
                    theDestinationAccount,
                    theAmount,
                    theTransactionDescription,
                    theSourceReference,
                    theRequestApplication,
                    theSourceApplication);

            if (loanPaymentViaSavingsWrapper.hasErrors()) {
                System.err.println(theIdentifier + " => ApStarCBS.loanPaymentViaSavings() - " + loanPaymentViaSavingsWrapper.getErrors() + "\n" + loanPaymentViaSavingsWrapper.getMessages());

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, an error occurred while processing your Loan Payment via Savings Account request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                        .putValue("title", "Transaction Error")
                        .putValue("cbs_api_error_message", loanPaymentViaSavingsWrapper.getErrors() + " - " + loanPaymentViaSavingsWrapper.getMessages())
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            FlexicoreHashMap loanPaymentViaSavingsResultMap = loanPaymentViaSavingsWrapper.getSingleRecord();

            String requestStatus = loanPaymentViaSavingsResultMap.getStringValue("request_status");
            FlexicoreHashMap loanPaymentViaSavingsMap = loanPaymentViaSavingsResultMap.getFlexicoreHashMap("response_payload");

            if (requestStatus.equalsIgnoreCase("INSUFFICIENT_BAL") ) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.loanPaymentViaSavings(" + theSourceAccount + ")");
                loanPaymentViaSavingsResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Dear member, the source account " + theSourceAccount + " balance is insufficient to process your Loan Payment via Savings Account request");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setHasErrors(true);
                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.NO)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("title", "Insufficient Balance")
                        .putValue("display_message", "Sorry, your account balance is insufficient to process your Loan Payment via Savings Account request." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + loanPaymentViaSavingsMap.getStringValue("error_message") + " - " + loanPaymentViaSavingsMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG));

                return resultWrapper;
            }

            //if request status is error
            if(requestStatus.equalsIgnoreCase("ERROR")){
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.loanPaymentViaSavings(" + theSourceAccount + ")");
                loanPaymentViaSavingsResultMap.printRecordVerticalLabelled();
                FlexicoreHashMap accountBalResponseMap = loanPaymentViaSavingsResultMap.getFlexicoreHashMap("response_payload");


                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage(accountBalResponseMap.getStringValueOrIfNull("transaction_status_description","Sorry, an error occurred while processing your Loan Payment via Savings Account request. Please try again later."));
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("title", "Transaction Error")
                        .putValue("display_message", accountBalResponseMap.getStringValueOrIfNull("transaction_status_description","Sorry, an error occurred while processing your Loan Payment via Savings Account request. Please try again later.") + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + loanPaymentViaSavingsMap.getStringValue("error_message") + " - " + loanPaymentViaSavingsMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                System.err.println(strRequestingMobileNumber + " => ApStarCBS.loanPaymentViaSavings(" + theSourceAccount + ")");
                loanPaymentViaSavingsResultMap.printRecordVerticalLabelled();

                SMSMSG cbsMSG = new SMSMSG();
                cbsMSG.setMessage("Sorry, an error occurred while processing your Loan Payment via Savings Account request. Please try again later.");
                cbsMSG.setMode(MSGConstants.MSGMode.SAF);
                cbsMSG.setPriority(210);
                cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("title", "Transaction Error")
                        .putValue("display_message", "Sorry, an error occurred while processing your Loan Payment via Savings Account request. Please try again later." + getTrailerMessage())
                        .putValue("cbs_api_error_message", requestStatus + ": " + loanPaymentViaSavingsMap.getStringValue("error_message") + " - " + loanPaymentViaSavingsMap.getStringValue("devMessage"))
                        .putValue("msg_object", cbsMSG)
                );

                return resultWrapper;
            }

            String strFormattedDateTime = DateTime.getCurrentDateTime("dd-MMM-yyyy HH:mm:ss");
            //String strFormattedDateTime = Utils.formatDate(strDateTime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");
            String strFormattedAmount = Utils.formatDouble(theAmount, "#,##0.00");

            String strMSG = "Dear member, your Request for Loan Payment via Savings Account of KES " + strFormattedAmount + " to account " + theDestinationAccount + " on " + strFormattedDateTime + " has been received and is being processed.";

            SMSMSG cbsMSG = new SMSMSG();
            cbsMSG.setMessage(strMSG);
            cbsMSG.setMode(MSGConstants.MSGMode.SAF);
            cbsMSG.setPriority(210);
            cbsMSG.setSensitivity(MSGConstants.Sensitivity.NORMAL);

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("display_message", "Loan Payment via Savings Account received successfully.")
                    .putValue("title", "Transaction Accepted")
                    .putValue("msg_object", cbsMSG)
                    .putValue("cbs_transaction_reference", loanPaymentViaSavingsMap.getStringValue("transaction_reference"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(theIdentifier + " => CBSAPI.loanPaymentViaSavings(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("title", "Transaction Error")
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage())
                    .putValue("cbs_api_error_message", e.getMessage())

            );
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getLoanTypes(String strRequestingMobileNumber,
                                                                    String theIdentifierType,
                                                                    String theIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            TransactionWrapper<FlexicoreArrayList> loanTypesListWrapper = ApStarCBS.getLoanTypes(theIdentifierType, theIdentifier);

            if (loanTypesListWrapper.hasErrors() && loanTypesListWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanTypes() - " + loanTypesListWrapper.getErrors() + "\n" + loanTypesListWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(loanTypesListWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (loanTypesListWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry, no loans found.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, no loans found."));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", loanTypesListWrapper.getData())
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.getLoanTypes(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.addError(e.getMessage());
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later."));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getMerchants(String strRequestingMobileNumber,
                                                                    String theIdentifierType,
                                                                    String theIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
            TransactionWrapper<FlexicoreArrayList> merchantsListWrapper = ApStarCBS.getMerchants(theIdentifierType, theIdentifier);

            if (merchantsListWrapper.hasErrors() && merchantsListWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanTypes() - " + merchantsListWrapper.getErrors() + "\n" + merchantsListWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(merchantsListWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (merchantsListWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry! There are no sellers found.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry! There are no sellers found."));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", merchantsListWrapper.getData())
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.getLoanTypes(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.addError(e.getMessage());
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later."));
        }
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getMerchantProducts(String strRequestingMobileNumber,
                                                                    String theIdentifierType,
                                                                    String theIdentifier,
                                                                    String strMerchantId,
                                                                    String strPage,
                                                                    String strPageCount) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {
//            TransactionWrapper<FlexicoreArrayList> loanTypesListWrapper = ApStarCBS.getMerchantProducts(theIdentifierType, theIdentifier,strMerchantId,strPage,strPageCount);
            TransactionWrapper<FlexicoreHashMap> merchantProductsWrapper = ApStarCBS.getMerchantProducts(theIdentifierType, theIdentifier,strMerchantId,strPage,strPageCount);

            if (merchantProductsWrapper.hasErrors() && merchantProductsWrapper.getStatusCode() != HttpsURLConnection.HTTP_NOT_FOUND) {
                System.err.println(strRequestingMobileNumber + " => ApStarCBS.getLoanTypes() - " + merchantProductsWrapper.getErrors() + "\n" + merchantProductsWrapper.getMessages());

                resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(merchantProductsWrapper.getErrors());
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later." + getTrailerMessage()));

                return resultWrapper;
            }

            if (merchantProductsWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError("Sorry! There are no products listed for sale by this seller.");
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry! There are no products listed for sale by this seller."));

                return resultWrapper;
            }

            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.NO)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.SUCCESS)
                    .putValue("payload", merchantProductsWrapper.getData())
//                    .putValue("pagination", merchantProductsWrapper.getSingleRecord().getFlexicoreHashMap("pagination"))
            );

            return resultWrapper;
        } catch (Exception e) {
            System.err.println(strRequestingMobileNumber + " => CBSAPI.getLoanTypes(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.addError(e.getMessage());
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later."));
        }
        return resultWrapper;
    }

    public static double dailyTotalMoneyOut(String theSourceIdentifier, String limitType) {

        try {

            String strFilter = "";

            if (limitType.equalsIgnoreCase("BANK_TRANSFER")) {
                strFilter = " AND pesa_category = :pesa_category";
            } else {
                strFilter = " AND pesa_category != :pesa_category";
            }

            TransactionWrapper<FlexicoreHashMap> wrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    "mbanking_logs.pesa_out_log",
                    "SUM(transaction_amount) AS total_amount",
                    new FilterPredicate("pesa_type = :pesa_type AND source_identifier = :source_identifier\n" +
                                        " AND local_date_created >= :date_created_lower_limit AND local_date_created <= :date_created_upper_limit\n" +
                                        " AND pesa_status_code IN (10, 101, 102, 103, 105, 106)\n" + strFilter
                    ),
                    new FlexicoreHashMap()
                            .addQueryArgument(":pesa_category", "BANK_TRANSFER")
                            .addQueryArgument(":pesa_type", "PESA_OUT")
                            .addQueryArgument(":source_identifier", theSourceIdentifier)
                            .addQueryArgument(":date_created_lower_limit", DateTime.getCurrentDate() + " 00:00:00.000000")
                            .addQueryArgument(":date_created_upper_limit", DateTime.getCurrentDate() + " 23:59:59.999999")
            );

            FlexicoreHashMap flexicoreHashMap = wrapper.getSingleRecord();

            String strWithdrawnAmount = "0";

            if (flexicoreHashMap != null && !flexicoreHashMap.isEmpty()) {
                strWithdrawnAmount = flexicoreHashMap.getStringValueOrIfNull("total_amount", "0");
            }

            String strOrgParametersXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strOrgParametersXML);

            String strDailyLimit;

            if (limitType.equalsIgnoreCase("BANK_TRANSFER")) {
                strDailyLimit = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/DAILY_LIMITS/BANK_TRANSFER");
            } else {
                strDailyLimit = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/DAILY_LIMITS/WITHDRAWAL");
            }

            double dblDailyLimit = Double.parseDouble(strDailyLimit);
            double dblWithdrawnAmount = Double.parseDouble(strWithdrawnAmount);

            double dblRemainingBalance = dblDailyLimit - dblWithdrawnAmount;
            if (dblRemainingBalance < 10) {
                dblRemainingBalance = 0;
            }

            return dblRemainingBalance;
        } catch (Exception e) {
            System.err.println("CBSAPI.dailyTotalMoneyOut(): " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }


    public static boolean isMandateInactive(Object strMobileNumber, AppConstants.MobileMandates mobileMandate) {

        QueryBuilder queryBuilder = new QueryBuilder()
                .select()
                .selectColumn("msm.*")
                .from()
                .joinPhrase("""
                        mobile_banking.mobile_signatory_mandates msm
                             LEFT JOIN mobile_banking.mobile_banking_register mbr
                             ON mbr.mobile_register_id = msm.mobile_register_id""")
                .where("mbr.mobile_number = :mobile_number AND msm.mandate_code = :mandate_code");

        TransactionWrapper<FlexicoreHashMap> wrapper = Repository.joinSelectQuery(StringRefs.SENTINEL,
                queryBuilder,
                new FlexicoreHashMap()
                        .addQueryArgument(":mobile_number", strMobileNumber.toString())
                        .addQueryArgument(":mandate_code", mobileMandate.getValue())
        );

        if (wrapper.displayQueriesExecuted().hasErrors()) {
            return true;
        }

        FlexicoreHashMap flexicoreHashMap = wrapper.getSingleRecord();

        if (flexicoreHashMap == null || flexicoreHashMap.isEmpty()) {
            System.err.println("No Mandate found for " + AppConstants.strMobileBankingName + " Number '" + strMobileNumber + "' AND mandate_code = '" + mobileMandate.name() + ": " + mobileMandate.getValue() + "'");
            return true;
        }

        return !flexicoreHashMap.getStringValue("status").equalsIgnoreCase("ACTIVE");
    }

    public static FlexicoreHashMap getServiceStatusDetails(AppConstants.MobileBankingChannel mobileBankingChannel, AppConstants.MobileBankingServices mobileBankingService) {
        try {
            String strSettingsXML = SystemParameters.getParameter("MBANKING_SERVICES_MANAGEMENT");
            Document document = XmlUtils.parseXml(strSettingsXML);

            Element elService = XmlUtils.getElementNodeFromXpath(document, "/MBANKING_SERVICES/" + mobileBankingChannel.getValue() + "/SERVICE[@CODE='" + mobileBankingService.getValue() + "']");
            if (elService != null) {

                FlexicoreHashMap serviceMap = new FlexicoreHashMap();
                serviceMap.putValue("code", elService.getAttribute("CODE"));
                serviceMap.putValue("label", elService.getAttribute("LABEL"));
                serviceMap.putValue("status", elService.getAttribute("STATUS"));
                serviceMap.putValue("status_date", elService.getAttribute("STATUS_DATE"));
                serviceMap.putValue("display_message", elService.getAttribute("DISPLAY_MESSAGE"));

                return serviceMap;
            } else {
                return new FlexicoreHashMap().putValue("status", "ACTIVE");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new FlexicoreHashMap().putValue("status", "ACTIVE");
        }
    }

    public static FlexicoreHashMap existingDividendsRequest(Object strMobileNumber) {

        TransactionWrapper<FlexicoreHashMap> wrapper = Repository.selectWhere(StringRefs.SENTINEL,
                TBL_DIVIDENDS_REQUESTS,
                new FilterPredicate("phone_number = :phone_number AND (disbursement_status = :disbursement_status1 OR " +
                                    "disbursement_status = :disbursement_status2)"),
                new FlexicoreHashMap()
                        .addQueryArgument(":phone_number", strMobileNumber.toString())
                        .addQueryArgument(":disbursement_status1", "UNPAID")
                        .addQueryArgument(":disbursement_status2", "PENDING_PAYMENT")
        );

        if (wrapper.displayQueriesExecuted().hasErrors()) {
            return null;
        }

        FlexicoreHashMap flexicoreHashMap = wrapper.getSingleRecord();
        if (flexicoreHashMap == null || flexicoreHashMap.isEmpty()) {
            return null;
        }

        return flexicoreHashMap;
    }

    public static FlexicoreHashMap createDividendsRequest(FlexicoreHashMap insertMap) {


        TransactionWrapper<FlexicoreHashMap> wrapper = Repository.insertAutoIncremented(StringRefs.SENTINEL, TBL_DIVIDENDS_REQUESTS, insertMap);

        if (wrapper.displayQueriesExecuted().hasErrors()) {
            return null;
        }

        FlexicoreHashMap flexicoreHashMap = wrapper.getSingleRecord();
        if (flexicoreHashMap == null || flexicoreHashMap.isEmpty()) {
            return null;
        }

        return flexicoreHashMap;
    }

    public static FlexicoreArrayList getHelpAndSupportCategories() {

        try {

            TransactionWrapper<FlexicoreArrayList> wrapper = Repository.selectWhereOrderBy(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT_CATEGORIES,
                    new FilterPredicate("status = :status"),
                    "category_code",
                    new FlexicoreHashMap().addQueryArgument(":status", "ACTIVE"));

            FlexicoreArrayList flexicoreArrayList = wrapper.getData();

            if (flexicoreArrayList == null) {
                flexicoreArrayList = new FlexicoreArrayList();
            }

            return flexicoreArrayList;
        } catch (Exception e) {
            System.err.println("CBSAPI.getHelpAndSupportCategories(): " + e.getMessage());
            e.printStackTrace();
        }
        return new FlexicoreArrayList();
    }

    public static FlexicoreArrayList getSupportRequestsList(String signatoryId, String strStatus, String strDateFrom, String strDateTo) {

        try {

            FlexicoreHashMap queryArguments = new FlexicoreHashMap();
            queryArguments.addQueryArgument(":signatory_id", signatoryId);

            String strFilter = "has.signatory_id = :signatory_id";

            switch (strStatus) {
                case "OPEN" -> strFilter = "  AND has.status IN ('OPEN', 'UNASSIGNED', 'PENDING')";
                case "CLOSED" -> strFilter = "  AND has.status = 'CLOSED'";
                case "ALL" -> {
                }
            }

            if (strDateFrom.isBlank()) {
                strDateFrom = "2000-01-01";
            }

            if (strDateTo.isBlank()) {
                strDateTo = "2100-12-31";
            }

            queryArguments.addQueryArgument(":date_from", strDateFrom);
            queryArguments.addQueryArgument(":date_to", strDateTo);

            strFilter = strFilter + " AND has.date_created >= :date_from AND has.date_created <= :date_to";

            QueryBuilder queryBuilderSupport = new QueryBuilder()
                    .select()
                    .selectColumn("has.*, " +
                                  "hasc.category_code," +
                                  " hasc.category_name"
                    )
                    .from()
                    .joinPhrase(TBL_HELP_AND_SUPPORT + " has\n" +
                                "         LEFT JOIN " + TBL_HELP_AND_SUPPORT_CATEGORIES + " hasc ON has.category_id = hasc.category_id")
                    .where(strFilter)
                    .orderBy("has.support_id DESC");

            FlexicoreArrayList flexicoreArrayList = (FlexicoreArrayList) Repository.joinSelectQuery(StringRefs.SENTINEL, queryBuilderSupport,
                    queryArguments).getData();

            if (flexicoreArrayList == null) {
                flexicoreArrayList = new FlexicoreArrayList();
            }

            return flexicoreArrayList;
        } catch (Exception e) {
            System.err.println("CBSAPI.getSupportRequestList(): " + e.getMessage());
            e.printStackTrace();
        }
        return new FlexicoreArrayList();
    }

    public static FlexicoreHashMap closeSupportRequest(String signatoryId, String strSupportId) {

        try {

            TransactionWrapper<?> updateWrapper = Repository.update(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT,
                    new FlexicoreHashMap()
                            .putValue("closed_by_member", "YES")
                            .putValue("status", "CLOSED")
                            .putValue("date_closed", DateTime.getCurrentDateTime())
                            .putValue("date_modified", DateTime.getCurrentDateTime()),
                    new FilterPredicate("support_id = :support_id"),
                    new FlexicoreHashMap().addQueryArgument(":support_id", strSupportId));

            FlexicoreHashMap requestInfoMap = new FlexicoreHashMap();
            requestInfoMap.putValue("request_comments", "Request closed by member");
            requestInfoMap.putValue("user_reference_type", "CUSTOMER_SIGNATORY");
            requestInfoMap.putValue("user_reference_value", signatoryId);
            requestInfoMap.putValue("support_id", strSupportId);
            requestInfoMap.putValue("date_created", DateTime.getCurrentDateTime());
            requestInfoMap.putValue("date_modified", DateTime.getCurrentDateTime());

            requestInfoMap = Repository.insertAutoIncremented(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT_REQUEST_INFO, requestInfoMap).getSingleRecord();

            Repository.update(StringRefs.SENTINEL,
                    TBL_HELP_AND_SUPPORT,
                    new FlexicoreHashMap()
                            .putValue("last_request_info_id", requestInfoMap.getStringValue("request_info_id"))
                            .putValue("date_modified", DateTime.getCurrentDateTime()),
                    new FilterPredicate("support_id = :support_id"),
                    new FlexicoreHashMap().addQueryArgument(":support_id", strSupportId));


            return updateWrapper.getSingleRecord();
        } catch (Exception e) {
            System.err.println("CBSAPI.closeSupportRequest(): " + e.getMessage());
            e.printStackTrace();
        }
        return new FlexicoreHashMap();
    }

    public static String getNextSupportReference() {

        try {
            FlexicoreHashMap parameterMap = Repository.selectWhere(StringRefs.SENTINEL, TBL_SYSTEM_PARAMETERS,
                    new FilterPredicate("parameter_name = :parameter_name"),
                    new FlexicoreHashMap().addQueryArgument(":parameter_name", "HELP_AND_SUPPORT")).getSingleRecord();

            String paramValue = parameterMap.getStringValue("parameter_value");

            Document document = XmlUtils.parseXml(paramValue);

            String nextSupportReference = XmlUtils.getTagValue(document, "/HELP_AND_SUPPORT/NEXT_SUPPORT_REFERENCE");

            long lgNextSupportReference = Long.parseLong(nextSupportReference);

            long updatedSupportReference = lgNextSupportReference + 1;

            Document docUpdated = XmlUtils.updateXMLTag(document, "/HELP_AND_SUPPORT/NEXT_SUPPORT_REFERENCE", String.valueOf(updatedSupportReference));

            String updatedXml = XmlUtils.convertNodeToStr(docUpdated);

            TransactionWrapper<?> wrapper = Repository.update(StringRefs.SENTINEL, TBL_SYSTEM_PARAMETERS,
                    new FlexicoreHashMap()
                            .putValue("parameter_value", updatedXml)
                            .putValue("date_modified", DateTime.getCurrentDateTime()),
                    new FilterPredicate("parameter_id = :parameter_id"),
                    new FlexicoreHashMap().addQueryArgument(":parameter_id", parameterMap.getStringValue("parameter_id"))
            );

            if (wrapper.hasErrors()) {
                return HashUtils.convertToBase36(DateTime.getCurrentDateTime("yyMMddHHmmssSS"));
            }

            return String.valueOf(lgNextSupportReference);
        } catch (Exception e) {
            e.printStackTrace();
            return HashUtils.convertToBase36(DateTime.getCurrentDateTime("yyMMddHHmmssSS"));
        }
    }


    public static class SMSMSG {
        private String strMessage;
        private int intPriority;
        private String strCharge;
        private String strCategory;
        private MSGConstants.Sensitivity sensitivity;
        private String strRequestApplication;
        private String strSourceApplication;
        private String strSessionID;
        private String strCorrelationID;
        private MSGConstants.MSGMode theMode;

        public String getMessage() {
            return strMessage;
        }

        public void setMessage(String theMessage) {
            this.strMessage = theMessage;
        }

        public int getPriority() {
            return intPriority;
        }

        public void setPriority(int thePriority) {
            this.intPriority = thePriority;
        }

        public String getCharge() {
            return strCharge;
        }

        public void setCharge(String theCharge) {
            this.strCharge = theCharge;
        }

        public String getCategory() {
            return strCategory;
        }

        public void setCategory(String theCategory) {
            this.strCategory = theCategory;
        }

        public String getRequestApplication() {
            return strRequestApplication;
        }

        public void setRequestApplication(String theRequestApplication) {
            this.strRequestApplication = theRequestApplication;
        }

        public String getSourceApplication() {
            return strSourceApplication;
        }

        public void setSourceApplication(String theSourceApplication) {
            this.strSourceApplication = theSourceApplication;
        }

        public String getSessionID() {
            return strSessionID;
        }

        public void setSessionID(String theSessionID) {
            this.strSessionID = theSessionID;
        }

        public String getCorrelationID() {
            return strCorrelationID;
        }

        public void setCorrelationID(String theCorrelationID) {
            this.strCorrelationID = theCorrelationID;
        }

        public MSGConstants.MSGMode getMode() {
            return theMode;
        }

        public void setMode(MSGConstants.MSGMode theMode) {
            this.theMode = theMode;
        }

        public MSGConstants.Sensitivity getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(MSGConstants.Sensitivity theSensitivity) {
            this.sensitivity = theSensitivity;
        }

        @Override
        public String toString() {
            return "SMSMSG{" +
                   "strMessage='" + strMessage + '\'' +
                   ", intPriority=" + intPriority +
                   ", strCharge='" + strCharge + '\'' +
                   ", strCategory='" + strCategory + '\'' +
                   ", sensitivity=" + sensitivity +
                   ", strRequestApplication='" + strRequestApplication + '\'' +
                   ", strSourceApplication='" + strSourceApplication + '\'' +
                   ", strSessionID='" + strSessionID + '\'' +
                   ", strCorrelationID='" + strCorrelationID + '\'' +
                   ", theMode=" + theMode +
                   '}';
        }
    }

/*
    public static TransactionWrapper<FlexicoreHashMap> getUserAccounts(String theIdentifierType, String theIdentifier, String theDeviceIdentifierType, String theDeviceIdentifier) {

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            TransactionWrapper<FlexicoreHashMap> checkUserResultMapWrapper = checkUser(theIdentifierType, theIdentifier, theDeviceIdentifierType, theDeviceIdentifier);
            FlexicoreHashMap checkUserResultMap = checkUserResultMapWrapper.getSingleRecord();

            if (checkUserResultMapWrapper.hasErrors()) {
                return checkUserResultMapWrapper;
            }

            FlexicoreHashMap signatoryDetailsMap = checkUserResultMap.getValue("signatory_details");

            String strCustomerId = signatoryDetailsMap.getStringValue("identifier");

            TransactionWrapper<FlexicoreArrayList> customerAccountsListWrapper = ProfitsCBS.getCustomerAccountsTEMP(strCustomerId);
            if(customerAccountsListWrapper.getStatusCode() == HttpsURLConnection.HTTP_INTERNAL_ERROR){
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later or contact us for assistance."));

                return resultWrapper;
            }




            FlexicoreHashMap mobileBankingMap = checkUserResultMap.getValue("mobile_register_details");

            FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
            theUpdateLoginParamsMap.put("pin",  MobileBankingCryptography.hashPIN(signatoryDetailsMap.getStringValue("primary_mobile_number"), theNewPIN));
            theUpdateLoginParamsMap.put("pin_set_date", DateTime.getCurrentDateTime());
            theUpdateLoginParamsMap.put("pin_status", "ACTIVE");
            theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

            mobileBankingMap.copyFrom(theUpdateLoginParamsMap);

            String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingMap);
            theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

            TransactionWrapper<?> updateWrapper = Repository.update(
                    StringRefs.SENTINEL,
                    TBL_MOBILE_BANKING_REGISTER,
                    theUpdateLoginParamsMap,
                    new FilterPredicate("mobile_register_id = :mobile_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":mobile_register_id", mobileBankingMap.getStringValue("mobile_register_id"))
            );

            if (updateWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap()
                        .putValue("end_session", USSDAPIConstants.Condition.YES)
                        .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                        .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later or contact us for assistance."));
            } else {
                resultWrapper.setData(checkUserResultMap);
            }

            return resultWrapper;

        } catch (Exception e) {
            System.err.println("CBSAPI.setPIN(): " + e.getMessage());
            e.printStackTrace();
            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap()
                    .putValue("end_session", USSDAPIConstants.Condition.YES)
                    .putValue("cbs_api_return_val", USSDAPIConstants.StandardReturnVal.ERROR)
                    .putValue("display_message", "Sorry, an error occurred while processing your request. Please try again later or contact us for assistance."));
        }
        return resultWrapper;
    }
*/


    public static TransactionWrapper<FlexicoreHashMap> subscribeToMembersPortal(String theMobileNo, USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            FilterPredicate signatoryFilterPredicate = null;
            FlexicoreHashMap queryArguments = new FlexicoreHashMap();

            signatoryFilterPredicate = new FilterPredicate("primary_mobile_number = :primary_mobile_number");
            queryArguments.addQueryArgument(":primary_mobile_number", theMobileNo);

            FlexicoreHashMap customerRegisterDetailsMap = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER, signatoryFilterPredicate, queryArguments).getSingleRecord();

            String customerRegisterId = customerRegisterDetailsMap.getStringValue("customer_register_id");

            FlexicoreHashMap signatoryDetailsMap = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES, signatoryFilterPredicate, queryArguments).getSingleRecord();

            String signatoryId = signatoryDetailsMap.getStringValue("signatory_id");

            FlexicoreHashMap internetBankingMap = Repository.selectWhere(StringRefs.SENTINEL,
                    TBL_INTERNET_BANKING_REGISTER, new FilterPredicate("signatory_id = :signatory_id"),
                    new FlexicoreHashMap().addQueryArgument(":signatory_id", signatoryId)).getSingleRecord();

            if (internetBankingMap != null && !internetBankingMap.isEmpty()) {
                String status = internetBankingMap.getStringValue("status");

                if (status.equalsIgnoreCase("ACTIVE")) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Your members portal account is already ACTIVE. Please use your current password to proceed to login."));
                    return resultWrapper;
                }

                if (!status.equalsIgnoreCase("USER_DEACTIVATED")) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to activate account. Please reach out to the SACCO for assistance. ERR_IB100"));

                    return resultWrapper;
                }

                String workflowContent = "<?xml version=\"1.0\"?>\n" +
                        "<APP ACTION=\"EDIT\" APP_CODE=\"" + SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ACTIVATION + "\" TYPE=\"INTERNET_BANKING_SELF_ACTIVATION\">\n" +
                        "  <STATUS>ACTIVE</STATUS>\n" +
                        "</APP>";

                String auditTrailContent = "<CONTENT>\n" +
                        "     <APP APP_CODE=\"" + SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ACTIVATION + "\" INTERNET_REGISTER_ID=\"" + internetBankingMap.getStringValue("internet_register_id") + "\">\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"" + internetBankingMap.getStringValue("status") + "\">status</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"" + DateTime.getCurrentDateTime() + "\" PREV_VALUE=\"" + internetBankingMap.getStringValue("status_date") + "\">status_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"Self-Activation by Member\" PREV_VALUE=\"" + internetBankingMap.getStringValue("status_description") + "\">status_description</FIELD>\n" +
                        "     </APP>\n" +
                        "</CONTENT>\n";

                FlexicoreHashMap workflowMap = createWorkflow(SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ACTIVATION,
                        "Members Portal REACTIVATION for " + signatoryDetailsMap.getStringValue("full_name") + "-" + signatoryDetailsMap.getStringValue("identifier"),
                        "EDIT", workflowContent, auditTrailContent);

                if (workflowMap == null) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to activate account. An error occurred while processing your request. Please try again later."));
                    return resultWrapper;
                }
                String workflowId = workflowMap.getStringValue("workflow_id");

                FlexicoreHashMap updateMap = new FlexicoreHashMap()
                        .putValue("status", "ACTIVE")
                        .putValue("status_date", DateTime.getCurrentDateTime())
                        .putValue("status_description", "Self-Activation by Member")
                        .putValue("updated_workflow_id", workflowId)
                        .putValue("date_modified", DateTime.getCurrentDateTime());

                String previousUpdatedWorkflowIds = internetBankingMap.getStringValue("previous_updated_workflow_ids") + ",";
                previousUpdatedWorkflowIds = previousUpdatedWorkflowIds + updateMap.getValue("updated_workflow_id");
                updateMap.putValue("previous_updated_workflow_ids", previousUpdatedWorkflowIds);

                internetBankingMap.copyFrom(updateMap);

                String integrityHash = SMPCryptography.calculateIntegrityHash(internetBankingMap);
                updateMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> wrapper = Repository.update(StringRefs.SENTINEL, TBL_INTERNET_BANKING_REGISTER, updateMap,
                        new FilterPredicate("internet_register_id = :internet_register_id"),
                        new FlexicoreHashMap().addQueryArgument(":internet_register_id", internetBankingMap.getStringValue("internet_register_id")));

                if (wrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to activate account. An error occurred while processing your request. Please try again later."));
                    return resultWrapper;
                }

                String memberName = signatoryDetailsMap.getStringValue("full_name").split(" ")[0];
                if (memberName.trim().isEmpty()) {
                    memberName = "member";
                }
                memberName = memberName.trim();
                memberName = Misc.convertToTitleCase(memberName);

                String strOnboardingTemplate = "Dear " + memberName + ". Your Members Portal Account has been REACTIVATED. Please use your current password to login. In case this action was not initiated by you, kindly reach out to the SACCO for assistance.";

                String internetBankingMobileNumber = signatoryDetailsMap.getStringValue("primary_mobile_number");
                new USSDAPI().sendSMS(internetBankingMobileNumber, strOnboardingTemplate, MSGConstants.MSGMode.SAF, 203, "INTERNET_BANKING_ACCOUNT_REACTIVATION", theUSSDRequest);

                resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Your members portal account has been re-activated successfully."));
                return resultWrapper;
            }

            String workflowId;

            //CREATE WORKFLOW
            {
                String workflowContent = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                        "<APP ACTION=\"ADD\" APP_CODE=\"" + SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ONBOARDING + "\" TYPE=\"INTERNET_BANKING_SELF_ONBOARDING\">\n" +
                        "    <SIGNATORY_ID>" + signatoryId + "</SIGNATORY_ID>\n" +
                        "    <STATUS>ACTIVE</STATUS>\n" +
                        "    <STATUS_DATE/>\n" +
                        "    <STATUS_DESCRIPTION/>\n" +
                        "    <STATUS_START_DATE/>\n" +
                        "    <STATUS_END_DATE/>\n" +
                        "    <SOURCE_APPLICATION/>\n" +
                        "    <INTERNET_BANKING_APPLICATION_REF/>\n" +
                        "    <CHANNEL_DETAILS>\n" +
                        "        <ATTACHMENTS/>\n" +
                        "        <MANDATES>\n" +
                        "            <MANDATE CODE=\"BALANCE_ENQUIRY\" STATUS=\"ACTIVE\">Balance Enquiry</MANDATE>\n" +
                        "            <MANDATE CODE=\"ACCOUNT_STATEMENT\" STATUS=\"ACTIVE\">Account Statement</MANDATE>\n" +
                        "            <MANDATE CODE=\"DEPOSIT\" STATUS=\"ACTIVE\">Deposit</MANDATE>\n" +
                        "            <MANDATE CODE=\"WITHDRAWAL\" STATUS=\"ACTIVE\">Withdraw</MANDATE>\n" +
                        "            <MANDATE CODE=\"INTERNAL_TRANSFER\" STATUS=\"ACTIVE\">Internal Transfer</MANDATE>\n" +
                        "            <MANDATE CODE=\"BANK_TRANSFER\" STATUS=\"ACTIVE\">Bank Transfer</MANDATE>\n" +
                        "            <MANDATE CODE=\"UTILITY_PAYMENTS\" STATUS=\"ACTIVE\">Utility Payments</MANDATE>\n" +
                        "            <MANDATE CODE=\"BUY_AIRTIME\" STATUS=\"ACTIVE\">Buy Airtime</MANDATE>\n" +
                        "            <MANDATE CODE=\"LOAN_QUALIFICATION\" STATUS=\"ACTIVE\">Buy Airtime</MANDATE>\n" +
                        "            <MANDATE CODE=\"LOAN_APPLICATION\" STATUS=\"ACTIVE\">Loan Application</MANDATE>\n" +
                        "            <MANDATE CODE=\"LOAN_REPAYMENT\" STATUS=\"ACTIVE\">Loan Repayment</MANDATE>\n" +
                        "            <MANDATE CODE=\"LOAN_STATEMENT\" STATUS=\"ACTIVE\">Loan Statement</MANDATE>\n" +
                        "            <MANDATE CODE=\"LOAN_BALANCE\" STATUS=\"ACTIVE\">Loan Balance Enquiry</MANDATE>\n" +
                        "            <MANDATE CODE=\"LOAN_GUARANTORSHIP\" STATUS=\"ACTIVE\">Loan Guarantorship</MANDATE>\n" +
                        "            <MANDATE CODE=\"MEMBER_ONBOARDING\" STATUS=\"ACTIVE\">Member Onboarding</MANDATE>\n" +
                        "        </MANDATES>\n" +
                        "    </CHANNEL_DETAILS>\n" +
                        "</APP>";


                String auditTrailContent = "<CONTENT>\n" +
                        "     <APP APP_CODE=\"" + SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ONBOARDING + "\">\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">status</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"" + DateTime.getCurrentDateTime() + "\" PREV_VALUE=\"\">status_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"Self-Onboarding by " + signatoryDetailsMap.getStringValue("full_name") + " via USSD\" PREV_VALUE=\"\">status_description</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">status_start_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">status_end_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">source_application</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">internet_banking_application_reference</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"[{mandate_code=BALANCE_ENQUIRY, status=ACTIVE}, {mandate_code=ACCOUNT_STATEMENT, status=ACTIVE}, {mandate_code=DEPOSIT, status=ACTIVE}, {mandate_code=WITHDRAWAL, status=ACTIVE}, {mandate_code=INTERNAL_TRANSFER, status=ACTIVE}, {mandate_code=BANK_TRANSFER, status=ACTIVE}, {mandate_code=UTILITY_PAYMENTS, status=ACTIVE}, {mandate_code=BUY_AIRTIME, status=ACTIVE}, {mandate_code=LOAN_QUALIFICATION, status=ACTIVE}, {mandate_code=LOAN_APPLICATION, status=ACTIVE}, {mandate_code=LOAN_REPAYMENT, status=ACTIVE}, {mandate_code=LOAN_STATEMENT, status=ACTIVE}, {mandate_code=LOAN_BALANCE, status=ACTIVE}, {mandate_code=LOAN_GUARANTORSHIP, status=ACTIVE}, {mandate_code=MEMBER_ONBOARDING, status=ACTIVE}]\" PREV_VALUE=\"\">mandates</FIELD>\n" +
                        "     </APP>\n" +
                        "<APP APP_CODE=\"120\">\n" +
                        "          <FIELD NEW_VALUE=\"YES\" PREV_VALUE=\"NO\">has_internet_banking</FIELD>\n" +
                        "     </APP>\n" +
                        "<APP APP_CODE=\"150\">\n" +
                        "          <FIELD NEW_VALUE=\"YES\" PREV_VALUE=\"NO\">has_internet_banking</FIELD>\n" +
                        "     </APP>\n" +
                        "<APP APP_CODE=\"250\">\n" +
                        "          <FIELD NEW_VALUE=\"" + signatoryId + "\" PREV_VALUE=\"\">signatory_id</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">password</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"RESET\" PREV_VALUE=\"\">password_status</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"" + DateTime.getCurrentDateTime() + "\" PREV_VALUE=\"\">password_set_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">status</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">status_description</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"" + DateTime.getCurrentDateTime() + "\" PREV_VALUE=\"\">status_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">status_start_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">status_end_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ENABLED\" PREV_VALUE=\"\">activation_kyc</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">activation_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">source_application</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">internet_banking_application_reference</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"0\" PREV_VALUE=\"\">login_attempts</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"NONE\" PREV_VALUE=\"\">login_auth_action</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">login_auth_action_valid_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">login_auth_flag</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"0\" PREV_VALUE=\"\">otp_attempts</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"NONE\" PREV_VALUE=\"\">otp_auth_action</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">otp_auth_action_valid_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">otp_auth_flag</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">activation_kyc_set_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"NO\" PREV_VALUE=\"\">accepted_terms_and_conditions</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">date_accepted_terms_and_conditions</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"0\" PREV_VALUE=\"\">kyc_attempts</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"NONE\" PREV_VALUE=\"\">kyc_auth_action</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">kyc_auth_action_valid_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">kyc_auth_flag</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">last_login_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">otp</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"\" PREV_VALUE=\"\">otp_expiry_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"&lt;MFA_MODES&gt;&#10;    &lt;SKY_AUTHENTICATOR APPLICABLE=&quot;NO&quot; ENROLLED=&quot;NO&quot; ENROLLMENT_DATE=&quot;&quot;&gt;&#10;        &lt;MAPP_PUBKEY/&gt;&#10;        &lt;TOTP APPLICABLE=&quot;YES&quot; DEFAULT=&quot;YES&quot;&gt;&#10;            &lt;OTP_SK/&gt;&#10;        &lt;/TOTP&gt;&#10;        &lt;PUSH APPLICABLE=&quot;YES&quot; DEFAULT=&quot;YES&quot;/&gt;&#10;        &lt;QR_CODE APPLICABLE=&quot;YES&quot; DEFAULT=&quot;YES&quot;/&gt;&#10;    &lt;/SKY_AUTHENTICATOR&gt;&#10;    &lt;SMS APPLICABLE=&quot;YES&quot;/&gt;&#10;    &lt;EMAIL APPLICABLE=&quot;NO&quot;/&gt;&#10;&lt;/MFA_MODES&gt;\" PREV_VALUE=\"\">mfa_modes</FIELD>\n" +
                        "     </APP>\n" +
                        "<APP APP_CODE=\"255\">\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Balance Enquiry</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Account Statement</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Deposit</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Withdraw</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Internal Transfer</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Bank Transfer</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Utility Payments</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Buy Airtime</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Buy Airtime</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Loan Application</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Loan Repayment</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Loan Statement</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Loan Balance Enquiry</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Loan Guarantorship</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"\">Member Onboarding</FIELD>\n" +
                        "     </APP>\n" +
                        "</CONTENT>\n";

                FlexicoreHashMap workflowMap = createWorkflow(SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ONBOARDING,
                        "Members Portal Self-Onboarding for " + signatoryDetailsMap.getStringValue("full_name") + "-" + signatoryDetailsMap.getStringValue("identifier") + " via USSD",
                        "ADD", workflowContent, auditTrailContent);

                if (workflowMap == null) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to activate account. An error occurred while processing your request. Please try again later."));
                    return resultWrapper;
                }

                workflowId = workflowMap.getStringValue("workflow_id");
            }

            CommutationRepository theCommutationRepo = new CommutationRepository(StringRefs.SENTINEL);

            String userLoginPassword = new Passwords.PasswordBuilder()
                    .useDigits(true)
                    .usePunctuation(false)
                    .useLower(true)
                    .useUpper(true)
                    .build().generate(9)
                    .replace("O", "A")
                    .replace("o", "a")
                    .replace("I", "K")
                    .replace("1", "M")
                    .replace("0", "G")
                    .replace("i", "b")
                    .replace("l", "Y");


            String internetBankingPassword = userLoginPassword;
            String internetBankingMobileNumber = signatoryDetailsMap.getStringValue("primary_mobile_number");
            String internetBankingEmailAddress = signatoryDetailsMap.getStringValue("primary_email_address");
            String memberFullName = signatoryDetailsMap.getStringValue("full_name");

            String hashedPin = InternetBankingCryptography.hashPIN("", userLoginPassword);

            FlexicoreHashMap channelMap = new FlexicoreHashMap();

            String dateCreated = DateTime.getCurrentDateTime();
            channelMap.putValue("signatory_id", signatoryId);
            channelMap.putValue("status", "ACTIVE");
            channelMap.putValue("status_date", DateTime.getCurrentDateTime());
            channelMap.putValue("status_description", "Portal Self-Onboarding by Member");
            channelMap.putValue("source_application", "USSD");
            channelMap.putValue("internet_banking_application_reference", theUSSDRequest.getUSSDTraceID());
            channelMap.putValue("date_created", dateCreated);
            channelMap.putValue("date_modified", dateCreated);
            channelMap.putValue("created_workflow_id", workflowId);
            channelMap.putValue("updated_workflow_id", workflowId);
            channelMap.putValue("previous_updated_workflow_ids", workflowId);
            channelMap.putValue("password", hashedPin);
            channelMap.putValue("password_status", "RESET");
            channelMap.putValue("password_set_date", DateTime.getCurrentDateTime());
            channelMap.putValue("mfa_modes", """
                    <MFA_MODES>
                        <SKY_AUTHENTICATOR APPLICABLE="NO" ENROLLED="NO" ENROLLMENT_DATE="">
                            <MAPP_PUBKEY/>
                            <TOTP APPLICABLE="YES" DEFAULT="YES">
                                <OTP_SK/>
                            </TOTP>
                            <PUSH APPLICABLE="YES" DEFAULT="YES"/>
                            <QR_CODE APPLICABLE="YES" DEFAULT="YES"/>
                        </SKY_AUTHENTICATOR>
                        <SMS APPLICABLE="YES"/>
                        <EMAIL APPLICABLE="NO"/>
                    </MFA_MODES>""");

            String previousPasswords = "<PREVIOUS_PASSWORDS/>";

            try {
                Document docPrevPasswords = XmlUtils.parseXml(previousPasswords);

                Element elPassword = docPrevPasswords.createElement("PASSWORD");
                elPassword.setTextContent(hashedPin);
                docPrevPasswords.getDocumentElement().appendChild(elPassword);

                previousPasswords = XmlUtils.convertXmlDocToStr(docPrevPasswords);

            } catch (Exception e) {
                e.printStackTrace();
            }

            channelMap.putValue("previous_passwords", previousPasswords);

            // channelMap.putValue("sim_identifier_type", );

            TransactionWrapper<?> channelWrapper = theCommutationRepo.insertAutoIncremented(StringRefs.SENTINEL, TBL_INTERNET_BANKING_REGISTER, channelMap);
            if (channelWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(channelWrapper.getErrors());
                theCommutationRepo.rollback();
                return resultWrapper;
            }

            channelMap = channelWrapper.getSingleRecord();

            String integrityHash = SMPCryptography.calculateIntegrityHash(channelMap);

            theCommutationRepo.update(StringRefs.SENTINEL, TBL_INTERNET_BANKING_REGISTER,
                    new FlexicoreHashMap().addQueryArgument("integrity_hash", integrityHash),
                    new FilterPredicate("internet_register_id = :internet_register_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":internet_register_id", channelMap.getStringValue("internet_register_id")));

            String strInternetRegisterId = channelMap.getStringValue("internet_register_id");


            FlexicoreHashMap customerSignatoryMapping = new FlexicoreHashMap();
            customerSignatoryMapping.putValue("internet_register_id", strInternetRegisterId);
            customerSignatoryMapping.putValue("customer_register_id", customerRegisterId);
            //customerSignatoryMapping.putValue("attachments_xml", attachmentsXml);

            dateCreated = DateTime.getCurrentDateTime();
            customerSignatoryMapping.putValue("date_created", dateCreated);
            customerSignatoryMapping.putValue("date_modified", dateCreated);
            customerSignatoryMapping.putValue("created_workflow_id", workflowId);
            customerSignatoryMapping.putValue("updated_workflow_id", workflowId);
            customerSignatoryMapping.putValue("previous_updated_workflow_ids", workflowId);

            TransactionWrapper<?> mobileCustomerSignMappingWrapper = theCommutationRepo.insertAutoIncremented(StringRefs.SENTINEL, TBL_INTERNET_SIGNATORY_CUSTOMER_MAPPING, customerSignatoryMapping);
            if (mobileCustomerSignMappingWrapper.hasErrors()) {
                resultWrapper.setHasErrors(true);
                resultWrapper.addError(mobileCustomerSignMappingWrapper.getErrors());
                theCommutationRepo.rollback();
                return resultWrapper;
            }

            customerSignatoryMapping = mobileCustomerSignMappingWrapper.getSingleRecord();

            integrityHash = SMPCryptography.calculateIntegrityHash(customerSignatoryMapping);

            theCommutationRepo.update(StringRefs.SENTINEL, TBL_INTERNET_SIGNATORY_CUSTOMER_MAPPING,
                    new FlexicoreHashMap().addQueryArgument("integrity_hash", integrityHash),
                    new FilterPredicate("int_banking_signatory_mapping_id = :int_banking_signatory_mapping_id"),
                    new FlexicoreHashMap()
                            .addQueryArgument(":int_banking_signatory_mapping_id", customerSignatoryMapping.getStringValue("int_banking_signatory_mapping_id")));

            String strInternetBankingMandates = SystemParameters.getParameter("INTERNET_BANKING_MANDATES");

            Document document = XmlUtils.parseXml(strInternetBankingMandates);

            NodeList nodeList = document.getDocumentElement().getChildNodes();
            int length = nodeList.getLength();
            for (int index = 0; index < length; index++) {
                Node node = nodeList.item(index);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;

                Element element = (Element) node;

                String mandateCode = element.getAttribute("CODE");

                FlexicoreHashMap flexicoreHashMap = new FlexicoreHashMap();
                flexicoreHashMap.put("mandate_code", mandateCode);
                flexicoreHashMap.put("status", "ACTIVE");
                flexicoreHashMap.put("int_banking_signatory_mapping_id", customerSignatoryMapping.getStringValue("int_banking_signatory_mapping_id"));
                flexicoreHashMap.put("internet_register_id", customerSignatoryMapping.getStringValue("internet_register_id"));
                flexicoreHashMap.put("customer_register_id", customerSignatoryMapping.getStringValue("customer_register_id"));
                flexicoreHashMap.put("date_created", DateTime.getCurrentDateTime());
                flexicoreHashMap.put("date_modified", DateTime.getCurrentDateTime());
                flexicoreHashMap.put("created_workflow_id", workflowId);
                flexicoreHashMap.put("updated_workflow_id", workflowId);
                flexicoreHashMap.put("previous_updated_workflow_ids", workflowId);

                                    /*String strSigStatus = signatoryMap.getStringValue("status");
                                    if (!strSigStatus.equalsIgnoreCase("ACTIVE")) {
                                        String strMandateCode = flexicoreHashMap.getStringValue("mandate_code");

                                        if (strMandateCode.equalsIgnoreCase("145")
                                            || strMandateCode.equalsIgnoreCase("150")
                                        ) {
                                            flexicoreHashMap.putValue("status", "INACTIVE");
                                        }
                                    }*/


                TransactionWrapper<?> mobileMandatesMappingWrapper = theCommutationRepo.insertAutoIncremented(StringRefs.SENTINEL, SystemTables.TBL_INTERNET_SIGNATORY_MANDATES, flexicoreHashMap);
                if (mobileMandatesMappingWrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.addError(mobileMandatesMappingWrapper.getErrors());
                    theCommutationRepo.rollback();
                    return resultWrapper;
                }
            }

            {
                FlexicoreHashMap custUpdate = new FlexicoreHashMap();
                custUpdate.putValue("has_internet_banking", "YES");

                customerRegisterDetailsMap.copyFrom(custUpdate);

                integrityHash = SMPCryptography.calculateIntegrityHash(customerRegisterDetailsMap);

                custUpdate.putValue("integrity_hash", integrityHash);

                theCommutationRepo.update(StringRefs.SENTINEL, TBL_CUSTOMER_REGISTER,
                        custUpdate,
                        new FilterPredicate("customer_register_id = :customer_register_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":customer_register_id", customerRegisterId));


                FlexicoreHashMap sigUpdate = new FlexicoreHashMap();
                sigUpdate.putValue("has_internet_banking", "YES");

                signatoryDetailsMap.copyFrom(sigUpdate);

                integrityHash = SMPCryptography.calculateIntegrityHash(signatoryDetailsMap);

                sigUpdate.putValue("integrity_hash", integrityHash);

                theCommutationRepo.update(StringRefs.SENTINEL, TBL_CUSTOMER_REGISTER_SIGNATORIES,
                        sigUpdate,
                        new FilterPredicate("signatory_id = :signatory_id"),
                        new FlexicoreHashMap()
                                .addQueryArgument(":signatory_id", signatoryId));

            }

            theCommutationRepo.commit();

            //COMMUNICATION
            {
                String memberName = memberFullName.split(" ")[0];
                if (memberName.trim().isEmpty()) {
                    memberName = "member";
                }
                memberName = memberName.trim();
                memberName = Misc.convertToTitleCase(memberName);

                String strOnboardingTemplate = Messaging.getMessagingTemplate("SMS", "INTERNET_BANKING_ONBOARDING_MESSAGE");
                strOnboardingTemplate = strOnboardingTemplate.replace("[MEMBER_NAME]", memberName);
                strOnboardingTemplate = strOnboardingTemplate.replace("[PASSWORD]", internetBankingPassword);

                internetBankingMobileNumber = Misc.sanitizePhoneNumber(internetBankingMobileNumber);

                if (!internetBankingMobileNumber.equalsIgnoreCase("INVALID")) {
                    new USSDAPI().sendSMS(internetBankingMobileNumber, strOnboardingTemplate, MSGConstants.MSGMode.EXPRESS, 200, "INTERNET_BANKING_NEW_ACCOUNT", theUSSDRequest);
                } else {
                    System.err.println("CBSAPI.subscribeToMembersPortal() - Failed to send SMS to user with mobile number '" + signatoryDetailsMap.getStringValue("primary_mobile_number") + "'");
                }

                //TODO: Check if email is valid

                strOnboardingTemplate = Messaging.getMessagingTemplate("EMAIL", "INTERNET_BANKING_ONBOARDING_MESSAGE");
                strOnboardingTemplate = strOnboardingTemplate.replace("[MEMBER_NAME]", memberName);
                strOnboardingTemplate = strOnboardingTemplate.replace("[PASSWORD]", internetBankingPassword);

                EmailMessaging.sendEmail(internetBankingEmailAddress, "Members Portal Account", strOnboardingTemplate, "INTERNET_BANKING_NEW_ACCOUNT");
            }
        } catch (Exception e) {
            e.printStackTrace();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to activate account. An error occurred while processing your request. Please try again later."));
            return resultWrapper;
        }

        resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Your members portal account has been activated successfully. You will receive an SMS with your Login Password."));
        return resultWrapper;
    }


    public static TransactionWrapper<FlexicoreHashMap> deactivateMembersPortal(String theMobileNo, USSDRequest theUSSDRequest) {
        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            FilterPredicate signatoryFilterPredicate = null;
            FlexicoreHashMap queryArguments = new FlexicoreHashMap();

            signatoryFilterPredicate = new FilterPredicate("primary_mobile_number = :primary_mobile_number");
            queryArguments.addQueryArgument(":primary_mobile_number", theMobileNo);

            FlexicoreHashMap customerRegisterDetailsMap = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER, signatoryFilterPredicate, queryArguments).getSingleRecord();

            String customerRegisterId = customerRegisterDetailsMap.getStringValue("customer_register_id");

            FlexicoreHashMap signatoryDetailsMap = Repository.selectWhere(StringRefs.SENTINEL,
                    SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES, signatoryFilterPredicate, queryArguments).getSingleRecord();

            String signatoryId = signatoryDetailsMap.getStringValue("signatory_id");

            FlexicoreHashMap internetBankingMap = Repository.selectWhere(StringRefs.SENTINEL,
                    TBL_INTERNET_BANKING_REGISTER, new FilterPredicate("signatory_id = :signatory_id"),
                    new FlexicoreHashMap().addQueryArgument(":signatory_id", signatoryId)).getSingleRecord();

            if (internetBankingMap != null && !internetBankingMap.isEmpty()) {
                String status = internetBankingMap.getStringValue("status");

                if (!status.equalsIgnoreCase("ACTIVE")) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to deactivate account. Please reach out to the SACCO for assistance. ERR_IB100"));

                    return resultWrapper;
                }

                String workflowContent = "<?xml version=\"1.0\"?>\n" +
                        "<APP ACTION=\"EDIT\" APP_CODE=\"" + SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_DEACTIVATION + "\" TYPE=\"INTERNET_BANKING_SELF_DEACTIVATION\">\n" +
                        "  <STATUS>ACTIVE</STATUS>\n" +
                        "</APP>";

                String auditTrailContent = "<CONTENT>\n" +
                        "     <APP APP_CODE=\"" + SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_DEACTIVATION + "\" INTERNET_REGISTER_ID=\"" + internetBankingMap.getStringValue("internet_register_id") + "\">\n" +
                        "          <FIELD NEW_VALUE=\"ACTIVE\" PREV_VALUE=\"" + internetBankingMap.getStringValue("status") + "\">status</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"" + DateTime.getCurrentDateTime() + "\" PREV_VALUE=\"" + internetBankingMap.getStringValue("status_date") + "\">status_date</FIELD>\n" +
                        "          <FIELD NEW_VALUE=\"Self-Activation by Member\" PREV_VALUE=\"" + internetBankingMap.getStringValue("status_description") + "\">status_description</FIELD>\n" +
                        "     </APP>\n" +
                        "</CONTENT>\n";

                FlexicoreHashMap workflowMap = createWorkflow(SystemApplicationCodes.APP_CODE_INTERNET_BANKING_SELF_ACTIVATION,
                        "Members Portal DEACTIVATION for " + signatoryDetailsMap.getStringValue("full_name") + "-" + signatoryDetailsMap.getStringValue("identifier"),
                        "EDIT", workflowContent, auditTrailContent);

                if (workflowMap == null) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to deactivate account. An error occurred while processing your request. Please try again later."));
                    return resultWrapper;
                }
                String workflowId = workflowMap.getStringValue("workflow_id");

                FlexicoreHashMap updateMap = new FlexicoreHashMap()
                        .putValue("status", "USER_DEACTIVATED")
                        .putValue("status_date", DateTime.getCurrentDateTime())
                        .putValue("status_description", "Self-Deactivation by Member")
                        .putValue("updated_workflow_id", workflowId)
                        .putValue("date_modified", DateTime.getCurrentDateTime());

                String previousUpdatedWorkflowIds = internetBankingMap.getStringValue("previous_updated_workflow_ids") + ",";
                previousUpdatedWorkflowIds = previousUpdatedWorkflowIds + updateMap.getValue("updated_workflow_id");
                updateMap.putValue("previous_updated_workflow_ids", previousUpdatedWorkflowIds);

                internetBankingMap.copyFrom(updateMap);

                String integrityHash = SMPCryptography.calculateIntegrityHash(internetBankingMap);
                updateMap.putValue("integrity_hash", integrityHash);

                TransactionWrapper<?> wrapper = Repository.update(StringRefs.SENTINEL, TBL_INTERNET_BANKING_REGISTER, updateMap,
                        new FilterPredicate("internet_register_id = :internet_register_id"),
                        new FlexicoreHashMap().addQueryArgument(":internet_register_id", internetBankingMap.getStringValue("internet_register_id")));

                if (wrapper.hasErrors()) {
                    resultWrapper.setHasErrors(true);
                    resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to deactivate members portal account. An error occurred while processing your request. Please try again later."));
                    return resultWrapper;
                }

                String memberName = signatoryDetailsMap.getStringValue("full_name").split(" ")[0];
                if (memberName.trim().isEmpty()) {
                    memberName = "member";
                }
                memberName = memberName.trim();
                memberName = Misc.convertToTitleCase(memberName);

                String strOnboardingTemplate = "Dear " + memberName + ". Your Members Portal Account has been DISABLED. In case this action was not initiated by you, kindly reach out to the SACCO for assistance.";

                String internetBankingMobileNumber = signatoryDetailsMap.getStringValue("primary_mobile_number");
                new USSDAPI().sendSMS(internetBankingMobileNumber, strOnboardingTemplate, MSGConstants.MSGMode.SAF, 203, "INTERNET_BANKING_ACCOUNT_DEACTIVATION", theUSSDRequest);

                resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Your members portal account has been DISABLED successfully."));

                updateMap = new FlexicoreHashMap()
                        .putValue("token_status", "EXPIRED")
                        .putValue("date_modified", DateTime.getCurrentDateTime());

                Repository.update(StringRefs.SENTINEL, TBL_INTERNET_BANKING_ACCESS_TOKENS, updateMap, new FilterPredicate("internet_register_id = :internet_register_id"),
                        new FlexicoreHashMap().addQueryArgument(":internet_register_id", internetBankingMap.getStringValue("internet_register_id")));

                return resultWrapper;
            } else {
                resultWrapper.setHasErrors(true);
                resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Sorry, you are not subscribed to the members portal channel."));
                return resultWrapper;
            }
        } catch (Exception e) {
            e.printStackTrace();

            resultWrapper.setHasErrors(true);
            resultWrapper.setData(new FlexicoreHashMap().putValue("display_message", "Failed to deactivate account. An error occurred while processing your request. Please try again later."));
            return resultWrapper;
        }
    }


    private static FlexicoreHashMap createWorkflow(String applicationCode, String workflowTitle,
                                                   String applicationAction,
                                                   String workflowContent, String auditTrailContent) {

        FlexicoreHashMap userInternetBankingMap = Repository.selectWhere(StringRefs.SENTINEL,
                TBL_USER_ACCOUNTS,
                new FilterPredicate("username = :username"),
                new FlexicoreHashMap().addQueryArgument(":username", "root@smp")).getSingleRecord();

        if (userInternetBankingMap == null || userInternetBankingMap.isEmpty()) {
            System.err.println("FAILED TO CREATE WORKFLOW FOR APPLICATION: " + applicationCode + ". USER 'internetbanking@apstar' not found");
            return null;
        }


        FlexicoreHashMap workflow = new FlexicoreHashMap();
        workflow.putValue("application_code", applicationCode);
        workflow.putValue("approval_levels", 0);
        workflow.putValue("user_id", userInternetBankingMap.getStringValue("user_id"));
        workflow.putValue("approval_level", 0);
        workflow.putValue("approval_status", "APPROVED");
        workflow.putValue("date_created", DateTime.getCurrentDateTime());
        workflow.putValue("date_modified", DateTime.getCurrentDateTime());
        workflow.putValue("workflow_status", "COMPLETED");
        workflow.putValue("workflow_title", workflowTitle);
        workflow.putValue("application_action", applicationAction);
        workflow.putValue("workflow_content", workflowContent);
        workflow.putValue("audit_trail_content", auditTrailContent);
        workflow.putValue("workflow_type", "SINGLE");
        workflow.putValue("branch_code", userInternetBankingMap.getStringValue("branch_code"));
        workflow.putValue("branch", userInternetBankingMap.getStringValue("branch"));
        workflow.putValue("dictionary_version", "1.0");
        workflow.putValue("integrity_hash", "PENDING_CALCULATION");

        System.out.println("WORKFLOW CREATED: " + workflow);

        TransactionWrapper<FlexicoreHashMap> workflowWrapper = Repository.insertAutoIncremented(StringRefs.SENTINEL, TBL_WORKFLOWS, workflow);



        if (workflowWrapper.hasErrors()) {
            return null;
        }

        workflow = workflowWrapper.getSingleRecord();
        String workflowId = workflow.getValue("workflow_id").toString();

        String integrityHash = SMPCryptography.calculateIntegrityHash(workflow);

        Repository.update(StringRefs.SENTINEL,
                TBL_WORKFLOWS, new FlexicoreHashMap().addQueryArgument("integrity_hash", integrityHash),
                new FilterPredicate("workflow_id = :workflow_id"),
                new FlexicoreHashMap().addQueryArgument(":workflow_id", workflowId));

        return workflow;
    }
}
