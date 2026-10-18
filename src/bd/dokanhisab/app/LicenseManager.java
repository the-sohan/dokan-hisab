package bd.dokanhisab.app;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Offline feature entitlement. No activation or device usage is reported to a server. */
public final class LicenseManager {
    private static Set<String> allowed;
    private static synchronized Set<String> hashes(Context context) {
        if (allowed!=null) return allowed;
        Set<String> result=new HashSet<>();
        try (BufferedReader r=new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.license_hashes),StandardCharsets.UTF_8))) {
            String line;while((line=r.readLine())!=null)if(!line.trim().isEmpty())result.add(line.trim());
        }catch(Exception ex){throw new IllegalStateException("লাইসেন্স তালিকা পড়া যায়নি।",ex);}
        allowed=result;return result;
    }
    static String sha256(String text) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    public static boolean isPro(Context context) {return hashes(context).contains(context.getSharedPreferences("license",0).getString("hash",""));}
    public static void activate(Context context,String key) {
        String normalized=key.replaceAll("\\s+","").toUpperCase(Locale.ROOT),hash=sha256(normalized);
        if(!hashes(context).contains(hash))throw new IllegalArgumentException("লাইসেন্স কী সঠিক নয়। আবার মিলিয়ে লিখুন।");
        if(!context.getSharedPreferences("license",0).edit().putString("hash",hash).putString("suffix",normalized.substring(normalized.length()-5)).commit())throw new IllegalStateException("লাইসেন্স সেভ করা যায়নি।");
    }
    public static String suffix(Context context){return context.getSharedPreferences("license",0).getString("suffix","");}
    public static void requirePro(Context context){if(!isPro(context))throw new IllegalStateException("ব্যাকআপ ব্যবহার করতে Pro লাইসেন্স সক্রিয় করুন।");}
}
