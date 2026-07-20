package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.register.MemberRegisterResponse;
import ke.skyworld.lib.mbanking.register.RegisterConstants;
import ke.skyworld.lib.mbanking.register.RegisterProcessor;
import ke.skyworld.lib.mbanking.ussd.*;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import ke.skyworld.sp.manager.SPManager;
import ke.skyworld.sp.manager.SPManagerConstants;
import org.w3c.dom.Document;

import javax.net.ssl.HttpsURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static ke.skyworld.mbanking.ussdapplication.AppConstants.strSACCOProductName;

public interface FundsTransferMenus {
    default USSDResponse displayMenu_FundTransfer(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.name())) {
                String strHeader = "Funds Transfer\nSelect Funds Transfer option";
                theUSSDResponse = getFundsTransferOptions(theUSSDRequest, strHeader);

            } else {
                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {
                    case FUNDS_TRANSFER_MENU: {
                        String strFundsTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name());
                        if (strFundsTransferOption.equalsIgnoreCase("FUNDS_TRANSFER_INTERNAL")) {

                            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.INTERNAL_FUNDS_TRANSFER);
                            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Internal Funds Transfer\n" + getServiceStatusDetails.getStringValue("display_message"));
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;

                            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.INTERNAL_TRANSFER)) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Internal Funds Transfer\n" + AppConstants.strServiceUnavailable);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;
                            }

                            theUSSDResponse = displayMenu_FundTransferInternal(theUSSDRequest, theParam);

                        } else if (strFundsTransferOption.equalsIgnoreCase("FUNDS_TRANSFER_EXTERNAL")) {

                            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BANK_TRANSFER);
                            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + getServiceStatusDetails.getStringValue("display_message"));
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;

                            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + AppConstants.strServiceUnavailable);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;
                            }

                            theUSSDResponse = displayMenu_FundTransferExternal(theUSSDRequest, theParam);
                        } else if (strFundsTransferOption.equalsIgnoreCase("PESALINK")) {
                            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BANK_TRANSFER);
                            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + getServiceStatusDetails.getStringValue("display_message"));
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;

                            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + AppConstants.strServiceUnavailable);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;
                            }

                            theUSSDResponse = displayMenu_FundTransferPesalink(theUSSDRequest, theParam);
                        } else if (strFundsTransferOption.equalsIgnoreCase("PAYBILL")) {
                            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BANK_TRANSFER);
                            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Paybill\n" + getServiceStatusDetails.getStringValue("display_message"));
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;

                            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Paybill\n" + AppConstants.strServiceUnavailable);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;
                            }

                            //TODO: Will Work Like
                            theUSSDResponse = displayMenu_FundTransferPaybill(theUSSDRequest, theParam);

                        } else if (strFundsTransferOption.equalsIgnoreCase("TILL_NUMBER")) {
                            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BANK_TRANSFER);
                            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + getServiceStatusDetails.getStringValue("display_message"));
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;

                            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + AppConstants.strServiceUnavailable);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;
                            }

                            //TODO: Will Work Like
                            theUSSDResponse = displayMenu_FundTransferBuyGoods(theUSSDRequest, theParam);
                        } else if (strFundsTransferOption.equalsIgnoreCase("LIPA_NA")) {
                            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.BANK_TRANSFER);
                            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + getServiceStatusDetails.getStringValue("display_message"));
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;

                            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "External Funds Transfer\n" + AppConstants.strServiceUnavailable);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                return theUSSDResponse;
                            }

                            //TODO: Will Work Like
                            theUSSDResponse = displayMenu_FundTransferExternal(theUSSDRequest, theParam);
                        } else {
                            String strHeader = "Funds Transfer\n{Select a valid Funds Transfer option}\n";
                            theUSSDResponse = getFundsTransferOptions(theUSSDRequest, strHeader);
                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransfer() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Funds Transfer\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransfer() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getFundsTransferOptions(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        final USSDAPI theUSSDAPI = new USSDAPI();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FUNDS_TRANSFER_INTERNAL", "1: " + AppConstants.strSACCOName + " accounts");

            String strFundsTransferXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strFundsTransferXML);

            String strCheckEmployerRestriction = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/EXTERNAL_FUNDS_TRANSFER/@ENABLED");

            if (strCheckEmployerRestriction.equalsIgnoreCase("YES")) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "FUNDS_TRANSFER_EXTERNAL", "2: Transfer to Bank");
            }

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "PESALINK", "3: PesaLink");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "4", "PAYBILL", "4: Paybill");
            // USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "5", "TILL_NUMBER", "5: Till Number");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "6", "LIPA_NA", "6: Lipa na " + strSACCOProductName);

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_MENU, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getFundsTransferOptions() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_FundTransferInternalCustomersList(USSDRequest theUSSDRequest, String theParam, String theCustomersHeader, String theAccountsHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theCustomersHeader);

            TransactionWrapper<FlexicoreHashMap> customersListWrapper = theUSSDAPI.getSignatoryCustomersList(theUSSDRequest);
            if (customersListWrapper.hasErrors()) {
                String strResponse = customersListWrapper.getSingleRecord().getStringValue("display_message");
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            FlexicoreArrayList customersList = customersListWrapper.getSingleRecord().getValue("payload");
            if (customersList.isEmpty()) {
                String strResponse = "Sorry, no member accounts found";
                theArrayListUSSDSelectOption = new ArrayList<>();
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            /*String strCustomerIdentifier = customersList.getRecord(0).getStringValue("identifier");
            theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theAccountsHeader, strCustomerIdentifier,theUSSDDataType, theUSSD_END_DataType);
*/

            if (customersList.size() > 1) {

                int intOptionMenu = 1;
                for (FlexicoreHashMap customersMap : customersList) {
                    String strCustomerIdentifier = customersMap.getStringValue("identifier");

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, String.valueOf(intOptionMenu), strCustomerIdentifier, intOptionMenu + ": " + strCustomerIdentifier);
                    intOptionMenu++;
                }

                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CUSTOMER, "NO", theArrayListUSSDSelectOption);

            } else {
                String strCustomerIdentifier = customersList.getRecord(0).getStringValue("identifier");
                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, theAccountsHeader, strCustomerIdentifier, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
        }

        return theUSSDResponse;

    }

    //Internal Fund Transfer Menus
    default USSDResponse displayMenu_FundTransferInternal(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Funds Transfer";

        try {

            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name())) {
                String strCustomerHeader = strHeader + "\nSelect source member\n";
                String strAccountsHeader = strHeader + "\nSelect source account\n";

                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                        theParam, strCustomerHeader, strAccountsHeader,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CUSTOMER,
                        USSDAPIConstants.AccountType.WITHDRAWABLE_IFT,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);

                //theUSSDResponse = displayMenu_FundTransferInternalCustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader);

            } else {
                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                System.out.println("THE USSD TYPE: " + ussdDataType.name());

                switch (ussdDataType) {

                    case FUNDS_TRANSFER_INTERNAL_CUSTOMER: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CUSTOMER.name());

                        if (strCustomerIdentifier.length() > 0) {
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE_IFT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CUSTOMER.name());
                        String strAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());


                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strAccountNumber);

                        String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                        System.out.println("strSourceAccountNo: " + strSourceAccountNo);


                        if (strAccountNumber.length() > 0) {
                            MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo, RegisterConstants.MemberRegisterType.BLACKLIST);
                            System.out.println("memberRegisterResponse: " + memberRegisterResponse.getResponseType());
                            if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);

                            } else {
                                String strHeader2 = strHeader + "\nSelect Funds Transfer option\n";
                                theUSSDResponse = getFundTransferInternalOptionMenu(theUSSDRequest, strHeader2);
                            }

                        } else {
                            String strAccountsHeader = strHeader + " \n{Select a valid source account}\n";

                            String strCustomerHeader = strHeader + " \n{Select source member}\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
                            } else {

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE_IFT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
                            }
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_INTERNAL_OPTION: {

                        String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OPTION.name());
                        if (strOption.equalsIgnoreCase("MY_ACCOUNT")) {
                            String strHeader2 = strHeader + "\nSelect destination account";
                            theUSSDResponse = theAppMenus.getFundTransferInternalToAccountCategoriesOptionMenu(theUSSDRequest, strHeader2);
                        } else if (strOption.equalsIgnoreCase("OTHER_ACCOUNT")) {

                            theUSSDResponse = getFundTransferInternalToOptionMenu(theUSSDRequest, strHeader + "\nSelect the destination option");

                            //String strResponse = strHeader + "\nEnter the destination account\n";
                            //theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");

                        } else {
                            String strHeader2 = strHeader + "\n{Select a valid menu}\nSelect Funds Transfer option\n";
                            theUSSDResponse = getFundTransferInternalOptionMenu(theUSSDRequest, strHeader2);
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT_CATEGORIES: {

                        String strCategory = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT_CATEGORIES.name());

                        if (strCategory.equals("")) {
                            String strHeader2 = strHeader + "\n{Select a VALID destination account}";
                            theUSSDResponse = theAppMenus.getFundTransferInternalToAccountCategoriesOptionMenu(theUSSDRequest, strHeader2);
                        } else {

                            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                            String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                            String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                            String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                            List<String> excludeAccounts = new ArrayList<>();
                            excludeAccounts.add(strFromAccountNumber);

                            String strHeader2 = strHeader + "\nSelect destination account";

                            theUSSDResponse = theAppMenus.getBankAccountsFundsTransferToWithType(theUSSDRequest, theParam, strHeader2, theCustomerIdentifier, excludeAccounts);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_INTERNAL_TO_OPTION: {
                        String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());

                        if (strToOption.equalsIgnoreCase("Mobile Number")) {
                            String strResponse = strHeader + "\nEnter Mobile Number of the destination acct.\n";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (strToOption.equalsIgnoreCase("Member Number")) {
                            String strResponse = strHeader + "\nEnter Member Number of the destination acct.\n";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (strToOption.equalsIgnoreCase("ID Number")) {
                            String strResponse = strHeader + "\nEnter ID Number of the destination acct.\n";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (strToOption.equalsIgnoreCase("Account Number")) {
                            String strResponse = strHeader + "\nEnter the destination account\n";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else {
                            String strHeader2 = strHeader + "\n{Select a valid menu}\nSelect transfer option\n";
                            theUSSDResponse = getFundTransferInternalToOptionMenu(theUSSDRequest, strHeader2);
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT: {
                        String strDestinationAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT.name());
                        if (strDestinationAccount.trim().isEmpty()) {
                            String strResponse = strHeader + "\n{Enter a VALID destination account}\n";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else {

                            String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());
                            if (strToOption.equalsIgnoreCase("Account Number")) {
                                String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());
                                TransactionWrapper<FlexicoreHashMap> validateAccountNumberWrapper = CBSAPI.validateAccountNumber(
                                        strMobileNo,
                                        "MSISDN",
                                        strMobileNo,
                                        strDestinationAccount);

                                if (validateAccountNumberWrapper.hasErrors()) {

                                    String strResponse = strHeader + "\n" + validateAccountNumberWrapper.getSingleRecord().getStringValue("display_message");

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);

                                } else {

                                    FlexicoreHashMap accountDetailsResultMap = validateAccountNumberWrapper.getSingleRecord();

                                    FlexicoreHashMap accountDetailsResponseMap = accountDetailsResultMap.getFlexicoreHashMap("response_payload");
                                    String requestStatus = accountDetailsResultMap.getStringValue("request_status");
                                    String canDeposit = accountDetailsResponseMap.getStringValue("can_deposit");

                                    if (!requestStatus.equalsIgnoreCase("SUCCESS")) {

                                    /*String strResponse = strHeader + "\n" + "Sorry, account " + strDestinationAccount + " not found";

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                    */

                                        String strResponse = strHeader + "\n" + "Sorry, account " + strDestinationAccount + " not found. Please enter an existing account.";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");


                                    } else if (!canDeposit.equalsIgnoreCase("YES")) {

                                    /*String strResponse = strHeader + "\n" + "Sorry, account " + strDestinationAccount + " does not allow deposits. Please try again.";

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                                    */

                                        String strResponse = strHeader + "\n" + "Sorry, account " + strDestinationAccount + " does not allow deposits. Please try again.";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");

                                    } else {

                                        FlexicoreHashMap memberDetailsMap = accountDetailsResponseMap.getFlexicoreHashMap("member_details");

                                        String fullName = memberDetailsMap.getStringValueOrIfNull("full_name", "");
                                        String[] fullNameArr = fullName.split(" ");
                                        fullName = fullNameArr[0];

                                        String strMobileNumber = memberDetailsMap.getStringValueOrIfNull("mobile_number", "");

                                        strMobileNumber = AppUtils.maskPhoneNumber(strMobileNumber);

                                        String strResponse = strHeader + " - " + strDestinationAccount + "\nName: " + fullName + "\nPhone: " + strMobileNumber + "\n" + "\nEnter amount:";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");

                                    }
                                }

                            } else {

                                HashMap<String, String> hmToAccountNoDetails = Utils.toHashMap(strDestinationAccount);
                                String strAccountNumber = hmToAccountNoDetails.get("ac_no");
                                String strAccountName = hmToAccountNoDetails.get("ac_name");

                                String strResponse = strHeader + "\nA/C Name: " + strAccountName + "\nA/C No: " + strAccountNumber + "\n" + "\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");

                            }

                        }
                        break;
                    }

                    case FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER: {
                        String strToIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER.name());
                        String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());

                        if (!strToIdentifier.trim().isEmpty()) {

                            if (strToOption.equalsIgnoreCase("Mobile Number")) {
                                strToIdentifier = AppUtils.sanitizePhoneNumber(strToIdentifier);
                            }

                            if ((strToOption.equalsIgnoreCase("Mobile Number")) && (!strToIdentifier.matches("^2547\\d{8}$"))) {
                                String strResponse = strHeader + "\n{Please enter a valid Mobile No. of the destination acct.)\n";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                            } else if ((strToOption.equalsIgnoreCase("Member Number")) && (strToIdentifier.isEmpty())) {
                                String strResponse = strHeader + "\n{Please enter a valid Member Number of the destination acct.\n";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                            } else if ((strToOption.equalsIgnoreCase("ID Number")) && (strToIdentifier.isEmpty())) {
                                String strResponse = strHeader + "\n{Please enter a valid ID Number of the destination acct.\n";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                            } else {
                                String strHeader2 = strHeader + "\nSelect Funds Transfer destination acct.\n";

                                String strCustomerIdentifierType = "CUSTOMER_NO";

                                if (strToOption.equalsIgnoreCase("Mobile Number")) {
                                    strCustomerIdentifierType = "MSISDN";
                                }

                                TransactionWrapper<FlexicoreHashMap> customerDetailsFromCBSWrapper = theUSSDAPI.getCustomerDetailsFromCBS(theUSSDRequest, strCustomerIdentifierType, strToIdentifier);
                                FlexicoreHashMap customerDetailsFromCBSMap = customerDetailsFromCBSWrapper.getSingleRecord();

                                if (customerDetailsFromCBSWrapper.hasErrors() && customerDetailsFromCBSWrapper.getStatusCode() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                                    String strResponse = customerDetailsFromCBSMap.getStringValue("display_message");

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);

                                } else if (customerDetailsFromCBSWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                                    if (strToOption.equalsIgnoreCase("Mobile Number")) {
                                        String strResponse = strHeader + "\n{Account NOT Found. Please enter an existing Mobile Number of the destination acct.}\n";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                                    } else if (strToOption.equalsIgnoreCase("Member Number")) {
                                        String strResponse = strHeader + "\n{Account NOT Found. Please enter an existing Member Number of the destination acct.}\n";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                                    } else if (strToOption.equalsIgnoreCase("ID Number")) {
                                        String strResponse = strHeader + "\n{Account NOT Found. Please enter an existing ID Number of the destination acct.}\n";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                                    }

                                } else {
                                    FlexicoreHashMap customerDetailsMap = new FlexicoreHashMap();

                                    customerDetailsFromCBSMap.printRecordVerticalLabelled();

                                    customerDetailsMap.putValue("customer_name", customerDetailsFromCBSMap.getStringValue("full_name"));
                                    customerDetailsMap.putValue("customer_number", customerDetailsFromCBSMap.getStringValue("identifier"));

                                    strHeader2 = strHeader2 + "Acc. Owner: " + customerDetailsFromCBSMap.getStringValue("full_name");

                                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                                            theParam, strHeader2,
                                            USSDAPIConstants.AccountType.ALL,
                                            "CUSTOMER_NO", customerDetailsMap.getStringValue("customer_number"),
                                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);

                                }
                            }
                        } else {
                            if (strToOption.equalsIgnoreCase("Mobile Number")) {
                                String strResponse = strHeader + "\n{Please enter a valid Mobile Number of the destination acct.)\n";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (strToOption.equalsIgnoreCase("Member Number")) {
                                String strResponse = strHeader + "\n{Please enter a valid Member Number of the destination acct.\n";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (strToOption.equalsIgnoreCase("ID Number")) {
                                String strResponse = strHeader + "\n{Please enter a valid ID Number of the destination acct.\n";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                            } else {
                                String strHeader2 = strHeader + "\n{Select a valid menu}\nSelect transfer option\n";
                                theUSSDResponse = getFundTransferInternalToOptionMenu(theUSSDRequest, strHeader2);
                            }
                        }

                        break;
                    }
                    case FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT: {

                        String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OPTION.name());
                        String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());
                        String strToIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER.name());
                        String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());

                        HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                        String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                        String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                        String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                        String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                        String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                        List<String> excludeAccounts = new ArrayList<>();
                        excludeAccounts.add(strFromAccountNumber);

                        String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT.name());

                        if (strToAccountDetails.equals("")) {
                            if (strOption.equalsIgnoreCase("MY_ACCOUNT")) {
                                String strHeader2 = strHeader + "\n{Select a VALID Funds Transfer destination acct.}\n";

                                theUSSDResponse = theAppMenus.getBankAccountsFundsTransferToWithType(theUSSDRequest, theParam, strHeader2, theCustomerIdentifier, excludeAccounts);

                            } else {

                                String strCustomerIdentifierType = "CUSTOMER_NO";

                                if (strToOption.equalsIgnoreCase("Mobile Number")) {
                                    strCustomerIdentifierType = "MSISDN";
                                }

                                TransactionWrapper<FlexicoreHashMap> customerDetailsFromCBSWrapper = theUSSDAPI.getCustomerDetailsFromCBS(theUSSDRequest, strCustomerIdentifierType, strToIdentifier);
                                FlexicoreHashMap customerDetailsFromCBSMap = customerDetailsFromCBSWrapper.getSingleRecord();

                                if (customerDetailsFromCBSWrapper.hasErrors() && customerDetailsFromCBSWrapper.getStatusCode() == HttpsURLConnection.HTTP_INTERNAL_ERROR) {
                                    String strResponse = customerDetailsFromCBSMap.getStringValue("display_message");

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);

                                } else if (customerDetailsFromCBSWrapper.getStatusCode() == HttpsURLConnection.HTTP_NOT_FOUND) {
                                    if (strToOption.equalsIgnoreCase("Mobile Number")) {
                                        String strResponse = strHeader + "\n{Account NOT Found. Please enter an existing Mobile Number of the destination acct.}\n";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                                    } else if (strToOption.equalsIgnoreCase("Member Number")) {
                                        String strResponse = strHeader + "\n{Account NOT Found. Please enter an existing Member Number of the destination acct.}\n";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER, USSDConstants.USSDInputType.STRING, "NO");
                                    }
                                } else {

                                    String strHeader2 = strHeader + "\n{Select a VALID Other Funds Transfer destination acct.}\n";

                                    FlexicoreHashMap customerDetailsMap = new FlexicoreHashMap();

                                    customerDetailsMap.putValue("customer_name", customerDetailsFromCBSMap.getStringValue("full_name"));
                                    customerDetailsMap.putValue("customer_number", customerDetailsFromCBSMap.getStringValue("identifier"));

                                    strHeader2 = strHeader2 + "Acc. Owner: " + customerDetailsFromCBSMap.getStringValue("full_name");

                                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                                            theParam, strHeader2,
                                            USSDAPIConstants.AccountType.ALL,
                                            "CUSTOMER_NO", customerDetailsMap.getStringValue("customer_number"),
                                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);

                                }
                            }
                        } else {

                            HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);
                            String strToAccountName = hmToAccountDetails.get("ac_name");
                            String strToAccountLabel = hmToAccountDetails.get("ac_label");
                            String strToAccountNumber = hmToAccountDetails.get("ac_no");
                            String strToFullName = hmToAccountDetails.get("cust_name");
                            String strToCustomerIdentifier = hmToAccountDetails.get("cust_id");

                            if (strToAccountNumber.equalsIgnoreCase(strFromAccountNumber)) {
                                if (strOption.equalsIgnoreCase("MY_ACCOUNT")) {
                                    String strHeader2 = strHeader + "\n{Select a different destination acct from source acct}\n";

                                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                                            theParam, strHeader2,
                                            USSDAPIConstants.AccountType.ALL,
                                            "CUSTOMER_NO", theCustomerIdentifier,
                                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);

                                } else {
                                    String strHeader2 = strHeader + "\n{Select a different destination acct from source acct}\n";

                                    FlexicoreHashMap customerDetailsMap = new FlexicoreHashMap();
                                    customerDetailsMap.putValue("customer_name", strToFullName);
                                    customerDetailsMap.putValue("customer_number", strToCustomerIdentifier);

                                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                                            theParam, strHeader2,
                                            USSDAPIConstants.AccountType.ALL,
                                            "CUSTOMER_NO", theCustomerIdentifier,
                                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);

                                }
                            } else {
                                String strResponse = strHeader + "\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_INTERNAL_AMOUNT: {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT.name());
                        if (strAmount.matches("^[1-9][0-9]*$")) {

                            String strResponse = strHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            String strFundsTransferXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                            Document document = XmlUtils.parseXml(strFundsTransferXML);

                            String strFundsTransferMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.INTERNAL_FUNDS_TRANSFER).getMinimum();
                            String strFundsTransferMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.INTERNAL_FUNDS_TRANSFER).getMaximum();

                            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());

                            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                            String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                            String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                            String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                            double dblAvailableBalance = 0;
                            try {
                                dblAvailableBalance = Double.parseDouble(strFromAccountAvailableBalance);
                            } catch (Exception e) {
                            }

                            double dblFundsTransferMinimum = Double.parseDouble(strFundsTransferMinimum);
                            double dblFundsTransferMaximum = Double.parseDouble(strFundsTransferMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            if (dblAmountEntered < dblFundsTransferMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strFundsTransferMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblAmountEntered > dblFundsTransferMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strFundsTransferMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                                strResponse = "\n{Funds Transfer " + strFromAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {
                            String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_INTERNAL_PIN: {
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {

                            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());

                            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                            //String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                            String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                            String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                            String strToIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER.name());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT.name());
                            String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OPTION.name());

                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            if (strOption.equalsIgnoreCase("OTHER_ACCOUNT")) {

                                String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());

                                String strOtherAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT.name());

                                if (!strToOption.equalsIgnoreCase("Account Number")) {
                                    HashMap<String, String> hmToAccountNoDetails = Utils.toHashMap(strOtherAccount);
                                    strOtherAccount = hmToAccountNoDetails.get("ac_no");
                                    String strAccountName = hmToAccountNoDetails.get("ac_name");
                                }

                                String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.IFT_ACCOUNT_TO_ACCOUNT.getValue(),
                                        Double.parseDouble(strAmount));

                                String strCharge = "";
                                if (chargesWrapper.hasErrors()) {
                                    strCharge = "";
                                } else {
                                    strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                                }

                                String strResponse = "Confirm Funds Transfer\nFrom A/C: " + strFromAccountLabel + "\nTo A/C: " + strOtherAccount + "\nAmount: KES " + strFormattedAmount + strCharge + "\n";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT.name());

                                HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);
                                String strToAccountLabel = hmToAccountDetails.get("ac_label");
                                String strToAccountNumber = hmToAccountDetails.get("ac_no");

                                String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                                TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.IFT_ACCOUNT_TO_ACCOUNT.getValue(),
                                        Double.parseDouble(strAmount));

                                String strCharge = "";
                                if (chargesWrapper.hasErrors()) {
                                    strCharge = "";
                                } else {
                                    strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                                }

                                String strResponse = "Confirm Funds Transfer\nFrom A/C: " + strFromAccountLabel + "\nTo A/C: " + strToAccountLabel + " - " + strToAccountNumber + "\n" + "Amount: KES " + strFormattedAmount + strCharge + "\n";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            }

                        } else {
                            String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_INTERNAL_CONFIRMATION: {

                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CONFIRMATION.name());

                        if (strConfirmation.equalsIgnoreCase("YES")) {

                            String strResponse = "Dear member, your Funds Transfer request has been received successfully. Please wait shortly as it's being processed.\n";

                            Thread worker = new Thread(() -> {

                                TransactionWrapper<FlexicoreHashMap> internalFundsTransferWrapper = theUSSDAPI.internalFundsTransfer(theUSSDRequest);
                                if (internalFundsTransferWrapper.hasErrors()) {
                                    FlexicoreHashMap internalFundsTransferMap = internalFundsTransferWrapper.getSingleRecord();
                                    String strErrorMessage = internalFundsTransferMap.getValue("cbs_api_return_val").toString() + "\n";
                                    strErrorMessage += internalFundsTransferMap.getStringValue("display_message");

                                    System.err.println("FundsTransferMenus.displayMenu_FundTransferInternal() - Response " + strErrorMessage);
                                }
                            });
                            worker.start();

                            /*APIConstants.TransactionReturnVal transactionReturnVal = theUSSDAPI.fundsTransfer(theUSSDRequest);


                            if(transactionReturnVal.equals(APIConstants.TransactionReturnVal.SUCCESS)){
                                strResponse = "Dear member, your Funds Transfer request has been received successfully. Please wait shortly as it's being processed.";
                            }else {

                                switch (transactionReturnVal) {
                                    case INCORRECT_PIN:
                                    case INVALID_ACCOUNT: {
                                        strResponse = "Sorry the PIN provided is incorrect. Your Funds Transfer request CANNOT be completed.\n";
                                        break;
                                    }
                                    case INSUFFICIENT_BAL: {
                                        strResponse = "Sorry the source acount has insufficient balance for this transaction. Your Funds Transfer request CANNOT be completed.\n";
                                        break;
                                    }
                                    case BLOCKED: {
                                        strResponse = "Dear member, your account has been blocked. Your Funds Transfer request CANNOT be completed.\n";
                                        break;
                                    }
                                    default: {
                                        strResponse = "Sorry, your Funds Transfer request CANNOT be completed at the moment. Please try again later.\n";
                                        break;
                                    }
                                }
                            }*/

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your Funds Transfer request NOT confirmed. Funds Transfer request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                            String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                            String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                            String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                            String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                            String strToIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_IDENTIFIER.name());

                            String strToAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT.name());
                            HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strToAccountDetails);

                            String strToAccountLabel = hmToAccountDetails.get("ac_label");
                            String strToAccountNumber = hmToAccountDetails.get("ac_no");

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_AMOUNT.name());
                            String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OPTION.name());

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.IFT_ACCOUNT_TO_ACCOUNT.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                            String strResponse = "Confirm Funds Transfer\n{Select a valid menu}\nFrom A/C: " + strFromAccountLabel + "\nTo A/C: " + strToAccountLabel + " - " + strToAccountNumber + "\n" + "Amount: KES " + strFormattedAmount + strCharge + "\n";

                            if (strOption.equalsIgnoreCase("OTHER_ACCOUNT")) {

                                String strToOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION.name());

                                String strOtherAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OTHER_ACCOUNT.name());

                                if (!strToOption.equalsIgnoreCase("Account Number")) {
                                    HashMap<String, String> hmToAccountNoDetails = Utils.toHashMap(strOtherAccount);
                                    strOtherAccount = hmToAccountNoDetails.get("ac_no");
                                    String strAccountName = hmToAccountNoDetails.get("ac_name");
                                }

                                strResponse = "Confirm Funds Transfer\n{Select a valid menu}\nFrom A/C: " + strFromAccountLabel + "\nTo A/C: " + strOtherAccount + "\n" + "Amount: KES " + strFormattedAmount + strCharge + "\n";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransferInternal() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }

            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransferInternal() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getFundTransferInternalToAccountCategoriesOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "FOSA_ACCOUNTS", "1: FOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BOSA_ACCOUNTS", "2: BOSA Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "BUSINESS_ACCOUNTS", "3: Business Accounts");
