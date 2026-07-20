package ke.skyworld.mbanking.ussdapplication;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.Misc;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.data_formatting.Converter;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingXMLFactory;
import ke.skyworld.lib.mbanking.mapp.MAPPConstants;
import ke.skyworld.lib.mbanking.ussd.*;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.mappapi.MAPPAPIConstants;
import ke.skyworld.mbanking.pesaapi.PESAAPI;
import ke.skyworld.mbanking.pesaapi.PESAAPIConstants;
import ke.skyworld.mbanking.pesaapi.PesaParam;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.w3c.dom.Element;

import java.util.*;

public interface LoansMenus {
    default USSDResponse displayMenu_Loan(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {
            String strUSSDDataType = theUSSDRequest.getUSSDDataType();

            if (strUSSDDataType.equalsIgnoreCase(AppConstants.USSDDataType.MAIN_IN_MENU.getValue())) {
                String strHeader = "Loans";
                theUSSDResponse = getLoansMenus(theUSSDRequest, strHeader);
            } else { //LOAN_MENU

                String strLOAN_MENU = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MENU.name());

                switch (strLOAN_MENU) {
                    case "CHECK_LOAN_LIMIT": {
                        theUSSDResponse = theAppMenus.displayMenu_CheckLoanLimit(theUSSDRequest, theParam);
                        break;
                    }
                   /* case "CHECK_GUARANTORSHIP_ABILITY": {
                        theUSSDResponse = theAppMenus.displayMenu_CheckLoanGuarantorshipAbility(theUSSDRequest, theParam);
                        break;
                    }*/
                    case "LOAN_APPLICATION": {
                        theUSSDResponse = theAppMenus.displayMenu_LoanApplication(theUSSDRequest, theParam);
                        break;
                    }
                    case "LOAN_REPAYMENT": {
                        theUSSDResponse = theAppMenus.displayMenu_LoanRepayment(theUSSDRequest, theParam);
                        break;
                    }
                    case "LOAN_BALANCE": {
                        theUSSDResponse = theAppMenus.displayMenu_LoanBalanceEnquiry(theUSSDRequest, "MENU");
                        break;
                    }
                    case "LOAN_MINI_STATEMENT": {
                        theUSSDResponse = theAppMenus.displayMenu_LoanMiniStatement(theUSSDRequest, "MENU");
                        break;
                    }
                    case "LOAN_GUARANTORS": {
                        theUSSDResponse = theAppMenus.displayMenu_LoanGuarantors(theUSSDRequest, theParam);
                        break;
                    }
                    case "LOANS_GUARANTEED": {
                        theUSSDResponse = theAppMenus.displayMenu_LoansGuaranteed(theUSSDRequest, theParam);
                        break;
                    }

                    default: {
                        String strHeader = "Loans\n{Select a valid menu}";
                        theUSSDResponse = getLoansMenus(theUSSDRequest, strHeader);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getLoansMenus(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            /*USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "CHECK_LOAN_LIMIT", "1: Check Loan Limit");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "CHECK_GUARANTORSHIP_ABILITY", "2: Check Guarantorship Ability");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "LOAN_APPLICATION", "3: Apply Loan");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "4", "LOAN_REPAYMENT", "4: Pay Loan");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "5", "LOAN_BALANCE", "5: Loan Balance");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "6", "LOAN_MINI_STATEMENT", "6: Loan Mini-Statement");*/

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "CHECK_LOAN_LIMIT", "1: Check Loan Limit");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "LOAN_APPLICATION", "2: Apply Loan");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "3", "LOAN_REPAYMENT", "3: Pay Loan");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "4", "LOAN_BALANCE", "4: Loan Balance");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "5", "LOAN_MINI_STATEMENT", "5: Loan Mini-Statement");
/*            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "6", "LOAN_GUARANTORS", "6: Loan Guarantors");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "7", "LOANS_GUARANTEED", "7: Loans Guaranteed");*/

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_MENU, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.getLoansMenus() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_CheckLoanLimit(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        try {
            switch (theParam) {
                case "MENU": {
                    String strHeader = "Check Loan Limit";

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.LOAN_QUALIFICATION);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_QUALIFICATION_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;

                    } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_QUALIFICATION)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_QUALIFICATION_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    theUSSDResponse = GeneralMenus.displayMenu_LoanTypes(theUSSDRequest, theParam, strHeader, AppConstants.USSDDataType.LOAN_QUALIFICATION_TYPE, AppConstants.USSDDataType.LOAN_QUALIFICATION_END);
                    break;
                }

                case "TYPE": {

                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_QUALIFICATION_TYPE.name());

                    if (strLoanType.length() > 0) {
                        HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                        String strLoanTypeID = hmLoanType.get("id");
                        String strLoanTypeCode = hmLoanType.get("code");
                        String strLoanTypeName = hmLoanType.get("name");
                        String strLoanTypeLabel = hmLoanType.get("label");
                        /*String strLoanTypeMaxAmount = hmLoanType.get("max");
                        String strLoanTypeMinAmount = hmLoanType.get("min");
                        String strLoanTypeMaxDuration = hmLoanType.get("duration");
                        String strLoanTypeInterest = hmLoanType.get("interest");*/

                        String strResponse = "";

                        TransactionWrapper<FlexicoreHashMap> loanApplicationWrapper = theUSSDAPI.loanQualificationCheck(theUSSDRequest);
                        FlexicoreHashMap loanApplicationMap = loanApplicationWrapper.getSingleRecord();

                        if (loanApplicationWrapper.hasErrors()) {
                            String strErrorMessage = loanApplicationMap.getValue("cbs_api_return_val").toString() + "\n";
                            strErrorMessage += loanApplicationMap.getStringValue("display_message");
                            System.err.println("LoansMenus.displayMenu_CheckLoanQualification() - Response " + strErrorMessage);

                            strResponse = "Sorry, an error occurred while processing your request. Please try again later.";
                        } else {
                            strResponse = "Loan Qualification - " + strLoanTypeName + "\n";

                            FlexicoreHashMap loanLimitMap = loanApplicationMap.getFlexicoreHashMap("payload");
                            String eligibleAmount = loanLimitMap.getStringValue("eligible_amount");

                            String strFormattedAmount = Utils.formatDouble(eligibleAmount, "#,##0.00");

                            strResponse = strResponse + "Eligible Amount: KES " + strFormattedAmount + "\n";
                        }

                        /*String strResponse = "Dear member, your " + strLoanTypeName + " qualification request has been received successfully. Please wait shortly as it's being processed.\n";

                        Thread worker = new Thread(() -> {

                        });
                        worker.start();*/


                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_QUALIFICATION_END, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strHeader = "Check Loan Limit\n{Select a valid menu}";
                        theUSSDResponse = GeneralMenus.displayMenu_LoanTypes(theUSSDRequest, theParam, strHeader, AppConstants.USSDDataType.LOAN_QUALIFICATION_TYPE, AppConstants.USSDDataType.LOAN_QUALIFICATION_END);
                    }

