package ke.skyworld.mbanking.ussdapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ke.skyworld.lib.mbanking.core.MBankingConstants;
import ke.skyworld.lib.mbanking.core.MBankingUtils;
import ke.skyworld.lib.mbanking.core.MBankingXMLFactory;
import ke.skyworld.lib.mbanking.mapp.MAPPRequest;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.msg.MSGProcessor;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.register.MemberRegisterResponse;
import ke.skyworld.lib.mbanking.register.RegisterConstants;
import ke.skyworld.lib.mbanking.register.RegisterProcessor;
import ke.skyworld.lib.mbanking.ussd.USSDRequest;
import ke.skyworld.lib.mbanking.utils.Crypto;
import ke.skyworld.mbanking.mbankingapi.MBankingAPI;
import ke.skyworld.mbanking.nav.NavisionAgency;
import ke.skyworld.sp.manager.SPManager;
import ke.skyworld.sp.manager.SPManagerConstants;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.bind.DatatypeConverter;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

public class APIUtils {
    public APIUtils() {
    }

    public final static long ONE_SECOND = 1000;
    public final static long SECONDS = 60;

    public final static long ONE_MINUTE = ONE_SECOND * 60;
    public final static long MINUTES = 60;

    public final static long ONE_HOUR = ONE_MINUTE * 60;
    public final static long HOURS = 24;

    public final static long ONE_DAY = ONE_HOUR * 24;

    public static String ENCRYPTION_KEY = "6l04zjBa*iuGSv6l(2akwfqA";
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";

