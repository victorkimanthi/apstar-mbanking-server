package ke.skyworld.mbanking.nav;

import ke.skyworld.lib.mbanking.utils.Crypto;

public class Test {
    public static void main(String[] args) {
        try {

            //MBankingAPI mBankingAPI = new MBankingAPI();
            //mBankingAPI.processOnStartup();
            Crypto crypto = new Crypto();


            String strEncryptionKey = "Vx@3GhTu*7nbHJg^)SYTDhs>pij?2H";
            String strClearPasswrd = crypto.decrypt(strEncryptionKey, "N0shVWqrZpOpLOzm3QM9H66qMwd19JdvCLmJl4AIbrI=");


            System.out.println(strClearPasswrd);


        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}