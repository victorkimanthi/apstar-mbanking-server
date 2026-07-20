package ke.skyworld.mbanking.cbs.dynamics;

import ke.skyworld.mbanking.cbs.ApStarCBSParams;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.net.URL;

public class NavisionConnectionManager {


    public static BasicCredentialsProvider getCredentialsProvider() {
        try {
            String strURL = ApStarCBSParams.getSOAPURL();

            URL url = new URL(strURL);

            String strHost = extractHostFromURL(url);
            int strPort = extractPortFromURL(url);

            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(new AuthScope(strHost, strPort)),
                    //basic
                    /*new UsernamePasswordCredentials(
                            NavisionConnectionManager.params.getCoreBankingUsername(),
                            NavisionConnectionManager.params.getCoreBankingPassword().toCharArray()
                    )*/
                    //ntlm

                    //new NTCredentials(NavisionConnectionManager.params.getCoreBankingUsername(),NavisionConnectionManager.params.getCoreBankingPassword().toCharArray(), ".",NavisionConnectionManager.params.getCoreBankingDomain())


                    new NTCredentials(
                            ApStarCBSParams.getUser(),
                            ApStarCBSParams.getPassword().toCharArray(),
                            ApStarCBSParams.getWorkstation(),
                            ApStarCBSParams.getDomain()
                    )

            );
            return credentialsProvider;
        } catch (Exception e) {
            System.err.println("Navision.getCredentialsProvider(): Error getting Credentials Provider Object: " + e.getMessage());
            if (ApStarCBSParams.isLogRequestEnabled())
                e.printStackTrace();
        }
        return null;
    }

    public static CloseableHttpClient getHttpClient() {
        try {
            return HttpClients.custom().setDefaultCredentialsProvider(getCredentialsProvider()).build();
        } catch (Exception e) {
            System.err.println("Navision.getHttpClient(): Error getting Http Client Object: " + e.getMessage());
            if (ApStarCBSParams.isLogResponseEnabled())
                e.printStackTrace();
        }
        return null;
    }

    public static String extractHostFromURL(URL url) {
        return url.getHost();
    }

    // Function to extract the port from a URL
    public static int extractPortFromURL(URL url) {
        int port = url.getPort();

        if (port == -1) {
            if (url.getProtocol().equals("http")) {
                port = 80;
            } else if (url.getProtocol().equals("https")) {
                port = 443;
            }
        }

        return port;
    }
}