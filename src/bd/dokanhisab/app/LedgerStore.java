package bd.dokanhisab.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local repository boundary. Stable UUIDs and tombstones allow a future sync adapter. */
public final class LedgerStore extends SQLiteOpenHelper {
    public static final class Customer {
        public String id, name, phone;
        public long credit, paid;
        public long balance() { return credit - paid; }
    }
    public static final class Entry {
        public String id, customerId, kind, date, note;
        public long amount;
    }
    public LedgerStore(Context context) { this(context, "dokan-hisab.db"); }
    public LedgerStore(Context context, String file) { super(context, file, null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers (id TEXT PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)");
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, customer_id TEXT NOT NULL REFERENCES customers(id), kind TEXT NOT NULL CHECK(kind IN ('credit','payment')), amount INTEGER NOT NULL CHECK(amount > 0), entry_date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER)");
        db.execSQL("CREATE INDEX entry_customer_date ON entries(customer_id, entry_date, created_at)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Database migration required"); }
    public BackupCodec.Snapshot backup() throws Exception {return BackupCodec.exportDatabase(getWritableDatabase());}
    public void restore(BackupCodec.Snapshot snapshot)throws Exception {BackupCodec.restoreDatabase(getWritableDatabase(),snapshot);}
    public String saveCustomer(String id, String name, String phone) {
        name = name.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("কাস্টমারের নাম লিখুন।");
        if (name.length() > 100) throw new IllegalArgumentException("নাম ১০০ অক্ষরের মধ্যে লিখুন।");
        phone = Money.phone(phone);
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues(); v.put("name", name); v.put("phone", phone); v.put("updated_at", now);
        if (id == null) { id = UUID.randomUUID().toString(); v.put("id", id); v.put("created_at", now); getWritableDatabase().insertOrThrow("customers", null, v); }
        else if (getWritableDatabase().update("customers", v, "id=? AND deleted_at IS NULL", new String[]{id}) != 1) throw new IllegalArgumentException("কাস্টমার পাওয়া যায়নি।");
        return id;
    }
    public List<Customer> customers() {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT c.id,c.name,c.phone,COALESCE(SUM(CASE WHEN e.kind='credit' THEN e.amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN e.kind='payment' THEN e.amount ELSE 0 END),0) FROM customers c LEFT JOIN entries e ON c.id=e.customer_id AND e.deleted_at IS NULL WHERE c.deleted_at IS NULL GROUP BY c.id ORDER BY c.name COLLATE NOCASE";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) { Customer c = new Customer(); c.id=cursor.getString(0); c.name=cursor.getString(1); c.phone=cursor.getString(2); c.credit=cursor.getLong(3); c.paid=cursor.getLong(4); list.add(c); }
        }
        return list;
    }
    public Customer customer(String id) { for (Customer c : customers()) if (c.id.equals(id)) return c; return null; }
    public List<Entry> entries(String customerId) {
        List<Entry> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,customer_id,kind,amount,entry_date,note FROM entries WHERE customer_id=? AND deleted_at IS NULL ORDER BY entry_date DESC,created_at DESC,rowid DESC", new String[]{customerId})) {
            while (c.moveToNext()) { Entry e = new Entry(); e.id=c.getString(0); e.customerId=c.getString(1); e.kind=c.getString(2); e.amount=c.getLong(3); e.date=c.getString(4); e.note=c.getString(5); list.add(e); }
        }
        return list;
    }
    public String saveEntry(String id, String customerId, String kind, long amount, String date, String note) {
        if (!kind.equals("credit") && !kind.equals("payment")) throw new IllegalArgumentException("Invalid entry kind");
        if (amount <= 0 || amount > 999999999L) throw new IllegalArgumentException("সঠিক টাকার পরিমাণ লিখুন।");
        try { java.time.LocalDate.parse(date); } catch (Exception ex) { throw new IllegalArgumentException("সঠিক তারিখ বেছে নিন।"); }
        if (note.length() > 300) throw new IllegalArgumentException("বিবরণ ৩০০ অক্ষরের মধ্যে লিখুন।");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            Customer c = customer(customerId);
            if (c == null) throw new IllegalArgumentException("কাস্টমার পাওয়া যায়নি।");
            long balance = c.balance();
            if (id != null) {
                Entry old = null;
                for (Entry e : entries(customerId)) if (e.id.equals(id)) old = e;
                if (old == null) throw new IllegalArgumentException("এন্ট্রি পাওয়া যায়নি।");
                balance -= old.kind.equals("credit") ? old.amount : -old.amount;
            }
            balance += kind.equals("credit") ? amount : -amount;
            if (balance < 0) throw new IllegalArgumentException("পরিশোধ মোট বাকির চেয়ে বেশি হচ্ছে। আগে সংশ্লিষ্ট পরিশোধের এন্ট্রি সংশোধন করুন।");
            long now = System.currentTimeMillis();
            ContentValues v = new ContentValues(); v.put("customer_id", customerId); v.put("kind", kind); v.put("amount", amount); v.put("entry_date", date); v.put("note", note.trim()); v.put("updated_at", now);
            if (id == null) { id=UUID.randomUUID().toString(); v.put("id", id); v.put("created_at", now); db.insertOrThrow("entries", null, v); }
            else db.update("entries", v, "id=? AND deleted_at IS NULL", new String[]{id});
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }
    public void deleteEntry(Entry entry) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            Entry current = null;
            for (Entry e : entries(entry.customerId)) if (e.id.equals(entry.id)) current = e;
            if (current == null) throw new IllegalArgumentException("এন্ট্রি পাওয়া যায়নি।");
            Customer c=customer(current.customerId);
            if (current.kind.equals("credit") && c.balance() < current.amount) throw new IllegalArgumentException("এটি মুছলে পরিশোধ মোট বাকির চেয়ে বেশি হবে। আগে সংশ্লিষ্ট পরিশোধের এন্ট্রি সংশোধন বা মুছুন।");
            ContentValues v=tombstone(); db.update("entries", v, "id=? AND deleted_at IS NULL", new String[]{current.id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private ContentValues tombstone() { ContentValues v=new ContentValues(); long now=System.currentTimeMillis(); v.put("deleted_at", now); v.put("updated_at", now); return v; }
    public void deleteCustomer(String id) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try { ContentValues v=tombstone(); db.update("entries",v,"customer_id=? AND deleted_at IS NULL",new String[]{id}); db.update("customers",v,"id=? AND deleted_at IS NULL",new String[]{id}); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
}
