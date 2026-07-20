package ke.skyworld.mbanking.agencyapi;

import ke.co.skyworld.smp.query_manager.beans.FlexicoreArrayList;
import ke.co.skyworld.smp.query_manager.beans.FlexicoreHashMap;
import ke.co.skyworld.smp.query_manager.beans.TransactionWrapper;
import ke.co.skyworld.smp.query_manager.query.FilterPredicate;
import ke.co.skyworld.smp.query_repository.Repository;
import ke.co.skyworld.smp.utility_items.constants.StringRefs;
import ke.skyworld.lib.mbanking.core.MBankingDB;
import ke.skyworld.lib.mbanking.utils.NamedParameterStatement;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;

public class AgencyAPIDB {
    static String strUserDataTableName = "agency_banking_user_data";
    static String strReceiptsTableName = "agency_banking_receipts";

    public static boolean fnInsertAgentData(String theUserName, String theFingerprintData) {
        boolean rVal = false;
        NamedParameterStatement p = null;
        String strSQL = null;

        try {
            strSQL = "INSERT INTO " + strUserDataTableName + " (username, fingerprint_data) VALUES (:username, :fingerprint_data);";
            p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
            p.setString("username", theUserName);
            p.setString("fingerprint_data", theFingerprintData);
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

    public static boolean fnUpdateAgentData(String theUserName, String theFingerprintData) {
        boolean rVal = false;
        NamedParameterStatement p = null;
        String strSQL = null;

        try {
            strSQL = "UPDATE " + strUserDataTableName + " SET username = :username, fingerprint_data = :fingerprint_data;";
            p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
            p.setString("username", theUserName);
            p.setString("fingerprint_data", theFingerprintData);
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

    public static String fnSelectAgentData(String theUserName, String theRowToFetch) {
        String rVal = "";
        NamedParameterStatement p = null;
        ResultSet rs = null;
        String strSQL = null;

        try {
            strSQL = "SELECT "+theRowToFetch+" FROM " + strUserDataTableName + " WHERE username = :username";
            p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
            p.setString("username", theUserName);
            rs = p.executeQuery();
            if (rs.next()) {
                rVal = rs.getString(theRowToFetch);
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

    public static boolean fnCheckIfAgentExists(String theUserName) {
        boolean rVal = false;
        NamedParameterStatement p = null;
        ResultSet rs = null;
        String strSQL = null;

        try {
            strSQL = "SELECT COUNT(*) as agent_count FROM " + strUserDataTableName + " WHERE username = :username";
            p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
            p.setString("username", theUserName);
            rs = p.executeQuery();
            if (rs.next()) {
                rVal = Integer.parseInt(rs.getString("agent_count")) > 0;
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

    public static boolean fnInsertReceipt(String username, String sessionID, String transactionType, String print) {
        // Get current timestamp
        String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Step 1: Prepare insert parameters
        FlexicoreHashMap insertParams = new FlexicoreHashMap();
        insertParams.put("username", username);
        insertParams.put("session_id", sessionID);
        insertParams.put("transaction_type", transactionType);
        insertParams.put("print", print);
        insertParams.put("date_created", currentDateTime);
        insertParams.put("date_modified", currentDateTime);

        // Step 2: Insert into the database
        TransactionWrapper<?> insertWrapper = Repository.insertAutoIncremented(
                StringRefs.SENTINEL,
                "agency_banking.agency_banking_receipts",
                insertParams
        );

        return insertWrapper.hasErrors();
    }


    /*public static boolean fnInsertReceipt(String theUserName, String theSessionID, String theTransactionType, String thePrint) {
        boolean rVal = false;
        NamedParameterStatement p = null;
        String strSQL = null;

        try {
            strSQL = "INSERT INTO " + strReceiptsTableName + " (username, session_id, transaction_type, print) VALUES (:username, :session_id, :transaction_type, :print);";
            p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
            p.setString("username", theUserName);
            p.setString("session_id", theSessionID);
            p.setString("transaction_type", theTransactionType);
            p.setString("print", thePrint);
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
    }*/

    public static String fnSelectReceipts(String username, LinkedList<String> sessionIds) {
        JSONArray jsonArray = new JSONArray();

        // Step 1: Prepare query parameters
        FlexicoreHashMap queryParams = new FlexicoreHashMap().addQueryArgument(":username", username);
        StringBuilder sessionFilter = new StringBuilder("session_id IN (");

        for (int i = 0; i < sessionIds.size(); i++) {
            String paramName = ":session_id_" + i;
            sessionFilter.append(paramName);
            queryParams.addQueryArgument(paramName, sessionIds.get(i));

            if (i < sessionIds.size() - 1) {
                sessionFilter.append(", ");
            }
        }
        sessionFilter.append(")");

        // Step 2: Execute query
        FlexicoreArrayList flexicoreArrayList = (FlexicoreArrayList)Repository.selectWhere(
                StringRefs.SENTINEL,
                "agency_banking.agency_banking_receipts",
                new FilterPredicate("username = :username AND " + sessionFilter),
                queryParams
        ).getData();

        // Step 3: Process results
        for (FlexicoreHashMap record : flexicoreArrayList) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("username", record.getStringValue("username"));
            jsonObject.put("session_id", record.getStringValue("session_id"));
            jsonObject.put("transaction_type", record.getStringValue("transaction_type"));
            jsonObject.put("print", record.getStringValue("print"));
            jsonArray.put(jsonObject);
        }

        return jsonArray.toString();
    }

    /*public static String fnSelectReceipts(String theUsername, LinkedList<String> llsSessionIds) {
        String rVal = "";
        NamedParameterStatement p = null;
        ResultSet rs = null;
        String strSQL = null;
        JSONArray jsonArray = new JSONArray();

        try {
            StringBuilder strSessionIdFilter = new StringBuilder();
            for (int count = 0; count < llsSessionIds.size(); count++) {
                strSessionIdFilter.append(":session_id_").append(count);
                if(count <= (llsSessionIds.size()-2)){
                    strSessionIdFilter.append(", ");
                }
            }

            strSQL = "SELECT " +
                    " json_merge(" +
                    "     json_object('username', username)," +
                    "     json_object('session_id', session_id)," +
                    "     json_object('transaction_type', transaction_type)," +
                    "     json_object('print', print)" +
                    " )" +
                    " as json_response, count(session_id) as count FROM "+strReceiptsTableName+" WHERE session_id IN("+strSessionIdFilter+") AND username = :username GROUP BY session_id;".trim();
            p = new NamedParameterStatement(MBankingDB.getConnection(), strSQL);
            p.setString("username", theUsername);
            for (int count = 0; count < llsSessionIds.size(); count++) {
                p.setString("session_id_"+count, llsSessionIds.get(count));
            }
            rs = p.executeQuery();
            while (rs.next()) {
                if(Integer.parseInt(rs.getString("count")) > 0){
                    JSONObject jsonObject = new JSONObject(rs.getString("json_response"));
                    jsonArray.put(jsonObject);
                }
            }

            rVal = jsonArray.toString();

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
    }*/
}
