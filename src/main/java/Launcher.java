public class Launcher {
    public static void main(String[] args) {
		System.setProperty("java.net.useSystemProxies", "true");

        boolean isCliMode = false;
        for (String arg : args) {
            if ("--cli".equals(arg)) {
                isCliMode = true;
                break;
            }
        }

        if (isCliMode) {
            CliRunner runner = new CliRunner();
            runner.run();
        } else {
            SpeedTestApp.main(args);
        }
    }
}