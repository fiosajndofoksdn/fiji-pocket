package ij.util;
/** Only the numeric parser needed by ImageJ's TIFF decoder. */
public final class Tools {
    public static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (RuntimeException e) { return fallback; }
    }
}
