package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.authentication_manager.MobileBankingCryptography;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.co.skyworld.smp.utility_items.enums.ReturnValue;
import ke.co.skyworld.smp.utility_items.security.HashUtils;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;

public interface SetPINMenus {
    public default USSDResponse displayMenu_SetPIN(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {
            switch (theParam) {
                case "PIN": {
                    String strResponse = "Provide information below to set your PIN\nEnter your National ID:";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_ID_NO, USSDConstants.USSDInputType.STRING, "NO");
                    break;
                }
                case "ID_NO": {
                    String strIDNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SET_PIN_ID_NO.name());
                    if (strIDNo.matches("^[A-Za-z0-9]{4,15}$")) {

                        TransactionWrapper<FlexicoreHashMap> validKYCDetailsWrapper = theUSSDAPI.isValidKYCDetails(theUSSDRequest, strIDNo);
                        FlexicoreHashMap validKYCDetailsMap = validKYCDetailsWrapper.getSingleRecord();

                        if (validKYCDetailsWrapper.hasErrors()) {

                            String strResponse = "Set PIN\n"+validKYCDetailsMap.getStringValue("display_message");

                            //USSDAPIConstants.Condition isValidKYCDetails = validKYCDetailsMap.getValue("cbs_api_return_val");
                            USSDAPIConstants.Condition endSession = validKYCDetailsMap.getValue("end_session");

                            if (endSession == USSDAPIConstants.Condition.NO) {
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_ID_NO, USSDConstants.USSDInputType.STRING, "NO");

                            } else {
                                strResponse = validKYCDetailsMap.getStringValue("display_message");

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                            }
                        } else {
                            String strResponse = "Set PIN\nEnter your new PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_NEW_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                    } else {
                        String strResponse = "Set PIN\n{Please enter a valid National ID Number}\nEnter your National ID:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_ID_NO, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }
                case "NEW_PIN": {

                    String strNewPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SET_PIN_NEW_PIN.name());
                    if (strNewPIN.matches("^[0-9]{4,15}$")) {
                        if (theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name()).equals(theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SET_PIN_NEW_PIN.name()))) {
                            String strResponse = "Set PIN\n{New PIN should not be same as Current PIN}\nEnter your new PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_NEW_PIN, USSDConstants.USSDInputType.STRING, "NO");

                        } else {

                            TransactionWrapper<FlexicoreHashMap> currentUserWrapper = theUSSDAPI.getCurrentUserDetails(theUSSDRequest);
                            FlexicoreHashMap currentUserDetailsMap = currentUserWrapper.getSingleRecord();
                            FlexicoreHashMap mobileBankingDetailsMap = currentUserDetailsMap.getFlexicoreHashMap("mobile_register_details");

                            String previousPasswords = mobileBankingDetailsMap.getStringValueOrIfNull("previous_pins", "<PREVIOUS_PINS/>");

                            Document docPrevPasswords = XmlUtils.parseXml(previousPasswords);
                            NodeList allprevPasswordsList = null;

                            try {
                                allprevPasswordsList = XmlUtils.getNodesFromXpath(docPrevPasswords, "/PREVIOUS_PINS/PIN");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            boolean hasUsedPinBefore = false;

                            if (allprevPasswordsList != null) {

                                int intMin = 0;

                                if(allprevPasswordsList.getLength() > 5){
                                    intMin = 5;
                                }

                                for (int i = allprevPasswordsList.getLength() - 1; i >= intMin; i--) {
                                    Node node = allprevPasswordsList.item(i);
                                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                                        continue;
                                    }

                                    Element element = (Element) node;
                                    if (element.getTextContent().equalsIgnoreCase(MobileBankingCryptography.hashPIN(String.valueOf(theUSSDRequest.getUSSDMobileNo()), strNewPIN))) {
                                        hasUsedPinBefore = true;
                                        break;
                                    }
                                }
                            }

                            String strResponse;
                            if (hasUsedPinBefore) {
                                strResponse = "Set PIN\n{Please provide a new PIN that you have not used before}\nEnter your new PIN:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_NEW_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            } else {
                                strResponse = "Set PIN\nConfirm your new PIN:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_CONFIRM_PIN, USSDConstants.USSDInputType.STRING, "NO");

                            }
                        }
                    } else {
                        String strResponse = "Set PIN\n{Please enter a valid PIN}\nEnter your new PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_NEW_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "CONFIRM_PIN": {
                    if (theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SET_PIN_CONFIRM_PIN.name()).equals(theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SET_PIN_NEW_PIN.name()))) {

                        TransactionWrapper<FlexicoreHashMap> setPINWrapper = theUSSDAPI.setPIN(theUSSDRequest);
                        FlexicoreHashMap setPINMap = setPINWrapper.getSingleRecord();
                        if (setPINWrapper.hasErrors()) {
                            //USSDAPIConstants.Condition endSession = setPINMap.getValue("end_session");
                            String strResponse = setPINMap.getStringValue("display_message");

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.SET_PIN_END, "NO", theArrayListUSSDSelectOption);

                        } else {
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

                            String strResponse = "Set PIN\nYour new PIN has been set successfully. Select an option below to proceed.\n";
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.INIT.name(), "00: Login");
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.SET_PIN_END, "NO", theArrayListUSSDSelectOption);
                        }

                    } else {
                        String strResponse = "Set PIN\n{New PIN mismatch}\nConfirm your new PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.SET_PIN_CONFIRM_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "END": {
                    String strResponse = "Set PIN\n{Invalid menu selected}\nPlease select an option below\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    //LINK OPTION - Force user to login after error at the end.
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.INIT.name(), "00: Login");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.SET_PIN_END, "NO", theArrayListUSSDSelectOption);
                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_SetPIN() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = "Set PIN\n{Sorry, an error has occurred while processing Set PIN}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    //LINK OPTION
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "00", "_LINK", AppConstants.USSDDataType.LOGIN_PIN.name(), "00: Retry to set PIN");
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithExit(theUSSDRequest, AppConstants.USSDDataType.SET_PIN_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_SetPIN() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }


} // End interface AppAuthMenus
