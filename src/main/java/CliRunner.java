import javafx.application.Platform;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CliRunner {

    private ServerRegion region;
    private double sizeInGb = 1.0;

    public void run() {
        new javafx.embed.swing.JFXPanel();

        Scanner scanner = new Scanner(System.in);
        System.out.println("欢迎使用原神测速命令行交互模式！");

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
                if (sizeInGb > 0) {
                    break;
                }
                System.out.println("错误: 测速大小必须是一个正数。");
            } catch (NumberFormatException e) {
                System.out.println("输入无效，请输入一个有效的数字。");
            }
        }

        scanner.close();

        runIpInfoService();
        runPingTestService();
        runSpeedTestService();

        Platform.exit();
        System.exit(0);
    }

    private void runIpInfoService() {
        System.out.println("\n正在获取IP信息...");
        IpInfoService ipInfoService = new IpInfoService();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<IpInfo> ipInfoRef = new AtomicReference<>();

        ipInfoService.setOnSucceeded(event -> {
            ipInfoRef.set(ipInfoService.getValue());
            latch.countDown();
        });
        ipInfoService.setOnFailed(event -> {
            System.err.println("获取IP信息失败: " + ipInfoService.getException().getMessage());
            latch.countDown();
        });

        ipInfoService.start();
        try {
            latch.await(10, TimeUnit.SECONDS);
            IpInfo info = ipInfoRef.get();
            if (info != null) {
                System.out.println("  IP        : " + info.getIp());
                System.out.println("  地理位置  : " + info.getLocation());
                System.out.println("  运营商    : " + info.getIsp());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("获取IP信息被中断。");
        }
    }

    private void runPingTestService() {
        System.out.println("\n正在测试延迟和抖动...");
        PingTestService pingTestService = new PingTestService();
        pingTestService.setHost(region.getPingHost());
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PingResult> pingResultRef = new AtomicReference<>();

        pingTestService.setOnSucceeded(event -> {
            pingResultRef.set(pingTestService.getValue());
            latch.countDown();
        });
        pingTestService.setOnFailed(event -> {
            System.err.println("延迟测试失败: " + pingTestService.getException().getMessage());
            latch.countDown();
        });

        pingTestService.start();
        try {
            latch.await(15, TimeUnit.SECONDS);
            PingResult result = pingResultRef.get();
            if (result != null) {
                System.out.printf("  平均延迟  : %.2f ms\n", result.latency());
                System.out.printf("  抖动      : %.2f ms\n", result.jitter());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("延迟测试被中断。");
        }
    }

    private void runSpeedTestService() {
        System.out.printf("\n准备开始下载测速... (地区: %s, 大小: %.2f GB)\n", region.toString(), sizeInGb);
        SpeedTestService speedTestService = new SpeedTestService();
        speedTestService.setRegion(region);
        speedTestService.setTargetDownloadSizeInMb((int) (sizeInGb * 1024));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Double> finalSpeed = new AtomicReference<>();

        speedTestService.setOnSucceeded(event -> {
            finalSpeed.set(speedTestService.getValue());
            latch.countDown();
        });
        speedTestService.setOnFailed(event -> {
            System.err.println("\n测速时发生错误: " + speedTestService.getException().getMessage());
            latch.countDown();
        });
        speedTestService.setOnCancelled(event -> {
            System.out.println("\n测试被用户取消。");
            latch.countDown();
        });

        ScheduledExecutorService progressReporter = Executors.newSingleThreadScheduledExecutor();
        progressReporter.scheduleAtFixedRate(() -> {
            double currentSpeed = speedTestService.speedProperty().get();
            System.out.printf("\r当前速度: %7.2f Mbps (%.2f MB/s)", currentSpeed, currentSpeed / 8.0);
        }, 0, 500, TimeUnit.MILLISECONDS);

        speedTestService.start();
        try {
            latch.await();
            progressReporter.shutdown();
            speedTestService.shutdownExecutor();
            System.out.println();
            Double result = finalSpeed.get();
            if (result != null) {
                System.out.printf("最终平均速度: %.2f Mbps (%.2f MB/s)\n", result, result / 8.0);
            } else {
                System.err.println("未能获取最终测速结果。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("\n测速被中断。");
            speedTestService.cancel();
            speedTestService.shutdownExecutor();
            progressReporter.shutdown();
        }
    }
}