package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.SystemTables;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_manager.util.SystemParameters;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.UUID;

public interface DividendsRequestsMenus {

    default USSDResponse displayMenu_DividendsRequest(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String theHeader = "Request for Dividends";

        try {

            switch (theParam) {
                case "MENU": {

                    FlexicoreHashMap flexicoreHashMap = CBSAPI.existingDividendsRequest(theUSSDRequest.getUSSDMobileNo());

                    if (flexicoreHashMap == null || flexicoreHashMap.isEmpty()) {
                        String strResponse = theHeader + "\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");

                    } else {
                        String strAmount = flexicoreHashMap.getStringValue("amount_requested");
                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strResponse = "Dear member, you have an existing " + theHeader + " of amount KES "+strAmount+". Please wait for it to be processed.\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DIVIDENDS_REQUEST_END, "NO", theArrayListUSSDSelectOption);
                    }
                    break;
                }

                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT.name());

                    if (strAmount.matches("^[1-9][0-9]*$")) {

                        String strOrgParametersXML = SystemParameters.getParameter(AppConstants.strSettingParamName);
                        Document document = XmlUtils.parseXml(strOrgParametersXML);

                        String strMinimumAmount = XmlUtils.getTagValue(document, "/MBANKING_SETTINGS/REQUEST_FOR_DIVIDENDS/Minimum");

                        double dblAmountEntered = Double.parseDouble(strAmount);
                        double dblMinimumAmount = Double.parseDouble(strMinimumAmount);

                        if (dblAmountEntered < dblMinimumAmount) {
                            String strResponse = theHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strMinimumAmount, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else {
                            String strResponse = theHeader + "\nEnter your PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DIVIDENDS_REQUEST_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        }
                    } else {
                        String strResponse = theHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DIVIDENDS_REQUEST_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT.name());
                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strResponse = "Confirm Request for Dividends\nAmount: KES " + strAmount + "\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.DIVIDENDS_REQUEST_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = theHeader + "\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DIVIDENDS_REQUEST_PIN, USSDConstants.USSDInputType.STRING, "NO");
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
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DIVIDENDS_REQUEST_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {

                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT.name());

                            FlexicoreHashMap signatoryHashMap = Repository.selectWhere(StringRefs.SENTINEL,
                                    SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES,
                                    new FilterPredicate("primary_mobile_number = :primary_mobile_number"),
                                    new FlexicoreHashMap().addQueryArgument(":primary_mobile_number",
                                            String.valueOf(theUSSDRequest.getUSSDMobileNo()))).getSingleRecord();

                            FlexicoreHashMap insertMap = new FlexicoreHashMap();
                            insertMap.put("identifier_type", signatoryHashMap.getStringValue("identifier_type"));
                            insertMap.put("identifier", signatoryHashMap.getStringValue("identifier"));
                            insertMap.put("identity_type", signatoryHashMap.getStringValue("identity_type"));
                            insertMap.put("identity", signatoryHashMap.getStringValue("identity"));
                            insertMap.put("full_name", signatoryHashMap.getStringValue("full_name"));
                            insertMap.put("phone_number", String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                            insertMap.put("disbursement_status", "UNPAID");
                            insertMap.put("disbursement_status_date", DateTime.getCurrentDateTime());
                            insertMap.put("amount_requested", strAmount);
                            insertMap.put("correlation_id", UUID.randomUUID().toString());
                            insertMap.put("date_created", DateTime.getCurrentDateTime());
                            insertMap.put("date_modified", DateTime.getCurrentDateTime());

                            CBSAPI.createDividendsRequest(insertMap);

                            String strResponse = "Dear member, your " + theHeader + " request has been received successfully. Please wait shortly as it is being processed.";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DIVIDENDS_REQUEST_END, "NO", theArrayListUSSDSelectOption);

                            break;
                        }
                        case "NO": {
                            String strResponse = "Dear member, your " + theHeader + " request NOT confirmed. " + theHeader + " request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DIVIDENDS_REQUEST_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {
                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.DIVIDENDS_REQUEST_AMOUNT.name());
                            strAmount = Utils.formatDouble(strAmount, "#,##0.00");

                            String strResponse = "Confirm " + theHeader + "\n{Select a valid menu}\nAmount: KES " + strAmount + "\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.DIVIDENDS_REQUEST_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                    }
                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_DividendsRequest() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = theHeader + "\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DIVIDENDS_REQUEST_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_DividendsRequest() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

}
