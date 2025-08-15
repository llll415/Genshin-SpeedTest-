import java.util.Objects;

public final class PingResult {
    private final double latency;
    private final double jitter;

    public PingResult(double latency, double jitter) {
        this.latency = latency;
        this.jitter = jitter;
    }

    public double latency() {
        return latency;
    }

    public double jitter() {
        return jitter;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PingResult) obj;
        return Double.doubleToLongBits(this.latency) == Double.doubleToLongBits(that.latency) &&
               Double.doubleToLongBits(this.jitter) == Double.doubleToLongBits(that.jitter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(latency, jitter);
    }

    @Override
    public String toString() {
        return "PingResult[" +
               "latency=" + latency + ", " +
               "jitter=" + jitter + ']';
    }
}