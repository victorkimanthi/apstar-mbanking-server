package ke.skyworld.mbanking.mappapi;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.skyworld.lib.mbanking.mapp.MAPPConstants;
import ke.skyworld.lib.mbanking.mapp.MAPPRequest;
import ke.skyworld.lib.mbanking.mapp.MAPPResponse;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.ussdapplication.AppConstants;
import org.w3c.dom.Node;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringWriter;

public class MAPPAPIProcessor {
    public MAPPResponse processMAPPAPI(MAPPRequest theMAPPRequest) {

        MAPPResponse theMAPPResponse = null;

        MAPPAPI theMAPPAPI = new MAPPAPI();

        try {
            String strAction = theMAPPRequest.getAction();

            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(theMAPPRequest.getMSG()), new StreamResult(writer));
            String strMsg = writer.toString();

            System.out.println(strMsg);

            switch (strAction) {
                case "LOGIN": {
                    theMAPPResponse = theMAPPAPI.userLogin(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.LOGIN, false);
                    break;
                }
                case "PASSWORD_VERIFICATION": {
                    theMAPPResponse = theMAPPAPI.userLogin(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL, false);
                    break;
                }
                case "ACTIVATE_MOBILE_APP": {
                    theMAPPResponse = theMAPPAPI.validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.ACTIVATION);
                    break;
                }

                case "ACCEPT_TERMS_AND_CONDITIONS": {
                    theMAPPResponse = theMAPPAPI.acceptTermsAndConditions(theMAPPRequest);
                    break;
                }

                case "GENERATE_OTP": {
                    theMAPPResponse = theMAPPAPI.generateOTP(theMAPPRequest);
                    break;
                }


                case "VALIDATE_OTP": {
                    theMAPPResponse = theMAPPAPI.validateOTP(theMAPPRequest, MAPPAPIConstants.OTP_TYPE.TRANSACTIONAL);
                    break;
                }
                /* case "ACTIVATE_MOBILE_APP_WITH_KYC": {
                    theMAPPResponse = theMAPPAPI.activateMobileAppWithKYC(theMAPPRequest);
                    break;
                }*/
                case "GET_ACCOUNTS":
                case "GET_ACCOUNTS_FOR_STATEMENT": {
                    theMAPPResponse = theMAPPAPI.getBankAccounts(theMAPPRequest, MAPPAPIConstants.AccountType.ALL, strAction);
                    break;
                }


                case "GET_DIVIDEND_PAYSLIP": {
                    theMAPPResponse = theMAPPAPI.getDividendPayslip(theMAPPRequest);
                    break;
                }
                /*case "ACCOUNT_BALANCE_XML":
                {
                    theMAPPResponse = theMAPPAPI.accountBalanceXML(theMAPPRequest);
                    break;
                }*/

                case "GET_CHARGES": {
                    theMAPPResponse = theMAPPAPI.getCharges(theMAPPRequest);
                    break;
                }

                case "GET_TRANSACTION_ACCOUNTS_AND_DEPOSIT_SERVICES":
                case "GET_QR_TRANSACTION_ACCOUNTS": {
                    theMAPPResponse = theMAPPAPI.getBankAccounts(theMAPPRequest, MAPPAPIConstants.AccountType.DEPOSIT, strAction);
                    break;
                }

                case "GET_ATM_CARDS": {
                    theMAPPResponse = theMAPPAPI.getATMCards(theMAPPRequest);
                    break;
                }
                case "GET_WITHDRAWAL_ACCOUNTS_AND_MOBILE_MONEY_SERVICES": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.WITHDRAWAL_VIA_MPESA);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    boolean isMandateInactive = CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.WITHDRAWAL);
                    System.out.println("Mandate is ***************: " + isMandateInactive + "******************");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Withdrawal", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.WITHDRAWAL)) {

                        System.out.println("Mandate is inactive");
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Withdrawal");
                    } else {
                        theMAPPResponse = theMAPPAPI.getWithdrawalAccountsAndMobileMoneyServices(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE);
                    }

                    break;
                }
                case "WITHDRAW_MONEY_VIA_MPESA":
                case "CASH_WITHDRAWAL":
                case "WITHDRAW_MONEY_VIA_ATM":
                case "WITHDRAW_MONEY_VIA_AGENT": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.WITHDRAWAL_VIA_MPESA);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Withdrawal", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.WITHDRAWAL)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Withdrawal");
                    } else {
                        theMAPPResponse = theMAPPAPI.mobileMoneyWithdrawal(theMAPPRequest);
                    }

                    break;
                }

                case "DEPOSIT_MONEY": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.DEPOSIT_VIA_MPESA);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Deposit", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.DEPOSIT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Deposit");
                    } else {
                        theMAPPResponse = theMAPPAPI.depositMoney(theMAPPRequest);
                    }

                    break;
                }


                case "DIVIDEND_PAYSLIP": {
                    //add payslip
                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.DEPOSIT_VIA_MPESA);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Deposit", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.DEPOSIT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Deposit");
                    } else {
                        theMAPPResponse = theMAPPAPI.getPaySlipDetails(theMAPPRequest);
                    }

                    break;
                }
                case "GET_WITHDRAWAL_ACCOUNTS": {
                    theMAPPResponse = theMAPPAPI.getWithdrawalAccounts(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE);
                    break;
                }


                case "GET_PESALINK_TRANSFER_ACCOUNTS":{
                    theMAPPResponse = theMAPPAPI.getPesalinkAccountsAndBanks(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE);
                    break;
                }

                case "GET_BANK_TRANSFER_ACCOUNTS": {

                    /*if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Bank Transfer");
                    } else {
                    }*/

                    theMAPPResponse = theMAPPAPI.getWithdrawalAccountsAndBanks(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE);


                    break;
                }
                case "GET_WITHDRAWAL_ACCOUNTS_AND_PAY_BILL_SERVICES": {

                    /*if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.UTILITY_PAYMENTS)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Pay Bill");
                    } else {
                    }*/

                    theMAPPResponse = theMAPPAPI.getWithdrawalAccountsAndPaybillServices(theMAPPRequest, MAPPAPIConstants.AccountType.WITHDRAWABLE);

                    break;
                }
                case "BUY_AIRTIME": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.BUY_AIRTIME);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Buy Airtime", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.BUY_AIRTIME)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Buy Airtime");
                    } else {
                        theMAPPResponse = theMAPPAPI.buyAirtime(theMAPPRequest);
                    }

                    break;
                }

                case "INTERNAL_FUNDS_TRANSFER": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.INTERNAL_FUNDS_TRANSFER);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Internal Funds Transfer", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.INTERNAL_TRANSFER)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Internal Funds Transfer");
                    } else {
                        theMAPPResponse = theMAPPAPI.fundsTransfer(theMAPPRequest);
                    }

                    break;
                }
                case "BANK_TRANSFER": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.BANK_TRANSFER);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");
                    try {
                        XPath configXPath = XPathFactory.newInstance().newXPath();
                        Node ndRequestMSG = theMAPPRequest.getMSG();
                        String isPesaLink = configXPath.evaluate("PESALINK", ndRequestMSG).trim();
                        if (isPesaLink.equalsIgnoreCase("TRUE")) {
                            theMAPPResponse = theMAPPAPI.bankTransferViaPESALINK(theMAPPRequest);
                            //theMAPPResponse = theMAPPAPI.bankTransferViaPesaLink(theMAPPRequest);
                        } else {

                            if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                                theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Bank Transfer", getServiceStatusDetails.getStringValue("display_message"));
                            } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                                theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Bank Transfer");
                            } else {
                                theMAPPResponse = theMAPPAPI.bankTransferViaB2B(theMAPPRequest);
                            }
                        }
                    } catch (Exception exception) {
                        if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                            theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Bank Transfer", getServiceStatusDetails.getStringValue("display_message"));
                        } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.BANK_TRANSFER)) {
                            theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Bank Transfer");
                        } else {
                            theMAPPResponse = theMAPPAPI.bankTransferViaB2B(theMAPPRequest);
                        }
                        exception.printStackTrace();
                    }


                    break;
                }
                case "PAY_BILL": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.UTILITY_PAYMENTS);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Pay Bill", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.UTILITY_PAYMENTS)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Pay Bill");
                    } else {
                        theMAPPResponse = theMAPPAPI.payBill(theMAPPRequest);
                    }

                    break;
                }
                case "LIPA_NA": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.UTILITY_PAYMENTS);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Pay Bill", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.UTILITY_PAYMENTS)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Pay Bill");
                    } else {
                        theMAPPResponse = theMAPPAPI.lipaNa(theMAPPRequest);
                    }

                    break;
                }

                case "VALIDATE_ACCOUNT_NUMBER": {
                    theMAPPResponse = theMAPPAPI.validateAccountNumber(theMAPPRequest);
                    break;
                }

                case "GET_TRANSFER_ACCOUNTS": {
                    theMAPPResponse = theMAPPAPI.getTransferAccounts(theMAPPRequest);
                    break;
                }

                case "PESALINK_BANK_TRANSFER": {
                    theMAPPResponse = theMAPPAPI.bankTransferViaPESALINK(theMAPPRequest);
                    break;
                }

                case "ACCOUNT_BALANCE": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.BALANCE_ENQUIRY);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Balance Enquiry", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.BALANCE_ENQUIRY)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Balance Enquiry");
                    } else {
                        theMAPPResponse = theMAPPAPI.accountBalanceEnquiry(theMAPPRequest);
                    }

                    break;
                }

                case "ACCOUNT_STATEMENT": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.ACCOUNT_STATEMENT);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Account Statement", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.ACCOUNT_STATEMENT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Account Statement");
                    } else {
                        theMAPPResponse = theMAPPAPI.accountStatement(theMAPPRequest);
                    }

                    break;
                }

                case "ACCOUNT_STATEMENT_BASE64": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.ACCOUNT_STATEMENT);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Account Statement", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.ACCOUNT_STATEMENT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Account Statement");
                    } else {
                        theMAPPResponse = theMAPPAPI.accountStatementBase64(theMAPPRequest);
                    }

                    break;
                }

                case "CHANGE_PASSWORD": {
                    theMAPPResponse = theMAPPAPI.changePassword(theMAPPRequest);
                    break;
                }

                case "GET_LOAN_TYPES": {
                    theMAPPResponse = theMAPPAPI.getLoanTypes(theMAPPRequest);
                    break;
                }

                case "GET_LOANS":
                case "GET_LOAN_BALANCES": {
                    theMAPPResponse = theMAPPAPI.getMemberLoans(theMAPPRequest);
                    break;
                }

                case "PAY_LOAN": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_REPAYMENT);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Repayment", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_REPAYMENT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Repayment");
                    } else {
                        theMAPPResponse = theMAPPAPI.loanRepayment(theMAPPRequest);
                    }

                    break;
                }
                case "LOAN_BALANCE": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_BALANCE);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Balance", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_BALANCE)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Balance");
                    } else {
                        theMAPPResponse = theMAPPAPI.loanBalanceEnquiry(theMAPPRequest);
                    }

                    break;
                }

                case "APPLY_LOAN": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_APPLICATION);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Application", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_APPLICATION)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Application");
                    } else {
                        theMAPPResponse = theMAPPAPI.applyLoan(theMAPPRequest);
                    }

                    break;
                }


                case "LOAN_LIMIT": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_QUALIFICATION);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Qualification", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_QUALIFICATION)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Qualification");
                    } else {
                        theMAPPResponse = theMAPPAPI.checkLoanLimit(theMAPPRequest);
                        //theMAPPResponse = theMAPPAPI.loanQualificationAmount(theMAPPRequest);
                    }
                    break;
                }

                case "GET_LOAN_QUALIFICATION_AMOUNT": {

                    //FOR APPLYING LOAN. SO MANDATE IS LOAN_APPLICAITON
                    /*if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_APPLICATION)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Application");
                    } else {

                    }*/
                    theMAPPResponse = theMAPPAPI.loanQualificationAmount(theMAPPRequest);

                    break;
                }

                case "GET_LOANS_WITH_PAYMENT_DETAILS": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_REPAYMENT);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Repayment", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_REPAYMENT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Repayment");
                    } else {
                        theMAPPResponse = theMAPPAPI.getMemberLoansWithPaymentDetails(theMAPPRequest);
                    }

                    break;
                }

                case "LOAN_STATEMENT": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_STATEMENT);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Statement", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_STATEMENT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Statement");
                    } else {
                        theMAPPResponse = theMAPPAPI.loanStatement(theMAPPRequest);
                    }

                    break;
                }

                case "LOAN_STATEMENT_BASE64": {

                    FlexicoreHashMap getServiceStatusDetails = CBSAPI.getServiceStatusDetails(AppConstants.MobileBankingChannel.MAPP, AppConstants.MobileBankingServices.LOAN_STATEMENT);
                    String strServiceStatus = getServiceStatusDetails.getStringValue("status");

                    if (!strServiceStatus.equalsIgnoreCase("ACTIVE")) {
                        theMAPPResponse = theMAPPAPI.serviceOnMaintenance(theMAPPRequest, "Loan Statement", getServiceStatusDetails.getStringValue("display_message"));
                    } else if (CBSAPI.isMandateInactive(theMAPPRequest.getUsername(), AppConstants.MobileMandates.LOAN_STATEMENT)) {
                        theMAPPResponse = theMAPPAPI.mandateNotActive(theMAPPRequest, "Loan Statement");
                    } else {
                        theMAPPResponse = theMAPPAPI.loanStatementBase64(theMAPPRequest);
                    }

                    break;
                }

                case "GET_LOANS_WITH_GUARANTORS": {
                    theMAPPResponse = theMAPPAPI.getMemberLoans(theMAPPRequest);
                    break;
                }
                case "UPDATE_LOAN_GUARANTOR_STATUS": {
                    theMAPPResponse = theMAPPAPI.updateLoanGuarantorStatus(theMAPPRequest);
                    break;
                }

                case "DISABLE_ATM_CARD": {
                    theMAPPResponse = theMAPPAPI.disableATMCard(theMAPPRequest);
                    break;
                }

                case "ENCRYPT_TEXT": {
                    theMAPPResponse = theMAPPAPI.encryptText(theMAPPRequest);
                    break;
                }
                case "DECRYPT_TEXT": {
                    theMAPPResponse = theMAPPAPI.decryptText(theMAPPRequest);
                    break;
                }
                /*
                 case "LOAN_LIMIT": {
                    theMAPPResponse = theMAPPAPI.checkLoanLimit(theMAPPRequest);
                    break;
                }
                case "APPLY_LOAN": {
                    theMAPPResponse = theMAPPAPI.applyLoan(theMAPPRequest);
                    break;
                }
                case "LOAN_STATEMENT": {
                    theMAPPResponse = theMAPPAPI.loanStatement(theMAPPRequest);
                    break;
                }*/
                case "LOAN_GUARANTORS": {
                    theMAPPResponse = theMAPPAPI.loanGuarantors(theMAPPRequest);
                    break;
                }
                case "ADD_LOAN_GUARANTORS": {
                    theMAPPResponse = theMAPPAPI.addLoanGuarantors(theMAPPRequest);
                    break;
                }
                /*case "GET_LOANS_PENDING_GUARANTORS": {
                    theMAPPResponse = theMAPPAPI.getMemberLoansWithPendingGuarantors(theMAPPRequest);
                    break;
                }*/

                case "LOANS_GUARANTEED":
                case "LOAN_GUARANTORSHIP_REQUESTS": {
                    theMAPPResponse = theMAPPAPI.loansGuaranteed(theMAPPRequest);
                    break;
                }
                case "GET_MEMBER_NAME": {
                    theMAPPResponse = theMAPPAPI.getMemberName(theMAPPRequest);
                    break;
                }
                case "ADD_UTILITY_AND_PAYBILL_ACCOUNT": {
                    theMAPPResponse = theMAPPAPI.addOrDeleteUtilityAndPaybillAccount(theMAPPRequest, "ADD");
                    break;
                }
                case "DELETE_UTILITY_AND_PAYBILL_ACCOUNT": {
                    theMAPPResponse = theMAPPAPI.addOrDeleteUtilityAndPaybillAccount(theMAPPRequest, "DELETE");
                    break;
                }

                case "REGISTER_NEW_MEMBER": {
                    theMAPPResponse = theMAPPAPI.registerMember(theMAPPRequest);
                    break;
                }
                case "GET_HOME_PAGE_ADDONS": {
                    theMAPPResponse = theMAPPAPI.getHomePageAddons(theMAPPRequest);
                    break;
                }

                case "GET_SUPPORT_CATEGORIES": {
                    theMAPPResponse = theMAPPAPI.getHelpAndSupportCategories(theMAPPRequest);
                    break;
                }

                case "GET_SUPPORT_REQUESTS_LIST": {
                    theMAPPResponse = theMAPPAPI.getSupportRequests(theMAPPRequest);
                    break;
                }

                case "GET_SUPPORT_REQUEST": {
                    theMAPPResponse = theMAPPAPI.getSingleSupportRequest(theMAPPRequest);
                    break;
                }

                case "CREATE_SUPPORT_REQUEST": {
                    theMAPPResponse = theMAPPAPI.createSupportRequestItem(theMAPPRequest);
                    break;
                }

                case "GET_ATTACHMENT": {
                    theMAPPResponse = theMAPPAPI.getAttachment(theMAPPRequest);
                    break;
                }

                case "CLOSE_SUPPORT_REQUEST": {
                    theMAPPResponse = theMAPPAPI.closeSupportRequest(theMAPPRequest);
                    break;
                }

                case "GET_OTHER_SERVICES": {
                    theMAPPResponse = theMAPPAPI.getOtherServices(theMAPPRequest);
                    break;
                }

                case "GET_MERCHANTS": {
                    theMAPPResponse = theMAPPAPI.getMerchants(theMAPPRequest);
                    break;
                }

                case "GET_MERCHANT_PRODUCTS": {
                    theMAPPResponse = theMAPPAPI.getMerchantProducts(theMAPPRequest);
                    break;
                }

                /*case "UNKNOWN": {
                    theMAPPResponse = theMAPPAPI.accountBalanceEnquiry(theMAPPRequest);
                    break;
                }
                default: {
                    theMAPPResponse = theMAPPAPI.accountBalanceEnquiry(theMAPPRequest);
                    break;
                }*/
            }
        } catch (Exception e) {
            System.err.println("MAPPAPIProcessor.processMAPPAPI() ERROR : " + e.getMessage());
        } finally {
            theMAPPAPI = null;
        }

        return theMAPPResponse;
    }
}
