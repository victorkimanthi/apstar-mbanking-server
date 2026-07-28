package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.utility_items.data_formatting.Converter;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.ussd.*;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.mappapi.MAPPAPIConstants;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import ke.skyworld.sp.manager.SPManager;
import ke.skyworld.sp.manager.SPManagerConstants;
import org.w3c.dom.Document;

import java.util.*;

public interface GeneralMenus {

    static USSDResponse displayMenu_CustomersList(USSDRequest theUSSDRequest,
                                                  String theParam,
                                                  String theCustomerHeader,
                                                  String theAccountsHeader,
                                                  AppConstants.USSDDataType theCustomerUSSDType,
                                                  USSDAPIConstants.AccountType theAccountType,
                                                  AppConstants.USSDDataType theAccountUSSDType,
                                                  AppConstants.USSDDataType theUSSDType_END) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theCustomerHeader);

            TransactionWrapper<FlexicoreHashMap> customersListWrapper = theUSSDAPI.getSignatoryCustomersList(theUSSDRequest);
            if (customersListWrapper.hasErrors()) {
                String strResponse = customersListWrapper.getSingleRecord().getStringValue("display_message");
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDType_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            FlexicoreArrayList customersList = customersListWrapper.getSingleRecord().getValue("payload");

            System.out.println("ACCOUNTS LIST FOR FOSA: " + customersList);
            if (customersList.isEmpty()) {
                String strResponse = "Sorry, no member accounts found";
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDType_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            /*String strCustomerIdentifier = customersList.getRecord(0).getStringValue("identifier");
            theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theHeader, strCustomerIdentifier, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
*/

            if (customersList.size() > 1) {

                int intOptionMenu = 1;
                for (FlexicoreHashMap customersMap : customersList) {
                    String strCustomerNumber = customersMap.getStringValue("identifier");

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), strCustomerNumber, intOptionMenu + ": " + strCustomerNumber);
                    intOptionMenu++;
                }

                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theCustomerUSSDType, "NO", theArrayListUSSDSelectOption);

            } else {
                String strCustomerIdentifier = customersList.getRecord(0).getStringValue("identifier");

                /*if(theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE){
                    theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, theAccountsHeader, strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }else if(theAccountType != USSDAPIConstants.AccountType.ALL){
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest, theParam, theAccountsHeader, theAccountType,"CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }else{
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theParam, theAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }*/

                if (theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE) {
                    theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, theAccountsHeader, strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                } else if (theAccountType == USSDAPIConstants.AccountType.LOAN) {
                    theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, theAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                } else {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest, theParam, theAccountsHeader, theAccountType, "CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }

            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
        }

        return theUSSDResponse;
    }

    static USSDResponse getAccountsBalanceEnquiry(USSDRequest theUSSDRequest,
                                                  String theParam,
                                                  String theCustomerHeader,
                                                  String theAccountsHeader,
                                                  AppConstants.USSDDataType theCustomerUSSDType,
                                                  USSDAPIConstants.AccountType theAccountType,
                                                  AppConstants.USSDDataType theAccountUSSDType,
                                                  AppConstants.USSDDataType theUSSDType_END) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theCustomerHeader);

            TransactionWrapper<FlexicoreHashMap> customersListWrapper = theUSSDAPI.getSignatoryCustomersList(theUSSDRequest);
            if (customersListWrapper.hasErrors()) {
                String strResponse = customersListWrapper.getSingleRecord().getStringValue("display_message");
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDType_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            FlexicoreArrayList customersList = customersListWrapper.getSingleRecord().getValue("payload");
            if (customersList.isEmpty()) {
                String strResponse = "Sorry, no member accounts found";
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDType_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            /*String strCustomerIdentifier = customersList.getRecord(0).getStringValue("identifier");
            theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theHeader, strCustomerIdentifier, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_END);
*/

            if (customersList.size() > 1) {

                int intOptionMenu = 1;
                for (FlexicoreHashMap customersMap : customersList) {
                    String strCustomerNumber = customersMap.getStringValue("identifier");

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), strCustomerNumber, intOptionMenu + ": " + strCustomerNumber);
                    intOptionMenu++;
                }

                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theCustomerUSSDType, "NO", theArrayListUSSDSelectOption);

            } else {
                String strCustomerIdentifier = customersList.getRecord(0).getStringValue("identifier");

                /*if(theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE){
                    theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, theAccountsHeader, strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }else if(theAccountType != USSDAPIConstants.AccountType.ALL){
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest, theParam, theAccountsHeader, theAccountType,"CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }else{
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theParam, theAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }*/

                if (theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE) {
                    theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, theAccountsHeader, strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                } else if (theAccountType == USSDAPIConstants.AccountType.LOAN) {
                    theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, theAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                } else {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest, theParam, theAccountsHeader, theAccountType, "CUSTOMER_NO", strCustomerIdentifier, theAccountUSSDType, theUSSDType_END);
                }

            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
        }

        return theUSSDResponse;
    }


    static USSDResponse displayMenu_Withdrawable_Accounts(USSDRequest theUSSDRequest, String theParam, String theHeader, String theCustomerIdentifier,
                                                          AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {

        return displayMenu_AccountsListWithType(theUSSDRequest, theParam, theHeader, USSDAPIConstants.AccountType.WITHDRAWABLE, "CUSTOMER_NO",
                theCustomerIdentifier, new ArrayList<>(), theUSSDDataType, theUSSD_END_DataType);

    }


    static USSDResponse displayMenu_FOSA_Deposit_Accounts(USSDRequest theUSSDRequest, String theParam, String theHeader, String theCustomerIdentifier,
                                                          AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {

        return displayMenu_AccountsListWithType(theUSSDRequest, theParam, theHeader, USSDAPIConstants.AccountType.FOSA, "CUSTOMER_NO",
                theCustomerIdentifier, new ArrayList<>(), theUSSDDataType, theUSSD_END_DataType);

    }

    static USSDResponse displayMenu_Withdrawable_AccountsIFT(USSDRequest theUSSDRequest, String theParam, String theHeader, String theCustomerIdentifier,
                                                             AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {

        return displayMenu_AccountsListWithType(theUSSDRequest, theParam, theHeader, USSDAPIConstants.AccountType.WITHDRAWABLE_IFT, "CUSTOMER_NO",
                theCustomerIdentifier, new ArrayList<>(), theUSSDDataType, theUSSD_END_DataType);

    }

    static USSDResponse displayMenu_AccountsList(USSDRequest theUSSDRequest, String theParam, String theHeader, String theCustomerIdentifierType, String theCustomerIdentifier,
                                                 AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {
        return displayMenu_AccountsList(theUSSDRequest, theParam, theHeader, theCustomerIdentifierType, theCustomerIdentifier, new ArrayList<>(), theUSSDDataType, theUSSD_END_DataType);
    }

    static USSDResponse displayMenu_AccountsList(USSDRequest theUSSDRequest, String theParam, String theHeader,
                                                 String theCustomerIdentifierType,
                                                 String theCustomerIdentifier,
                                                 List<String> excludedAccounts,
                                                 AppConstants.USSDDataType theUSSDDataType,
                                                 AppConstants.USSDDataType theUSSD_END_DataType) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            TransactionWrapper<FlexicoreHashMap> accountsListWrapper = theUSSDAPI.getCustomerAccounts(theUSSDRequest, theCustomerIdentifierType, theCustomerIdentifier, "");

            if (accountsListWrapper.hasErrors()) {
                FlexicoreHashMap customersListMap = accountsListWrapper.getSingleRecord();
                USSDAPIConstants.Condition endSession = customersListMap.getValue("end_session");
                String strResponse = accountsListWrapper.getSingleRecord().getStringValue("display_message");

                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);

                return theUSSDResponse;
            }

            boolean foundAccounts = false;

            TreeMap<String, FlexicoreHashMap> accountsList = accountsListWrapper.getSingleRecord().getValue("payload");
            System.out.println("ACCOUNTS LIST: " + accountsList);

            int intOptionMenu = 1;
            for (Map.Entry<String, FlexicoreHashMap> entry : accountsList.entrySet()) {
                FlexicoreHashMap accountMap = entry.getValue();

                String strAccountName = accountMap.getStringValue("OutSelectedGrpOutGrmProductDescription").trim();
                String strAccountNumber = accountMap.getStringValue("OutSelectedGrpOutGrmProfitsAccountAccountNumber").trim();
                String strAccountBalance = accountMap.getStringValue("OutSelectedGrpOutGrmSearchAccountBookBalance").trim();
                String strAccountLabel = accountMap.getStringValue("product_short_name").trim();
                String strBalanceEnquiry = accountMap.getStringValue("balance_enquiry").trim();
                String strAllowDeposit = accountMap.getStringValue("allow_deposit").trim();
                String strStatus = accountMap.getStringValue("account_status").trim();

                System.out.println("ACCOUNT: " + strAccountNumber + " - " + strAccountName + " - " + strAccountBalance + " - " + strAccountLabel + " - " + strBalanceEnquiry + " - " + strAllowDeposit + " - " + strStatus);

                if (strStatus.toLowerCase().startsWith("closed")) {
                    continue;
                }

                if (excludedAccounts != null && excludedAccounts.contains(strAccountNumber)) {
                    continue;
                }

                if (/*theUSSDDataType == AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNT ||*/
                        theUSSDDataType == AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT) {
                    if (!strBalanceEnquiry.equalsIgnoreCase("YES")) continue;
                    if (!strStatus.equalsIgnoreCase("ACTIVE")) continue;

                    System.out.println("SUCCEEDED: " + strAccountNumber + " - " + strAccountLabel);
                }

                if (theUSSDDataType == AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT) {
                    if (!strAllowDeposit.equalsIgnoreCase("YES")) continue;
                }

                foundAccounts = true;

                HashMap<String, String> hmOption = new HashMap<>();
                //  hmOption.put("cust_name",strFullName);
                hmOption.put("cust_id", theCustomerIdentifier);
                hmOption.put("ac_no", strAccountNumber);
                hmOption.put("ac_name", strAccountName);
                hmOption.put("ac_label", strAccountLabel);

                String optionName = Converter.toJson(hmOption);

                // String optionName = theCustomerIdentifier+"::"+strAccountNumber+"::"+strAccountName+"::"+strAccountBalance;

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), optionName, intOptionMenu + ": " + strAccountLabel + " (" + strAccountNumber + ")");
                intOptionMenu++;
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            if (!foundAccounts) {
                String strResponse = "Sorry, no accounts found." + CBSAPI.getTrailerMessage();
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
        }

        return theUSSDResponse;
    }

    static USSDResponse displayMenu_LoanAccountsList(USSDRequest theUSSDRequest, String theParam, String theHeader, String theCustomerIdentifierType, String theCustomerIdentifier, AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            TransactionWrapper<FlexicoreHashMap> accountsListWrapper = theUSSDAPI.getCustomerLoanAccounts(theUSSDRequest, theCustomerIdentifierType, theCustomerIdentifier);

            if (accountsListWrapper.hasErrors()) {
                FlexicoreHashMap customersListMap = accountsListWrapper.getSingleRecord();
                USSDAPIConstants.Condition endSession = customersListMap.getValue("end_session");
                String strResponse = accountsListWrapper.getSingleRecord().getStringValue("display_message");

                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);

                return theUSSDResponse;
            }

            FlexicoreArrayList accountsList = accountsListWrapper.getSingleRecord().getFlexicoreArrayList("payload");

            if (accountsList.isEmpty()) {

                String strResponse = "No loans found";

                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);

            } else {

                int intOptionMenu = 1;
                for (FlexicoreHashMap accountMap : accountsList) {

                    String strAccountName = accountMap.getStringValue("loan_type_name").trim();
                    String strAccountNumber = accountMap.getStringValue("loan_serial_number").trim();
                    String strAccountBalance = accountMap.getStringValue("loan_balance").trim();
                    String strInterestBalance = accountMap.getStringValue("interest_amount").trim();
                    String strInstallMent = accountMap.getStringValue("installment_amount").trim();
                    //String strAccountLabel = accountMap.getStringValue("account_label").trim();

                    /*strAccountBalance = strAccountBalance.replaceFirst("-", "");
                    strInterestBalance = strInterestBalance.replaceFirst("-", "");*/

                    double dblAccountBalance = Double.parseDouble(strAccountBalance);
                    double dblInterestBalance = Double.parseDouble(strInterestBalance);

                    /*if (dblAccountBalance + dblInterestBalance <= 0.00) {
                        continue;
                    }*/

                    HashMap<String, String> hmOption = new HashMap<>();
                    //  hmOption.put("cust_name",strFullName);
                    hmOption.put("cust_id", theCustomerIdentifier);
                    hmOption.put("ac_no", strAccountNumber);
                    hmOption.put("ac_name", strAccountName);
                    hmOption.put("ac_label", strAccountName);
                    hmOption.put("bal", strAccountBalance);
                    hmOption.put("intr", strInterestBalance);
                    hmOption.put("installment_amount", strInstallMent);

                    String optionName = Converter.toJson(hmOption);

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), optionName, intOptionMenu + ": " + strAccountName );
                    intOptionMenu++;
                }

                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
        }

        return theUSSDResponse;
    }


    static USSDResponse displayMenu_AccountsList(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType accountType, FlexicoreHashMap customerDetailsMap,
                                                 AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            String strCustomerIdentifier = customerDetailsMap.getStringValue("customer_number");
            String strFullName = customerDetailsMap.getStringValue("customer_name");

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            TransactionWrapper<FlexicoreHashMap> accountsListWrapper = theUSSDAPI.getCustomerAccounts(theUSSDRequest, "CUSTOMER_NO", strCustomerIdentifier, accountType.getValue());
            if (accountsListWrapper.hasErrors()) {
                FlexicoreHashMap customersListMap = accountsListWrapper.getSingleRecord();
                USSDAPIConstants.Condition endSession = customersListMap.getValue("end_session");
                String strResponse = accountsListWrapper.getSingleRecord().getStringValue("display_message");

                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);

                return theUSSDResponse;
            }

            TreeMap<String, FlexicoreHashMap> accountsList = accountsListWrapper.getSingleRecord().getValue("payload");

            boolean foundAccounts = false;
            int intOptionMenu = 1;
            for (Map.Entry<String, FlexicoreHashMap> entry : accountsList.entrySet()) {
                FlexicoreHashMap accountMap = entry.getValue();
                String strAccountName = accountMap.getStringValue("OutSelectedGrpOutGrmProductDescription").trim();
                String strAccountNumber = accountMap.getStringValue("OutSelectedGrpOutGrmProfitsAccountAccountNumber").trim();
                String strAccountBalance = accountMap.getStringValue("OutSelectedGrpOutGrmSearchAccountBookBalance").trim();
                String strAccountLabel = accountMap.getStringValue("product_short_name").trim();
                String strAllowDeposit = accountMap.getStringValue("allow_deposit").trim();

                if (theUSSDDataType == AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT) {
                    if (!strAllowDeposit.equalsIgnoreCase("YES")) continue;
                }

                foundAccounts = true;

                HashMap<String, String> hmOption = new HashMap<>();
                hmOption.put("cust_name", strFullName);
                hmOption.put("cust_id", strCustomerIdentifier);
                hmOption.put("ac_no", strAccountNumber);
                hmOption.put("ac_name", strAccountName);
                hmOption.put("ac_label", strAccountLabel);

                String optionName = Converter.toJson(hmOption);

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), optionName, intOptionMenu + ": " + strAccountLabel + " (" + strAccountNumber + ")");
                intOptionMenu++;
            }

            if (!foundAccounts) {
                String strResponse = "Sorry, no accounts found." + CBSAPI.getTrailerMessage();
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
        }

        return theUSSDResponse;
    }


    static USSDResponse displayMenu_AccountsListWithType(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, String theCustomerIdentifierType, String theCustomerIdentifier, AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {
        return displayMenu_AccountsListWithType(theUSSDRequest, theParam, theHeader, theAccountType, theCustomerIdentifierType, theCustomerIdentifier, new ArrayList<>(), theUSSDDataType, theUSSD_END_DataType);
    }

    static USSDResponse displayMenu_AccountsListWithType(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, String theCustomerIdentifierType, String theCustomerIdentifier, List<String> excludedAccounts, AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);


            USSDAPIConstants.AccountType theAccountTypeMain = theAccountType;

            if (theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE_IFT) {
                theAccountTypeMain = USSDAPIConstants.AccountType.WITHDRAWABLE;
            }

            TransactionWrapper<FlexicoreHashMap> accountsListWrapper = theUSSDAPI.getCustomerAccounts(theUSSDRequest, theCustomerIdentifierType, theCustomerIdentifier, theAccountTypeMain.getValue());
            if (accountsListWrapper.hasErrors()) {
                FlexicoreHashMap customersListMap = accountsListWrapper.getSingleRecord();
                USSDAPIConstants.Condition endSession = customersListMap.getValue("end_session");
                String strResponse = accountsListWrapper.getSingleRecord().getStringValue("display_message");

                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);

                return theUSSDResponse;
            }

            FlexicoreArrayList accountsList = accountsListWrapper.getSingleRecord().getValue("payload");

            boolean foundAccounts = false;

            int intOptionMenu = 1;
            for (FlexicoreHashMap accountMap : accountsList) {

                String strAccountStatus = accountMap.getStringValue("account_status").trim();
                String strAccountNumber = accountMap.getStringValue("account_number").trim();
                String strAccountLabel = accountMap.getStringValue("account_label").trim();
                String strAccountBookBalance = accountMap.getStringValue("account_balance").trim();
                String strCanDeposit = accountMap.getStringValue("can_deposit").trim();
                String strCanWithdraw = accountMap.getStringValue("can_withdraw").trim();
                String strCanDepositIft = accountMap.getStringValue("can_deposit_ift").trim();
                String strCanWithdrawIft = accountMap.getStringValue("can_withdraw_ift").trim();


                if (strAccountLabel.equalsIgnoreCase("FOSA MAIN SAVINGS ACCOUNT")) {
                    strAccountLabel = "FOSA Savings Account";
                }

                if (strAccountLabel.equalsIgnoreCase("GROUP SAVINGS ACCOUNTS")) {
                    strAccountLabel = "Group Savings Account";
                }

                if (strAccountLabel.equalsIgnoreCase("FIXED DEPOSIT")) {
                    strAccountLabel = "Fixed Deposit";
                }


                if (theUSSDDataType == AppConstants.USSDDataType.DEPOSIT_ACCOUNT) {
                    if (!strCanDeposit.equalsIgnoreCase("YES")) continue;

                    /*if (!strAccountStatus.equalsIgnoreCase("ACTIVE")) {
                        continue;
                    }*/
                } else if (theUSSDDataType == AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT) {
                    if (!strCanDepositIft.equalsIgnoreCase("YES")) continue;
                }

                if (theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE) {
                    if (!strCanWithdraw.equalsIgnoreCase("YES")) {
                        continue;
                    }

                    if (!strAccountStatus.equalsIgnoreCase("ACTIVE")) {
                        continue;
                    }
                }

                if (theAccountType == USSDAPIConstants.AccountType.WITHDRAWABLE_IFT) {

                    System.out.println("WITHDRAWABLE_IFT: " + strAccountNumber + " - " + strAccountLabel);
                    if (!strCanWithdrawIft.equalsIgnoreCase("YES")) {
                        continue;
                    }

                    if (!strAccountStatus.equalsIgnoreCase("ACTIVE")) {
                        continue;
                    }
                }

                if (excludedAccounts.contains(strAccountNumber)) continue;

                foundAccounts = true;

                HashMap<String, String> hmOption = new HashMap<>();
                hmOption.put("cust_id", theCustomerIdentifier);
                hmOption.put("ac_no", strAccountNumber);
                hmOption.put("ac_name", strAccountLabel);
                hmOption.put("ac_label", strAccountLabel);
                hmOption.put("ac_bal", strAccountBookBalance);

                String optionName = Converter.toJson(hmOption);

                // String optionName = theCustomerIdentifier+"::"+strAccountNumber+"::"+strAccountName+"::"+strAccountBalance;

                //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), optionName, intOptionMenu + ": " + strAccountLabel + "-" + strAccountNumber);
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), optionName, intOptionMenu + ": " + strAccountLabel);

                intOptionMenu++;
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            if (!foundAccounts) {
                String strResponse = "Sorry, no accounts found." + CBSAPI.getTrailerMessage();
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } finally {
        }

        return theUSSDResponse;
    }


    static USSDResponse displayMenu_AccountCategories(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "PERSONAL", "1: Personal Account");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "GROUP", "2: Group Account");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_AccountTypes() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_LoanCategories(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "PERSONAL", "1: Personal Loan");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "GROUP", "2: Group Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_AccountTypes() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse getAccountMaintenanceMenus(USSDRequest theUSSDRequest, AppConstants.USSDDataType theUSSDDataType, String theAccountType, String theAccountNaming, String theSPProviderAccountCode, String theHeader, USSDConstants.Condition theDesplayAddRemove) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {

            String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            try {

                String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                SPManager spManager = new SPManager(strIntegritySecret);
                LinkedList<LinkedHashMap<String, String>> listAccounts = spManager.getUserSavedAccountsByProvider(SPManagerConstants.UserIdentifierType.MSISDN, strMobileNo, theSPProviderAccountCode);

                //todo REMOVE CODE
                System.out.println("\n\n**** START LIST ACCOUNTS FOR "+theSPProviderAccountCode+" ****");
                System.out.println(listAccounts.toString());
                System.out.println("**** END LIST ACCOUNTS ****\n\n");

                int count = 0;
                for (int i = 1; i <= listAccounts.size(); i++) {
                    LinkedHashMap<String, String> account = listAccounts.get((i - 1));

                    String strUserAccountID = account.get("user_account_id");
                    String strUserAccountName = account.get("user_account_name");
                    String strUserAccountIdentifier = account.get("user_account_identifier");
                    String strIntegrityHashViolated = account.get("integrity_hash_violated");

                    HashMap<String, String> hmOptionValueAccount = new HashMap<>();
                    hmOptionValueAccount.put("ACTION", "CHOICE");
                    hmOptionValueAccount.put("ACCOUNT_ID", strUserAccountID);
                    hmOptionValueAccount.put("ACCOUNT_NAME", strUserAccountName);
                    hmOptionValueAccount.put("ACCOUNT_IDENTIFIER", strUserAccountIdentifier);
                    String strOptionValueAccount = Utils.serialize(hmOptionValueAccount);


                    System.out.println("****Values Stored***********");
                    System.out.println("ACCOUNT_ID: " + strUserAccountID);
                    System.out.println("ACCOUNT_NAME: " + strUserAccountName);
                    System.out.println("ACCOUNT_IDENTIFIER: " + strUserAccountIdentifier);
                    System.out.println("***************************");

                    String strOptionDisplayText = i + ": " + strUserAccountName + " (" + strUserAccountIdentifier + ")";

                    //TODO: FATAL: REMOVE THIS CODE IN LIVEEEEEEEEEEEEE!!!!!!!
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(i), strOptionValueAccount, strOptionDisplayText);
                    if (strIntegrityHashViolated.equalsIgnoreCase(USSDConstants.Condition.NO.getValue())) {

                        //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(i), strOptionValueAccount, strOptionDisplayText);
                        count++;
                    } else {
                        System.out.println("\n\n\n\nINTEGRITY_VIOLATED.....\n\n\n");
                    }
                }
            } catch (Exception e) {
                System.err.println("GeneralMenus.getAccountMaintenanceMenus() ERROR : " + e.getMessage());
                e.printStackTrace();
            }


            if (theDesplayAddRemove.equals(USSDConstants.Condition.YES)) {
                HashMap<String, String> hmOptionValueADD = new HashMap<>();
                hmOptionValueADD.put("ACTION", "ADD");
                hmOptionValueADD.put("ACCOUNT_TYPE", theAccountType);
                String strOptionValueADD = Utils.serialize(hmOptionValueADD);

                HashMap<String, String> hmOptionValueREMOVE = new HashMap<>();
                hmOptionValueREMOVE.put("ACTION", "REMOVE");
                hmOptionValueREMOVE.put("ACCOUNT_TYPE", theAccountType);
                String strOptionValueREMOVE = Utils.serialize(hmOptionValueREMOVE);

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "88", strOptionValueADD, "88: Add " + theAccountNaming);
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "99", strOptionValueREMOVE, "99: Remove " + theAccountNaming);
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);


        } catch (Exception e) {
            System.err.println("GeneralMenus.getAccountMaintenanceMenus() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_Loans(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            //HashMap<String, HashMap<String, String>>  loans = theUSSDAPI.get(theUSSDRequest);
            HashMap<String, HashMap<String, String>> loans = new HashMap<>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            if (loans != null) {

                for (String loan_id : loans.keySet()) {
                    count++;
                    HashMap<String, String> hmLoan = loans.get(loan_id);

                    String strLoanAccountLabel = hmLoan.get("label");

                    String strOptionValue = Utils.serialize(hmLoan);
                    String strOptionMenu = Integer.toString(count);

                    String strOptionDisplayText = strOptionMenu + ": " + strLoanAccountLabel;

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                }
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            ((USSDResponseSELECT) theUSSDResponse).setUSSDSelectOptionCustomCount(count);

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_LoanTypes(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            TransactionWrapper<FlexicoreHashMap> getLoanTypesWrapper = CBSAPI.getLoanTypes(strMobileNo, "MSISDN", strMobileNo);

            FlexicoreHashMap getLoanTypesMap = getLoanTypesWrapper.getSingleRecord();

            int count = 0;

            if (!getLoanTypesWrapper.hasErrors()) {

                FlexicoreArrayList mobileEnabledLoansList = getLoanTypesMap.getFlexicoreArrayList("payload");

                if (mobileEnabledLoansList != null && !mobileEnabledLoansList.isEmpty()) {

                    for (FlexicoreHashMap loanTypesItemMap : mobileEnabledLoansList) {
                        count++;

                        HashMap<String, String> hmLoanType = new HashMap<>();

                        String strLoanTypeName = loanTypesItemMap.getStringValue("loan_type_name");
                        String strLoanTypeID = loanTypesItemMap.getStringValue("loan_type_id");
                        String strLoanTypeLabel = loanTypesItemMap.getStringValue("loan_type_name");
                        String strLoanTypeMin = loanTypesItemMap.getStringValue("loan_type_min_amount");
                        String strLoanTypeMax = loanTypesItemMap.getStringValue("loan_type_max_amount");

                        if(strLoanTypeID.equalsIgnoreCase("709")){
                            continue;
                        }

                        hmLoanType.put("id", strLoanTypeID);
                        hmLoanType.put("name", strLoanTypeName);
                        hmLoanType.put("label", strLoanTypeLabel);
                        hmLoanType.put("min", strLoanTypeMin);
                        hmLoanType.put("max", strLoanTypeMax);

                        String strOptionValue = Utils.serialize(hmLoanType);

                        String strOptionMenu = Integer.toString(count);
                        String strOptionDisplayText = strOptionMenu + ": " + strLoanTypeLabel;

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                    }
                }
            }

            if (count > 0) {
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);
                ((USSDResponseSELECT) theUSSDResponse).setUSSDSelectOptionCustomCount(count);

            } else {
                String strResponse = "Sorry, no loans found.";
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_LoanTypesForGurantorship(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();
        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            HashMap<String, HashMap<String, String>>  loanTypes = new HashMap<>();
            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("name", "Ufanisi Loan");
                hmLoanType.put("id", "U0001");
                hmLoanType.put("code", "U0001");
                hmLoanType.put("max", "3");
                hmLoanType.put("min", "2");
                hmLoanType.put("duration", "12");
                hmLoanType.put("interest", "8");
                loanTypes.put("UFANISI", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("name", "Development Loan");
                hmLoanType.put("id", "D0002");
                hmLoanType.put("code", "D0002");
                hmLoanType.put("max", "3");
                hmLoanType.put("min", "2");
                hmLoanType.put("duration", "12");
                hmLoanType.put("interest", "8");
                loanTypes.put("DEVELOPMENT", hmLoanType);
            }

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            if(loanTypes != null) {

                for (String loanType : loanTypes.keySet()) {
                    count++;
                    String strAccount = loanType;
                    HashMap<String, String> hmLoanType = loanTypes.get(loanType);

                    String strOptionValue = Utils.serialize(hmLoanType);
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");

                    String strOptionMenu = Integer.toString(count);
                    String strOptionDisplayText = strOptionMenu + ": " + strLoanTypeName;

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                }
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            ( (USSDResponseSELECT)  theUSSDResponse ).setUSSDSelectOptionCustomCount(count);

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }



    static USSDResponse displayMenu_ATMCards(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType, AppConstants.USSDDataType theUSSD_END_DataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            int count = 1;

            long randomNumber = (long) (Math.random() * 1_000_000_000_000_000L);
            String accountNumber = String.format("%014d", randomNumber);

            HashMap<String, String> hmLoanType = new HashMap<>();

            hmLoanType.put("ID", accountNumber);
            hmLoanType.put("NAME", "CoopBank ATM Card");

            String strOptionValue = Utils.serialize(hmLoanType);

            String strOptionMenu = Integer.toString(count);
            String strOptionDisplayText = strOptionMenu + ": " + "CoopBank ATM Card";
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);

            if (count > 0) {
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);
                ((USSDResponseSELECT) theUSSDResponse).setUSSDSelectOptionCustomCount(count);

            } else {
                String strResponse = "Sorry, no ATMs found.";
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSD_END_DataType, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_ATMCards() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    /*static USSDResponse displayMenu_IdentifierBankAccounts(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try{
            //
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            HashMap<Object, Object>  hmRVal = theUSSDAPI.getIdentifierBankAccounts(theUSSDRequest, theAccountType);

            HashMap<String, HashMap <String, String>>  accounts = (HashMap<String, HashMap <String, String>>) hmRVal.get("accounts");
            HashMap<String, String>  hmUserDetails = (HashMap<String, String>) hmRVal.get("user_details");


            //String strRequestStatus = (String) hmUserDetails.get("request_status");
            //String strMemberNumber = (String)  hmUserDetails.get("member_number");
            String strFullName = (String) hmUserDetails.get("full_name");
            //String strIdentifierType = (String) hmUserDetails.get("identifier_type");
            //String strIdentifier = (String) hmUserDetails.get("identifier");
            //String strIdentityType = (String) hmUserDetails.get("identity_type");
            //String strIdentity = (String) hmUserDetails.get("identity");

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            if(accounts != null) {

                for (String account_no : accounts.keySet()) {
                    count++;
                    //System.out.println("account: " + account_no + " account type: " + accounts.get(account_no));

                    HashMap <String, String> hmAccount = accounts.get(account_no);
                    hmAccount.put("full_name", strFullName);

                    String strOptionValue  = Utils.serialize(hmAccount);
                    String strOptionMenu = Integer.toString(count);

                    String strAccountLabel = hmAccount.get("label");

                    String strOptionDisplayText = strOptionMenu + ": " + strAccountLabel;

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                }
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            ( (USSDResponseSELECT)  theUSSDResponse ).setUSSDSelectOptionCustomCount(count);
        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_IdentifierBankAccounts() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_Loans(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();
        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            HashMap<String, HashMap<String, String>>  loans = theUSSDAPI.getLoans(theUSSDRequest);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            if(loans != null) {

                for (String loan_id : loans.keySet()) {
                    count++;
                    HashMap<String, String> hmLoan = loans.get(loan_id);

                    String strLoanAccountLabel= hmLoan.get("label");

                    String strOptionValue = Utils.serialize(hmLoan);
                    String strOptionMenu = Integer.toString(count);

                    String strOptionDisplayText = strOptionMenu + ": " + strLoanAccountLabel;

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                }
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            ( (USSDResponseSELECT)  theUSSDResponse ).setUSSDSelectOptionCustomCount(count);

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_LoanTypes(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();
        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            HashMap<String, HashMap<String, String>>  loanTypes = theUSSDAPI.getLoanTypes(theUSSDRequest);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            if(loanTypes != null) {

                for (String loanType : loanTypes.keySet()) {
                    count++;
                    String strAccount = loanType;
                    HashMap<String, String> hmLoanType = loanTypes.get(loanType);

                    String strOptionValue = Utils.serialize(hmLoanType);
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");

                    String strOptionMenu = Integer.toString(count);
                    String strOptionDisplayText = strOptionMenu + ": " + strLoanTypeName;

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                }
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            ( (USSDResponseSELECT)  theUSSDResponse ).setUSSDSelectOptionCustomCount(count);

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_AccountGroups(USSDRequest theUSSDRequest, String theParam, String theHeader, USSDAPIConstants.AccountType theAccountType, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            HashMap<String, String> accounts = theUSSDAPI.getAccountGroups(theUSSDRequest);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            if(accounts != null) {

                for (String account : accounts.keySet()) {
                    count++;
                    System.out.println("group: " + account + " group id: " + accounts.get(account));
                    String strAccount = account;
                    String strAccountType = accounts.get(account);

                    String strOptionValue = strAccount;
                    String strOptionMenu = Integer.toString(count);
                    String strOptionDisplayText = strOptionMenu + ": " + strAccountType;// + " (" + strAccount + ")"; //"1: Member Acct.(10101010101)"

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
                }
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO", theArrayListUSSDSelectOption);

            ( (USSDResponseSELECT)  theUSSDResponse ).setUSSDSelectOptionCustomCount(count);

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_BankAccounts() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_AccountTypes(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA", "1: Savings Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA", "2: Shares, Deposits and Benevolent");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "LOAN", "3: Loans");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO",theArrayListUSSDSelectOption);
        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_AccountTypes() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_AccountTypesPlusALL(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA", "1: Savings Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA", "2: Shares, Deposits and Benevolent");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "LOAN", "3: Loans");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "4", "ALL", "4: All Accounts");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO",theArrayListUSSDSelectOption);
        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_AccountTypesPlusALL() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_AccountCategories(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "PERSONAL", "1: Personal Account");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "GROUP", "2: Group Account");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO",theArrayListUSSDSelectOption);
        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_AccountTypes() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse displayMenu_LoanCategories(USSDRequest theUSSDRequest, String theParam, String theHeader, AppConstants.USSDDataType theUSSDDataType) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try{
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "PERSONAL", "1: Personal Loan");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "GROUP", "2: Group Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO",theArrayListUSSDSelectOption);
        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_AccountTypes() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    static USSDResponse getAccountMaintenanceMenus(USSDRequest theUSSDRequest, AppConstants.USSDDataType theUSSDDataType, String theAccountType, String theAccountNaming, String theSPProviderAccountCode, String theHeader, USSDConstants.Condition theDesplayAddRemove) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try{

            String strMobileNo = String.valueOf( theUSSDRequest.getUSSDMobileNo() );

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            try{

                String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                SPManager spManager = new SPManager(strIntegritySecret);
                LinkedList<LinkedHashMap<String, String>> listAccounts = spManager.getUserSavedAccountsByProvider(SPManagerConstants.UserIdentifierType.MSISDN, strMobileNo, theSPProviderAccountCode);

                //todo REMOVE CODE
                System.out.println("\n\n**** START LIST ACCOUNTS ****");
                System.out.println(listAccounts.toString());
                System.out.println("**** END LIST ACCOUNTS ****\n\n");

                int count = 0;
                for (int i = 1; i <= listAccounts.size() ; i++) {
                    LinkedHashMap<String, String> account = listAccounts.get((i-1));

                    String strUserAccountID = account.get("user_account_id");
                    String strUserAccountName = account.get("user_account_name");
                    String strUserAccountIdentifier = account.get("user_account_identifier");
                    String strIntegrityHashViolated = account.get("integrity_hash_violated");

                    HashMap<String, String> hmOptionValueAccount = new HashMap<>();
                    hmOptionValueAccount.put("ACTION","CHOICE");
                    hmOptionValueAccount.put("ACCOUNT_ID",strUserAccountID);
                    hmOptionValueAccount.put("ACCOUNT_NAME",strUserAccountName);
                    hmOptionValueAccount.put("ACCOUNT_IDENTIFIER",strUserAccountIdentifier);
                    String strOptionValueAccount= Utils.serialize(hmOptionValueAccount);

                    String strOptionDisplayText = i + ": " + strUserAccountName + " (" + strUserAccountIdentifier + ")";

                    if(strIntegrityHashViolated.equalsIgnoreCase(USSDConstants.Condition.NO.getValue())){
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(i), strOptionValueAccount, strOptionDisplayText);
                        count++;
                    }
                }
            }catch (Exception e){
                System.err.println("GeneralMenus.getAccountMaintenanceMenus() ERROR : " + e.getMessage());
            }


            if(theDesplayAddRemove.equals(USSDConstants.Condition.YES)){
                HashMap<String, String> hmOptionValueADD = new HashMap<>();
                hmOptionValueADD.put("ACTION","ADD");
                hmOptionValueADD.put("ACCOUNT_TYPE",theAccountType);
                String strOptionValueADD = Utils.serialize(hmOptionValueADD);

                HashMap<String, String> hmOptionValueREMOVE = new HashMap<>();
                hmOptionValueREMOVE.put("ACTION","REMOVE");
                hmOptionValueREMOVE.put("ACCOUNT_TYPE",theAccountType);
                String strOptionValueREMOVE = Utils.serialize(hmOptionValueREMOVE);

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "88", strOptionValueADD, "88: Add " + theAccountNaming);
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "99", strOptionValueREMOVE, "99: Remove " + theAccountNaming);
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, theUSSDDataType, "NO",theArrayListUSSDSelectOption);


        }catch(Exception e){
            System.err.println("GeneralMenus.getAccountMaintenanceMenus() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }
*/
}

