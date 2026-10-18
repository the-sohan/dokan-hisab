package bd.dokanhisab.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Versioned, bounded data interchange. Import never executes SQL from a file. */
public final class BackupCodec {
    public static final int MAX_BYTES=10*1024*1024;
    public static final class Snapshot {
        public JSONObject envelope;
        public JSONArray customers,entries;
        public String hash;
        public long createdAt;
        public int customerCount,entryCount;
        public byte[] bytes(){return envelope.toString().getBytes(StandardCharsets.UTF_8);}
    }
    static Snapshot exportDatabase(SQLiteDatabase db) throws Exception {
        JSONObject data=new JSONObject();db.beginTransactionNonExclusive();
        try {data.put("customers",readTable(db,"customers"));data.put("entries",readTable(db,"entries"));db.setTransactionSuccessful();}
        finally {db.endTransaction();}
        String payload=data.toString();JSONObject root=new JSONObject();root.put("format","dokan-hisab-backup");root.put("version",1);root.put("createdAt",System.currentTimeMillis());root.put("payload",payload);root.put("sha256",LicenseManager.sha256(payload));
        return parse(root.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static JSONArray readTable(SQLiteDatabase db,String table)throws Exception {
        JSONArray result=new JSONArray();
        try(Cursor c=db.query(table,null,null,null,null,null,"id ASC")){
            String[] columns=c.getColumnNames();while(c.moveToNext()) {JSONObject row=new JSONObject();for(int i=0;i<columns.length;i++)row.put(columns[i],c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i));result.put(row);}
        }return result;
    }
    public static Snapshot read(InputStream input)throws Exception {
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null)throw bad();byte[] buffer=new byte[8192];int count,total=0;
            while((count=in.read(buffer))!=-1){total+=count;if(total>MAX_BYTES)throw new IllegalArgumentException("ব্যাকআপ ফাইলটি সর্বোচ্চ ১০ MB হতে পারবে।");out.write(buffer,0,count);}
            return parse(out.toByteArray());
        }
    }
    public static Snapshot parse(byte[] bytes)throws Exception {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("ব্যাকআপ ফাইলটি সর্বোচ্চ ১০ MB হতে পারবে।");
        try {
            JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!"dokan-hisab-backup".equals(root.getString("format"))||integer(root,"version")!=1)throw bad();
            String payload=root.getString("payload"),hash=root.getString("sha256");
            if(!LicenseManager.sha256(payload).equals(hash))throw new IllegalArgumentException("ব্যাকআপ ফাইলটি অসম্পূর্ণ বা পরিবর্তিত হয়েছে। অন্য একটি কপি বেছে নিন।");
            JSONObject data=new JSONObject(payload);Snapshot snap=new Snapshot();snap.envelope=root;snap.hash=hash;snap.createdAt=integer(root,"createdAt");snap.customers=data.getJSONArray("customers");snap.entries=data.getJSONArray("entries");
            if(snap.customers.length()>20000||snap.entries.length()>100000||snap.createdAt<0)throw bad();
            Set<String> ids=new HashSet<>(),deleted=new HashSet<>(),entryIds=new HashSet<>();Map<String,Long> balances=new HashMap<>();
            for(int i=0;i<snap.customers.length();i++) {
                JSONObject c=snap.customers.getJSONObject(i);String id=id(c,"id"),name=string(c,"name");
                if(!ids.add(id)||name.trim().isEmpty()||name.length()>100)throw bad();
                String phone=string(c,"phone");if(!Money.phone(phone).equals(phone))throw bad();timestamps(c);
                if(!c.isNull("deleted_at"))deleted.add(id);else snap.customerCount++;
                balances.put(id,0L);
            }
            for(int i=0;i<snap.entries.length();i++) {
                JSONObject e=snap.entries.getJSONObject(i);String eid=id(e,"id"),customerId=id(e,"customer_id"),kind=string(e,"kind");
                if(!entryIds.add(eid)||!ids.contains(customerId)||(!kind.equals("credit")&&!kind.equals("payment")))throw bad();
                long amount=integer(e,"amount");if(amount<=0||amount>999999999L)throw bad();
                LocalDate.parse(string(e,"entry_date"));if(string(e,"note").length()>300)throw bad();timestamps(e);
                if(e.isNull("deleted_at")){if(deleted.contains(customerId))throw bad();balances.put(customerId,balances.get(customerId)+(kind.equals("credit")?amount:-amount));snap.entryCount++;}
            }
            for(long amount:balances.values())if(amount<0)throw bad();return snap;
        }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw bad();}
    }
    private static String string(JSONObject obj,String key)throws Exception {Object v=obj.get(key);if(!(v instanceof String))throw bad();return (String)v;}
    private static String id(JSONObject obj,String key)throws Exception {String v=string(obj,key);if(!v.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw bad();return v;}
    private static long integer(JSONObject obj,String key)throws Exception {Object v=obj.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw bad();return ((Number)v).longValue();}
    private static void timestamps(JSONObject obj)throws Exception {if(integer(obj,"created_at")<0||integer(obj,"updated_at")<0)throw bad();if(!obj.has("deleted_at"))throw bad();if(!obj.isNull("deleted_at")&&integer(obj,"deleted_at")<0)throw bad();}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("এটি একটি সঠিক dokan hisab ব্যাকআপ ফাইল নয়।");}
    static void restoreDatabase(SQLiteDatabase db,Snapshot snap)throws Exception {
        // Revalidate at the transaction boundary, even when the caller holds a Snapshot.
        Snapshot verified=parse(snap.bytes());db.beginTransaction();
        try {db.delete("entries",null,null);db.delete("customers",null,null);insert(db,"customers",verified.customers);insert(db,"entries",verified.entries);db.setTransactionSuccessful();}
        finally {db.endTransaction();}
    }
    private static void insert(SQLiteDatabase db,String table,JSONArray rows)throws Exception {
        String[] columns=table.equals("customers")?new String[]{"id","name","phone","created_at","updated_at","deleted_at"}:new String[]{"id","customer_id","kind","amount","entry_date","note","created_at","updated_at","deleted_at"};
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);ContentValues v=new ContentValues();for(String key:columns){Object value=row.get(key);if(value==JSONObject.NULL)v.putNull(key);else if(value instanceof Number)v.put(key,((Number)value).longValue());else v.put(key,(String)value);}db.insertOrThrow(table,null,v);}
    }
}
