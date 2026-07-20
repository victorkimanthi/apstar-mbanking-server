package ke.skyworld.mbanking.mappapi;
//import java.io.*;

import ke.co.skyworld.smp.authentication_manager.MobileBankingCryptography;
import ke.co.skyworld.smp.query_manager.SystemTables;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.DateTime;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.co.skyworld.smp.utility_items.security.HashUtils;
import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.core.MBankingLocalParameters;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.utils.NamedParameterStatement;
import ke.skyworld.mbanking.ussdapi.APIUtils;
import ke.skyworld.mbanking.ussdapi.USSDAPIConstants;

import javax.net.ssl.HttpsURLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static ke.co.skyworld.smp.query_manager.SystemTables.TBL_MOBILE_BANKING_REGISTER;

public class MAPPAPIDB {
    public static ResultSet executeQuery(String query) {

        Statement st = null;
        ResultSet rs = null;

        System.out.println("executeQuery(String query): " + query);
        try {
            if (MBankingDB.getConnection() != null) {
                st = MBankingDB.getConnection().createStatement();
				rs = st.executeQuery(query);
			}
		}
		catch (Exception e) {
			System.err.println ("\n\nexecuteQuery(String query): Error message: " + e.getMessage ()); /* ignore close errors */
		}
		finally {
			//try{st.close();}catch(Exception e){}
			//st = null;
		}
		return rs;
	}
		
	public static boolean isConnected()
	{
		if (MBankingDB.getConnection() != null)
		{
			try {
				return !MBankingDB.getConnection().isClosed();
			} catch (SQLException e) {
				return false;
			}
		}
		else
		{
			return false;		
		}
	}
	
	public static int isConnectionNull()
	{
		if (MBankingDB.getConnection() != null)
		{
			return 0;
		}
		else
		{
			return 1;		
		}
	}
	
	public static String getDBDateTime()
	{
		Statement st = null;
		ResultSet rs = null;
		String strDateTime=null;
		String strSQL = null;
		try
		{
			switch (MBankingLocalParameters.getDatabaseType()) {
			case MySQL: 
				strSQL = "SELECT NOW() AS DB_DATETIME";	
				break;
			case MicrosoftSQL: 
				strSQL = "SELECT CONVERT(VARCHAR, GETDATE(), 120) AS DB_DATETIME";
				break;     
			case Oracle:
				strSQL = "SELECT TO_CHAR(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS') AS DB_DATETIME FROM DUAL";
				break;
			case PostgreSQL: 
				strSQL = "SELECT TO_CHAR(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS') AS DB_DATETIME FROM DUAL";
				break;
			case MicrosoftAccess:
				strSQL = "SELECT CONVERT(VARCHAR, GETDATE(), 120) AS DB_DATETIME";
				break;
			default:	 
				System.err.println("Invalid Database Type selected\n");
				break;
			} 
			
			st = MBankingDB.getConnection().createStatement();
			rs = st.executeQuery(strSQL);
			rs.next();
			
			strDateTime = rs.getString("DB_DATETIME").substring(0,19);
			//System.out.println("strDateTime: " + strDateTime);
		}
		catch (Exception e)
		{
			System.err.println ("Error message: " + e.getMessage ());
		}
		finally
		{
			try{rs.close();}catch(Exception e){}
			try{st.close();}catch(Exception e){}
			
			rs = null;
			st = null;
			strSQL = null;
		}
		return strDateTime;
	}
	
	public static String getDBTimeStamp()
	{
		Statement st = null;
		ResultSet rs = null;
		String strDateTime=null;
		String strSQL = null;
		try
		{			
			switch (MBankingLocalParameters.getDatabaseType()) {
			case MySQL: 
				strSQL = "SELECT DATE_FORMAT(NOW(),'%Y%m%d%H%i%s') AS DB_DATETIME";	
				break;
			case MicrosoftSQL: 
				strSQL ="DECLARE @today DATETIME = SYSDATETIME(); " +
						"SELECT CONVERT(VARCHAR,@today,112) + REPLACE(CONVERT(VARCHAR, @today, 108),':','')  AS DB_DATETIME;";
				break;     
			case Oracle:
				strSQL = "SELECT TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS') AS DB_DATETIME FROM DUAL";
				break;
			case PostgreSQL: 
				strSQL = "SELECT TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS') AS DB_DATETIME FROM DUAL";
				break;
			case MicrosoftAccess:
				System.err.println("Microsoft Access Database Type is not yet supported\n");
				break;
			default:	 
				System.err.println("Invalid Database Type selected\n");
				break;
			} 
			
			st = MBankingDB.getConnection().createStatement();
			rs = st.executeQuery(strSQL);
			if(rs.next()){
				strDateTime = rs.getString("DB_DATETIME");
			}
			
		}
		catch (Exception e)
		{
			System.err.println ("Error message 123: " + e.getMessage ());
		}
		finally {
            try {
                rs.close();
            } catch (Exception e) {
            }
            try {
                st.close();
            } catch (Exception e) {
            }

            rs = null;
            st = null;
            strSQL = null;
        }
        return strDateTime;
    }

