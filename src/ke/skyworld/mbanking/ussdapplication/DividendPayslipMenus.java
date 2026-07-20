package ke.skyworld.mbanking.ussdapplication;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.file_utils.FileOps;
import ke.co.skyworld.smp.utility_items.security.HashUtils;
import ke.skyworld.lib.mbanking.ussd.*;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.cbs.ApStarCBS;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.channelutils.EmailMessaging;
import ke.skyworld.mbanking.mappapi.AccountStatements;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;

public interface DividendPayslipMenus {

    default USSDResponse displayMenu_DividendPayslipMenus(USSDRequest theUSSDRequest, String theParam, String theHeader) {
        USSDResponse theUSSDResponse = null;
        AppMenus theAppMenus = new AppMenus();

        try {
            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();

            FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.USSD, AppConstants.MobileBankingServices.ACCOUNT_STATEMENT);
            String strServiceStatus = getServiceStatusDetails.getStringValue("status");

            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader + "\n" + getServiceStatusDetails.getStringValue("display_message"));
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;

            } else if (CBSAPI.isMandateInactive(theUSSDRequest.getUSSDMobileNo(), AppConstants.MobileMandates.ACCOUNT_STATEMENT)) {
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader + "\n" + AppConstants.strServiceUnavailable);
                theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_END, "NO", theArrayListUSSDSelectOption);
                return theUSSDResponse;
            }

            theUSSDResponse = displayMenu_DividendPayslipYears(theUSSDRequest, theHeader+"\nSelect Year");

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_DividendPayslipMenus() ERROR : " + e.getMessage());
        } finally {
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    public default USSDResponse displayMenu_DividendPayslipYears(USSDRequest theUSSDRequest, String theHeader) {

        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        try {

            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, theHeader);

            int intCurrentYear = LocalDate.now().getYear()-2;

            String strDisplayYear;
            String strValueYear;

            for (int i = 1; i <= 1; i++) {
                strDisplayYear = String.valueOf(intCurrentYear) ;
                strValueYear = strDisplayYear;
                USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, Integer.toString(i), strValueYear, i + ": " + strDisplayYear);
                intCurrentYear--;
            }
            
            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_YEAR, "NO", theArrayListUSSDSelectOption);

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_TermsAndConditions() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }

    default USSDResponse displayMenu_DividendPayslip(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        String theHeader = "Dividend Payslip";

        try {

            switch (theParam) {
                case "MENU": {
                    theUSSDResponse = displayMenu_DividendPayslipMenus(theUSSDRequest, theParam, theHeader);
                    break;
                }
                case "YEAR": {
                    String strPayslipYear = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_YEAR.name());
                    if (strPayslipYear.matches("\\d{4}")) {
                        String strResponse = theHeader + " \nEnter Email Address:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_EMAIL_ADDRESS, USSDConstants.USSDInputType.STRING, "NO");

                    } else {
                        String strResponse = theHeader + " \n{Invalid Option}\nSelect year:";
                        theUSSDResponse = displayMenu_DividendPayslipYears(theUSSDRequest, strResponse);
                    }

                    break;
                }

                case "EMAIL_ADDRESS": {

                    String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_EMAIL_ADDRESS.name());

                    if (AppUtils.isValidEmailAddress(strEmailAddress)) {
                        String strResponse = theHeader + " \nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_PIN, USSDConstants.USSDInputType.STRING, "NO");

                    } else {
                        String strResponse = theHeader + " \n{Invalid Email}\nEnter a Valid Email Address:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse,
                                AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_EMAIL_ADDRESS, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "PIN": {
                    String strLoginPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.LOGIN_PIN.name());
                    String strPIN = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_PIN.name());
                    if (strLoginPIN.equals(strPIN)) {

                        String strYear = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_YEAR.name());
                        String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_EMAIL_ADDRESS.name());

                        String strResponse = "Confirm Dividend Payslip Request"
                                             +"\nYear: "+strYear
                                             +"\nEmail: "+strEmailAddress;

                        ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                        USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                        theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                    } else {
                        String strResponse = theHeader+"\n{Please enter a correct PIN}\nEnter your PIN:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_PIN, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {
                            String strYear = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_YEAR.name());
                            String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_EMAIL_ADDRESS.name());
                            String strPhoneNumber = String.valueOf(theUSSDRequest.getUSSDMobileNo());
                            //get user full names
                            String strFullName = theUSSDAPI.getUserFullName(strPhoneNumber);

                            String strResponse = "Your Dividend Payslip for Year " + strYear + " has been received and will be sent to '"+strEmailAddress+"'.\n";



                            Thread worker = new Thread(() -> {

                                //call get Dividend Payslip APi
                                TransactionWrapper<FlexicoreHashMap> flexicoreHashMapTransactionWrapper = ApStarCBS.getDividendPayslipReport("MSISDN", String.valueOf(theUSSDRequest.getUSSDMobileNo()),strYear,strEmailAddress);
                                FlexicoreHashMap fxhashmapStatus = flexicoreHashMapTransactionWrapper.getSingleRecord();
                                FlexicoreHashMap getChargesMap = fxhashmapStatus.getFlexicoreHashMap("response_payload");
                                String strBase64ReportData = getChargesMap.getStringValue("data");




                                String theFileName = saveBase64PDF(strBase64ReportData,strEmailAddress);

                                String[] filenameArr = theFileName.split("/");


                                HashMap<String, String> attachmentsMap = new HashMap<>();
                                attachmentsMap.put(filenameArr[filenameArr.length-1], theFileName);

//                                strFormattedStartDate = DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "dd MMM yyyy");
//                                strFormattedEndDate = DateTime.convertStringToDateToString(strStartDate, "dd/MM/yyyy", "dd MMM yyyy");

                                String emailTemplate = AccountStatements.getDividendPayslipHtml();

                                emailTemplate = emailTemplate
                                        .replace("[YEAR]", strYear)
                                                .replace("[FULL_NAME]",strFullName)
                                                        .replace("[PHONE_NUMBER]",strPhoneNumber);

                                EmailMessaging.sendEmail(strEmailAddress, "Dividend Payslip", emailTemplate, "DIVIDEND", attachmentsMap);



                            });
                            worker.start();

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_END, "NO", theArrayListUSSDSelectOption);

                            break;
                        }
                        case "NO": {
                            String strResponse = "";
                            strResponse = "Dear member, your Dividend Payslip request was NOT confirmed. Dividend Payslip request NOT COMPLETED.\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_END, "NO", theArrayListUSSDSelectOption);
                            break;
                        }
                        default: {

                            String strYear = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_YEAR.name());
                            String strEmailAddress = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_EMAIL_ADDRESS.name());

                            String strResponse = "Confirm Dividend Payslip Request\n{Select a valid menu}\n"
                                                 +"\nYear: "+strYear
                                                 +"\nEmail: "+strEmailAddress;

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmation(theUSSDRequest, AppConstants.USSDDataType.MY_ACCOUNT_DIVIDEND_PAYSLIP_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                            break;
                        }
                    }
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_DividendPayslip() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }



    public static String saveBase64PDF(String strBase64Report, String strEmail) {
        String strTheOutputFileName = System.getProperty("user.dir") + "/system-folders/outputs/"+ "Dividend-Payslip-" + DateTime.getCurrentDateTime("yyyy-MM-dd-HHmmss") + ".pdf";

        OutputStream outputStream = null;

        try {
            byte[] pdfData = Base64.getDecoder().decode(strBase64Report);

            outputStream = new FileOutputStream(strTheOutputFileName);
            outputStream.write(pdfData);

            return strTheOutputFileName;  // Return the file path if successful

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return "";
    }
}
