package ke.skyworld.mbanking.ussdapplication;

import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingUtils;
import ke.skyworld.lib.mbanking.ussd.USSDConstants;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.ussd.USSDResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Utils;
import ke.skyworld.mbanking.pesaapi.PESAAPI;
import ke.skyworld.mbanking.pesaapi.PESAAPIConstants;
import ke.skyworld.mbanking.pesaapi.PesaParam;
import ke.skyworld.mbanking.ussdapi.USSDAPI;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import java.util.ArrayList;
import java.util.HashMap;

public interface BuyGoodsMenu {
    default USSDResponse displayMenu_BuyGoodsMenus(USSDRequest theUSSDRequest, String theParam) {
        USSDResponse theUSSDResponse = null;
        USSDAPI theUSSDAPI = new USSDAPI();
        AppMenus theAppMenus = new AppMenus();

        PesaParam pesaParam = PESAAPI.getPesaParam(MBankingConstants.ApplicationType.PESA, PESAAPIConstants.PESA_PARAM_TYPE.MPESA_C2B);
        String strSender = pesaParam.getSenderIdentifier();

        try {
            String strHeader = "Lipa na "+AppConstants.strSACCOProductName;

            System.out.println("The Param @ displayMenu_BuyGoodsMenus(): "+theParam);

            switch (theParam) {
                case "MENU": {
                    String strResponse = strHeader + "\n";
                    strResponse = strResponse + "Enter Business Short Code:";
                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE, USSDConstants.USSDInputType.STRING, "NO");
                    break;
                }

                case "BUSINESS_SHORT_CODE": {
                    HashMap<String, String> verifyBusinessShortCodeRVal = theUSSDAPI.verifyBusinessShortCode(theUSSDRequest);
                    String status = verifyBusinessShortCodeRVal.get("STATUS");

                    if (status.equals("SUCCESS")) {
                        String strResponse = strHeader + "\n";
                        strResponse = strResponse + "Enter Amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    } else if (status.equals("SHORT_CODE_NOT_FOUND")) {
                        String strResponse = strHeader + "\n{Please enter a valid Business Short Code}\nEnter Business Short Code:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE, USSDConstants.USSDInputType.STRING, "NO");
                    } else {
                        String strResponse = strHeader + "\n{Error validating Business Short Code. Please retry}\nEnter Business Short Code:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE, USSDConstants.USSDInputType.STRING, "NO");
                    }

                    break;
                }

                case "AMOUNT": {
                    String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.BUY_GOODS_AMOUNT.name());
                    if (strAmount.matches("^[1-9][0-9]*$")) {
                        strAmount = Utils.formatDouble(strAmount, "#,###");

                        HashMap<String, String> verifyBusinessShortCodeRVal = theUSSDAPI.verifyBusinessShortCode(theUSSDRequest);
                        String status = verifyBusinessShortCodeRVal.get("STATUS");
                        String strBusinessShortCode = verifyBusinessShortCodeRVal.get("BUSINESS_SHORT_CODE");
                        String strBusinessName = verifyBusinessShortCodeRVal.get("BUSINESS_NAME");

                        if (!status.equals("SUCCESS") && !status.equals("SHORT_CODE_NOT_FOUND")) {
                            String strResponse = strHeader + "\n{Error validating Business Short Code. Please retry}\nEnter Business Short Code:";
                            theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE, USSDConstants.USSDInputType.STRING, "NO");
                        } else {
                            String strResponse = "Confirm " + strHeader + "\n" +
                                    "Business Name: " + strBusinessName + "\n" +
                                    "Short Code: " + strBusinessShortCode + "\n" +
                                    "Amount: KES " + strAmount + "\n";

                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmationWithoutHome(theUSSDRequest, AppConstants.USSDDataType.BUY_GOODS_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                            String strDepositMinimum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMinimum();
                            String strDepositMaximum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMaximum();

                            double dblDepositMinimum = Double.parseDouble(strDepositMinimum);
                            double dblDepositMaximum = Double.parseDouble(strDepositMaximum);

                            double dblAmountEntered = Double.parseDouble(strAmount);

                            if (dblAmountEntered < dblDepositMinimum) {
                                strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strDepositMinimum, "#,###.##") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                            if (dblAmountEntered > dblDepositMaximum) {
                                strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strDepositMaximum, "#,###.##") + "}\nEnter amount:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                            }
                        }
                    } else {
                        String strResponse = strHeader + "\n{Please enter a valid amount}\nEnter amount:";
                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.DEPOSIT_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                    }
                    break;
                }

                case "CONFIRMATION": {
                    String strConfirmation = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.BUY_GOODS_CONFIRMATION.name());

                    switch (strConfirmation) {
                        case "YES": {
                            String strResponse = "";
                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.BUY_GOODS_AMOUNT.name());

                            HashMap<String, String> verifyBusinessShortCodeRVal = theUSSDAPI.verifyBusinessShortCode(theUSSDRequest);
                            String status = verifyBusinessShortCodeRVal.get("STATUS");
                            String strBusinessShortCode = verifyBusinessShortCodeRVal.get("BUSINESS_SHORT_CODE");
                            String strBusinessName = verifyBusinessShortCodeRVal.get("BUSINESS_NAME");

                            if (!status.equals("SUCCESS") && !status.equals("SHORT_CODE_NOT_FOUND")) {
                                strResponse = strHeader + "\n{Error validating Business Short Code. Please retry}\nEnter Business Short Code:";
                                theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE, USSDConstants.USSDInputType.STRING, "NO");
                            } else {
                                if (theUSSDRequest.getUSSDProviderCode() == AppConstants.USSDProvider.SAFARICOM.getValue()) {

                                    strResponse = "You will be prompted by M-PESA for payment\nPaybill no: " + strSender + "\n" + "A/C: " + strBusinessShortCode + "\n" + "Amount: KES " + strAmount + "\n";

                                    String strOriginatorID = MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());
                                    String strBeneficiaryMobileNo = Long.toString(theUSSDRequest.getUSSDMobileNo());
                                    double dblAmount = Utils.stringToDouble(strAmount);
                                    String strTraceID = theUSSDRequest.getUSSDTraceID();

                                    Thread worker = new Thread(() -> {
                                        PESAAPI thePESAAPI = new PESAAPI();
                                        thePESAAPI.pesa_C2B_Request(
                                                strBeneficiaryMobileNo,
                                                strBeneficiaryMobileNo,
                                                strTraceID,
                                                "USSD",
                                                strBeneficiaryMobileNo,
                                                strBeneficiaryMobileNo,
                                                "USSD",
                                                strOriginatorID,
                                                strBeneficiaryMobileNo,
                                                strBeneficiaryMobileNo,
                                                strBusinessShortCode,
                                                dblAmount,
                                                "BUY_GOODS");

                                    });
                                    worker.start();
                                } else {
                                    strResponse = "Use the details below to pay via M-PESA\nPaybill no: " + strSender + "\n" + "A/C: " + strBusinessShortCode + "\n" + "Amount: KES " + strAmount + "\n";
                                }

                                //End USSD.
                                theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");

                            /*Cont USSD
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption  = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithHomeAndExit(theUSSDRequest, AppConstants.USSDDataType.DEPOSIT_END, "NO",theArrayListUSSDSelectOption);
                              */
                            }
                            break;
                        }
                        case "NO": {
                            String strResponse = "Dear member, your " + strHeader + " request NOT confirmed. " + strHeader + " request NOT COMPLETED.";
                            ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                            USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                            theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                            break;
                        }
                        default: {
                            String strAmount = theUSSDRequest.getUSSDData().get(AppConstants.USSDDataType.BUY_GOODS_AMOUNT.name());
                            if (strAmount.matches("^[1-9][0-9]*$")) {
                                strAmount = Utils.formatDouble(strAmount, "#,###");

                                HashMap<String, String> verifyBusinessShortCodeRVal = theUSSDAPI.verifyBusinessShortCode(theUSSDRequest);
                                String status = verifyBusinessShortCodeRVal.get("STATUS");
                                String strBusinessShortCode = verifyBusinessShortCodeRVal.get("BUSINESS_SHORT_CODE");
                                String strBusinessName = verifyBusinessShortCodeRVal.get("BUSINESS_NAME");

                                if (!status.equals("SUCCESS") && !status.equals("SHORT_CODE_NOT_FOUND")) {
                                    String strResponse = strHeader + "\n{Error validating Business Short Code. Please retry}\nEnter Business Short Code:";
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_BUSINESS_SHORT_CODE, USSDConstants.USSDInputType.STRING, "NO");
                                } else {
                                    String strResponse = "Confirm " + strHeader + "\n" +
                                            "Business Name: " + strBusinessName + "\n" +
                                            "Short Code: " + strBusinessShortCode + "\n" +
                                            "Amount: KES " + strAmount + "\n";

                                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                                    theUSSDResponse = theAppMenus.displayMenu_GeneralSelectWithConfirmationWithoutHome(theUSSDRequest, AppConstants.USSDDataType.BUY_GOODS_CONFIRMATION, "NO", theArrayListUSSDSelectOption);

                                    String strDepositMinimum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMinimum();
                                    String strDepositMaximum = theUSSDAPI.getAmountLimitCustomParameters(MBankingConstants.ApplicationType.USSD, USSDAPIConstants.USSD_PARAM_TYPE.DEPOSIT).getMaximum();

                                    double dblDepositMinimum = Double.parseDouble(strDepositMinimum);
                                    double dblDepositMaximum = Double.parseDouble(strDepositMaximum);

                                    double dblAmountEntered = Double.parseDouble(strAmount);

                                    if (dblAmountEntered < dblDepositMinimum) {
                                        strResponse = strHeader + "\n{Minimum amount allowed is KES " + Utils.formatDouble(strDepositMinimum, "#,###.##") + "}\nEnter amount:";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                    }
                                    if (dblAmountEntered > dblDepositMaximum) {
                                        strResponse = strHeader + "\n{Maximum amount allowed is KES " + Utils.formatDouble(strDepositMaximum, "#,###.##") + "}\nEnter amount:";
                                        theUSSDResponse = theAppMenus.displayMenu_GeneralInput(theUSSDRequest, strResponse, AppConstants.USSDDataType.BUY_GOODS_AMOUNT, USSDConstants.USSDInputType.STRING, "NO");
                                    }
                                }
                            }
                        }
                        break;
                    }
                    break;
                }
                default: {
                    System.err.println("theAppMenus.displayMenu_AccountsDeposit() UNKNOWN PARAM ERROR : theParam = " + theParam);

                    String strResponse = strHeader + "\n{Sorry, an error has occurred while processing your request}";
                    ArrayList<USSDResponseSELECTOption> theArrayListUSSDSelectOption = new ArrayList<USSDResponseSELECTOption>();
                    USSDResponseSELECTOption.setUSSDSelectOption(theArrayListUSSDSelectOption, strResponse);
                    theUSSDResponse = theAppMenus.displayMenu_GeneralDisplay(theUSSDRequest, strResponse, "NO");
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("theAppMenus.displayMenu_BalanceEnquiry() ERROR : " + e.getMessage());
        } finally {
            theUSSDAPI = null;
            theAppMenus = null;
        }
        return theUSSDResponse;
    }
}
