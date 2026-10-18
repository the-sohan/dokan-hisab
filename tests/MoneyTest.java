import bd.dokanhisab.app.Money;

public final class MoneyTest {
    private static int checks;
    private static void eq(Object actual,Object expected) { checks++; if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected); }
    private static void invalid(String value) { checks++; try {Money.parse(value);}catch(IllegalArgumentException expected){return;}throw new AssertionError("Accepted invalid amount: "+value); }
    public static void main(String[] args) {
        eq(Money.parse("৫০০.৫০"),50050L); eq(Money.parse("0.01"),1L); eq(Money.parse("9999999.99"),999999999L);
        eq(Money.format(0),"৳ ০"); eq(Money.format(100),"৳ ১"); eq(Money.format(10050),"৳ ১০০.৫০"); eq(Money.format(123456789),"৳ ১২,৩৪,৫৬৭.৮৯");
        eq(Money.format(1234567),"৳ ১২,৩৪৫.৬৭"); eq(Money.format(123456),"৳ ১,২৩৪.৫৬"); eq(Money.format(1000000000L),"৳ ১,০০,০০,০০০");
        eq(Money.input(50050),"500.5"); eq(Money.phone("+880 1712-345678"),"01712345678"); eq(Money.phone("০১৭১২৩৪৫৬৭৮"),"01712345678"); eq(Money.phone(""),"");
        for(String value:new String[]{"0","-2","1.001","1e4","NaN","10000000","1,000","",".","1..2"})invalid(value);
        System.out.println("PASS: "+checks+" money and phone checks");
    }
}
