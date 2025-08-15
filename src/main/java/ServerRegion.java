import java.util.List;

public enum ServerRegion {
    CHINA("中国", "autopatchcn.yuanshen.com", List.of(
        "https://autopatchcn.yuanshen.com/client_app/download/pc_zip/20250314105313_OcRjEyGXX8Txtqm4/YuanShen_5.5.0.zip.001",
        "https://autopatchcn.juequling.com/package_download/op/client_app/download/20250701110943_jl75SMuF4iArnIDR/VolumeZip/juequling_2.1.0_AS.zip.001"
    )),

    HONG_KONG("香港", "autopatchhk.yuanshen.com", List.of(
        "https://autopatchhk.yuanshen.com/client_app/download/pc_zip/20250314110016_HcIQuDGRmsbByeAE/GenshinImpact_5.5.0.zip.005",
        "https://autopatchos.zenlesszonezero.com/package_download/op/client_app/os/download/20250701101616_TN2VXgNxxOr6jC4P/VolumeZip/ZenlessZoneZero_2.1.0_AS.zip.007"
    ));

    private final String displayName;
    private final String pingHost;
    private final List<String> downloadUrls;

    ServerRegion(String displayName, String pingHost, List<String> downloadUrls) {
        this.displayName = displayName;
        this.pingHost = pingHost;
        this.downloadUrls = downloadUrls;
    }

    public String getPingHost() {
        return pingHost;
    }

    public List<String> getDownloadUrls() {
        return downloadUrls;
    }

    @Override
    public String toString() {
        return displayName;
    }
}