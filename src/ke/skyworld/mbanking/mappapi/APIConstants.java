package ke.skyworld.mbanking.mappapi;

public class APIConstants {
    public enum MAPP_PARAM_TYPE {
        CASH_WITHDRAWAL,
        AIRTIME_PURCHASE,
        PAY_BILL,
        EXTERNAL_FUNDS_TRANSFER,
        INTERNAL_FUNDS_TRANSFER,
        DEPOSIT,
        APPLY_LOAN,
        PAY_LOAN,
    }

    public enum OTP_VERIFICATION_STATUS {
        SUCCESS,
        ERROR,
    }

    public enum OTP_CHECK_STAGE {
        GENERATION,
        VERIFICATION,
    }

    public enum OTP_TYPE {
        LOGIN,
        ACTIVATION,
        TRANSACTIONAL,
        TRANSACTIONAL_WITH_CUSTOMER_OTP,
        TRANSACTIONAL_WITH_AGENT_OTP,
    }

    public enum AGNT_PARAM_TYPE {
        CASH_WITHDRAWAL,
        AIRTIME_PURCHASE,
        PAY_BILL,
        EXTERNAL_FUNDS_TRANSFER,
        INTERNAL_FUNDS_TRANSFER,
        DEPOSIT,
        APPLY_LOAN,
        PAY_LOAN,
    }
}
