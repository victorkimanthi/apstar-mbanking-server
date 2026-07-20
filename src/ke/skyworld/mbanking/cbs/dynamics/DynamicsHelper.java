package ke.skyworld.mbanking.cbs.dynamics;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.skyworld.lib.mbanking.mapp.MAPPRequest;
import ke.skyworld.lib.mbanking.pesa.PESA;
import ke.skyworld.mbanking.mappapi.MAPPAPIConstants;
import ke.skyworld.mbanking.ussdapi.USSDAPI;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

public class DynamicsHelper {

    public static String TABLE_MBANKING_ACCESS_LOGS = "mbanking_logs.mbanking_access_logs";


    public static LinkedHashMap<String, Object> setPESADetailsForSendingToDynamics(PESA pesa){
        LinkedHashMap<String, Object> lhRequestData = new LinkedHashMap<>();

        try {
            lhRequestData.put("originator_id", pesa.getOriginatorID());
            lhRequestData.put("product_id", pesa.getProductID());
            lhRequestData.put("pesa_type", pesa.getPESAType().getValue());
            lhRequestData.put("action", pesa.getPESAAction().getValue());
            lhRequestData.put("command", pesa.getCommand());
            lhRequestData.put("sensitivity", pesa.getSensitivity());
            lhRequestData.put("charge", pesa.getChargeApplied());
            lhRequestData.put("identifier_type", MAPPAPIConstants.IdentifierType.MOBILE_NUMBER);
            lhRequestData.put("identifier", pesa.getInitiatorIdentifier());

            LinkedHashMap<String, Object> lhTransactionInitiatorDetails = new LinkedHashMap<>();
            lhTransactionInitiatorDetails.put("identifier_type", pesa.getInitiatorType());
            lhTransactionInitiatorDetails.put("identifier", pesa.getInitiatorIdentifier());
            lhTransactionInitiatorDetails.put("account", pesa.getInitiatorAccount());
            lhTransactionInitiatorDetails.put("name", (pesa.getInitiatorName() == null ? "" : pesa.getInitiatorName().trim()));
            lhTransactionInitiatorDetails.put("reference", pesa.getInitiatorReference() == null ? "" : pesa.getInitiatorReference());
            lhTransactionInitiatorDetails.put("other_details", pesa.getInitiatorOtherDetails());
            lhRequestData.put("transaction_initiator_details", lhTransactionInitiatorDetails);

            LinkedHashMap<String, Object> lhTransactionSourceDetails = new LinkedHashMap<>();
            lhTransactionSourceDetails.put("identifier_type", pesa.getSourceType());
            lhTransactionSourceDetails.put("identifier", pesa.getSourceIdentifier());
            lhTransactionSourceDetails.put("account", pesa.getSourceAccount());
            lhTransactionSourceDetails.put("name", (pesa.getSourceName() == null ? "" : pesa.getSourceName().trim()));
            lhTransactionSourceDetails.put("reference", pesa.getSourceReference() == null ? "" : pesa.getSourceReference());
            lhTransactionSourceDetails.put("other_details", pesa.getSourceOtherDetails());
            lhRequestData.put("transaction_source_details", lhTransactionSourceDetails);

            LinkedHashMap<String, Object> lhTransactionSenderDetails = new LinkedHashMap<>();
            lhTransactionSenderDetails.put("identifier_type", pesa.getSenderType());
            lhTransactionSenderDetails.put("identifier", pesa.getSenderIdentifier());
            lhTransactionSenderDetails.put("account", pesa.getSenderAccount());
            lhTransactionSenderDetails.put("name", (pesa.getSenderName() == null ? "" : pesa.getSenderName().trim()));
            lhTransactionSenderDetails.put("reference", pesa.getSenderReference() == null ? "" : pesa.getSenderReference());
            lhTransactionSenderDetails.put("other_details", pesa.getSenderOtherDetails());
            lhRequestData.put("transaction_sender_details", lhTransactionSenderDetails);


            LinkedHashMap<String, Object> lhTransactionReceiverDetails = new LinkedHashMap<>();
            lhTransactionReceiverDetails.put("identifier_type", pesa.getReceiverType());
            lhTransactionReceiverDetails.put("identifier", pesa.getReceiverIdentifier());
            lhTransactionReceiverDetails.put("account", pesa.getReceiverAccount());
            lhTransactionReceiverDetails.put("name", (pesa.getReceiverName() == null ? "" : pesa.getReceiverName().trim()));
            lhTransactionReceiverDetails.put("reference", pesa.getReceiverReference() == null ? "" : pesa.getReceiverReference());
            lhTransactionReceiverDetails.put("other_details", pesa.getReceiverOtherDetails());
            lhRequestData.put("transaction_receiver_details", lhTransactionReceiverDetails);

            LinkedHashMap<String, Object> lhTransactionBeneficiaryDetails = new LinkedHashMap<>();
            lhTransactionBeneficiaryDetails.put("identifier_type", pesa.getBeneficiaryType());
            lhTransactionBeneficiaryDetails.put("identifier", pesa.getBeneficiaryIdentifier());
            lhTransactionBeneficiaryDetails.put("account", pesa.getBeneficiaryAccount());
            lhTransactionBeneficiaryDetails.put("name", (pesa.getBeneficiaryName() == null ? "" : pesa.getBeneficiaryName().trim()));
            lhTransactionBeneficiaryDetails.put("reference", pesa.getBeneficiaryReference() == null ? "" : pesa.getBeneficiaryReference());
            lhTransactionBeneficiaryDetails.put("other_details", pesa.getBeneficiaryOtherDetails());
            lhRequestData.put("transaction_beneficiary_details", lhTransactionBeneficiaryDetails);

            lhRequestData.put("amount", pesa.getTransactionAmount());
            lhRequestData.put("category", pesa.getCategory());
            lhRequestData.put("transaction_description", pesa.getPESAStatusDescription());
            lhRequestData.put("source_reference", pesa.getSourceReference() == null ? "" : pesa.getSourceReference());
            lhRequestData.put("request_application", pesa.getCorrelationApplication());
            lhRequestData.put("source_application", pesa.getSourceApplication());
            lhRequestData.put("transaction_date_time", pesa.getPesaDateCreated());
        } catch (Exception e){
            e.printStackTrace();
            System.err.println(DynamicsHelper.class.getSimpleName() + "." + new Object() {
            }.getClass().getEnclosingMethod().getName() + "() ERROR : " + e.getMessage());
        }
        return lhRequestData;
    }


    public static enum ChargeServiceStatus {
        CHARGED("CHARGED"),
        NOT_CHARGED("NOT_CHARGED"),
        ERROR("ERROR"),
        FAILED("FAILED");

        private String value;

        ChargeServiceStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static enum ChargeServiceType {
        MAPP_LOGIN("MAPP_LOGIN");

        private String value;

        ChargeServiceType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static enum ChargeServiceAction {
        CHECK("CHECK"),
        CHARGE("CHARGE");

        private String value;

        ChargeServiceAction(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
