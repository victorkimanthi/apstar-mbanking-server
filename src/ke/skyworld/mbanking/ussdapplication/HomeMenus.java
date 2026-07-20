package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.ussd.*;
import ke.skyworld.mbanking.cbs.ApStarCBS;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.w3c.dom.Document;

import javax.net.ssl.HttpsURLConnection;
import java.util.ArrayList;

import static ke.co.skyworld.smp.comm_channels_manager.SMSManager.sendSMS;
import static ke.skyworld.mbanking.cbs.CBSAPI.*;
import static ke.skyworld.mbanking.ussdapi.APIUtils.getAllowedNumbers;

public interface HomeMenus {

    default USSDResponse displayMenu_Init(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        try {

            String strResponse;

            String strSettingsXML = SystemParameters.getParameter("MBANKING_SERVICES_MANAGEMENT");
            Document document = XmlUtils.parseXml(strSettingsXML);

            String strUssdEnabled = XmlUtils.getTagValue(document, "/MBANKING_SERVICES/USSD/@STATUS");

            System.out.println("strUssdEnabled: " + strUssdEnabled);
            String strUssdDisplayMessage = XmlUtils.getTagValue(document, "/MBANKING_SERVICES/USSD/@MESSAGE");
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
            String strUSSDCode = String.valueOf(theUSSDRequest.getUSSDCode());
            String strUSSDSubCode = String.valueOf(theUSSDRequest.getUSSDSubCode());

            if (!strUssdEnabled.equalsIgnoreCase("ACTIVE")) {
                theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strUssdDisplayMessage, "NO");
            } else {
           /*     if (strUSSDCode.equals(AppConstants.strSACCOUSSDCode) ) {
                    String strHeader = "Welcome to " + AppConstants.strSACCOName;
                    theUSSDResponse = displayMenu_GeneralMenus(theUSSDRequest, theParam, strHeader);
                    return theUSSDResponse;
                }
*/
                TransactionWrapper<FlexicoreHashMap> checkUserWrapper = theUSSDAPI.checkUser(theUSSDRequest);
       /*         System.out.println("checkUserWrapper: " + checkUserWrapper);
                System.out.println("************************************************************");*/
                if (checkUserWrapper.hasErrors()) {
                    FlexicoreHashMap checkUserMap = checkUserWrapper.getSingleRecord();
                    strResponse = checkUserMap.getStringValue("display_message");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                } else {


                   /* strResponse = "Welcome to " + AppConstants.strMobileBankingName + " Mobile Banking Services.\n\nPlease enter your PIN to proceed:";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOGIN_PIN, USSDConstants.USSDInputType.STRING, "NO");


*/
                   /* if(strMobileNumber.equals("254720259655") || strMobileNumber.equals("254729566788")
                            || strMobileNumber.equals("254720416494") || strMobileNumber.equals("254722832021")
                            || strMobileNumber.equals("254722793859") || strMobileNumber.equals("254726589392")
                            || strMobileNumber.equals("254728432445") || strMobileNumber.equals("254713000249")
                            || strMobileNumber.equals("254716304210") ||strMobileNumber.equals("254723782649") || strMobileNumber.equals("254729692224") || strMobileNumber.equals("254114041681") || strMobileNumber.equals("254726958265") || strMobileNumber.equals("254722378923")

                            || strMobileNumber.equals("254722793859XX") || strMobileNumber.equals("254729692224XX")) {*/
                        strResponse = "Welcome to " + AppConstants.strMobileBankingName + " Mobile Banking Services.\n\nPlease enter your PIN to proceed:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOGIN_PIN, USSDConstants.USSDInputType.STRING, "NO");

                /*}else{
                        //strResponse = "Sorry, your mobile number is not registered for " + AppConstants.strMobileBankingName + " Mobile Banking Services.\n\nPlease contact customer care for assistance.";
                        //under maintanance
                        strResponse = "Sorry, " + AppConstants.strMobileBankingName + " Mobile Banking Services are currently under Maintenance.\n\nPlease try again later.";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                    }*/
                }


                //theUSSDResponse = displayMenu_GeneralMenus(theUSSDRequest, theParam, "Welcome to "+AppConstants.strMobileBankingName);
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            if (theUSSDResponse != null) {

            }
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_Login(USSDRequest theUSSDRequest, String theParam) {
        USSDAPI theUSSDAPI = new USSDAPI();
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            String strResponse;

            TransactionWrapper<FlexicoreHashMap> userLoginWrapper = theUSSDAPI.userLogin(theUSSDRequest);
            FlexicoreHashMap userLoginMap = userLoginWrapper.getSingleRecord();

            if (userLoginWrapper.hasErrors()) {
                strResponse = userLoginMap.getStringValue("display_message");

                Object cbsApiReturnVal = userLoginMap.getStringValue("cbs_api_return_val");
                USSDAPIConstants.Condition endSession = userLoginMap.getValue("end_session");

                if (endSession == USSDAPIConstants.Condition.YES) {
                    theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                } else {
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOGIN_PIN, USSDConstants.USSDInputType.STRING, "NO");
                }
            } else {

                FlexicoreHashMap mobileBankingMap = userLoginMap.getValue("mobile_register_details");
                String strAcceptedTermsAndConditions = mobileBankingMap.getStringValue("accepted_terms_and_conditions");
                String strUSSDActivationKYC = mobileBankingMap.getStringValue("ussd_activation_kyc");
                String strPINStatus = mobileBankingMap.getStringValueOrIfNull("pin_status", "");

                if (strAcceptedTermsAndConditions.equalsIgnoreCase("NO")) {
                    String strHeader = "Privacy Statement\nI confirm that I have read and understood the Privacy Statement of using " + AppConstants.strMobileBankingName + " Mobile Banking Services\n";
                    theUSSDResponse = displayMenu_TermsAndConditionsMenus(theUSSDRequest, strHeader);
                } else {




                    if (strUSSDActivationKYC.equalsIgnoreCase("ENABLED") || strPINStatus.equalsIgnoreCase("RESET")) {
                        theUSSDResponse = theAppMenus.displayMenu_SetPIN(theUSSDRequest, "PIN");
                    } else {
                        theUSSDResponse = theAppMenus.displayMenu_MainInMenus(theUSSDRequest, theParam, AppConstants.strHomeMenuHeader);
                    }
                }


                /*FlexicoreHashMap signatoryMap = userLoginMap.getValue("signatory_details");

                TransactionWrapper<FlexicoreHashMap> customerDetails = ProfitsCBS.getCustomerDetails(UUID.randomUUID().toString(),
                        signatoryMap.getStringValue("identifier"), signatoryMap.getStringValue("status"));

                if (customerDetails.hasErrors()) {
                    strResponse = "Sorry, an error occurred while processing your request. Please try again later";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                } else {
                    String strNewPhoneNumber = customerDetails.getSingleRecord().getStringValueOrIfNull("primary_mobile_number", "").trim();

                    //TODO: REMOVE THIS PLEASE!!!!
                    if (!strNewPhoneNumber.equalsIgnoreCase(String.valueOf(theUSSDRequest.getUSSDMobileNo())) &&
                            !String.valueOf(theUSSDRequest.getUSSDMobileNo()).equalsIgnoreCase("254706405989") &&
                            !String.valueOf(theUSSDRequest.getUSSDMobileNo()).equalsIgnoreCase("254790491947")
                    ) {
                        strResponse = "Sorry, your current mobile number cannot use " + AppConstants.strMobileBankingName + " mobile banking services.\n\nERR_MOB350\nPlease try again later.";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                    }else{


                    }
                }*/
            }


        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_MainInMenus(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponseSELECT theUSSDResponse = new USSDResponseSELECT();
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {
            //SELECT
            theUSSDResponse.setUSSDSessionID(theUSSDRequest.getUSSDSessionID());
            theUSSDResponse.setUSSDAction(USSDConstants.USSDAction.CON);
            theUSSDResponse.setUSSDCharge("NO");

            theUSSDResponse.setUSSDSelectDataType(AppConstants.USSDDataType.MAIN_IN_MENU.getValue());
            theUSSDResponse.setUSSDSelectName(AppConstants.USSDDataType.MAIN_IN_MENU.name());
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            //OPTIONS
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();


            String strSettingsXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
            Document document = XmlUtils.parseXml(strSettingsXML);

            String strRequestForDividendsEnabled = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/REQUEST_FOR_DIVIDENDS/@ENABLED");

                if (strRequestForDividendsEnabled.equalsIgnoreCase("YES")) {

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "REQUEST_FOR_DIVIDENDS", "1: Request For Dividends");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BALANCE_ENQUIRY", "2: Balance Enquiry");

                } else {

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
                    //check if member has dormant account
                    TransactionWrapper<FlexicoreHashMap> flexicoreHashMapTransactionWrapper = ApStarCBS.checkFOSAAccountStatus("MSISDN", String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                    FlexicoreHashMap fxhashmapStatus = flexicoreHashMapTransactionWrapper.getSingleRecord();
                    FlexicoreHashMap getChargesMap = fxhashmapStatus.getFlexicoreHashMap("response_payload");
                    String accountStatus = getChargesMap.getStringValue("account_status");
                    System.out.println("accountStatus: " + accountStatus);

                    if (accountStatus != null && !accountStatus.equals("ACTIVE")) {
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ACTIVATE_SAVINGS_ACC", "1: Activate Savings Account");

                    } else {
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "BALANCE_ENQUIRY", "1: Balance Enquiry");

                    }
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "WITHDRAWAL", "2: Cash Withdrawal");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "DEPOSIT", "3: Deposit");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "4", "LOAN", "4: Loans");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "5", "MY_ACCOUNT", "5: My Account");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "6", "FUNDS_TRANSFER", "6: Funds Transfer");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "7", "UTILITIES", "7: Utilities");

                    //enroll members portal
                    // if(getAllowedNumbers().contains(strMobileNumber)) {

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "8", "MEMBERS_PORTAL", "8: Portal");

                    //  }
                }

            USSDResponseSELECTOption.setUSSDSelectOptionEXIT(theArrayListUSSDSelectOption, AppConstants.USSDDiplayText.EXIT.getValue());

            theUSSDResponse.setUSSDSelectOption(theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
        }

        return theUSSDResponse;

    }

    default USSDResponse displayMenu_MainIn(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            String strLastKey = (String) theUSSDRequest.getUSSDData().keySet().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() - 1];
            //System.out.println("MAIN IN strLastKey: " +strLastKey);
            //System.out.println("MAIN IN strLastValue: " +strLastValue);
            if (strLastValue.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.name()) && (USSDConstants.arrBranchOptionNames.contains(strLastKey))) { //If the last entry is from LOGIN_PIN then display MAIN_IN_MENU
                theUSSDResponse = theAppMenus.displayMenu_MainInMenus(theUSSDRequest, theParam, AppConstants.strHomeMenuHeader);
            } else {
                String strMainInMenu = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MAIN_IN_MENU.name());
                switch (strMainInMenu) {
                    case "WITHDRAWAL": {
                        theUSSDResponse = theAppMenus.displayMenu_Withdrawal(theUSSDRequest, theParam);
                        break;
                    }

                    case "ACTIVATE_SAVINGS_ACC": {
                        theUSSDResponse = theAppMenus.displayMenu_SavingsAccount(theUSSDRequest, theParam);
                        break;
                    }
                    case "UTILITIES": {
                        theUSSDResponse = theAppMenus.displayMenu_Utilities(theUSSDRequest, theParam);
                        break;
                    }
                    case "DEPOSIT": {
                        theUSSDResponse = theAppMenus.displayMenu_Deposit(theUSSDRequest, theParam);
                        break;
                    }
                    case "MY_ACCOUNT": {
                        theUSSDResponse = theAppMenus.displayMenu_MyAccount(theUSSDRequest, theParam);
                        break;
                    }
                    case "LOAN": {
                        theUSSDResponse = theAppMenus.displayMenu_Loan(theUSSDRequest, theParam);
                        break;
                    }
                    case "FUNDS_TRANSFER": {
                        theUSSDResponse = theAppMenus.displayMenu_FundTransfer(theUSSDRequest, theParam);
                        break;
                    }
                    case "BALANCE_ENQUIRY": {
                        theUSSDResponse = theAppMenus.displayMenu_BalanceEnquiry(theUSSDRequest, "MENU");
                        break;
                    }
                    case "REQUEST_FOR_DIVIDENDS": {
                        theUSSDResponse = theAppMenus.displayMenu_DividendsRequest(theUSSDRequest, "MENU");
                        break;
                    }
                    case "MEMBERS_PORTAL": {
                        theUSSDResponse = theAppMenus.displayMenu_MembersPortal(theUSSDRequest, "MENU");
                        break;
                    }
                    case "ACTION_PRODUCT_PURCHASE_REQUEST": {
                        theUSSDResponse = theAppMenus.displayMenu_MembersPortal(theUSSDRequest, "MENU");
                        break;
                    }


                    /*  case "BALANCE_ENQUIRY": {
                        theUSSDResponse = theAppMenus.displayMenu_BalanceEnquiry(theUSSDRequest, theParam);
                        break;
                    }
                    */

                    default: {
                        String strHeader = AppConstants.strHomeMenuHeader + "\n{Select a valid menu}";
                        theUSSDResponse = theAppMenus.displayMenu_MainInMenus(theUSSDRequest, theParam, strHeader);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    public default USSDResponse displayMenu_TermsAndConditionsMenus(USSDRequest theUSSDRequest, String theHeader) {

        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            //String strResponse = "Privacy Statement\nI confirm that I have read and understood the Privacy Statement of using " + AppConstants.strMobileBankingName + " Mobile Banking Services\n";
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "YES", "1: Yes");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "NO", "2: No");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "READ", "3: Read Our Terms");

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_TermsAndConditions() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


    public default USSDResponse displayMenu_TermsAndConditions(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

        try {
            switch (theParam) {
                case "TC": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.TERMS_AND_CONDITIONS.name());

                    if (strConfirmation.trim().isEmpty()) {
                        String strHeader = "Privacy Statement\n{Select a valid option}\nI confirm that I have read and understood the Privacy Statement of using " + AppConstants.strMobileBankingName + " Mobile Banking Services\n";
                        theUSSDResponse = displayMenu_TermsAndConditionsMenus(theUSSDRequest, strHeader);
                    } else if (strConfirmation.equalsIgnoreCase("YES")) {

                        TransactionWrapper<FlexicoreHashMap> userDetailsWrapper = theUSSDAPI.getCurrentUserDetails(theUSSDRequest);
                        FlexicoreHashMap userDetailsMap = userDetailsWrapper.getSingleRecord();

                        if (userDetailsWrapper.hasErrors()) {
                            String strResponse = userDetailsMap.getStringValue("display_message");
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS_END, "NO", theArrayListUSSDSelectOption);

                        } else {

                            FlexicoreHashMap mobileBankingMap = userDetailsMap.getValue("mobile_register_details");

                            TransactionWrapper<FlexicoreHashMap> acceptTermsAndConditionsWrapper = theUSSDAPI.acceptTermsAndConditions(theUSSDRequest, mobileBankingMap);
                            FlexicoreHashMap acceptTermsAndConditionsMap = acceptTermsAndConditionsWrapper.getSingleRecord();
                            if (acceptTermsAndConditionsWrapper.hasErrors()) {
                                //USSDAPIConstants.Condition endSession = acceptTermsAndConditionsMap.getValue("end_session");
                                String strResponse = acceptTermsAndConditionsMap.getStringValue("display_message");

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strUSSDActivationKYC = mobileBankingMap.getStringValue("ussd_activation_kyc");

                                if (strUSSDActivationKYC.equalsIgnoreCase("ENABLED")) {
                                    theUSSDResponse = theAppMenus.displayMenu_SetPIN(theUSSDRequest, "PIN");
                                } else {
                                    theUSSDResponse = theAppMenus.displayMenu_MainInMenus(theUSSDRequest, theParam, AppConstants.strHomeMenuHeader);
                                }
                            }
                        }
                    } else if (strConfirmation.equalsIgnoreCase("READ")) {
                        //send sms on terms

                        String strResponse = "Privacy Statement\nYou will Receive an SMS Link to our " + AppConstants.strMobileBankingName + " Terms.\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.INIT.name(), "00: Login");
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS_END, "NO", theArrayListUSSDSelectOption);
                        String strSMS = "Dear Member, Please access our terms and conditions on the following link: https://apstarsacco.coop/by-laws/ \n " +
                                "If you have any questions, feel free to reach out to our customer support team.";
                        theUSSDAPI.sendSMS(strMobileNumber, strSMS, MSGConstants.MSGMode.SAF, 210, "BALANCE_ENQUIRY", theUSSDRequest);


                    } else {
                        String strResponse = "Privacy Statement\nSorry, you did not the accept the privacy Statement for using " + AppConstants.strMobileBankingName + ".\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.INIT.name(), "00: Login");
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS_END, "NO", theArrayListUSSDSelectOption);
                    }
                    break;
                }
                case "END": {
                    String strResponse = "Privacy Statement\n{Invalid menu selected}\nPlease select an option below\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    //LINK OPTION - Force user to login after error at the end.
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.INIT.name(), "00: Login");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS_END, "NO", theArrayListUSSDSelectOption);
                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_TermsAndConditions() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = "Privacy Statement\n{Sorry, an error has occurred while processing Privacy Statement}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    //LINK OPTION
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.INIT.name(), "00: Login");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.TERMS_AND_CONDITIONS_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_TermsAndConditions() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_GeneralMenus(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponseSELECT theUSSDResponse = new USSDResponseSELECT();
        try {
            //SELECT
            theUSSDResponse.setUSSDSessionID(theUSSDRequest.getUSSDSessionID());
            theUSSDResponse.setUSSDAction(USSDConstants.USSDAction.CON);
            theUSSDResponse.setUSSDCharge("NO");

            theUSSDResponse.setUSSDSelectDataType(AppConstants.USSDDataType.GENERAL_MENU.getValue());
            theUSSDResponse.setUSSDSelectName(AppConstants.USSDDataType.GENERAL_MENU.name());

            //OPTIONS
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "LOGIN", "1: Login");
            //USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "BUY_GOODS", "2: Lipa Na "+AppConstants.strSACCOProductName + " (Buy Goods & Services)");

            USSDResponseSELECTOption.setUSSDSelectOptionEXIT(theArrayListUSSDSelectOption, AppConstants.USSDDiplayText.EXIT.getValue());

            //SELECT OPTIONSequalsIgnoreCase
            theUSSDResponse.setUSSDSelectOption(theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
        }

        return theUSSDResponse;

    }

    default USSDResponse displayMenu_General(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();
        try {
            String strLastValue = (String) theUSSDRequest.getUSSDData().values().toArray()[theUSSDRequest.getUSSDData().size() - 1];

            if (strLastValue.equalsIgnoreCase(AppConstants.USSDDataType.GENERAL_MENU.name())) {
                theUSSDResponse = theAppMenus.displayMenu_GeneralMenus(theUSSDRequest, theParam, AppConstants.strHomeMenuHeader);
            } else {
                String strGENERAL_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.GENERAL_MENU.name());
                System.out.println("strGENERAL_MENU: " + strGENERAL_MENU);
                switch (strGENERAL_MENU) {
                    case "LOGIN": {

                        String strResponse = "";

                        TransactionWrapper<FlexicoreHashMap> checkUserWrapper = theUSSDAPI.checkUser(theUSSDRequest);
                        if (checkUserWrapper.hasErrors()) {
                            FlexicoreHashMap checkUserMap = checkUserWrapper.getSingleRecord();
                            strResponse = checkUserMap.getStringValue("display_message");
                            theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                        } else {
                            strResponse = "Welcome to " + AppConstants.strMobileBankingName + " Mobile Banking Services.\n\nPlease enter your PIN to proceed:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOGIN_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }

                        break;
                    }

                    case "BUY_GOODS": {
                        theUSSDResponse = theAppMenus.displayMenu_BuyGoodsMenus(theUSSDRequest, theParam);
                        break;
                    }

                    default: {
                        String strHeader = AppConstants.strHomeMenuHeader + "\n{Select a valid menu}";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralMenus(theUSSDRequest, theParam, strHeader);
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

}
