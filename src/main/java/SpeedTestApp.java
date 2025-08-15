import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

public class SpeedTestApp extends Application {

    private Speedometer speedometer;
    private Button startButton;
    private Label proxyLabel, ipLabel, locationLabel, ispLabel, resultLabel;
    private Label liveLatencyLabel, liveJitterLabel;
    private VBox statsPane;
    private ComboBox<ServerRegion> regionSelector;
    private ComboBox<String> sizeSelector;
    private PingTestService pingTestService;
    private SpeedTestService speedTestService;
    private IpInfoService ipInfoService;
    private StackPane displayStage;

    @Override
    public void start(Stage primaryStage) {
        speedometer = new Speedometer();
        startButton = new Button("开始测速");
        proxyLabel = new Label("代理: 正在检测...");
        ipLabel = new Label("IP: 正在获取...");
        locationLabel = new Label("地理位置: 正在获取...");
        ispLabel = new Label("运营商: 正在获取...");
        resultLabel = new Label();
        liveLatencyLabel = new Label("- ms");
        liveJitterLabel = new Label("- ms");
        statsPane = createStatsPane(liveLatencyLabel, liveJitterLabel, resultLabel);
        statsPane.setVisible(false);

        displayStage = new StackPane(speedometer, statsPane);
        VBox.setVgrow(displayStage, Priority.ALWAYS);

        Label regionLabel = new Label("服务器地区:");
        regionSelector = new ComboBox<>(FXCollections.observableArrayList(ServerRegion.values()));
        regionSelector.setValue(ServerRegion.CHINA);
        
        Label sizePromptLabel = new Label("测速大小:");
        sizeSelector = new ComboBox<>(FXCollections.observableArrayList("0.5", "1", "2", "3", "4", "5"));
        sizeSelector.setEditable(true);
        sizeSelector.setValue("1");
        sizeSelector.setPrefWidth(80);
        Label sizeUnitLabel = new Label("GB");
        
        HBox configBox = new HBox(15, 
            new HBox(5, regionLabel, regionSelector),
            new HBox(5, sizePromptLabel, sizeSelector, sizeUnitLabel)
        );
        configBox.setAlignment(Pos.CENTER);
        
        Label disclaimerLabel = new Label("提示: 本工具测得为净速度(应用层速度), 与任务管理器的物理层速度存在协议开销差异属正常现象。");
        disclaimerLabel.setWrapText(true);
        disclaimerLabel.setTextAlignment(TextAlignment.CENTER);
        Hyperlink apiLink = new Hyperlink("免费IP归属地查询API");
        apiLink.setOnAction(e -> getHostServices().showDocument("https://www.ip9.com.cn"));
        HBox apiBox = new HBox(new Label("数据来源: "), apiLink);
        apiBox.setAlignment(Pos.CENTER);
        Label driverLabel = new Label("下载测试由米哈游强力驱动bushi(");
        driverLabel.setTextAlignment(TextAlignment.CENTER);

        styleControls();
        disclaimerLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #858796;");
        apiBox.setStyle("-fx-font-size: 11px;");
        driverLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #858796;");

        VBox root = new VBox(8);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f8f9fa;");
        
        VBox controls = new VBox(10, configBox, startButton);
        controls.setAlignment(Pos.CENTER);
        
        VBox footer = new VBox(4, proxyLabel, ipLabel, locationLabel, ispLabel, apiBox, driverLabel, disclaimerLabel);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8, 0, 0, 0));

        root.getChildren().addAll(displayStage, statsPane, controls, footer);

        Scene scene = new Scene(root, 430, 630);
        primaryStage.setTitle("原神测速！");
        primaryStage.setScene(scene);
        
        primaryStage.show();

        pingTestService = new PingTestService();
        speedTestService = new SpeedTestService();
        ipInfoService = new IpInfoService();
        setupActions();
        bindServicesToUI();
        detectAndShowProxyInfo();
        ipInfoService.start();

        primaryStage.setOnCloseRequest(event -> {
            if (pingTestService != null) pingTestService.cancel();
            if (speedTestService != null) {
                speedTestService.cancel();
                speedTestService.shutdownExecutor();
            }
            Platform.exit();
            System.exit(0);
        });
    }
    
    private VBox createStatsPane(Label latencyValue, Label jitterValue, Label resultValue) {
        HBox latencyRow = new HBox(20, 
            createResultItem("延迟:", latencyValue), 
            createResultItem("抖动:", jitterValue)
        );
        latencyRow.setAlignment(Pos.CENTER);
        
        VBox pane = new VBox(8, latencyRow, resultValue);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
    
    private HBox createResultItem(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        Font titleFont = Font.font("System", FontWeight.NORMAL, 16);
        titleLabel.setFont(titleFont);
        titleLabel.setTextFill(Color.web("#5a5c69"));
        Font valueFont = Font.font("System", FontWeight.BOLD, 16);
        valueLabel.setFont(valueFont);
        valueLabel.setTextFill(Color.web("#4e73df"));
        return new HBox(5, titleLabel, valueLabel);
    }
    
    private void styleControls() {
        startButton.setStyle("-fx-background-color: #4e73df; -fx-text-fill: white; -fx-font-size: 16px; -fx-background-radius: 5;");
        startButton.setPrefSize(140, 45);
        Font labelFont = Font.font("System", FontWeight.NORMAL, 12);
        proxyLabel.setFont(labelFont);
        ipLabel.setFont(labelFont);
        locationLabel.setFont(labelFont);
        ispLabel.setFont(labelFont);
        resultLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        Color labelColor = Color.web("#858796");
        proxyLabel.setTextFill(labelColor);
        ipLabel.setTextFill(labelColor);
        locationLabel.setTextFill(labelColor);
        ispLabel.setTextFill(labelColor);
    }

    private void detectAndShowProxyInfo() {
        new Thread(() -> {
            try {
                List<Proxy> proxies = ProxySelector.getDefault().select(new URI("https://ip9.com.cn"));
                if (proxies != null && !proxies.isEmpty()) {
                    Proxy proxy = proxies.get(0);
                    if (proxy != Proxy.NO_PROXY) {
                        String proxyInfo = "代理: " + proxy.type() + " @ " + proxy.address();
                        System.out.println("检测到系统代理: " + proxyInfo);
                        Platform.runLater(() -> proxyLabel.setText(proxyInfo));
                        return;
                    }
                }
                Platform.runLater(() -> proxyLabel.setText("代理: 未使用"));
            } catch (Exception e) {
                System.err.println("检测代理时发生错误: " + e.getMessage());
                Platform.runLater(() -> proxyLabel.setText("代理: 检测失败"));
            }
        }).start();
    }
    
    private void handleButtonClick() {
        if (!pingTestService.isRunning() && !speedTestService.isRunning()) {
            statsPane.setVisible(false);
            resultLabel.setText("");
            speedometer.setValue(0);
            
            ServerRegion selectedRegion = regionSelector.getValue();
            pingTestService.setHost(selectedRegion.getPingHost());
            
            int sizeInMb = parseSizeInGb(sizeSelector.getValue());
            speedTestService.setTargetDownloadSizeInMb(sizeInMb);
            speedTestService.setRegion(selectedRegion);
            
            pingTestService.restart();
        } else if (speedTestService.isRunning()) {
            speedTestService.cancel();
        }
    }

    private void setupActions() {
        startButton.setOnAction(event -> handleButtonClick());
    }

    private int parseSizeInGb(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) return 1024;
        try {
            double valueInGb = Double.parseDouble(userInput.trim());
            if (valueInGb <= 0) return 1024;
            return (int) (valueInGb * 1024);
        } catch (NumberFormatException e) {
            return 1024;
        }
    }

    private void bindServicesToUI() {
        startButton.textProperty().bind(Bindings.createStringBinding(() -> {
            if (!pingTestService.isRunning() && !speedTestService.isRunning()) return "开始测速";
            if (pingTestService.isRunning()) return "测试延迟...";
            if (speedTestService.isRunning()) return "结束";
            return "重新测速";
        }, pingTestService.runningProperty(), speedTestService.runningProperty()));
        
        startButton.disableProperty().bind(pingTestService.runningProperty());
        regionSelector.disableProperty().bind(pingTestService.runningProperty().or(speedTestService.runningProperty()));
        sizeSelector.disableProperty().bind(pingTestService.runningProperty().or(speedTestService.runningProperty()));

        pingTestService.setOnSucceeded(e -> {
            PingResult result = pingTestService.getValue();
            liveLatencyLabel.setText(String.format("%.2f ms", result.latency()));
            liveJitterLabel.setText(String.format("%.2f ms", result.jitter()));
            statsPane.setVisible(true);
            speedTestService.restart();
        });

        speedTestService.setOnSucceeded(e -> {
            double averageSpeed = speedTestService.getValue();
            resultLabel.setText(String.format("最终平均速度: %.2f Mbps (%.2f MB/s)", averageSpeed, averageSpeed / 8.0));
            statsPane.setVisible(true);
        });
        
        speedTestService.setOnCancelled(e -> {
             Platform.runLater(() -> {
                 speedometer.setValue(0);
                 resultLabel.setText("测试已取消");
                 statsPane.setVisible(true);
             });
        });
        
        speedTestService.speedProperty().addListener((obs, o, n) -> speedometer.setValue(n.doubleValue()));
        
        ipInfoService.setOnSucceeded(event -> {
            IpInfo info = ipInfoService.getValue();
            Platform.runLater(() -> {
                ipLabel.setText("IP: " + info.getIp());
                locationLabel.setText("地理位置: " + info.getLocation());
                ispLabel.setText("运营商: " + info.getIsp());
            });
        });
        
        ipInfoService.setOnFailed(event -> {
            Throwable e = ipInfoService.getException();
            Platform.runLater(() -> {
                String errorMsg = "IP获取失败: " + e.getClass().getSimpleName();
                ipLabel.setText(errorMsg);
                locationLabel.setText("请检查网络或防火墙设置");
                ispLabel.setText("");
            });
            e.printStackTrace();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}