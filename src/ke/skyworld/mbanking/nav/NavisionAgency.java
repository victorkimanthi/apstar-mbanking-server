package ke.skyworld.mbanking.nav;

import java.util.HashMap;
import java.util.Map;

public class NavisionAgency extends NavisionHelper {


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
<Body>
    <AccountBalanceAsAt xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyMobile">
        <account>[string]</account>
        <asAt>[date]</asAt>
    </AccountBalanceAsAt>
</Body>
</Envelope>*/
    public static String getAccountBalanceAsAt(String account, String asAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("account", account);
        params.put("asAt", asAt);
        return sendAgency("AccountBalanceAsAt", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <AgentLogin xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
            <deviceIMEI>[string]</deviceIMEI>
            <deviceSerialNumber>[string]</deviceSerialNumber>
            <deviceMake>[string]</deviceMake>
            <deviceModel>[string]</deviceModel>
            <deviceProcessorID>[string]</deviceProcessorID>
            <softwareID>[string]</softwareID>
            <appVersionCode>[string]</appVersionCode>
            <appEnvironment>[string]</appEnvironment>
        </AgentLogin>
    </Body>
</Envelope>*/

    public static String agentLogin(String action, boolean indent, String agentUsername, String agentPassword, String deviceIMEI, String deviceSerialNumber, String deviceMake, String deviceModel, String deviceProcessorID, String softwareID, String appVersionCode, String appEnvironment) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        params.put("deviceIMEI", deviceIMEI);
        params.put("deviceSerialNumber", deviceSerialNumber);
        params.put("deviceMake", deviceMake);
        params.put("deviceModel", deviceModel);
        params.put("deviceProcessorID", deviceProcessorID);
        params.put("softwareID", softwareID);
        params.put("appVersionCode", appVersionCode);
        params.put("appEnvironment", appEnvironment);
        return sendAgency("AgentLogin", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <AgentMinistatement xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <entryCode>[string]</entryCode>
            <transactionID>[string]</transactionID>
            <startDate>[date]</startDate>
            <endDate>[date]</endDate>
            <statementAccount>[string]</statementAccount>
            <pin>[string]</pin>
            <agentUsername>[string]</agentUsername>
        </AgentMinistatement>
    </Body>
</Envelope>*/
    public static String agentMinistatement(String entryCode, String transactionID, String startDate, String endDate, String statementAccount, String pin, String agentUsername) {
        Map<String, Object> params = new HashMap<>();
        params.put("entryCode", entryCode);
        params.put("transactionID", transactionID);
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("statementAccount", statementAccount);
        params.put("pin", pin);
        params.put("agentUsername", agentUsername);
        return sendAgency("AgentMinistatement", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <AgentTransactions xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <entryCode>[string]</entryCode>
            <transactionID>[string]</transactionID>
            <startDate>[date]</startDate>
            <endDate>[date]</endDate>
            <pin>[string]</pin>
            <agentUsername>[string]</agentUsername>
            <transactionType>[string]</transactionType>
        </AgentTransactions>
    </Body>
</Envelope>*/

    public static String agentTransactions(String entryCode, String transactionID, String startDate, String endDate, String pin, String agentUsername, String transactionType) {
        Map<String, Object> params = new HashMap<>();
        params.put("entryCode", entryCode);
        params.put("transactionID", transactionID);
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("pin", pin);
        params.put("agentUsername", agentUsername);
        params.put("transactionType", transactionType);
        return sendAgency("AgentTransactions", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <ChangeAgentPassword xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentOldPassword>[string]</agentOldPassword>
            <agentNewPassword>[string]</agentNewPassword>
        </ChangeAgentPassword>
    </Body>
</Envelope>*/
    public static String changeAgentPassword(String action, boolean indent, String agentUsername, String agentOldPassword, String agentNewPassword) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentOldPassword", agentOldPassword);
        params.put("agentNewPassword", agentNewPassword);
        return sendAgency("ChangeAgentPassword", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <FormatName xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <memberNo>[string]</memberNo>
        </FormatName>
    </Body>
</Envelope>*/

    public static String formatName(String memberNo) {
        Map<String, Object> params = new HashMap<>();
        params.put("memberNo", memberNo);
        return sendAgency("FormatName", params);
    }

  /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetAccountBalance xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <account>[string]</account>
        </GetAccountBalance>
    </Body>
</Envelope>*/

    public static String getAccountBalance(String account) {
        Map<String, Object> params = new HashMap<>();
        params.put("account", account);
        return sendAgency("GetAccountBalance", params);
    }

/*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetAgentAccounts xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
            <accountType>[string]</accountType>
        </GetAgentAccounts>
    </Body>
</Envelope>*/

    public static String getAgentAccounts(String action, boolean indent, String agentUsername, String agentPassword, String accountType) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        params.put("accountType", accountType);
        return sendAgency("GetAgentAccounts", params);
    }

/*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetAgentData xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
        </GetAgentData>
    </Body>
</Envelope>*/

    public static String getAgentData(String action, boolean indent, String agentUsername, String agentPassword) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        return sendAgency("GetAgentData", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetAgentReport xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
            <fromDate>[date]</fromDate>
            <toDate>[date]</toDate>
        </GetAgentReport>
    </Body>
</Envelope>*/

    public static String getAgentReport(String action, boolean indent, String agentUsername, String agentPassword, String fromDate, String toDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        params.put("fromDate", fromDate);
        params.put("toDate", toDate);
        return sendAgency("GetAgentReport", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetAmountTransacted xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <transaction>[string]</transaction>
            <accountNo>[string]</accountNo>
            <date>[date]</date>
            <type>[int]</type>
        </GetAmountTransacted>
    </Body>
</Envelope>*/

    public static String getAmountTransacted(String transaction, String accountNo, String date, int type) {
        Map<String, Object> params = new HashMap<>();
        params.put("transaction", transaction);
        params.put("accountNo", accountNo);
        params.put("date", date);
        params.put("type", type);
        return sendAgency("GetAmountTransacted", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
        <Body>
            <GetChargeAmount xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
                <code>[string]</code>
                <chargeAmt>[decimal]</chargeAmt>
                <transAmount>[decimal]</transAmount>
                <vendorCharge>[decimal]</vendorCharge>
                <skyCharge>[decimal]</skyCharge>
            </GetChargeAmount>
        </Body>
    </Envelope>*/
    public static String getChargeAmount(String code, double chargeAmt, double transAmount, double vendorCharge, double skyCharge) {
        Map<String, Object> params = new HashMap<>();
        params.put("code", code);
        params.put("chargeAmt", chargeAmt);
        params.put("transAmount", transAmount);
        params.put("vendorCharge", vendorCharge);
        params.put("skyCharge", skyCharge);
        return sendAgency("GetChargeAmount", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetCustomerSearchOptions xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
        </GetCustomerSearchOptions>
    </Body>
</Envelope>*/
    public static String getCustomerSearchOptions(String action, boolean indent, String agentUsername, String agentPassword) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        return sendAgency("GetCustomerSearchOptions", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetCustomerSearchResult xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
            <customerSearchOption>[string]</customerSearchOption>
            <customerSearchData>[string]</customerSearchData>
            <accounttype>[string]</accounttype>
        </GetCustomerSearchResult>
    </Body>
</Envelope>*/
    public static String getCustomerSearchResult(String action, boolean indent, String agentUsername, String agentPassword, String customerSearchOption, String customerSearchData, String accounttype) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        params.put("customerSearchOption", customerSearchOption);
        params.put("customerSearchData", customerSearchData);
        params.put("accounttype", accounttype);
        return sendAgency("GetCustomerSearchResult", params);
    }

  /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetEmployersAndRegions xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency"/>
    </Body>
</Envelope>*/

    public static String getEmployersAndRegions() {
        Map<String, Object> params = new HashMap<>();
        return sendAgency("GetEmployersAndRegions", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
        <Body>
            <GetExciseDutyGL xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency"/>
        </Body>
    </Envelope>*/
    public static String getExciseDutyGL() {
        Map<String, Object> params = new HashMap<>();
        return sendAgency("GetExciseDutyGL", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
      <Body>
          <GetExciseRate xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency"/>
      </Body>
  </Envelope>*/
    public static String getExciseRate() {
        Map<String, Object> params = new HashMap<>();
        return sendAgency("GetExciseRate", params);
    }

/*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetTransactionLimits xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <transactionType>[string]</transactionType>
        </GetTransactionLimits>
    </Body>
</Envelope>*/

    public static String getTransactionLimits(String action, String transactionType) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("transactionType", transactionType);
        return sendAgency("GetTransactionLimits", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetUnhashedPINs xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency"/>
    </Body>
</Envelope>*/
    public static String getUnhashedPINs() {
        Map<String, Object> params = new HashMap<>();
        return sendAgency("GetUnhashedPINs", params);
    }

/*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetUserLoginAttemptAction xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <username>[string]</username>
            <type>[string]</type>
        </GetUserLoginAttemptAction>
    </Body>
</Envelope>*/

    public static String getUserLoginAttemptAction(String action, boolean indent, String username, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("username", username);
        params.put("type", type);
        return sendAgency("GetUserLoginAttemptAction", params);
    }

/*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetUserLoginAttemptCount xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <username>[string]</username>
            <type>[string]</type>
        </GetUserLoginAttemptCount>
    </Body>
</Envelope>*/

    public static String getUserLoginAttemptCount(String action, boolean indent, String username, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("username", username);
        params.put("type", type);
        return sendAgency("GetUserLoginAttemptCount", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetUserLoginAttemptExpiry xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <username>[string]</username>
            <type>[string]</type>
        </GetUserLoginAttemptExpiry>
    </Body>
</Envelope>*/

    public static String getUserLoginAttemptExpiry(String action, boolean indent, String username, String type) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("username", username);
        params.put("type", type);
        return sendAgency("GetUserLoginAttemptExpiry", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <GetVirtualMemberRegistrationImagesPath xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency"/>
    </Body>
</Envelope>*/
    public static String getVirtualMemberRegistrationImagesPath() {
        Map<String, Object> params = new HashMap<>();
        return sendAgency("GetVirtualMemberRegistrationImagesPath", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
        <Body>
            <PerformAgentTransaction xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
                <action>[string]</action>
                <indent>[boolean]</indent>
                <agentUsername>[string]</agentUsername>
                <agentPassword>[string]</agentPassword>
                <agentDebitNumber>[string]</agentDebitNumber>
                <debitAccountType>[string]</debitAccountType>
                <agentCreditNumber>[string]</agentCreditNumber>
                <transactionSessionID>[string]</transactionSessionID>
                <transactionAmount>[decimal]</transactionAmount>
                <transactionAmountStylized>[string]</transactionAmountStylized>
                <transactionStatementCount>[int]</transactionStatementCount>
                <transactionNarration>[string]</transactionNarration>
                <transactionDate>[string]</transactionDate>
                <transactionPrintReceipt>[boolean]</transactionPrintReceipt>
                <deviceIMEI>[string]</deviceIMEI>
                <deviceSerialNumber>[string]</deviceSerialNumber>
            </PerformAgentTransaction>
        </Body>
    </Envelope>*/
    public static String performAgentTransaction(String action, boolean indent, String agentUsername, String agentPassword, String agentDebitNumber, String debitAccountType, String agentCreditNumber, String transactionSessionID, double transactionAmount, String transactionAmountStylized, int transactionStatementCount, String transactionNarration, String transactionDate, boolean transactionPrintReceipt, String deviceIMEI, String deviceSerialNumber) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("agentUsername", agentUsername);
        params.put("agentPassword", agentPassword);
        params.put("agentDebitNumber", agentDebitNumber);
        params.put("debitAccountType", debitAccountType);
        params.put("agentCreditNumber", agentCreditNumber);
        params.put("transactionSessionID", transactionSessionID);
        params.put("transactionAmount", transactionAmount);
        params.put("transactionAmountStylized", transactionAmountStylized);
        params.put("transactionStatementCount", transactionStatementCount);
        params.put("transactionNarration", transactionNarration);
        params.put("transactionDate", transactionDate);
        params.put("transactionPrintReceipt", transactionPrintReceipt);
        params.put("deviceIMEI", deviceIMEI);
        params.put("deviceSerialNumber", deviceSerialNumber);
        return sendAgency("PerformAgentTransaction", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <PostAgentTransaction xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <trandsactionID>[string]</trandsactionID>
        </PostAgentTransaction>
    </Body>
</Envelope>*/
    public static String postAgentTransaction(String trandsactionID) {
        Map<String, Object> params = new HashMap<>();
        params.put("trandsactionID", trandsactionID);
        return sendAgency("PostAgentTransaction", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
        <Body>
            <PostMpesaTransaction xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
                <sessionID>[string]</sessionID>
            </PostMpesaTransaction>
        </Body>
    </Envelope>*/
    public static String postMpesaTransaction(String sessionID) {
        Map<String, Object> params = new HashMap<>();
        params.put("sessionID", sessionID);
        return sendAgency("PostMpesaTransaction", params);
    }

/*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <RegisterVirtualMember xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <name>[string]</name>
            <national_ID_Number>[string]</national_ID_Number>
            <mobile_Number>[string]</mobile_Number>
            <date_of_Birth>[date]</date_of_Birth>
            <entry_Number>[string]</entry_Number>
            <sessionID>[string]</sessionID>
            <dateFormatted>[string]</dateFormatted>
            <agentCode>[string]</agentCode>
            <postall_Address>[string]</postall_Address>
            <employment_Status>[string]</employment_Status>
            <employer>[string]</employer>
            <region>[string]</region>
            <transaction_Date>[date]</transaction_Date>
        </RegisterVirtualMember>
    </Body>
</Envelope>*/

    public static String registerVirtualMember(String action, boolean indent, String name, String national_ID_Number, String mobile_Number, String date_of_Birth, String entry_Number, String sessionID, String dateFormatted, String agentCode, String postall_Address, String employment_Status, String employer, String region, String transaction_Date) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("name", name);
        params.put("national_ID_Number", national_ID_Number);
        params.put("mobile_Number", mobile_Number);
        params.put("date_of_Birth", date_of_Birth);
        params.put("entry_Number", entry_Number);
        params.put("sessionID", sessionID);
        params.put("dateFormatted", dateFormatted);
        params.put("agentCode", agentCode);
        params.put("postall_Address", postall_Address);
        params.put("employment_Status", employment_Status);
        params.put("employer", employer);
        params.put("region", region);
        params.put("transaction_Date", transaction_Date);
        return sendAgency("RegisterVirtualMember", params);
    }

   /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <SendSmsWithID xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <source>[int]</source>
            <telephone>[string]</telephone>
            <textsms>[string]</textsms>
            <reference>[string]</reference>
            <accNo>[string]</accNo>
            <chargeable>[boolean]</chargeable>
            <priority>[int]</priority>
            <chargeMember>[boolean]</chargeMember>
            <requestApplication>[string]</requestApplication>
            <requestCorrelationID>[string]</requestCorrelationID>
            <sourceApplication>[string]</sourceApplication>
        </SendSmsWithID>
    </Body>
</Envelope>*/

    public static String sendSmsWithID(int source, String telephone, String textsms, String reference, String accNo, boolean chargeable, int priority, boolean chargeMember, String requestApplication, String requestCorrelationID, String sourceApplication) {
        Map<String, Object> params = new HashMap<>();
        params.put("source", source);
        params.put("telephone", telephone);
        params.put("textsms", textsms);
        params.put("reference", reference);
        params.put("accNo", accNo);
        params.put("chargeable", chargeable);
        params.put("priority", priority);
        params.put("chargeMember", chargeMember);
        params.put("requestApplication", requestApplication);
        params.put("requestCorrelationID", requestCorrelationID);
        params.put("sourceApplication", sourceApplication);
        return sendAgency("SendSmsWithID", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
        <Body>
            <SetHashedPIN xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
                <username>[string]</username>
                <agentCode>[string]</agentCode>
                <password>[string]</password>
            </SetHashedPIN>
        </Body>
    </Envelope>*/
    public static String setHashedPIN(String username, String agentCode, String password) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", username);
        params.put("agentCode", agentCode);
        params.put("password", password);
        return sendAgency("SetHashedPIN", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <TestFunction xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency"/>
    </Body>
</Envelope>*/
    public static String testFunction() {
        Map<String, Object> params = new HashMap<>();
        return sendAgency("TestFunction", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <UpdateAuthAttempts xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <username>[string]</username>
            <type>[string]</type>
            <count>[int]</count>
            <tag>[string]</tag>
            <auth_Action>[string]</auth_Action>
            <validity>[dateTime]</validity>
            <clearValidity>[boolean]</clearValidity>
        </UpdateAuthAttempts>
    </Body>
</Envelope>*/
    public static String updateAuthAttempts(String action, boolean indent, String username, String type, int count, String tag, String auth_Action, String validity, boolean clearValidity) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("indent", indent);
        params.put("username", username);
        params.put("type", type);
        params.put("count", count);
        params.put("tag", tag);
        params.put("auth_Action", auth_Action);
        params.put("validity", validity);
        params.put("clearValidity", clearValidity);
        return sendAgency("UpdateAuthAttempts", params);
    }


    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <UpdateVirtualMemberRegistration xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <image_Entry_Number>[string]</image_Entry_Number>
            <image_Path>[string]</image_Path>
            <registration_Entry_Number>[string]</registration_Entry_Number>
            <image_Type>[string]</image_Type>
            <indent>[boolean]</indent>
        </UpdateVirtualMemberRegistration>
    </Body>
</Envelope>*/
    public static String updateVirtualMemberRegistration(String action, String image_Entry_Number, String image_Path, String registration_Entry_Number, String image_Type, boolean indent) {
        Map<String, Object> params = new HashMap<>();
        params.put("action", action);
        params.put("image_Entry_Number", image_Entry_Number);
        params.put("image_Path", image_Path);
        params.put("registration_Entry_Number", registration_Entry_Number);
        params.put("image_Type", image_Type);
        params.put("indent", indent);
        return sendAgency("UpdateVirtualMemberRegistration", params);
    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <FromBase64 xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <location>[string]</location>
            <fileName>[string]</fileName>
            <base64>[string]</base64>
        </FromBase64>
    </Body>
</Envelope>*/

    public static String fromBase64(String strLocation, String strFileName, String strBase64String) {
        Map<String, Object> params = new HashMap<>();
        params.put("location", strLocation);
        params.put("fileName", strFileName);
        params.put("base64", strBase64String);
        return sendAgency("FromBase64", params);


    }

    /*<Envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/">
    <Body>
        <PerformTransaction xmlns="urn:microsoft-dynamics-schemas/codeunit/SkyAgency">
            <action>[string]</action>
            <indent>[boolean]</indent>
            <agentUsername>[string]</agentUsername>
            <agentPassword>[string]</agentPassword>
            <agentAccountNumber>[string]</agentAccountNumber>
            <customerAccountNumber>[string]</customerAccountNumber>
            <customerAccountName>[string]</customerAccountName>
            <customerLoanNumber>[string]</customerLoanNumber>
            <customerLoanName>[string]</customerLoanName>
            <customerName>[string]</customerName>
            <customerMemberNumber>[string]</customerMemberNumber>
            <customerNationalIDNumber>[string]</customerNationalIDNumber>
            <customerMobileNumber>[string]</customerMobileNumber>
            <transactionType>[string]</transactionType>
            <transactionName>[string]</transactionName>
            <transactionSessionID>[string]</transactionSessionID>
            <transactionAmount>[decimal]</transactionAmount>
            <transactionAmountStylized>[string]</transactionAmountStylized>
            <transactionStatementCount>[int]</transactionStatementCount>
            <transactionNarration>[string]</transactionNarration>
            <transactionDate>[string]</transactionDate>
            <transactionPrintReceipt>[boolean]</transactionPrintReceipt>
            <deviceIMEI>[string]</deviceIMEI>
            <deviceSerialNumber>[string]</deviceSerialNumber>
        </PerformTransaction>
    </Body>
</Envelope>*/

    public static String performTransaction(


            String action,
            boolean indent,
            String agentUsername,
            String agentPassword,
            String agentAccountNumber,
            String customerAccountNumber,
            String customerAccountName,
            String customerLoanNumber,
            String customerLoanName,
            String customerName,
            String customerMemberNumber,
            String customerNationalIDNumber,
            String customerMobileNumber,
            String transactionType,
            String transactionName,
            String transactionSessionID,
            Double transactionAmount,
            String transactionAmountStylized,
            int transactionStatementCount,
            String transactionNarration,
            String transactionDate,
            boolean transactionPrintReceipt,
            String deviceIMEI,
            String deviceSerialNumber) {


        Map<String, Object> params = new HashMap<>();
         params.put("action",action);
         params.put("indent",indent);
         params.put("agentUsername",agentUsername);
         params.put("agentPassword",agentPassword);
         params.put("agentAccountNumber",agentAccountNumber);
         params.put("customerAccountNumber",customerAccountNumber);
         params.put("customerAccountName",customerAccountName);
         params.put("customerLoanNumber",customerLoanNumber);
         params.put("customerLoanName",customerLoanName);
         params.put("customerName",customerName);
         params.put("customerMemberNumber",customerMemberNumber);
         params.put("customerNationalIDNumber",customerNationalIDNumber);
         params.put("customerMobileNumber",customerMobileNumber);
         params.put("transactionType",transactionType);
         params.put("transactionName",transactionName);
         params.put("transactionSessionID",transactionSessionID);
         params.put("transactionAmount",transactionAmount);
         params.put("transactionAmountStylized",transactionAmountStylized);
         params.put("transactionStatementCount",transactionStatementCount);
         params.put("transactionNarration",transactionNarration);
         params.put("transactionDate",transactionDate);
         params.put("transactionPrintReceipt",transactionPrintReceipt);
         params.put("deviceIMEI",deviceIMEI);
         params.put("deviceSerialNumber",deviceSerialNumber);
        return sendAgency("PerformTransaction", params);

    }

}