    public static String getDBTimeStampWithFormat() {
        Statement st = null;
        ResultSet rs = null;
        String strDateTime = null;
        String strSQL = null;
        try {
            switch (MBankingLocalParameters.getDatabaseType()) {
                case MySQL:
                    strSQL = "SELECT DATE_FORMAT(NOW(),'%d-%b-%Y %H:%i:%s') AS DB_DATETIME";
                    break;
                case MicrosoftSQL:
                    strSQL = "SELECT FORMAT (getdate(), 'dd-MMM-yyyy H:mm:s') AS DB_DATETIME";
                    break;
                case Oracle:
                    strSQL = "SELECT TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS') AS DB_DATETIME FROM DUAL";
                    break;
                case PostgreSQL:
                    strSQL = "SELECT TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS') AS DB_DATETIME FROM DUAL";
                    break;
                case MicrosoftAccess:
                    System.err.println("Microsoft Access Database Type is not yet supported\n");
                    break;
                default:
                    System.err.println("Invalid Database Type selected\n");
                    break;
            }

            st = MBankingDB.getConnection().createStatement();
            rs = st.executeQuery(strSQL);
            if (rs.next()) {
                strDateTime = rs.getString("DB_DATETIME");
            }

        } catch (Exception e) {
            System.err.println("Error message 123: " + e.getMessage());
        } finally {
            try {
                rs.close();
            } catch (Exception e) {
            }
            try {
                st.close();
            } catch (Exception e) {
            }

            rs = null;
            st = null;
            strSQL = null;
        }
        return strDateTime;
    }


	public static String fnCreateAgentTransactionReceipt(String theOriginatorID, PESAConstants.PESAType thePESAType, String strRowToFetch) {
		String rVal = "";
		NamedParameterStatement p = null;
		ResultSet rs = null;
		String strSQL = null;
		String strPESATableName = "pesa_log";
		if (thePESAType.equals(PESAConstants.PESAType.PESA_OUT)) {
			strPESATableName = PESALocalParameters.get_PESA_OUT_TableName();
		} else if (thePESAType.equals(PESAConstants.PESAType.PESA_IN)) {
			strPESATableName = PESALocalParameters.get_PESA_IN_TableName();
		}

		try {
			strSQL = "SELECT "+strRowToFetch+" FROM " + strPESATableName + " WHERE originator_id = :originator_id AND pesa_type = :pesa_type";
			p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
			p.setString("originator_id", theOriginatorID);
			p.setString("pesa_type", thePESAType.getValue());
			rs = p.executeQuery();
			if (rs.next()) {
				rVal = rs.getString(strRowToFetch);
			}

			rs.close();
			p.close();
			rs = null;
			p = null;
			strSQL = null;
		} catch (Exception var22) {
			System.err.println("Error message: " + var22.getMessage());
		} finally {
			strSQL = null;
			if (rs != null) {
				try {
					rs.close();
				} catch (Exception var21) {
				}
			}

			if (p != null) {
				try {
					p.close();
				} catch (Exception var20) {
				}
			}

			rs = null;
			p = null;
			strSQL = null;
		}

		return rVal;
	}

