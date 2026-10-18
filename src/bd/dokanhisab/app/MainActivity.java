package bd.dokanhisab.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private final int ink=Color.rgb(28,49,42), muted=Color.rgb(105,119,110), green=Color.rgb(18,102,79), bg=Color.rgb(246,247,242), line=Color.rgb(225,230,222), red=Color.rgb(166,60,45), orange=Color.rgb(153,91,27);
    private LedgerStore store;
    private LinearLayout root, content, customerList;
    private String selected, query="";
    private boolean onlyDue;
    private List<LedgerStore.Customer> all;
    private AlertDialog backupDialog;
    private final ExecutorService backupExecutor=Executors.newSingleThreadExecutor();
    private static final int EXPORT_BACKUP=801,LOCAL_BACKUP=802,CLOUD_BACKUP=803,IMPORT_BACKUP=804;
    private int dp(float n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new LedgerStore(this);
        if (state!=null) { selected=state.getString("customer"); query=state.getString("query",""); onlyDue=state.getBoolean("onlyDue"); }
        render();
        try {BackupManager.schedule(this);if(BackupManager.due(this))backupExecutor.submit(()->{try{BackupManager.run(this,false);}catch(Exception ignored){}});}catch(Exception ex){toast(ex.getMessage());}
    }
    @Override public void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("customer",selected); state.putString("query",query); state.putBoolean("onlyDue",onlyDue); }
    @Override public void onDestroy() {backupExecutor.shutdown();store.close();super.onDestroy();}
    @Override public void onBackPressed() { if (selected!=null) { selected=null; render(); } else super.onBackPressed(); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        return t;
    }
    private void add(LinearLayout parent, View child, int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); parent.addView(child,p); }
    private void weighted(LinearLayout parent,View child) { parent.addView(child,new LinearLayout.LayoutParams(0,-2,1)); }
    private void gap(LinearLayout l,int size) { View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(dp(size),dp(size))); }
    private Button button(String label,boolean primary,Runnable click) {
        Button b=new Button(this); b.setText(label); b.setTextSize(16); b.setAllCaps(false); b.setTypeface(null,Typeface.BOLD); b.setTextColor(primary?Color.WHITE:green); b.setBackgroundTintList(null); b.setBackground(shape(primary?green:Color.rgb(230,239,229),14)); b.setMinHeight(dp(52)); b.setMinimumHeight(dp(52)); b.setPadding(dp(12),dp(10),dp(12),dp(10)); b.setOnClickListener(v->click.run()); return b;
    }
    private LinearLayout card(int color) { LinearLayout l=column(); l.setPadding(dp(20),dp(18),dp(20),dp(18)); l.setBackground(shape(color,20)); return l; }
    private void render() {
        root=column(); root.setBackgroundColor(bg); root.setFitsSystemWindows(true);
        root.setOnApplyWindowInsetsListener((v,insets)-> { root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets; });
        setContentView(root);
        if (selected!=null && store.customer(selected)==null) selected=null;
        if (selected==null) home(); else detail();
    }
    private void header(String title,String subtitle, boolean back) {
        LinearLayout bar=row(); bar.setPadding(dp(20),dp(16),dp(20),dp(12));
        if (back) { TextView arrow=text("‹",36,green,true); arrow.setGravity(Gravity.CENTER); arrow.setContentDescription("কাস্টমার তালিকায় ফিরুন"); arrow.setOnClickListener(v->{ selected=null; render(); }); bar.addView(arrow,new LinearLayout.LayoutParams(dp(48),dp(52))); gap(bar,8); }
        LinearLayout labels=column(); add(labels,text(title,24,ink,true),0); if (!subtitle.isEmpty()) add(labels,text(subtitle,12,muted,false),2); weighted(bar,labels);
        if (!back) { TextView info=text("ⓘ",25,green,false); info.setContentDescription("অ্যাপ সম্পর্কে"); info.setGravity(Gravity.CENTER); info.setOnClickListener(v->about()); bar.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48))); }
        root.addView(bar);
    }
    private void scrollContent() { ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); content=column(); content.setPadding(dp(20),dp(8),dp(20),dp(24)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); }
    private void footer(View view) { LinearLayout panel=column(); panel.setPadding(dp(20),dp(10),dp(20),dp(14)); add(panel,view,0); root.addView(panel); }
    private void home() {
        all=store.customers(); long balance=0; int due=0; for (LedgerStore.Customer c:all) { balance+=c.balance(); if(c.balance()>0) due++; }
        header("dokan hisab","আপনার দোকানের বাকির খাতা",false); scrollContent();
        LinearLayout summary=card(green); add(summary,text("মোট পাওনা",15,Color.rgb(214,235,219),false),0); TextView total=text(Money.format(balance),34,Color.WHITE,true); total.setContentDescription("মোট পাওনা "+Money.input(balance)+" টাকা"); add(summary,total,8);
        add(summary,text(Money.bn(Integer.toString(due))+" জনের কাছে বাকি আছে",14,Color.rgb(214,235,219),false),10); add(content,summary,0);
        LinearLayout heading=row(); weighted(heading,text("কাস্টমার",21,ink,true)); heading.addView(text(Money.bn(Integer.toString(all.size()))+" জন",14,muted,false)); add(content,heading,24);
        EditText search=field("নাম বা মোবাইল দিয়ে খুঁজুন",InputType.TYPE_CLASS_TEXT,100); search.setText(query); search.setContentDescription("কাস্টমার খুঁজুন"); add(content,search,12);
        LinearLayout filters=row(); Button every=button("সব কাস্টমার",!onlyDue,()->{onlyDue=false; render();}); Button dues=button("শুধু বাকি",onlyDue,()->{onlyDue=true; render();}); weighted(filters,every); gap(filters,10); weighted(filters,dues); add(content,filters,12);
        customerList=column(); add(content,customerList,12); populate();
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int f){} public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString(); populate();} public void afterTextChanged(Editable e){} });
        footer(button("＋  নতুন কাস্টমার",true,()->customerForm(null)));
    }
    private void populate() {
        customerList.removeAllViews(); int count=0;
        String needle=Money.latin(query.trim()).toLowerCase(java.util.Locale.ROOT);
        for (LedgerStore.Customer c:all) {
            if (onlyDue && c.balance()==0) continue;
            if (!Money.latin(c.name).toLowerCase(java.util.Locale.ROOT).contains(needle) && !c.phone.contains(needle)) continue;
            count++;
            LinearLayout tile=card(Color.WHITE); LinearLayout r=row();
            TextView avatar=text(c.name.substring(0,Character.charCount(c.name.codePointAt(0))),21,green,true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(shape(Color.rgb(234,241,231),15)); r.addView(avatar,new LinearLayout.LayoutParams(dp(46),dp(46))); gap(r,12);
            LinearLayout labels=column(); add(labels,text(c.name,18,ink,true),0); add(labels,text(c.phone.isEmpty()?"মোবাইল নম্বর নেই":Money.bn(c.phone),13,muted,false),4); weighted(r,labels); gap(r,8); r.addView(text("›",26,muted,false)); add(tile,r,0);
            LinearLayout amount=row(); weighted(amount,text(c.balance()>0?"বর্তমান বাকি":"হিসাব পরিশোধিত",13,c.balance()>0?muted:green,false)); amount.addView(text(Money.format(c.balance()),21,c.balance()>0?orange:green,true)); add(tile,amount,14);
            tile.setContentDescription(c.name+", বাকি "+Money.input(c.balance())+" টাকা"); tile.setFocusable(true); tile.setOnClickListener(v->{selected=c.id; hideKeyboard(); render();}); add(customerList,tile,8);
        }
        if (count==0) {
            LinearLayout empty=card(Color.WHITE); TextView icon=text("▤",42,green,false); icon.setGravity(Gravity.CENTER); add(empty,icon,8);
            TextView title=text(all.isEmpty()?"আপনার প্রথম কাস্টমার যোগ করুন":"কোনো কাস্টমার পাওয়া যায়নি",19,ink,true); title.setGravity(Gravity.CENTER); add(empty,title,12);
            TextView hint=text(all.isEmpty()?"কার কাছে কত বাকি, সব হিসাব রাখুন এক জায়গায়।":"অন্য নাম বা নম্বর দিয়ে খুঁজে দেখুন।",14,muted,false); hint.setGravity(Gravity.CENTER); add(empty,hint,8); add(customerList,empty,12);
        }
    }
    private void detail() {
        LedgerStore.Customer c=store.customer(selected); header("কাস্টমারের খাতা","বাকি ও পরিশোধের সম্পূর্ণ হিসাব",true); scrollContent();
        LinearLayout identity=row(); LinearLayout names=column(); add(names,text(c.name,25,ink,true),0); add(names,text(c.phone.isEmpty()?"মোবাইল নম্বর নেই":Money.bn(c.phone),15,muted,false),5); weighted(identity,names);
        TextView edit=text("সম্পাদনা",14,green,true); edit.setPadding(dp(12),dp(14),0,dp(14)); edit.setOnClickListener(v->customerForm(c)); identity.addView(edit); add(content,identity,4);
        LinearLayout summary=card(green); add(summary,text("বর্তমান বাকি",15,Color.rgb(214,235,219),false),0); add(summary,text(Money.format(c.balance()),33,Color.WHITE,true),6);
        LinearLayout totals=row(); LinearLayout credits=column(); add(credits,text("মোট বাকি নিয়েছেন",12,Color.rgb(214,235,219),false),0); add(credits,text(Money.format(c.credit),18,Color.WHITE,true),5); weighted(totals,credits); gap(totals,12); LinearLayout paid=column(); add(paid,text("মোট পরিশোধ",12,Color.rgb(214,235,219),false),0); add(paid,text(Money.format(c.paid),18,Color.WHITE,true),5); weighted(totals,paid); add(summary,totals,22); add(content,summary,20);
        List<LedgerStore.Entry> entries=store.entries(selected); LinearLayout title=row(); weighted(title,text("লেনদেনের ইতিহাস",20,ink,true)); title.addView(text(Money.bn(Integer.toString(entries.size()))+"টি",14,muted,false)); add(content,title,24);
        if (entries.isEmpty()) { LinearLayout empty=card(Color.WHITE); add(empty,text("এখনো কোনো লেনদেন নেই",18,ink,true),0); add(empty,text("নিচের বাটন থেকে বাকি বা পরিশোধ যোগ করুন।",14,muted,false),8); add(content,empty,14); }
        for (LedgerStore.Entry e:entries) {
            boolean credit=e.kind.equals("credit"); LinearLayout tile=card(Color.WHITE); LinearLayout top=row(); weighted(top,text(credit?"বাকি নিয়েছেন":"পরিশোধ করেছেন",16,credit?orange:green,true)); top.addView(text(Money.format(e.amount),21,credit?orange:green,true)); add(tile,top,0);
            add(tile,text(displayDate(e.date),13,muted,false),8); if(!e.note.isEmpty()) add(tile,text(e.note,15,ink,false),8);
            TextView action=text("সংশোধন / মুছুন  ›",12,muted,false); add(tile,action,10); tile.setFocusable(true); tile.setOnClickListener(v->entryOptions(e)); add(content,tile,12);
        }
        LinearLayout actions=row(); weighted(actions,button("＋ বাকি",true,()->entryForm(null,"credit"))); gap(actions,10); weighted(actions,button("✓ পরিশোধ",false,()->entryForm(null,"payment"))); footer(actions);
    }
    private String displayDate(String date) { return Money.bn(LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd / MM / yyyy"))); }
    private EditText field(String hint,int type,int max) { EditText e=new EditText(this); e.setTextSize(17); e.setTextColor(ink); e.setHintTextColor(muted); e.setHint(hint); e.setInputType(type); e.setSingleLine(true); e.setPadding(dp(14),dp(12),dp(14),dp(12)); GradientDrawable d=shape(Color.WHITE,12); d.setStroke(dp(1),line); e.setBackground(d); e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); return e; }
    private LinearLayout form() { LinearLayout l=column(); l.setPadding(dp(22),dp(8),dp(22),dp(12)); return l; }
    private ScrollView formScroll(LinearLayout form) { ScrollView s=new ScrollView(this); s.addView(form); return s; }
    private void label(LinearLayout form,String value) { add(form,text(value,14,ink,true),14); }
    private void customerForm(LedgerStore.Customer c) {
        LinearLayout f=form(); label(f,"কাস্টমারের নাম *"); EditText name=field("নাম লিখুন",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,100); if(c!=null) name.setText(c.name); add(f,name,6);
        label(f,"মোবাইল নম্বর (ঐচ্ছিক)"); EditText phone=field("01XXXXXXXXX",InputType.TYPE_CLASS_PHONE,20); phone.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789০১২৩৪৫৬৭৮৯+ -()")); if(c!=null) phone.setText(c.phone); add(f,phone,6);
        TextView error=text("",13,red,false); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(f,error,10);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(c==null?"নতুন কাস্টমার":"কাস্টমার সম্পাদনা").setView(formScroll(f)).setNegativeButton("বাতিল",null).setPositiveButton("সেভ করুন",null).create();
        if(c!=null) { Button remove=button("কাস্টমার মুছুন",false,()->confirmCustomerDelete(c,dialog)); remove.setTextColor(red); add(f,remove,14); }
        dialog.setOnShowListener(v->{ dialog.getButton(-1).setOnClickListener(w->{ try { String id=store.saveCustomer(c==null?null:c.id,name.getText().toString(),phone.getText().toString()); selected=id; dialog.dismiss(); hideKeyboard(); render(); toast("কাস্টমার সেভ হয়েছে"); } catch(IllegalArgumentException ex){ error.setText(ex.getMessage()); } catch(Exception ex){error.setText("সেভ করা যায়নি। আবার চেষ্টা করুন।");} }); });
        dialog.show(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void confirmCustomerDelete(LedgerStore.Customer c,AlertDialog parent) {
        new AlertDialog.Builder(this).setTitle("কাস্টমার মুছবেন?").setMessage(c.name+" এবং তাঁর সব লেনদেন খাতা থেকে মুছে যাবে। এই কাজ ফিরিয়ে নেওয়া যাবে না।").setNegativeButton("বাতিল",null).setPositiveButton("মুছে ফেলুন",(d,w)->{ try {store.deleteCustomer(c.id); parent.dismiss(); selected=null; render(); toast("কাস্টমার মুছে ফেলা হয়েছে");}catch(Exception ex){toast("মুছে ফেলা যায়নি।");} }).show();
    }
    private void entryOptions(LedgerStore.Entry e) {
        new AlertDialog.Builder(this).setTitle(e.kind.equals("credit")?"বাকির এন্ট্রি":"পরিশোধের এন্ট্রি").setItems(new String[]{"সংশোধন করুন","মুছে ফেলুন"},(d,which)->{ if(which==0)entryForm(e,e.kind);else confirmEntryDelete(e); }).setNegativeButton("বন্ধ করুন",null).show();
    }
    private void confirmEntryDelete(LedgerStore.Entry e) {
        new AlertDialog.Builder(this).setTitle("এন্ট্রিটি মুছবেন?").setMessage(displayDate(e.date)+" তারিখের "+Money.format(e.amount)+"-এর এন্ট্রি মুছে যাবে। মোট বাকি নতুন করে হিসাব হবে।").setNegativeButton("বাতিল",null).setPositiveButton("মুছে ফেলুন",(d,w)->{ try{store.deleteEntry(e);render();toast("এন্ট্রি মুছে ফেলা হয়েছে");}catch(IllegalArgumentException ex){message(ex.getMessage());}catch(Exception ex){message("মুছে ফেলা যায়নি। আবার চেষ্টা করুন।");} }).show();
    }
    private void entryForm(LedgerStore.Entry e,String kind) {
        boolean credit=kind.equals("credit"); LinearLayout f=form(); LedgerStore.Customer c=store.customer(selected); add(f,text(c.name+" · বাকি "+Money.format(c.balance()),15,muted,false),8);
        label(f,"টাকার পরিমাণ (৳) *"); EditText amount=field("যেমন: ৫০০",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,11); amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789০১২৩৪৫৬৭৮৯.")); amount.setRawInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); if(e!=null)amount.setText(Money.input(e.amount)); add(f,amount,6);
        final LocalDate[] date={e==null?LocalDate.now():LocalDate.parse(e.date)}; label(f,"লেনদেনের তারিখ *"); Button dateButton=button(displayDate(date[0].toString())+"  ▾",false,()->{}); dateButton.setContentDescription("লেনদেনের তারিখ পরিবর্তন করুন");
        dateButton.setOnClickListener(v->new DatePickerDialog(this,(picker,y,m,d)->{date[0]=LocalDate.of(y,m+1,d);dateButton.setText(displayDate(date[0].toString())+"  ▾");},date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth()).show()); add(f,dateButton,6);
        label(f,"বিবরণ (ঐচ্ছিক)"); EditText note=field("যেমন: চাল, ডাল",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,300); note.setSingleLine(false); note.setMaxLines(3); if(e!=null)note.setText(e.note); add(f,note,6);
        TextView error=text("",13,red,false); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(f,error,10);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(e!=null?"এন্ট্রি সংশোধন":credit?"বাকি যোগ করুন":"পরিশোধ যোগ করুন").setView(formScroll(f)).setNegativeButton("বাতিল",null).setPositiveButton("সেভ করুন",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try {store.saveEntry(e==null?null:e.id,selected,kind,Money.parse(amount.getText().toString()),date[0].toString(),note.getText().toString()); dialog.dismiss(); hideKeyboard(); render(); toast("লেনদেন সেভ হয়েছে");}catch(IllegalArgumentException ex){error.setText(ex.getMessage());}catch(Exception ex){error.setText("সেভ করা যায়নি। আবার চেষ্টা করুন।");}})); dialog.show(); dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void about() {
        boolean pro=LicenseManager.isPro(this);
        String body="dokan hisab · ০.২.০\n\nইন্টারনেট ছাড়াই আপনার ফোনে হিসাব সেভ হয়।\n\nঅ্যাপ আনইনস্টল করলে বা অ্যাপের ডেটা মুছলে ফোনে থাকা হিসাব হারিয়ে যাবে। অ্যাপের বাইরে ব্যাকআপের কপি থাকলে পরে ফিরিয়ে আনা যাবে।\n\n"+(pro?"আপনার Pro লাইসেন্স সক্রিয়।":"ব্যাকআপ সুবিধা নিতে আপনার অ্যাপে প্রিমিয়াম লাইসেন্স সক্রিয় করুন।")+"\nযোগাযোগ: 01930162223";
        new AlertDialog.Builder(this).setTitle("dokan hisab").setMessage(body).setNegativeButton("বন্ধ করুন",null).setNeutralButton(pro?"লাইসেন্সের তথ্য":"Activate license",(d,w)->{if(pro)message("Pro লাইসেন্স সক্রিয়\nলাইসেন্স: ••••• "+LicenseManager.suffix(this)+"\n\nফোন রিসেট বা অ্যাপ আনইনস্টল করলে একই লাইসেন্স কী আবার লিখতে হবে।");else licenseForm();}).setPositiveButton("ব্যাকআপ",(d,w)->backupSettings()).show();
    }
    private void licenseForm() {
        LinearLayout f=form();add(f,text("ব্যাকআপ, অটো ব্যাকআপ ও হিসাব ফিরিয়ে আনার সুবিধা চালু করুন।",15,muted,false),8);label(f,"লাইসেন্স কী");EditText key=field("DH-XXXXX-XXXXX-XXXXX-XXXXX",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,40);add(f,key,8);
        add(f,text("যোগাযোগ: 01930162223",14,muted,false),12);TextView error=text("",13,red,false);add(f,error,8);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Activate license").setView(formScroll(f)).setNegativeButton("বাতিল",null).setPositiveButton("সক্রিয় করুন",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{LicenseManager.activate(this,key.getText().toString());BackupManager.schedule(this);d.dismiss();hideKeyboard();render();backupSettings();}catch(Exception ex){error.setText(ex.getMessage());}}));d.show();
    }
    private void backupSettings() {
        if(!LicenseManager.isPro(this)){new AlertDialog.Builder(this).setTitle("ব্যাকআপ · Pro").setMessage("ব্যাকআপ সুবিধা নিতে আপনার অ্যাপে প্রিমিয়াম লাইসেন্স সক্রিয় করুন।\n\nযোগাযোগ: 01930162223").setNegativeButton("বন্ধ করুন",null).setPositiveButton("Activate license",(d,w)->licenseForm()).show();return;}
        if(backupDialog!=null&&backupDialog.isShowing())backupDialog.dismiss();
        android.content.SharedPreferences p=BackupManager.prefs(this);LinearLayout f=form();
        add(f,text("আপনার Pro সুবিধা",20,green,true),8);add(f,text("ফোনের ফাইল বা Google Drive-এ ব্যাকআপ রাখুন। একই ফাইল পরের ব্যাকআপে আপডেট হবে।",14,muted,false),8);
        android.widget.Switch automatic=new android.widget.Switch(this);automatic.setText("স্বয়ংক্রিয় ব্যাকআপ");automatic.setTextSize(16);automatic.setTextColor(ink);automatic.setChecked(p.getBoolean("enabled",true));add(f,automatic,20);
        automatic.setOnCheckedChangeListener((b,checked)->{p.edit().putBoolean("enabled",checked).apply();try{BackupManager.schedule(this);}catch(Exception ex){message(ex.getMessage());}});
        long period=p.getLong("interval",BackupManager.HOUR);
        Button interval=button(period==BackupManager.HOUR?"সময়: প্রায় প্রতি ১ ঘণ্টা":"সময়: প্রায় প্রতিদিন",false,()->{});add(f,interval,12);
        interval.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("ব্যাকআপের সময়").setSingleChoiceItems(new String[]{"প্রায় প্রতি ১ ঘণ্টা (প্রস্তাবিত)","প্রায় প্রতিদিন"},p.getLong("interval",BackupManager.HOUR)==BackupManager.HOUR?0:1,(d,which)->{long chosen=which==0?BackupManager.HOUR:BackupManager.DAY;p.edit().putLong("interval",chosen).apply();BackupManager.schedule(this);interval.setText(which==0?"সময়: প্রায় প্রতি ১ ঘণ্টা":"সময়: প্রায় প্রতিদিন");d.dismiss();}).setNegativeButton("বাতিল",null).show());
        add(f,text("শুধু হিসাব বদলালে ফাইল আপডেট হবে। Android-এর ব্যাটারি ব্যবস্থাপনায় সময় পিছিয়ে যেতে পারে।",12,muted,false),10);
        label(f,"অ্যাপের ভেতরের কপি");add(f,text("সর্বশেষ: "+backupTime(p.getLong("internal_at",0)),13,muted,false),4);add(f,text("অ্যাপ মুছলে বা ফোন রিসেট করলে এই কপিও হারাবে। নিচে আলাদা ফাইল বা Drive বেছে নিন।",12,muted,false),6);
        add(f,button("এখন ব্যাকআপ নিন",true,()->backupTask("ব্যাকআপ হচ্ছে…",()->{String errors=BackupManager.run(this,true);if(!errors.isEmpty())return "অ্যাপের ভেতরে একটি কপি রাখা হয়েছে।\n\n"+errors;if(p.getString("local_uri","").isEmpty()&&p.getString("cloud_uri","").isEmpty())return "অ্যাপের ভেতরে কপি রাখা হয়েছে। ফোন রিসেটের পর ফেরত পেতে আলাদা ব্যাকআপ ফাইল বা Drive বেছে নিন।";return "বেছে নেওয়া ব্যাকআপ ফাইলে লেখা হয়েছে। Drive ব্যবহার করলে অনলাইন সিঙ্ক সম্পন্ন হয়েছে কি না Drive app-এ দেখে নিন।";})),12);
        add(f,button("ব্যাকআপ ফাইল সেভ করুন",false,()->pickBackupFile(EXPORT_BACKUP)),10);
        label(f,"ফোনের অটো ব্যাকআপ ফাইল");add(f,text(targetStatus("local"),13,muted,false),4);add(f,button("ফোনে ব্যাকআপ ফাইল বেছে নিন",false,()->pickBackupFile(LOCAL_BACKUP)),10);
        label(f,"Google Drive / ক্লাউড");add(f,text(targetStatus("cloud"),13,muted,false),4);
        add(f,button("Drive-এ ব্যাকআপ ফাইল বেছে নিন",false,()->new AlertDialog.Builder(this).setTitle("Google Drive-এ ব্যাকআপ").setMessage("পরের স্ক্রিনের মেনু থেকে Google Drive ও আপনার অ্যাকাউন্ট বেছে ফাইল সেভ করুন।\n\nDrive না দেখালে ফোনে Google Drive app-এ সাইন ইন করুন।\n\nইন্টারনেট থাকলে Drive ফাইলটি অনলাইনে সিঙ্ক করবে। ফাইলে লেখা হয়েছে মানেই অনলাইন সিঙ্ক শেষ হয়েছে—এমন নিশ্চয়তা নেই। ফোন রিসেটের আগে Drive-এ ফাইলটি পৌঁছেছে কি না দেখে নিন।").setNegativeButton("বাতিল",null).setPositiveButton("ফাইল বেছে নিন",(d,w)->pickBackupFile(CLOUD_BACKUP)).show()),10);
        if(!p.getString("cloud_uri","").isEmpty())add(f,button("ক্লাউড ব্যাকআপ বন্ধ করুন",false,()->{BackupManager.disconnect(this,"cloud");backupSettings();toast("এই ফাইলে অটো আপডেট বন্ধ হয়েছে");}),8);
        label(f,"ব্যাকআপ থেকে হিসাব ফিরিয়ে আনুন");add(f,button("ব্যাকআপ ফাইল খুলুন",false,()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,IMPORT_BACKUP);}),10);
        add(f,text("ফোন রিসেট হলে Google Drive-এর অনলাইন কপি থেকে ফাইল বেছে ফিরিয়ে আনতে পারবেন। লাইসেন্স কী আলাদা করে রাখুন; সেটি ব্যাকআপে থাকে না।",12,muted,false),14);
        backupDialog=new AlertDialog.Builder(this).setTitle("ব্যাকআপ · Pro").setView(formScroll(f)).setPositiveButton("বন্ধ করুন",null).show();
    }
    private String backupTime(long time){if(time==0)return "এখনো হয়নি";return Money.bn(java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")));}
    private String targetStatus(String kind){android.content.SharedPreferences p=BackupManager.prefs(this);if(p.getString(kind+"_uri","").isEmpty())return "কোনো ফাইল বেছে নেওয়া হয়নি";String error=p.getString(kind+"_error","");return (error.isEmpty()?"ফাইলে সর্বশেষ লেখা: "+backupTime(p.getLong(kind+"_at",0)):error);}
    private void pickBackupFile(int request){if(!LicenseManager.isPro(this)){licenseForm();return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"dokan-hisab-backup.json");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);}
    private interface BackupWork {String run()throws Exception;}
    private void backupTask(String title,BackupWork work){ProgressDialog progress=new ProgressDialog(this);progress.setMessage(title);progress.setCancelable(false);progress.show();backupExecutor.submit(()->{String result;try{result=work.run();}catch(Exception ex){result=ex instanceof IllegalArgumentException||ex instanceof IllegalStateException?ex.getMessage():"কাজটি সম্পন্ন হয়নি। ফাইলের অনুমতি ও খালি জায়গা পরীক্ষা করে আবার চেষ্টা করুন।";}final String message=result;runOnUiThread(()->{if(isDestroyed())return;progress.dismiss();if(backupDialog!=null&&backupDialog.isShowing())backupSettings();message(message);});});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;if(request<EXPORT_BACKUP||request>IMPORT_BACKUP)return;if(!LicenseManager.isPro(this)){licenseForm();return;}Uri uri=data.getData();
        if(request==IMPORT_BACKUP){backupExecutor.submit(()->{try{BackupCodec.Snapshot snapshot=BackupCodec.read(getContentResolver().openInputStream(uri));runOnUiThread(()->{if(isDestroyed())return;new AlertDialog.Builder(this).setTitle("ব্যাকআপ ফিরিয়ে আনবেন?").setMessage("ব্যাকআপের তারিখ: "+backupTime(snapshot.createdAt)+"\nকাস্টমার: "+Money.bn(Integer.toString(snapshot.customerCount))+" জন\nলেনদেন: "+Money.bn(Integer.toString(snapshot.entryCount))+"টি\n\nবর্তমান সব হিসাব এই ব্যাকআপের হিসাব দিয়ে বদলে যাবে।").setNegativeButton("বাতিল",null).setPositiveButton("ফিরিয়ে আনুন",(d,w)->backupTask("হিসাব ফিরিয়ে আনা হচ্ছে…",()->{BackupManager.restore(this,snapshot);runOnUiThread(()->{selected=null;query="";render();});return "ব্যাকআপ থেকে হিসাব ফিরিয়ে আনা হয়েছে।";})).show();});}catch(Exception ex){runOnUiThread(()->{if(!isDestroyed())message(ex instanceof IllegalArgumentException?ex.getMessage():"ব্যাকআপ ফাইলটি পড়া যায়নি। অন্য একটি কপি বেছে নিন।");});}});return;}
        if(request==EXPORT_BACKUP){backupTask("ব্যাকআপ ফাইল সেভ হচ্ছে…",()->{try(LedgerStore fresh=new LedgerStore(this)){BackupManager.write(this,uri,fresh.backup().bytes());}return "ব্যাকআপ ফাইল সেভ হয়েছে।";});return;}
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{getContentResolver().takePersistableUriPermission(uri,flags);BackupManager.target(this,request==LOCAL_BACKUP?"local":"cloud",uri);BackupManager.schedule(this);backupTask("ব্যাকআপ ফাইলে লেখা হচ্ছে…",()->{String errors=BackupManager.run(this,true);return errors.isEmpty()?"ব্যাকআপ ফাইল যুক্ত হয়েছে। এই ফাইলই পরের বার আপডেট হবে।":errors;});}catch(Exception ex){message("ফাইলটিতে স্থায়ীভাবে লেখার অনুমতি পাওয়া যায়নি। অন্য জায়গায় ফাইল সেভ করুন।");}
    }
    private void message(String body) { new AlertDialog.Builder(this).setTitle("dokan hisab").setMessage(body).setPositiveButton("ঠিক আছে",null).show(); }
    private void toast(String value) { Toast.makeText(this,value,Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard() { android.view.inputmethod.InputMethodManager manager=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE); View focus=getCurrentFocus(); if(focus!=null)manager.hideSoftInputFromWindow(focus.getWindowToken(),0); }
}
