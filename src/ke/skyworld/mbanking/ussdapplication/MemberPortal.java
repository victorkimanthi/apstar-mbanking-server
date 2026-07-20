package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.mbanking.cbs.ApStarCBS;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import java.util.ArrayList;

public interface MemberPortal {

    public default USSDResponse displayMenu_MembersPortal(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        final USSDAPI theUSSDAPI = new USSDAPI();

        try {
            String strHeader = "Member's Portal";
            USSDAPIConstants.AccountType accountType = USSDAPIConstants.AccountType.ALL;

            switch (theParam) {
                case "MENU": {
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ENROLL_MEMBERS_PORTAL", "1: Enroll");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "PASSWORD_RESET_MEMBERS_PORTAL", "2: Reset Password");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "DISABLE_MEMBERS_PORTAL", "2: Disable");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_ACTION, "NO", theArrayListUSSDSelectOption);

                    break;
                }
                case "ACTION": {

                    String strAction = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_ACTION.name());


                    if (strAction != null) {

                        //pin
                        if (strAction.equalsIgnoreCase("ENROLL_MEMBERS_PORTAL")) {
                            String strResponse = strHeader + "\nPlease enter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (strAction.equalsIgnoreCase("PASSWORD_RESET_MEMBERS_PORTAL")) {
                            String strResponse = strHeader + "\nPlease enter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (strAction.equalsIgnoreCase("DISABLE_MEMBERS_PORTAL")) {
                            String strResponse = strHeader + "\nPlease enter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }

                    } else {
                        strHeader = strHeader + "\n{Select a valid menu}\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ENROLL_MEMBERS_PORTAL", "1: Enroll");
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "PASSWORD_RESET_MEMBERS_PORTAL", "2: Reset Password");
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "DISABLE_MEMBERS_PORTAL", "2: Disable");
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MAPP_ACTIVATION_ACTION, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                    break;
                }


                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MEMBERS_PORTAL_ACTIVATION_PIN.name());
                    String strAction = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MAPP_ACTIVATION_ACTION.name());

                    if (strLoginPIN.equals(strPIN)) {
                        String strResponse = strHeader + "\nProceed to ACTIVATE your Member's Portal?\n";
                        if (strAction.equalsIgnoreCase("ENROLL_MEMBERS_PORTAL")) {
                            strResponse = strHeader + "\nProceed to ENROLL for Member's Portal?\n";
                        } else if (strAction.equalsIgnoreCase("DISABLE_MEMBERS_PORTAL")) {
                            strResponse = strHeader + "\nProceed to DISABLE your Member's Portal?\n";
                        } else {
                            strResponse = strHeader + "\nProceed to ACTIVATE your Member's Portal?\n";
                        }

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strResponse = strHeader + "\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }
                }


                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MEMBERS_PORTAL_ENROLLMENT_CONFIRMATION.name());
                    String strAction = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MAPP_ACTIVATION_ACTION.name());
                    switch (strConfirmation) {
                        case "YES": {
                            String strResponse = "";
                            String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                            String strSessionID = Long.toString(theUSSDRequest.getUSSDSessionID());
                            strSessionID = "USSD_" + strSessionID;

                            if (strAction.equalsIgnoreCase("ENROLL_MEMBERS_PORTAL")) {
                                String finalStrSessionID = strSessionID;
                                Thread worker = new Thread(() -> {
                                    String strMSG = "";
                                    USSDAPIConstants.TransactionReturnVal theResponseStatus = null;//theUSSDAPI.activateMobileApp(theUSSDRequest);

                                    switch (theResponseStatus) {
                                        case SUCCESS: {
                                            strMSG = "Dear member, your request to Enroll for Member's Portal has been received successfully.";
                                            break;
                                        }
                                        case ERROR:
                                        default: {
                                            strMSG = "Dear member, your request to Enroll for Member's Portal has FAILED. Please try again. If enrolling for Member's Portal fails again, contact the SACCO for assistance.";
                                            break;
                                        }
                                    }

                                    theUSSDAPI.sendSMS(strMobileNo, strMSG, MSGConstants.MSGMode.EXPRESS, 200, "MAPP_ACTIVATION", theUSSDRequest);
                                });
                                worker.start();

                                strResponse = "Your request to Enroll for Member's Portal has been received successfully. Confirmation will be sent to you via SMS.";
                            } else {
                                String finalStrSessionID = strSessionID;
                                Thread worker = new Thread(() -> {
                                    String strMSG = "";
                                    USSDAPIConstants.TransactionReturnVal theResponseStatus = theUSSDAPI.deactivateMobileApp(theUSSDRequest);

                                    switch (theResponseStatus) {
                                        case SUCCESS: {
                                            strMSG = "Dear member, your request to Enroll for Member's Portal has been received successfully.";
                                            break;
                                        }
                                        case ERROR:
                                        default: {
                                            strMSG = "Dear member, your request to DISABLE Mobile App has FAILED. Please try again. If disabling Mobile App fails again, contact the SACCO for assistance.";
                                            break;
                                        }
                                    }

                                    theUSSDAPI.sendSMS(strMobileNo, strMSG, MSGConstants.MSGMode.EXPRESS, 200, "MAPP_ACTIVATION", theUSSDRequest);
                                });
                                worker.start();

                                strResponse = "Your request to DISABLE Mobile App has been received successfully. Confirmation will be sent to you via SMS.";

                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                            break;
                        }
                        case "NO": {
                            String strResponse = "";

                            if (strAction.equalsIgnoreCase("ACTIVATE_MOBILE_APP")) {
                                strResponse = "Dear member, your request to ACTIVATE Mobile App was NOT confirmed. Mobile App Activation request NOT COMPLETED.\n";
                            } else {
                                strResponse = "Dear member, your request to DISABLE Mobile App was NOT confirmed.\n";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MAPP_ACTIVATION_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {
                            strHeader = strHeader + "\n{Select a valid menu}\n";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ENROLL_MEMBERS_PORTAL", "1: Enroll");
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "PASSWORD_RESET_MEMBERS_PORTAL", "2: Reset Password");
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "DISABLE_MEMBERS_PORTAL", "2: Disable");
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MAPP_ACTIVATION_ACTION, "NO", theArrayListUSSDSelectOption);

                            break;
                        }
                    }
                    break;
                }
                default: {

                    strHeader = strHeader + "\n{Select a valid menu}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader);
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "ENROLL_MEMBERS_PORTAL", "1: Enroll");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "PASSWORD_RESET_MEMBERS_PORTAL", "2: Reset Password");
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "DISABLE_MEMBERS_PORTAL", "2: Disable");

                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MAPP_ACTIVATION_ACTION, "NO", theArrayListUSSDSelectOption);

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
