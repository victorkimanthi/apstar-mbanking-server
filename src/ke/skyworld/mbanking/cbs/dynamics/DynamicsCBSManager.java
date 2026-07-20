package ke.skyworld.mbanking.cbs.dynamics;

import com.fasterxml.jackson.databind.ObjectMapper;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.UUID;

public class DynamicsCBSManager {
    public static String send(String theAction, String theSessionId, String theMobileNumber, Object theRequestBody) {
        String rVal = "";
        try {
            String strURL = DynamicsCBSParams.getSOAPURL();
            String strJsonBody = wrapJsonRequest(theAction, theSessionId, theMobileNumber, theRequestBody);
            String strXMLBody = buildSoapRequest(strJsonBody);

            if (DynamicsCBSParams.isLogRequestEnabled()) {
                System.out.println("***********************SENDING REQ TO CBS*************************");

                //System.out.println("REQ: -> : "+strXMLBody);
                System.out.println("REQ: -> : " + strJsonBody);
                System.out.println("*******************************************************************");
            }

            URL url = new URL(strURL);

            String strHost = extractHostFromURL(url);
            int strPort = extractPortFromURL(url);

            // NTLM Credentials
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();

            credentialsProvider.setCredentials(
                    new AuthScope(new AuthScope(strHost, strPort)),
                    new UsernamePasswordCredentials(DynamicsCBSParams.getUser(), DynamicsCBSParams.getPassword().toCharArray()));
            //TODO:Testing if NTLM
            //new NTCredentials(DynamicsCBSParams.getUser(), DynamicsCBSParams.getPassword().toCharArray(), ".", DynamicsCBSParams.getDomain()));

            // Create HttpClient with NTLM authentication
            try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credentialsProvider).build()) {
                HttpPost httpPost = new HttpPost(strURL);

                // Set JSON body
                httpPost.setEntity(new StringEntity(strXMLBody));

                // Set headers
                httpPost.setHeader("Content-type", "application/xml");
                httpPost.setHeader("SOAPAction", DynamicsCBSParams.getSOAPAction());

                // Execute the request
                CloseableHttpResponse response = httpClient.execute(httpPost);

                // Handle response
                int statusCode = response.getCode();

                // Get response entity
                HttpEntity entity = response.getEntity();

                // Print response body
                if (entity != null) {
                    String responseBody = EntityUtils.toString(entity);
                    String strReason = response.getReasonPhrase();

                    /*if (DynamicsCBSParams.isLogResponseEnabled()) {
                        System.out.println("Response Body: <- : " + responseBody);
                        System.out.println("Response Reason: <- : " + strReason);
                    }*/

                    if (statusCode == HttpStatus.SC_OK) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document document = builder.parse(new InputSource(new StringReader(responseBody)));

                        NodeList returnNodes = document.getElementsByTagName("return_value");
                        if (returnNodes.getLength() > 0) {
                            Node returnNode = returnNodes.item(0);
                            if (returnNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element returnElement = (Element) returnNode;
                                String jsonContent = returnElement.getTextContent();

                                if (DynamicsCBSParams.isLogResponseEnabled()) {
                                    System.out.println();
                                    System.out.println("********************** RESPONSE FROM CBS *******************************");
                                    System.out.println("RES: <- : " + jsonContent);
                                    System.out.println("************************************************************************");

                                    return jsonContent;
                                }
                            }
                        }
                    } else {
                        if (DynamicsCBSParams.isLogResponseEnabled()) {
                            System.out.println();
                            System.out.println("********************** RESPONSE FROM CBS E *******************************");
                            System.out.println("Response Code: <- : " + statusCode);
                            System.out.println("Response Body: <- : " + responseBody);
                            System.out.println("************************************************************************");

                        }
                        return "{\"response\":{\"id\":\"dc29f9e2-6101-45b1-aef3-b0ad53c2ee54\",\"action\":\"-\",\"status\":\"failed\",\"hash\":\"44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a\",\"timestamp\":\"2023-09-13T15:53:11.3560000Z\",\"payload\":{\"title\":\"Error\",\"message\":\"An error occurred while processing your request. Please try again later.\",\"data\":{}}}}";
                    }
                }
            } catch (Exception e) {
                System.err.println(DynamicsCBSManager.class.getSimpleName() + "." + new Object() {
                }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println(DynamicsCBSManager.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return rVal;
    }


    /**
     * Wraps the given request data in a JSON request object.
     *
     * @param theAction      The action to be performed in the request.
     * @param theRequestData The data to be included in the request payload.
     * @return The JSON request object as a string.
     */
    public static String wrapJsonRequest(String theAction, String theSessionId, String theMobileNumber, Object theRequestData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            //objectMapper.enable(SerializationFeature.INDENT_OUTPUT);


            LinkedHashMap<String, Object> lhPayload = new LinkedHashMap<>();
            lhPayload.put("data", theRequestData);

            String strId = UUID.randomUUID().toString();

            LinkedHashMap<String, Object> lhRequest = new LinkedHashMap<>();
            lhRequest.put("action", theAction);
            lhRequest.put("id", strId);
            lhRequest.put("hash", "hash");
            lhRequest.put("session_id", theSessionId);
            lhRequest.put("user", theMobileNumber);
            lhRequest.put("timestamp", "timestamp");
            lhRequest.put("payload", lhPayload);

            LinkedHashMap<String, Object> lhParent = new LinkedHashMap<>();
            lhParent.put("request", lhRequest);

            return objectMapper.writeValueAsString(lhParent);
        } catch (Exception e) {
            System.err.println(DynamicsCBSManager.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            return "";
        }
    }

    public static String buildSoapRequest(String requestContent) {
        return buildSoapRequest(DynamicsCBSParams.getSOAPAction(), requestContent);
    }

    /**
     * Builds a SOAP request with the provided request content.
     *
     * @param requestContent the content of the request
     * @return the SOAP request as a string, or empty string if an exception occurs
     */
    public static String buildSoapRequest(String SOAPAction, String requestContent) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            // Create SOAP Envelope
            Element envelope = doc.createElement("soapenv:Envelope");
            envelope.setAttribute("xmlns:soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
            doc.appendChild(envelope);

            // Create SOAP Body
            Element body = doc.createElement("soapenv:Body");
            envelope.appendChild(body);

            String[] lsSoapActionParts = SOAPAction.split(":");

            // Create ProcessRequest Element
            Element processRequest = doc.createElement("ext:" + lsSoapActionParts[lsSoapActionParts.length - 1]);
            processRequest.setAttribute("xmlns:ext", lsSoapActionParts[0] + ":" + lsSoapActionParts[1]);
            body.appendChild(processRequest);


            // Set the provided request content
            if (requestContent != null && !requestContent.isEmpty()) {
                // Create Request Element
                Element request = doc.createElement("ext:request");
                processRequest.appendChild(request);

                Text contentText = doc.createTextNode(requestContent);
                request.appendChild(contentText);
            }

            // Convert Document to String
            return XmlToString(doc);

        } catch (Exception e) {
            System.err.println(DynamicsCBSManager.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            return "";
        }
    }


    /**
     * Converts an XML document to a string.
     *
     * @param doc the XML document to be converted
     * @return the string representation of the XML document
     * @throws Exception if an exception occurs during the conversion process
     */
    private static String XmlToString(Document doc) throws Exception {
        javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer = tf.newTransformer();
        java.io.StringWriter writer = new java.io.StringWriter();
        transformer.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(writer));
        return writer.getBuffer().toString();
    }

    // Function to extract the host (IP or domain) from a URL
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

    public static class DynamicsResponse {
        private Object parent;
        private Object data;
        private String title;
        private String message;
        private boolean hasError;
        private boolean endSession;

        public DynamicsResponse() {
        }

        public Object getParent() {
            return parent;
        }

        public void setParent(Object parent) {
            this.parent = parent;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public boolean hasError() {
            return hasError;
        }

        public void setHasError(boolean hasError) {
            this.hasError = hasError;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isEndSession() {
            return endSession;
        }

        public void setEndSession(boolean endSession) {
            this.endSession = endSession;
        }
    }
}
