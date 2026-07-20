package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.pesaapi.PESAAPI;
import ke.skyworld.mbanking.pesaapi.PESAAPIConstants;
import ke.skyworld.mbanking.pesaapi.PesaParam;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static ke.skyworld.mbanking.ussdapplication.GeneralMenus.displayMenu_FOSA_Deposit_Accounts;

public interface DepositMenus {

    default USSDResponse displayMenu_DepositMenu(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.DEPOSIT_VIA_MPESA);
            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Deposit\n" + getServiceStatusDetails.getStringValue("display_message"));
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.DEPOSIT)) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Deposit\n" + AppConstants.strServiceUnavailable);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA_ACCOUNTS", "1: FOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA_ACCOUNTS", "2: BOSA Accounts");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "BUSINESS_ACCOUNTS", "3: Business Accounts");
//            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "PAY_LOAN", "3: Pay Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_DepositMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_Deposit(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {
            String strUSSDDataType = theUSSDRequest.getUSSDDataType();

            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.getValue())) {
                String strHeader = "Deposit via M-PESA";
                theUSSDResponse = displayMenu_DepositMenu(theUSSDRequest, theParam, strHeader);
            } else { //LOAN_MENU

                String strDEPOSIT_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_MENU.name());

                switch (strDEPOSIT_MENU) {
                    case "FOSA_ACCOUNTS", "BOSA_ACCOUNTS", "BUSINESS_ACCOUNTS": {
                        String strHeader = "Deposit via M-PESA";
                        theUSSDResponse = displayMenu_AccountsDeposit(theUSSDRequest, theParam, strHeader);
                        break;
                    }

                    case "PAY_LOAN": {
                        theUSSDResponse = theAppMenus.displayMenu_LoanRepayment(theUSSDRequest, theParam);
                        break;
                    }
                    default: {
                        String strHeader = "Deposit via M-PESA\n{Select a valid menu}";
                        theUSSDResponse = displayMenu_DepositMenu(theUSSDRequest, theParam, strHeader);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Deposit() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_AccountsDeposit(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_C2B);
        String strSender = pesaParam.getSenderIdentifier();

        try {

            switch (theParam) {
                case "MENU": {

                    String strCustomerHeader = theHeader + " \nSelect member\n";
                    String strAccountsHeader = theHeader + " \nSelect account\n";

                    theUSSDResponse = getBankAccounts(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);

                    break;
                }
                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccountsWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);

                    } else {

                        String strCustomerHeader = theHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = theHeader + " \nSelect account\n";

                        theUSSDResponse = getBankAccounts(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_ACCOUNT.name());
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_CUSTOMER.name());

                    if (strAccount.length() > 0) {
                        String strResponse = theHeader + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DEPOSIT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else {

                        String strCustomerHeader = theHeader + " \nSelect member\n";
                        String strAccountsHeader = theHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = getBankAccountsWithType(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier);
                        } else {
                            theUSSDResponse = getBankAccounts(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);
                        }
                    }
                    break;
                }
                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_AMOUNT.name());
                    if (strAmount.matches("^[1-9][0-9]*$")) {
                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        String strCustomerIdentifier = hmAccountDetails.get("cust_id");
                        String strAccountNo = hmAccountDetails.get("ac_no");
                        //String strAccountName = hmAccountDetails.get("ac_name");
                        String strAccountLabel = hmAccountDetails.get("ac_label");

                        String strResponse = "Confirm Deposit via M-PESA\nPaybill no.: " + strSender + "\nAccount: " + strAccountLabel + "-" + strAccountNo + "\n" + "Amount: KES " + strAmount + "\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        String strDepositMinimum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMinimum();
                        String strDepositMaximum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMaximum();

                        double dblDepositMinimum = Double.parseDouble(strDepositMinimum);
                        double dblDepositMaximum = Double.parseDouble(strDepositMaximum);

                        double dblAmountEntered = Double.parseDouble(strAmount);

                        if (dblAmountEntered < dblDepositMinimum) {
                            strResponse = theHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strDepositMinimum, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DEPOSIT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        if (dblAmountEntered > dblDepositMaximum) {
                            strResponse = theHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strDepositMaximum, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DEPOSIT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                    } else {
                        String strResponse = theHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DEPOSIT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }
                /* SKIP PIN REQUEST
                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_AMOUNT.name());
                    if(strAmount.matches("^[1-9][0-9]*$")){
                        String strResponse = theHeader+"\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.DEPOSIT_PIN, USSDConstants.USSDInputType.STRING,"NO");
                    }else{
                        String strResponse = theHeader+"\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.DEPOSIT_AMOUNT, USSDConstants.USSDInputType.STRING,"NO");
                    }
                    break;
                }
                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_PIN.name());
                    if(strLoginPIN.equals(strPIN)){
                        String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_ACCOUNT.name());
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_AMOUNT.name());
                        strAmount = Utils.formatDouble(strAmount, "#,###");

                        String strResponse =  "Confirm "+theHeader+"\nAccount: "+strAccount+"\n" + "Amount: KES "+strAmount+"\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_CONFIRMATION, "NO",theArrayListUSSDSelectOption);

                    }else{
                        String strResponse = theHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.DEPOSIT_PIN, USSDConstants.USSDInputType.STRING,"NO");
                    }
                    break;
                }
                 */
                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {
                            String strResponse = "";

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_AMOUNT.name());
                            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                            String strCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strAccountNo = hmAccountDetails.get("ac_no");
                            //String strAccountName = hmAccountDetails.get("ac_name");
                            String strAccountLabel = hmAccountDetails.get("ac_label");

                            if (theUSSDRequest.getUSSDProviderCode() == AppConstants.USSDProvider.SAFARICOM.getValue()) {

                                strResponse = "You will be prompted by M-PESA for payment\nPaybill no: " + strSender + "\n" + "A/C: " + strAccountNo + "\n" + "Amount: KES " + strAmount + "\n";

                                String strOriginatorID = theUSSDRequest.getUSSDTraceID();
                                String strBeneficiaryMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());
                                String strReceiverDetails = strBeneficiaryMobileNo;

                                double dblAmount = Utils.stringToDouble(strAmount);

                                String strReference = strOriginatorID;


                                String strMemberName = theUSSDAPI.getUserFullName(String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                                String strTraceID = theUSSDRequest.getUSSDTraceID();

                                Thread worker = new Thread(() -> {
                                    PESAAPI thePESAAPI = new PESAAPI();

                                    thePESAAPI.pesa_C2B_Request(
                                            String.valueOf(theUSSDRequest.getUSSDMobileNo()),
                                            strMemberName,
                                            strTraceID,
                                            "USSD",

                                            strAccountNo,
                                            strAccountLabel,
                                            "MBANKING_SERVER",
                                            strReference,

                                            strBeneficiaryMobileNo,
                                            strMemberName,
                                            strAccountNo,

                                            dblAmount,
                                            "DEPOSIT");

                                });
                                worker.start();
                            } else {
                                strResponse = "Use the details below to pay via M-PESA\nPaybill no: " + strSender + "\n" + "A/C: " + strAccountNo + "\n" + "Amount: KES " + strAmount + "\n";
                            }

                            //End USSD.
                            theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");

                            /*Cont USSD
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_END, "NO",theArrayListUSSDSelectOption);
                              */
                            break;
                        }
                        case "NO": {
                            String strResponse = "Dear member, your " + theHeader + " request NOT confirmed. " + theHeader + " request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {
                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_AMOUNT.name());
                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            /*String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);*/


                            String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                            String strCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strAccountNo = hmAccountDetails.get("ac_no");
                            //String strAccountName = hmAccountDetails.get("ac_name");
                            String strAccountLabel = hmAccountDetails.get("ac_label");

                            String strResponse = "Confirm Deposit via M-PESA\n{Select a valid menu}\nPaybill no.: " + strSender + "\nAccount: " + strAccountLabel + "-" + strAccountNo + "\nAmount: KES " + strAmount + "\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                    }
                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_AccountsDeposit() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = theHeader + "\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_AccountsDeposit() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccounts(USSDRequest theUSSDRequest, String theParam, String strCustomerHeader, String strAccountsHeader) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.DEPOSIT_CUSTOMER,
                            USSDAPIConstants.AccountType.FOSA,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.DEPOSIT_CUSTOMER,
                            USSDAPIConstants.AccountType.BOSA,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);

                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.DEPOSIT_CUSTOMER,
                            USSDAPIConstants.AccountType.BUSINESS,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);

                    break;
                }

                case "PAY_LOAN": {

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.LOAN,
                            AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN, AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccounts() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsWithType(USSDRequest theUSSDRequest, String theParam, String strAccountsHeader, String strCustomerIdentifier) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DEPOSIT_MENU.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {


                    /*theUSSDResponse=displayMenu_FOSA_Deposit_Accounts(theUSSDRequest,
                            theParam,
                            strAccountsHeader,
                            strCustomerIdentifier,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);*/
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.FOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BOSA,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);
                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BUSINESS,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.DEPOSIT_ACCOUNT, AppConstants.USSDDataType.DEPOSIT_END);
                    break;
                }

                case "PAY_LOAN": {

                    theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader,
                            "CUSTOMER_NO", strCustomerIdentifier,
                            AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                            AppConstants.USSDDataType.LOAN_REPAYMENT_END);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccounts() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }
}