	public static boolean insertOrUpdateAgencyOTP(String username, String otp, long otpTTL, String sourceApplication) {
		// Get current timestamp and OTP expiry time
		LocalDateTime currentDateTime = LocalDateTime.now();
		LocalDateTime expiryDateTime = currentDateTime.plus(otpTTL, ChronoUnit.SECONDS);
		String strExpiryDateTime = expiryDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		// Hash the OTP before storing
		//String hashedOTP = MobileBankingCryptography.hashPIN(username, otp);


		// Step 1: Check if an OTP record already exists for this username
		TransactionWrapper<FlexicoreHashMap> agencyOtpWrapper = Repository.selectWhere(
				StringRefs.SENTINEL,
				"agency_banking.agency_otps",
				new FilterPredicate("username = :username"),
				new FlexicoreHashMap().addQueryArgument(":username", username)
		);

		FlexicoreHashMap existingRecord = agencyOtpWrapper.getSingleRecord();
		FlexicoreHashMap updateParams = new FlexicoreHashMap();

		// Step 2: Prepare update or insert parameters
		updateParams.put("otp", otp);
		updateParams.put("otp_expiry_date", strExpiryDateTime);
		updateParams.put("date_modified", DateTime.getCurrentDateTime());
		updateParams.put("source_application", sourceApplication);

		if (existingRecord != null) {
			// If record exists, update it
			existingRecord.copyFrom(updateParams);
			String integrityHash = MobileBankingCryptography.calculateIntegrityHash(existingRecord);
			updateParams.putValue("integrity_hash", integrityHash);

			TransactionWrapper<?> updateWrapper = Repository.update(
					StringRefs.SENTINEL,
					"agency_banking.agency_otps",
					updateParams,
					new FilterPredicate("username = :username"),
					new FlexicoreHashMap().addQueryArgument(":username", username)
			);

			return updateWrapper.hasErrors();
		} else {
			// If no record exists, insert a new one
			updateParams.put("username", username);
			updateParams.put("login_attempts", 0);
			updateParams.put("otp_attempts", 0);
			updateParams.put("login_auth_action", "NONE");
			updateParams.put("otp_auth_action", "NONE");
			updateParams.put("integrity_hash", "PENDING_CALCULATION");
			updateParams.put("date_created", DateTime.getCurrentDateTime());

			TransactionWrapper<?> insertWrapper = Repository.insertAutoIncremented(
					StringRefs.SENTINEL,
					"agency_banking.agency_otps",
					updateParams
			);

			return insertWrapper.hasErrors();
		}
	}

