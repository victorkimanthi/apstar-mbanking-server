package ke.skyworld.mbanking.test;

import ke.co.skyworld.smp.utility_items.DateTime;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.lib.mbanking.register.MemberRegisterResponse;
import ke.skyworld.lib.mbanking.register.RegisterConstants;
import ke.skyworld.lib.mbanking.register.RegisterProcessor;
import ke.skyworld.lib.mbanking.ussd.USSDResponseSELECTOption;
import ke.skyworld.lib.mbanking.utils.Crypto;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapplication.AppConstants;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static ke.skyworld.mbanking.ussdapi.APIUtils.generateLastNMonthsDateRanges;

public class Test {
   /* public static void main(String[] args) {
        String strURL = "http://192.168.151.35:4003/UkulimaTest/WS/UKULIMA%20SACCO%20SOCIETY%20LTD/Codeunit/NewSkyMbanking";

        try {
            URL url = new URL(strURL);
            String strHost = extractHostFromURL(url);
            int strPort = extractPortFromURL(url);
            String navUser = ".\\hchambela";
            String navPassword = "@Tr@ilABC23";
            String navDomain = "ukulimafosa";

            // Set up the credentials provider
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            ((BasicCredentialsProvider) credentialsProvider).setCredentials(
                    new AuthScope(strHost, strPort),
                    new NTCredentials(navUser, navPassword.toCharArray(), ".", navDomain)
            );

            // Create HttpClient with the credentials provider
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .build()) {

                // Create a POST request
                HttpPost httpPost = new HttpPost(strURL);
                httpPost.setHeader("Content-Type", "text/xml; charset=utf-8");
                httpPost.setHeader("SOAPAction", ApStarCBSParams.getSOAPAction());

                // Define the SOAP XML body
                String soapRequestBody =
                        "<Envelope xmlns=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                                "<Body>" +
                                "<HandleRequest xmlns=\"urn:microsoft-dynamics-schemas/codeunit/NewSkyMbanking\">" +
                                "<request>{\"action\":\"GET_DEPOSIT_ACCOUNTS\",\"api_request_id\":\"8b36d565-7a9c-46c6-ba2b-673f54ed8543\",\"payload\":{\"identifier_type\":\"CUSTOMER_NO\",\"identifier\":\"0058629\",\"account_type\":\"FOSA\",\"transaction_date_time\":\"2024-11-06 11:52:48\"}}</request>" +
                                "</HandleRequest>" +
                                "</Body>" +
                                "</Envelope>";

                // Attach the XML payload to the POST request
                httpPost.setEntity(new StringEntity(soapRequestBody));

                // Watch the time taken for the request
                Watch watch = new Watch();
                watch.start();

                // Execute the request using SOAPResponseHandler
                ClassicHttpResponse response = (ClassicHttpResponse) httpClient.execute(httpPost, new SOAPResponseHandler(
                        "8b36d565-7a9c-46c6-ba2b-673f54ed8543",
                        "CUSTOMER_NO",
                        "0058629",
                        "GET_DEPOSIT_ACCOUNTS",
                        watch
                ));

                // Print the response status and body
                System.out.println("Response Status: " + response.getCode());
                String responseBody = EntityUtils.toString(response.getEntity());
                System.out.println("Response Body: " + responseBody);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/


    /*public static void main(String[] args) {
        String secretKey = "Secret1234";


//        +--------------+------------------------------------+-------+---------+--------+----------+---------+-----------+------------+----------------+--------------------+-------------------+--------------+--------------------+-----------------+---------------+------------------------------------+---------------------+-----------------------+-----------+-----------------+--------------+---------------+----------------+------------------+---------------------------------------------------------------------------------------+-----------+-----------------+--------------+------------------+----------------+------------------+--------------------+-------------+-------------------+----------------+-------------+------------------+--------------------+--------------------------------------------------------------------+----------------+----------------------+-------------------+----------------+---------------------+-----------------------+--------------------------------------------------------------------+------------------------------------+------------------------------------+-----------------------+--------------------+------------------+------------------+--------------------------------------------+-----------------+-------------+---------------+-------------+--------------------------+--------------------------+-------------+----------------------------------------------------------------+-----------------+--------------------------+--------------------------+----------------+----------------+------------------------------------------------------------------+--------------------------+------------------+------------------+------------------+-----------------------+------------------------------------------------------------------+--------------------------+----------------------+----------------------------------------------------------------+--------------------------+----------------+----------------+---------------------+-----------------------+----------------+--------------------+--------------------------+----------------------+
//                |transaction_id|originator_id                       |pesa_id|server_id|trace_id|product_id|pesa_type|pesa_action|pesa_command|pesa_sensitivity|pesa_charge_proposed|pesa_charge_applied|initiator_type|initiator_identifier|initiator_account|initiator_name |initiator_reference                 |initiator_application|initiator_other_details|source_type|source_identifier|source_account|source_name    |source_reference|source_application|source_other_details                                                                   |sender_type|sender_identifier|sender_account|sender_name       |sender_reference|sender_application|sender_other_details|receiver_type|receiver_identifier|receiver_account|receiver_name|receiver_reference|receiver_application|receiver_other_details                                              |beneficiary_type|beneficiary_identifier|beneficiary_account|beneficiary_name|beneficiary_reference|beneficiary_application|beneficiary_other_details                                           |batch_reference                     |correlation_reference               |correlation_application|transaction_currency|transaction_amount|transaction_charge|transaction_remark                          |pesa_category    |pesa_priority|pesa_send_count|schedule_pesa|pesa_date_scheduled       |pesa_date_created         |pesa_xml_data|pesa_send_integrity_hash                                        |pesa_general_flag|local_date_created        |local_pesa_date_sent      |pesa_status_code|pesa_status_name|pesa_status_description                                           |pesa_status_date          |pesa_response_code|pesa_response_name|pesa_response_type|pesa_response_reference|pesa_response_description                                         |pesa_response_date        |pesa_response_xml_data|pesa_response_integrity_hash                                    |local_pesa_response_date  |pesa_result_code|pesa_result_name|pesa_result_reference|pesa_result_description|pesa_result_date|pesa_result_xml_data|pesa_result_integrity_hash|local_pesa_result_date|
//                +--------------+------------------------------------+-------+---------+--------+----------+---------+-----------+------------+----------------+--------------------+-------------------+--------------+--------------------+-----------------+---------------+------------------------------------+---------------------+-----------------------+-----------+-----------------+--------------+---------------+----------------+------------------+---------------------------------------------------------------------------------------+-----------+-----------------+--------------+------------------+----------------+------------------+--------------------+-------------+-------------------+----------------+-------------+------------------+--------------------+--------------------------------------------------------------------+----------------+----------------------+-------------------+----------------+---------------------+-----------------------+--------------------------------------------------------------------+------------------------------------+------------------------------------+-----------------------+--------------------+------------------+------------------+--------------------------------------------+-----------------+-------------+---------------+-------------+--------------------------+--------------------------+-------------+----------------------------------------------------------------+-----------------+--------------------------+--------------------------+----------------+----------------+------------------------------------------------------------------+--------------------------+------------------+------------------+------------------+-----------------------+------------------------------------------------------------------+--------------------------+----------------------+----------------------------------------------------------------+--------------------------+----------------+----------------+---------------------+-----------------------+----------------+--------------------+--------------------------+----------------------+
//                |211           |6842ef52-f5b7-4f1f-a64c-ee60ce604f01|0      |0        |        |0         |PESA_OUT |B2B        |PESALINK    |NORMAL          |null                |null               |MSISDN        |254723902802        |254723902802     |Collins Collins|270939f7-5d6d-47df-9419-c5cc39ef1b9d|MAPP                 |<DATA/>                |ACCOUNT_NO |5000009086000    |5000009086000 |Collins Collins|FYANQM6         |CBS               |<DATA><BANK_CODE>Apstar SACCO</BANK_CODE><BRANCH_CODE>Apstar SACCO</BRANCH_CODE></DATA>|ACCOUNT_NO |0                |0             |TEST - FBL Account|null            |null              |<DATA/>             |ACCOUNT_NO   |318612             |318612          |test         |null              |null                |<DATA><BANK_CODE>11</BANK_CODE><BRANCH_CODE>000</BRANCH_CODE></DATA>|ACCOUNT_NO      |318612                |318612             |test            |null                 |null                   |<DATA><BANK_CODE>11</BANK_CODE><BRANCH_CODE>000</BRANCH_CODE></DATA>|F93D7318-A1C5-48DB-83C2-471A1F0401CD|270939f7-5d6d-47df-9419-c5cc39ef1b9d|MAPP                   |KES                 |14.00000          |null              |PL|BT|11|318612|254723902802|Collins Collins|PESALINK_TRANSFER|201          |1              |NO           |2024-11-18 17:57:55.000000|2024-11-18 17:57:55.000000|<DATA/>      |3e093a3bf5131811b585f02c8b26cf42779d4e766728fc4606d6107a97b87999|null             |2024-11-18 17:57:55.318477|2024-11-18 17:57:55.318477|811             |ERROR           |Security Violation - Integrity Check Failed. Response NOT Accepted|2024-11-18 17:57:57.376112|811               |ERROR             |RESPONSE          |null                   |Security Violation - Integrity Check Failed. Response NOT Accepted|2024-11-18 17:57:57.000000|null                  |020a4b3ad5488e09ab44ed6c0f97439e8addc911eb542b3fef4f20bb3d347dff|2024-11-18 17:57:57.376112|null            |null            |null                 |null                   |null            |null                |null                      |null                  |
//                +--------------+------------------------------------+-------+---------+--------+----------+---------+-----------+------------+----------------+--------------------+-------------------+--------------+--------------------+-----------------+---------------+------------------------------------+---------------------+-----------------------+-----------+-----------------+--------------+---------------+----------------+------------------+---------------------------------------------------------------------------------------+-----------+-----------------+--------------+------------------+----------------+------------------+--------------------+-------------+-------------------+----------------+-------------+------------------+--------------------+--------------------------------------------------------------------+----------------+----------------------+-------------------+----------------+---------------------+-----------------------+--------------------------------------------------------------------+------------------------------------+------------------------------------+-----------------------+--------------------+------------------+------------------+--------------------------------------------+-----------------+-------------+---------------+-------------+--------------------------+--------------------------+-------------+----------------------------------------------------------------+-----------------+--------------------------+--------------------------+----------------+----------------+------------------------------------------------------------------+--------------------------+------------------+------------------+------------------+-----------------------+------------------------------------------------------------------+--------------------------+----------------------+----------------------------------------------------------------+--------------------------+----------------+----------------+---------------------+-----------------------+----------------+--------------------+--------------------------+----------------------+
//
//


    }*/


 /*   public static void main(String[] args) {

        String strEndDate = "  31/10/2024";
      String strD= DateTime.convertStringToDateToString(strEndDate, "dd/MM/yyyy", "dd MMM yyyy");
        System.out.println(strD);

    }*/
/*  public static void main(String[] args) {
        //generate guid
        String guid = java.util.UUID.randomUUID().toString();
        System.out.println(guid);

      //generateLastNMonthsDateRanges(10);
    }*/

    public static void main(String[] args) {
        int intCurrentYear = LocalDate.now().getYear()-2;

        for (int i = 1; i <= 1; i++) {
            intCurrentYear--;
            System.out.println(intCurrentYear);
        }


    }
}