package bd.dokanhisab.app;

import java.math.BigDecimal;

/** Monetary values are integer poisha; floating point is never used. */
public final class Money {
    public static String latin(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) out.append(c >= '০' && c <= '৯' ? (char) ('0' + c - '০') : c);
        return out.toString();
    }
    public static String bn(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) out.append(c >= '0' && c <= '9' ? (char) ('০' + c - '0') : c);
        return out.toString();
    }
    public static long parse(String text) {
        String value = latin(text.trim());
        if (!value.matches("[0-9]{1,7}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("সঠিক টাকার পরিমাণ লিখুন (সর্বোচ্চ ৯৯,৯৯,৯৯৯.৯৯)।");
        long amount = new BigDecimal(value).movePointRight(2).longValueExact();
        if (amount <= 0) throw new IllegalArgumentException("টাকার পরিমাণ শূন্যের বেশি হতে হবে।");
        return amount;
    }
    public static String input(long amount) { return BigDecimal.valueOf(amount, 2).stripTrailingZeros().toPlainString(); }
    public static String format(long amount) {
        String number = Long.toString(Math.abs(amount / 100));
        StringBuilder grouped = new StringBuilder();
        int first = number.length() > 3 ? (number.length() - 3) % 2 : 0;
        if (number.length() > 3 && first == 0) first = 2;
        int index = 0;
        if (number.length() > 3) { grouped.append(number, 0, first); index = first; }
        while (number.length() - index > 3) {
            if (grouped.length() > 0) grouped.append(',');
            grouped.append(number, index, index + 2); index += 2;
        }
        if (grouped.length() > 0) grouped.append(',');
        grouped.append(number.substring(index));
        long fraction = Math.abs(amount % 100);
        if (fraction != 0) grouped.append('.').append(fraction < 10 ? "0" : "").append(fraction);
        return "৳ " + (amount < 0 ? "−" : "") + bn(grouped.toString());
    }
    public static String phone(String raw) {
        String value = latin(raw).replaceAll("[\\s()\\-]", "");
        if (value.isEmpty()) return value;
        if (value.startsWith("+880")) value = "0" + value.substring(4);
        else if (value.startsWith("880")) value = "0" + value.substring(3);
        if (!value.matches("01[3-9][0-9]{8}")) throw new IllegalArgumentException("সঠিক ১১ সংখ্যার বাংলাদেশি মোবাইল নম্বর লিখুন।");
        return value;
    }
}