//            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "PAY_LOAN", "3: Pay Loan");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT_CATEGORIES, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_BalanceEnquiryMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getBankAccountsFundsTransferToWithType(USSDRequest theUSSDRequest, String theParam, String strAccountsHeader, String strCustomerIdentifier, List<String> excludeAccounts) {
        USSDResponse theUSSDResponse = null;

        try {
            String strAccountTypes = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT_CATEGORIES.name());
            switch (strAccountTypes) {
                case "FOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.FOSA,
                            "CUSTOMER_NO", strCustomerIdentifier, excludeAccounts,
                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
                    break;
                }
                case "BOSA_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BOSA,
                            "CUSTOMER_NO", strCustomerIdentifier, excludeAccounts,
                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
                    break;
                }
                case "BUSINESS_ACCOUNTS": {
                    theUSSDResponse = GeneralMenus.displayMenu_AccountsListWithType(theUSSDRequest,
                            theParam, strAccountsHeader,
                            USSDAPIConstants.AccountType.BUSINESS,
                            "CUSTOMER_NO", strCustomerIdentifier, excludeAccounts,
                            AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_ACCOUNT, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_END);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.getBankAccountsFundsTransferToWithType() ERROR : " + e.getMessage());
        } finally {

        }
        return theUSSDResponse;
    }

    default USSDResponse getFundTransferInternalToOptionMenu(USSDRequest theUSSDRequest, String strHeader) {

        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "Mobile Number", "1: Mobile Number");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "Member Number", "2: Member Number");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "ID Number", "3: ID Number");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "Account Number", "3: Account Number");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_TO_OPTION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.getFundTransferInternalOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }

        return theUSSDResponse;
    }

    default USSDResponse getFundTransferInternalOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "MY_ACCOUNT", "1: My Accounts");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "OTHER_ACCOUNT", "2: Other Accounts");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_INTERNAL_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getFundTransferInternalOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    //External Fund Transfer Menus
    default USSDResponse displayMenu_FundTransferExternalBanks(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            System.out.println();
            String strFundsTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name());

            SPManagerConstants.ProviderAccountType providerAccountType = SPManagerConstants.ProviderAccountType.BANK_SHORT_CODE;
            if (strFundsTransferOption.equalsIgnoreCase("FUNDS_TRANSFER_EXTERNAL")) {
                providerAccountType = SPManagerConstants.ProviderAccountType.BANK_SHORT_CODE;
            } else {
                providerAccountType = SPManagerConstants.ProviderAccountType.BANK_CODE;
            }


            System.out.println("***********Provider Account Type: " + providerAccountType.getValue() + "***********");
            LinkedList<APIUtils.ServiceProviderAccount> llSPAAccounts = APIUtils.getSPAccounts(providerAccountType);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            for (APIUtils.ServiceProviderAccount serviceProviderAccount : llSPAAccounts) {
                int intOptionMenu = llSPAAccounts.indexOf(serviceProviderAccount);
                intOptionMenu = intOptionMenu + 1;

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
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getFundsTransferOptions() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_FundTransferExternal(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Funds Transfer";

        try {

            {
                String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION.name());

                System.out.println("***********Transfer Option: " + strTransferOption + "***********at displayMenu_FundTransferExternal");
                if (strTransferOption != null && !strTransferOption.isBlank()) {
                    if (strTransferOption.equalsIgnoreCase("M-PESA")) {
                        strHeader = strHeader + " (M-PESA)";
                    } else {
                        strHeader = strHeader + " (PesaLink)";
                    }
                }
            }

            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name())) {
                String strHeader2 = strHeader + "\nSelect Bank to transfer funds";
                theUSSDResponse = displayMenu_FundTransferExternalBanks(theUSSDRequest, strHeader2);

            } else {

                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {
                    case FUNDS_TRANSFER_EXTERNAL_BANK: {

                        String strToBank = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
                        if (strToBank != "") {
                            theUSSDResponse = displayMenu_FundTransferExternal_Maintain_Accounts(theUSSDRequest, theParam);
                        } else {
                            String strHeader2 = "\n{Select a valid Bank to transfer funds}";
                            theUSSDResponse = displayMenu_FundTransferExternalBanks(theUSSDRequest, strHeader2);
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO: {

                        System.out.println("*****Transfer Option: " + theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION.name()) + "*****");
                        String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());

                        HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                        String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                        String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                        String strToAccountType = hmBankAccountDetails.get("type");
                        String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                        String strToBankName = hmBankAccountDetails.get("long_tag");

                        String strMenuOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

                        String strAction = "";
                        if (!strMenuOption.isEmpty()) {
                            HashMap<String, String> hmMenuOption = Utils.toHashMap(strMenuOption);
                            strAction = hmMenuOption.get("ACTION");
                        }

                        switch (strAction) {
                            case "CHOICE": {
                                //theUSSDResponse = getExternalFundsTransferOptionMenu(theUSSDRequest, strHeader + "\nSelect Transfer Option");

                                String strCustomerHeader = strHeader + " \nSelect source member\n";
                                String strAccountsHeader = strHeader + " \nSelect source account\n";

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                        theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);


                                break;
                            }
                            case "ADD": {

                                String strResponse = "Add " + strToBankName + " " + strToAccountNaming + "\nEnter " + strToAccountNaming + ":";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                break;
                            }
                            case "REMOVE": {
                                String strHeader2 = "Remove " + strToBankName + " " + strToAccountNaming + "\nSelect " + strToAccountNaming + " to Remove:";
                                theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_REMOVE, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader2, USSDConstants.Condition.NO);
                                break;
                            }
                            default: {
                                String strHeader2 = strHeader + "\n{Select a VALID MENU}:\n";
                                theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader2, USSDConstants.Condition.YES);

                                break;
                            }
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION: {
                        System.out.println("*****Transfer Option: " + theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name()) + "*****");
                        String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION.name());
                        switch (strTransferOption) {
                            case "M-PESA", "PESALINK" -> {
                                String strCustomerHeader = strHeader + " \nSelect source member\n";
                                String strAccountsHeader = strHeader + " \nSelect source account\n";

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                        theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                            }
                            default ->
                                    theUSSDResponse = getExternalFundsTransferOptionMenu(theUSSDRequest, strHeader + "\n{Invalid Option}\nSelect Transfer Option");
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_CUSTOMER: {
                        System.out.println("*****Transfer Option: " + theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name()) + "*****");
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER.name());

                        if (strCustomerIdentifier.length() > 0) {
                            String strAccountsHeader = strHeader + "\nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NAME: {
                        System.out.println("*****Transfer Option: " + theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name()) + "*****");
                        String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());

                        HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                        String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                        String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                        String strToAccountType = hmBankAccountDetails.get("type");
                        String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                        String strToBankName = hmBankAccountDetails.get("long_tag");

                        String strToBankAccountName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NAME.name());
                        if (strToBankAccountName.length() >= 3) { //Three or more Characters

                            String strCustomerHeader = strHeader + " \nSelect source member\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);


                        } else {
                            String strHeader2 = strHeader + "\n{Enter a valid " + strToBankName + " account name to receive funds}:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strHeader2, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NAME, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT: {
                        String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                        HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                        String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                        String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();


                        String strFromAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                        if (strFromAccount.length() > 0) {

                            MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strSourceAccountNo, RegisterConstants.MemberRegisterType.BLACKLIST);
                            if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strResponse = strHeader + "\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {

                            String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER.name());

                            String strAccountsHeader = strHeader + " \n{Select a valid source account}\n";
                            String strCustomerHeader = strHeader + " \nSelect source member\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                            } else {
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                            }
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_AMOUNT: {
                        System.out.println("*****Transfer Option: " + theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name()) + "*****");
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());
                        if (strAmount.matches("^[1-9][0-9]*$")) {
                            String strResponse = strHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            String strFundsTransferMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                            String strFundsTransferMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            double dblAvailableBalance = 0;
                            try {
                                dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                            } catch (Exception e) {
                            }

                            double dblFundsTransferMinimum = Double.parseDouble(strFundsTransferMinimum);
                            double dblFundsTransferMaximum = Double.parseDouble(strFundsTransferMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                            dblFundsTransferMaximum = Math.min(dblFundsTransferMaximum, dblDailyLimitRemainingAmount);

                            if (dblAmountEntered < dblFundsTransferMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strFundsTransferMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblDailyLimitRemainingAmount <= 0) {
                                strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                            } else if (dblAmountEntered > dblFundsTransferMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strFundsTransferMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                                strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        } else {
                            String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_PIN: {
                        System.out.println("*****Transfer Option: " + theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name()) + "*****");
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {

                            String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
                            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                            String strToAccountType = hmBankAccountDetails.get("type");
                            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                            String strToBankName = hmBankAccountDetails.get("long_tag");

                            String strToBankAccountNoHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

                            HashMap<String, String> hmAccount = Utils.toHashMap(strToBankAccountNoHashMap);
                            String strAccountID = hmAccount.get("ACCOUNT_ID");
                            String strAccountName = hmAccount.get("ACCOUNT_NAME");
                            String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name());

                            if (strTransferOption.equalsIgnoreCase("FUNDS_TRANSFER_EXTERNAL")) {
                                strHeader = "External Funds Transfer";
                            } else if (strTransferOption.equalsIgnoreCase("PESALINK")) {
                                strHeader = "PesaLink Funds Transfer";
                            }

                            String strResponse = "Confirm " + strHeader + " to " + strToBankName + "\nA/C: " + strAccountIdentifier +
                                    " - " + strAccountName + "\nFrom A/C: " + strSourceAccountName + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_CONFIRMATION: {
                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CONFIRMATION.name());
                        if (strConfirmation.equalsIgnoreCase("YES")) {

                            TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = null;


                            String strFundsTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name());

                            if (strFundsTransferOption.equalsIgnoreCase("FUNDS_TRANSFER_EXTERNAL")) {
                                moneyOutWrapper = theUSSDAPI.bankTransferViaB2B(theUSSDRequest, PESAConstants.PESAType.PESA_OUT);
                            } else {
                                moneyOutWrapper = theUSSDAPI.bankTransferViaPesaLink(theUSSDRequest, PESAConstants.PESAType.PESA_OUT);
                            }
                            FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                            if (moneyOutWrapper.hasErrors()) {
                                String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += moneyOutMap.getStringValue("display_message");
                                System.err.println("FundsTransferMenus.bankTransferViaB2B() - Response " + strErrorMessage);
                            }

                            String strResponse = moneyOutMap.getStringValue("display_message");

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Funds Transfer request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
                            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                            String strToAccountType = hmBankAccountDetails.get("type");
                            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                            String strToBankName = hmBankAccountDetails.get("long_tag");

                            String strToBankAccountNoHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

                            HashMap<String, String> hmAccount = Utils.toHashMap(strToBankAccountNoHashMap);
                            String strAccountID = hmAccount.get("ACCOUNT_ID");
                            String strAccountName = hmAccount.get("ACCOUNT_NAME");
                            String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");


                            String strResponse = "Confirm Funds Transfer to " + strToBankName + "\n{Select a valid menu}\nA/C: " + strAccountIdentifier +
                                    " - " + strAccountName + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransfer() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Funds Transfer\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransfer() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse displayMenu_FundTransferExternal_Maintain_Accounts(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {

            AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());
            String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());

            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
            System.out.println("*****Transfer strToSPProviderAccountCode: " + "*****");
            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
            String strToAccountType = hmBankAccountDetails.get("type");
            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
            String strToBankName = hmBankAccountDetails.get("long_tag");

            switch (ussdDataType) {
                case FUNDS_TRANSFER_EXTERNAL_BANK: {
                    if (strProviderBankAccountDetails != "") {
                        String strHeader = "Funds Transfer\nSelect " + strToBankName + " " + strToAccountNaming + " to receive funds:\n";
                        theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);
                    } else {
                        String strHeader = "Funds Transfer\n{Select a valid Bank to transfer funds}";
                        theUSSDResponse = displayMenu_FundTransferExternalBanks(theUSSDRequest, strHeader);
                    }

                    break;
                }
                case FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_ACCOUNT: {
                    String theAccountNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_ACCOUNT.name());

                    if (theAccountNo.matches("^\\d{6,24}$")) { //6 - 24 Digits

                        String strResponse = "Add " + strToBankName + " " + strToAccountNaming + "\nEnter the NAME of the account HOLDER\n(" + strToBankName + " " + strToAccountNaming + " " + theAccountNo + "):";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_NAME, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strResponse = "Add " + strToBankName + " " + strToAccountNaming + "\n{Enter a VALID " + strToAccountNaming + "}:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;

                }
                case FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_NAME: {
                    //ADD Account
                    String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                    String strAccountNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_ACCOUNT.name());
                    String strAccountName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_NAME.name());

                    try {
                        String strIntegritySecret = PESALocalParameters.getIntegritySecret();
                        SPManager spManager = new SPManager(strIntegritySecret);
                        spManager.createUserSavedAccount(SPManagerConstants.UserIdentifierType.MSISDN, strMobileNo, strToSPProviderAccountCode, SPManagerConstants.AccountIdentifierType.ACCOUNT_NO, strAccountNo, strAccountName);


                    } catch (Exception e) {
                        System.err.println("theAppMenus.displayMenu_FundTransferExternal_Maintain_Accounts() ERROR : " + e.getMessage());
                    }

                    String strHeader = "Funds Transfer\nSelect " + strToBankName + " " + strToAccountNaming + " to receive funds:\n";
                    theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);
                    break;
                }
                case FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_REMOVE: {
                    //REMOVE Account
                    String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                    String strAccountHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_REMOVE.name());

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
                            System.err.println("theAppMenus.displayMenu_FundTransferExternal_Maintain_Accounts() ERROR : " + e.getMessage());
                        }

                        String strHeader = "Funds Transfer\nSelect " + strToBankName + " " + strToAccountNaming + " to receive funds:\n";
                        theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);

                    } else {
                        String strHeader = "Remove " + strToBankName + " " + strToAccountNaming + "\n{Select a VALID MENU}:";
                        theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_REMOVE, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.NO);
                    }

                    break;
                }
                default: {
                    String strHeader = "Funds Transfer\n{Select a VALID " + strToBankName + " " + strToAccountNaming + " to receive funds}:\n";
                    System.err.println("theAppMenus.displayMenu_FundTransferExternal_Maintain_Accounts() UNKNOWN PARAM ERROR : strUSSDDataType = " + ussdDataType.name());

                    theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader, USSDConstants.Condition.YES);

                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransferExternal_Maintain_Accounts() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_FundTransferPaybill(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Paybill Transfer";

        try {
            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name())) {

                String strCustomerHeader = strHeader + " \nSelect source member\n";
                String strAccountsHeader = strHeader + " \nSelect source account\n";

                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                        theParam, strCustomerHeader, strAccountsHeader,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CUSTOMER,
                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END);

            } else {

                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {

                    case FUNDS_TRANSFER_PAYBILL_CUSTOMER: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CUSTOMER.name());

                        if (!strCustomerIdentifier.isEmpty()) {
                            String strAccountsHeader = strHeader + "\nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT: {
                        String strFromAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT.name());
                        if (!strFromAccount.isEmpty()) {

                            MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strFromAccount, RegisterConstants.MemberRegisterType.BLACKLIST);
                            if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strResponse = strHeader + "\nEnter Paybill Number:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CODE, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {

                            String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CUSTOMER.name());

                            String strAccountsHeader = strHeader + " \n{Select a valid source account}\n";
                            String strCustomerHeader = strHeader + " \nSelect source member\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END);
                            } else {
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END);
                            }
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_PAYBILL_CODE: {
                        String strPaybillBusinessCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CODE.name());

                        if (strPaybillBusinessCode.matches("\\d{5,}")) {
                            String strResponse = strHeader + "\nEnter Account Number:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER, USSDConstants.USSDInputType.STRING, "NO");

                        } else {
                            String strResponse = strHeader + "\n{Invalid Paybill Provided}\nEnter Paybill Number:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CODE, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER: {
                        String strPaybillAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER.name());

                        if (!strPaybillAccountNumber.isEmpty()) {
                            String strResponse = strHeader + "\nEnter Amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");

                        } else {
                            String strResponse = strHeader + "\n{Invalid Account Number}\nEnter Account Number:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_PAYBILL_AMOUNT: {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT.name());
                        if (strAmount.matches("^[1-9][0-9]*$")) {
                            String strResponse = strHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            String strFundsTransferMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                            String strFundsTransferMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            double dblAvailableBalance = 0;
                            try {
                                dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                            } catch (Exception e) {
                            }

                            double dblFundsTransferMinimum = Double.parseDouble(strFundsTransferMinimum);
                            double dblFundsTransferMaximum = Double.parseDouble(strFundsTransferMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                            dblFundsTransferMaximum = Math.min(dblFundsTransferMaximum, dblDailyLimitRemainingAmount);

                            if (dblAmountEntered < dblFundsTransferMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strFundsTransferMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblDailyLimitRemainingAmount <= 0) {
                                strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END, "NO", theArrayListUSSDSelectOption);
                            } else if (dblAmountEntered > dblFundsTransferMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strFundsTransferMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                                strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        } else {
                            String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_PAYBILL_PIN: {
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {

                            String strPaybillCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CODE.name());
                            String strPaybillAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER.name());
                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strResponse = "Confirm " + strHeader + " to Business Code " + strPaybillCode + "\nA/C: " + strPaybillAccountNumber + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_PAYBILL_CONFIRMATION: {
                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CONFIRMATION.name());
                        if (strConfirmation.equalsIgnoreCase("YES")) {

                            TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = theUSSDAPI.paybillTransferViaB2B(theUSSDRequest, PESAConstants.PESAType.PESA_OUT);
                            FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                            if (moneyOutWrapper.hasErrors()) {
                                String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += moneyOutMap.getStringValue("display_message");
                                System.err.println("FundsTransferMenus.paybillTransferViaB2B() - Response " + strErrorMessage);
                            }

                            String strResponse = moneyOutMap.getStringValue("display_message");

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Paybill Transfer request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            String strPaybillCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CODE.name());
                            String strPaybillAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_ACCOUNT_NUMBER.name());
                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strResponse = "Confirm " + strHeader + " to Business Code " + strPaybillCode + "{Select a Valid Option}\nA/C: " + strPaybillAccountNumber + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransfer() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Funds Transfer\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PAYBILL_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransfer() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse displayMenu_FundTransferBuyGoods(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Buy Goods and Services";

        try {
            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name())) {

                String strCustomerHeader = strHeader + " \nSelect source member\n";
                String strAccountsHeader = strHeader + " \nSelect source account\n";

                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                        theParam, strCustomerHeader, strAccountsHeader,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CUSTOMER,
                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END);

            } else {

                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {

                    case FUNDS_TRANSFER_TILL_CUSTOMER: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CUSTOMER.name());

                        if (!strCustomerIdentifier.isEmpty()) {
                            String strAccountsHeader = strHeader + "\nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_TILL_FROM_ACCOUNT: {
                        String strFromAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT.name());
                        if (!strFromAccount.isEmpty()) {

                            MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strFromAccount, RegisterConstants.MemberRegisterType.BLACKLIST);
                            if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strResponse = strHeader + "\nEnter Till Number:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CODE, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {

                            String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CUSTOMER.name());

                            String strAccountsHeader = strHeader + " \n{Select a valid source account}\n";
                            String strCustomerHeader = strHeader + " \nSelect source member\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END);
                            } else {
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END);
                            }
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_TILL_CODE: {
                        String strTillBusinessCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CODE.name());

                        if (strTillBusinessCode.matches("\\d{5,}")) {
                            String strResponse = strHeader + "\nEnter Amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else {
                            String strResponse = strHeader + "\n{Invalid Till Provided}\nEnter Till Number:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CODE, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }


                    case FUNDS_TRANSFER_TILL_AMOUNT: {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT.name());
                        if (strAmount.matches("^[1-9][0-9]*$")) {
                            String strResponse = strHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            String strFundsTransferMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                            String strFundsTransferMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            double dblAvailableBalance = 0;
                            try {
                                dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                            } catch (Exception e) {
                            }

                            double dblFundsTransferMinimum = Double.parseDouble(strFundsTransferMinimum);
                            double dblFundsTransferMaximum = Double.parseDouble(strFundsTransferMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                            dblFundsTransferMaximum = Math.min(dblFundsTransferMaximum, dblDailyLimitRemainingAmount);

                            if (dblAmountEntered < dblFundsTransferMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strFundsTransferMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblDailyLimitRemainingAmount <= 0) {
                                strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END, "NO", theArrayListUSSDSelectOption);
                            } else if (dblAmountEntered > dblFundsTransferMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strFundsTransferMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                                strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        } else {
                            String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_TILL_PIN: {
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {

                            String strTillCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CODE.name());
                            String strTillAccountNumber = "";
                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strResponse = "Confirm " + strHeader + " to Business Code " + strTillCode + "\nA/C: " + strTillAccountNumber + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_TILL_CONFIRMATION: {
                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CONFIRMATION.name());
                        if (strConfirmation.equalsIgnoreCase("YES")) {

                            TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = theUSSDAPI.tillTransferViaB2B(theUSSDRequest, PESAConstants.PESAType.PESA_OUT);
                            FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                            if (moneyOutWrapper.hasErrors()) {
                                String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += moneyOutMap.getStringValue("display_message");
                                System.err.println("FundsTransferMenus.tillTransferViaB2B() - Response " + strErrorMessage);
                            }

                            String strResponse = moneyOutMap.getStringValue("display_message");

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Till Transfer request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            String strTillCode = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CODE.name());
                            String strTillAccountNumber = "";
                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strResponse = "Confirm " + strHeader + " to Business Code " + strTillCode + "{Select a Valid Option}\nA/C: " + strTillAccountNumber + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransfer() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Funds Transfer\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_TILL_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransfer() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse displayMenu_FundTransferPesalink(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Funds Transfer via Pesalink";

        try {

           /* {
                String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION.name());
                if (strTransferOption != null && !strTransferOption.isBlank()) {
                    if (strTransferOption.equalsIgnoreCase("M-PESA")) {
                        strHeader = strHeader + " (M-PESA)";
                    } else {
                        strHeader = strHeader + " (PesaLink)";
                    }
                }
            }*/

            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name())) {
                String strHeader2 = strHeader + "\nSelect Transfer Option:";
                theUSSDResponse = getExternalFundsTransferPesalinkOptionMenu(theUSSDRequest, strHeader2);

            } else {

                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {

                    case FUNDS_TRANSFER_PESALINK_OPTION: {
                        String strPesalinkOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_OPTION.name());

                        switch (strPesalinkOption) {
                            case "MOBILE_NUMBER": {
                                theUSSDResponse = displayMenu_FundTransferPesalinkMobileTransfer(theUSSDRequest, theParam);
                                break;
                            }
                            case "BANK_ACCOUNT": {
                                String strHeader2 = strHeader + "\nSelect Bank to transfer funds";
                                theUSSDResponse = displayMenu_FundTransferExternalBanks(theUSSDRequest, strHeader2);
                                break;
                            }
                            default: {
                                String strHeader2 = strHeader + "\n{Invalid Option}\nSelect Transfer Option:";
                                theUSSDResponse = getExternalFundsTransferPesalinkOptionMenu(theUSSDRequest, strHeader2);
                                break;
                            }
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_BANK: {
                        String strToBank = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
                        if (strToBank != "") {
                            theUSSDResponse = displayMenu_FundTransferExternal_Maintain_Accounts(theUSSDRequest, theParam);
                        } else {
                            String strHeader2 = "\n{Select a valid Bank to transfer funds}";
                            theUSSDResponse = displayMenu_FundTransferExternalBanks(theUSSDRequest, strHeader2);
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO: {
                        String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());

                        HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                        String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                        String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                        String strToAccountType = hmBankAccountDetails.get("type");
                        String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                        String strToBankName = hmBankAccountDetails.get("long_tag");

                        String strMenuOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

                        String strAction = "";
                        if (!strMenuOption.isEmpty()) {
                            HashMap<String, String> hmMenuOption = Utils.toHashMap(strMenuOption);
                            strAction = hmMenuOption.get("ACTION");
                        }

                        switch (strAction) {
                            case "CHOICE": {
                                //theUSSDResponse = getExternalFundsTransferOptionMenu(theUSSDRequest, strHeader + "\nSelect Transfer Option");

                                String strCustomerHeader = strHeader + " \nSelect source member\n";
                                String strAccountsHeader = strHeader + " \nSelect source account\n";

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                        theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);


                                break;
                            }
                            case "ADD": {

                                String strResponse = "Add " + strToBankName + " " + strToAccountNaming + "\nEnter " + strToAccountNaming + ":";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_ACCOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                break;
                            }
                            case "REMOVE": {
                                String strHeader2 = "Remove " + strToBankName + " " + strToAccountNaming + "\nSelect " + strToAccountNaming + " to Remove:";
                                theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_MAINTENANCE_ACCOUNT_REMOVE, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader2, USSDConstants.Condition.NO);
                                break;
                            }
                            default: {
                                String strHeader2 = strHeader + "\n{Select a VALID MENU}:\n";
                                theUSSDResponse = GeneralMenus.getAccountMaintenanceMenus(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO, strToAccountType, strToAccountNaming, strToSPProviderAccountCode, strHeader2, USSDConstants.Condition.YES);

                                break;
                            }
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION: {
                        String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION.name());
                        switch (strTransferOption) {
                            case "M-PESA", "PESALINK" -> {
                                String strCustomerHeader = strHeader + " \nSelect source member\n";
                                String strAccountsHeader = strHeader + " \nSelect source account\n";

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                        theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                            }
                            default ->
                                    theUSSDResponse = getExternalFundsTransferOptionMenu(theUSSDRequest, strHeader + "\n{Invalid Option}\nSelect Transfer Option");
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_CUSTOMER: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER.name());

                        if (strCustomerIdentifier.length() > 0) {
                            String strAccountsHeader = strHeader + "\nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NAME: {
                        String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());

                        HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                        String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                        String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                        String strToAccountType = hmBankAccountDetails.get("type");
                        String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                        String strToBankName = hmBankAccountDetails.get("long_tag");

                        String strToBankAccountName = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NAME.name());
                        if (strToBankAccountName.length() >= 3) { //Three or more Characters

                            String strCustomerHeader = strHeader + " \nSelect source member\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                                    theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);


                        } else {
                            String strHeader2 = strHeader + "\n{Enter a valid " + strToBankName + " account name to receive funds}:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strHeader2, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NAME, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT: {
                        String strFromAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                        if (strFromAccount.length() > 0) {

                            MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strFromAccount, RegisterConstants.MemberRegisterType.BLACKLIST);
                            if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strResponse = strHeader + "\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {

                            String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER.name());

                            String strAccountsHeader = strHeader + " \n{Select a valid source account}\n";
                            String strCustomerHeader = strHeader + " \nSelect source member\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                            } else {
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END);
                            }
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_AMOUNT: {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());
                        if (strAmount.matches("^[1-9][0-9]*$")) {
                            String strResponse = strHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            String strFundsTransferMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                            String strFundsTransferMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            double dblAvailableBalance = 0;
                            try {
                                dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                            } catch (Exception e) {
                            }

                            double dblFundsTransferMinimum = Double.parseDouble(strFundsTransferMinimum);
                            double dblFundsTransferMaximum = Double.parseDouble(strFundsTransferMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                            dblFundsTransferMaximum = Math.min(dblFundsTransferMaximum, dblDailyLimitRemainingAmount);

                            if (dblAmountEntered < dblFundsTransferMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strFundsTransferMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblDailyLimitRemainingAmount <= 0) {
                                strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                            } else if (dblAmountEntered > dblFundsTransferMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strFundsTransferMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                                strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        } else {
                            String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_PIN: {
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {

                            String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
                            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                            String strToAccountType = hmBankAccountDetails.get("type");
                            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                            String strToBankName = hmBankAccountDetails.get("long_tag");

                            String strToBankAccountNoHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

                            HashMap<String, String> hmAccount = Utils.toHashMap(strToBankAccountNoHashMap);
                            String strAccountID = hmAccount.get("ACCOUNT_ID");
                            String strAccountName = hmAccount.get("ACCOUNT_NAME");
                            String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name());

                            String strResponse = "Confirm " + strHeader + " to " + strToBankName + " via " + strTransferOption + " \nA/C: " + strAccountIdentifier +
                                    " - " + strAccountName + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_EXTERNAL_CONFIRMATION: {
                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CONFIRMATION.name());
                        if (strConfirmation.equalsIgnoreCase("YES")) {


                            TransactionWrapper<FlexicoreHashMap> moneyOutWrapper = null;


                            String strFundsTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_MENU.name());

                            if (strFundsTransferOption.equalsIgnoreCase("FUNDS_TRANSFER_EXTERNAL")) {
                                moneyOutWrapper = theUSSDAPI.bankTransferViaB2B(theUSSDRequest, PESAConstants.PESAType.PESA_OUT);
                            } else {
                                moneyOutWrapper = theUSSDAPI.bankTransferViaPesaLink(theUSSDRequest, PESAConstants.PESAType.PESA_OUT);
                            }

                            FlexicoreHashMap moneyOutMap = moneyOutWrapper.getSingleRecord();
                            if (moneyOutWrapper.hasErrors()) {
                                String strErrorMessage = moneyOutMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += moneyOutMap.getStringValue("display_message");
                                System.err.println("FundsTransferMenus.bankTransferViaB2B() - Response " + strErrorMessage);
                            }

                            String strResponse = moneyOutMap.getStringValue("display_message");

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Funds Transfer request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strProviderBankAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_BANK.name());
                            HashMap<String, String> hmBankAccountDetails = Utils.toHashMap(strProviderBankAccountDetails);

                            String strToSPProviderAccountCode = hmBankAccountDetails.get("code");
                            String strToAccountIdentifier = hmBankAccountDetails.get("identifier");
                            String strToAccountType = hmBankAccountDetails.get("type");
                            String strToAccountNaming = hmBankAccountDetails.get("type_tag");
                            String strToBankName = hmBankAccountDetails.get("long_tag");

                            String strToBankAccountNoHashMap = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TO_BANK_ACCOUNT_NO.name());

                            HashMap<String, String> hmAccount = Utils.toHashMap(strToBankAccountNoHashMap);
                            String strAccountID = hmAccount.get("ACCOUNT_ID");
                            String strAccountName = hmAccount.get("ACCOUNT_NAME");
                            String strAccountIdentifier = hmAccount.get("ACCOUNT_IDENTIFIER");

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");


                            String strResponse = "Confirm Funds Transfer to " + strToBankName + "\n{Select a valid menu}\nA/C: " + strAccountIdentifier +
                                    " - " + strAccountName + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransfer() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Funds Transfer\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransfer() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse displayMenu_FundTransferPesalinkMobileTransfer(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Funds Transfer via Pesalink";

        try {

           /* {
                String strTransferOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION.name());
                if (strTransferOption != null && !strTransferOption.isBlank()) {
                    if (strTransferOption.equalsIgnoreCase("M-PESA")) {
                        strHeader = strHeader + " (M-PESA)";
                    } else {
                        strHeader = strHeader + " (PesaLink)";
                    }
                }
            }*/

            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() -1];

            if (strLastKey.equalsIgnoreCase(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_OPTION.name())) {

                String strCustomerHeader = strHeader + " \nSelect source member\n";
                String strAccountsHeader = strHeader + " \nSelect source account\n";

                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                        strLastKey, strCustomerHeader, strAccountsHeader,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CUSTOMER,
                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT,
                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END);

            } else {

                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {

                    case FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CUSTOMER: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CUSTOMER.name());

                        if (strCustomerIdentifier.length() > 0) {
                            String strAccountsHeader = strHeader + "\nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT,
                                    AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END);
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT: {
                        String strFromAccount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT.name());
                        if (strFromAccount.length() > 0) {

                            MemberRegisterResponse memberRegisterResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.ACCOUNT_NO, strFromAccount, RegisterConstants.MemberRegisterType.BLACKLIST);
                            if (memberRegisterResponse.getResponseType().equals(RegisterConstants.RegisterViewResponse.VALID.getValue())) {
                                String strResponse = strHeader + "\nSorry, an error occurred while processing your request.\n\nERR_ACCBL300";

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strResponse = strHeader + "\nEnter Mobile Number:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_MOBILE, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {

                            String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CUSTOMER.name());

                            String strAccountsHeader = strHeader + " \n{Select a valid source account}\n";
                            String strCustomerHeader = strHeader + " \nSelect source member\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END);
                            } else {
                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT,
                                        AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END);
                            }
                        }
                        break;
                    }

                    case FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_MOBILE: {
                        String strMobileNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_MOBILE.name());

                        strMobileNumber = AppUtils.sanitizePhoneNumber(strMobileNumber);

                        if (!strMobileNumber.equalsIgnoreCase("INVALID MOBILE NUMBER")) {
                            String strResponse = strHeader + "\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else {
                            String strFullHeader = strHeader + "\n{Enter a valid mobile number}\nEnter Mobile Number.\n";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strFullHeader, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_MOBILE, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }

                    case FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT: {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT.name());
                        if (strAmount.matches("^[1-9][0-9]*$")) {
                            String strResponse = strHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            String strFundsTransferMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMinimum();
                            String strFundsTransferMaximum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.EXTERNAL_FUNDS_TRANSFER).getMaximum();

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            double dblAvailableBalance = 0;
                            try {
                                dblAvailableBalance = Double.parseDouble(strSourceAccountAvailableBalance);
                            } catch (Exception e) {
                            }

                            double dblFundsTransferMinimum = Double.parseDouble(strFundsTransferMinimum);
                            double dblFundsTransferMaximum = Double.parseDouble(strFundsTransferMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            double dblDailyLimitRemainingAmount = CBSAPI.dailyTotalMoneyOut(strSourceAccountNo, "BANK_TRANSFER");

                            dblFundsTransferMaximum = Math.min(dblFundsTransferMaximum, dblDailyLimitRemainingAmount);

                            if (dblAmountEntered < dblFundsTransferMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strFundsTransferMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblDailyLimitRemainingAmount <= 0) {
                                strResponse = strHeader + "\nSorry, the remaining amount you can transact today is KES 0.";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END, "NO", theArrayListUSSDSelectOption);
                            } else if (dblAmountEntered > dblFundsTransferMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strFundsTransferMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (Double.parseDouble(strAmount) > dblAvailableBalance) {
                                strResponse = strHeader + "\n{" + strSourceAccountLabel + " avail bal KES " + Utils.formatDouble(dblAvailableBalance, "#,##0.00") + " is INSUFFICIENT to withdraw KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        } else {
                            String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_PIN: {
                        String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                        String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_PIN.name());
                        if (strLoginPIN.equals(strPIN)) {

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strDestinationMobile = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_MOBILE.name());

                            String strResponse = "Confirm " + strHeader + " \nA/C: " + strDestinationMobile + "\nFrom A/C: " + strSourceAccountName + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                        } else {
                            String strResponse = strHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CONFIRMATION: {
                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CONFIRMATION.name());
                        if (strConfirmation.equalsIgnoreCase("YES")) {

                            String strResponse = "Dear member, your " + strHeader + " has been received successfully and is being processed.";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. Funds Transfer request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            String strDestinationMobile = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_MOBILE.name());

                            String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                            String strSourceAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_FROM_ACCOUNT.name());
                            HashMap<String, String> hmAccountDetails = Utils.toHashMap(strSourceAccountDetails);

                            String strSourceCustomerIdentifier = hmAccountDetails.get("cust_id");
                            String strSourceAccountNo = hmAccountDetails.get("ac_no").trim();
                            String strSourceAccountName = hmAccountDetails.get("ac_name").trim();;
                            String strSourceAccountLabel = hmAccountDetails.get("ac_label");
                            String strSourceAccountAvailableBalance = hmAccountDetails.get("ac_bal");

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_AMOUNT.name());

                            TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.BANK_TRANSFER.getValue(),
                                    Double.parseDouble(strAmount));

                            String strCharge = "";
                            if (chargesWrapper.hasErrors()) {
                                strCharge = "";
                            } else {
                                strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                            }

                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strResponse = "Confirm Funds Transfer via PesaLink\n{Select a valid menu}\nA/C: " + strDestinationMobile + "\nFrom A/C: " + strSourceAccountName + " - " + strSourceAccountNo + "\nAmount: KES " + strAmount + strCharge;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                        }
                        break;
                    }
                    default: {
                        System.err.println("theAppMenus.displayMenu_FundTransfer() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Funds Transfer\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_MOBILE_TRANSFER_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_FundTransfer() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;

    }

    default USSDResponse getExternalFundsTransferOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "M-PESA", "1: M-PESA");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "PESALINK", "2: PesaLink");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_EXTERNAL_TRANSFER_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getExternalFundsTransferOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getExternalFundsTransferPesalinkOptionMenu(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "MOBILE_NUMBER", "1: Mobile Number");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "BANK_ACCOUNT", "1: Bank Account");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.FUNDS_TRANSFER_PESALINK_OPTION, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("theAppMenus.getExternalFundsTransferOptionMenu() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


}
