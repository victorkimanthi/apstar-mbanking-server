package ke.skyworld.mbanking.ussdapplication;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class AppUtils {

	public AppUtils(){
	}

	public static AppConstants.USSDDataType getUSSDDataTypeFromName(String theUSSDDataTypeName){
		AppConstants.USSDDataType theUSSDDataType = AppConstants.USSDDataType.USSD_PROCESS_OVERIDE_DATA_TYPE_NOT_FOUND;

		for(AppConstants.USSDDataType  ussdDataType: AppConstants.USSDDataType.values()){
			if( theUSSDDataTypeName.equalsIgnoreCase(ussdDataType.name()) ) {
				theUSSDDataType = ussdDataType;
				break;
			}
		}
		return theUSSDDataType;
	}

	public static AppConstants.USSDDataType getUSSDDataTypeFromValue(String theUSSDDataTypeValue){
		AppConstants.USSDDataType theUSSDDataType = AppConstants.USSDDataType.USSD_PROCESS_OVERIDE_DATA_TYPE_NOT_FOUND;

		for(AppConstants.USSDDataType  ussdDataType: AppConstants.USSDDataType.values()){
			if( theUSSDDataTypeValue.equalsIgnoreCase(ussdDataType.getValue()) ) {
				theUSSDDataType = ussdDataType;
				break;
			}
		}
		return theUSSDDataType;
	}

	public static String sanitizePhoneNumber(String thePhoneNumber){
		thePhoneNumber = thePhoneNumber.trim();
		try {
			if(thePhoneNumber.startsWith("+")){
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

			return "INVALID MOBILE NUMBER";
		}catch (Exception e){
			e.printStackTrace();
			return e.getMessage();
		}
	}

	public static String maskPhoneNumber(String phoneNumber) {

		try {
			phoneNumber = phoneNumber.replaceAll("\\s*", "");

			if (phoneNumber.length() < 6) {
				return phoneNumber;
			}

			String lastThreeDigits = phoneNumber.substring(phoneNumber.length() - 3);

			String maskedPart = "******";

			String remainingDigits = phoneNumber.substring(0, phoneNumber.length() - 6);

			return remainingDigits + maskedPart + lastThreeDigits;
		}catch (Exception e){
			e.printStackTrace();
		}

		return phoneNumber;
	}


	public static boolean isValidDate(String strDate, String format) {
		DateFormat sdf = new SimpleDateFormat(format);
		sdf.setLenient(false);
		try {
			sdf.parse(strDate);
		} catch (ParseException e) {
			return false;
		}
		return true;
	}

	public static boolean isValidEmailAddress(String emailAddress){
		return emailAddress.matches("^[\\p{L}0-9_!#$%&'*+/=?`{|}~^.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	}

}
