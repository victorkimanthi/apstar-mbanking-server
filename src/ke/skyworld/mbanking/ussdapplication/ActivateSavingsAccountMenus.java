package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.mbanking.cbs.ApStarCBS;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPI;

import java.util.ArrayList;

public interface ActivateSavingsAccountMenus {


    public default  USSDResponse displayMenu_SavingsAccount(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        final USSDAPI theUSSDAPI = new USSDAPI();

        try{
            String strHeader = "Savings Account Activation";

            switch (theParam){
                case "MENU": {
                    String strResponse =  strHeader + "\nEnter your mobile banking PIN\n";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_PIN, USSDConstants.USSDInputType.STRING,"NO");
                    break;
                }
                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_PIN.name());
                    if(strLoginPIN.equals(strPIN)){

                        String strResponse =  strHeader + "\nProceed to ACTIVATE your savings account?\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_CONFIRMATION, "NO",theArrayListUSSDSelectOption);
                    }else{
                        String strResponse = strHeader+"\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest,strResponse, AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_PIN, USSDConstants.USSDInputType.STRING,"NO");
                    }

                    break;
                }
                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {
                            String strResponse;
                            String strMobileNo = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                            String strSessionID = String.valueOf(theUSSDRequest.getUSSDSessionID());

                            TransactionWrapper<FlexicoreHashMap> flexicoreHashMapTransactionWrapper = ApStarCBS.activatesFOSAAccount("MSISDN", strMobileNo);
                            FlexicoreHashMap fxhashmapStatus = flexicoreHashMapTransactionWrapper.getSingleRecord();
                            //FlexicoreHashMap statusFlexicoreHashMap = fxhashmapStatus.getFlexicoreHashMap("response_payload");
                            String theResponseStatus = fxhashmapStatus.getStringValue("request_status");
                           // String accountStatus = statusFlexicoreHashMap.getStringValue("account_status");



                            if(theResponseStatus.equalsIgnoreCase("SUCCESS")){
                                strResponse = "Dear member, your request to ACTIVATE Savings Account has been completed successfully.";
                            } else {
                                strResponse = "Dear member, your request to ACTIVATE Savings Account has FAILED. Please try again. If activating Savings Account fails again, contact us for assistance.";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        case "NO": {
                            String strResponse = "Dear member, your request to ACTIVATE Savings Account was NOT confirmed. Savings Account Activation request NOT COMPLETED.\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {
                            String strResponse = strHeader + "\n{Select a valid menu}\nProceed to ACTIVATE your Savings Account?\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.SAVINGS_ACCOUNT_ACTIVATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                    }
                    break;
                }
                default: {
                    return displayMenu_SavingsAccount(theUSSDRequest, "MENU");
                }
            }

        }
        catch(Exception e){
            System.err.println("theAppMenus.displayMenu_ActivateSavingsAccount() ERROR : " + e.getMessage());
        }
        finally{
            theAppMenus = null;
        }
        return theUSSDResponse;
    }
}
