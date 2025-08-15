public class IpInfo {
    private final String ip;
    private final String location;
    private final String isp;

    public IpInfo(String ip, String location, String isp) {
        this.ip = ip;
        this.location = location;
        this.isp = isp;
    }

    public String getIp() {
        return ip;
    }

    public String getLocation() {
        return location;
    }

    public String getIsp() {
        return isp;
    }
}