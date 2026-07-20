package ke.skyworld.mbanking.pesaapi;
//import java.io.*;

import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.core.MBankingLocalParameters;
import ke.skyworld.lib.mbanking.pesa.PESAConstants;
import ke.skyworld.lib.mbanking.pesa.PESADB;
import ke.skyworld.lib.mbanking.pesa.PESALocalParameters;
import ke.skyworld.lib.mbanking.utils.NamedParameterStatement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public  class PESAAPIDB
{
	public static ResultSet executeQuery(String query){

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

}

