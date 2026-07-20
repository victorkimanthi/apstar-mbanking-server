package ke.skyworld.mbanking.msgapi;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.Misc;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.counters.Watch;
import ke.co.skyworld.smp.utility_items.data_formatting.Converter;
import ke.co.skyworld.smp.utility_items.data_formatting.XmlUtils;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.msg.*;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.mbanking.cbs.ApStarCBS;
import ke.skyworld.mbanking.cbs.ApStarCBSParams;
import ke.skyworld.mbanking.cbs.SOAPResponseHandler;
import ke.skyworld.mbanking.mbankingapi.MBankingAPI;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.w3c.dom.Document;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.util.UUID;

public class MSGAPI {

    public static void processOnlineMOMSG(MSG theMOMSG, MOMSGOnlineResponse theMOMSGOnlineResponse) {

        String strOriginatorID = UUID.randomUUID().toString().toLowerCase();
        String strMessage = "";
        String strCategory = "TEST_MESSAGE";

        try {
            String strReceiver = theMOMSG.getReceiver();
            String strReceiverType = theMOMSG.getReceiverType();
            String strSourceApplication = theMOMSG.getSourceApplication();
            String strRequestApplication = theMOMSG.getRequestApplication();
            String strRequestCorrelationID = theMOMSG.getRequestCorrelationID();
            String strDestinationReference = theMOMSG.getDestinationReference();
            String strSender = theMOMSG.getSender();
            String strSenderType = theMOMSG.getSenderType();
            String strMOMSG = theMOMSG.getMessage();
            String strSourceReference = theMOMSG.getSourceReference();
            String strMOMessage = theMOMSG.getMessage();

            strMOMessage = (strMOMessage != null) ? strMOMessage.trim() : "";
            if (strSourceApplication == null || strSourceApplication.isEmpty())
                strSourceApplication = "MBANKING_SERVER";
            if (strRequestApplication == null || strRequestApplication.isEmpty())
                strSourceApplication = "MBANKING_SERVER";
            if (strRequestCorrelationID == null || strRequestCorrelationID.isEmpty())
                strRequestCorrelationID = UUID.randomUUID().toString().toLowerCase();
            if (strDestinationReference == null || strDestinationReference.isEmpty())
                strDestinationReference = UUID.randomUUID().toString().toLowerCase();

            //Request
            System.out.println();
            System.out.println("*************************************************");
            System.out.println("            MO MSG Request Details");
            System.out.println("*************************************************");
            System.out.println("Sender               : " + strSender);
            System.out.println("SenderType           : " + strSenderType);
            System.out.println("MO MSG               : " + strMOMSG);
            System.out.println("SourceReference      : " + strSourceReference);
            System.out.println("Receiver             : " + strReceiver);
            System.out.println("ReceiverType         : " + strReceiverType);
            System.out.println("SourceApplication    : " + strSourceApplication);
            System.out.println("RequestApplication   : " + strRequestApplication);
            System.out.println("RequestCorrelationID : " + strRequestCorrelationID);
            System.out.println("DestinationReference : " + strDestinationReference);
            System.out.println("*************************************************");
            System.out.println("            MO MSG Request Details");
            System.out.println("*************************************************");

            //Response
            //Get Online & Error Messages
            strMessage = "Dear member, your message was received!";

            new Thread(() -> {

                /*TransactionWrapper<FlexicoreHashMap> resultWrapper = ApStarCBS.getMemberDetails("MSISDN", strSender);
                if (!resultWrapper.hasErrors()) {

                    FlexicoreHashMap customerDetails = resultWrapper.getSingleRecord();

                    if (customerDetails != null && !customerDetails.isEmpty()) {
                        Repository.update(StringRefs.SENTINEL, "mbanking_logs.msg_log",
                                new FlexicoreHashMap()
                                        .putValue("member_name", customerDetails.getStringValue("full_name"))
                                        .putValue("member_number", customerDetails.getStringValue("identifier")),
                                new FilterPredicate("originator_id = :originator_id"),
                                new FlexicoreHashMap().addQueryArgument(":originator_id", theMOMSG.getOriginatorID()));

                    }
                }*/

                FlexicoreHashMap customerDetails = getMemberDetails(strSender);

                if (customerDetails != null && !customerDetails.isEmpty()) {
                    Repository.update(StringRefs.SENTINEL, "mbanking_logs.msg_log",
                            new FlexicoreHashMap()
                                    .putValue("member_name", customerDetails.getStringValue("name"))
                                    .putValue("member_number", customerDetails.getStringValue("member_no")),
                            new FilterPredicate("originator_id = :originator_id"),
                            new FlexicoreHashMap().addQueryArgument(":originator_id", theMOMSG.getOriginatorID()));

                }

            }).start();
        } catch (Exception e) {
            strMessage = "Dear member, your message was received";
        } finally {
            theMOMSGOnlineResponse.setDestinationReference(strOriginatorID);
            theMOMSGOnlineResponse.setSensitivity(MSGConstants.Sensitivity.NORMAL.getValue());
            theMOMSGOnlineResponse.setMessageFormat(MSGConstants.MessageFormat.TEXT.getValue());
            theMOMSGOnlineResponse.setMessage(strMessage);
            theMOMSGOnlineResponse.setCategory(strCategory);

            theMOMSGOnlineResponse.setResponse(PESAConstants.PESAResponse.SUCCESS.getValue());
            theMOMSGOnlineResponse.setResponseDescription("IMMIGRATION RESPONSE: " + "SUCCESS");
            theMOMSGOnlineResponse.setDateCreated(DateTime.getCurrentDateTime());
        }
    }

