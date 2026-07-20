package ke.skyworld.mbanking.ussdapplication;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.Misc;
import ke.co.skyworld.smp.utility_items.file_utils.FileOps;
import ke.skyworld.lib.mbanking.mapp.MAPPConstants;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.cbs.ChannelService;
import ke.skyworld.mbanking.channelutils.EmailMessaging;
import ke.skyworld.mbanking.mappapi.AccountStatements;
import ke.skyworld.mbanking.mappapi.MAPPAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import static ke.skyworld.lib.mbanking.mapp.MAPPConstants.ResponseAction.CON;
import static ke.skyworld.lib.mbanking.mapp.MAPPConstants.ResponseStatus.FAILED;
import static ke.skyworld.lib.mbanking.mapp.MAPPConstants.ResponsesDataType.TEXT;
import static ke.skyworld.mbanking.ussdapi.APIUtils.generateLastNMonthsDateRanges;

public interface FullStatementMenus {

    default USSDResponse displayMenu_FullStatementMenu(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.ACCOUNT_STATEMENT);
            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;

            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.ACCOUNT_STATEMENT)) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader + "\n" + AppConstants.strServiceUnavailable);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }


            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA_ACCOUNTS", "1: FOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA_ACCOUNTS", "2: BOSA Accounts");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "BUSINESS_ACCOUNTS", "3: Business Accounts");
//            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "PAY_LOAN", "3: Pay Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FullstatementMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_FullStatement(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            /*switch (theParam) {
                case "MENU" -> {
                    String strHeader = "Full Statement";
                    theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                }

                case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS" -> {
                    String strHeader = "Full Statement";
                    theUSSDResponse = displayMenu_AccountsFull(theUSSDRequest, theParam, strHeader);
                }

                default -> {
                    String strHeader = "Full Statement\n{Select a valid menu}";
                    theUSSDResponse = displayMenu_BalanceEnquiryMenu(theUSSDRequest, theParam, strHeader);
                }
            }
