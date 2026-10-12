package ij;
/** Minimal platform-neutral logging/progress adapter; no desktop environment. */
public final class IJ {
    public static final boolean debugMode = false;
    public static void log(String text) { System.err.println(text); }
    public static void showStatus(String text) {}
    public static void showProgress(double value) {}
    public static void showProgress(int value, int total) {}
    public static void showProgress(long value, long total) {}
    public static String addSeparator(String path) {
        return path.isEmpty() || path.endsWith(java.io.File.separator) ? path : path+java.io.File.separator;
    }
}
