package ke.skyworld.mbanking.nav;

import ke.skyworld.lib.mbanking.utils.Crypto;
import ke.skyworld.mbanking.cbs.CBSAPI;
import ke.skyworld.mbanking.nav.params.AgencyBankingNavisionLocalParams;
import ke.skyworld.mbanking.nav.utils.LoggingLevel;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class NavisionUtils {

/*
    public static AgencyBankingNavisionLocalParams getAgencyBankingNavisionLocalParameters(String strfilename) {
        try {
            String strFilePath = System.getProperty("user.dir")+ File.separator+ strfilename;
            System.out.println("NavisionUtils.getNavisionAgencyLocalParameters() Reading from file: "+strFilePath);

            BufferedReader bufferedReader = new BufferedReader(new FileReader(new File(strFilePath)));
            String strLine;
            StringBuilder stringBuilder = new StringBuilder();

            while((strLine=bufferedReader.readLine())!= null){
                stringBuilder.append(strLine.trim());
            }

            String strConfig = stringBuilder.toString();

            InputSource source = new InputSource(new StringReader(strConfig));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);
            XPath configXPath = XPathFactory.newInstance().newXPath();

            String strLoggingLevel = configXPath.evaluate("/CONFIG/@LOGGING_LEVEL", xmlDocument, XPathConstants.STRING).toString();
            String strType = configXPath.evaluate("/CONFIG/CORE_BANKING/TYPE", xmlDocument, XPathConstants.STRING).toString();
            String strURL = configXPath.evaluate("/CONFIG/CORE_BANKING/URL", xmlDocument, XPathConstants.STRING).toString();
            String strUsername = configXPath.evaluate("/CONFIG/CORE_BANKING/USERNAME", xmlDocument, XPathConstants.STRING).toString();
            String strPassword = configXPath.evaluate("/CONFIG/CORE_BANKING/PASSWORD", xmlDocument, XPathConstants.STRING).toString();
            String strPasswordType = configXPath.evaluate("/CONFIG/CORE_BANKING/PASSWORD/@TYPE", xmlDocument, XPathConstants.STRING).toString();
            String strDomain = configXPath.evaluate("/CONFIG/CORE_BANKING/DOMAIN", xmlDocument, XPathConstants.STRING).toString();
            String strWorkstation = configXPath.evaluate("/CONFIG/CORE_BANKING/WORKSTATION", xmlDocument, XPathConstants.STRING).toString();
            String strSOAPActionPrefix = configXPath.evaluate("/CONFIG/CORE_BANKING/SOAP_ACTION_REFFIX", xmlDocument, XPathConstants.STRING).toString();

            String strEncryptionKey = "Vx@3GhTu*7nbHJg^)SYTDhs>pij?2H";

            if(strPasswordType.equalsIgnoreCase("CLEARTEXT")){
                // Get the root element
                NodeList nlCoreBanking= xmlDocument.getFirstChild().getChildNodes().item(0).getChildNodes();
                Node ndPassword = nlCoreBanking.item(3);

                Crypto crypto = new Crypto();
                String strEncryptedPassword = crypto.encrypt(strEncryptionKey, strPassword);
                ndPassword.setTextContent(strEncryptedPassword);
                ndPassword.getAttributes().getNamedItem("TYPE").setTextContent("ENCRYPTED");

                // write the content into xml file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();

                DOMSource dOMSource = new DOMSource(format(xmlDocument));
                StreamResult result = new StreamResult(new File(strFilePath));
                transformer.transform(dOMSource, result);
            } else if(strPasswordType.equalsIgnoreCase("ENCRYPTED")){
                strPassword = new Crypto().decrypt(strEncryptionKey, strPassword);
            } else {
                System.err.println("NavisionUtils.getNavisionLocalParameters() Error. Unknown password type");
                return null;
            }

            AgencyBankingNavisionLocalParams localParams = new AgencyBankingNavisionLocalParams();

            LoggingLevel loggingLevel = LoggingLevel.INFO;
            try {
                loggingLevel = LoggingLevel.valueOf(strLoggingLevel);
            } catch (Exception ignore) {}

            localParams.setCoreBankingLoggingLevel(loggingLevel);
            localParams.setCoreBankingType(strType);
            localParams.setCoreBankingUrl(strURL);
            localParams.setCoreBankingUsername(strUsername);
            localParams.setCoreBankingPassword(strPassword);
            localParams.setCoreBankingDomain(strDomain);
            localParams.setCoreBankingWorkstation(strWorkstation);
            localParams.setCoreBankingSOAPActionPrefix(strSOAPActionPrefix);
            System.out.println("NavisionUtils.getNavisionAgencyLocalParameters() Done");

            return localParams;

        } catch (Exception e) {
            System.err.println("NavisionUtils.getNavisionLocalParameters() Error. "+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }
*/



    public static AgencyBankingNavisionLocalParams getAgencyBankingNavisionLocalParameters(String strfilename) {
        try {
            String strFilePath = System.getProperty("user.dir") + File.separator + strfilename;
            System.out.println("Reading from file: " + strFilePath);

            // Efficient file reading
            String strConfig = Files.readString(Paths.get(strFilePath));

            // Parse XML
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(new InputSource(new StringReader(strConfig)));
            XPath configXPath = XPathFactory.newInstance().newXPath();

            // Extract values
            String strLoggingLevel = configXPath.evaluate("/CONFIG/@LOGGING_LEVEL", xmlDocument);
            String strType = configXPath.evaluate("/CONFIG/CORE_BANKING/TYPE", xmlDocument);
            String strURL = configXPath.evaluate("/CONFIG/CORE_BANKING/URL", xmlDocument);
            String strUsername = configXPath.evaluate("/CONFIG/CORE_BANKING/USERNAME", xmlDocument);
            String strPassword = configXPath.evaluate("/CONFIG/CORE_BANKING/PASSWORD", xmlDocument);
            String strPasswordType = configXPath.evaluate("/CONFIG/CORE_BANKING/PASSWORD/@TYPE", xmlDocument);
            String strDomain = configXPath.evaluate("/CONFIG/CORE_BANKING/DOMAIN", xmlDocument);
            String strWorkstation = configXPath.evaluate("/CONFIG/CORE_BANKING/WORKSTATION", xmlDocument);
            String strSOAPActionPrefix = configXPath.evaluate("/CONFIG/CORE_BANKING/SOAP_ACTION_REFFIX", xmlDocument);
            //Params
            System.out.println("Logging Level: " + strLoggingLevel);
            System.out.println("Core Banking Parameters");
            System.out.println("----------------------------------------------------");
            System.out.println("Type                  : " + strType);
            System.out.println("URL                   : " + strURL);
            System.out.println("Username              : " + strUsername);
            System.out.println("Password              : " + strPassword);
            System.out.println("Domain                : " + strDomain);
            System.out.println("Workstation           : " + strWorkstation);





            // Retrieve encryption key securely
            String strEncryptionKey = "Vx@3GhTu*7nbHJg^)SYTDhs>pij?2H";
            if (strEncryptionKey == null || strEncryptionKey.isEmpty()) {
                throw new IllegalStateException("Encryption key is not set in environment variables.");
            }

            // Locate the PASSWORD node dynamically
            XPathExpression expr = configXPath.compile("/CONFIG/CORE_BANKING/PASSWORD");
            Node ndPassword = (Node) expr.evaluate(xmlDocument, XPathConstants.NODE);

            Crypto crypto = new Crypto();

            if ("CLEARTEXT".equalsIgnoreCase(strPasswordType)) {
                String strEncryptedPassword = crypto.encrypt(strEncryptionKey, strPassword);
                if (ndPassword != null) {
                    ndPassword.setTextContent(strEncryptedPassword);
                    ndPassword.getAttributes().getNamedItem("TYPE").setTextContent("ENCRYPTED");
                }

                // Save the updated XML back to the file
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                DOMSource domSource = new DOMSource(xmlDocument);
                StreamResult result = new StreamResult(new File(strFilePath));
                transformer.transform(domSource, result);
            } else if ("ENCRYPTED".equalsIgnoreCase(strPasswordType)) {
                strPassword = crypto.decrypt(strEncryptionKey, strPassword);
            } else {
                System.err.println("Error: Unknown password type");
                return null;
            }

            System.out.println("Decrypted password: " + strPassword);
            // Populate the parameters object
            AgencyBankingNavisionLocalParams localParams = new AgencyBankingNavisionLocalParams();
            LoggingLevel loggingLevel = LoggingLevel.DEBUG;
            try {
                loggingLevel = LoggingLevel.valueOf(strLoggingLevel);
            } catch (Exception ignore) {}

            localParams.setCoreBankingLoggingLevel(loggingLevel);
            localParams.setCoreBankingType(strType);
            localParams.setCoreBankingUrl(strURL);
            localParams.setCoreBankingUsername(strUsername);
            localParams.setCoreBankingPassword(strPassword);
            localParams.setCoreBankingDomain(strDomain);
            localParams.setCoreBankingWorkstation(strWorkstation);
            localParams.setCoreBankingSOAPActionPrefix(strSOAPActionPrefix);

            System.out.println("Successfully processed Navision configuration.");
            return localParams;

        } catch (FileNotFoundException e) {
            System.err.println("Error: Configuration file not found: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error: Unable to read file: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }





    public static Document format(Document theXMLDocument) {
        Document rVal = null;
        try{
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer;

            transformer = tf.newTransformer();

            StringWriter writer = new StringWriter();

            transformer.transform(new DOMSource(theXMLDocument), new StreamResult(writer));

            String xmlString = writer.getBuffer().toString();

            xmlString = prettyFormat(xmlString, "4");

            System.out.println("XML String formatted: "+xmlString);

            InputSource source = new InputSource(new StringReader(xmlString));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            rVal = builder.parse(source);
        } catch (Exception e){
            System.err.println(new Object() {}.getClass().getEnclosingMethod().getName()+"() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    public static String prettyFormat(String input, String indent) {
        Source xmlInput = new StreamSource(new StringReader(input));
        StringWriter stringWriter = new StringWriter();
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent);
            transformer.transform(xmlInput, new StreamResult(stringWriter));

            return stringWriter.toString().trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

