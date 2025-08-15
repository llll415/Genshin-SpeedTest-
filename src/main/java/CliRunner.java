import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class CliRunner {

    private ServerRegion region;
    private double sizeInGb = 1.0;

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("欢迎使用原神测速工具命令行交互模式！");

        while (true) {
            System.out.print("请选择服务器地区 (1: 中国, 2: 香港): ");
            String choice = scanner.nextLine().trim();
            if ("1".equals(choice)) {
                region = ServerRegion.CHINA;
                break;
            } else if ("2".equals(choice)) {
                region = ServerRegion.HONG_KONG;
                break;
            } else {
                System.out.println("输入无效，请输入 1 或 2。");
            }
        }

        while (true) {
            System.out.print("请输入测速大小 (GB, 直接回车默认为 1GB): ");
            String sizeInput = scanner.nextLine().trim();
            if (sizeInput.isEmpty()) {
                sizeInGb = 1.0;
                break;
            }
            try {
                sizeInGb = Double.parseDouble(sizeInput);
                if (sizeInGb > 0) break;
                System.out.println("错误: 测速大小必须是一个正数。");
            } catch (NumberFormatException e) {
                System.out.println("输入无效，请输入一个有效的数字。");
            }
        }
        scanner.close();

        runIpInfoTest();
        runPingTest();
        runSpeedTest();

        System.exit(0);
    }

    private void runIpInfoTest() {
        System.out.println("\n正在获取IP信息...");
        try {
            IpInfo info = getIpInfo();
            System.out.println("  IP        : " + info.getIp());
            System.out.println("  地理位置  : " + info.getLocation());
            System.out.println("  运营商    : " + info.getIsp());
        } catch (Exception e) {
            System.err.println("获取IP信息失败: " + e.getMessage());
        }
    }

    private void runPingTest() {
        System.out.println("\n正在测试延迟和抖动...");
        try {
            PingResult result = getPingResult(region.getPingHost());
            System.out.printf("  平均延迟  : %.2f ms\n", result.latency());
            System.out.printf("  抖动      : %.2f ms\n", result.jitter());
        } catch (Exception e) {
            System.err.println("延迟测试失败: " + e.getMessage());
        }
    }

    private void runSpeedTest() {
        System.out.printf("\n准备开始下载测速... (地区: %s, 大小: %.2f GB)\n", region.toString(), sizeInGb);
        double finalSpeed = 0;
        try {
            finalSpeed = executeSpeedTest(region, (int) (sizeInGb * 1024));
        } catch (Exception e) {
            System.err.println("\n测速时发生错误: " + e.getMessage());
        }
        System.out.printf("最终平均速度: %.2f Mbps (%.2f MB/s)\n", finalSpeed, finalSpeed / 8.0);
    }
    
    private IpInfo getIpInfo() throws Exception {
        TrustManager[] trustAllCerts = { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return null; }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        }};
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HostnameVerifier allHostsValid = (hostname, session) -> true;
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

        URL url = new URL("https://ip9.com.cn/get?ip");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        }
        conn.disconnect();

        JSONObject data = new JSONObject(response.toString()).getJSONObject("data");
        String ip = data.getString("ip");
        String location = (data.optString("country", "") + " " + data.optString("prov", "") + " " + data.optString("city", "")).trim();
        String isp = data.optString("isp", "未知");
        return new IpInfo(ip, location, isp);
    }
    
    private PingResult getPingResult(String pingHost) throws InterruptedException {
        final int PING_COUNT = 5;
        final int PING_TIMEOUT_MS = 2000;
        final int PING_PORT = 443;
        List<Double> latencies = new ArrayList<>();
        
        Proxy proxy = Proxy.NO_PROXY;
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select(new URI("socket://" + pingHost + ":" + PING_PORT));
            if (proxies != null && !proxies.isEmpty()) proxy = proxies.get(0);
        } catch (Exception e) {}

        for (int i = 0; i < PING_COUNT; i++) {
            try (Socket socket = new Socket(proxy)) {
                long startTime = System.nanoTime();
                socket.connect(new InetSocketAddress(pingHost, PING_PORT), PING_TIMEOUT_MS);
                long endTime = System.nanoTime();
                latencies.add((endTime - startTime) / 1_000_000.0);
            } catch (Exception e) {}
            if (i < PING_COUNT - 1) Thread.sleep(300);
        }
        if (latencies.isEmpty()) return new PingResult(0.0, 0.0);
        
        double avg = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double jitter = Math.sqrt(latencies.stream().mapToDouble(l -> Math.pow(l - avg, 2)).average().orElse(0.0));
        return new PingResult(avg, jitter);
    }

    private double executeSpeedTest(ServerRegion region, int targetSizeInMb) throws InterruptedException, ExecutionException {
        final int THREADS_PER_URL = 5;
        final double WARM_UP_PERCENTAGE = 0.10;
        final long targetDownloadSizeInBytes = (long) targetSizeInMb * 1024 * 1024;
        int totalThreads = region.getDownloadUrls().size() * THREADS_PER_URL;
        
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        ScheduledExecutorService progressReporter = Executors.newSingleThreadScheduledExecutor();

        final LongAdder totalBytesRead = new LongAdder();
        final AtomicBoolean stopSignal = new AtomicBoolean(false);
        
        final AtomicLong lastBytes = new AtomicLong(0);
        final AtomicLong lastTime = new AtomicLong(System.nanoTime());

        try {
            progressReporter.scheduleAtFixedRate(() -> {
                long currentTime = System.nanoTime();
                long currentBytes = totalBytesRead.sum();
                long timeDiffNs = currentTime - lastTime.get();
                long byteDiff = currentBytes - lastBytes.get();
                
                lastTime.set(currentTime);
                lastBytes.set(currentBytes);

                if (timeDiffNs > 0) {
                    // [修正] 这是正确的瞬时速度计算公式
                    double seconds = timeDiffNs / 1_000_000_000.0;
                    double speedMbps = (byteDiff * 8.0) / seconds / 1_000_000.0;
                    System.out.printf("\r当前速度: %7.2f Mbps (%.2f MB/s)  ", speedMbps, speedMbps / 8.0);
                }
            }, 1, 1, TimeUnit.SECONDS);

            final long warmUpThresholdBytes = (long) (targetDownloadSizeInBytes * WARM_UP_PERCENTAGE);
            final AtomicLong effectiveStartTime = new AtomicLong(0);
            final AtomicLong bytesAtEffectiveStart = new AtomicLong(0);

            List<Callable<Long>> tasks = new ArrayList<>();
            for (String url : region.getDownloadUrls()) {
                for (int i = 0; i < THREADS_PER_URL; i++) {
                    tasks.add(() -> {
                        long bytesRead = 0;
                        try (InputStream in = new URL(url).openStream()) {
                            byte[] buffer = new byte[8192];
                            int n;
                            while ((n = in.read(buffer)) != -1) {
                                if (stopSignal.get()) break;
                                totalBytesRead.add(n);
                                bytesRead += n;
                                long currentTotal = totalBytesRead.sum();

                                if (effectiveStartTime.get() == 0 && currentTotal >= warmUpThresholdBytes) {
                                    effectiveStartTime.compareAndSet(0, System.nanoTime());
                                    bytesAtEffectiveStart.compareAndSet(0, currentTotal);
                                }
                                if (currentTotal >= targetDownloadSizeInBytes) {
                                    stopSignal.set(true);
                                }
                            }
                        } catch (Exception e) {}
                        return bytesRead;
                    });
                }
            }
            
            long absoluteStartTime = System.nanoTime();
            executor.invokeAll(tasks);
            
            long absoluteEndTime = System.nanoTime();
            if (effectiveStartTime.get() == 0) {
                effectiveStartTime.set(absoluteStartTime);
            }
            
            long measuredDurationNs = absoluteEndTime - effectiveStartTime.get();
            long measuredBytes = totalBytesRead.sum() - bytesAtEffectiveStart.get();
            
            if (measuredDurationNs <= 0) return 0.0;
            double effectiveTimeInSeconds = measuredDurationNs / 1_000_000_000.0;
            return (measuredBytes * 8.0) / (effectiveTimeInSeconds * 1_000_000.0);

        } finally {
            progressReporter.shutdownNow();
            executor.shutdownNow();
            System.out.print("\r                                                   \r");
        }
    }
}