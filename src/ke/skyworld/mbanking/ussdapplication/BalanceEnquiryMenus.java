package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.skyworld.lib.mbanking.ussd.*;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import java.util.ArrayList;
import java.util.HashMap;

public interface BalanceEnquiryMenus {

  /*  default USSDResponse displayMenu_BalanceEnquiry(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Balance Enquiry";

        try {
            switch (theParam) {
                case "MENU": {
                    String strCustomerHeader = strHeader + " \nSelect member\n";
                    String strAccountsHeader = strHeader + " \nSelect account\n";

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER,
                            USSDAPIConstants.AccountType.ALL,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);

                    break;
                }
                case "CUSTOMER": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);

                    } else {
                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER,
                                USSDAPIConstants.AccountType.ALL,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                    }

                    break;
                }

                case "ACCOUNT": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER.name());
                    String strAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());

                    if (strAccountNumber.length() > 0) {
                        String strResponse = strHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {

                        String strCustomerHeader = strHeader + " \n{Select member}\n";
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";
                        if(strCustomerIdentifier != null && strCustomerIdentifier.length() > 0){
                            theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                        }else{

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER,
                                    USSDAPIConstants.AccountType.ALL,
                                    AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT,
                                    AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                        }
                    }

                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());
                        HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strAccountDetails);
                        String strAccountName = hmToAccountDetails.get("ac_name");
                        String strAccountLabel = hmToAccountDetails.get("ac_label");
                        String strAccountNumber = hmToAccountDetails.get("ac_no");
                        String strFullName = hmToAccountDetails.get("cust_name");
                        String strCustomerIdentifier = hmToAccountDetails.get("cust_id");

                        String strResponse = "Dear member, your Balance Enquiry request for Account " + strAccountNumber + " has been received successfully. Please wait shortly as it's being processed.\n";

                        USSDAPI finalTheUSSDAPI = theUSSDAPI;
                        Thread worker = new Thread(() -> {
                            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = finalTheUSSDAPI.accountBalanceEnquiry(theUSSDRequest);
                            if (accountBalanceEnquiryWrapper.hasErrors()) {
                                FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();
                                String strErrorMessage = accountBalanceEnquiryMap.getValue("cbs_api_return_val").toString()+"\n";
                                strErrorMessage += accountBalanceEnquiryMap.getStringValue("display_message");
                                System.err.println("BalanceEnquiryMenus.displayMenu_BalanceEnquiry() - Response " + strErrorMessage);
                            }
                        });
                        worker.start();

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = "Balance Enquiry\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_BalanceEnquiryMenus() UNKNOWN PARAM ERROR : theParam = " + theParam);
                    String strResponse = "Balance Enquiry\n{Sorry, an error has occurred while processing your request}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }

                */
    /*default: {

                    String strAccountType = null;

                    AppConstants.USSDDataType ussdDataType = getBalanceEnquiryCallerMenu(theUSSDRequest.getUSSDData().toString());

                    switch (ussdDataType) {
                        case MY_ACCOUNT_BALANCE_ACCOUNTS_LIST: {
                            strAccountType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNTS_LIST.name());
                            break;
                        }
                        case LOAN_MENU: {
                            strAccountType = USSDAPIConstants.AccountType.LOAN.getValue();
                            break;
                        }
                    }

                    if (strAccountType != null) {
                        switch (strAccountType) {
                            case "FOSA": {
                                strHeader = "Savings Accounts Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.FOSA;
                                break;
                            }
                            case "BOSA": {
                                strHeader = "Shares, Deposits and Benevolent Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.BOSA;
                                break;
                            }
                            case "LOAN": {
                                strHeader = "Loans Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.LOAN;
                                break;
                            }
                            case "ALL": {
                                strHeader = "All Accounts Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.ALL;
                                break;
                            }
                        }
                    }
                    theUSSDResponse = displayMenu_BalanceEnquiryMenus(theUSSDRequest, theParam, accountType, strHeader);
                    break;
                }*//*
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_BalanceEnquiry() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }
*/

    default USSDResponse displayMenu_BalanceEnquiryMenu(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BALANCE_ENQUIRY);
            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Balance Enquiry\n" + getServiceStatusDetails.getStringValue("display_message"));
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;

            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BALANCE_ENQUIRY)) {

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Balance Enquiry\n" + AppConstants.strServiceUnavailable);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA_ACCOUNTS", "1: FOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA_ACCOUNTS", "2: BOSA Accounts");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "BUSINESS_ACCOUNTS", "3: Business Accounts");
//            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "PAY_LOAN", "3: Pay Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_BalanceEnquiryMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_BalanceEnquiry(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            /*switch (theParam) {
                case "MENU" -> {
                    String strHeader = "Balance Enquiry";
                    theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                }

                case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS" -> {
                    String strHeader = "Balance Enquiry";
                    theUSSDResponse = displayMenu_Accounts(theUSSDRequest, theParam, strHeader);
                }

                default -> {
                    String strHeader = "Balance Enquiry\n{Select a valid menu}";
                    theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                }
            }
