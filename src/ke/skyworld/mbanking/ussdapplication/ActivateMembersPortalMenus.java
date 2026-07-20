package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import java.util.ArrayList;

public interface ActivateMembersPortalMenus {

    public default USSDResponse displayMenu_MembersPortal(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        final USSDAPI theUSSDAPI = new USSDAPI();

        try {
            String strHeader = "Members Portal";
            USSDAPIConstants.AccountType accountType = USSDAPIConstants.AccountType.ALL;

            switch (theParam) {
                case "MENU": {
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ACTIVATE_MEMBERS_PORTAL", "1: ACTIVATE Members Portal");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "DISABLE_MEMBERS_PORTAL", "2: DISABLE Members Portal");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_ACTION, "NO", theArrayListUSSDSelectOption);

                    break;
                }
                case "ACTION": {

                    String strAction = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WAPP_ACTIVATION_ACTION.name());

                    String strResponse = "";

                    switch (strAction) {
                        case "ACTIVATE_MEMBERS_PORTAL": {
                            strResponse = strHeader + "\nProceed to ACTIVATE your Members Portal?\n";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        case "DISABLE_MEMBERS_PORTAL": {
                            strResponse = strHeader + "\nProceed to DISABLE your Members Portal?\n";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {
                            strHeader = strHeader + "\n{Select a valid menu}\n";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();

                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ACTIVATE_MEMBERS_PORTAL", "1: ACTIVATE Members Portal");
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "DISABLE_MEMBERS_PORTAL", "2: DISABLE Members Portal");
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_ACTION, "NO", theArrayListUSSDSelectOption);
                        }
                    }

                    break;
                }
                case "CONFIRMATION": {

                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WAPP_ACTIVATION_CONFIRMATION.name());
                    String strAction = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.WAPP_ACTIVATION_ACTION.name());

                    switch (strConfirmation) {
                        case "YES": {
                            String strResponse = "";
                            String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                            String strSessionID = Long.toString(theUSSDRequest.getUSSDSessionID());
                            strSessionID = "USSD_" + strSessionID;

                            if (strAction.equalsIgnoreCase("ACTIVATE_MEMBERS_PORTAL")) {
                                TransactionWrapper<FlexicoreHashMap> subscribeToMembersPortalWrapper = CBSAPI.subscribeToMembersPortal(strMobileNo, theUSSDRequest);
                                FlexicoreHashMap flexicoreHashMap = subscribeToMembersPortalWrapper.getSingleRecord();

                                strResponse = flexicoreHashMap.getStringValue("display_message");

                            } else {

                                TransactionWrapper<FlexicoreHashMap> subscribeToMembersPortalWrapper = CBSAPI.deactivateMembersPortal(strMobileNo, theUSSDRequest);
                                FlexicoreHashMap flexicoreHashMap = subscribeToMembersPortalWrapper.getSingleRecord();
                                strResponse = flexicoreHashMap.getStringValue("display_message");
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                            break;
                        }
                        case "NO": {
                            String strResponse = "";

                            if (strAction.equalsIgnoreCase("ACTIVATE_MEMBERS_PORTAL")) {
                                strResponse = "Dear member, your request to ACTIVATE Members Portal was NOT confirmed. Members Portal Activation request NOT COMPLETED.\n";
                            } else {
                                strResponse = "Dear member, your request to DISABLE Members Portal was NOT confirmed.\n";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {
                            String strResponse = "";
                            if (strAction.equalsIgnoreCase("ACTIVATE_MEMBERS_PORTAL")) {
                                strResponse = strHeader + "\n{Select a valid menu}\nProceed to ACTIVATE your Members Portal?\n";
                            } else {
                                strResponse = strHeader + "\n{Select a valid menu}\nProceed to DISABLE your Members Portal?\n";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                    }
                    break;
                }
                default: {

                    strHeader = strHeader + "\n{Select a valid menu}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ACTIVATE_MEMBERS_PORTAL", "1: ACTIVATE Members Portal");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "DISABLE_MEMBERS_PORTAL", "2: DISABLE Members Portal");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.WAPP_ACTIVATION_ACTION, "NO", theArrayListUSSDSelectOption);

                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_MembersPortal() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


}