    public static void processOfflineMOMSG(MSG theMOMSG, MOMSGOfflineResponse theMOMSGOfflineResponse) {
        try {

        } catch (Exception e) {

        } finally {

        }
    }

    public static void processMSGResult(MTMSGResult theMTMSGResult, MTMSGResultResponse theMTMSGResultResponse) {
        try {

        } catch (Exception e) {

        } finally {

        }
    }

    public static FlexicoreHashMap getMemberDetails(String thePhoneNumber) {

        String strRequestId = UUID.randomUUID().toString();

        thePhoneNumber = ApStarCBS.getTheIdentifier(thePhoneNumber);

        String strAction = "GET_MEMBER_DETAILS";

        FlexicoreHashMap requestBody = new FlexicoreHashMap()
                .putValue("action", strAction)
                .putValue("api_request_id", strRequestId)
                .putValue("payload", new FlexicoreHashMap()
                        .putValue("filter_property", "phone_number")
                        .putValue("filter_value", thePhoneNumber)
                );

        TransactionWrapper<FlexicoreHashMap> resultWrapper = sendSoapRequest("phone_number", thePhoneNumber, strRequestId, Converter.toJson(requestBody), strAction);

        if (resultWrapper.hasErrors()) {
            return new FlexicoreHashMap();
        }

        FlexicoreHashMap resultMap = resultWrapper.getSingleRecord();

        if (resultMap == null) {
            resultWrapper.setHasErrors(true);
            resultWrapper.addError("Failed to fetch customer details.");
            resultWrapper.addMessage("Result from CBS could not be parsed.");
            resultWrapper.setStatusCode(HttpsURLConnection.HTTP_INTERNAL_ERROR);
            return new FlexicoreHashMap();
        }

        return resultMap.getFlexicoreHashMap("data");
    }


