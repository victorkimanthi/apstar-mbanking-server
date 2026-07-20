package ke.skyworld.mbanking.cbs;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.Misc;
import ke.co.skyworld.smp.utility_items.counters.Watch;
import ke.co.skyworld.smp.utility_items.data_formatting.Converter;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.mbanking.cbs.dynamics.NavisionConnectionManager;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.w3c.dom.Document;

import javax.net.ssl.*;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.UUID;


public class ApStarCBS {

    static {
        try {
            disableSSLVerification();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void disableSSLVerification() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    public static TransactionWrapper<FlexicoreHashMap> getMemberDetails(String theIdentifierType, String theIdentifier) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_MEMBER_DETAILS";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        TransactionWrapper<FlexicoreHashMap> resultWrapper = sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        if (resultWrapper.hasErrors()) {
            return resultWrapper;
        }

        FlexicoreHashMap resultMap = resultWrapper.getSingleRecord();

        if (resultMap == null) {
            resultWrapper.setHasErrors(true);
            resultWrapper.addError("Failed to fetch customer details.");
            resultWrapper.addMessage("Result from CBS could not be parsed.");
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
            return resultWrapper;
        }

        String requestStatus = resultMap.getStringValue("request_status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.addError("Customer not found.");
            resultWrapper.addMessage("Customer with identifier type '" + theIdentifierType + "' and identifier '" + theIdentifier + "' not found in CBS.");
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

        FlexicoreHashMap customerDetails = resultMap.getFlexicoreHashMap("response_payload");

        if (customerDetails == null || customerDetails.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.addError("Customer not found.");
            resultWrapper.addMessage("Customer with identifier type '" + theIdentifierType + "' and identifier '" + theIdentifier + "' not found in CBS.");
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

        customerDetails.putValue("identity_document_reference_xml", null);
        resultWrapper.setData(customerDetails);
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreArrayList> getMemberDepositAccounts(String theIdentifierType, String theIdentifier, String theAccountType) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_DEPOSIT_ACCOUNTS";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_type", theAccountType)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        TransactionWrapper<FlexicoreArrayList> resultWrapper = new TransactionWrapper<>();

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        if (apiResponseWrapper.hasErrors()) {
            resultWrapper.copyFrom(apiResponseWrapper);
            return resultWrapper;
        }

        FlexicoreHashMap apiResponseMap = apiResponseWrapper.getSingleRecord();

        String requestStatus = apiResponseMap.getStringValue("request_status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("response_payload");

        if (customerAccounts == null || customerAccounts.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }
        resultWrapper.setData(customerAccounts);
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> getAccountDetails(String theIdentifierType, String theIdentifier, String theAccountNumber) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "VALIDATE_ACCOUNT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_number", theAccountNumber)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }

    public static TransactionWrapper<FlexicoreHashMap> getDepositAccountBalance(String theIdentifierType, String theIdentifier, String theAccountNumber, String strRef) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "BALANCE_ENQUIRY";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_number", theAccountNumber)
                        .putValue("source_application", "USSD")
                        .putValue("source_reference",strRef)
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }


    public static TransactionWrapper<FlexicoreHashMap> getAccountMiniStatement(String theIdentifierType, String theIdentifier,
                                                                               String theAccountNumber, String theNumberOfTransactions) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "ACCOUNT_MINI_STATEMENT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_number", theAccountNumber)
                        .putValue("number_of_transactions", theNumberOfTransactions)
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }

    public static TransactionWrapper<FlexicoreHashMap> getAccountFullStatement(String theIdentifierType, String theIdentifier,
                                                                               String theAccountNumber,
                                                                               String theNumberOfTransactions,
                                                                               String theStartDate,
                                                                               String theEndDate) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "ACCOUNT_FULL_STATEMENT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_number", theAccountNumber)
                        .putValue("number_of_transactions", theNumberOfTransactions)
                        .putValue("start_date", theStartDate.trim())
                        .putValue("end_date", theEndDate.trim())
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }

    public static TransactionWrapper<FlexicoreHashMap> withdrawal(String theIdentifierType,
                                                                  String theIdentifier,
                                                                  String theOriginatorId,
                                                                  String theProductId,
                                                                  String thePesaType,
                                                                  String theAction,
                                                                  String theCommand,
                                                                  FlexicoreHashMap theInitiatorDetailsMap,
                                                                  FlexicoreHashMap theSourceDetailsMap,
                                                                  FlexicoreHashMap theSenderDetailsMap,
                                                                  FlexicoreHashMap theReceiverDetailsMap,
                                                                  FlexicoreHashMap theBeneficiaryDetailsMap,
                                                                  double theAmount,
                                                                  String theCategory,
                                                                  String theTransactionDescription,
                                                                  String theSourceReference,
                                                                  String theRequestApplication,
                                                                  String theSourceApplication,
                                                                  String theWithdrawalType) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "WITHDRAWAL";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", theOriginatorId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id", theOriginatorId)
                        .putValue("product_id", theProductId)
                        .putValue("pesa_type", thePesaType)
                        .putValue("action", theAction)
                        .putValue("command", theCommand)
                        .putValue("transaction_initiator_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theInitiatorDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", getTheIdentifier(theInitiatorDetailsMap.getStringValueOrIfNull("identifier", "")))
                                .putValue("account", getTheIdentifier(theInitiatorDetailsMap.getStringValueOrIfNull("account", "")))
                                .putValue("name", theInitiatorDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theInitiatorDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theInitiatorDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_source_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theSourceDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", theSourceDetailsMap.getStringValueOrIfNull("identifier", ""))
                                .putValue("account", theSourceDetailsMap.getStringValueOrIfNull("account", ""))
                                .putValue("name", theSourceDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theSourceDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theSourceDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_sender_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theSenderDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", theSenderDetailsMap.getStringValueOrIfNull("identifier", ""))
                                .putValue("account", theSenderDetailsMap.getStringValueOrIfNull("account", ""))
                                .putValue("name", theSenderDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theSenderDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theSenderDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_receiver_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theReceiverDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", getTheIdentifier(theReceiverDetailsMap.getStringValueOrIfNull("identifier", "")))
                                .putValue("account", getTheIdentifier(theReceiverDetailsMap.getStringValueOrIfNull("account", "")))
                                .putValue("name", theReceiverDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theReceiverDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theReceiverDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_beneficiary_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theBeneficiaryDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", getTheIdentifier(theBeneficiaryDetailsMap.getStringValueOrIfNull("identifier", "")))
                                .putValue("account", getTheIdentifier(theBeneficiaryDetailsMap.getStringValueOrIfNull("account", "")))
                                .putValue("name", theBeneficiaryDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theBeneficiaryDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theBeneficiaryDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("amount", theAmount)
                        .putValue("category", theCategory)
                        .putValue("transaction_description", theTransactionDescription)
                        .putValue("source_reference", theSourceReference)
                        .putValue("request_application", theRequestApplication)
                        .putValue("source_application", theSourceApplication)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction + " - " + theWithdrawalType);
    }

    public static TransactionWrapper<FlexicoreHashMap> withdrawalResult(String theIdentifierType,
                                                                        String theIdentifier,
                                                                        String theOriginatorId,
                                                                        String theTransactionStatus,
                                                                        String theTransactionStatusDescription,
                                                                        String theBeneficiaryIdentifierType,
                                                                        String theBeneficiaryIdentifier,
                                                                        String theBeneficiaryName,
                                                                        String theBeneficiaryOtherDetails,
                                                                        String theBeneficiaryReference,
                                                                        String theTransactionDateTime) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "WITHDRAWAL_RESULT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", theOriginatorId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id", theOriginatorId)
                        .putValue("transaction_status", theTransactionStatus)
                        .putValue("transaction_status_description", theTransactionStatusDescription)
                        .putValue("transaction_beneficiary_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theBeneficiaryIdentifierType)
                                .putValue("identifier", theBeneficiaryIdentifier)
                                .putValue("name", theBeneficiaryName)
                                .putValue("other_details", theBeneficiaryOtherDetails)
                        )
                        .putValue("beneficiary_reference", theBeneficiaryReference)
                        .putValue("transaction_date_time", theTransactionDateTime)
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction + "-" + theTransactionStatus);
    }


    public static TransactionWrapper<FlexicoreHashMap> cashDeposit(String theIdentifierType,
                                                                   String theIdentifier,
                                                                   String theOriginatorId,
                                                                   String theProductId,
                                                                   String thePesaType,
                                                                   String theAction,
                                                                   String theCommand,
                                                                   String theSensitivity,
                                                                   String theCharge,
                                                                   FlexicoreHashMap theInitiatorDetailsMap,
                                                                   FlexicoreHashMap theSourceDetailsMap,
                                                                   FlexicoreHashMap theSenderDetailsMap,
                                                                   FlexicoreHashMap theReceiverDetailsMap,
                                                                   FlexicoreHashMap theBeneficiaryDetailsMap,
                                                                   double theAmount,
                                                                   String theCategory,
                                                                   String theTransactionDescription,
                                                                   String theSourceReference,
                                                                   String theRequestApplication,
                                                                   String theSourceApplication) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "DEPOSIT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id",UUID.randomUUID().toString().toUpperCase())

                        .putValue("product_id", theProductId)
                        .putValue("pesa_type", thePesaType)
                        .putValue("action", theAction)
                        .putValue("command", theCommand)
                        .putValue("sensitivity", theSensitivity)
                        .putValue("charge", theCharge)
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("transaction_initiator_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theInitiatorDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", getTheIdentifier(theInitiatorDetailsMap.getStringValueOrIfNull("identifier", "")))
                                .putValue("account", getTheIdentifier(theInitiatorDetailsMap.getStringValueOrIfNull("account", "")))
                                .putValue("name", theInitiatorDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theInitiatorDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theInitiatorDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_source_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theSourceDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", theSourceDetailsMap.getStringValueOrIfNull("identifier", ""))
                                .putValue("account", theSourceDetailsMap.getStringValueOrIfNull("account", ""))
                                .putValue("name", theSourceDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theSourceDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theSourceDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_sender_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theSenderDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", theSenderDetailsMap.getStringValueOrIfNull("identifier", ""))
                                .putValue("account", theSenderDetailsMap.getStringValueOrIfNull("account", ""))
                                .putValue("name", theSenderDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theSenderDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theSenderDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_receiver_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theReceiverDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", theReceiverDetailsMap.getStringValueOrIfNull("identifier", ""))
                                .putValue("account", theReceiverDetailsMap.getStringValueOrIfNull("account", ""))
                                .putValue("name", theReceiverDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theReceiverDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theReceiverDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("transaction_beneficiary_details", new FlexicoreHashMap()
                                .putValue("identifier_type", theBeneficiaryDetailsMap.getStringValueOrIfNull("identifier_type", ""))
                                .putValue("identifier", theBeneficiaryDetailsMap.getStringValueOrIfNull("identifier", ""))
                                .putValue("account", theBeneficiaryDetailsMap.getStringValueOrIfNull("account", ""))
                                .putValue("name", theBeneficiaryDetailsMap.getStringValueOrIfNull("name", ""))
                                .putValue("reference", theBeneficiaryDetailsMap.getStringValueOrIfNull("reference", ""))
                                .putValue("other_details", theBeneficiaryDetailsMap.getStringValueOrIfNull("other_details", ""))
                        )
                        .putValue("amount", theAmount)
                        .putValue("category", theCategory)
                        .putValue("transaction_description", theTransactionDescription + " - " +theBeneficiaryDetailsMap.getStringValueOrIfNull("name", "") )
                        .putValue("source_reference", theSourceReference)
                        .putValue("request_application", theRequestApplication)
                        .putValue("source_application", theSourceApplication)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }

    public static TransactionWrapper<FlexicoreHashMap> internalFundsTransfer(
            String theIdentifierType,
            String theIdentifier,
            String theOriginatorId,
            String theSourceAccount,
            String theDestinationAccount,
            double theAmount,
            String theTransactionDescription,
            String theSourceReference,
            String theRequestApplication,
            String theSourceApplication) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "IFT_ACCOUNT_TO_ACCOUNT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id", theOriginatorId)
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("source_account", theSourceAccount)
                        .putValue("destination_account", theDestinationAccount)
                        .putValue("amount", theAmount)
                        .putValue("transaction_description", theTransactionDescription)
                        .putValue("source_reference", theSourceReference.substring(0,10))
                        .putValue("request_application", theRequestApplication)
                        .putValue("source_application", theSourceApplication)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }


    public static TransactionWrapper<FlexicoreHashMap> loanPaymentViaSavings(
            String theIdentifierType,
            String theIdentifier,
            String theOriginatorId,
            String theSourceAccount,
            String theDestinationAccount,
            double theAmount,
            String theTransactionDescription,
            String theSourceReference,
            String theRequestApplication,
            String theSourceApplication) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "IFT_LOAN_REPAYMENT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id", theOriginatorId)
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("source_account", theSourceAccount)
                        .putValue("destination_account", theDestinationAccount)
                        .putValue("amount", theAmount)
                        .putValue("transaction_description", theTransactionDescription)
                        .putValue("source_reference", theSourceReference.substring(0,10))
                        .putValue("request_application", theRequestApplication)
                        .putValue("source_application", theSourceApplication)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }

    public static TransactionWrapper<FlexicoreHashMap> getLoanMiniStatement(String theIdentifierType, String theIdentifier,
                                                                            String theLoanSerialNumber,
                                                                            String theNumberOfTransactions) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "LOAN_MINI_STATEMENT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("loan_serial_number", theLoanSerialNumber)
                        .putValue("number_of_transactions", theNumberOfTransactions)
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }

    public static TransactionWrapper<FlexicoreHashMap> getLoanFullStatement(String theIdentifierType,
                                                                            String theIdentifier,
                                                                            String theLoanSerialNumber,
                                                                            String theNumberOfTransactions,
                                                                            String theStartDate,
                                                                            String theEndDate) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "LOAN_FULL_STATEMENT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("loan_serial_number", theLoanSerialNumber)
                        .putValue("number_of_transactions", theNumberOfTransactions)
                        .putValue("start_date", theStartDate)
                        .putValue("end_date", theEndDate)
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }


    public static TransactionWrapper<FlexicoreHashMap> getCharges(
            String theIdentifierType,
            String theIdentifier,
            String theAccountNumber,
            String theChargeAction,
            double theAmount) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_CHARGES";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_number", theAccountNumber)
                        .putValue("charge_action", theChargeAction)
                        .putValue("amount", theAmount)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        /*TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        resultWrapper.setData(new FlexicoreHashMap()
                .putValue("api_request_id", strRequestId)
                .putValue("request_status", "SUCCESS")
                .putValue("response_payload", new FlexicoreHashMap()
                        .putValue("charge_amount", 21.00)
                )
        );

        return resultWrapper;*/
    }



    public static TransactionWrapper<FlexicoreHashMap> getDividendPayslipReport(
            String theIdentifierType,
            String theIdentifier,
            String strFromDate,
            String strEmail
    ) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_DIVIDEND_PAYSLIP";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("date", strFromDate)
                        .putValue("email", strEmail)

                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

    }


    public static TransactionWrapper<FlexicoreHashMap> checkFOSAAccountStatus(
            String theIdentifierType,
            String theIdentifier
            ) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "CHECK_FOSA_ACCOUNT_STATUS";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

    }


    public static TransactionWrapper<FlexicoreHashMap> activatesFOSAAccount(
            String theIdentifierType,
            String theIdentifier
            ) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "ACTIVATE_ACCOUNT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("account_number", "")
                        .putValue("account_type", "SAVINGS")
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0, 20))
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

    }

    public static TransactionWrapper<FlexicoreHashMap> checkLoanLimit(String theIdentifierType,
                                                                      String theIdentifier,
                                                                      String theLoanTypeId,String strLoanAmount) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "CHECK_LOAN_LIMIT";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id", strRequestId)
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("loan_type_id", theLoanTypeId)
                        .putValue("loan_duration", "1")
                        .putValue("loan_amount", strLoanAmount)
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }


    public static TransactionWrapper<FlexicoreArrayList> getMemberLoansInService(String theIdentifierType, String theIdentifier) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_LOANS_IN_SERVICE";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        TransactionWrapper<FlexicoreArrayList> resultWrapper = new TransactionWrapper<>();

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        if (apiResponseWrapper.hasErrors()) {
            resultWrapper.copyFrom(apiResponseWrapper);
            return resultWrapper;
        }

        FlexicoreHashMap apiResponseMap = apiResponseWrapper.getSingleRecord();

        String requestStatus = apiResponseMap.getStringValue("request_status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("response_payload");

        if (customerAccounts == null || customerAccounts.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }
        resultWrapper.setData(customerAccounts);
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreHashMap> loanApplication(String theIdentifierType,
                                                                       String theIdentifier,
                                                                       String theLoanTypeId,
                                                                       double theAmount,
                                                                       String theLoanDuration,
                                                                       String theMerchantId,
                                                                       String theProductId,
                                                                       String theSourceReference,
                                                                       String theRequestApplication,
                                                                       String theTransactionDateTime
    ) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "LOAN_APPLICATION";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("originator_id", theSourceReference)
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("loan_type_id", theLoanTypeId)
                        .putValue("amount", theAmount)
                        .putValue("loan_duration", theLoanDuration)
                        .putValue("merchant_id", theMerchantId)
                        .putValue("product_id", theProductId)
                        .putValue("source_reference",theSourceReference.toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("request_application", theRequestApplication)
                        .putValue("transaction_date_time", theTransactionDateTime)
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }


    public static TransactionWrapper<FlexicoreHashMap> getLoanAccountBalance(String theIdentifierType, String theIdentifier, String theAccountNumber) {

        String strRequestId = UUID.randomUUID().toString();

        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "LOAN_BALANCE_ENQUIRY";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("loan_serial_number", theAccountNumber)
                        .putValue("source_reference",UUID.randomUUID().toString().substring(0,10))
                        .putValue("originator_id",UUID.randomUUID().toString())
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        return sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);
    }


    public static TransactionWrapper<FlexicoreArrayList> getLoanTypes(String theIdentifierType, String theIdentifier) {

        String strRequestId = UUID.randomUUID().toString();


        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_LOAN_TYPES";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("transaction_date_time", DateTime.getCurrentDateTime("yyyy-MM-dd HH:mm:ss"))
                );

        TransactionWrapper<FlexicoreArrayList> resultWrapper = new TransactionWrapper<>();

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        if (apiResponseWrapper.hasErrors()) {
            resultWrapper.copyFrom(apiResponseWrapper);
            return resultWrapper;
        }

        FlexicoreHashMap apiResponseMap = apiResponseWrapper.getSingleRecord();

        String requestStatus = apiResponseMap.getStringValue("request_status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("response_payload");

        if (customerAccounts == null || customerAccounts.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }
        resultWrapper.setData(customerAccounts);
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreArrayList> getMerchants(String theIdentifierType, String theIdentifier) {

        String strRequestId = UUID.randomUUID().toString();


        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_ALL_MERCHANTS";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                );

        TransactionWrapper<FlexicoreArrayList> resultWrapper = new TransactionWrapper<>();

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        if (apiResponseWrapper.hasErrors()) {
            resultWrapper.copyFrom(apiResponseWrapper);
            return resultWrapper;
        }

        FlexicoreHashMap apiResponseMap = apiResponseWrapper.getSingleRecord();

//        String requestStatus = apiResponseMap.getStringValue("request_status");
        String requestStatus = apiResponseMap.getStringValue("status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

//        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("response_payload");
        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("data");

        if (customerAccounts == null || customerAccounts.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }
        resultWrapper.setData(customerAccounts);
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreArrayList> getMerchantProducts(String theIdentifierType, String theIdentifier,String strMerchantId) {

        String strRequestId = UUID.randomUUID().toString();


        theIdentifier = getTheIdentifier(theIdentifier);

        String strAction = "GET_MERCHANT_PRODUCTS";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("identifier_type", theIdentifierType)
                        .putValue("identifier", theIdentifier)
                        .putValue("merchant_id",strMerchantId)
                );

        TransactionWrapper<FlexicoreArrayList> resultWrapper = new TransactionWrapper<>();

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = sendSoapRequest(theIdentifierType, theIdentifier, strRequestId, Converter.toJson(requestBody), strAction);

        if (apiResponseWrapper.hasErrors()) {
            resultWrapper.copyFrom(apiResponseWrapper);
            return resultWrapper;
        }

        FlexicoreHashMap apiResponseMap = apiResponseWrapper.getSingleRecord();

//        String requestStatus = apiResponseMap.getStringValue("request_status");
        String requestStatus = apiResponseMap.getStringValue("status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

//        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("response_payload");
        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("data");

        if (customerAccounts == null || customerAccounts.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }
        resultWrapper.setData(customerAccounts);
        return resultWrapper;
    }

    public static TransactionWrapper<FlexicoreArrayList> callBC365Service(String theServiceName) {

        String strRequestId = UUID.randomUUID().toString();
        String strAction = "CALL_SERVICE";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("service_id", theServiceName)
                );

        TransactionWrapper<FlexicoreArrayList> resultWrapper = new TransactionWrapper<>();

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = sendSoapRequest(theServiceName, theServiceName, strRequestId, Converter.toJson(requestBody), strAction);

        if (apiResponseWrapper.hasErrors()) {
            resultWrapper.copyFrom(apiResponseWrapper);
            return resultWrapper;
        }

        FlexicoreHashMap apiResponseMap = apiResponseWrapper.getSingleRecord();

        String requestStatus = apiResponseMap.getStringValue("request_status");

        if (!requestStatus.equalsIgnoreCase("SUCCESS")) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }

        FlexicoreArrayList customerAccounts = apiResponseMap.getFlexicoreArrayList("response_payload");

        if (customerAccounts == null || customerAccounts.isEmpty()) {
            resultWrapper.setHasErrors(true);
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_NOT_FOUND);
            return resultWrapper;
        }
        resultWrapper.setData(customerAccounts);
        return resultWrapper;
    }

    /*private static TransactionWrapper<FlexicoreHashMap> makeAPICall(String requestBody, String functionInvoked) {
        String strCredentials = ApStarCBSParams.getTheUsername() + ":" + ApStarCBSParams.getThePassword();
        strCredentials = HashUtils.base64Encode(strCredentials);

        LinkedHashMap<String, String> hmHeaders = new LinkedHashMap<>();
        hmHeaders.put("Authorization", "Basic " + strCredentials);
        hmHeaders.put("Content-Type", "application/json");

        TransactionWrapper<FlexicoreHashMap> apiResponseWrapper = new TransactionWrapper<>();

        HTTPResponse httpResponse = HttpClient.httpPOST(ApStarCBSParams.getTheCBS_URL(), hmHeaders, null, requestBody, functionInvoked);
        System.out.println("----------------------------------------------------------");
        System.out.println(httpResponse);

        if (httpResponse.getResponseCode() != HttpsURLConnection.HTTP_OK) {
            apiResponseWrapper.setHasErrors(true);
            apiResponseWrapper.addError("Request failed. Status Code: " + httpResponse.getResponseCode());
            apiResponseWrapper.addMessage("Unsuccessful Request. Error: " + httpResponse.getResponseMessage());
            apiResponseWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
            return apiResponseWrapper;
        }

        FlexicoreHashMap apiResponseMap = httpResponse.getResponseBodyAsMap();

        if (apiResponseMap == null) {
            apiResponseWrapper.setHasErrors(true);
            apiResponseWrapper.addError("Failed to parse CBS Response.");
            apiResponseWrapper.addMessage("Result from CBS could not be parsed.");
            apiResponseWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
            return apiResponseWrapper;
        }

        apiResponseWrapper.setData(apiResponseMap);
        return apiResponseWrapper;
    }*/

    public static TransactionWrapper<FlexicoreHashMap> sendSoapRequest(String theIdentifierType, String theIdentifier, String theRequestUUID, String theRequestJSON, String theAction) {

        Watch watch = new Watch();
        watch.start();

/*        String requestXML = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:dyn="urn:microsoft-dynamics-schemas/codeunit/SkyMBankingAPI">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <dyn:HandleRequest>
                            <dyn:request/>
                        </dyn:HandleRequest>
                    </soapenv:Body>
                </soapenv:Envelope>""";*/


        String requestXML = """
                    <Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
                    <Body>
                        <HandleRequest xmlns="urn:microsoft-dynamics-schemas/codeunit/NewSkyMbanking">
                            <request/>
                        </HandleRequest>
                    </Body>
                </Envelope>""";

        Document document = XmlUtils.parseXml(requestXML);

        FlexicoreHashMap updateMap = new FlexicoreHashMap();
        updateMap.putValue("/Envelope/Body/HandleRequest/request", theRequestJSON);

        String theRequestBody = XmlUtils.updateXMLTags(document, updateMap);

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            String strURL = ApStarCBSParams.getSOAPURL();
            System.out.println("************CBS CREDENTIALS************");
            System.out.println("URL: " + strURL);
            System.out.println("**************************************");


            URL url = new URL(strURL);

            String strHost = extractHostFromURL(url);
            int strPort = extractPortFromURL(url);

            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();

            /*System.out.println("Win auth available: " + WinHttpClients.isWinAuthAvailable());
            if (WinHttpClients.isWinAuthAvailable()) {
                ClassicHttpRequest request = new HttpGet("http://localhost:8080/ssotestwebapp/webapp");
                CloseableHttpClient client = WinHttpClients.createDefault();
                HttpClientContext context = HttpClientContext.create();

                Collection<String> targetPreferredAuthSchemes = Collections.unmodifiableList(
                        Arrays.asList(StandardAuthScheme.NTLM, StandardAuthScheme.KERBEROS, StandardAuthScheme.SPNEGO,
                                StandardAuthScheme.BEARER, StandardAuthScheme.DIGEST, StandardAuthScheme.BASIC));
                RequestConfig config = RequestConfig.custom().setTargetPreferredAuthSchemes(targetPreferredAuthSchemes)
                        .build();
                context.setRequestConfig(config);

                client.execute(request, context, response -> {
                    System.out.println("----------------------------------------");
                    System.out.println(new StatusLine(response));

                    HttpEntity entity = response.getEntity();
                    String s = EntityUtils.toString(entity);
                    System.out.println(s);
                    EntityUtils.consume(response.getEntity());
                    return null;
                });
            }*/

            credentialsProvider.setCredentials(
                    new AuthScope(new AuthScope(strHost, strPort)),
                    new NTCredentials(ApStarCBSParams.getUser(),
                            ApStarCBSParams.getPassword().toCharArray(),
                            ".",
                            ApStarCBSParams.getDomain()));

            if (ApStarCBSParams.isLogRequestEnabled()) {
                if (!theAction.equalsIgnoreCase("CALL_SERVICE")) {
                    System.out.println("\n----------------| REQUEST |----------------\n" +
                            "Request Log Ref : " + theRequestUUID + "\n" +
                            "Requester Type  : " + theIdentifierType + "\n" +
                            "Requester       : " + theIdentifier + "\n" +
                            "Request Action  : " + theAction + "\n" +
                            "Request Body    : " + "\n" +
                            "-------------------------------------------\n" + theRequestBody + "\n");
                }
            } else {

                if (!theAction.equalsIgnoreCase("CALL_SERVICE")) {
                    System.out.println("\n----------------| REQUEST |----------------\n" +
                            "Request Log Ref : " + theRequestUUID + "\n" +
                            "Requester Type  : " + theIdentifierType + "\n" +
                            "Requester       : " + theIdentifier + "\n" +
                            "Request Action  : " + theAction + "\n" +
                            "Request Body    : " + "\n" +
                            "-------------------------------------------\n");
                }
            }


            CloseableHttpClient closeableHttpClient = NavisionConnectionManager.getHttpClient();


            //sample response
                    /*


                  <Soap:Envelope xmlns:Soap="http://schemas.xmlsoap.org/soap/envelope/">
    <Soap:Body>
        <GetTransactionCharges_Result xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyMobile">
            <return_value>60</return_value>
        </GetTransactionCharges_Result>
    </Soap:Body>
</Soap:Envelope>

                     */


            //try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider).build()) {


            if (closeableHttpClient != null) {

                HttpPost httpPost = new HttpPost(strURL);
                httpPost.setEntity(new StringEntity(theRequestBody));
                httpPost.setHeader("Content-type", "application/xml");
                httpPost.setHeader("SOAPAction", ApStarCBSParams.getSOAPAction());

                return closeableHttpClient.execute(httpPost, new SOAPResponseHandler(theRequestUUID, theIdentifierType, theIdentifier, theAction, watch));

            }

        } catch (Exception e) {
            resultWrapper.copyFrom(Misc.getTransactionWrapperStackTrace(e));
            resultWrapper.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

            return resultWrapper;
        }
        return resultWrapper;
    }

        public static String extractHostFromURL (URL url){
            return url.getHost();
        }

        // Function to extract the port from a URL
        public static int extractPortFromURL (URL url){
            int port = url.getPort();

            if (port == -1) {
                if (url.getProtocol().equals("http")) {
                    port = 80;
                } else if (url.getProtocol().equals("https")) {
                    port = 443;
                }
            }

            return port;
        }

        //TODO: REMOVE IN PRODUCTION
        public static String getTheIdentifier (String theIdentifier){
            if (theIdentifier.equalsIgnoreCase("")) {
                //theIdentifier = "254721565634";
            }


            return theIdentifier;
        }
    }