                    break;
                }

                default: {
                    System.err.println("theAppMenus.displayMenu_CheckLoanQualification() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = "Check Loan Limit\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_QUALIFICATION_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_CheckLoanQualification() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanApplication(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        try {
            String strLoan = "";
            switch (theParam) {
                case "MENU": {
                    String strHeader = "Loan Application";


                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.LOAN_APPLICATION);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Loan Application\n" + getServiceStatusDetails.getStringValue("display_message"));
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;

                    } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_APPLICATION)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "Loan Application\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    theUSSDResponse = GeneralMenus.displayMenu_LoanTypes(theUSSDRequest, theParam, strHeader, AppConstants.USSDDataType.LOAN_APPLICATION_TYPE, AppConstants.USSDDataType.LOAN_APPLICATION_END);
                    break;
                }

                case "TYPE": {

                    String strLoanApplicationType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());

                    if (strLoanApplicationType.length() > 0) {
                        String strLoanTypeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
                        HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanTypeDetails);
                        String strLoanTypeID = hmLoanType.get("id");
                        String strLoanTypeName = hmLoanType.get("name");
                        String strLoanTypeLabel = hmLoanType.get("label");
                        String strLoanApplicationMinimum = hmLoanType.get("min");
                        String strLoanTypeMax = hmLoanType.get("max");



                        TransactionWrapper<FlexicoreHashMap> loanQualificationWrapper = theUSSDAPI.loanQualificationCheckForLoanApplication(theUSSDRequest);

                        FlexicoreHashMap loanQualificationMap = loanQualificationWrapper.getSingleRecord();

                        if (loanQualificationWrapper.hasErrors()) {

                            USSDAPIConstants.StandardReturnVal returnVal = loanQualificationMap.getValue("cbs_api_return_val");
                            USSDAPIConstants.Condition condition = loanQualificationMap.getValue("end_session");

                            String strResponse = strLoanTypeLabel + " Application\n" + loanQualificationMap.getStringValue("display_message");

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);

                        } else {

                            //String strLoanApplicationMinimum = USSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.APPLY_LOAN).getMinimum();

                            String strEligibleAmount = loanQualificationMap.getFlexicoreHashMap("payload").getStringValue("eligible_amount");
                            String strReason= loanQualificationMap.getFlexicoreHashMap("payload").getStringValue("reason");

                            double dblEligibleAmount = Double.parseDouble(strEligibleAmount);
                            double dblMinimumApplicable = Double.parseDouble(strLoanApplicationMinimum);
                            double dblMaximumApplicable = Double.parseDouble(strLoanTypeMax);

                            double dblMaxCanApply = Math.min(dblEligibleAmount, dblMaximumApplicable);




                            if (dblEligibleAmount < dblMinimumApplicable) {
                                String strResponse = strLoanTypeLabel + " Application\n Sorry, you are not eligible to apply for loan " + strLoanTypeLabel
                                        +"\n"+strReason;

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);

                            } else {

                                String strNarration = "(Min: KES " + Utils.formatDouble(strLoanApplicationMinimum, "#,##0.00") + " Max: KES " +
                                        Utils.formatDouble(dblMaxCanApply, "#,##0.00") + ")";

                                String strResponse = strLoanTypeLabel + " Application\nEnter amount " + strNarration;

                                FlexicoreHashMap tmpLoanDetailsMap = new FlexicoreHashMap();

                                FlexicoreHashMap application_details = new FlexicoreHashMap();
                                application_details.putValue("minimum", strLoanApplicationMinimum);
                                application_details.putValue("maximum", dblMaxCanApply);
                                application_details.putValue("narration", strNarration);
                                application_details.putValue("application_type", "NEW_LOAN");
                                application_details.putValue("product_id", strLoanTypeID);

                                tmpLoanDetailsMap.putValue("mobile_number", String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                                tmpLoanDetailsMap.putValue("application_details", Converter.toJson(application_details));
                                tmpLoanDetailsMap.putValue("date_created", DateTime.getCurrentDateTime());

                                TransactionWrapper<?> wrapper = theUSSDAPI.insertOrUpdateLoanDetails(tmpLoanDetailsMap, String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                                if (wrapper.hasErrors()) {
                                    strResponse = strLoanTypeLabel + " Application\nSorry, an error occurred while processing your request. Please try again later";

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);
                                } else {
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                }
                            }

                        }

                        /*String strResponse = strLoanTypeLabel + " Application\nEnter amount:";

                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        */

                    } else {
                        String strHeader = "{Select a valid menu}";
                        theUSSDResponse = GeneralMenus.displayMenu_LoanTypes(theUSSDRequest, theParam, strHeader, AppConstants.USSDDataType.LOAN_APPLICATION_TYPE, AppConstants.USSDDataType.LOAN_APPLICATION_END);
                    }

                    break;
                }

                case "AMOUNT": {
                    String strLoanTypeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanTypeDetails);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeLabel = hmLoanType.get("label");
                    /*String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");*/

                   /* double dblLoanTypeMinAmount = 0;
                    double dblLoanTypeMaxAmount = 0;
                    double dblLoanTypeMaxDuration = 0;
                    double dblLoanTypeInterest = 0;
*/
                    /*try { dblLoanTypeMinAmount = Double.parseDouble(strLoanTypeMinAmount); }catch (Exception e){}
                    try { dblLoanTypeMaxAmount = Double.parseDouble(strLoanTypeMaxAmount); }catch (Exception e){}
                    try { dblLoanTypeMaxDuration = Double.parseDouble(strLoanTypeMaxDuration); }catch (Exception e){}
                    try { dblLoanTypeInterest = Double.parseDouble(strLoanTypeInterest); }catch (Exception e){}*/


                  /*  TransactionWrapper<FlexicoreHashMap> wrapper = theUSSDAPI.getTMPLoanDetails(String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                    FlexicoreHashMap tmpLoanDetails = wrapper.getSingleRecord();

                    FlexicoreHashMap applicationDetailsMap = Misc.getBodyObjectWithGson(FlexicoreHashMap.class, tmpLoanDetails.getStringValue("application_details"), StringRefs.APPLICATION_JSON);


                    String strNarration = applicationDetailsMap.getStringValue("narration");
                    String strApplicationType = applicationDetailsMap.getStringValue("application_type");*/

                    TransactionWrapper<FlexicoreHashMap> wrapper = theUSSDAPI.getTMPLoanDetails(String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                    FlexicoreHashMap tmpLoanDetails = wrapper.getSingleRecord();

                    FlexicoreHashMap applicationDetailsMap = Misc.getBodyObjectWithGson(FlexicoreHashMap.class, tmpLoanDetails.getStringValue("application_details"), StringRefs.APPLICATION_JSON);

                    String strLoanApplicationMinimum = applicationDetailsMap.getStringValue("minimum");
                    String strLoanApplicationMaximum = applicationDetailsMap.getStringValue("maximum");

                    String strNarration = applicationDetailsMap.getStringValue("narration");
                    String strApplicationType = applicationDetailsMap.getStringValue("application_type");

                    double dblLoanApplicationMinimum = Double.parseDouble(strLoanApplicationMinimum);
                    double dblLoanApplicationMaximum = Double.parseDouble(strLoanApplicationMaximum);

                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT.name());

                   /* dblLoanApplicationMinimum = Math.max(dblLoanApplicationMinimum, dblLoanTypeMinAmount);
                    dblLoanApplicationMaximum = Math.min(dblLoanApplicationMaximum, dblLoanTypeMaxAmount);*/

                   /* String strMenuInfo = "";
                    if (dblLoanApplicationMinimum > 0) {
                        strMenuInfo = strMenuInfo + "Min: KES " + Utils.formatDouble(dblLoanApplicationMinimum, "#,##0.00") + "\n";
                    }
                    if (dblLoanApplicationMaximum > 0) {
                        strMenuInfo = strMenuInfo + "Max: KES " + Utils.formatDouble(dblLoanApplicationMaximum, "#,##0.00") + "\n";
                    }*/
                   /* if(dblLoanTypeMaxDuration > 0){ strMenuInfo = strMenuInfo + "Duration: " + Utils.formatDouble(dblLoanTypeMaxDuration, "#,##0.00") + " month(s)\n";}
                    if(dblLoanTypeInterest > 0){ strMenuInfo = strMenuInfo + "Interest : " + Utils.formatDouble(dblLoanTypeInterest, "#,##0.00") + "%\n";}*/

                    if (strAmount.matches("^[1-9][0-9]*$")) {
                        String strResponse = strLoanTypeLabel + " Application\nSelect Purpose:";

                        theUSSDResponse = displayMenu_LoanPurposes(theUSSDRequest, strResponse);

                        double dblAmountEntered = Double.parseDouble(strAmount);
                        if (dblAmountEntered < dblLoanApplicationMinimum) {
                            strResponse = strLoanTypeLabel + " Application\n{Minimum amount is KES " + Utils.formatDouble(dblLoanApplicationMinimum, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        } else if (dblAmountEntered > dblLoanApplicationMaximum) {
                            strResponse = strLoanTypeLabel + " Application\n{Maximum amount is KES " + Utils.formatDouble(dblLoanApplicationMaximum, "#,##0.00") + "}\nEnter amount:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                    } else {
                        String strResponse = strLoanTypeLabel + " Application\n{Please enter a valid amount}\n" + strNarration + ":";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "PURPOSE": {

                    String strLoanTypeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanTypeDetails);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeLabel = hmLoanType.get("label");

                    String strLoanPurposeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_PURPOSE.name());

                    if (!strLoanPurposeDetails.isBlank()) {
                        HashMap<String, String> hmLoanPurpose = Utils.toHashMap(strLoanPurposeDetails);
                        //String strLoanTypeCode = hmLoanPurpose.get("code");
                        //String strLoanTypeName = hmLoanPurpose.get("description");

                        String strResponse = strLoanTypeLabel + " Application\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {
                        String strResponse = strLoanTypeLabel + " Application\n{Invalid Option}\nSelect Purpose:";
                        theUSSDResponse = displayMenu_LoanPurposes(theUSSDRequest, strResponse);
                    }

                    break;
                }

                case "PIN": {
                    String strLoanTypeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanTypeDetails);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeLabel = hmLoanType.get("label");
                    /*String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");*/

                    /*double dblLoanTypeMinAmount = 0;
                    double dblLoanTypeMaxAmount = 0;
                    double dblLoanTypeMaxDuration = 0;
                    double dblLoanTypeInterest = 0;*/

                    /*try { dblLoanTypeMinAmount = Double.parseDouble(strLoanTypeMinAmount); }catch (Exception e){}
                    try { dblLoanTypeMaxAmount = Double.parseDouble(strLoanTypeMaxAmount); }catch (Exception e){}
                    try { dblLoanTypeMaxDuration = Double.parseDouble(strLoanTypeMaxDuration); }catch (Exception e){}
                    try { dblLoanTypeInterest = Double.parseDouble(strLoanTypeInterest); }catch (Exception e){}*/

                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT.name());

                        String strMenuInfo = "";
                        /*if(dblLoanTypeMaxDuration > 0){ strMenuInfo = strMenuInfo + "Duration: " + Utils.formatDouble(dblLoanTypeMaxDuration, "#,##0.00") + " month(s)\n";}
                        if(dblLoanTypeInterest > 0){ strMenuInfo = strMenuInfo + "Interest : " + Utils.formatDouble(dblLoanTypeInterest, "#,##0.00") + "%\n";}*/

                        String strMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());

                        TransactionWrapper<FlexicoreHashMap> chargesWrapper = CBSAPI.getCharges(strMobileNo, "MSISDN", strMobileNo, AppConstants.ChargeServices.LOAN_APPLICATION.getValue(),
                                Double.parseDouble(strAmount));

                        String strCharge = "";
                        if (chargesWrapper.hasErrors()) {
                            strCharge = "";
                        } else {
                            strCharge = "\nCharge: KES " + chargesWrapper.getSingleRecord().getStringValue("charge_amount");
                        }


                        String strLoanPurposeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_PURPOSE.name());
                        HashMap<String, String> hmLoanPurpose = Utils.toHashMap(strLoanPurposeDetails);
                        String strLoanPurposeCode = hmLoanPurpose.get("code");
                        String strLoanPurposeName = hmLoanPurpose.get("description");

                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");
                        String strResponse = "Confirm " + strLoanTypeLabel + " Application\nLoan Purpose: " + strLoanPurposeName + "\nAmount Applied: KES " + strAmount + strCharge + "\n";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strLoanTypeLabel + " Application\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_APPLICATION_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "CONFIRMATION": {

                    String strLoanTypeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanTypeDetails);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeLabel = hmLoanType.get("label");
                    /*String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");*/

                    /*double dblLoanTypeMinAmount = 0;
                    double dblLoanTypeMaxAmount = 0;
                    double dblLoanTypeMaxDuration = 0;
                    double dblLoanTypeInterest = 0;*/

                    /*try { dblLoanTypeMinAmount = Double.parseDouble(strLoanTypeMinAmount); }catch (Exception e){}
                    try { dblLoanTypeMaxAmount = Double.parseDouble(strLoanTypeMaxAmount); }catch (Exception e){}
                    try { dblLoanTypeMaxDuration = Double.parseDouble(strLoanTypeMaxDuration); }catch (Exception e){}
                    try { dblLoanTypeInterest = Double.parseDouble(strLoanTypeInterest); }catch (Exception e){}*/

                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_CONFIRMATION.name());
                    if (strConfirmation.equalsIgnoreCase("YES")) {
                        String strResponse = "Dear member, your " + strLoanTypeLabel + " Application has been received successfully and is being processed.\n";

                        Thread worker = new Thread(() -> {
                        });
                        worker.start();

                        TransactionWrapper<FlexicoreHashMap> loanApplicationWrapper = theUSSDAPI.loanApplication(theUSSDRequest);
                        FlexicoreHashMap loanApplicationMap = loanApplicationWrapper.getSingleRecord();

                        strResponse = strLoanTypeLabel + " Loan Application\n" + loanApplicationMap.getStringValue("display_message");

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);

                        /*
                        APIConstants.TransactionReturnVal transactionReturnVal = theUSSDAPI.loanApplication(theUSSDRequest);

                        String strResponse ="";

                        if(transactionReturnVal.equals(APIConstants.TransactionReturnVal.SUCCESS)){
                            strResponse = "Dear member, your "+strLoanName+" Application request has been received successfully. Please wait shortly as it's being processed.";
                        }else {


                            switch (transactionReturnVal) {
                                case INCORRECT_PIN: {
                                    strResponse = "Sorry the PIN provided is incorrect. Your "+strLoanName+" Application request CANNOT be completed.\n";
                                    break;
                                }
                                case BLOCKED: {
                                    strResponse = "Dear member, your account has been blocked. Your "+strLoanName+" Application request CANNOT be completed.\n";
                                    break;
                                }
                                default: {
                                    strResponse = "Sorry, your "+strLoanName+" Application request CANNOT be completed at the moment. Please try again later.\n";
                                    break;
                                }
                            }
                        }
                        */

                    } else if (strConfirmation.equalsIgnoreCase("NO")) {
                        String strResponse = "Dear member, your " + strLoanTypeLabel + " Application request NOT confirmed. Loan Application request NOT COMPLETED.";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_AMOUNT.name());

                        String strMenuInfo = "";
                        /*if(dblLoanTypeMaxDuration > 0){ strMenuInfo = strMenuInfo + "Duration: " + Utils.formatDouble(dblLoanTypeMaxDuration, "#,##0.00") + " month(s)\n";}
                        if(dblLoanTypeInterest > 0){ strMenuInfo = strMenuInfo + "Interest : " + Utils.formatDouble(dblLoanTypeInterest, "#,##0.00") + "%\n";}*/

                        String strLoanPurposeDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_APPLICATION_PURPOSE.name());
                        HashMap<String, String> hmLoanPurpose = Utils.toHashMap(strLoanPurposeDetails);
                        String strLoanPurposeCode = hmLoanPurpose.get("code");
                        String strLoanPurposeName = hmLoanPurpose.get("description");

                        strAmount = Utils.formatDouble(strAmount, "#,##0.00");
                        String strResponse = "Confirm " + strLoanTypeLabel + " Application\n{Select a valid menu}\nLoan Purpose: " + strLoanPurposeName + "\nAmount Applied: KES " + strAmount + "\nCharge: KES 10.00\n";

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                    }

                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_LoanApplication() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = "Loan Application\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_LoanApplication() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanPurposes(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        USSDAPI theUSSDAPI = new USSDAPI();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            HashMap<String, HashMap<String, String>> loanTypes = new HashMap<>();

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "AGRICULTURE");
                hmLoanType.put("description", "Agriculture");
                loanTypes.put("AGRICULTURE", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "TRADE");
                hmLoanType.put("description", "Trade");
                loanTypes.put("TRADE", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "MANUFACTURING");
                hmLoanType.put("description", "Manufacturing");
                loanTypes.put("MANUFACTURING", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "EDUCATION");
                hmLoanType.put("description", "Education");
                loanTypes.put("EDUCATION", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "HEALTH");
                hmLoanType.put("description", "Health");
                loanTypes.put("HEALTH", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "HOUSING");
                hmLoanType.put("description", "Housing");
                loanTypes.put("HOUSING", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "FINANCE");
                hmLoanType.put("description", "Finance");
                loanTypes.put("FINANCE", hmLoanType);
            }

            {
                HashMap<String, String> hmLoanType = new HashMap<>();
                hmLoanType.put("code", "CONSUMPTION");
                hmLoanType.put("description", "Consumption");
                loanTypes.put("CONSUMPTION", hmLoanType);
            }


            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int count = 0;

            for (String loanType : loanTypes.keySet()) {
                count++;

                HashMap<String, String> hmLoanType = loanTypes.get(loanType);

                String strOptionValue = Utils.serialize(hmLoanType);
                String strLoanTypeName = hmLoanType.get("description");
                String strLoanTypeCode = hmLoanType.get("code");

                String strOptionMenu = Integer.toString(count);
                String strOptionDisplayText = strOptionMenu + ": " + strLoanTypeName;

                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_APPLICATION_PURPOSE, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_Loans() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
            theUSSDAPI = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanRepayment(USSDRequest theUSSDRequest, String theParam) {

        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_C2B);
        String strSender = pesaParam.getSenderIdentifier();

        String strHeader = "Pay Loan";

        try {
            String strUSSDDataType = theUSSDRequest.getUSSDDataType();
            if (theParam.equalsIgnoreCase("MENU")) {

                FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.LOAN_REPAYMENT);
                String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                    return theUSSDResponse;

                } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_REPAYMENT)) {
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                    return theUSSDResponse;
                }

                theUSSDResponse = getLoanRepaymentOption(theUSSDRequest, strHeader);

            } else { //LOAN_REPAYMENT_MENU
                String strLOAN_REPAYMENT_OPTION = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_OPTION.name());

                AppConstants.USSDDataType ussdDataType = AppUtils.getUSSDDataTypeFromValue(theUSSDRequest.getUSSDDataType());

                switch (ussdDataType) {

                    case LOAN_REPAYMENT_OPTION: {
                        String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_OPTION.name());
                        if (!strLoanType.equals("")) {
                            String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "";
                            //theUSSDResponse = GeneralMenus.displayMenu_Loans(theUSSDRequest, theParam, strHeader2, USSDAPIConstants.AccountType.ALL, AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN);

                            String strCustomerHeader = strHeader2 + "\nSelect Member\n";
                            String strAccountsHeader = strHeader2 + "\nSelect Loan\n";

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER,
                                    USSDAPIConstants.AccountType.LOAN,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_END);

                        } else {
                            String strHeader2 = strHeader + "\n{Select a valid menu}";
                            theUSSDResponse = getLoanRepaymentOption(theUSSDRequest, strHeader2);
                        }
                        break;
                    }

                    case LOAN_REPAYMENT_CUSTOMER: {

                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER.name());
                        if (strCustomerIdentifier.length() > 0) {
                            String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "";
                            String strAccountsHeader = strHeader2 + "\nSelect Loan\n";
                            theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader,
                                    "CUSTOMER_NO", strCustomerIdentifier,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid Member}\n";
                            String strAccountsHeader = strHeader + " \nSelect Loan\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER,
                                    USSDAPIConstants.AccountType.LOAN,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                        }

                        break;
                    }

                    case LOAN_REPAYMENT_LOAN: {

                        if (strLOAN_REPAYMENT_OPTION.equalsIgnoreCase("Savings Account")) {
                            String strLoansInService = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                            if (!strLoansInService.equals("")) {
                                //String strHeader = "Pay Loan\nSelect source of funds account\n";

                                String strCustomerHeader = "Pay Loan\nSelect source of funds member\n";
                                String strAccountsHeader = "Pay Loan\nSelect source of funds account\n";

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                            } else {
                                String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION;

                                String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER.name());

                                String strAccountsHeader = strHeader2 + " \n{Select a VALID Loan}\n";
                                String strCustomerHeader = strHeader2 + " \n{Select Member}\n";

                                if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                    theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader,
                                            "CUSTOMER_NO", strCustomerIdentifier,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                                } else {

                                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER,
                                            USSDAPIConstants.AccountType.LOAN,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                                }
                            }
                        } else {
                            String strLoansInService = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());

                            HashMap<String, String> hmLoan = Utils.toHashMap(strLoansInService);
                            String strLoanNumber = hmLoan.get("ac_no");
                            String strLoanAmount = hmLoan.get("amount");
                            String strLoanName = hmLoan.get("ac_name");
                            String strLoanBalance = hmLoan.get("bal");
                            String strLoanAccountLabel = hmLoan.get("ac_label");
                            String strLoanInterestAmount = hmLoan.get("intr");

                            String strLoanInstallmentAmount = hmLoan.get("installment_amount");
                            //String strLoanInterestAmount = hmLoan.get("interest");

                            String strFormattedLoanBalance = Utils.formatDouble(strLoanBalance, "#,##0.00");
                            String strFormattedLoanInstallmentAmount = Utils.formatDouble(strLoanInstallmentAmount, "#,##0.00");
                            String strFormattedLoanInterestAmount = Utils.formatDouble(strLoanInterestAmount, "#,##0.00");

                            double totalPayable = Double.parseDouble(strLoanBalance) ;
                            String strFormattedLoanTotalPayableAmount = Utils.formatDouble(String.valueOf(totalPayable), "#,##0.00");

                            if (!strLoansInService.equals("")) {
                                String strResponse = "Pay " + strLoanName + " via " + strLOAN_REPAYMENT_OPTION;
                                strResponse = strResponse + "\nBalance KES " + strFormattedLoanBalance;
                                //strResponse = strResponse + "\nInstalment KES " + strFormattedLoanInstallmentAmount;
//                                strResponse = strResponse + "\nInterest KES " + strFormattedLoanInterestAmount;
                                //strResponse = strResponse + "\nTotal KES " + strFormattedLoanTotalPayableAmount;
                                strResponse = strResponse + "\n\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else {
                                String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\n{Select a valid option}";

                                String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER.name());

                                String strAccountsHeader = strHeader2 + " \n{Select a valid option}\n";
                                String strCustomerHeader = strHeader2 + " \n{Select Member}\n";

                                if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                    theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader,
                                            "CUSTOMER_NO", strCustomerIdentifier,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                                } else {

                                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_CUSTOMER,
                                            USSDAPIConstants.AccountType.LOAN,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN,
                                            AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                                }
                            }
                        }
                        break;
                    }

                    case LOAN_REPAYMENT_FUNDS_CUSTOMER: {
                        String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_CUSTOMER.name());

                        if (strCustomerIdentifier.length() > 0) {

                            String strAccountsHeader = "Pay Loan\nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader, strCustomerIdentifier,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_END);

                        } else {
                            String strCustomerHeader = strHeader + " \n{Select a valid source member}\n";
                            String strAccountsHeader = strHeader + " \nSelect source account\n";
                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_CUSTOMER,
                                    USSDAPIConstants.AccountType.WITHDRAWABLE,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT,
                                    AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                        }

                        break;
                    }

                    case LOAN_REPAYMENT_FUNDS_ACCOUNT: {

                        String strFromAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT.name());

                        String strLoan = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                        if (strFromAccountDetails.length() > 0) {
                            String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\nPayment Option";

                            String strLoansInService = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());

                            HashMap<String, String> hmLoan = Utils.toHashMap(strLoansInService);

                            String strLoanNumber = hmLoan.get("ac_no");
                            String strLoanAmount = hmLoan.get("amount");
                            String strLoanName = hmLoan.get("ac_name");
                            String strLoanBalance = hmLoan.get("bal");
                            String strLoanAccountLabel = hmLoan.get("ac_label");
                            String strLoanInterestAmount = hmLoan.get("intr");
                            String strLoanInstallmentAmount = hmLoan.get("installment_amount");

                            /*String strLoanInstallmentAmount = hmLoan.get("installment");
                            String strLoanInterestAmount = hmLoan.get("interest");*/

                            String strFormattedLoanBalance = Utils.formatDouble(strLoanBalance, "#,##0.00");
                            String strFormattedLoanInstallmentAmount = Utils.formatDouble(strLoanInstallmentAmount, "#,##0.00");
                            String strFormattedLoanInterestAmount = Utils.formatDouble(strLoanInterestAmount, "#,##0.00");

                            //double totalPayable = Double.parseDouble(strLoanBalance) + Double.parseDouble(strLoanInterestAmount);
                            double totalPayable = Double.parseDouble(strLoanBalance) ;
                            String strFormattedLoanTotalPayableAmount = Utils.formatDouble(String.valueOf(totalPayable), "#,##0.00");

                            if (totalPayable == 0d) {
                                String strResponse = "Your Loan Balance is KES 0.00. Please contact the SACCO for further assistance in clearing it.\n";
                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                            } else {
                                String strResponse = "Pay " + strLoanName + " via " + strLOAN_REPAYMENT_OPTION;
                                strResponse = strResponse + "\nBalance KES " + strFormattedLoanBalance;
                               // strResponse = strResponse + "\nInstalment KES " + strFormattedLoanInstallmentAmount;
//                                strResponse = strResponse + "\nInterest KES " + strFormattedLoanInterestAmount;
                                /*strResponse = strResponse + "\nTotal KES " + strFormattedLoanTotalPayableAmount;*/
                                strResponse = strResponse + "\n\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }

                        } else {
                            String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "";

                            String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_CUSTOMER.name());

                            String strAccountsHeader = strHeader2 + " \n{Select a valid source account}\n";

                            String strCustomerHeader = strHeader2 + " \n{Select source member}\n";

                            if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                                theUSSDResponse = GeneralMenus.displayMenu_Withdrawable_Accounts(theUSSDRequest, theParam, strAccountsHeader,
                                        strCustomerIdentifier,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                            } else {

                                theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_CUSTOMER,
                                        USSDAPIConstants.AccountType.WITHDRAWABLE,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT,
                                        AppConstants.USSDDataType.LOAN_REPAYMENT_END);
                            }
                        }
                        break;
                    }

                    /*case LOAN_REPAYMENT_CLEARING: {
                        String strOption = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_CLEARING.name());

                        switch (strOption) {

                            case "CLEAR_LOAN" -> {
                                TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = theUSSDAPI.loanBalanceEnquiry(theUSSDRequest, true);
                                if (accountBalanceEnquiryWrapper.hasErrors()) {
                                    FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();
                                    String strErrorMessage = accountBalanceEnquiryMap.getStringValue("display_message");
                                    System.err.println("LoansMenus.displayMenu_LoanBalanceEnquiry() - Response " + strErrorMessage);

                                    String strResponse = "Pay Loan\nSorry, an error occurred while processing your Loan Payment Request. Please try again later.";
                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);

                                } else {

                                    FlexicoreHashMap loanBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();

                                    String strLoanBalance = loanBalanceEnquiryMap.getStringValueOrIfNull("OutLoanAccountDrvWorkAccountBal", "0").trim();
                                    strLoanBalance = strLoanBalance.replaceFirst("-", "");

                                    //TODO: ADD HERE THE INTEREST BALANCE
                                    String strLoanInterest = loanBalanceEnquiryMap.getStringValueOrIfNull("OutLoanAccountNrmAcrIntBal", "0").trim();
                                    strLoanInterest = strLoanInterest.replaceFirst("-", "");

                                    double dblLoanBalance = Double.parseDouble(strLoanBalance);
                                    double dblInterest = Double.parseDouble(strLoanInterest);

                                    dblLoanBalance = dblLoanBalance + dblInterest;

                                    if (dblLoanBalance == 0d) {
                                        String strResponse = "Pay Loan\nYour Total Loan Balance is KES 0.00. Please contact the SACCO for further assistance in clearing the loan.\n";
                                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                                    } else {

                                        String strFormattedLoanBalance = Utils.formatDouble(dblLoanBalance, "#,##0.00");

                                        String strLoanDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                                        HashMap<String, String> hmLoan = Utils.toHashMap(strLoanDetails);
                                        String strLoanNumber = hmLoan.get("ac_no");
                                        String strLoanAccountLabel = hmLoan.get("ac_label");

                                        String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT.name());
                                        HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);

                                        String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                                        String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");

                                        String strResponse = "Confirm Clear Loan\nFunds Account: " + strFromAccountLabel + "(" + strFromAccountNumber + ")\n"
                                                + "Loan: " + strLoanAccountLabel + "(" + strLoanNumber + ")\n" + "Amount: KES " + strFormattedLoanBalance + "\n";

                                        theUSSDResponse = displayMenu_ClearingConfirmation(theUSSDRequest, strResponse, String.valueOf(dblLoanBalance));
                                    }
                                }
                            }
                            case "OTHER_AMOUNT" -> {

                                String strLoansInService = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());

                                HashMap<String, String> hmLoan = Utils.toHashMap(strLoansInService);

                                String strLoanNumber = hmLoan.get("ac_no");
                                String strLoanBalance = hmLoan.get("bal");
                                String strLoanAccountLabel = hmLoan.get("ac_label");

                                double dblLoanBalance = Double.parseDouble(strLoanBalance);
                                if(dblLoanBalance == 0d){
                                    String strResponse = "Your Loan Balance is KES 0.00. Please use the 'Clear Loan' option to clear the loan or contact the SACCO for further assistance.\n";
                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                                }else{
                                    String strFormattedLoanBalance = Utils.formatDouble(strLoanBalance, "#,##0.00");
                                    //String strFormattedLoanInstallmentAmount = Utils.formatDouble(strLoanInstallmentAmount, "#,##0.00");

                                    String strResponse = "Pay " + strLoanAccountLabel + " via " + strLOAN_REPAYMENT_OPTION;
                                    strResponse = strResponse + "\nBalance KES " + strFormattedLoanBalance;
                                    //strResponse = strResponse + "\nInstalment KES " + strFormattedLoanInstallmentAmount;
                                    strResponse = strResponse + "\n\nEnter amount:";
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                }
                            }
                            default -> {
                                String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\nPayment Option\n{Select a valid menu}";
                                theUSSDResponse = getLoanRepaymentClearingOption(theUSSDRequest, strHeader2);
                            }
                        }

                        break;
                    }*/

                    case LOAN_REPAYMENT_AMOUNT: {

                        String strLoanDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                        HashMap<String, String> hmLoan = Utils.toHashMap(strLoanDetails);
                        String strLoanNumber = hmLoan.get("ac_no");
                        String strLoanAmount = hmLoan.get("amount");
                        String strLoanName = hmLoan.get("ac_name");
                        String strLoanBalance = hmLoan.get("bal");
                        String strLoanAccountLabel = hmLoan.get("ac_label");
                        String strLoanInterestAmount = hmLoan.get("intr");

                        String strFormattedLoanBalance = Utils.formatDouble(strLoanBalance, "#,##0.00");
                        String strFormattedLoanInstallmentAmount = Utils.formatDouble(strLoanInterestAmount, "#,##0.00");
                        String strFormattedLoanInterestAmount = Utils.formatDouble(strLoanInterestAmount, "#,##0.00");

                        double totalPayable = Double.parseDouble(strLoanBalance)+50;
                        String strFormattedLoanTotalPayableAmount = Utils.formatDouble(String.valueOf(totalPayable), "#,##0.00");


                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT.name());

                        if (strAmount.matches("^[1-9][0-9]*$")) {
                            String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");
                            String strResponse = "";

                            if (strLOAN_REPAYMENT_OPTION.equalsIgnoreCase("Savings Account")) {
                                String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT.name());
                                HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);

                                String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                                String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                                String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                                String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                                String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                                strResponse = "Confirm Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\nFunds Account: " + strFromAccountLabel + "-" + strFromAccountNumber + "\n" + "Loan: " + strLoanName + "-" + strLoanNumber + "\nAmount: KES " + strFormattedAmount + "\n";
                            } else {
                                strResponse = "Confirm Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\nPaybill no.: " + strSender + "\nLoan: " + strLoanName + "-" + strLoanNumber + "\nAmount: KES " + strFormattedAmount + "\n";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                            /*double dblLoanBalance = 0;

                            try {
                                dblLoanBalance = Double.parseDouble(strLoanBalance);
                            } catch (Exception e) {
                            }*/

                            String strPayLoanMinimum;
                            String strPayLoanMaximum;
                            if (strLOAN_REPAYMENT_OPTION.equalsIgnoreCase("Savings Account")) {
                                strPayLoanMinimum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.PAY_LOAN).getMinimum();
                                strPayLoanMaximum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.PAY_LOAN).getMaximum();
                            } else {
                                strPayLoanMinimum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMinimum();
                                strPayLoanMaximum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMaximum();
                            }

                            double dblPayLoanMinimum = Double.parseDouble(strPayLoanMinimum);
                            double dblPayLoanMaximum = Double.parseDouble(strPayLoanMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            if (dblAmountEntered < dblPayLoanMinimum) {
                                strResponse = "Pay " + strLoanName + " via " + strLOAN_REPAYMENT_OPTION + "\n{Minimum amount allowed for Repayment Option " + strLOAN_REPAYMENT_OPTION + " is KES " + Utils.formatDouble(strPayLoanMinimum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblAmountEntered > dblPayLoanMaximum) {
                                strResponse = "Pay " + strLoanName + " via " + strLOAN_REPAYMENT_OPTION + "\n{Maximum amount allowed for Repayment Option " + strLOAN_REPAYMENT_OPTION + " is KES " + Utils.formatDouble(strPayLoanMaximum, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else if (dblAmountEntered > totalPayable) {
                                strResponse = "Pay " + strLoanName + "\n{Amount KES " + Utils.formatDouble(strAmount, "#,##0.00") + " EXCEEDS  loan balance KES " + Utils.formatDouble(totalPayable, "#,##0.00") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            } else {
                                if (strLOAN_REPAYMENT_OPTION.equalsIgnoreCase("Savings Account")) {
                                    String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT.name());
                                    HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);

                                    String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                                    String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                                    String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                                    String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                                    String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                                    double dblFromAccountAvailableBalance = 0;
                                    try {
                                        dblFromAccountAvailableBalance = Double.parseDouble(strFromAccountAvailableBalance);
                                    } catch (Exception e) {
                                    }
                                    if (dblAmountEntered > dblFromAccountAvailableBalance) {
                                        strResponse = "Pay " + strLoanName + "\n{" + strFromAccountLabel + " avail bal KES " + Utils.formatDouble(dblFromAccountAvailableBalance, "#,##0.00") + " is INSUFFICIENT to pay KES " + Utils.formatDouble(strAmount, "#,##0.00") + "}\nEnter amount:";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                    }
                                }
                            }

                        } else {

                            String strResponse = "Pay " + strLoanName + " via " + strLOAN_REPAYMENT_OPTION;
                            strResponse = strResponse + "\nBalance KES " + strFormattedLoanBalance;
                            //strResponse = strResponse + "\nInstalment KES " + strFormattedLoanInstallmentAmount;
                            //strResponse = strResponse + "\nInterest KES " + strFormattedLoanInterestAmount;
                            /* strResponse = strResponse + "\nTotal KES " + strFormattedLoanTotalPayableAmount;*/

                            strResponse = strResponse + "\n\n{Please enter a valid amount}:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                        }
                        break;
                    }
                    case LOAN_REPAYMENT_CONFIRMATION: {
                        String strLoanDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                        HashMap<String, String> hmLoan = Utils.toHashMap(strLoanDetails);
                        String strLoanNumber = hmLoan.get("ac_no");
                        String strLoanBalance = hmLoan.get("bal");
                        String strLoanName = hmLoan.get("ac_name");
                        String strLoanAccountLabel = hmLoan.get("ac_label");

                        String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_AMOUNT.name());
                        String strFormattedAmount = Utils.formatDouble(strAmount, "#,##0.00");

                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_CONFIRMATION.name());
                        if (strConfirmation.equalsIgnoreCase("YES")) {

                            String strLoan = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                            String strResponse = "";

                            if (strLOAN_REPAYMENT_OPTION.equalsIgnoreCase("Savings Account")) {
                                strResponse = "Dear member, your request to Pay " + strLoanName + "-" + strLoanNumber + " via " + strLOAN_REPAYMENT_OPTION + " has been received successfully.\n";

                                Thread worker = new Thread(() -> {

                                    TransactionWrapper<FlexicoreHashMap> loanPaymentWrapper = theUSSDAPI.loanPaymentViaSavings(theUSSDRequest, false, strAmount);
                                    if (loanPaymentWrapper.hasErrors()) {
                                        FlexicoreHashMap internalFundsTransferMap = loanPaymentWrapper.getSingleRecord();
                                        String strErrorMessage = internalFundsTransferMap.getValue("cbs_api_return_val").toString() + "\n";
                                        strErrorMessage += internalFundsTransferMap.getStringValue("display_message");

                                        System.err.println("LoansMenus.displayMenu_LoanRepayment() - Response " + strErrorMessage);
                                    }
                                });
                                worker.start();

                                ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);


                            } else {
                                if (theUSSDRequest.getUSSDProviderCode() == AppConstants.USSDProvider.SAFARICOM.getValue()) {

                                    strResponse = "You will be prompted by " + strLOAN_REPAYMENT_OPTION + " for payment\nPaybill no: " + strSender + "\n" + "Loan: " + strLoanNumber + "\n" + "Amount: KES " + strFormattedAmount + "\n";

                                    String strOriginatorID = theUSSDRequest.getUSSDTraceID();
                                    String strBeneficiaryMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());
                                    //String strReceiverDetails = strBeneficiaryMobileNo; //todo -> Get Receiver Name
                                    String strAccountNo = strLoanNumber;

                                    double lnAmount = Utils.stringToDouble(strAmount);

                                    String strReference = strBeneficiaryMobileNo;

                                    String strMemberName = theUSSDAPI.getUserFullName(String.valueOf(theUSSDRequest.getUSSDMobileNo()));
                                    String strTraceID = theUSSDRequest.getUSSDTraceID();

                                    Thread worker = new Thread(() -> {
                                        PESAAPI thePESAAPI = new PESAAPI();
                                        //thePESAAPI.pesa_C2B_Request(strOriginatorID, strReceiver, strReceiverDetails, strAccount, "KES", lnAmount, "LOAN_REPAYMENT", strReference, "USSD", "MBANKING");

                                        thePESAAPI.pesa_C2B_Request(
                                                String.valueOf(theUSSDRequest.getUSSDMobileNo()),
                                                strMemberName,
                                                strTraceID,
                                                "USSD",

                                                strAccountNo,
                                                strLoanAccountLabel,
                                                "MBANKING_SERVER",
                                                strReference,

                                                strBeneficiaryMobileNo,
                                                strMemberName,
                                                strAccountNo,

                                                lnAmount,
                                                "LOAN_REPAYMENT");
                                    });
                                    worker.start();

                                } else {
                                    strResponse = "Use the details below to pay via " + strLOAN_REPAYMENT_OPTION + "\nPaybill no: " + strSender + "\n" + "Loan: " + strLoanNumber + "\n" + "Amount: KES " + strFormattedAmount + "\n";
                                }

                                //End USSD.
                                theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");

                            }

                        } else if (strConfirmation.equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your request to pay " + strLoanAccountLabel + " via " + strLOAN_REPAYMENT_OPTION + " was NOT confirmed. Pay Loan request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                        } else {
                            String strResponse = "";

                            if (strLOAN_REPAYMENT_OPTION.equalsIgnoreCase("Savings Account")) {
                                String strFromAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_FUNDS_ACCOUNT.name());
                                HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strFromAccountNoDetails);
                                String theCustomerIdentifier = hmFromAccountNoDetails.get("cust_id");
                                String strFromAccountNumber = hmFromAccountNoDetails.get("ac_no");
                                String strFromAccountName = hmFromAccountNoDetails.get("ac_name");
                                String strFromAccountLabel = hmFromAccountNoDetails.get("ac_label");
                                String strFromAccountAvailableBalance = hmFromAccountNoDetails.get("ac_bal");

                                strResponse = "Confirm Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\n{Select a valid menu}\nFunds Account: " + strFromAccountLabel + "-" + strFromAccountNumber + "\n" + "Loan: " + strLoanName + "-" + strLoanNumber + "\n" + "Amount: KES " + strFormattedAmount + "\n";
                            } else {
                                strResponse = "Confirm Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\n{Select a valid menu}\nPaybill no.: " + strSender + "\nLoan: " + strLoanName + "-" + strLoanNumber + "\nAmount: KES " + strFormattedAmount + "\n";
                            }

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_CONFIRMATION, "NO", theArrayListUSSDSelectOption);
                        }

                        break;
                    }

                    /*case LOAN_REPAYMENT_CLEARING_CONFIRMATION: {

                        String strLoanDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_LOAN.name());
                        HashMap<String, String> hmLoan = Utils.toHashMap(strLoanDetails);
                        String strLoanNumber = hmLoan.get("ac_no");
                        String strLoanBalance = hmLoan.get("bal");
                        String strLoanAccountLabel = hmLoan.get("ac_label");

                        String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_REPAYMENT_CLEARING_CONFIRMATION.name());
                        String[] strConfirmOptionsArr = strConfirmation.split("::");


                        if (strConfirmOptionsArr[0].equalsIgnoreCase("YES")) {

                            String strResponse = "Dear member, your request to Clear Loan " + strLoanAccountLabel + "(" + strLoanNumber + ") has been received successfully.\n";

                            Thread worker = new Thread(() -> {

                                TransactionWrapper<FlexicoreHashMap> loanPaymentWrapper = theUSSDAPI.loanPaymentViaSavings(theUSSDRequest, true, strConfirmOptionsArr[1]);
                                if (loanPaymentWrapper.hasErrors()) {
                                    FlexicoreHashMap internalFundsTransferMap = loanPaymentWrapper.getSingleRecord();
                                    String strErrorMessage = internalFundsTransferMap.getValue("cbs_api_return_val").toString() + "\n";
                                    strErrorMessage += internalFundsTransferMap.getStringValue("display_message");

                                    System.err.println("LoansMenus.displayMenu_LoanRepayment() - Response " + strErrorMessage);
                                }
                            });
                            worker.start();

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);

                        } else if (strConfirmOptionsArr[0].equalsIgnoreCase("NO")) {
                            String strResponse = "Dear member, your request to Clear Loan " + strLoanAccountLabel + " was NOT confirmed. Pay Loan request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                        } else {

                            //DONE INTENTIONALLY TO GO BACK TO CLEARING OPTION
                            String strHeader2 = "Pay Loan via " + strLOAN_REPAYMENT_OPTION + "\nPayment Option\n{Select a valid menu}";
                            theUSSDResponse = getLoanRepaymentClearingOption(theUSSDRequest, strHeader2);
                        }

                        break;
                    }*/

                    default: {
                        System.err.println("theAppMenus.displayMenu_LoanRepayment() UNKNOWN PARAM ERROR : theParam = " + theParam);

                        String strResponse = "Pay Loan\n{Sorry, an error has occurred while processing your request}";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_END, "NO", theArrayListUSSDSelectOption);
                        break;
                    }
                }

            }
        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_LoanRepayment() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanBalanceEnquiry(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Loan Balance Enquiry";

        try {
            switch (theParam) {
                case "MENU": {

                    if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_BALANCE)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_BALANCE_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    String strCustomerHeader = strHeader + " \nSelect member\n";
                    String strAccountsHeader = strHeader + " \nSelect account\n";

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER,
                            USSDAPIConstants.AccountType.LOAN,
                            AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT, AppConstants.USSDDataType.LOAN_BALANCE_END);

                    break;
                }
                case "CUSTOMER": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier,
                                AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT,
                                AppConstants.USSDDataType.LOAN_BALANCE_END);

                    } else {
                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER,
                                USSDAPIConstants.AccountType.LOAN,
                                AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT,
                                AppConstants.USSDDataType.LOAN_BALANCE_END);
                    }

                    break;
                }

                case "ACCOUNT": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER.name());
                    String strAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT.name());

                    if (strAccountNumber.length() > 0) {
                        String strResponse = strHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.LOAN_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {

                        String strCustomerHeader = strHeader + " \n{Select member}\n";
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_AccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT, AppConstants.USSDDataType.LOAN_BALANCE_END);
                        } else {

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER,
                                    USSDAPIConstants.AccountType.LOAN,
                                    AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT,
                                    AppConstants.USSDDataType.LOAN_BALANCE_END);
                        }
                    }

                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT.name());
                        HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strAccountDetails);
                        String strAccountName = hmToAccountDetails.get("ac_name");
                        String strAccountLabel = hmToAccountDetails.get("ac_label");
                        String strAccountNumber = hmToAccountDetails.get("ac_no");
                        String strAccountCd = hmToAccountDetails.get("ac_cd");
                        String strFullName = hmToAccountDetails.get("cust_name");
                        String strCustomerIdentifier = hmToAccountDetails.get("cust_id");

                        String strResponse = "Dear member, your Loan Balance Enquiry request for Account " + strAccountNumber + " has been received successfully. Please wait shortly as it's being processed.\n";

                        USSDAPI finalTheUSSDAPI = theUSSDAPI;
                        Thread worker = new Thread(() -> {
                            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = finalTheUSSDAPI.loanBalanceEnquiry(theUSSDRequest);
                            if (accountBalanceEnquiryWrapper.hasErrors()) {
                                FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();
                                String strErrorMessage = accountBalanceEnquiryMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += accountBalanceEnquiryMap.getStringValue("display_message");
                                System.err.println("LoansMenus.displayMenu_LoanBalanceEnquiry() - Response " + strErrorMessage);

                            }
                        });
                        worker.start();

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strHeader + "\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_LoanBalanceEnquiry() UNKNOWN PARAM ERROR : theParam = " + theParam);
                    String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }

                /*default: {

                    String strAccountType = null;

                    AppConstants.USSDDataType ussdDataType = getBalanceEnquiryCallerMenu(theUSSDRequest.getUSSDData().toString());

                    switch (ussdDataType) {
                        case MY_ACCOUNT_BALANCE_ACCOUNTS_LIST: {
                            strAccountType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNTS_LIST.name());
                            break;
                        }
                        case LOAN_MENU: {
                            strAccountType = USSDAPIConstants.AccountType.LOAN.getValue();
                            break;
                        }
                    }

                    if (strAccountType != null) {
                        switch (strAccountType) {
                            case "FOSA": {
                                strHeader = "Savings Accounts Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.FOSA;
                                break;
                            }
                            case "BOSA": {
                                strHeader = "Shares, Deposits and Benevolent Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.BOSA;
                                break;
                            }
                            case "LOAN": {
                                strHeader = "Loans Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.LOAN;
                                break;
                            }
                            case "ALL": {
                                strHeader = "All Accounts Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.ALL;
                                break;
                            }
                        }
                    }
                    theUSSDResponse = displayMenu_BalanceEnquiryMenus(theUSSDRequest, theParam, accountType, strHeader);
                    break;
                }*/
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_LoanBalanceEnquiry() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanBalanceEnquiryOLD(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Loan Balance Enquiry";

        try {
            switch (theParam) {
                case "MENU": {

                    if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_BALANCE)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_BALANCE_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }


                    String strResponse = strHeader + " \nEnter your PIN:";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                            AppConstants.USSDDataType.LOAN_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    /*String strCustomerHeader = strHeader + " \nSelect member\n";
                    String strAccountsHeader = strHeader + " \nSelect account\n";

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER,
                            USSDAPIConstants.AccountType.LOAN,
                            AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT, AppConstants.USSDDataType.LOAN_BALANCE_END);*/

                    break;
                }
                /*case "CUSTOMER": {


                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier,
                                AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT,
                                AppConstants.USSDDataType.LOAN_BALANCE_END);

                    } else {
                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER,
                                USSDAPIConstants.AccountType.LOAN,
                                AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT,
                                AppConstants.USSDDataType.LOAN_BALANCE_END);
                    }

                    break;
                }

                case "ACCOUNT": {
                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER.name());
                    String strAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT.name());

                    if (strAccountNumber.length() > 0) {
                        String strResponse = strHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.LOAN_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {

                        String strCustomerHeader = strHeader + " \n{Select member}\n";
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT, AppConstants.USSDDataType.LOAN_BALANCE_END);
                        } else {

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.LOAN_BALANCE_CUSTOMER,
                                    USSDAPIConstants.AccountType.LOAN,
                                    AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT,
                                    AppConstants.USSDDataType.LOAN_BALANCE_END);
                        }
                    }

                    break;
                }*/

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        /*String strAccountDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_BALANCE_ACCOUNT.name());
                        HashMap<String, String> hmToAccountDetails = Utils.toHashMap(strAccountDetails);
                        String strAccountName = hmToAccountDetails.get("ac_name");
                        String strAccountLabel = hmToAccountDetails.get("ac_label");
                        String strAccountNumber = hmToAccountDetails.get("ac_no");*/

                        /*String strResponse = "Dear member, your Loan Balance Enquiry request for " + strAccountLabel + " has been received successfully. Please wait shortly as it's being processed.\n";

                        USSDAPI finalTheUSSDAPI = theUSSDAPI;
                        Thread worker = new Thread(() -> {
                            TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = finalTheUSSDAPI.loanBalanceEnquiry(theUSSDRequest);
                            if (accountBalanceEnquiryWrapper.hasErrors()) {
                                FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();
                                String strErrorMessage = accountBalanceEnquiryMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += accountBalanceEnquiryMap.getStringValue("display_message");
                                System.err.println("LoansMenus.displayMenu_LoanBalanceEnquiry() - Response " + strErrorMessage);

                            }
                        });
                        worker.start();*/

                        String strResponse = "";
                        TransactionWrapper<FlexicoreHashMap> accountBalanceEnquiryWrapper = theUSSDAPI.loanBalanceEnquiry(theUSSDRequest);
                        FlexicoreHashMap accountBalanceEnquiryMap = accountBalanceEnquiryWrapper.getSingleRecord();

                        if (accountBalanceEnquiryWrapper.hasErrors()) {
                            String strErrorMessage = accountBalanceEnquiryMap.getValue("cbs_api_return_val").toString() + "\n";
                            strErrorMessage += accountBalanceEnquiryMap.getStringValue("display_message");
                            strResponse = accountBalanceEnquiryMap.getStringValue("display_message");

                            //System.err.println("BalanceEnquiryMenus.displayMenu_BalanceEnquiry() - Response " + strErrorMessage);
                        } else {
                            CBSAPI.SMSMSG cbsMSG = accountBalanceEnquiryMap.getValue("msg_object");

                            String strMSG = cbsMSG.getMessage();

                            if (strMSG.length() > 180) {
                                strMSG = strMSG.substring(0, 180);
                            }

                            strResponse = strMSG;
                        }

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strHeader + "\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_BALANCE_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_LoanBalanceEnquiry() UNKNOWN PARAM ERROR : theParam = " + theParam);
                    String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}\n";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_BALANCE_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }

                /*default: {

                    String strAccountType = null;

                    AppConstants.USSDDataType ussdDataType = getBalanceEnquiryCallerMenu(theUSSDRequest.getUSSDData().toString());

                    switch (ussdDataType) {
                        case MY_ACCOUNT_BALANCE_ACCOUNTS_LIST: {
                            strAccountType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_BALANCE_ACCOUNTS_LIST.name());
                            break;
                        }
                        case LOAN_MENU: {
                            strAccountType = USSDAPIConstants.AccountType.LOAN.getValue();
                            break;
                        }
                    }

                    if (strAccountType != null) {
                        switch (strAccountType) {
                            case "FOSA": {
                                strHeader = "Savings Accounts Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.FOSA;
                                break;
                            }
                            case "BOSA": {
                                strHeader = "Shares, Deposits and Benevolent Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.BOSA;
                                break;
                            }
                            case "LOAN": {
                                strHeader = "Loans Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.LOAN;
                                break;
                            }
                            case "ALL": {
                                strHeader = "All Accounts Balance Enquiry";
                                accountType = USSDAPIConstants.AccountType.ALL;
                                break;
                            }
                        }
                    }
                    theUSSDResponse = displayMenu_BalanceEnquiryMenus(theUSSDRequest, theParam, accountType, strHeader);
                    break;
                }*/
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_LoanBalanceEnquiry() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanMiniStatement(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String strHeader = "Loan Mini-Statement";

        try {

            switch (theParam) {
                case "MENU": {

                    if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.LOAN_STATEMENT)) {
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strHeader + "\n" + AppConstants.strServiceUnavailable);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END, "NO", theArrayListUSSDSelectOption);
                        return theUSSDResponse;
                    }

                    String strCustomerHeader = strHeader + " \nSelect member\n";
                    String strAccountsHeader = strHeader + " \nSelect account\n";

                    theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest,
                            theParam, strCustomerHeader, strAccountsHeader, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_CUSTOMER,
                            USSDAPIConstants.AccountType.LOAN,
                            AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END);

                    break;
                }
                case "CUSTOMER": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MINI_STATEMENT_CUSTOMER.name());

                    if (strCustomerIdentifier.length() > 0) {
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier,
                                AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT,
                                AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END);

                    } else {
                        String strCustomerHeader = strHeader + " \n{Select a valid member}\n";
                        String strAccountsHeader = strHeader + " \nSelect account\n";
                        theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                AppConstants.USSDDataType.LOAN_MINI_STATEMENT_CUSTOMER,
                                USSDAPIConstants.AccountType.LOAN,
                                AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT,
                                AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END);
                    }

                    break;
                }

                case "ACCOUNT": {

                    String strCustomerIdentifier = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MINI_STATEMENT_CUSTOMER.name());
                    String strAccountNumber = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT.name());

                    if (strAccountNumber.length() > 0) {
                        String strResponse = strHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.LOAN_MINI_STATEMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {

                        String strCustomerHeader = strHeader + " \n{Select member}\n";
                        String strAccountsHeader = strHeader + " \n{Select a valid account}\n";
                        if (strCustomerIdentifier != null && strCustomerIdentifier.length() > 0) {
                            theUSSDResponse = GeneralMenus.displayMenu_LoanAccountsList(theUSSDRequest, theParam, strAccountsHeader, "CUSTOMER_NO", strCustomerIdentifier, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END);
                        } else {

                            theUSSDResponse = GeneralMenus.displayMenu_CustomersList(theUSSDRequest, theParam, strCustomerHeader, strAccountsHeader,
                                    AppConstants.USSDDataType.LOAN_MINI_STATEMENT_CUSTOMER,
                                    USSDAPIConstants.AccountType.LOAN,
                                    AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT,
                                    AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END);
                        }
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MINI_STATEMENT_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strAccountNoDetails = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_MINI_STATEMENT_ACCOUNT.name());

                        HashMap<String, String> hmFromAccountNoDetails = Utils.toHashMap(strAccountNoDetails);
                        String strAccountNumber = hmFromAccountNoDetails.get("ac_no");

                        String strResponse = "Dear member, your Mini Statement request for Loan " + strAccountNumber + " has been received successfully. Please wait shortly as it's being processed.\n";

                        USSDAPI finalTheUSSDAPI = theUSSDAPI;
                        Thread worker = new Thread(() -> {
                            TransactionWrapper<FlexicoreHashMap> accountMiniStatementWrapper = finalTheUSSDAPI.loanMiniStatement(theUSSDRequest);
                            if (accountMiniStatementWrapper.hasErrors()) {
                                FlexicoreHashMap accountMiniStatementMap = accountMiniStatementWrapper.getSingleRecord();
                                String strErrorMessage = accountMiniStatementMap.getValue("cbs_api_return_val").toString() + "\n";
                                strErrorMessage += accountMiniStatementMap.getStringValue("display_message");

                                System.err.println("LoanMenus.displayMenu_LoanMiniStatement() - Response " + strErrorMessage);
                            }
                        });
                        worker.start();

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strHeader + "\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_MINI_STATEMENT_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

            }

        } catch (Exception e) {
            System.err.println("LoanMenus.displayMenu_LoanMiniStatement() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getLoanRepaymentOption(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();
        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "M-PESA", "1: M-PESA");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "Savings Account", "2: Savings Account");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_REPAYMENT_OPTION, "NO", theArrayListUSSDSelectOption);
            return theUSSDResponse;
        } catch (Exception e) {
            System.err.println("theAppMenus.getLoanRepaymentOption() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoanGuarantors(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        try {
            String strLoan = "";
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            switch (theParam) {
                case "MENU": {
                    String strHeader = "Loan Guarantors";
                    theUSSDResponse = GeneralMenus.displayMenu_LoanTypesForGurantorship(theUSSDRequest, theParam, strHeader, USSDAPIConstants.AccountType.ALL, AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE);
                    break;
                }
                case "TYPE": {

                    String strGuarantorsType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());

                    if (!strGuarantorsType.equals("")) {
                        String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());
                        HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                        String strLoanTypeID = hmLoanType.get("id");
                        String strLoanTypeCode = hmLoanType.get("code");
                        String strLoanTypeName = hmLoanType.get("name");
                        String strLoanTypeMaxAmount = hmLoanType.get("max");
                        String strLoanTypeMinAmount = hmLoanType.get("min");
                        String strLoanTypeMaxDuration = hmLoanType.get("duration");
                        String strLoanTypeInterest = hmLoanType.get("interest");

                        //TODO: check if Loan requires guarantors
                        //if yes
                        String strResponse = strLoanTypeName + " Guarantors\n";
                        theUSSDResponse = getGuarantorsMenus(theUSSDRequest, strResponse);
                        //if no

                    } else {
                        String strHeader = "Loan Guarantors\n{Select a valid menu}";
                        theUSSDResponse = GeneralMenus.displayMenu_LoanTypesForGurantorship(theUSSDRequest, theParam, strHeader, USSDAPIConstants.AccountType.ALL, AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE);
                    }
                    break;
                }
                case "OPTION": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strResponse = strLoanTypeName + " Guarantors\n";
                    String strAction = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_OPTION.name());

                    switch (strAction) {
                        case "VIEW_GUARANTORS": {
                            //TODO: fetch all guarantors of the loan
                            theUSSDResponse = getAllLoanGuarantors(theUSSDRequest, strResponse);
                            break;
                        }
                        case "ADD_GUARANTOR": {
                            //TODO: check if all required number of guarantors have been added
                            strResponse = strLoanTypeName + " Add Guarantor\n" + "Enter Guarantor Mobile No.";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_GUARANTORS_MOBILE_NUMBER, USSDConstants.USSDInputType.STRING, "NO");
                            break;
                        }
                        default: {
                            strResponse = strLoanTypeName + " Guarantors\n{Select a valid menu}";
                            theUSSDResponse = getGuarantorsMenus(theUSSDRequest, strResponse);
                            break;
                        }
                    }
                    break;
                }
                case "GUARANTORS": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strGuarantor = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_GUARANTORS.name());
                    if (strGuarantor != null && !strGuarantor.isEmpty()) {
                        HashMap<String, String> hmGuarantor = Utils.toHashMap(strGuarantor);
                        String strName = hmGuarantor.get("NAME");
                        String strID = hmGuarantor.get("ID");
                        String strMobileNo = hmGuarantor.get("MOBILE_NUMBER");

                        String strResponse = strLoanTypeName + " Guarantors\n";

                        strResponse = strResponse + "Guarantor Details:\n";
                        strResponse = strResponse + "\nName: " + strName;
                        strResponse = strResponse + "\nID: " + strID;
                        strResponse = strResponse + "\nMobile: " + strMobileNo;

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_END, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = strLoanTypeName + " Guarantors\n{Select a valid menu}";
                        theUSSDResponse = getAllLoanGuarantors(theUSSDRequest, strResponse);
                    }
                    break;
                }
                case "MOBILE_NUMBER": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strResponse = strLoanTypeName + " Add Guarantor\n";

                    String strMobileNo = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_MOBILE_NUMBER.name());
                    strMobileNo = APIUtils.sanitizePhoneNumber(strMobileNo);

                    if (!strMobileNo.equalsIgnoreCase("INVALID MOBILE NUMBER")/* || !strOtherMobileNo.matches("^254((7[0-2][0-9])|(74[0-3])|(74[5-6])|(748)|(75[7-9])|(76[8-9])|(79[0-9]))[0-9]{6}$")*/) {
                        //TODO: check if user with specified mobile number exists. and check if user is already a guarantor for the loan
                        //if okay
                        strResponse = strResponse + "\nEnter PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_GUARANTORS_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        strResponse = strResponse + "{Enter a valid mobile number}\nEnter Guarantor Mobile No.\n";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_GUARANTORS_MOBILE_NUMBER, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }
                case "PIN": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {
                        //TODO: fetch guarantor details with mobile number
                        String strResponse = "Confirm Guarantor Details for " + strLoanTypeName + ":\n";
                        theUSSDResponse = addGuarantorConfirmation(theUSSDRequest, strResponse);
                    } else {
                        String strResponse = strLoanTypeName + " Add Guarantor\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOAN_GUARANTORS_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }
                case "CONFIRMATION": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOAN_GUARANTORS_CONFIRMATION.name());
                    if (strConfirmation.equalsIgnoreCase("YES")) {
                        String strResponse = "Dear member, your " + strLoanTypeName + " Add Guarantor request has been received successfully. Please wait shortly as it's being processed.";

                        Thread worker = new Thread(() -> {
                            //TODO: call function for adding guarantor
                            /*APIConstants.TransactionReturnVal transactionReturnVal = theUSSDAPI.loanApplication(theUSSDRequest);
                            System.out.println("loanApplication: " + transactionReturnVal.getValue());*/
                        });
                        worker.start();
                        /*
                        APIConstants.TransactionReturnVal transactionReturnVal = theUSSDAPI.loanApplication(theUSSDRequest);

                        String strResponse ="";

                        if(transactionReturnVal.equals(APIConstants.TransactionReturnVal.SUCCESS)){
                            strResponse = "Dear member, your "+strLoanName+" Application request has been received successfully. Please wait shortly as it's being processed.";
                        }else {


                            switch (transactionReturnVal) {
                                case INCORRECT_PIN: {
                                    strResponse = "Sorry the PIN provided is incorrect. Your "+strLoanName+" Application request CANNOT be completed.\n";
                                    break;
                                }
                                case BLOCKED: {
                                    strResponse = "Dear member, your account has been blocked. Your "+strLoanName+" Application request CANNOT be completed.\n";
                                    break;
                                }
                                default: {
                                    strResponse = "Sorry, your "+strLoanName+" Application request CANNOT be completed at the moment. Please try again later.\n";
                                    break;
                                }
                            }
                        }
                        */
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_END, "NO", theArrayListUSSDSelectOption);

                    } else if (strConfirmation.equalsIgnoreCase("NO")) {
                        String strResponse = "Dear member, your " + strLoanTypeName + " Add Guarantor request NOT confirmed. Add Guarantor request NOT COMPLETED.";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_END, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strResponse = "Confirm Guarantor Details for " + strLoanTypeName + ":\n{Select a valid menu}\n";
                        theUSSDResponse = addGuarantorConfirmation(theUSSDRequest, strResponse);
                    }
                    break;
                }
                case "END": {
                    String strResponse = "Loan Guarantors\n{Select a valid menu}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_END, "NO", theArrayListUSSDSelectOption);
                    break;
                }

                default: {
                    System.err.println("theAppMenus.displayMenu_LoanGuarantors() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = "Loan Guarantors\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_LoanGuarantors() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getGuarantorsMenus(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "VIEW_GUARANTORS", "1: View Guarantors");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "ADD_GUARANTOR", "2: Add Guarantor");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_OPTION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.getGuarantorsMenus() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getAllLoanGuarantors(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            //TODO: fetch all guarantors of the loan
            //SAMPLE TEST
            List<HashMap<String, String>> lstGuarantors = new ArrayList<HashMap<String, String>>() {{
                add(new HashMap<String, String>() {{
                    put("NAME", "James Munene");
                    put("ID", "123456");
                    put("MOBILE_NUMBER", "254712082273");
                }});

                add(new HashMap<String, String>() {{
                    put("NAME", "Mary Ann");
                    put("ID", "48189294");
                    put("MOBILE_NUMBER", "2547009988556");
                }});

                add(new HashMap<String, String>() {{
                    put("NAME", "Alex Kibet");
                    put("ID", "983626633");
                    put("MOBILE_NUMBER", "2547018873476");
                }});

            }};
            int i = 0;
            for (HashMap<String, String> hmGuarantor : lstGuarantors) {
                i++;
                String strOptionValue = Utils.serialize(hmGuarantor);
                String strName = hmGuarantor.get("NAME");
                String strOptionMenu = Integer.toString(i);
                String strOptionDisplayText = strOptionMenu + ": " + strName;
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
            }

            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_GUARANTORS, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("LoansMenus.getAllLoanGuarantors() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse addGuarantorConfirmation(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            //TODO: fetch guarantor details with mobile number
            //if found
            //SAMPLE TEST
            HashMap<String, String> hmGuarantor = new HashMap<String, String>() {{
                put("NAME", "James Munene");
                put("ID", "123456");
                put("MOBILE_NUMBER", "254712082273");
            }};

            String strName = hmGuarantor.get("NAME");
            String strID = hmGuarantor.get("ID");
            String stMobileNumber = hmGuarantor.get("MOBILE_NUMBER");

            theHeader = theHeader + "\nName: " + strName;
            theHeader = theHeader + "\nID: " + strID;
            theHeader = theHeader + "\nMobile: " + stMobileNumber;

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.LOAN_GUARANTORS_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("LoansMenus.addGuarantorConfirmation() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_LoansGuaranteed(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        final USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();
        try {
            String strLoan = "";
            String strMobileNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());

            switch (theParam) {
                case "MENU": {
                    String strHeader = "Loans Guaranteed\n";
                    theUSSDResponse = getLoansGuaranteedOptions(theUSSDRequest, strHeader);
                    break;
                }
                case "OPTION": {
                    String strOptions = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_OPTION.name());
                    if (!strOptions.equals("")) {
                        String strHeader = "Loans Guaranteed - " + strOptions;
                        theUSSDResponse = GeneralMenus.displayMenu_LoanTypesForGurantorship(theUSSDRequest, theParam, strHeader, USSDAPIConstants.AccountType.ALL, AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE);
                    } else {
                        String strHeader = "Loans Guaranteed\n{Select a valid menu}";
                        theUSSDResponse = getLoansGuaranteedOptions(theUSSDRequest, strHeader);
                    }
                    break;
                }
                case "TYPE": {
                    String strOptions = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_OPTION.name());
                    String strGuarantorsType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE.name());

                    if (!strGuarantorsType.equals("")) {
                        String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE.name());
                        HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                        String strLoanTypeID = hmLoanType.get("id");
                        String strLoanTypeCode = hmLoanType.get("code");
                        String strLoanTypeName = hmLoanType.get("name");
                        String strLoanTypeMaxAmount = hmLoanType.get("max");
                        String strLoanTypeMinAmount = hmLoanType.get("min");
                        String strLoanTypeMaxDuration = hmLoanType.get("duration");
                        String strLoanTypeInterest = hmLoanType.get("interest");

                        if (strOptions.equals("PENDING")) {
                            String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n";
                            theUSSDResponse = getLoansGuaranteed_LoanDetails(theUSSDRequest, strResponse, "PENDING");
                        } else {
                            String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n";
                            theUSSDResponse = getLoansGuaranteed_LoanDetails(theUSSDRequest, strResponse, "APPROVED");
                        }
                    } else {
                        String strHeader = "Loans Guaranteed - " + strOptions + "\n{Select a valid menu}";
                        theUSSDResponse = GeneralMenus.displayMenu_LoanTypesForGurantorship(theUSSDRequest, theParam, strHeader, USSDAPIConstants.AccountType.ALL, AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE);
                    }
                    break;
                }
                case "LOAN_DETAILS": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strOptions = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_OPTION.name());
                    String strLoaner = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_LOAN_DETAILS.name());

                    if (strLoaner != null && !strLoaner.isEmpty()) {
                        if (strOptions.equals("PENDING")) {
                            String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n";
                            strResponse = strResponse + "Enter PIN:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOANS_GUARANTEED_PIN, USSDConstants.USSDInputType.STRING, "NO");
                        } else {
                            HashMap<String, String> hmGuarantor = Utils.toHashMap(strLoaner);
                            String strLoanSerial = hmGuarantor.get("LOAN_SERIAL");
                            String strName = hmGuarantor.get("NAME");
                            String stPhoneNumber = hmGuarantor.get("MOBILE_NUMBER");
                            String strAmount = hmGuarantor.get("AMOUNT");
                            String strDate = hmGuarantor.get("DATE");

                            String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n";

                            strResponse = strResponse + "\nSerial: " + strLoanSerial;
                            strResponse = strResponse + "\nName: " + strName;
                            strResponse = strResponse + "\nMobile: " + stPhoneNumber;
                            strResponse = strResponse + "\nAmount: " + strAmount;
                            strResponse = strResponse + "\nDate: " + strDate;


                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_END, "NO", theArrayListUSSDSelectOption);
                        }
                    } else {
                        if (strOptions.equals("PENDING")) {
                            String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n{Select a valid menu}";
                            theUSSDResponse = getLoansGuaranteed_LoanDetails(theUSSDRequest, strResponse, "PENDING");
                        } else {
                            String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n{Select a valid menu}";
                            theUSSDResponse = getLoansGuaranteed_LoanDetails(theUSSDRequest, strResponse, "APPROVED");
                        }
                    }
                    break;
                }
                case "PIN": {
                    String strOptions = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_OPTION.name());

                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {
                        String strResponse = "Confirm Details for " + strLoanTypeName + ":\n";
                        theUSSDResponse = confirmLoanGuarantee(theUSSDRequest, strResponse);
                    } else {
                        String strResponse = strLoanTypeName + " Guaranteed - " + strOptions + "\n{Please enter correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.LOANS_GUARANTEED_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }
                case "CONFIRMATION": {
                    String strLoanType = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_TYPE.name());
                    HashMap<String, String> hmLoanType = Utils.toHashMap(strLoanType);
                    String strLoanTypeID = hmLoanType.get("id");
                    String strLoanTypeCode = hmLoanType.get("code");
                    String strLoanTypeName = hmLoanType.get("name");
                    String strLoanTypeMaxAmount = hmLoanType.get("max");
                    String strLoanTypeMinAmount = hmLoanType.get("min");
                    String strLoanTypeMaxDuration = hmLoanType.get("duration");
                    String strLoanTypeInterest = hmLoanType.get("interest");

                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_CONFIRMATION.name());
                    if (strConfirmation.equalsIgnoreCase("YES")) {
                        String strResponse = "Dear member, your Loan Guarantee request has been received successfully. Please wait shortly as it's being processed.";

                        Thread worker = new Thread(() -> {
                            //TODO: call function for adding guarantor
                            /*APIConstants.TransactionReturnVal transactionReturnVal = theUSSDAPI.loanApplication(theUSSDRequest);
                            System.out.println("loanApplication: " + transactionReturnVal.getValue());*/
                        });
                        worker.start();
                        /*
                        APIConstants.TransactionReturnVal transactionReturnVal = theUSSDAPI.loanApplication(theUSSDRequest);

                        String strResponse ="";

                        if(transactionReturnVal.equals(APIConstants.TransactionReturnVal.SUCCESS)){
                            strResponse = "Dear member, your "+strLoanName+" Application request has been received successfully. Please wait shortly as it's being processed.";
                        }else {


                            switch (transactionReturnVal) {
                                case INCORRECT_PIN: {
                                    strResponse = "Sorry the PIN provided is incorrect. Your "+strLoanName+" Application request CANNOT be completed.\n";
                                    break;
                                }
                                case BLOCKED: {
                                    strResponse = "Dear member, your account has been blocked. Your "+strLoanName+" Application request CANNOT be completed.\n";
                                    break;
                                }
                                default: {
                                    strResponse = "Sorry, your "+strLoanName+" Application request CANNOT be completed at the moment. Please try again later.\n";
                                    break;
                                }
                            }
                        }
                        */
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_END, "NO", theArrayListUSSDSelectOption);

                    } else if (strConfirmation.equalsIgnoreCase("NO")) {
                        String strResponse = "Dear member, your Loan Guarantee request NOT confirmed. Loan Guarantee request NOT COMPLETED.";
                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_END, "NO", theArrayListUSSDSelectOption);
                    } else {
                        String strResponse = "Confirm Details for " + strLoanTypeName + ":\n{Select a valid menu}\n";
                        theUSSDResponse = confirmLoanGuarantee(theUSSDRequest, strResponse);
                    }
                    break;
                }
                case "END": {
                    String strResponse = "Loans Guaranteed\n{Select a valid menu}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_END, "NO", theArrayListUSSDSelectOption);
                    break;
                }

                default: {
                    System.err.println("theAppMenus.displayMenu_LoansGuaranteed() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = "Loans Guaranteed\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_END, "NO", theArrayListUSSDSelectOption);

                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_LoanGuarantors() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getLoansGuaranteedOptions(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "1", "PENDING", "1: Pending");
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, "2", "APPROVED", "2: Approved");
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_OPTION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.getLoansGuaaranteedOptions() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse getLoansGuaranteed_LoanDetails(USSDRequest theUSSDRequest, String theHeader, String strLoanStatus) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            //TODO: fetch all loans pending approval with strStatus
            //SAMPLE TEST
            List<HashMap<String, String>> lstLoaners = new ArrayList<HashMap<String, String>>() {{
                add(new HashMap<String, String>() {{
                    put("LOAN_SERIAL", "LN9283838");
                    put("NAME", "James Munene");
                    put("ID", "12345688");
                    put("MOBILE_NUMBER", "254712082273");
                    put("AMOUNT", "23456");
                    put("DATE", "2020-06-25 12:00:00");
                }});

                add(new HashMap<String, String>() {{
                    put("LOAN_SERIAL", "LN0002883");
                    put("NAME", "Mary Ann");
                    put("ID", "48189294");
                    put("MOBILE_NUMBER", "2547009988556");
                    put("AMOUNT", "876455");
                    put("DATE", "2020-06-25 12:00:00");
                }});

                add(new HashMap<String, String>() {{
                    put("LOAN_SERIAL", "LN1255454");
                    put("NAME", "Alex Kibet");
                    put("ID", "983626633");
                    put("MOBILE_NUMBER", "2547018873476");
                    put("AMOUNT", "1299480");
                    put("DATE", "2020-06-25 12:00:00");
                }});
            }};
            int i = 0;
            for (HashMap<String, String> hmGuarantor : lstLoaners) {
                i++;
                String strOptionValue = Utils.serialize(hmGuarantor);
                String strLoanSerial = hmGuarantor.get("LOAN_SERIAL");
                String strName = hmGuarantor.get("NAME");
                String strOptionMenu = Integer.toString(i);
                String strOptionDisplayText = strOptionMenu + ": " + strLoanSerial + " - " + strName;
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strOptionMenu, strOptionValue, strOptionDisplayText);
            }
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_LOAN_DETAILS, "NO", theArrayListUSSDSelectOption);
        } catch (Exception e) {
            System.err.println("LoansMenus.getAllLoaners_PENDING() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse confirmLoanGuarantee(USSDRequest theUSSDRequest, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            //SAMPLE TEST
            String strLoaner = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOANS_GUARANTEED_LOAN_DETAILS.name());

            HashMap<String, String> hmGuarantor = Utils.toHashMap(strLoaner);

            String strLoanSerial = hmGuarantor.get("LOAN_SERIAL");
            String strName = hmGuarantor.get("NAME");
            String strMobileNumber = hmGuarantor.get("MOBILE_NUMBER");
            String strAmount = hmGuarantor.get("AMOUNT");
            String strDate = hmGuarantor.get("DATE");

            theHeader = theHeader + "\nSerial: " + strLoanSerial;
            theHeader = theHeader + "\nName: " + strName;
            theHeader = theHeader + "\nMobile: " + strMobileNumber;
            theHeader = theHeader + "\nAmount: " + strAmount;
            theHeader = theHeader + "\nDate: " + strDate;

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.LOANS_GUARANTEED_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("LoansMenus.confirmLoanGuarantee() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

}