    public static TransactionWrapper<FlexicoreHashMap> sendSoapRequest(String theIdentifierType, String theIdentifier, String theRequestUUID, String theRequestJSON, String theAction) {

        Watch watch = new Watch();
        watch.start();

        String requestXML = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:dyn="urn:microsoft-dynamics-schemas/codeunit/DynamicsCRMAPI">
                    <soapenv:Header/>
                    <soapenv:Body>
                        <dyn:HandleRequest>
                            <dyn:request/>
                        </dyn:HandleRequest>
                    </soapenv:Body>
                </soapenv:Envelope>""";

        Document document = XmlUtils.parseXml(requestXML);

        FlexicoreHashMap updateMap = new FlexicoreHashMap();
        updateMap.putValue("/Envelope/Body/HandleRequest/request", theRequestJSON);

        String theRequestBody = XmlUtils.updateXMLTags(document, updateMap);

        TransactionWrapper<FlexicoreHashMap> resultWrapper = new TransactionWrapper<>();

        try {

            String strURL = ApStarCBSParams.getSOAPURL();

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
                            ApStarCBSParams.getPassword().toCharArray(), ".", ApStarCBSParams.getDomain()));

            if (ApStarCBSParams.isLogRequestEnabled()) {
                if (!theAction.equalsIgnoreCase("CALL_SERVICE")) {
                    System.out.println("\n----------------| REQUEST |----------------\n"+
                                       "Request Log Ref : " + theRequestUUID + "\n" +
                                       "Requester Type  : " + theIdentifierType + "\n" +
                                       "Requester       : " + theIdentifier + "\n" +
                                       "Request Action  : " + theAction + "\n" +
                                       "Request Body    : " + "\n" +
                                       "-------------------------------------------\n" + theRequestBody+"\n");
                }
            } else {

                if (!theAction.equalsIgnoreCase("CALL_SERVICE")) {
                    System.out.println("\n----------------| REQUEST |----------------\n"+
                                       "Request Log Ref : " + theRequestUUID + "\n" +
                                       "Requester Type  : " + theIdentifierType + "\n" +
                                       "Requester       : " + theIdentifier + "\n" +
                                       "Request Action  : " + theAction + "\n" +
                                       "Request Body    : " + "\n" +
                                       "-------------------------------------------\n");
                }
            }

            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider).build()) {
                HttpPost httpPost = new HttpPost(strURL);
                httpPost.setEntity(new StringEntity(theRequestBody));
                httpPost.setHeader("Content-type", "application/xml");
                httpPost.setHeader("SOAPAction", ApStarCBSParams.getSOAPAction());

                return httpClient.execute(httpPost, new SOAPResponseHandler(theRequestUUID, theIdentifierType, theIdentifier, theAction, watch));

            } catch (Exception e) {
                resultWrapper.copyFrom(Misc.getTransactionWrapperStackTrace(e));
                resultWrapper.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                return resultWrapper;
            }

        } catch (Exception e) {
            resultWrapper.copyFrom(Misc.getTransactionWrapperStackTrace(e));
            resultWrapper.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

            return resultWrapper;
        }
    }

    public static String extractHostFromURL(URL url) {
        return url.getHost();
    }

    // Function to extract the port from a URL
    public static int extractPortFromURL(URL url) {
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


    public static int sendMSG(String theOriginatorID, MSGConstants.MSGMode theMSGMode, String theReceiverType, String theReceiver, String theMessage, String theRequestApplication,
                              String theSourceApplication, int thePriority, String theCategory,
                              MSGConstants.Sensitivity theSensitivity, String theRequestCorrelationID, String theSourceReference) {
        int status = -1;
        try {
            String theSenderType = MSGConstants.SenderType.SENDER_ID.getValue();
            String theMessageFormat = "TEXT";
            long theProductID = Long.parseLong(MBankingAPI.getValueFromLocalParams(MBankingConstants.ApplicationType.MSG, "OTHER_DETAILS/CUSTOM_PARAMETERS/SMS/MT/PRODUCT_ID"));
            String theSender = MBankingAPI.getValueFromLocalParams(MBankingConstants.ApplicationType.MSG, "OTHER_DETAILS/CUSTOM_PARAMETERS/SMS/MT/SENDER");
            String theCommand = "BulkSMS";
            String theCharge = "YES";

            status = MSGProcessor.sendMSG(theOriginatorID, theProductID, theSenderType, theSender, theReceiverType, theReceiver, theMessageFormat, theMessage,
                    theCommand, theSensitivity, theCategory, thePriority, theCharge, theMSGMode, theRequestApplication, theRequestCorrelationID,
                    theSourceApplication, theSourceReference);

            if (status <= 0) {
                System.err.println("ERROR Sending " + theCategory + " - " + theMessage + " to " + theReceiver + "\n");
            }
        } catch (Exception e) {
            System.err.println("MSGAPI.sendMSG() ERROR : " + e.getMessage());
        }

        return status;
    }

    /*public static void TEST(String strTrackingId){
        String strOriginatorID = UUID.randomUUID().toString().toLowerCase();
        String strCategory = "PASSPORT_STATUS_ENQUIRY";
        String str_MT_MSG_ReceiverType = "MSISDN";
        //String str_MT_MSG_Receiver = "254721913958";
        String str_MT_MSG_Receiver = "254721303295";
        String strSourceApplication = "IMMIGRATION_SERVER";
        String strRequestApplication = "IMMIGRATION_SERVER";
        String strRequestCorrelationID = UUID.randomUUID().toString().toLowerCase();
        String strSourceReference = UUID.randomUUID().toString().toLowerCase();
        String strDestinationReference = UUID.randomUUID().toString().toLowerCase();

        String strPassportStatusMessage = "";

        try {
            PassportApplication passportApplication = InquiryServiceDB.getPassportApplicationByTrackingId(strTrackingId);
            if(passportApplication == null){
                strPassportStatusMessage = MSGAPIDB.getMsgTemplateByTemplateCode(InquiryServiceConstants.SystemMsgTemplateCode.INVALID_TRACKING_ID).get("template");
            } else {
                String passportApplicationStage = passportApplication.getStage();
                HashMap<String, String> msgTemplate = MSGAPIDB.getMsgTemplateByPassportApplicationStage(passportApplicationStage);

                if(msgTemplate.get("stage_notification_status").equals("ACTIVE")){
                    strPassportStatusMessage = msgTemplate.get("template");
                } else {
                    strPassportStatusMessage = MSGAPIDB.getMsgTemplateByTemplateCode(InquiryServiceConstants.SystemMsgTemplateCode.UNKNOWN_STAGE).get("template");
                }
            }
        } catch (Exception e){
            e.printStackTrace();
            strPassportStatusMessage = MSGAPIDB.getMsgTemplateByTemplateCode(InquiryServiceConstants.SystemMsgTemplateCode.SYSTEM_ERROR).get("template");
        }

        sendMSG(strOriginatorID, MSGConstants.MSGMode.SAF, str_MT_MSG_ReceiverType, str_MT_MSG_Receiver, strPassportStatusMessage, strRequestApplication,
                strSourceApplication, 210, strCategory, MSGConstants.Sensitivity.NORMAL, strRequestCorrelationID, strSourceReference);

    }*/
}