    public static Object toHashMap(String objStr, TypeReference T) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map map = new HashMap();
        try {
            map = objectMapper.readValue(objStr, T);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    public static String serialize(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }


    public static HashMap<String, String[]> getXmlStringV2(String strLoansXML) {

        HashMap<String, String[]> loans = new HashMap<>();

        try {
            InputSource source = new InputSource(new StringReader(strLoansXML));
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            Document xmlDocument = builder.parse(source);
            XPath configXPath = XPathFactory.newInstance().newXPath();

            NodeList nlLoans = ((NodeList) configXPath
                    .evaluate("/Loans", xmlDocument, XPathConstants.NODESET))
                    .item(0).getChildNodes();

            for (int i = 0; i < nlLoans.getLength(); i++) {
                NodeList nlLoan = ((NodeList) configXPath
                        .evaluate("Product", nlLoans, XPathConstants.NODESET))
                        .item(i).getChildNodes();

                loans.put(nlLoan.item(2).getTextContent(),
                        new String[]{
                                nlLoan.item(0).getTextContent(),
                                nlLoan.item(1).getTextContent(),
                                nlLoan.item(3).getTextContent()
                        });
            }
        } catch (ParserConfigurationException | IOException | XPathExpressionException | SAXException e) {
            e.printStackTrace();
        }
        return loans;
    }

    /*public static String sanitizePhoneNumber(String thePhoneNumber){
        thePhoneNumber = thePhoneNumber.trim();
        try {
            if(thePhoneNumber.startsWith("+")){
                thePhoneNumber = thePhoneNumber.replaceFirst("^\\+", "");
            }

            if(thePhoneNumber.matches("^2547\\d{8}$")){
                return thePhoneNumber;
            }

            if(thePhoneNumber.matches("^07\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^0", "254");
            }

            if(thePhoneNumber.matches("^7\\d{8}$")){
                return "254"+thePhoneNumber;
            }

            return "INVALID MOBILE NUMBER";
        }catch (Exception e){
            e.printStackTrace();
            return e.getMessage();
        }
    }*/

    public static String sanitizePhoneNumber(String thePhoneNumber) {
        thePhoneNumber = thePhoneNumber.replaceAll("\\s", "");
        thePhoneNumber = thePhoneNumber.replaceFirst("^\\+", "");
        try {
            if (thePhoneNumber.startsWith("+")) {
                thePhoneNumber = thePhoneNumber.replaceFirst("^\\+", "");
            }

            if (thePhoneNumber.matches("^2547\\d{8}$") || thePhoneNumber.matches("^2541\\d{8}$")) {
                return thePhoneNumber;
            }

            if (thePhoneNumber.matches("^07\\d{8}$") || thePhoneNumber.matches("^01\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^0", "254");
            }

            if (thePhoneNumber.matches("^7\\d{8}$") || thePhoneNumber.matches("^1\\d{8}$")) {
                return "254" + thePhoneNumber;
            }

            if (thePhoneNumber.matches("^25407\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^25407", "2547");
            }

            if (thePhoneNumber.matches("^25401\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^25401", "2541");
            }

            if (thePhoneNumber.matches("^254\\+254\\d{9}$")) {
                return thePhoneNumber.replaceFirst("^254\\+254", "254");
            }

            if (thePhoneNumber.matches("^254254\\d{9}$")) {
                return thePhoneNumber.replaceFirst("^254254", "254");
            }

            if (thePhoneNumber.matches("^254\\+25401\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^254\\+25401", "2541");
            }

            if (thePhoneNumber.matches("^254\\+25407\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^254\\+25407", "2547");
            }

            if (thePhoneNumber.matches("^25425401\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^25425401", "2541");
            }

            if (thePhoneNumber.matches("^25425407\\d{8}$")) {
                return thePhoneNumber.replaceFirst("^25425407", "2547");
            }
            return "INVALID_MOBILE_NUMBER";
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    /*
    NAV Specific Function
    public static void hashPINsOnNAV() {
        try {
            String strClearTextPINXML = Navision.getPort().getUnhashedPINs();

            //System.out.println(strClearTextPINXML);

            if (!strClearTextPINXML.equals("ERROR")) {
                InputSource source = new InputSource(new StringReader(strClearTextPINXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                NodeList ndAccounts = ((NodeList) configXPath.evaluate("/ACCOUNTS", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();

                double lnStartTime = (double) System.currentTimeMillis();
                for (int i = 0; i < ndAccounts.getLength(); i++) {
                    String strPhoneNumber = ndAccounts.item(i).getAttributes().getNamedItem("PHONE_NUMBER").getTextContent();
                    String strAccountNumber = ndAccounts.item(i).getAttributes().getNamedItem("ACCOUNT_NUMBER").getTextContent();
                    String strPIN = ndAccounts.item(i).getAttributes().getNamedItem("PIN").getTextContent();

                    System.out.println("Count: " + (i + 1));
                    System.out.println("Account Number: " + strAccountNumber);
                    System.out.println("Phone Number: " + strPhoneNumber);
                    System.out.println("Cleartext PIN: " + strPIN);

                    String strHashedPIN = hashPIN(strPIN);
                    //System.out.println("Hashed PIN: " + strHashedPIN);

                    String strResult = Navision.getPort().setHashedPIN(strAccountNumber, strPhoneNumber, strHashedPIN);
                    //System.out.println("RESULT: " + strResult + "\n");
                }
                double lnEndTime = (double) System.currentTimeMillis();
                double lnTimeTaken = (lnEndTime - lnStartTime) / 1000;
                //System.out.println("Finished Task In " + lnTimeTaken + " Seconds");
            }
        } catch (Exception e) {
            System.err.println("USSDAPI.hashPINsOnNAV() ERROR : " + e.getMessage());
            //hashPINsOnNAV();
        } finally {
        }
    }
    */
    public static String millisToLongDHMS(long duration) {
        StringBuffer res = new StringBuffer();
        long temp = 0;
        boolean hasDay = false;
        boolean hasHasHour = false;
        boolean hasMinute = false;
        if (duration >= ONE_SECOND) {
            temp = duration / ONE_DAY;
            if (temp > 0) {
                hasDay = true;
                duration -= temp * ONE_DAY;
                res.append(temp).append(" day").append(temp > 1 ? "s" : "")
                        .append(duration >= ONE_MINUTE ? ", " : "");
            }

            temp = duration / ONE_HOUR;
            if (temp > 0) {
                hasHasHour = true;
                duration -= temp * ONE_HOUR;
                res.append(temp).append(" hour").append(temp > 1 ? "s" : "")
                        .append(duration >= ONE_MINUTE ? ", " : "");
            }

            if (!hasDay) {
                temp = duration / ONE_MINUTE;
                if (temp > 0) {
                    hasMinute = true;
                    duration -= temp * ONE_MINUTE;
                    res.append(temp).append(" minute").append(temp > 1 ? "s" : "");
                }
            }

            /*if (!res.toString().equals("") && duration >= ONE_SECOND) {
                res.append(" and ");
            }

            temp = duration / ONE_SECOND;
            if (temp > 0) {
                res.append(temp).append(" second").append(temp > 1 ? "s" : "");
            }*/
            return res.toString();
        } else {
            return "0 second";
        }
    }

    public static Date convertDateStringToDate(String date) {
        SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat(SPManagerConstants.DEFAULT_DATE_TIME_FORMAT);
        try {
            return (date == null) ? null : simpleDateFormat.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String getPrettyDateTimeDifference(Date startDate, Date endDate) {
        String seconds = "second";
        String minutes = "minute";
        String hours = "hour";
        String days = "day";
        //milliseconds
        long different = endDate.getTime() - startDate.getTime();
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long hoursInMilli = minutesInMilli * 60;
        long daysInMilli = hoursInMilli * 24;
        long elapsedDays = different / daysInMilli;
        different = different % daysInMilli;
        long elapsedHours = different / hoursInMilli;
        different = different % hoursInMilli;
        long elapsedMinutes = different / minutesInMilli;
        different = different % minutesInMilli;
        long elapsedSeconds = different / secondsInMilli;
        if (elapsedSeconds > 1) seconds = seconds + "s";
        if (elapsedMinutes > 1) minutes = minutes + "s";
        if (elapsedHours > 1) hours = hours + "s";
        if (elapsedDays > 1) days = days + "s";
        if (elapsedDays <= 0) {
            if (elapsedHours <= 0) {
                if (elapsedMinutes <= 0) {
                    if (elapsedSeconds <= 0) {
                        return "3 seconds";
                    } else {
                        return String.format("%d " + seconds + "%n", elapsedSeconds);
                    }
                } else {
                    if (elapsedSeconds > 0) {
                        return String.format("%d " + minutes + ", %d " + seconds + "%n",
                                elapsedMinutes, elapsedSeconds);
                    } else {
                        return String.format("%d " + minutes + "%n", elapsedMinutes);
                    }
                }
            } else {
                if (elapsedMinutes > 0) {
                    return String.format("%d " + hours + ", %d " + minutes + "%n",
                            elapsedHours, elapsedMinutes);
                } else {
                    return String.format("%d " + hours + "%n", elapsedHours);
                }
            }
        } else {
            if (elapsedHours > 0) {
                return String.format("%d " + days + ", %d " + hours + "%n",
                        elapsedDays, elapsedHours);
            } else {
                return String.format("%d " + days + "%n", elapsedDays);
            }
        }
    }


    public static String getPrettyDateTimeDifferenceRoundedUp(Date startDate, Date endDate) {
        //milliseconds
        long different = endDate.getTime() - startDate.getTime();
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long hoursInMilli = minutesInMilli * 60;
        long daysInMilli = hoursInMilli * 24;
        long elapsedDays = different / daysInMilli;
        different = different % daysInMilli;
        long elapsedHours = different / hoursInMilli;
        different = different % hoursInMilli;
        long elapsedMinutes = different / minutesInMilli;
        different = different % minutesInMilli;
        long elapsedSeconds = different / secondsInMilli;
        if (elapsedDays > 0) {
            if (elapsedHours > 0) {
                elapsedDays += 1;
            }
            String days = (elapsedDays == 1) ? "DAY" : "DAYS";
            return String.format("%d " + days + "%n", elapsedDays);
        } else {
            //Days 0. Do for hours
            if (elapsedHours > 0) {
                if (elapsedMinutes > 0) {
                    elapsedHours += 1;
                }
                String hours = (elapsedHours == 1) ? "HOUR" : "HOURS";
                return String.format("%d " + hours + "%n", elapsedHours);
            } else {
                //Hours 0. Do for minutes
                if (elapsedMinutes > 0) {
                    if (elapsedSeconds > 0) {
                        elapsedMinutes += 1;
                    }
                    String minutes = (elapsedMinutes == 1) ? "MINUTE" : "MINUTES";
                    return String.format("%d " + minutes + "%n", elapsedMinutes);
                } else {
                    return "1 MINUTE";
                }
            }
        }
    }

    public static String titleCase(String inputString) {
        if (StringUtils.isBlank(inputString)) {
            return "";
        }

        if (StringUtils.length(inputString) == 1) {
            return inputString.toUpperCase();
        }

        StringBuffer resultPlaceHolder = new StringBuffer(inputString.length());

        Stream.of(inputString.split(" ")).forEach(stringPart ->
        {
            if (stringPart.length() > 1)
                resultPlaceHolder.append(stringPart.substring(0, 1)
                                .toUpperCase())
                        .append(stringPart.substring(1)
                                .toLowerCase());
            else
                resultPlaceHolder.append(stringPart.toUpperCase());

            resultPlaceHolder.append(" ");
        });
        return StringUtils.trim(resultPlaceHolder.toString());
    }

    public static String fnModifyMAPPSessionID(MAPPRequest theMAPPRequest) {
        long strSessionID = theMAPPRequest.getSessionID();
        long strSessionSequence = theMAPPRequest.getSequence();
        try {
            return MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return "M" + theMAPPRequest.getSessionID() + "S" + theMAPPRequest.getSequence();
    }

    public static String fnModifyMAPPSessionIDBkp(String theSessionID) {
        try {
            ZonedDateTime nowZoned = ZonedDateTime.now();
            Instant midnight = nowZoned.toLocalDate().atStartOfDay(nowZoned.getZone()).toInstant();
            Duration duration = Duration.between(midnight, Instant.now());
            long seconds = duration.getSeconds();
            return theSessionID + "_" + String.format("%05d", Integer.parseInt(String.valueOf(seconds)));
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return theSessionID;
    }

    public static boolean fnCreateFileFromBase64(String theBase64Data, String theImagePath) {
        boolean rVal = false;
        try {
            // byte[] data = DatatypeConverter.parseBase64Binary(theBase64Data);
            byte[] data = DatatypeConverter.parseBase64Binary(theBase64Data);

            File file = new File(theImagePath);
            try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
                outputStream.write(data);
                rVal = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return rVal;
    }

    public static int fnSendSMS(String theReceiver, String theMessage, String theCharge, MSGConstants.MSGMode theMode, int thePriority, String theCategory, String theRequestApplication, String theSourceApplication, String theSessionID, String theCorrelationID) {
        try {
            String strProductID = MBankingAPI.getValueFromLocalParams(MBankingConstants.ApplicationType.MSG, "OTHER_DETAILS/CUSTOM_PARAMETERS/SMS/MT/PRODUCT_ID");
            long lnProductID = Long.parseLong(strProductID);
            String strSender = MBankingAPI.getValueFromLocalParams(MBankingConstants.ApplicationType.MSG, "OTHER_DETAILS/CUSTOM_PARAMETERS/SMS/MT/SENDER");
            String strCommand = "BulkSMS";
            MSGConstants.Sensitivity theSensitivity = MSGConstants.Sensitivity.NORMAL;


            Thread worker = new Thread(() -> {
                MSGProcessor.sendMSG(
                        lnProductID,
                        strSender,
                        theReceiver,
                        theMessage,
                        strCommand,
                        theSensitivity,
                        theCategory,
                        thePriority,
                        theCharge,
                        theMode,
                        theRequestApplication,
                        theCorrelationID,
                        theSourceApplication,
                        theSessionID
                );
            });
            worker.start();
            return 1;
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return 0;
    }

    public static class OTP {
        private int length;
        private int ttl;
        private String id;
        private String value;
        private boolean enabled;

        public OTP(int length, int ttl, String id, String value, boolean enabled) {
            this.length = length;
            this.ttl = ttl;
            this.value = value;
            this.id = id;
            this.enabled = enabled;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public int getTtl() {
            return ttl;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }


    public static class ServiceProviderAccount {
        private String strProviderCode;
        private String strProviderAccountCode;
        private String strProviderAccountName;
        private String strProviderAccountType;
        private String strProviderAccountTypeTag;
        private String strProviderAccountIdentifier;
        private String strProviderAccountLongTag;
        private String strProviderBranchCode;
        private String dblMinTransactionAmount;
        private String dblMaxTransactionAmount;

        public ServiceProviderAccount(String theProviderCode, String theProviderAccountCode, String theProviderAccountName, String theProviderAccountType, String theProviderAccountTypeTag, String theProviderAccountIdentifier, String theProviderAccountLongTag, String theProviderBranchCode, String theMinTransactionAmount, String theMaxTransactionAmount) {
            this.strProviderCode = theProviderCode;
            this.strProviderAccountCode = theProviderAccountCode;
            this.strProviderAccountName = theProviderAccountName;
            this.strProviderAccountType = theProviderAccountType;
            this.strProviderAccountTypeTag = theProviderAccountTypeTag;
            this.strProviderAccountIdentifier = theProviderAccountIdentifier;
            this.strProviderAccountLongTag = theProviderAccountLongTag;
            this.strProviderBranchCode = theProviderBranchCode;
            this.dblMinTransactionAmount = theMinTransactionAmount;
            this.dblMaxTransactionAmount = theMaxTransactionAmount;
        }

        public String getProviderCode() {
            return strProviderCode;
        }

        public void setProviderCode(String strProviderCode) {
            this.strProviderCode = strProviderCode;
        }

        public String getProviderAccountCode() {
            return strProviderAccountCode;
        }

        public void setProviderAccountCode(String strProviderAccountCode) {
            this.strProviderAccountCode = strProviderAccountCode;
        }

        public String getProviderAccountName() {
            return strProviderAccountName;
        }

        public void setProviderAccountName(String strProviderAccountName) {
            this.strProviderAccountName = strProviderAccountName;
        }

        public String getProviderAccountType() {
            return strProviderAccountType;
        }

        public String getProviderAccountTypeTag() {
            return strProviderAccountTypeTag;
        }

        public void setProviderAccountType(String strProviderAccountType) {
            this.strProviderAccountType = strProviderAccountType;
        }

        public String getProviderAccountIdentifier() {
            return strProviderAccountIdentifier;
        }

        public void setProviderAccountIdentifier(String strProviderAccountIdentifier) {
            this.strProviderAccountIdentifier = strProviderAccountIdentifier;
        }

        public String getProviderAccountLongTag() {
            return strProviderAccountLongTag;
        }

        public void setProviderAccountLongTag(String strProviderAccountLongTag) {
            this.strProviderAccountLongTag = strProviderAccountLongTag;
        }

        public String getProviderBranchCode() {
            return strProviderBranchCode;
        }

        public void setProviderBranchCode(String strProviderBranchCode) {
            this.strProviderBranchCode = strProviderBranchCode;
        }

        public String getMinTransactionAmount() {
            return dblMinTransactionAmount;
        }

        public void setMinTransactionAmount(String dblMinTransactionAmount) {
            this.dblMinTransactionAmount = dblMinTransactionAmount;
        }

        public String getMaxTransactionAmount() {
            return dblMaxTransactionAmount;
        }

        public void setMaxTransactionAmount(String dblMaxTransactionAmount) {
            this.dblMaxTransactionAmount = dblMaxTransactionAmount;
        }
    }

    public static LinkedList<ServiceProviderAccount> getSPAccounts(SPManagerConstants.ProviderAccountType theProviderAccountType) {
        LinkedList<ServiceProviderAccount> rVal = new LinkedList<ServiceProviderAccount>();
        SPManager spManager;
        try {
            String strIntegritySecret = PESALocalParameters.getIntegritySecret();
            spManager = new SPManager(strIntegritySecret);
            LinkedList<LinkedHashMap<String, String>> llHsB2CAccounts = spManager.getB2BCapabilitySPAccounts(theProviderAccountType);
            for (LinkedHashMap<String, String> lhsB2CAccount : llHsB2CAccounts) {
                String strProviderCode = lhsB2CAccount.get("provider_code");
                String strProviderAccountCode = lhsB2CAccount.get("provider_account_code");
                String strProviderAccountName = lhsB2CAccount.get("provider_account_name");
                String strProviderAccountType = lhsB2CAccount.get("provider_account_type");
                String strProviderAccountTypeTag = lhsB2CAccount.get("provider_account_type_tag");
                String strProviderAccountIdentifier = lhsB2CAccount.get("provider_account_identifier");
                String strProviderAccountLongTag = lhsB2CAccount.get("provider_account_long_tag");
                String strProviderOtherDetails = lhsB2CAccount.get("provider_other_details");
                String dblMinTransactionAmount = lhsB2CAccount.get("min_transaction_amount");
                String dblMaxTransactionAmount = lhsB2CAccount.get("max_transaction_amount");

                String strProviderBranchCode = MBankingXMLFactory.getXPathValueFromXMLString("/OTHER_DETAILS/DATA/PROVIDER_ACCOUNT_DETAILS/BRANCH_CODE", strProviderOtherDetails);
                ServiceProviderAccount spaServiceProviderAccount = new ServiceProviderAccount(strProviderCode, strProviderAccountCode, strProviderAccountName, strProviderAccountType, strProviderAccountTypeTag, strProviderAccountIdentifier, strProviderAccountLongTag, strProviderBranchCode, dblMinTransactionAmount, dblMaxTransactionAmount);
                rVal.add(spaServiceProviderAccount);

            }
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        } finally {
            spManager = null;
        }

        return rVal;
    }

    public static class WithdrawalChannel {
        private String name;
        private String label;
        private String status;
        private boolean withdrawalToOtherNumber;

        public WithdrawalChannel(String name, String label, String status, boolean withdrawalToOtherNumber) {
            this.name = name;
            this.label = label;
            this.status = status;
            this.withdrawalToOtherNumber = withdrawalToOtherNumber;
        }

        public String getName() {
            return name;
        }

        public void setName(String theName) {
            this.name = theName;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String theLabel) {
            this.label = theLabel;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String theStatus) {
            this.status = theStatus;
        }

        public boolean hasWithdrawalToOtherNumberEnabled() {
            return withdrawalToOtherNumber;
        }

        public void setWithdrawalToOtherNumber(boolean theWithdrawalToOtherNumber) {
            this.withdrawalToOtherNumber = theWithdrawalToOtherNumber;
        }
    }

    public static LinkedList<WithdrawalChannel> getActiveWithdrawalChannels(MBankingConstants.ApplicationType applicationType) {
        LinkedList<WithdrawalChannel> rVal = new LinkedList<>();
        NodeList nlWithdrawalChannels;
        Node ndChannel;
        WithdrawalChannel withdrawalChannel;
        try {
            nlWithdrawalChannels = MBankingAPI.getNodeListFromLocalParams(applicationType, "/OTHER_DETAILS/CUSTOM_PARAMETERS/SERVICE_CONFIGS/CONFIGURATION/CASH_WITHDRAWAL/CHANNELS/CHANNEL");
            for (int i = 0; i < nlWithdrawalChannels.getLength(); i++) {
                ndChannel = nlWithdrawalChannels.item(i);

                if (ndChannel != null && ndChannel.getNodeType() == Node.ELEMENT_NODE) {
                    String strName = ndChannel.getAttributes().getNamedItem("NAME").getTextContent();
                    String strLabel = ndChannel.getAttributes().getNamedItem("LABEL").getTextContent();
                    String strStatus = ndChannel.getAttributes().getNamedItem("STATUS").getTextContent();
                    boolean blWithdrawalOtherNumberEnabled = ndChannel.getAttributes().getNamedItem("WITHDRAW_TO_OTHER_NUMBER").getTextContent().equals("ACTIVE");
                    if (strStatus.equalsIgnoreCase("ACTIVE")) {
                        withdrawalChannel = new WithdrawalChannel(strName, strLabel, strStatus, blWithdrawalOtherNumberEnabled);
                        rVal.add(withdrawalChannel);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            nlWithdrawalChannels = null;
            ndChannel = null;
            withdrawalChannel = null;
        }
        return rVal;
    }

    /*    public static String fnModifyMAPPSessionID(MAPPRequest theMAPPRequest) {
            long strSessionID = theMAPPRequest.getSessionID();
            long strSessionSequence = theMAPPRequest.getSequence();
            try {
                return MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.MAPP, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
            } catch (Exception e) {
                System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
                }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            }
            return "M" + theMAPPRequest.getSessionID() + "S" + theMAPPRequest.getSequence();
        }*/
    public static String fnModifyUSSDSessionID(USSDRequest theUSSDRequest) {
        long strSessionID = theUSSDRequest.getUSSDSessionID();
        long strSessionSequence = theUSSDRequest.getSequence();
        try {
            return MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.USSD, theUSSDRequest.getUSSDSessionID(), theUSSDRequest.getSequence());
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return "U" + theUSSDRequest.getUSSDSessionID() + "S" + theUSSDRequest.getSequence();
    }


/*    public static HashMap<String, String[]> generateLastNMonthsDateRanges(int months) {
        HashMap<String, String[]> ranges = new HashMap<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (int i = 1; i <= months; i++) {
            int daysToSubtract = i * 30; // Approximate conversion of months to days
            LocalDate endDate = today.minusDays(daysToSubtract);
            LocalDate startDate = today.minusDays(daysToSubtract + 29); // 30-day range

            ranges.put(String.valueOf(i),
                    new String[]{startDate.format(formatter), endDate.format(formatter)});

            System.out.println("Last " + i + " month(s) : " + startDate.format(formatter) + " - " + endDate.format(formatter));
        }

        return ranges;
    }*/


    public static HashMap<String, String[]> generateLastNMonthsDateRanges(int months) {
        HashMap<String, String[]> ranges = new HashMap<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (int i = 1; i <= months; i++) {
            LocalDate endDate = today.minusMonths(i).withDayOfMonth(today.minusMonths(i).lengthOfMonth());
            LocalDate startDate = today;
            ranges.put(String.valueOf(i),
                    new String[]{startDate.format(formatter), endDate.format(formatter)});
            System.out.println("Last " + i + " month(s) : " + startDate.format(formatter) + " - " + endDate.format(formatter));

        }

        return ranges;
    }

    public static String fnModifyAGNTSessionID(MAPPRequest theMAPPRequest) {
        return MBankingUtils.generateTransactionIDFromSession(MBankingConstants.AppTransID.AGENCY, theMAPPRequest.getSessionID(), theMAPPRequest.getSequence());
    }
    public static String hashAgentPIN(String thePIN, String theUsername) {
        Crypto crypto = new Crypto();
        try {
            String strSalt = "kWr0v6grHgTkdP2BoUeRtvUeeHKRstsO8g2Y3oTioUZOzj4ll4d0j9T8dKPKtgoE";
            String strClearText = theUsername + strSalt + thePIN;
            String strHashed = crypto.hash("SHA-256", strClearText);
            strHashed = strHashed.toLowerCase();
            return strHashed;
        } catch (Exception e) {
            System.err.println("APIUtils.hashPIN(): ERROR " + e.getMessage());
        } finally {
            crypto = null;
        }
        return thePIN;
    }

    public static void hashAgencyPINsOnNAV() {
        try {
            //System.out.println("hashPINsOnNAV started");
            String strClearTextPINXML = NavisionAgency.getUnhashedPINs();

            //System.out.println(strClearTextPINXML);

            if (!strClearTextPINXML.equals("ERROR")) {
                InputSource source = new InputSource(new StringReader(strClearTextPINXML));
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document xmlDocument = builder.parse(source);
                XPath configXPath = XPathFactory.newInstance().newXPath();

                NodeList ndAccounts = ((NodeList) configXPath.evaluate("/ACCOUNTS", xmlDocument, XPathConstants.NODESET)).item(0).getChildNodes();

                double lnStartTime = (double) System.currentTimeMillis();
                for (int i = 0; i < ndAccounts.getLength(); i++) {
                    String strUsername = ndAccounts.item(i).getAttributes().getNamedItem("USERNAME").getTextContent();
                    String strAgentCode = ndAccounts.item(i).getAttributes().getNamedItem("AGENT_CODE").getTextContent();
                    String strPIN = ndAccounts.item(i).getAttributes().getNamedItem("PASSWORD").getTextContent();

                    System.out.println("PIN Hash Count: " + (i + 1));
                    //System.out.println("Account Number: " + strAccountNumber);
                    //System.out.println("Phone Number: " + strPhoneNumber);
                    //System.out.println("Cleartext PIN: " + strPIN);

                    String strHashedPIN = hashAgentPIN(strPIN, strUsername);
                    //System.out.println("Hashed PIN: " + strHashedPIN);

                    String strResult = NavisionAgency.setHashedPIN(strUsername, strAgentCode, strHashedPIN);
                    //System.out.println("RESULT: " + strResult + "\n");
                }
                double lnEndTime = (double) System.currentTimeMillis();
                double lnTimeTaken = (lnEndTime - lnStartTime) / 1000;
                //System.out.println("Finished Task In " + lnTimeTaken + " Seconds");
            }
        } catch (Exception e) {
            System.err.println("USSDAPI.hashAgencyPINsOnNAV() ERROR : " + e.getMessage());
            //hashPINsOnNAV();
        } finally {
        }
    }

    public static XMLGregorianCalendar stringToXMLGregorianCalendar(String strDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        try {
            Date date = dateFormat.parse(strDate);

            GregorianCalendar gregorianCalendar = new GregorianCalendar();
            gregorianCalendar.setTime(date);

            // Convert GregorianCalendar to XMLGregorianCalendar
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar);

        } catch (Exception e) {
            e.printStackTrace(); // Handle the exception as needed
            return null;
        }
    }


    public static Set<String> getAllowedNumbers() {
        Set<String> allowedNumbers = new HashSet<>();
        allowedNumbers.add("254726589392");
        allowedNumbers.add("254713000249");
        allowedNumbers.add("254716304210");
        //allowedNumbers.add("254729692224");
        allowedNumbers.add("254114041681");
        allowedNumbers.add("254722378923");
        allowedNumbers.add("254723782649");

        allowedNumbers.add("254720828221");
        allowedNumbers.add("254722497901");
        allowedNumbers.add("254728302114");
        allowedNumbers.add("254721540017");
        allowedNumbers.add("254722342942");
        allowedNumbers.add("254720839508");
        allowedNumbers.add("254721779193");
        allowedNumbers.add("254722830892");
        allowedNumbers.add("254727995183");
        allowedNumbers.add("254757321307");
        allowedNumbers.add("254722521242");
        allowedNumbers.add("254724177836");
        allowedNumbers.add("254722491904");
        allowedNumbers.add("254742088930");
        allowedNumbers.add("254722487619");
        allowedNumbers.add("254722301284");
        allowedNumbers.add("254714092166");
        allowedNumbers.add("254710334809");
        allowedNumbers.add("254720733095");
        allowedNumbers.add("254722943341");
        allowedNumbers.add("254722608634");
        allowedNumbers.add("254701164209");
        allowedNumbers.add("254724918418");
        allowedNumbers.add("254721350430");
        allowedNumbers.add("254729529539");
        allowedNumbers.add("254722358705");
        allowedNumbers.add("254720995689");
        allowedNumbers.add("254721479694");
        allowedNumbers.add("254721267727");
        allowedNumbers.add("254721525126");
        allowedNumbers.add("254714386832");
        allowedNumbers.add("254720399224");
        allowedNumbers.add("254721860313");
        allowedNumbers.add("254723697894");
        allowedNumbers.add("254726755474");
        allowedNumbers.add("254720203608");
        allowedNumbers.add("254722342959");
        allowedNumbers.add("254723428837");
        allowedNumbers.add("25720765230");
        allowedNumbers.add("254724856147");
        allowedNumbers.add("254711874939");
        allowedNumbers.add("254721560214");
        allowedNumbers.add("254718449607");
        allowedNumbers.add("254722432269");
        allowedNumbers.add("254723239891");
        allowedNumbers.add("254720564849");
        allowedNumbers.add("254726790704");
        allowedNumbers.add("254726758022");
        allowedNumbers.add("254722449205");
        allowedNumbers.add("254721540131");
        allowedNumbers.add("254700864918");
        allowedNumbers.add("254722714483");
        allowedNumbers.add("254722832021");
        allowedNumbers.add("254724740560");
        allowedNumbers.add("254724486381");
        allowedNumbers.add("254722898555");
        allowedNumbers.add("254728366222");
        allowedNumbers.add("254722824025");
        allowedNumbers.add("254722346758");
        allowedNumbers.add("254713978485");
        allowedNumbers.add("254722599564");
        allowedNumbers.add("254706422601");
        allowedNumbers.add("254726407974");
        allowedNumbers.add("254711980120");
        allowedNumbers.add("254725366538");
        allowedNumbers.add("254710450613");
        allowedNumbers.add("254722730574");
        allowedNumbers.add("254707663421");
        allowedNumbers.add("254723347332");
        allowedNumbers.add("254728526987");
        allowedNumbers.add("254721308531");
        allowedNumbers.add("254714701313");
        allowedNumbers.add("254721761135");
        allowedNumbers.add("254725519070");
        allowedNumbers.add("254721583550");
        allowedNumbers.add("254721757583");
        allowedNumbers.add("254721364308");
        allowedNumbers.add("254715220550");
        allowedNumbers.add("254720562712");
        allowedNumbers.add("254725946858");
        allowedNumbers.add("254710791469");
        allowedNumbers.add("254727229342");
        allowedNumbers.add("254717887127");
        allowedNumbers.add("254700376560");
        allowedNumbers.add("254741295777");
        allowedNumbers.add("254725711760");
        allowedNumbers.add("254711978843");
        allowedNumbers.add("254723553596");
        allowedNumbers.add("254711225263");
        allowedNumbers.add("254724249545");
        allowedNumbers.add("254720611130");
        allowedNumbers.add("254721387926");
        allowedNumbers.add("254728432445");
        allowedNumbers.add("254729634620");
        allowedNumbers.add("254707378413");
        allowedNumbers.add("254718908007");
        allowedNumbers.add("254725342177");
        allowedNumbers.add("254728130244");
        allowedNumbers.add("254721790405");
        allowedNumbers.add("254794557811");
        allowedNumbers.add("254726958265");
        allowedNumbers.add("254725437992");
        allowedNumbers.add("254710656477");
        allowedNumbers.add("254708610220");
        allowedNumbers.add("254728590760");
        allowedNumbers.add("254720093323");
        allowedNumbers.add("254711942385");
        allowedNumbers.add("254729168306");
        allowedNumbers.add("254724822219");
        allowedNumbers.add("254702616190");
        allowedNumbers.add("254701026766");
        allowedNumbers.add("254716795857");
        allowedNumbers.add("254713500543");
        allowedNumbers.add("254708808278");
        allowedNumbers.add("254703954724");
        allowedNumbers.add("254746176137");
        allowedNumbers.add("254740479238");
        allowedNumbers.add("254717321317");
        allowedNumbers.add("254729821925");
        allowedNumbers.add("254724572277");
        allowedNumbers.add("254757984332");
        allowedNumbers.add("254726143766");
        allowedNumbers.add("254728678260");
        allowedNumbers.add("254717630092");
        allowedNumbers.add("254721945955");
        allowedNumbers.add("254722268203");
        allowedNumbers.add("254728090118");
        allowedNumbers.add("254720416494");
        allowedNumbers.add("254725789388");
        allowedNumbers.add("254721973681");
        allowedNumbers.add("254710730945");
        allowedNumbers.add("254722161109");
        allowedNumbers.add("254723142176");
        allowedNumbers.add("254702942428");
        allowedNumbers.add("254715557963");
        allowedNumbers.add("254720248820");
        allowedNumbers.add("254708243139");
        allowedNumbers.add("254701785905");
        allowedNumbers.add("254725265467");
        allowedNumbers.add("254708912105");
        allowedNumbers.add("254715532010");
        allowedNumbers.add("254710105235");
        allowedNumbers.add("254115053582");
        allowedNumbers.add("254728173682");
        allowedNumbers.add("254722258313");
        allowedNumbers.add("254798672028");
        allowedNumbers.add("254710805311");
        allowedNumbers.add("254721759732");
        allowedNumbers.add("254728814964");
        allowedNumbers.add("254716191860");
        allowedNumbers.add("254701590783");
        allowedNumbers.add("254741047815");
        allowedNumbers.add("254702742408");
        allowedNumbers.add("254712354989");
        allowedNumbers.add("254721584255");
        allowedNumbers.add("254722248593");
        allowedNumbers.add("254707779056");
        allowedNumbers.add("254721912744");
        allowedNumbers.add("254724216523");
        allowedNumbers.add("254768461607");
        allowedNumbers.add("254711372976");
        allowedNumbers.add("254701645750");
        allowedNumbers.add("254719446871");
        allowedNumbers.add("254729675150");
        allowedNumbers.add("254719871205");
        allowedNumbers.add("254729566788");
        allowedNumbers.add("254111937095");
        allowedNumbers.add("254742104310");
        allowedNumbers.add("254726716309");
        allowedNumbers.add("254798430065");
        allowedNumbers.add("254796028451");
        allowedNumbers.add("254720048160");
        allowedNumbers.add("254706395939");
        allowedNumbers.add("254708288252");
        allowedNumbers.add("254717807324");
        allowedNumbers.add("254793705982");
        allowedNumbers.add("254796024128");
        allowedNumbers.add("254795988856");
        allowedNumbers.add("254726252664");
        allowedNumbers.add("254717006799");
        allowedNumbers.add("254799003656");
        allowedNumbers.add("254711295765");
        allowedNumbers.add("254715190785");
        allowedNumbers.add("254721862224");
        allowedNumbers.add("254111929222");
        allowedNumbers.add("254703419568");
        allowedNumbers.add("254714462527");
        allowedNumbers.add("254723908730");
        allowedNumbers.add("254726277902");
        allowedNumbers.add("254708092727");
        allowedNumbers.add("254715004853");
        allowedNumbers.add("254724574117");
        allowedNumbers.add("254795520992");
        allowedNumbers.add("254790756577");
        allowedNumbers.add("254720474193");
        allowedNumbers.add("254721490419");
        allowedNumbers.add("254797245624");
        allowedNumbers.add("254703301964");
        allowedNumbers.add("254723445287");
        allowedNumbers.add("254722563243");
        allowedNumbers.add("254712673204");
        allowedNumbers.add("254714512996");
        allowedNumbers.add("254700013756");
        allowedNumbers.add("254716521770");
        allowedNumbers.add("254758788714");
        allowedNumbers.add("254716691143");
        allowedNumbers.add("254728283902");
        allowedNumbers.add("254721371546");
        allowedNumbers.add("254702729288");
        allowedNumbers.add("254705389927");
        allowedNumbers.add("254716659937");
        allowedNumbers.add("254701898476");
        allowedNumbers.add("254717198126");
        allowedNumbers.add("254724011380");
        allowedNumbers.add("254790050179");
        allowedNumbers.add("254722440062");
        allowedNumbers.add("254799561325");
        allowedNumbers.add("254722772204");
        allowedNumbers.add("254746366056");
        allowedNumbers.add("254729075227");
        allowedNumbers.add("254746809077");
        allowedNumbers.add("254714930477");
        allowedNumbers.add("254743317692");
        allowedNumbers.add("254724728100");
        allowedNumbers.add("254759155112");
        allowedNumbers.add("254701146800");
        allowedNumbers.add("254727412006");
        allowedNumbers.add("254707547164");
        allowedNumbers.add("254796249150");
        allowedNumbers.add("254740745565");
        allowedNumbers.add("254790175655");
        allowedNumbers.add("254742373450");
        allowedNumbers.add("254729692224");


        return allowedNumbers;

    }

    public static WithdrawalChannel getWithdrawalChannel(String theChannelName) {
        WithdrawalChannel rVal = null;
        LinkedList<WithdrawalChannel> lsActiveWithdrawalChannels;
        try {
            if (theChannelName != null) {
                lsActiveWithdrawalChannels = getActiveWithdrawalChannels(MBankingConstants.ApplicationType.USSD);
                for (WithdrawalChannel lsActiveWithdrawalChannel : lsActiveWithdrawalChannels) {
                    String strName = lsActiveWithdrawalChannel.getName();
                    if (strName.equalsIgnoreCase(theChannelName)) {
                        rVal = lsActiveWithdrawalChannel;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        } finally {
            lsActiveWithdrawalChannels = null;
        }
        return rVal;
    }

    public static LinkedList<HashMap<String, String>> getStatementPeriods(MBankingConstants.ApplicationType applicationType) {
        LinkedList<HashMap<String, String>> rVal = new LinkedList<>();
        NodeList nlStatementPeriods;
        Node ndChannel;
        try {
            nlStatementPeriods = MBankingAPI.getNodeListFromLocalParams(applicationType, "/OTHER_DETAILS/CUSTOM_PARAMETERS/SERVICE_CONFIGS/CONFIGURATION/ACCOUNT_STATEMENT/STATEMENT_PERIODS/PERIOD");
            for (int i = 0; i < nlStatementPeriods.getLength(); i++) {
                ndChannel = nlStatementPeriods.item(i);

                if (ndChannel != null && ndChannel.getNodeType() == Node.ELEMENT_NODE) {
                    String strStatus = ndChannel.getAttributes().getNamedItem("STATUS").getTextContent();

                    if (strStatus.equalsIgnoreCase("ACTIVE")) {
                        HashMap<String, String> hmStatementPeriods = new HashMap<String, String>();
                        for (int j = 0; j < ndChannel.getAttributes().getLength(); j++) {
                            String strName = ndChannel.getAttributes().item(j).getNodeName();
                            String strValue = ndChannel.getAttributes().item(j).getTextContent();
                            hmStatementPeriods.put(strName, strValue);
                        }
                        rVal.add(hmStatementPeriods);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        } finally {
            nlStatementPeriods = null;
            ndChannel = null;
        }
        return rVal;
    }

    public static MemberRegisterResponse fnCheckMemberRegister(String theMobileNumber, RegisterConstants.MemberRegisterIdentifierType theRegisterType) {
        MemberRegisterResponse registerResponse = null;
        try {
            registerResponse = RegisterProcessor.getMemberRegister(RegisterConstants.MemberRegisterIdentifierType.MSISDN, theMobileNumber, RegisterConstants.MemberRegisterType.WHITELIST);
        } catch (Exception e) {
            System.err.println(APIUtils.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
            e.printStackTrace();
        }
        return registerResponse;
    }

    public static Date getCurrentJavaUtilDateTime() {
        return new Date();
    }

    public static int convertToSeconds(int period, String periodUnit) {
        int converted = period;
        switch (periodUnit) {
            case "SECOND": {
                converted = period;
                break;
            }

            case "MINUTE": {
                converted = period * 60;
                break;
            }

            case "HOUR": {
                converted = period * 60 * 60;
                break;
            }

            case "DAY": {
                converted = period * 60 * 60 * 24;
                break;
            }

            case "WEEK": {
                converted = period * 60 * 60 * 24 * 7;
                break;
            }

            case "MONTH": {
                converted = period * 60 * 60 * 24 * 7 * 30;
                break;
            }

            case "YEAR": {
                converted = period * 60 * 60 * 24 * 7 * 30 * 12;
                break;
            }
        }

        return converted;
    }

    public static Date add(int period, int periodUnit) {
        Date now = getCurrentJavaUtilDateTime();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(periodUnit, period);
        return cal.getTime();
    }

    /**
     * @param date java.util.Date Object to convert to String
     * @return String value of Date
     * Format = yyyy-M-dd HH:mm:ss (2017-10-25 18:02:25)
     */
    public static String convertDateToDateString(Date date) {
        SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT);
        try {
            return simpleDateFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @return current UNIX timestamp
     * of type Long
     */
    public static long getCurrentUnixTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * @return current date and time with system default format
     * Type String
     */
    public static String getCurrentDateTime() {
        return new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT)
                .format(new java.sql.Date(getCurrentUnixTimestamp()));
    }

    /**
     * @param format Desired date or date & time format
     *               Type String
     * @return current date
     * Type String
     */
    public static String getCurrentDate(String format) {
        try {
            return new SimpleDateFormat(format)
                    .format(new java.sql.Date(
                            getCurrentUnixTimestamp()));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static String getCustomDuration(String strLoginActionValidDate) {
        if (strLoginActionValidDate == null || strLoginActionValidDate.isEmpty()) {
            return "";
        } else {
            try {
                Date loginActionValidDate = APIUtils.convertDateStringToDate(strLoginActionValidDate);
                Date currentDate = APIUtils.getCurrentJavaUtilDateTime();
                return APIUtils.getPrettyDateTimeDifferenceRoundedUp(currentDate, Objects.requireNonNull(loginActionValidDate));
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }
    }
}
