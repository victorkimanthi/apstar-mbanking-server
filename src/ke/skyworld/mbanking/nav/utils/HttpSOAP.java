package ke.skyworld.mbanking.nav.utils;

import ke.skyworld.mbanking.nav.NavisionHelper;
import ke.skyworld.mbanking.nav.conn.NavisionAgencyConnectionManager;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.time.Duration;
import java.time.Instant;

public class HttpSOAP {



    public static String sendAgencyRequest(String SOAPFunction, String theRequestBody) throws Exception {
        System.out.println();
        System.out.println("----------------------------------------------------");
        System.out.println("Making SOAP Request...");
        System.out.println("URL                    : " + NavisionAgencyConnectionManager.params.getCoreBankingUrl());
        System.out.println("Username               : " + NavisionAgencyConnectionManager.params.getCoreBankingUsername());
        System.out.println("Domain                 : " + NavisionAgencyConnectionManager.params.getCoreBankingDomain());
        System.out.println("SOAP Action            : " + NavisionAgencyConnectionManager.params.getCoreBankingSOAPActionPrefix() + SOAPFunction);

        if (NavisionAgencyConnectionManager.params.getCoreBankingLoggingLevel() == LoggingLevel.DEBUG) {
            System.out.println("Request Body           :\n------\n" + theRequestBody + "\n------\n");
        }

        HttpPost httpPost = new HttpPost(NavisionAgencyConnectionManager.params.getCoreBankingUrl());
        httpPost.setEntity(new StringEntity(theRequestBody, ContentType.parse("UTF-8")));
        httpPost.setHeader("Content-type", "application/xml");
        httpPost.setHeader("SOAPAction", NavisionAgencyConnectionManager.params.getCoreBankingSOAPActionPrefix() + SOAPFunction);

        Instant instStart = Instant.now();

        try (CloseableHttpClient closeableHttpClient = NavisionAgencyConnectionManager.getHttpClient()) {
            if (closeableHttpClient == null) {
                throw new Exception("HttpSOAP.sendAgencyRequest() - ERROR: CloseableHttpClient is NULL");
            }

            String response="";
            try (CloseableHttpResponse httpResponse = closeableHttpClient.execute(httpPost)) {
                response = new HttpSOAPResponseHandler().handleResponse(httpResponse);
            }

            Instant instEnd = Instant.now();
            System.out.println("Response Time (ms)     : " + Duration.between(instStart, instEnd).toMillis());
            System.out.println("----------------------------------------------------");

            if (NavisionAgencyConnectionManager.params.getCoreBankingLoggingLevel() == LoggingLevel.DEBUG) {
                System.out.println("Response Body          :\n------\n" + response + "\n------\n");
            }

            // Validate response
            if (response == null || response.trim().isEmpty()) {
                System.out.println("Response is null or empty");
                return "";
            }

            // Extract return_value
            return extractReturnValue(response);

        } catch (Exception e) {
            String error = "HttpSOAP.sendAgencyRequest(): Error making HTTP SOAP request: " + e.getMessage();
            System.err.println(error);
            e.printStackTrace();
            throw new Exception(error);
        }
    }

    private static String extractReturnValue(String response) {
        if (response == null || response.isEmpty()) {
            return "";
        }

        String startTag = "<return_value>";
        String endTag = "</return_value>";

        int start = response.indexOf(startTag);
        int end = response.indexOf(endTag);

        if (start == -1 || end == -1) {
            return ""; // Tag not found
        }

        start += startTag.length(); // Move start position after tag

        return response.substring(start, end).trim();
    }



}
