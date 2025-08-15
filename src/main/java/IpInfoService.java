import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

public class IpInfoService extends Service<IpInfo> {

    private static final String API_URL = "https://ip9.com.cn/get?ip";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36";

    static {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            System.out.println("SSL证书信任管理器已成功安装!");
        } catch (Exception e) {
            System.err.println("安装SSL证书信任管理器时发生错误: " + e.getMessage());
        }
    }

    @Override
    protected Task<IpInfo> createTask() {
        return new Task<>() {
            @Override
            protected IpInfo call() throws Exception {
                System.out.println("正在获取IP地址信息...");
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", USER_AGENT);

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                conn.disconnect();

                JSONObject data = new JSONObject(response.toString()).getJSONObject("data");
                String ip = data.getString("ip");
                String location = (data.optString("country", "") + " " + data.optString("prov", "") + " " + data.optString("city", "")).trim();
                String isp = data.optString("isp", "未知");
                
                System.out.println("IP信息获取成功: " + ip);
                return new IpInfo(ip, location, isp);
            }
        };
    }
}