*/

            String strUSSDDataType = theUSSDRequest.getUSSDDataType();


            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MY_ACCOUNT_MENU.getValue())
                    || strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.getValue())) {
                String strHeader = "Full Statement";
                theUSSDResponse = displayMenu_FullStatementMenu(theUSSDRequest, theParam, strHeader);
            } else { //LOAN_MENU

                String strAccountBalance_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_MENU.name());

                switch (strAccountBalance_MENU) {
                    case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS" -> {
                        String strHeader = "Full Statement";
                        theUSSDResponse = displayMenu_AccountsFull(theUSSDRequest, theParam, strHeader);
                    }

                    default -> {
                        String strHeader = "Full Statement\n{Select a valid menu}";
                        theUSSDResponse = displayMenu_FullStatementMenu(theUSSDRequest, theParam, strHeader);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FullStatement() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_AccountsFull(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            switch (theParam) {
                case "MENU": {

                    String strCustomerHeader = theHeader + " \nSelect member\n";
                    String strAccountsHeader = theHeader + " \nSelect account\n";

                    theUSSDResponse = getBankAccountsFull(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);

                    break;
                }
                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsFullWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);

                    } else {

                        String strCustomerHeader = theHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsFull(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT.name());
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CUSTOMER.name());

                    if (strAccount.length() > 0) {
                        /*String strResponse = theHeader + " \nEnter Statement Start Date in the format (DD/MM/YYYY):";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE, USSDConstants.USSDInputType.STRING, "NO");*/
                        String strResponse = theHeader + " \n{Select a Duration}\n";
                        HashMap<String, String[]> dateRanges = generateLastNMonthsDateRanges(6);
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        for (int i = 1; i < dateRanges.size(); i++) {
                            String key = String.valueOf(i);
                            String[] dateRange = dateRanges.get(key);
                            String strV = dateRange[0] + " to " + dateRange[1];
                            String strFormattedDateRange = key+ ". Last " + key + " Months";

                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, key, strV, strFormattedDateRange);
                        }
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE, "NO", theArrayListUSSDSelectOption);


                    } else {

                        String strCustomerHeader = theHeader + " \nSelect member\n";
                        String strAccountsHeader = theHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = getBankAccountsFullWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);
                        } else {
                            theUSSDResponse = getBankAccountsFull(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                        }
                    }
                    break;
                }

                case "START_DATE": {

                    String strStartDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE.name());
                   /* if (AppUtils.isValidDate(strStartDate, "dd/MM/yyyy")) {
                        String strResponse = theHeader + " \nEnter Statement End Date in the format (DD/MM/YYYY):";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END_DATE, USSDConstants.USSDInputType.STRING, "NO");*/
                    if (strStartDate != null) {
                        String strResponse = theHeader + " \nEnter Email Address:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        HashMap<String, String[]> dateRanges = generateLastNMonthsDateRanges(6);
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Select a Valid Duration");
                        for (int i = 1; i < dateRanges.size(); i++) {
                            String key = String.valueOf(i);
                            String[] dateRange = dateRanges.get(key);
                            String strV = dateRange[0] + " to " + dateRange[1];
                            String strFormattedDateRange = key+ ". Last " + key + " Months";

                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, key, strV, strFormattedDateRange);
                        }
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE, "NO", theArrayListUSSDSelectOption);

                    }

                    break;
                }

                case "END_DATE": {

                    String strEndDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END_DATE.name());

                    if (AppUtils.isValidDate(strEndDate, "dd/MM/yyyy")) {
                        String strResponse = theHeader + " \nEnter Email Address:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strResponse = theHeader + " \n{Invalid Date}\nEnter Statement End Date in the format (DD/MM/YYYY):";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END_DATE, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "EMAIL_ADDRESS": {

                    String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS.name());
                    String strDateRange = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE.name());
                    System.out.println("strDateRange " + strDateRange);
                    String[] dateRange = strDateRange.split("to");
                    String strFormattedDateRange = dateRange[0] + " to " + dateRange[1];
                    System.out.println("FullStatementMenus.displayMenu_AccountsFull() - strFormattedDateRange " + strFormattedDateRange);

                    if (AppUtils.isValidEmailAddress(strEmailAddress)) {
                        String strResponse = theHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {
                        String strResponse = theHeader + " \n{Invalid Email}\nEnter a Valid Email Address:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT.name());
                        String strDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE.name());
                        String strStartDate= strDate.split("to")[0];
                        String strEndDate= strDate.split("to")[1];
                        String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS.name());

                        HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
                        String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
                        String strAccountName = hmFromAccountNoDetails.get("ac_name");

                        String strResponse = "Confirm Full Statement Request"
                                + "\nAcc Name: " + strAccountName
                                + "\nFrom " + strEndDate +" to "+ strStartDate
                                + "\nEmail: " + strEmailAddress;

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);


                    } else {
                        String strResponse = "Full Statement\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {
                            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT.name());

                            String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS.name());

                            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
                            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");

                            String strResponse = "Your Full Statement request for Account " + strAccountNumber + " has been received and will be sent via '" + strEmailAddress + "'.\n";

                            USSDAPI finalTheUSSDAPI = theUSSDAPI;
                            Thread worker = new Thread(() -> {
                            /*TransactionWrapper<FlexicoreHashMap> accountFullStatementWrapper = finalTheUSSDAPI.accountFullStatement(theUSSDRequest);
                            if (accountFullStatementWrapper.hasErrors()) {
                                FlexicoreHashMap accountFullStatementMap = accountFullStatementWrapper.getSingleRecord();
                                String strErrorMessage = accountFullStatementMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += accountFullStatementMap.getStringValue("display_message");

                                System.err.println("FullStatementMenus.displayMenu_FullStatement() - Response " + strErrorMessage);
                            }*/
                                generateStatement(theUSSDRequest);

                            });
                            worker.start();

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END, "NO", theArrayListUSSDSelectOption);

                            break;
                        }
                        case "NO": {
                            String strResponse = "";
                            strResponse = "Dear member, your Full Statement request was NOT confirmed. Full Statement request NOT COMPLETED.\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {

                            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT.name());
                            String strDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE.name());
                            String strStartDate= strDate.split("to")[0];
                            String strEndDate= strDate.split("to")[1];

                            String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS.name());

                            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
                            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
                            String strAccountName = hmFromAccountNoDetails.get("ac_name");

                            String strResponse = "Confirm Full Statement Request\n{Select a valid menu}\nAcc No: " + strAccountNumber
                                    + "\nAcc Name: " + strAccountName
                                    + "\nStart: " + strStartDate
                                    + "\nEnd: " + strEndDate
                                    + "\nEmail: " + strEmailAddress;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                            break;
                        }
                    }
                    break;
                }

            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_AccountsFull() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsFull(USSDRequest theUSSDRequest, String theParam, String strCustomerHeader, String strAccountsHeader) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.FOSA,
                            AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.BOSA,
                            AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END);

                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.BUSINESS,
                            AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsFull() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsFullWithType(USSDRequest theUSSDRequest, String theParam, String strAccountsHeader, String strCustomerIdentifier) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.FOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END);
                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BUSINESS,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT, AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_END);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsFullWithType() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }


    default void generateStatement(USSDRequest theUSSDRequest) {

        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_ACCOUNT.name());
            String strDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_START_DATE.name());
            String strStartDate= strDate.split("to")[0];
            String strEndDate= strDate.split("to")[1];

            String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_FULL_STATEMENT_EMAIL_ADDRESS.name());

            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
            String strAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strAccountName = hmFromAccountNoDetails.get("ac_name");

            String strFormattedStartDate = DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "yyyy-MM-dd");
            String strFormattedEndDate = DateTime.convertStringToDateToString(strEndDate, "dd/MM/yyyy", "yyyy-MM-dd");

            TransactionWrapper<FlexicoreHashMap> miniStatementWrapper = CBSAPI.accountFullStatement(String.valueOf(theUSSDRequest.getUSSDMobileNo()),
                    "MSISDN", String.valueOf(theUSSDRequest.getUSSDMobileNo()),
                    "IMSI", theUSSDRequest.getUSSDIMSI(), strAccountNumber,
                    "100", strFormattedEndDate + " 00:00:00", strFormattedStartDate + " 23:59:59");

            FlexicoreHashMap miniStatementMap = miniStatementWrapper.getSingleRecord();

            String strTitle = "Account Statement";

            String strMemberName = theUSSDAPI.getUserFullName(String.valueOf(theUSSDRequest.getUSSDMobileNo()));

            String strOriginatorId = UUID.randomUUID().toString();

            if (miniStatementWrapper.hasErrors()) {
                strTitle = "Error: Account Statement Failed";


            } else {
                FlexicoreArrayList allTransactionsList = miniStatementMap.getFlexicoreArrayList("payload");
                /*if (allTransactionsList.isEmpty()) {

                } else {*/

                    String theAccountStatement = AccountStatements.getAccountStatementHTML();
                    System.out.println(theAccountStatement);

                    String strFormattedPeriod = DateTime.convertStringToDateToString(strEndDate, "dd/MM/yyyy", "dd MMM yyyy");
                    strFormattedPeriod = strFormattedPeriod + " to ";
                    System.out.println("strEndDate " + strEndDate);
                    strFormattedPeriod = strFormattedPeriod + DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "dd MMM yyyy");

                    theAccountStatement = theAccountStatement.replace("[STATEMENT_PERIOD]", Misc.escapeHtmlEntity(strFormattedPeriod));

                    String strAvailableBalance = miniStatementMap.getStringValue("account_available_balance");

                    strAvailableBalance = Utils.formatDouble(strAvailableBalance, "#,##0.00");

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_BALANCE]", "KES " + strAvailableBalance);

                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_NAME]", Misc.escapeHtmlEntity(miniStatementMap.getStringValue("account_name")));
                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_NUMBER]", Misc.escapeHtmlEntity(strAccountNumber));
                    theAccountStatement = theAccountStatement.replace("[ACCOUNT_HOLDER]",strMemberName);

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
                                "                <td class='statement-header-acc-stmnt-date'>" + Misc.escapeHtmlEntity(strMSGFormattedTransactionDateTime) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-description'>" + Misc.escapeHtmlEntity(strMSGTransactionDescription) + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-paid-in'>" +
                                (strDebitCredit.equalsIgnoreCase("C") ? Utils.formatDouble(strMSGFormattedTransactionAmount, "#,##0.00") : "") + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-paid-out'>" +
                                (strDebitCredit.equalsIgnoreCase("D") ? Utils.formatDouble(strMSGFormattedTransactionAmount, "#,##0.00") : "") + "</td>\n" +
                                "                <td class='statement-header-acc-stmnt-balance'>" + Utils.formatDouble(strMSGRunningBalance, "#,##0.00") + "</td>\n" +
                                "            </tr>");

                        if (strDebitCredit.equalsIgnoreCase("C")) {
                            dblTotalPaidIn += Double.parseDouble(strMSGFormattedTransactionAmount);
                        } else {
                            dblTotalPaidOut += Double.parseDouble(strMSGFormattedTransactionAmount);
                        }


                        if (i >= 200) {
                            break;
                        }

                        i++;
                    }

                    theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_IN]", "KES " + Utils.formatDouble(dblTotalPaidIn, "#,##0.00"));
                    theAccountStatement = theAccountStatement.replace("[TOTAL_PAID_OUT]", "KES " + Utils.formatDouble(dblTotalPaidOut, "#,##0.00"));
                    theAccountStatement = theAccountStatement.replace("[THE_ACCOUNT_STATEMENT_DETAILS]", builder.toString());
                    String theFileName = generateAccountStatementPDF(theAccountStatement, strAccountNumber);

                    String[] filenameArr = theFileName.split("/");


                    HashMap<String, String> attachmentsMap = new HashMap<>();
                    attachmentsMap.put(filenameArr[filenameArr.length - 1], theFileName);

                    strFormattedStartDate = DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "dd MMM yyyy");
                    strFormattedEndDate = DateTime.convertStringToDateToString(strEndDate, "dd/MM/yyyy", "dd MMM yyyy");

                    String emailTemplate = AccountStatements.getEmailTemplateHTML();

                    emailTemplate = emailTemplate
                            .replace("[START_DATE]", strFormattedStartDate)
                            .replace("[END_DATE]", strFormattedEndDate)
                            .replace("[ACCOUNT_NUMBER]", strAccountNumber);

                    String strEmailBody = "Dear " + strMemberName + ",\n\n"
                            + "Please find attached your account statement for the period " + strFormattedStartDate + " to " + strFormattedEndDate + ".\n\n"
                            + "Thank you for choosing us.\n";

                    EmailMessaging.sendEmail(strEmailAddress, "Account Statement From "+strFormattedEndDate+  "-" +strFormattedEndDate , strEmailBody, "ACCOUNT_STATEMENT", attachmentsMap);
                //}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String generateAccountStatementPDF(String strStatement, String strAccountNo) {
        OutputStream outputStream = null;
        PDDocument doc = null;

        System.out.println("generateAccountStatementPDF() - strStatement: " + strStatement);
        System.out.println("generateAccountStatementPDF() - strAccountNo: " + strAccountNo);

        String strTheOutputFileName = System.getProperty("user.dir")
                + "/system-folders/outputs/"
                 + "ACC-"
                + DateTime.getCurrentDateTime("yyyy-MM-dd") + ".pdf";

        try {
            // Create output folder if it doesn't exist
            File outputFolder = new File(System.getProperty("user.dir") + "/system-folders/outputs/");
            if (!outputFolder.exists()) {
                outputFolder.mkdirs();
            }

            // Initialize PDF document and renderer
            doc = new PDDocument();
            PdfRendererBuilder pdfRendererBuilder = new PdfRendererBuilder();
            pdfRendererBuilder.useFastMode();
            pdfRendererBuilder.withHtmlContent(strStatement, "file://" + System.getProperty("user.dir") + "/system-folders/templates/");
            pdfRendererBuilder.usePDDocument(doc);
            pdfRendererBuilder.useSVGDrawer(new BatikSVGDrawer());

            // Generate PDF
            PdfBoxRenderer renderer = pdfRendererBuilder.buildPdfRenderer();
            renderer.createPDFWithoutClosing();
            renderer.close();

            // Save the generated PDF
            outputStream = new FileOutputStream(strTheOutputFileName);
            doc.save(outputStream);

            System.out.println("PDF created: " + strTheOutputFileName);
            return strTheOutputFileName;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (doc != null) {
                    doc.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return "";
    }



}
