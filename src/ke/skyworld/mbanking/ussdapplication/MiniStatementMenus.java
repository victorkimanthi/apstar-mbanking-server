package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import java.util.ArrayList;
import java.util.HashMap;

public interface MiniStatementMenus {

    default USSDResponse displayMenu_MiniStatementMenu(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.ACCOUNT_STATEMENT);
            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader+"\n" + getServiceStatusDetails.getStringValue("display_message"));
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;

            }else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.ACCOUNT_STATEMENT)) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader+"\n"+AppConstants.strServiceUnavailable);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }


            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA_ACCOUNTS", "1: FOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA_ACCOUNTS", "2: BOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "BUSINESS_ACCOUNTS", "3: Business Accounts");
//            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "PAY_LOAN", "3: Pay Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_MinistatementMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_MiniStatement(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            /*switch (theParam) {
                case "MENU" -> {
                    String strHeader = "Mini Statement";
                    theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                }

                case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS" -> {
                    String strHeader = "Mini Statement";
                    theUSSDResponse = displayMenu_AccountsMini(theUSSDRequest, theParam, strHeader);
                }

                default -> {
                    String strHeader = "Mini Statement\n{Select a valid menu}";
                    theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                }
            }
*/

            String strUSSDDataType = theUSSDRequest.getUSSDDataType();


            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MY_ACCOUNT_MENU.getValue())
                    || strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.getValue())) {
                String strHeader = "Mini Statement";
                theUSSDResponse = displayMenu_MiniStatementMenu(theUSSDRequest, theParam, strHeader);
            } else { //LOAN_MENU

                String strAccountBalance_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_MENU.name());

                switch (strAccountBalance_MENU) {
                    case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS" -> {
                        String strHeader = "Mini Statement";
                        theUSSDResponse = displayMenu_AccountsMini(theUSSDRequest, theParam, strHeader);
                    }

                    default -> {
                        String strHeader = "Mini Statement\n{Select a valid menu}";
                        theUSSDResponse = displayMenu_MiniStatementMenu(theUSSDRequest, theParam, strHeader);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_MiniStatement() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_AccountsMini(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            switch (theParam) {
                case "MENU": {

                    String strCustomerHeader = theHeader + " \nSelect member\n";
                    String strAccountsHeader = theHeader + " \nSelect account\n";

                    theUSSDResponse = getBankAccountsMini(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);

                    break;
                }
                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsMiniWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);

                    } else {

                        String strCustomerHeader = theHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsMini(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT.name());
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_CUSTOMER.name());

                    if (strAccount.length() > 0) {
                        String strResponse = theHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {

                        String strCustomerHeader = theHeader + " \nSelect member\n";
                        String strAccountsHeader = theHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = getBankAccountsMiniWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);
                        } else {
                            theUSSDResponse = getBankAccountsMini(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                        }
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT.name());

                        HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
                        String strAccountNumber = hmFromAccountNoDetails.get("ac_no");

                        String strResponse = "Dear member, your Mini Statement request for Account " + strAccountNumber + " has been received successfully. Please wait shortly as it's being processed.\n";

                        USSDAPI finalTheUSSDAPI = theUSSDAPI;
                        Thread worker = new Thread(() -> {
                            TransactionWrapper<FlexicoreHashMap> accountMiniStatementWrapper = finalTheUSSDAPI.accountMiniStatement(theUSSDRequest);
                            if (accountMiniStatementWrapper.hasErrors()) {
                                FlexicoreHashMap accountMiniStatementMap = accountMiniStatementWrapper.getSingleRecord();
                                String strErrorMessage = accountMiniStatementMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += accountMiniStatementMap.getStringValue("display_message");

                                System.err.println("MiniStatementMenus.displayMenu_MiniStatement() - Response " + strErrorMessage);
                            }
                        });
                        worker.start();

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = "Mini Statement\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_AccountsMini() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsMini(USSDRequest theUSSDRequest, String theParam, String strCustomerHeader, String strAccountsHeader) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.FOSA,
                            AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.BOSA,
                            AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END);

                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.BUSINESS,
                            AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsMini() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsMiniWithType(USSDRequest theUSSDRequest, String theParam, String strAccountsHeader, String strCustomerIdentifier) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.FOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END);
                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BUSINESS,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_MINI_STATEMENT_END);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsMiniWithType() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

}
