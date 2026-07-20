package ke.skyworld.mbanking.mbankingapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ke.co.skyworld.smp.query_manager.SystemTables;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.core.MBankingXMLFactory;
import ke.skyworld.lib.mbanking.msg.MSGConstants;
import ke.skyworld.lib.mbanking.utils.Crypto;
import ke.skyworld.lib.mbanking.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static ke.co.skyworld.smp.query_manager.SystemTables.TBL_MOBILE_BANKING_REGISTER;
import static ke.skyworld.mbanking.ussdapi.APIUtils.fnSendSMS;

public class MemberRegistrationUtil {
    private static HashMap<String, Object> memberDetails;

    public MemberRegistrationUtil() {
        this.memberDetails = new HashMap<>();
    }

    public static TransactionWrapper<FlexicoreHashMap> registerMember(
            String theUSSDSessionID,
            String theMobileNumber,
            String theNationalIdNumber,
            String theSurname,
            String theFirstName,
            String theOtherName,
            String theDateOfBirth,
            String theReferrerIdentifierType,
            String theReferrerIdentifier,
            String theEmailAddress,
            String theServiceNumber,
            String theEmployer,
            String theGender
    ) {
        TransactionWrapper<FlexicoreHashMap> insertResponse;
        try {

            FlexicoreHashMap insertMap = new FlexicoreHashMap();
            insertMap.putValue("originator_id", theUSSDSessionID);
            insertMap.putValue("onboarding_type", "SELF");
            insertMap.putValue("surname", theSurname);
            insertMap.putValue("first_name", theFirstName);
            insertMap.putValue("other_name", theOtherName);
            insertMap.putValue("primary_phone_number", theMobileNumber);
            insertMap.putValue("primary_email_address", theEmailAddress);
            insertMap.putValue("primary_identity_type", "NATIONAL_ID_NUMBER");
            insertMap.putValue("primary_identity", theNationalIdNumber);

            /*
            {
              "action": "MEMBER_REGISTRATION",
              "payload": {
                "api_request_id": "df3e7cf5-1e4b-41ef-a22f-e755be665432",
                "full_names": "John Doe Kamau",
                "phone_number": "254700112233",
                "email_address": "john.doe@gmail.com",
                "gender": "MALE ",
                "date_of_birth": "01/01/1990",
                "id_number": "1234567890",
                "passport_number": "1234567890",
                "home_address": "7th Floor Western Heights, Karuna Road, Westlands, Nairobi",
                "citizenship": "KENYA",
                "next_of_kin": [],
                "recruiter_identifier_type": "ID_NUMBER ",
                "recruiter_identifier": "1234567890",
                "t_and_c_accepted": "YES"
              }
            }
            * */

            FlexicoreHashMap registrationPayloadParent = new FlexicoreHashMap();
            registrationPayloadParent.put("action", "MEMBER_REGISTRATION");

            FlexicoreHashMap registrationPayload = new FlexicoreHashMap();
            registrationPayload.put("api_request_id", UUID.randomUUID().toString());
            registrationPayload.put("full_names", theFirstName + " " + theSurname);
            registrationPayload.put("phone_number", theMobileNumber);
            registrationPayload.put("email_address", theEmailAddress);
            registrationPayload.put("gender", theGender);
            registrationPayload.put("date_of_birth", theDateOfBirth);
            registrationPayload.put("id_number", theNationalIdNumber);
            registrationPayload.put("passport_number", "");
            registrationPayload.put("home_address", "");
            registrationPayload.put("citizenship", "");
            registrationPayload.put("service_number", theServiceNumber);
            registrationPayload.put("employer", theEmployer);
            registrationPayload.put("next_of_kin", new ArrayList<>());
            registrationPayload.put("recruiter_identifier_type", theReferrerIdentifierType);
            registrationPayload.put("recruiter_identifier", theReferrerIdentifier);
            registrationPayload.put("t_and_c_accepted", "YES");

            registrationPayloadParent.put("payload", registrationPayload);

            ObjectMapper objectMapper = new ObjectMapper();
            String strRegistrationPayload = objectMapper.writeValueAsString(registrationPayloadParent);

            insertMap.putValue("registration_payload", strRegistrationPayload);

            Crypto crypto = new Crypto();
            String strIntegrityHash = crypto.hash("SHA-256", strRegistrationPayload);

            insertMap.putValue("integrity_hash", strIntegrityHash);
            insertMap.putValue("date_created", DateTime.getCurrentDateTime());
            insertMap.putValue("date_modified", DateTime.getCurrentDateTime());


            insertResponse = Repository.insertAutoIncremented(StringRefs.SENTINEL, "mobile_banking.mobile_banking_registration_tmp", insertMap);

            return insertResponse;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getName(String theMobileNumber) {
        if (memberDetails.get("MobileNumber").equals(theMobileNumber)) {
            return (String) memberDetails.get("Name");
        }
        return null;
    }

    public static String getRegistrationStatus(String theMobileNumber) {
        String strRegistrationStatus = "NEW";
        try {
            /*{
                TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                        SystemTables.TBL_CUSTOMER_REGISTER_SIGNATORIES,
                        new FilterPredicate("primary_mobile_number = :mobile_number"),
                        new FlexicoreHashMap().addQueryArgument(":mobile_number", theMobileNumber));

                if (!signatoryDetailsWrapper.hasErrors()) {
                    FlexicoreHashMap singleRecordMap = signatoryDetailsWrapper.getSingleRecord();
                    if (singleRecordMap != null && !singleRecordMap.isEmpty()) {
                        return "REGISTERED";
                    }
                }
            }*/
            {
                TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                        "mobile_banking.mobile_banking_registration_tmp",
                        new FilterPredicate("primary_phone_number = :mobile_number"),
                        new FlexicoreHashMap().addQueryArgument(":mobile_number", theMobileNumber));

                if (!signatoryDetailsWrapper.hasErrors()) {
                    FlexicoreHashMap singleRecordMap = signatoryDetailsWrapper.getSingleRecord();
                    if (singleRecordMap != null && !singleRecordMap.isEmpty()) {
                        return "EXISTING";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strRegistrationStatus;
    }

    public static FlexicoreHashMap getExistingMemberData(String strMobileNumber) {
        FlexicoreHashMap singleRecordMap = null;
        TransactionWrapper<FlexicoreHashMap> detailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                "mobile_banking.mobile_banking_registration_tmp",
                new FilterPredicate("primary_phone_number = :primary_phone_number"),
                new FlexicoreHashMap().addQueryArgument(":primary_phone_number", strMobileNumber));
        if (!detailsWrapper.hasErrors()) {
            singleRecordMap = detailsWrapper.getSingleRecord();
        }
        return singleRecordMap;
    }

    public static FlexicoreHashMap getMemberRegistrationSetup() {
        FlexicoreHashMap singleRecordMap = new FlexicoreHashMap();

        try {
            FlexicoreHashMap singleRecordMap1 = null;
            TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper1 = Repository.selectWhere(StringRefs.SENTINEL,
                    "master.system_parameters",
                    new FilterPredicate("parameter_type = :parameter_type"),
                    new FlexicoreHashMap().addQueryArgument(":parameter_type", "MEMBER_REGISTRATION"));

            if (!signatoryDetailsWrapper1.hasErrors()) {
                singleRecordMap1 = signatoryDetailsWrapper1.getSingleRecord();
            }
            assert singleRecordMap1 != null;
            String strMemberRegistrationXML = singleRecordMap1.getStringValue("parameter_value");

            String strFeeApplicable = MBankingXMLFactory.getXPathValueFromXMLString("/MEMBER_REGISTRATION/FEE/@APPLICABLE", strMemberRegistrationXML);
            String strFeeAmount = MBankingXMLFactory.getXPathValueFromXMLString("/MEMBER_REGISTRATION/FEE", strMemberRegistrationXML);

            double dblFeeAmount = Double.parseDouble(strFeeAmount);

            singleRecordMap.put("applicable", strFeeApplicable.equalsIgnoreCase("YES"));
            singleRecordMap.put("amount", dblFeeAmount);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return singleRecordMap;
    }


    public static boolean updateRegistrationRecord(String theOriginatorID, String thePaymentReference, double theAmount) {
        try{


            TransactionWrapper<FlexicoreHashMap> signatoryDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
                    "mobile_banking.mobile_banking_registration_tmp",
                    new FilterPredicate("originator_id = :originator_id and payment_reference is null"),
                    new FlexicoreHashMap().
                            addQueryArgument(":originator_id", theOriginatorID)
            );

            if (!signatoryDetailsWrapper.hasErrors()) {
                FlexicoreHashMap singleRecordMap = signatoryDetailsWrapper.getSingleRecord();
                if (singleRecordMap != null && !singleRecordMap.isEmpty()) {
                    FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
                    theUpdateLoginParamsMap.put("payment_source", "M-PESA");
                    theUpdateLoginParamsMap.put("payment_reference", thePaymentReference);

                    TransactionWrapper<?> updateWrapper = Repository.update(
                            StringRefs.SENTINEL,
                            "mobile_banking.mobile_banking_registration_tmp",
                            theUpdateLoginParamsMap,
                            new FilterPredicate("originator_id = :originator_id"),
                            new FlexicoreHashMap()
                                    .addQueryArgument(":originator_id", theOriginatorID)
                    );

                    if(!updateWrapper.hasErrors()) {
                        String strFirstName = singleRecordMap.getStringValue("first_name");
                        String strMobileNumber = singleRecordMap.getStringValue("primary_phone_number");

                        String strDatetime = MBankingDB.getDBDateTime().trim();
                        String strFormattedDateTime = Utils.formatDate(strDatetime, "yyyy-MM-dd HH:mm:ss", "dd-MMM-yyyy HH:mm:ss");

                        String strAmount = Utils.formatDouble(theAmount, "#,###");

                        String strMSG = "Dear " + strFirstName + ",\n"+
                                "Your registration fee payment of KES: "+strAmount +" has been received.\n" +
                                "Kindly wait as we process your registration.\n\n"+
                                "M-Pesa Ref: "+thePaymentReference+"\n"+
                                "Registration Ref: "+theOriginatorID+"\n"+
                                "Date: " + strFormattedDateTime;



                        fnSendSMS(strMobileNumber, strMSG, "YES", MSGConstants.MSGMode.SAF, 210, "STANDING_ORDER_VIEW", "MBANKING_SERVER", "MPESA_BROKER", UUID.randomUUID().toString(), UUID.randomUUID().toString());

                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }
}
