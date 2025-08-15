import javafx.concurrent.Service;
import javafx.concurrent.Task;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class PingTestService extends Service<PingResult> {

    private String pingHost = "autopatchcn.yuanshen.com";
    private static final int PING_PORT = 443;
    private static final int PING_TIMEOUT_MS = 2000;
    private static final int PING_COUNT = 5;

    public void setHost(String host) {
        this.pingHost = host;
    }

    @Override
    protected Task<PingResult> createTask() {
        return new Task<>() {
            @Override
            protected PingResult call() throws Exception {
                List<Double> latencies = new ArrayList<>();
                System.out.println("开始延迟测试，目标: " + pingHost);

                Proxy proxy = Proxy.NO_PROXY;
                try {
                    List<Proxy> proxies = ProxySelector.getDefault().select(new URI("socket://" + pingHost + ":" + PING_PORT));
                    if (proxies != null && !proxies.isEmpty()) {
                        proxy = proxies.get(0);
                    }
                } catch (Exception e) {
                    System.err.println("无法为Ping测试解析代理: " + e.getMessage());
                }

                if (proxy != Proxy.NO_PROXY) {
                    System.out.println("正在通过代理 " + proxy + " 进行延迟测试...");
                }

                for (int i = 0; i < PING_COUNT; i++) {
                    if (isCancelled()) break;
                    try (Socket socket = new Socket(proxy)) {
                        long startTime = System.nanoTime();
                        socket.connect(new InetSocketAddress(pingHost, PING_PORT), PING_TIMEOUT_MS);
                        long endTime = System.nanoTime();
                        latencies.add((endTime - startTime) / 1_000_000.0);
                    } catch (Exception e) {
                        System.err.println("Ping " + (i + 1) + " 失败: " + e.getMessage());
                    }
                    if (i < PING_COUNT -1) Thread.sleep(300);
                }

                if (latencies.isEmpty()) {
                    System.err.println("所有Ping测试均失败，延迟和抖动将计为0。");
                    return new PingResult(0.0, 0.0);
                }
                
                double averageLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double jitter = Math.sqrt(latencies.stream().mapToDouble(l -> Math.pow(l - averageLatency, 2)).average().orElse(0.0));

                System.out.printf("延迟测试完成 - 平均延迟: %.2f ms, 抖动: %.2f ms%n", averageLatency, jitter);
                return new PingResult(averageLatency, jitter);
            }
        };
    }
}