	public static boolean fnIsValidAgencyOTP(String username, String inputOTP) {
		// Fetch the OTP and expiry date for the given username (phone number or agent number)
		TransactionWrapper<FlexicoreHashMap> agencyOTPWrapper = Repository.selectWhere(
				StringRefs.SENTINEL,
				"agency_banking.agency_otps",  // Table name
				"otp, otp_expiry_date",
				new FilterPredicate("username = :username"),
				new FlexicoreHashMap().addQueryArgument(":username", username)
		);

		// Get the record
		FlexicoreHashMap agencyOTPMap = agencyOTPWrapper.getSingleRecord();
		if (agencyOTPMap == null) {
			return false; // No OTP found
		}

		// Extract OTP and expiry date
		String storedOTP = agencyOTPMap.getStringValue("otp");
		String strOTExpiryDate = agencyOTPMap.getStringValueOrIfNull("otp_expiry_date", "1976-01-01 00:00:00.000000");

		if (storedOTP == null || !storedOTP.equals(inputOTP)) {
			return false; // OTP does not match
		}

		// Parse current and expiry date
		LocalDateTime currDateTime = LocalDateTime.parse(DateTime.getCurrentDateTime(),
				DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));
		LocalDateTime expiryDateTime = LocalDateTime.parse(strOTExpiryDate,
				DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));

		return currDateTime.isBefore(expiryDateTime);
	}


	public static boolean fnDeleteAgencyOTP(String username) {
		// Perform delete operation
		TransactionWrapper<?> deleteWrapper = Repository.delete(
				StringRefs.SENTINEL,
				"agency_banking.agency_otps",  // Table name
				new FilterPredicate("username = :username"),
				new FlexicoreHashMap().addQueryArgument(":username", username)
		);

		// Return true if deletion was successful (no errors)
		return !deleteWrapper.hasErrors();
	}










	public static boolean fnInsertOTPData(String theMobileNumber, String theOTP, long intOTPTTL) {

		LocalDateTime currentDateTime = LocalDateTime.now();
		LocalDateTime expiryDateTime = currentDateTime.plus(intOTPTTL, ChronoUnit.SECONDS);

		String strExpiryDateTime = expiryDateTime.format(DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));

		TransactionWrapper<FlexicoreHashMap> mobileMappingDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
				SystemTables.TBL_MOBILE_BANKING_REGISTER,
				new FilterPredicate("mobile_number = :mobile_number"),
				new FlexicoreHashMap().addQueryArgument(":mobile_number", theMobileNumber));

		FlexicoreHashMap mobileBankingDetailsMap = mobileMappingDetailsWrapper.getSingleRecord();

		FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
		theUpdateLoginParamsMap.put("otp", MobileBankingCryptography.hashPIN(theMobileNumber, theOTP));
		theUpdateLoginParamsMap.put("otp_expiry_date", strExpiryDateTime);
		theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

		mobileBankingDetailsMap.copyFrom(theUpdateLoginParamsMap);

		String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingDetailsMap);
		theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

		TransactionWrapper<?> updateWrapper = Repository.update(
				StringRefs.SENTINEL,
				TBL_MOBILE_BANKING_REGISTER,
				theUpdateLoginParamsMap,
				new FilterPredicate("mobile_number = :mobile_number"),
				new FlexicoreHashMap()
						.addQueryArgument(":mobile_number", theMobileNumber)
		);

		return updateWrapper.hasErrors();
	}

	public static boolean fnIsValidOTP(String theMobileNumber, long otpTTL) {

		TransactionWrapper<FlexicoreHashMap> mobileMappingDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
				SystemTables.TBL_MOBILE_BANKING_REGISTER, "otp, otp_expiry_date",
				new FilterPredicate("mobile_number = :mobile_number"),
				new FlexicoreHashMap().addQueryArgument(":mobile_number", theMobileNumber));

		FlexicoreHashMap mobileBankingDetailsMap = mobileMappingDetailsWrapper.getSingleRecord();
		String strOTP = mobileBankingDetailsMap.getStringValue("otp");
		String strOTExpiryDate = mobileBankingDetailsMap.getStringValueOrIfNull("otp_expiry_date", "1976-01-01 00:00:00.000000");

		if(strOTP==null){
			return false;
		}

		LocalDateTime currDateTime = LocalDateTime.parse(DateTime.getCurrentDateTime(), DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));
		LocalDateTime expiryDateTime = LocalDateTime.parse(strOTExpiryDate, DateTimeFormatter.ofPattern(DateTime.DEFAULT_DATE_TIME_FORMAT));

		return currDateTime.isBefore(expiryDateTime);
	}

	public static void fnDeleteOTPData(String theMobileNumber) {

		TransactionWrapper<FlexicoreHashMap> mobileMappingDetailsWrapper = Repository.selectWhere(StringRefs.SENTINEL,
				SystemTables.TBL_MOBILE_BANKING_REGISTER,
				new FilterPredicate("mobile_number = :mobile_number"),
				new FlexicoreHashMap().addQueryArgument(":mobile_number", theMobileNumber));

		FlexicoreHashMap mobileBankingDetailsMap = mobileMappingDetailsWrapper.getSingleRecord();

		FlexicoreHashMap theUpdateLoginParamsMap = new FlexicoreHashMap();
		theUpdateLoginParamsMap.put("otp", null);
		theUpdateLoginParamsMap.put("otp_expiry_date", null);
		theUpdateLoginParamsMap.put("date_modified", DateTime.getCurrentDateTime());

		mobileBankingDetailsMap.copyFrom(theUpdateLoginParamsMap);

		String integrityHash = MobileBankingCryptography.calculateIntegrityHash(mobileBankingDetailsMap);
		theUpdateLoginParamsMap.putValue("integrity_hash", integrityHash);

		TransactionWrapper<?> updateWrapper = Repository.update(
				StringRefs.SENTINEL,
				TBL_MOBILE_BANKING_REGISTER,
				theUpdateLoginParamsMap,
				new FilterPredicate("mobile_number = :mobile_number"),
				new FlexicoreHashMap()
						.addQueryArgument(":mobile_number", theMobileNumber)
		);
	}

	public static boolean fnDeleteOTPDataAfterTimeOut() {
		boolean rVal = false;
		NamedParameterStatement p = null;
		String strSQL = null;


		try {
			strSQL = "DELETE FROM temporary_otps WHERE otp_ttl < NOW();";
			p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
			rVal = p.execute();

			p.close();
			p = null;
			strSQL = null;
		} catch (Exception var22) {
			System.err.println("Error message: " + var22.getMessage());
		} finally {
			strSQL = null;

			if (p != null) {
				try {
					p.close();
				} catch (Exception var20) {
				}
			}

			p = null;
			strSQL = null;
		}

		return rVal;
	}
}