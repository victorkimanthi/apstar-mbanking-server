package ke.skyworld.mbanking.mbankingapi;

import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.enums.ReturnValue;
import ke.co.skyworld.smp.utility_items.logging.Log;
import ke.co.skyworld.smp.utility_items.security.CryptoInit;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.mapp.MAPPLocalParameters;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.msg.MSGLocalParameters;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.register.MemberRegisterResponse;
import ke.skyworld.lib.mbanking.register.RegisterConstants;
import ke.skyworld.lib.mbanking.register.RegisterProcessor;
import ke.skyworld.lib.mbanking.ussd.USSDLocalParameters;
import ke.skyworld.mbanking.cbs.ApStarCBSParams;
import ke.skyworld.mbanking.channelutils.EmailMessaging;
import ke.skyworld.mbanking.mappapi.MAPPAPI;
import ke.skyworld.mbanking.migrate_pins.MigratePINs;
import ke.skyworld.mbanking.nav.NavisionUtils;
import ke.skyworld.mbanking.nav.conn.NavisionAgencyConnectionManager;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.UUID;

import static ke.skyworld.mbanking.ussdapi.APIUtils.fnSendSMS;

public class MBankingAPI {
    static {
        System.setProperty("javax.xml.transform.TransformerFactory", "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
    }

    public void processOnStartup() {
        try {

            /*MigratePINs.run();
            System.exit(0);*/

            CryptoInit.init();

            while (Repository.setup() == ReturnValue.ERROR) {

                try {
                    System.out.println();
                    Log.error(MBankingAPI.class, "main", "FAILED TO CONNECT TO DATABASE. WILL RETRY AGAIN IN 10 SECONDS\n");
//                System.out.println("Failed to connect to database");
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            /*ConnectionManager.generateInitialConnections("CRM", "sky_crm_db", Constants.getDbHost(),
                    Constants.getDbPort(), Constants.getDbConnMetadata(), Constants.getDbUsername(), Constants.getDbPassword());*/

            ApStarCBSParams.initialize();
            NavisionAgencyConnectionManager.params = NavisionUtils.getAgencyBankingNavisionLocalParameters("live_agency_banking_navision_conf.xml");

//              System.out.println("simulate");
//                MAPPAPI.MAPPRequestSimulation();
//             System.out.println("simulate 2");


         /*   ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
            executor.scheduleAtFixedRate(() -> {
                ApStarCBS.callBC365Service("WITHDRAWAL");
                ApStarCBS.callBC365Service("MPESA_DEPOSITS");
                ApStarCBS.callBC365Service("LOAN_REPAYMENT");
                ApStarCBS.callBC365Service("MOBILE_LOAN_DISBURSEMENT");
                ApStarCBS.callBC365Service("INTERNAL");
            }, 3, 3, TimeUnit.SECONDS);
//*/
        } catch (Exception e) {
            System.err.println("MBankingAPI.processOnStartup() Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void processOnDBReconnect() {

    }

    public static String getValueFromLocalParams(MBankingConstants.ApplicationType theApplicationType, String thePath) {
        String rVal = "";
        try {
            String strConfigXML = "";
            if (theApplicationType == MBankingConstants.ApplicationType.PESA) {
                strConfigXML = PESALocalParameters.getClientXMLParameters();
            } else if (theApplicationType == MBankingConstants.ApplicationType.MSG) {
                strConfigXML = MSGLocalParameters.getClientXMLParameters();
            } else if (theApplicationType == MBankingConstants.ApplicationType.MAPP) {
                strConfigXML = MAPPLocalParameters.getClientXMLParameters();
            } else if (theApplicationType == MBankingConstants.ApplicationType.USSD) {
                strConfigXML = USSDLocalParameters.getClientXMLParameters();
            }

            InputSource source = new InputSource(new StringReader(strConfigXML));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);
            XPath configXPath = XPathFactory.newInstance().newXPath();

            rVal = configXPath.evaluate(thePath, xmlDocument, XPathConstants.STRING).toString();
        } catch (Exception e) {
            System.err.println("MBankingAPI.getValueFromLocalParams() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    public static NodeList getNodeListFromLocalParams(MBankingConstants.ApplicationType theApplicationType, String thePath) {
        NodeList rVal = null;
        try {
            String strConfigXML = "";
            if (theApplicationType == MBankingConstants.ApplicationType.PESA) {
                strConfigXML = PESALocalParameters.getClientXMLParameters();
            } else if (theApplicationType == MBankingConstants.ApplicationType.MSG) {
                strConfigXML = MSGLocalParameters.getClientXMLParameters();
            } else if (theApplicationType == MBankingConstants.ApplicationType.MAPP) {
                strConfigXML = MAPPLocalParameters.getClientXMLParameters();
            } else if (theApplicationType == MBankingConstants.ApplicationType.USSD) {
                strConfigXML = USSDLocalParameters.getClientXMLParameters();
            }

            InputSource source = new InputSource(new StringReader(strConfigXML));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);
            XPath configXPath = XPathFactory.newInstance().newXPath();

            rVal = ((NodeList) configXPath.evaluate(thePath, xmlDocument, XPathConstants.NODESET));
        } catch (Exception e) {
            System.err.println("PESADB.getValueFromLocalParams() ERROR : " + e.getMessage());
        }
        return rVal;
    }


    public static void processSendMSG(String theReceiverType, String theReceiver, String theMSG, String theCategory) {
        try {
            try {

                fnSendSMS(theReceiver, theMSG, "YES", MSGConstants.MSGMode.SAF, 203, theCategory,
                        "USSD", "MBANKING_SERVER",
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString());

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("MBankingAPI.processSendMSG() Error message: " + e.getMessage());
            }

        } finally {
            ;
        }
    }

    public static void processSendEmail(String theIdentifier, String theMSGSubject, String theMSG, String theCategory) {
        try {
            try {
                EmailMessaging.sendEmail(theIdentifier, theMSGSubject, theMSG, theCategory);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("MBankingAPI.processSendEmail() Error message: " + e.getMessage());
            }

        } finally {
            ;
        }
    }
}
