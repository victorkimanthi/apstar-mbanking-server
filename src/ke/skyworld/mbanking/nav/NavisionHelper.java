package ke.skyworld.mbanking.nav;

import ke.skyworld.mbanking.nav.conn.NavisionAgencyConnectionManager;
import ke.skyworld.mbanking.nav.utils.HttpSOAP;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Map;


public class NavisionHelper {

    public static String sendAgency(String strMethodName, Map<String, Object> mparams) {
        String rVal = null;
        String strNamespaceUrl = NavisionAgencyConnectionManager.params.getCoreBankingSOAPActionPrefix();
        if(strNamespaceUrl.endsWith(":")) {
            strNamespaceUrl = strNamespaceUrl.substring(0, strNamespaceUrl.length() - 1);
        }


        try {
            String strXMLBody = buildSoapRequest(strMethodName, strNamespaceUrl, mparams);
            rVal = HttpSOAP.sendAgencyRequest(strMethodName, strXMLBody);

            System.out.println("Response from Navision: " + rVal);

        } catch (Exception e) {
            System.err.println(NavisionHelper.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }


        return rVal;
    }

    public static String buildSoapRequest(String methodName, String namespace, Map<String, Object> parameters) {
        try {
            // Create a new XML document
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.newDocument();

            Element envelope = doc.createElement("Envelope");
            envelope.setAttribute("xmlns", "http://schemas.xmlsoap.org/soap/envelope/");
            doc.appendChild(envelope);

            // Create SOAP Body
            Element body = doc.createElement("Body");
            envelope.appendChild(body);

            // Create Method Element (e.g., AccountBalanceEnquiry, ActiveMobileMember)
            Element methodElement = doc.createElement(methodName);
            methodElement.setAttribute("xmlns", namespace);
            body.appendChild(methodElement);

            // Add dynamic parameters to the method element


            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                Element paramElement = doc.createElement(entry.getKey());

                // Convert the value to a string before creating a Text node
                String valueAsString = String.valueOf(entry.getValue());
                Text paramValue = doc.createTextNode(valueAsString);

                paramElement.appendChild(paramValue);
                methodElement.appendChild(paramElement);
            }


            // Convert Document to String
            return XmlToString(doc);

        } catch (Exception e) {
            e.printStackTrace();

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



    public static String unescapeXml(String escapedXml) {
        return escapedXml.replace("&lt;", "<").replace("&gt;", ">");
    }

  }