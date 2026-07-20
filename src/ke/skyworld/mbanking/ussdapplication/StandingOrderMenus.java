package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.authentication_manager.MobileBankingCryptography;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.data_formatting.Converter;
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
import ke.skyworld.mbanking.pesaapi.PESAAPI;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import static ke.skyworld.mbanking.ussdapplication.AppConstants.strSACCOProductName;

public interface StandingOrderMenus {

    default USSDResponse displayMenu_StandingOrderMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "VIEW_STOS", "1: View STOs");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "CREATE_STOS", "2: Create STO");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "CANCEL_STO", "3: Cancel STO");

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_StandingOrderMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_StandingOrder(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Standing Orders";

        try {

            String strUSSDDataType = theUSSDRequest.getUSSDDataType();

            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MY_ACCOUNT_MENU.getValue())) {
                theUSSDResponse = displayMenu_StandingOrderMenu(theUSSDRequest, strHeader);

            } else { //MY_ACCOUNT_MENU

                String strMY_ACCOUNT_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_MENU.name());

                switch (strMY_ACCOUNT_MENU) {

                    case "VIEW_STOS": {
                        theUSSDResponse = displayMenu_AllSTOsMenu(theUSSDRequest, "Standing Orders");
                        break;
                    }

                    case "CREATE_STOS": {
                        theUSSDResponse = displayMenu_StandingOrderCreate(theUSSDRequest, "MENU");
                        break;
                    }

                    default: {
                        theUSSDResponse = displayMenu_StandingOrderMenu(theUSSDRequest, strHeader + "\n{Invalid Option}\n");
                        break;
                    }

                }
            }


            /*switch (theParam) {
                case "MENU": {
                    theUSSDResponse = displayMenu_StandingOrderMenu(theUSSDRequest, strHeader);
                    break;
                }

            }*/

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_StandingOrders() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_AllSTOsMenu(USSDRequest theUSSDRequest, String theHeader){
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {

            FlexicoreArrayList allSTOs = (FlexicoreArrayList) Repository.selectWhereOrderBy(
                    StringRefs.SENTINEL,
                    "tmp.tmp_sto_details",
                    new FilterPredicate("mobile_number = :mobile_number"),
                    "sto_id DESC",
                    new FlexicoreHashMap().addQueryArgument(":mobile_number", String.valueOf(theUSSDRequest.getUSSDMobileNo()))
            ).getData();

            if(allSTOs == null || allSTOs.isEmpty()){

                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader+"\nSorry, no STOs found");
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_VIEW, "NO", theArrayListUSSDSelectOption);

            }else{

                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

                int intOptionMenu = 1;
                for (FlexicoreHashMap accountMap : allSTOs) {
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), accountMap.getStringValue("sto_id"), intOptionMenu + ": " + accountMap.getStringValue("sto_name"));
                    intOptionMenu++;
                }

                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_VIEW, "NO", theArrayListUSSDSelectOption);
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.getStandingOrderCreationConfirmation() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse displayMenu_AllSTOs(USSDRequest theUSSDRequest, String theParam){
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Standing Orders";

        try {

            switch (theParam){
                case "VIEW":{

                    String strViewOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_VIEW.name());
                    if(!strViewOption.isEmpty()){

                        FlexicoreHashMap allSTOs = Repository.selectWhere(
                                StringRefs.SENTINEL,
                                "tmp.tmp_sto_details",
                                new FilterPredicate("sto_id = :sto_id"),
                                new FlexicoreHashMap().addQueryArgument(":sto_id", strViewOption)
                        ).getSingleRecord();

                        String mobileNumber = allSTOs.getStringValue("mobile_number");
                        String stoName = allSTOs.getStringValue("sto_name");
                        String sourceAccountName = allSTOs.getStringValue("source_account_name");
                        String sourceAccountNumber = allSTOs.getStringValue("source_account_number");
                        String destinationAccountName = allSTOs.getStringValue("destination_account_name");
                        String destinationAccountNumber = allSTOs.getStringValue("destination_account_number");
                        String stoNextRunDate = allSTOs.getStringValue("sto_next_run_date");
                        String amount = allSTOs.getStringValue("amount");

                        String strFormattedAmount = Utils.formatDouble(amount, "#,##0.00");
                        String strFormattedDateTime = Utils.formatDate(stoNextRunDate, "dd/MM/yyyy", "dd MMM yyyy");

                        String strResponse = strHeader+"\n"
                                +"STO Name: "+stoName+"\n"
                                +"From A/C: "+sourceAccountName+" - "+sourceAccountNumber+"\n"
                                +"To A/C: "+destinationAccountName+" - "+destinationAccountNumber+"\n"
                                +"Amount: KES "+strFormattedAmount+"\n"
                                +"Next Run Date: "+strFormattedDateTime+"\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_END, "NO", theArrayListUSSDSelectOption);

                    }else{
                        theUSSDResponse = displayMenu_AllSTOsMenu(theUSSDRequest, "Standing Orders\n{Invalid Option}");
                    }

                    break;
                }
                case "END":{


                    break;
                }
                default:{
                    theUSSDResponse = displayMenu_AllSTOs(theUSSDRequest, strHeader+ "\n{Invalid Option}\n");
                    break;
                }
            }


        } catch (Exception e) {
            System.err.println("theAppMenus.getStandingOrderCreationConfirmation() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse displayMenu_StandingOrderCreate(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Create Standing Order";

        try {
            String strUSSDDataType = theUSSDRequest.getUSSDDataType();
            if (theParam.equalsIgnoreCase("MENU")) {

                /*FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.LOAN_REPAYMENT);
                String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                    return theUSSDResponse;

                }else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_REPAYMENT)) {
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                    return theUSSDResponse;
                }
*/

                String strCustomerHeader = strHeader + "\nSelect source member\n";
                String strAccountsHeader = strHeader + "\nSelect source account\n";

                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                        theParam, strCustomerHeader, strAccountsHeader,
                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_CUSTOMER,
                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT,
                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);

            } else {
                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {

                    case STANDING_ORDER_CREATION_CUSTOMER: {

                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_CUSTOMER.name());

                        if (strCustomerIdentifier.length() > 0) {
                            String strAccountsHeader = strHeader + " \nSelect source account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_AccountsIFT(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);
                        }

                        break;
                    }

                    case STANDING_ORDER_CREATION_SOURCE_ACCOUNT: {

                        String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT.name());
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_CUSTOMER.name());

                        if (strAccount.length() > 0) {

                            MemberRegisterResponse registerResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strAccount,
                                    RegisterConstants.MemberRegisterType.BLACKLIST);

                            if (registerResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {

                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);

                            } else {
                                theUSSDResponse = getStandingOrderCreationDestinationOptions(theUSSDRequest, strHeader + "\nSelect Destination Option:");
                            }

                        } else {
                            String strAccountsHeader = strHeader + " \n{Select a valid account}\n";

                            String strCustomerHeader = strHeader + " \nSelect member\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_AccountsIFT(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);
                            } else {
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);
                            }
                        }
                        break;
                    }


                    case STANDING_ORDER_CREATION_DESTINATION_OPTION: {
                        String strDestinationOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_OPTION.name());

                        switch (strDestinationOption) {
                            case "SAVINGS": {
                                String strCustomerHeader = strHeader + "\nSelect Member\n";
                                String strAccountsHeader = strHeader + "\nSelect Loan\n";

                                //TODO: make it like Funds Transfer
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_CUSTOMER,
                                        USSDAPIConstants.AccountType.ALL,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);

                                break;
                            }
                            case "LOAN": {

                                String strCustomerHeader = strHeader + "\nSelect Member\n";
                                String strAccountsHeader = strHeader + "\nSelect Loan\n";

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_CUSTOMER,
                                        USSDAPIConstants.AccountType.LOAN,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT,
                                        AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);

                                break;
                            }
                            default: {
                                theUSSDResponse = getStandingOrderCreationDestinationOptions(theUSSDRequest, strHeader + "\n{Invalid Option}\nSelect Destination Option:");
                                break;
                            }
                        }
                        break;
                    }

                    case STANDING_ORDER_CREATION_DESTINATION_ACCOUNT: {

                        String strDestinationOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_OPTION.name());

                        String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT.name());
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_CUSTOMER.name());

                        if (strAccount.length() > 0) {
                            String strResponse = strHeader + " \nEnter STO Name:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_STO_NAME, USSDConstants.USSDInputType.STRING, "NO");

                        } else {
                            String strAccountsHeader = strHeader + " \n{Select a valid account}\n";

                            String strCustomerHeader = strHeader + " \nSelect member\n";

                            switch (strDestinationOption) {
                                case "SAVINGS": {
                                    //TODO: make it like Funds Transfer
                                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                            AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_CUSTOMER,
                                            USSDAPIConstants.AccountType.BOSA,
                                            AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT,
                                            AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);

                                    break;
                                }
                                case "LOAN": {
                                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                            AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_CUSTOMER,
                                            USSDAPIConstants.AccountType.LOAN,
                                            AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT,
                                            AppConstants.USSDDataType.STANDING_ORDER_CREATION_END);

                                    break;
                                }
                            }
                        }

                        break;
                    }

                    case STANDING_ORDER_CREATION_STO_NAME: {

                        String strName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_STO_NAME.name());
                        String strResponse = strHeader + " \nEnter STO Start Date in the format (DD/MM/YYYY):";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.STANDING_ORDER_CREATION_START_DATE, USSDConstants.USSDInputType.STRING, "NO");

                        break;
                    }


                    case STANDING_ORDER_CREATION_START_DATE: {

                        String strStartDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_START_DATE.name());

                        if (AppUtils.isValidDate(strStartDate, "dd/MM/yyyy")) {
                            String strResponse = strHeader + "\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");

                        } else {
                            String strResponse = strHeader + " \n{Invalid Date}\nEnter STO Start Date in the format (DD/MM/YYYY):";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                    AppConstants.USSDDataType.STANDING_ORDER_CREATION_START_DATE, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }

                    case STANDING_ORDER_CREATION_AMOUNT: {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT.name());
                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");


                        double dblAvailableBalance = 0;
                        try {
                            dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                            dblAvailableBalance = dblAvailableBalance;
                        } catch (Exception e) {
                        }

                        String strOrgParametersXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                        Document document = XmlUtils.parseXml(strOrgParametersXML);

                        double dblMinimumAmount = Double.parseDouble(USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.INTERNAL_FUNDS_TRANSFER).getMinimum());
                        double dblMaximumAmount = Double.parseDouble(USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.INTERNAL_FUNDS_TRANSFER).getMaximum());

                        String strResponse = strHeader + "\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_PIN, USSDConstants.USSDInputType.STRING, "NO");

                        if (!strAmount.matches("^[1-9][0-9]*$")) {
                            strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (Double.parseDouble(strAmount) < dblMinimumAmount) {
                            strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(dblMinimumAmount, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }  else if (Double.parseDouble(strAmount) > dblMaximumAmount) {
                            strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(dblMaximumAmount, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                            strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to create an STO of KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }

                    case STANDING_ORDER_CREATION_PIN: {
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {
                            theUSSDResponse = getStandingOrderCreationConfirmation(theUSSDRequest, strHeader);
                        } else {
                            String strResponse = strHeader + "\n{Please enter a correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.STANDING_ORDER_CREATION_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }
                    case STANDING_ORDER_CREATION_CONFIRMATION: {
                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_CONFIRMATION.name());

                        switch (strConfirmation) {
                            case "YES": {
                                /*TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = theUSSDAPI.mobileMoneyWithdrawal(theUSSDRequest);
                                FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                                if (moneyOutWrapper.hasErrors()) {
                                    String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                                    strErrorMessage += moneyOutMap.getStringValue("display_message");
                                    System.err.println("StandingOrderMenusMenus.displayMenu_StandingOrder() - Response " + strErrorMessage);
                                }*/

                                {
                                    String strStartDate = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_START_DATE.name());
                                    String strStoName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_STO_NAME.name());
                                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT.name());
                                    String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT.name());
                                    HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                                    String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                                    String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                                    String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                                    String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                                    String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT.name());
                                    HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);

                                    String strToAccountLabel = hmToAccountDetails.get("ac_label");
                                    String strToAccountNumber = hmToAccountDetails.get("ac_no");

                                    String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");

                                    FlexicoreHashMap insertMap = new FlexicoreHashMap();
                                    insertMap.putValue("mobile_number", String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                                    insertMap.putValue("sto_name", strStoName);
                                    insertMap.putValue("source_account_name", strFromAccountLabel);
                                    insertMap.putValue("source_account_number", strFromAccountNumber);
                                    insertMap.putValue("destination_account_name", strToAccountLabel);
                                    insertMap.putValue("destination_account_number", strToAccountNumber);
                                    insertMap.putValue("sto_next_run_date", strStartDate);
                                    insertMap.putValue("amount", strAmount);
                                    insertMap.putValue("date_created", DateTime.getCurrentDateTime());

                                    Repository.insertAutoIncremented(StringRefs.SENTINEL, "tmp.tmp_sto_details", insertMap);
                                }

                                String strResponse = "Dear member, your " + strHeader + " request has been received successfully.\n";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_CREATION_END, "NO", theArrayListUSSDSelectOption);

                                break;
                            }
                            case "NO": {
                                String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Standing Order Creation request NOT COMPLETED.\n";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_CREATION_END, "NO", theArrayListUSSDSelectOption);
                                break;
                            }
                            default: {
                                theUSSDResponse = getStandingOrderCreationConfirmation(theUSSDRequest, strHeader + "\n{Invalid Option}\n");
                                break;
                            }
                        }

                        break;
                    }

                    default: {
                        System.err.println("theAppMenus.displayMenu_StandingOrder() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Pay Loan\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_CREATION_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }

            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_StandingOrder() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


    default USSDResponse getStandingOrderCreationDestinationOptions(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "SAVINGS", "1: Savings");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "LOAN", "2: Loan");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "BANK", "3: Bank");

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_StandingOrderMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getStandingOrderCreationConfirmation(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {

            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT.name());
            String strOrigiAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_AMOUNT.name());
            //String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WITHDRAWAL_ACCOUNT.name());
            //String strResponse =  "Confirm Cash Withdrawal\nAmount: KES "+strAmount+"\nAccount: " + strAccount + "\n";

            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_SOURCE_ACCOUNT.name());
            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
            String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
            String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

            String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.STANDING_ORDER_CREATION_DESTINATION_ACCOUNT.name());
            HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);

            String strToAccountLabel = hmToAccountDetails.get("ac_label");
            String strToAccountNumber = hmToAccountDetails.get("ac_no");

            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");

            String strResponse = "Confirm "+theHeader+"\nFrom A/C: " + strFromAccountLabel + "-" + strFromAccountNumber
                                 + "\nTo A/C: " + strToAccountLabel + "-" + strToAccountNumber
                                 + "\n" + "Amount: KES " + strFormattedAmount
                                 + "\n" + "Frequency every 1 DAY of every Month\n";

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.STANDING_ORDER_CREATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.getStandingOrderCreationConfirmation() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }
    
}
