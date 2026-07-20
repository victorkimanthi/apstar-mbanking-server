package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.authentication_manager.InternetBankingCryptography;
import ke.co.skyworld.smp.authentication_manager.MobileBankingCryptography;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.Misc;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.co.skyworld.smp.utility_items.memory.InMemoryCache;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.register.MemberRegisterResponse;
import ke.skyworld.lib.mbanking.register.RegisterConstants;
import ke.skyworld.lib.mbanking.register.RegisterProcessor;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.w3c.dom.Document;

import java.util.*;

import static ke.skyworld.mbanking.ussdapplication.AppConstants.strSACCOProductName;

public interface WithdrawalMenus {

    default USSDResponse displayMenu_Withdrawal(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Cash Withdrawal";


        try {

            {
                String strWithdrawalOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());
                if (strWithdrawalOption != null && !strWithdrawalOption.isBlank()) {
                    strHeader = strHeader + " - " + strWithdrawalOption;
                }
            }

            String strUSSDDataType = theUSSDRequest.getUSSDDataType();

            //The Flow:
            //Withdraw via M-Pesa -> Cash Withdrawal Select M-Pesa -> Select Source Account -> Select My Number -> Input Amount -> Input PIN -> Confirm Transactions
            switch (theParam) {
                case "MENU": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.WITHDRAWAL_VIA_MPESA);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;

                    } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.WITHDRAWAL)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    theUSSDResponse = getWithdrawalOptionMenu(theUSSDRequest, strHeader + "\nSelect Withdrawal Option");

                    break;
                }

                case "OPTION": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.WITHDRAWAL_VIA_MPESA);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;

                    } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.WITHDRAWAL)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    String strWithdrawalOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());

                    switch (strWithdrawalOption) {
                        case "M-PESA", "AIRTEL_MONEY", "T-KASH" -> {
                            String strCustomerHeader = strHeader + " \nSelect member\n";
                            String strAccountsHeader = strHeader + " \nSelect account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);

                        }

                        case "ATM" -> {
                            String strResponse = strHeader + "\nSelect ATM Option:";
                            theUSSDResponse = getWithdrawalATMOptionMenu(theUSSDRequest, strResponse);

                        }

                        case "AGENT" -> {
                            String strResponse = strHeader + "\nSelect Agent Option:";
                            theUSSDResponse = getWithdrawalAgentOptionMenu(theUSSDRequest, strResponse);
                        }

                        default ->
                                theUSSDResponse = getWithdrawalOptionMenu(theUSSDRequest, strHeader + "\n{Invalid Option}\nSelect Withdrawal Option");
                    }

                    break;
                }

                case "ATM_OPTION": {
                    String strATMOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ATM_OPTION.name());

                    switch (strATMOption) {
                        case "ORGANIZATION_ATM": {

                            String strCustomerHeader = strHeader + " \nSelect member\n";
                            String strAccountsHeader = strHeader + " (" + strSACCOProductName + ")\nSelect account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);

                            break;
                        }
                        case "COOPBANK_ATM": {

                            String strCustomerHeader = strHeader + " \nSelect member\n";
                            String strAccountsHeader = strHeader + " (CoopBank)\nSelect account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);

                            break;
                        }
                        default: {
                            String strResponse = strHeader + "\n{Invalid Option}\nSelect ATM Option:";
                            theUSSDResponse = getWithdrawalATMOptionMenu(theUSSDRequest, strResponse);
                            break;
                        }
                    }
                    break;
                }

                case "AGENT_OPTION": {
                    String strAgentOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_AGENT_OPTION.name());

                    switch (strAgentOption) {
                        case "ORGANIZATION_AGENT": {

                            String strCustomerHeader = strHeader + " \nSelect member\n";
                            String strAccountsHeader = strHeader + " (" + strSACCOProductName + ")\nSelect account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);

                            break;
                        }
                        case "COOPBANK_AGENT": {

                            String strCustomerHeader = strHeader + " \nSelect member\n";
                            String strAccountsHeader = strHeader + " (CoopBank)\nSelect account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);

                            break;
                        }
                        default: {
                            String strResponse = strHeader + "\n{Invalid Option}\nSelect Agent Option:";
                            theUSSDResponse = getWithdrawalAgentOptionMenu(theUSSDRequest, strResponse);
                            break;
                        }
                    }

                    break;
                }

                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect account\n";

                        theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                AppConstants.USSDDataType.WITHDRAWAL_END);

                    } else {
                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                USSDAPIConstants.AccountType.WITHDRAWABLE,
                                AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                AppConstants.USSDDataType.WITHDRAWAL_END);
                    }

                    break;
                }

                case "ACCOUNT": {


                    String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
                    HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
                    String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                    String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                    System.out.println("WithdrawalMenus.displayMenu_Withdrawal() - strAccount " + strSourceAccountNo);
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER.name());

                    if (strSourceAccountNo.length() > 0) {
                        MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo, RegisterConstants.MemberRegisterType.BLACKLIST);
                        System.out.println("WithdrawalMenus.displayMenu_Withdrawal() - memberRegisterResponse " + memberRegisterResponse.getResponseType());
                        System.out.println("***********************************************************************************************");
                        if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                            String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);

                        } else {

                            String strWithdrawalOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());

                            switch (strWithdrawalOption) {
                                case "M-PESA", "AIRTEL_MONEY", "T-KASH" -> {
                                    APIUtils.WithdrawalChannel withdrawalChannel = APIUtils.getWithdrawalChannel("M-PESA");
                                    if (withdrawalChannel != null) {
                                        if (withdrawalChannel.hasWithdrawalToOtherNumberEnabled()) {
                                            String strFullHeader = strHeader + "\nSelect withdrawal option\n";
                                            theUSSDResponse = getWithdrawalToOptionMenu(theUSSDRequest, strFullHeader);
                                        } else {

                                            //check available balance
                                            String strAccountDetails1 = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
                                            HashMap<String, String> hmAccountDetails1 = Utils.toHashMap(strAccountDetails1);
                                            String strSourceAccountAvailableBalance = hmAccountDetails1.get("ac_bal");
                                            String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());

                                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.MPESA_WITHDRAWAL.getValue(),
                                                    Double.parseDouble(strSourceAccountAvailableBalance.replace(",", "")));
                                            String strTotalCharges = chargesWrapper.getSingleRecord().getStringValue("charge_amount");

                                            double dbWithdrawalAmount = Double.parseDouble(strSourceAccountAvailableBalance) - Double.parseDouble(strTotalCharges.replace(",", ""));
                                            //format the amount
                                            String strformattedWithdrawalAmount = Utils.formatDouble(dbWithdrawalAmount, "#,##0.00");

                                            String strResponse = strHeader + "\nWithdrawable Amount: KES " + strformattedWithdrawalAmount + "\n\nEnter amount:";
                                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                        }
                                    } else {
                                        //check available balance
                                        String strAccountDetails1 = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
                                        HashMap<String, String> hmAccountDetails1 = Utils.toHashMap(strAccountDetails1);
                                        String strSourceAccountAvailableBalance = hmAccountDetails1.get("ac_bal");
                                        String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());

                                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.MPESA_WITHDRAWAL.getValue(),
                                                Double.parseDouble(strSourceAccountAvailableBalance.replace(",", "")));
                                        String strTotalCharges = chargesWrapper.getSingleRecord().getStringValue("charge_amount");

                                        double dbWithdrawalAmount = Double.parseDouble(strSourceAccountAvailableBalance) - Double.parseDouble(strTotalCharges.replace(",", ""));
                                        //format the amount
                                        String strformattedWithdrawalAmount = Utils.formatDouble(dbWithdrawalAmount, "#,##0.00");

                                        String strResponse = strHeader + "\nWithdrawable Amount: KES " + strformattedWithdrawalAmount + "\nEnter amount:";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                    }

                                }
                                case "ATM", "AGENT" -> {
                                    String strResponse = strHeader + "\nEnter amount:";
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                }
                            }
                        }


                        //USE ONLY WHEN WITHDRAWAL TO OTHER NUMBER IS ENABLED
                        /*

                         */

                        //USE TO SKIP WHEN WITHDRAWAL TO OTHER NUMBER IS DISABLED
                        /*String strResponse = strHeader + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
*/
                    } else {
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";

                        String strCustomerHeader = strHeader + " \nSelect member\n";

                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                    strCustomerIdentifier,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);
                        } else {
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.WITHDRAWAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT,
                                    AppConstants.USSDDataType.WITHDRAWAL_END);
                        }
                    }
                    break;
                }
                case "TO_OPTION": {
                    String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());
                    String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO_OPTION.name());
                    if (strToOption.equalsIgnoreCase("MY_NUMBER")) {

                        //check available balance
                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.MPESA_WITHDRAWAL.getValue(),
                                Double.parseDouble(strSourceAccountAvailableBalance));
                        String strTotalCharges = chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        double dbWithdrawalAmount = Double.parseDouble(strSourceAccountAvailableBalance) - Double.parseDouble(strTotalCharges);
                        //format the amount
                        String strformattedWithdrawalAmount = Utils.formatDouble(dbWithdrawalAmount, "#,##0.00");

                        String strResponse = strHeader + "\nWithdrawable Amount: KES " + strformattedWithdrawalAmount + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                        String strFullHeader = strHeader + "\nEnter Other Mobile No.\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strFullHeader, AppConstants.USSDDataType.WITHDRAWAL_TO, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strFullHeader = strHeader + "\n{Select a valid menu}\nSelect withdrawal option\n";
                        theUSSDResponse = getWithdrawalToOptionMenu(theUSSDRequest, strFullHeader);
                    }
                    break;
                }
                case "TO": {
                    String strOtherMobileNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO.name());
                    strOtherMobileNo = AppUtils.sanitizePhoneNumber(strOtherMobileNo);

                    if (!strOtherMobileNo.equalsIgnoreCase("INVALID MOBILE NUMBER") /*|| !strOtherMobileNo.matches("^254((7)[0-2][0-9])|(74[0-3])|(74[5-6])|(748)|(75[7-9])|(76[8-9])|(79[0-9]))[0-9]{6}$")*/) {
                        String strResponse = strHeader + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strFullHeader = strHeader + "\n{Enter a valid mobile number}\nEnter Other Mobile No.\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strFullHeader, AppConstants.USSDDataType.WITHDRAWAL_TO, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_AMOUNT.name());
                    String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
                    HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                    String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                    String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                    String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                    String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                    String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");


                    double dblAvailableBalance = 0;
                    try {
                        dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    String strOrgParametersXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                    Document document = XmlUtils.parseXml(strOrgParametersXML);

                    double dblMinimumAmount = Double.parseDouble(USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.CASH_WITHDRAWAL).getMinimum());
                    double dblMaximumAmount = Double.parseDouble(USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.CASH_WITHDRAWAL).getMaximum());

                    double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "MPESA_WITHDRAWAL");

                    dblMaximumAmount = Math.min(dblMaximumAmount, dblDailyLimitRemainingAmount);

                    String strResponse = strHeader + "\nEnter your PIN:";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (Double.parseDouble(strAmount) < dblMinimumAmount) {
                        strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(dblMinimumAmount, "#,##0.00") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (dblDailyLimitRemainingAmount <= 0) {
                        strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                    } else if (Double.parseDouble(strAmount) > dblMaximumAmount) {
                        strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(dblMaximumAmount, "#,##0.00") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                        strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {
                        theUSSDResponse = getWithdrawalConfirmation(theUSSDRequest, strHeader);
                    } else {
                        String strResponse = strHeader + "\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.WITHDRAWAL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {

                            String strWithdrawalOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());

                            switch (strWithdrawalOption) {
                                case "M-PESA", "AIRTEL_MONEY", "T-KASH" -> {
                                    TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = theUSSDAPI.mobileMoneyWithdrawal(theUSSDRequest);
                                    FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                                    if (moneyOutWrapper.hasErrors()) {
                                        String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                                        strErrorMessage += moneyOutMap.getStringValue("display_message");
                                        System.err.println("WithdrawalMenus.displayMenu_Withdrawal() - Response " + strErrorMessage);
                                    }


                                    String strResponse = moneyOutMap.getStringValue("display_message");

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);

                                }

                                case "ATM" -> {


                                    String strResponse = "An ATM Withdrawal Code has been sent to you via SMS. Complete your ATM Withdrawal Request within 3 minutes.";

                                    String strOTP = generateRandomOTP(6);
                                    String strPlainOTP = strOTP;

                                    Date expirationDate = DateTime.add(180, DateTime.getPeriodUnit("SECONDS"));
                                    String strExpirationDate = DateTime.convertDateToDateString(expirationDate, "dd-MM-yyyy HH:mm:ss");

                                    strOTP = MobileBankingCryptography.hashPIN("", strOTP);

                                    InMemoryCache.store(theUSSDRequest.getUSSDMobileNo() + "-ATM-WITHDRAWAL", strOTP, 180 * 1000L);

                                    String strSMS = "Dear member, your ATM Withdrawal Code is " + strPlainOTP + ". Generated at " + DateTime.getCurrentDateTime("dd-MM-yyyy HH:mm:ss")
                                            + ". Valid till " + strExpirationDate;

                                    theUSSDAPI.sendSMS(String.valueOf(theUSSDRequest.getUSSDMobileNo()), strSMS, MSGConstants.MSGMode.EXPRESS, 200, "ATM_WITHDRAWAL_CODE", theUSSDRequest);

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);

                                }

                                case "AGENT" -> {
                                    String strResponse = "An Agent Withdrawal Code has been sent to you via SMS. Complete your Agent Withdrawal Request within 3 minutes.";

                                    String strOTP = generateRandomOTP(6);
                                    String strPlainOTP = strOTP;

                                    Date expirationDate = DateTime.add(180, DateTime.getPeriodUnit("SECONDS"));
                                    String strExpirationDate = DateTime.convertDateToDateString(expirationDate, "dd-MM-yyyy HH:mm:ss");

                                    strOTP = MobileBankingCryptography.hashPIN("", strOTP);

                                    InMemoryCache.store(theUSSDRequest.getUSSDMobileNo() + "-AGENT-WITHDRAWAL", strOTP, 180 * 1000L);

                                    String strSMS = "Dear member, your Agent Withdrawal Code is " + strPlainOTP + ". Generated at " + DateTime.getCurrentDateTime("dd-MM-yyyy HH:mm:ss")
                                            + ". Valid till " + strExpirationDate;

                                    theUSSDAPI.sendSMS(String.valueOf(theUSSDRequest.getUSSDMobileNo()), strSMS, MSGConstants.MSGMode.EXPRESS, 200, "AGENT_WITHDRAWAL_CODE", theUSSDRequest);

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);

                                }
                            }
                            break;
                        }
                        case "NO": {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Cash Withdrawal request NOT COMPLETED.\n";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {

                            theUSSDResponse = getWithdrawalConfirmation(theUSSDRequest, strHeader + "\n{Invalid Option}\n");

                            break;
                        }
                    }

                    break;
                }

                default: {
                    System.err.println("theAppMenus.displayMenu_Withdrawal() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Withdrawal() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getWithdrawalOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            /*LinkedList<APIUtils.WithdrawalChannel> lsWithdrawalChannels = APIUtils.getActiveWithdrawalChannels(MBankingConstants.ApplicationType.USSD);

            for (int i = 0; i < lsWithdrawalChannels.size(); i++) {
                String strOptionMenu = String.valueOf(i + 1);
                String strName = lsWithdrawalChannels.get(i).getName();
                String strLabel = lsWithdrawalChannels.get(i).getLabel();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strName, strOptionMenu + ": " + strLabel);
            }*/

            //"M-PESA", "AIRTEL_MONEY", "T-KASH", "ATM", "AGENT"
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "M-PESA", "1: M-PESA");
            /*USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "AIRTEL-MONEY", "2: Airtel Money");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "T-KASH", "3: T-Kash");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "4", "ATM", "4: Cardless ATM");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "5", "AGENT", "5: Agent");*/
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getWithdrawalToOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getWithdrawalATMOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ORGANIZATION_ATM", "1: " + strSACCOProductName + " ATM");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "COOPBANK_ATM", "2: CoopBank ATM");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_ATM_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getWithdrawalATMOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getWithdrawalAgentOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ORGANIZATION_AGENT", "1: " + strSACCOProductName + " Agent");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "COOPBANK_AGENT", "2: CoopBank Agent");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_AGENT_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getWithdrawalATMOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


    default USSDResponse getWithdrawalConfirmation(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_AMOUNT.name());
            String strOrigiAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_AMOUNT.name());
            //String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
            //String strResponse =  "Confirm Cash Withdrawal\nAmount: KES "+strAmount+"\nAccount: " + strAccount + "\n";

            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());
            String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO_OPTION.name());

                        /*if(strToOption != null) {
                            if ( strOption.equalsIgnoreCase("M-PESA")  ){
                                if(strToOption.equalsIgnoreCase("OTHER_NUMBER")){
                                    strMobileNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO.name());
                                }
                            }
                        }*/

            APIUtils.WithdrawalChannel withdrawalChannel = APIUtils.getWithdrawalChannel("M-PESA");
            if (withdrawalChannel != null) {
                if (withdrawalChannel.hasWithdrawalToOtherNumberEnabled()) {
                    if (strToOption != null) {
                        if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                            strMobileNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_TO.name());
                        }
                    }
                }
            }

            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
            //String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                      /*  String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

            strMobileNo = APIUtils.sanitizePhoneNumber(strMobileNo);

            double dblOrigiAmount = Double.parseDouble(strOrigiAmount);

            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.MPESA_WITHDRAWAL.getValue(),
                    Double.parseDouble(strAmount));

            String strCharge = "";
            if (chargesWrapper.hasErrors()) {
                strCharge = "";
            } else {
                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
            }

            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

            String strResponse = "Confirm " + theHeader + "\nFrom: " + strSourceAccountLabel + "-" + strSourceAccountNo + "\nTo: " + strMobileNo + "\nAmount: KES " + strAmount + strCharge;

            String strWithdrawalOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_OPTION.name());
            switch (strWithdrawalOption) {
                case "ATM" -> {

                    String strATMOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ATM_OPTION.name());

                    if (strATMOption.equals("ORGANIZATION_ATM")) {
                        theHeader = theHeader + " (" + strSACCOProductName + ")";
                    } else {
                        theHeader = theHeader + " (CoopBank)";
                    }
                    strResponse = "Confirm " + theHeader + "\nFrom: " + strSourceAccountLabel + "\nAmount: KES " + strAmount + strCharge;
                }
                case "AGENT" -> {

                    String strAgentOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_AGENT_OPTION.name());

                    if (strAgentOption.equals("ORGANIZATION_AGENT")) {
                        theHeader = theHeader + " (" + strSACCOProductName + ")";
                    } else {
                        theHeader = theHeader + " (CoopBank)";
                    }

                    strResponse = "Confirm " + theHeader + "\nFrom: " + strSourceAccountLabel + "\nAmount: KES " + strAmount + strCharge;

                }
                default -> {
                    strResponse = "Confirm " + theHeader + "\nFrom: " + strSourceAccountLabel  + "\nTo: " + strMobileNo + "\nAmount: KES " + strAmount + strCharge;
                }
            }

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.getWithdrawalConfirmation() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


    default USSDResponse getWithdrawalToOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "MY_NUMBER", "1: MY Phone Number");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "OTHER_NUMBER", "2: OTHER Phone Number");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_TO_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getWithdrawalOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    public static String generateRandomOTP(int theLength) {
        StringBuilder strRandom = new StringBuilder();

        try {
            char[] chars = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
            Random random = new Random();

            for (int i = 0; i < theLength; ++i) {
                strRandom.append(chars[random.nextInt(chars.length)]);
            }

            return strRandom.toString();
        } catch (Exception var5) {
            System.out.println(var5.getMessage());
            return strRandom.toString();
        }
    }

}


