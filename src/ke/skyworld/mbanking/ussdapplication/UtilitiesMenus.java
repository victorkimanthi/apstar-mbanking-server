package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
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
import ke.skyworld.sp.manager.SPManager;
import ke.skyworld.sp.manager.SPManagerConstants;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public interface UtilitiesMenus {

    default USSDResponse displayMenu_UtilitiesMenu(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(SPManagerConstants.ProviderAccountType.UTILITY_CODE);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "Buy Airtime", "1: Buy Airtime");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "M-PESA Float Purchase", "2: M-PESA Float Purchase");


           /* for(APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts){
                String strOptionMenu = String.valueOf(intOptionMenu);
                String strProviderAccountCode = serviceProviderAccount.getProviderAccountCode();
                String strProviderAccountIdentifier = serviceProviderAccount.getProviderAccountIdentifier();
                String strProviderAccountLongTag = serviceProviderAccount.getProviderAccountLongTag();
                String strOptionDisplayText = strOptionMenu+": "+strProviderAccountLongTag;
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strProviderAccountIdentifier, strOptionDisplayText);
                intOptionMenu = intOptionMenu+1;

            }*/

            for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                int intOptionMenu = llSPAAccounts.indexOf(serviceProviderAccount) + 1 ;
                intOptionMenu = intOptionMenu ;

                String strProviderAccountIdentifier = serviceProviderAccount.getProviderAccountIdentifier();
                String strProviderCode = serviceProviderAccount.getProviderCode();
                String strProviderAccountCode = serviceProviderAccount.getProviderAccountCode();
                String strProviderAccountName = serviceProviderAccount.getProviderAccountName();
                String strProviderAccountType = serviceProviderAccount.getProviderAccountType();
                String strProviderAccountTypeTag = serviceProviderAccount.getProviderAccountTypeTag();
                String strProviderAccountLongTag = serviceProviderAccount.getProviderAccountLongTag();
                String dblMinTransactionAmount = serviceProviderAccount.getMinTransactionAmount();
                String dblMaxTransactionAmount = serviceProviderAccount.getMaxTransactionAmount();

                HashMap<String, String> hmProviderAccount = new HashMap<>();
                hmProviderAccount.put("code", strProviderAccountCode);
                //hmProviderAccount.put("name",strProviderAccountName);
                hmProviderAccount.put("identifier", strProviderAccountIdentifier);
                //hmProviderAccount.put("provider_code",strProviderCode);
                hmProviderAccount.put("type", strProviderAccountType);
                hmProviderAccount.put("type_tag", strProviderAccountTypeTag);
                hmProviderAccount.put("long_tag", strProviderAccountLongTag);
                //hmProviderAccount.put("min_amount",dblMinTransactionAmount);
                //hmProviderAccount.put("max_amount",dblMaxTransactionAmount);

                String strOptionMenu = String.valueOf(intOptionMenu);
                String strOptionValue = Utils.serialize(hmProviderAccount);

                String strOptionDisplayText = strOptionMenu + ": " + strProviderAccountLongTag;

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
            }
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.UTILITIES_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_UtilitiesMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_Utilities(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {
            String strUSSDDataType = theUSSDRequest.getUSSDDataType();

            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.getValue())) {
                String strHeader = "Utilities";
                theUSSDResponse = displayMenu_UtilitiesMenu(theUSSDRequest, theParam, strHeader);
            } else {

                String strUTILITIES_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.UTILITIES_MENU.name());

                if (strUTILITIES_MENU.equals("Buy Airtime")) {
                    theUSSDResponse = theAppMenus.displayMenu_Etopup(theUSSDRequest, theParam);
                }else if(strUTILITIES_MENU.equals("M-PESA Float Purchase")){
                    theUSSDResponse = theAppMenus.displayMenu_MPESA_Float_Purchase(theUSSDRequest, theParam);
                }

                else if (strUTILITIES_MENU.length() > 0) {
                    theUSSDResponse = theAppMenus.displayMenu_PayBill(theUSSDRequest, theParam);
                } else {
                    String strHeader = "Utilities\n{Select a valid menu}";
                    theUSSDResponse = displayMenu_UtilitiesMenu(theUSSDRequest, theParam, strHeader);
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Utilities() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_Etopup(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        String strHeader = "Buy Airtime";
        try {

            {
                String strMNOOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_MNO_OPTION.name());

                if (strMNOOption != null && !strMNOOption.isEmpty()) {

                    String strMNOName = switch (strMNOOption) {
                        case "SAFARICOM" -> "Safaricom";
                        case "AIRTEL" -> "Airtel";
                        case "TELKOM" -> "Telkom";
                        default -> "";
                    };

                    strHeader = strHeader + " (" + strMNOName + ")";
                }
            }

            switch (theParam) {
                case "MENU": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BALANCE_ENQUIRY);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;

                    } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BUY_AIRTIME)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    theUSSDResponse = getEtopupOptionMenu(theUSSDRequest, strHeader + "\n");

                    break;
                }

                case "MNO_OPTION": {

                    String strMNOOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_MNO_OPTION.name());
                    switch (strMNOOption) {
                        case "SAFARICOM", "AIRTEL", "TELKOM" -> {
                            String strCustomerHeader = strHeader + " \nSelect member\n";
                            String strAccountsHeader = strHeader + " \nSelect account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.ETOPUP_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.ETOPUP_ACCOUNT, AppConstants.USSDDataType.ETOPUP_END);
                        }
                        default ->
                                theUSSDResponse = getEtopupOptionMenu(theUSSDRequest, strHeader + "\n{Invalid Option}\n");
                    }

                    break;
                }

                case "CUSTOMER": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect Account\n";

                        theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier, AppConstants.USSDDataType.ETOPUP_ACCOUNT, AppConstants.USSDDataType.ETOPUP_END);

                    } else {

                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";

                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.ETOPUP_CUSTOMER,
                                USSDAPIConstants.AccountType.WITHDRAWABLE,
                                AppConstants.USSDDataType.ETOPUP_ACCOUNT, AppConstants.USSDDataType.ETOPUP_END);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_CUSTOMER.name());

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_ACCOUNT.name());

                    if (strAccount.length() > 0) {

                        MemberRegisterResponse registerResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strAccount,
                                RegisterConstants.MemberRegisterType.BLACKLIST);

                        if (registerResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {

                            String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strFullHeader = strHeader + "\nSelect Buy Airtime option\n";
                            theUSSDResponse = getEtopupToOptionMenu(theUSSDRequest, strFullHeader);
                        }

                    } else {
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";

                        String strCustomerHeader = strHeader + " \nSelect member\n";

                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                    strCustomerIdentifier,
                                    AppConstants.USSDDataType.ETOPUP_ACCOUNT,
                                    AppConstants.USSDDataType.ETOPUP_END);
                        } else {
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.ETOPUP_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.ETOPUP_ACCOUNT,
                                    AppConstants.USSDDataType.ETOPUP_END);
                        }
                    }
                    break;
                }
                case "TO_OPTION": {
                    String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO_OPTION.name());
                    if (strToOption.equalsIgnoreCase("MY_NUMBER")) {
                        String strResponse = strHeader + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                        String strFullHeader = strHeader + "\nEnter Other Mobile No.\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strFullHeader, AppConstants.USSDDataType.ETOPUP_TO, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strFullHeader = strHeader + "\n{Select a valid menu}\nSelect Buy Airtime option\n";
                        theUSSDResponse = getEtopupToOptionMenu(theUSSDRequest, strFullHeader);
                    }
                    break;
                }
                case "TO": {
                    String strOtherMobileNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO.name());
                    strOtherMobileNo = AppUtils.sanitizePhoneNumber(strOtherMobileNo);

                    if (!strOtherMobileNo.equalsIgnoreCase("INVALID MOBILE NUMBER") /*|| !strOtherMobileNo.matches("^254((7)[0-2][0-9])|(74[0-3])|(74[5-6])|(748)|(75[7-9])|(76[8-9])|(79[0-9]))[0-9]{6}$")*/) {
                        String strResponse = strHeader + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strFullHeader = strHeader + "\n{Enter a valid mobile number}\nEnter Other Mobile No.\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strFullHeader, AppConstants.USSDDataType.ETOPUP_TO, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_AMOUNT.name());

                    String strFundsTransferXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                    Document document = XmlUtils.parseXml(strFundsTransferXML);

                    String strMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.AIRTIME_PURCHASE).getMinimum();
                    String strMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.AIRTIME_PURCHASE).getMaximum();

                    double dblMinimum = Double.parseDouble(strMinimum);
                    double dblMaximum = Double.parseDouble(strMaximum);

                    String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_ACCOUNT.name());
                    HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                    String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                    String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                    String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                    String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                    double dblAvailableBalance = 0;
                    try {
                        dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                    } catch (Exception e) {
                    }

                    double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "AIRTIME_PURCHASE");

                    dblMaximum = Math.min(dblMaximum, dblDailyLimitRemainingAmount);

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (Double.parseDouble(strAmount) < dblMinimum) {
                        String strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strMinimum, "#,##0.00") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (dblDailyLimitRemainingAmount <= 0) {
                        String strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);
                    } else if (Double.parseDouble(strAmount) > dblMaximum) {
                        String strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strMaximum, "#,##0.00") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                        String strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to Buy Airtime of KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strResponse = strHeader + "\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        //String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                       /* String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_AMOUNT.name());

                        String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.AIRTIME_PURCHASE.getValue(),
                                Double.parseDouble(strAmount));

                        String strCharge = "";
                        if (chargesWrapper.hasErrors()) {
                            strCharge = "";
                        } else {
                            strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        }
                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");


                        String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO_OPTION.name());

                        if (strToOption.equalsIgnoreCase("OTHER_NUMBER")) {
                            strMobileNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_TO.name());
                        }

                        //String strResponse =  "Confirm "+strHeader + "\n" + "Amount: KES "+strAmount+"\n"; //Without Account No
                        String strResponse = "Confirm " + strHeader + "\n"
                                             + "Paying Account: " + strSourceAccountLabel + "-" + strSourceAccountNo +"\n"
                                             + "Mobile No: " + strMobileNo
                                             + "\nAmount: KES " + strAmount + strCharge + "\n"; //With Account No

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.ETOPUP_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_CONFIRMATION.name());
                    if (strConfirmation.equalsIgnoreCase("YES")) {

                        TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = theUSSDAPI.airtimePurchase(theUSSDRequest);
                        FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                        if (moneyOutWrapper.hasErrors()) {
                            String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                            strErrorMessage += moneyOutMap.getStringValue("display_message");
                            System.err.println("theAppMenus.airtimePurchase() - Response " + strErrorMessage);
                        }

                        String strResponse = moneyOutMap.getStringValue("display_message");

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);

                    } else if (strConfirmation.equalsIgnoreCase("NO")) {
                        String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. " + strHeader + "  request NOT COMPLETED.\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        /*String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.ETOPUP_AMOUNT.name());


                        String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.AIRTIME_PURCHASE.getValue(),
                                Double.parseDouble(strAmount));

                        String strCharge = "";
                        if (chargesWrapper.hasErrors()) {
                            strCharge = "";
                        } else {
                            strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        }

                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strResponse = "Confirm " + strHeader + "\n{Select a valid menu}\nPaying A/C: " + strSourceAccountName + "-" + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge + "\n"; //With Account No

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                    }

                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_Etopup() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Etopup() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getEtopupOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader+"\nSelect Buy Airtime Option:");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "SAFARICOM", "1: Safaricom");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "AIRTEL", "2: Airtel");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "TELKOM", "3: Telkom");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_MNO_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getEtopupOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_PayBill(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        String strUtilityProviderAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.UTILITIES_MENU.name());

        HashMap<String, String> hmUtilityAccountDetails = Utils.toHashMap(strUtilityProviderAccountDetails);

        String strToSPProviderAccountCode = hmUtilityAccountDetails.get("code");
        String strToAccountIdentifier = hmUtilityAccountDetails.get("identifier");
        String strToAccountType = hmUtilityAccountDetails.get("type");
        String strToAccountNaming = hmUtilityAccountDetails.get("type_tag");
        String strToBillerName = hmUtilityAccountDetails.get("long_tag");

        String strHeader = "Pay for " + strToBillerName;
        try {
            switch (theParam) {
                case "MENU": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.UTILITY_PAYMENTS);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Utility Payment\n" + getServiceStatusDetails.getStringValue("display_message"));
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;

                    } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.UTILITY_PAYMENTS)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Utility Payment\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    //USE MENUs
                    theUSSDResponse = displayMenu_Paybill_Maintain_Accounts(theUSSDRequest, theParam);

                    break;
                }

                case "BILLER_ACCOUNT": {

                    String strMenuOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT.name());

                    String strAction = "";
                    if (!strMenuOption.isEmpty()) {
                        HashMap<String, String> hmMenuOption = Utils.toHashMap(strMenuOption);
                        strAction = hmMenuOption.get("ACTION");
                    }

                    switch (strAction) {
                        case "CHOICE": {

                            String strCustomerHeader = strHeader + " \nSelect source member\n";
                            String strAccountsHeader = strHeader + " \nSelect paying account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.PAY_BILL_BILLER_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT, AppConstants.USSDDataType.PAY_BILL_END);


                            //theUSSDResponse = GeneralMenus.displayMenu_BankAccounts(theUSSDRequest, theParam, strHeader2, USSDAPIConstants.AccountType.FOSA, AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT);
                            break;
                        }
                        case "ADD": {
                            String strResponse = "Add " + strToBillerName + "\nEnter " + strToAccountNaming + ":";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            break;
                        }
                        case "REMOVE": {
                            String strHeader2 = "Remove " + strToBillerName + "\nSelect " + strToAccountNaming + " to Remove:";

                            theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_REMOVE, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader2, USSDConstants.Condition.NO);
                            break;
                        }
                        default: {
                            String strHeader2 = "Pay for " + strToBillerName + "\n{Select a VALID MENU}:";
                            theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader2, USSDConstants.Condition.YES);
                            break;
                        }
                    }
                    break;
                }

                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_BILLER_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect paying account\n";

                        theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier, AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT, AppConstants.USSDDataType.PAY_BILL_END);

                    } else {
                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect paying account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                AppConstants.USSDDataType.PAY_BILL_BILLER_CUSTOMER, USSDAPIConstants.AccountType.WITHDRAWABLE, AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT, AppConstants.USSDDataType.PAY_BILL_END);
                    }

                    break;
                }

                case "FROM_ACCOUNT": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_BILLER_CUSTOMER.name());

                    String strFromAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT.name());

                    if (strFromAccount.length() > 0) {

                        MemberRegisterResponse registerResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strFromAccount,
                                RegisterConstants.MemberRegisterType.BLACKLIST);

                        if (registerResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {

                            String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_END, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strResponse = strHeader + "\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }

                    } else {
                        String strAccountsHeader = strHeader + " \n{Select a valid paying account}\n";
                        String strCustomerHeader = strHeader + " \nSelect source member\n";

                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                    strCustomerIdentifier,
                                    AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.PAY_BILL_END);
                        } else {
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.PAY_BILL_BILLER_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.PAY_BILL_END);
                        }
                    }
                    break;
                }
                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_AMOUNT.name());

                    if (strAmount.matches("^[1-9][0-9]*$")) {
                        String strResponse = strHeader + "\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                        String strFundsTransferXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                        Document document = XmlUtils.parseXml(strFundsTransferXML);

                        String strPayBillMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.PAY_BILL).getMinimum();
                        String strPayBillMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.PAY_BILL).getMaximum();

                        double dblPayBillMinimum = Double.parseDouble(strPayBillMinimum);
                        double dblPayBillMaximum = Double.parseDouble(strPayBillMaximum);

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        /*String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("avail_bal");*/

                        double dblAvailableBalance = 0;
                        try {
                            dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                        } catch (Exception e) {
                        }

                        double dblAmountEntered = Double.parseDouble(strAmount);

                        double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BILL_PAYMENT");

                        dblPayBillMaximum = Math.min(dblPayBillMaximum, dblDailyLimitRemainingAmount);

                        if (dblAmountEntered < dblPayBillMinimum) {
                            strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strPayBillMinimum, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (dblDailyLimitRemainingAmount <= 0) {
                            strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_END, "NO", theArrayListUSSDSelectOption);
                        } else if (dblAmountEntered > dblPayBillMaximum) {
                            strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strPayBillMaximum, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                            strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to " + strHeader + " of KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                    } else {
                        String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strBillAccountNumberHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT.name());

                        HashMap<String, String> hmAccount = Utils.toHashMap(strBillAccountNumberHashMap);
                        String strAccountID = hmAccount.get("ACCOUNT_ID");
                        String strAccountName = hmAccount.get("ACCOUNT_NAME");
                        String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);
                      /*  String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        //String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_AMOUNT.name());

                        String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BILL_PAYMENT.getValue(),
                                Double.parseDouble(strAmount));

                        String strCharge = "";
                        if (chargesWrapper.hasErrors()) {
                            strCharge = "";
                        } else {
                            strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        }

                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strResponse = "Confirm " + strHeader + "\n\nBill " + strToAccountNaming + ": " + strAccountIdentifier + "\nName: " + strAccountName + "\nPaying A/C: " + strSourceAccountLabel + "-" + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge + "\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_CONFIRMATION.name());
                    if (strConfirmation.equalsIgnoreCase("YES")) {

                        TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = theUSSDAPI.utilityPayment(theUSSDRequest);
                        FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                        if (moneyOutWrapper.hasErrors()) {
                            String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                            strErrorMessage += moneyOutMap.getStringValue("display_message");
                            System.err.println("theAppMenus.utilityPayment() - Response " + strErrorMessage);
                        }

                        String strResponse = moneyOutMap.getStringValue("display_message");

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_END, "NO", theArrayListUSSDSelectOption);

                    } else if (strConfirmation.equalsIgnoreCase("NO")) {
                        String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. " + strHeader + "  request NOT COMPLETED.\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WITHDRAWAL_END, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strBillAccountNumberHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT.name());

                        HashMap<String, String> hmAccount = Utils.toHashMap(strBillAccountNumberHashMap);
                        String strAccountID = hmAccount.get("ACCOUNT_ID");
                        String strAccountName = hmAccount.get("ACCOUNT_NAME");
                        String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_FROM_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        /*String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        //String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_AMOUNT.name());

                        String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BILL_PAYMENT.getValue(),
                                Double.parseDouble(strAmount));

                        String strCharge = "";
                        if (chargesWrapper.hasErrors()) {
                            strCharge = "";
                        } else {
                            strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        }

                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strResponse = "Confirm " + strHeader + "\n{Select a valid menu}\n\nBill " + strToAccountNaming + ": " + strAccountIdentifier + "\nName: " + strAccountName + "\nPaying A/C: " + strSourceAccountLabel + "-" + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge + "\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                    }

                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_PayBill() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_PayBill() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_Paybill_Maintain_Accounts(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {

            AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());
            String strUtilityProviderAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.UTILITIES_MENU.name());
            System.out.println("strUtilityProviderAccountDetails : " + strUtilityProviderAccountDetails);

            HashMap<String, String> hmUtilityAccountDetails = Utils.toHashMap(strUtilityProviderAccountDetails);

            String strToSPProviderAccountCode = hmUtilityAccountDetails.get("code");
            String strToAccountIdentifier = hmUtilityAccountDetails.get("identifier");
            String strToAccountType = hmUtilityAccountDetails.get("type");
            String strToAccountNaming = hmUtilityAccountDetails.get("type_tag");
            String strToBillerName = hmUtilityAccountDetails.get("long_tag");


            switch (ussdDataType) {
                case UTILITIES_MENU: {
                    String strHeader = "Pay for " + strToBillerName + "\nSelect " + strToAccountNaming + ":";

                    theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);
                    break;
                }
                case PAY_BILL_MAINTENANCE_ACCOUNT_ACCOUNT: {

                    String theAccountNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_ACCOUNT.name());

                    if (theAccountNo.matches("^\\d{4,24}$")) { //4 - 24 Digits
                        String strResponse = "Add " + strToBillerName + "\nEnter " + strToAccountNaming + " NAME:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_NAME, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strResponse = "Add " + strToBillerName + "\n{Enter a VALID " + strToAccountNaming + "}:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case PAY_BILL_MAINTENANCE_ACCOUNT_NAME: {
                    //ADD Account
                    String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                    String strAccountNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_ACCOUNT.name());
                    String strAccountName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_NAME.name());

                    try {
                        String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                        SPManager spManager = new SPManager(strIntegritySecret);
                        spManager.createUserSavedAccount(SPManagerConstants.UserIdentifierType.MSISDN, strMobileNo, strToSPProviderAccountCode, SPManagerConstants.AccountIdentifierType.ACCOUNT_NO, strAccountNo, strAccountName);

                    } catch (Exception e) {
                        System.err.println("theAppMenus.displayMenu_Paybill_Maintain_Accounts() ERROR : " + e.getMessage());
                    }

                    String strHeader = "Pay for " + strToBillerName + "\nSelect " + strToAccountNaming + ":";
                    theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);
                    break;
                }
                case PAY_BILL_MAINTENANCE_ACCOUNT_REMOVE: {
                    //REMOVE Account

                    String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                    String strAccountHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_REMOVE.name());

                    if (!strAccountHashMap.isEmpty()) {
                        try {

                            HashMap<String, String> hmAccount = Utils.toHashMap(strAccountHashMap);
                            String strAccountID = hmAccount.get("ACCOUNT_ID");
                            //String strAccountName = hmAccount.get("ACCOUNT_NAME");
                            //String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");
                            String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                            SPManager spManager = new SPManager(strIntegritySecret);
                            spManager.removeUserSavedAccountsByAccountId(strAccountID);

                        } catch (Exception e) {
                            System.err.println("theAppMenus.displayMenu_Paybill_Maintain_Accounts() ERROR : " + e.getMessage());
                        }


                        String strHeader = "Pay for " + strToBillerName + "\nSelect " + strToAccountNaming + ":";
                        theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);

                    } else {
                        String strHeader = "Remove " + strToBillerName + "\n{Select a VALID MENU}:";
                        theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_MAINTENANCE_ACCOUNT_REMOVE, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.NO);
                    }

                    break;
                }
                default: {

                    String strHeader = "Pay for " + strToBillerName + "\n{Select a VALID " + strToAccountNaming + "}:";

                    System.err.println("theAppMenus.displayMenu_Paybill_Maintain_Accounts() UNKNOWN PARAM ERROR : strUSSDDataType = " + ussdDataType.name());
                    theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.PAY_BILL_BILLER_ACCOUNT, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Paybill_Maintain_Accounts() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getEtopupToOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "MY_NUMBER", "1: My Phone Number");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "OTHER_NUMBER", "2: Other Phone Number");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.ETOPUP_TO_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getWithdrawalOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_MPESA_Float_Purchase(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        String strHeader = "M-PESA Float Purchase";
        try{
            switch (theParam) {
                case "MENU": {
             
                    String strCustomerHeader = strHeader + " \nSelect member\n";
                    String strAccountsHeader = strHeader + " \nSelect account\n";

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CUSTOMER,
                            USSDAPIConstants.AccountType.WITHDRAWABLE,
                            AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END);
                   
                    break;
                }

                case "CUSTOMER": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect Account\n";

                        theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END);

                    } else {

                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";

                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CUSTOMER,
                                USSDAPIConstants.AccountType.WITHDRAWABLE,
                                AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CUSTOMER.name());

                    String strAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT.name());

                    if (strAccount.length() > 0) {

                        MemberRegisterResponse registerResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strAccount,
                                RegisterConstants.MemberRegisterType.BLACKLIST);

                        if (registerResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {

                            String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END, "NO", theArrayListUSSDSelectOption);

                        } else {

                            String strResponse = strHeader+"\nEnter Agent No.:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NO, USSDConstants.USSDInputType.STRING,"NO");
                        }

                    } else {
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";

                        String strCustomerHeader = strHeader + " \nSelect member\n";

                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                    strCustomerIdentifier,
                                    AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT,
                                    AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END);
                        } else {
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT,
                                    AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END);
                        }
                    }
                    break;
                }

                case "AGENT_NO": {
                    String strAgentNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NO.name());

                    if (strAgentNo.length() > 0){
                        String strResponse = strHeader+"\nEnter Agent Name:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NAME, USSDConstants.USSDInputType.STRING,"NO");

                    }else{
                        String strHeader2 = strHeader + " \n{Select a valid Agent No.}\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strHeader2, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NO, USSDConstants.USSDInputType.STRING,"NO");
                    }
                    break;
                }
                case "AGENT_NAME": {
                    String strAgentName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NAME.name());

                    if (strAgentName.length() > 0){
                        String strResponse = strHeader+"\nEnter Store No.:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_STORE_NO, USSDConstants.USSDInputType.STRING,"NO");

                    }else{
                        String strHeader2 = strHeader + " \n{Select a valid Agent Name}\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strHeader2, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NAME, USSDConstants.USSDInputType.STRING,"NO");
                    }
                    break;
                }
                case "STORE_NO": {
                    String strStoreNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_STORE_NO.name());

                    if (strStoreNumber.length() > 0){
                        String strResponse = strHeader+"\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT, USSDConstants.USSDInputType.STRING,"NO");

                    }else{
                        String strHeader2 = strHeader + " \n{Select a valid Store No.}\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strHeader2, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_STORE_NO, USSDConstants.USSDInputType.STRING,"NO");
                    }
                    break;
                }
                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT.name());

                    String strMinimum = "10";
                    String strMaximum = "15";

                    if (!strAmount.matches("^[1-9][0-9]*$")) {
                        String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (Double.parseDouble(strAmount) < Double.parseDouble(strMinimum)) {
                        String strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strMinimum, "#,###.##") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (Double.parseDouble(strAmount) > Double.parseDouble(strMaximum)) {
                        String strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strMaximum, "#,###.##") + "}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strResponse = strHeader + "\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_PIN.name());
                    if(strLoginPIN.equals(strPIN)){

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT.name());
                        String strAgentNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NO.name());
                        String strAgentName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NAME.name());
                        String strStoreNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_STORE_NO.name());
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT.name());
                        strAmount = Utils.formatDouble(strAmount, "#,###");

                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        /*String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        //String strResponse =  "Confirm "+strHeader + "\n" + "Amount: KES "+strAmount+"\n"; //Without Account No
                        String strResponse =  "Confirm "+strHeader + "\n"
                                              + "Paying A/C: " + strSourceAccountNo + "\n"
                                              + "Agent No: " + strAgentNumber + "\n"
                                              + "Agent Name: " + strAgentName + "\n"
                                              + "Store No: " + strStoreNumber + "\n"
                                              + "Amount: KES "+strAmount+"\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CONFIRMATION, "NO",theArrayListUSSDSelectOption);

                    }else{
                        String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_PIN, USSDConstants.USSDInputType.STRING,"NO");
                    }

                    break;
                }
                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CONFIRMATION.name());
                    if(strConfirmation.equalsIgnoreCase("YES")){
                        String  strResponse = "Dear member, your " +strHeader+ " request has been received successfully. Please wait shortly as it's being processed.";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END, "NO",theArrayListUSSDSelectOption);

                    }else if(strConfirmation.equalsIgnoreCase("NO")){
                        String strResponse = "Dear member, your " +strHeader+ " request NOT confirmed. " +strHeader+ "  request NOT COMPLETED.\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END, "NO",theArrayListUSSDSelectOption);
                    }else{
                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_ACCOUNT.name());
                        String strAgentNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NO.name());
                        String strAgentName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AGENT_NAME.name());
                        String strStoreNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_STORE_NO.name());
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_AMOUNT.name());
                        strAmount = Utils.formatDouble(strAmount, "#,###");

                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountDetails);

                        /*String strSourceAccountNo = hmAccountDetails.get("number");
                        String strSourceAccountName = hmAccountDetails.get("name");
                        String strSourceAccountTypeName = hmAccountDetails.get("type_name");
                        String strSourceAccountLabel = hmAccountDetails.get("label");*/

                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                        String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                        String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                        //String strResponse =  "Confirm "+strHeader + "\n" + "Amount: KES "+strAmount+"\n"; //Without Account No
                        String strResponse =  "Confirm "+strHeader + "\n"
                                              + "Paying Account No: " + strSourceAccountNo + "\n"
                                              + "Agent No.: " + strAgentNumber + "\n"
                                              + "Agent Name: " + strAgentName + "\n"
                                              + "Store No.: " + strStoreNumber + "\n"
                                              + "Amount: KES "+strAmount+"\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_CONFIRMATION, "NO",theArrayListUSSDSelectOption);
                    }

                    break;
                }
                default:{
                    System.err.println("theAppMenus.displayMenu_MPESA_Float_Purchase() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = strHeader+"\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MPESA_FLOAT_PURCHASE_END, "NO",theArrayListUSSDSelectOption);

                    break;
                }
            }

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_MPESA_Float_Purchase() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

}