*/


            System.out.println("\n\nTHE PARAMS: " + theParam + "\n\n");

            String strUSSDDataType = theUSSDRequest.getUSSDDataType();

            System.out.println("\n\nTHE TYPE: " + theUSSDRequest.getUSSDDataType() + "\n");

            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MY_ACCOUNT_MENU.getValue())
                || strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.getValue())) {
                String strHeader = "Balance Enquiry";
                theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
            } else { //LOAN_MENU

                String strAccountBalance_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_MENU.name());

                switch (strAccountBalance_MENU) {
                    case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS" -> {
                        String strHeader = "Balance Enquiry";
                        theUSSDResponse = displayMenu_Accounts(theUSSDRequest, theParam, strHeader);
                    }

                    default -> {
                        String strHeader = "Balance Enquiry\n{Select a valid menu}";
                        theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_BalanceEnquiry() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_AccountsALL(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            switch (theParam) {
                case "MENU": {

                    String strCustomerHeader = theHeader + " \nSelect member\n";
                    String strAccountsHeader = theHeader + " \nSelect account\n";

                    theUSSDResponse = getBankAccountsBal(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);

                    break;
                }
                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsBalWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);

                    } else {

                        String strCustomerHeader = theHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsBal(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER.name());

                    if (strAccount.length() > 0) {
                        String strResponse = theHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {

                        String strCustomerHeader = theHeader + " \nSelect member\n";
                        String strAccountsHeader = theHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = getBankAccountsBalWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);
                        } else {
                            theUSSDResponse = getBankAccountsBal(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                        }
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {
                        String strAccountType = "ALL";

                        String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_MENU.name());
                        switch (strAccountTypes) {
                            case "FOSA_ACCOUNTS": {
                                strAccountType = "FOSA";
                                break;
                            }
                            case "BOSA_ACCOUNTS": {
                                strAccountType = "BOSA";
                                break;
                            }
                            case "BUSINESS_ACCOUNTS": {
                                strAccountType = "BUSINESS";
                                break;
                            }

                        }

                        String strResponse = "";
                        TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = theUSSDAPI.accountsBalanceEnquiry(theUSSDRequest, strAccountType);
                        FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();

                        if (accountBalanceEnquiryWrapper.hasErrors()) {
                            String strErrorMessage = accountBalanceEnquiryMap.getValue("cbs_api_return_val").toString() + "\n";
                            strErrorMessage += accountBalanceEnquiryMap.getStringValue("display_message");
                            strResponse = accountBalanceEnquiryMap.getStringValue("display_message");

                            //System.err.println("BalanceEnquiryMenus.displayMenu_BalanceEnquiry() - Response " + strErrorMessage);
                        } else {
                            CBSAPI.SMSMSG cbsMSG = accountBalanceEnquiryMap.getValue("msg_object");
                            String strMSG = cbsMSG.getMessage();

                            if (strMSG.length() > 180) {
                                strMSG = strMSG.substring(0, 180);
                            }

                            strResponse = strMSG;
                        }

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = "Balance Enquiry\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Accounts() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_Accounts(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            switch (theParam) {
                case "MENU": {

                    String strCustomerHeader = theHeader + " \nSelect member\n";
                    String strAccountsHeader = theHeader + " \nSelect account\n";

                    theUSSDResponse = getBankAccountsBal(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);

                    break;
                }
                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER.name());

                    if (!strCustomerIdentifier.isBlank()) {
                        String strAccountsHeader = theHeader + " \nSelect account\n";
                        theUSSDResponse = getBankAccountsBalWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);

                    } else {

                        String strCustomerHeader = theHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsBal(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER.name());

                    if(strAccount.length() > 0){

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());
                        HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strAccountDetails);
                        String strAccountName = hmToAccountDetails.get("ac_name");
                        String strAccountLabel = hmToAccountDetails.get("ac_label");



                        String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());
                        String strAmount ="0.0";

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.SINGLE_ACCOUNT_BALANCE_ENQUIRY.getValue(),
                                Double.parseDouble(strAmount));

                        String strCharge = "";
                        if (chargesWrapper.hasErrors()) {
                            strCharge = "";
                        } else {
                            strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        }

                         theHeader = "Confirm " + strAccountLabel + " Balance Enquiry" + strCharge + "\n";


                        String strResponse = theHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    }else{

                        String strCustomerHeader = theHeader + " \nSelect member\n";
                        String strAccountsHeader = theHeader + " \n{Select a valid account}\n";
                        if(strCustomerIdentifier != null && strCustomerIdentifier.length() > 0){
                            theUSSDResponse = getBankAccountsBalWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);
                        }else{
                            theUSSDResponse = getBankAccountsBal(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                        }
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT.name());
                        HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strAccountDetails);
                        String strAccountName = hmToAccountDetails.get("ac_name");
                        String strAccountLabel = hmToAccountDetails.get("ac_label");
                        String strAccountNumber = hmToAccountDetails.get("ac_no");

                        String strResponse = "Dear member, your Balance Enquiry for " + strAccountName + " has been Received. Please wait shortly as it's being processed.\n";

                        USSDAPI finalTheUSSDAPI = theUSSDAPI;
                        Thread worker = new Thread(() -> {
                            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = finalTheUSSDAPI.accountBalanceEnquiry(theUSSDRequest);
                            if (accountBalanceEnquiryWrapper.hasErrors()) {
                                FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();
                                String strErrorMessage = accountBalanceEnquiryMap.getValue("cbs_api_return_val").toString()+"\n";
                                strErrorMessage += accountBalanceEnquiryMap.getStringValue("display_message");
                                System.err.println("BalanceEnquiryMenus.displayMenu_BalanceEnquiry() - Response " + strErrorMessage);
                            }
                        });
                        worker.start();

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = "Balance Enquiry\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

            }

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_Accounts() ERROR : " + e.getMessage());
        }
        finally{
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsBal(USSDRequest theUSSDRequest, String theParam, String strCustomerHeader, String strAccountsHeader) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER,
                            USSDAPIConstants.AccountType.FOSA,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER,
                            USSDAPIConstants.AccountType.BOSA,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);

                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_CUSTOMER,
                            USSDAPIConstants.AccountType.BUSINESS,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsBal() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsBalWithType(USSDRequest theUSSDRequest, String theParam, String strAccountsHeader, String strCustomerIdentifier) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.FOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BUSINESS,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsBalWithType() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

}
