import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class SpeedTestService extends Service<Double> {
    
    private static final int THREADS_PER_URL = 5;
    private static final int UPDATE_INTERVAL_MS = 250;
    private static final double WARM_UP_PERCENTAGE = 0.10;

    private final DoubleProperty speed = new SimpleDoubleProperty(0);
    private ExecutorService downloadExecutor;
    private long targetDownloadSizeInBytes = 1024 * 1024 * 1024;
    private List<String> currentDownloadUrls;
    private int currentTotalThreads;

    public SpeedTestService() {}

    public void setTargetDownloadSizeInMb(int mb) { this.targetDownloadSizeInBytes = (long) mb * 1024 * 1024; }
    
    public void setRegion(ServerRegion region) {
        this.currentDownloadUrls = region.getDownloadUrls();
        this.currentTotalThreads = this.currentDownloadUrls.size() * THREADS_PER_URL;
        System.out.println("测速地区已设置为: " + region + ", 总线程数: " + this.currentTotalThreads);
    }
    
    public void shutdownExecutor() {
        if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
            downloadExecutor.shutdownNow();
        }
    }
    
    public final DoubleProperty speedProperty() { return speed; }

    @Override
    protected Task<Double> createTask() {
        if (downloadExecutor == null || downloadExecutor.isShutdown() || ((ThreadPoolExecutor)downloadExecutor).getCorePoolSize() != this.currentTotalThreads) {
            if (downloadExecutor != null) downloadExecutor.shutdownNow();
            downloadExecutor = Executors.newFixedThreadPool(currentTotalThreads, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
        }

        return new Task<>() {
            private ScheduledExecutorService scheduler;
            
            @Override
            protected Double call() throws Exception {
                final LongAdder totalBytesRead = new LongAdder();
                final AtomicBoolean stopSignal = new AtomicBoolean(false);
                final long absoluteStartTime = System.nanoTime();
                final AtomicLong effectiveStartTime = new AtomicLong(0);
                final AtomicLong bytesAtEffectiveStart = new AtomicLong(0);
                final long warmUpThresholdBytes = (long) (targetDownloadSizeInBytes * WARM_UP_PERCENTAGE);

                scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
                
                final LongAdder lastBytesForUi = new LongAdder();
                final AtomicLong lastUiUpdateTime = new AtomicLong(System.nanoTime());
                scheduler.scheduleAtFixedRate(() -> {
                    long now = System.nanoTime();
                    long currentTotalBytes = totalBytesRead.sum();
                    long timeDiff = now - lastUiUpdateTime.get();
                    long byteDiff = currentTotalBytes - lastBytesForUi.sum();
                    lastUiUpdateTime.set(now);
                    lastBytesForUi.add(byteDiff);
                    if (timeDiff > 0) {
                        double intervalSpeed = (byteDiff * 8.0) / (timeDiff / 1_000_000_000.0 * 1_000_000.0);
                        Platform.runLater(() -> speed.set(intervalSpeed));
                    }
                }, 1000, UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);

                List<Future<Long>> futureList = new ArrayList<>();
                for (String url : currentDownloadUrls) {
                    for (int i = 0; i < THREADS_PER_URL; i++) {
                        Callable<Long> downloadTask = createDownloadTask(url, totalBytesRead, stopSignal, 
                                                                         effectiveStartTime, bytesAtEffectiveStart, warmUpThresholdBytes);
                        futureList.add(downloadExecutor.submit(downloadTask));
                    }
                }

                for (Future<Long> future : futureList) {
                    try { future.get(); } catch (Exception e) { }
                }

                scheduler.shutdownNow();
                
                long absoluteEndTime = System.nanoTime();
                if (effectiveStartTime.get() == 0) {
                    effectiveStartTime.set(absoluteStartTime);
                    bytesAtEffectiveStart.set(0);
                }
                long measuredDuration = absoluteEndTime - effectiveStartTime.get();
                long measuredBytes = totalBytesRead.sum() - bytesAtEffectiveStart.get();
                double effectiveTimeInSeconds = measuredDuration / 1_000_000_000.0;
                if (effectiveTimeInSeconds <= 0) return 0.0;
                
                double averageSpeed = (measuredBytes * 8.0) / (effectiveTimeInSeconds * 1_000_000.0);
                Platform.runLater(() -> speed.set(averageSpeed));
                
                return averageSpeed;
            }
            
            private Callable<Long> createDownloadTask(String url, LongAdder totalBytesRead, AtomicBoolean stopSignal, 
                                                     AtomicLong effectiveStartTime, AtomicLong bytesAtEffectiveStart, long warmUpThresholdBytes) {
                return () -> {
                    long bytesRead = 0;
                    try (InputStream inputStream = new URL(url).openStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesReadFromStream;
                        while ((bytesReadFromStream = inputStream.read(buffer)) != -1) {
                            if (stopSignal.get() || isCancelled()) break;
                            
                            bytesRead += bytesReadFromStream;
                            totalBytesRead.add(bytesReadFromStream);
                            long currentTotalBytes = totalBytesRead.sum();

                            if (effectiveStartTime.get() == 0 && currentTotalBytes >= warmUpThresholdBytes) {
                                effectiveStartTime.set(System.nanoTime());
                                bytesAtEffectiveStart.set(currentTotalBytes);
                                System.out.println("预热完成, 开始正式测量...");
                            }
                            if (currentTotalBytes >= targetDownloadSizeInBytes) {
                                stopSignal.set(true);
                            }
                        }
                    } catch (Exception e) {
                        if (!isCancelled() && !stopSignal.get()) e.printStackTrace();
                    }
                    return bytesRead;
                };
            }
        };
    }
}