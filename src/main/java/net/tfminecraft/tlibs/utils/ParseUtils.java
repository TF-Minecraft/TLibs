package net.tfminecraft.tlibs.utils;

public class ParseUtils {
    
    public static Double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static Integer parseInt(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isPositive(Double value) {
        return value != null && value > 0;
    }
    public static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }
}
