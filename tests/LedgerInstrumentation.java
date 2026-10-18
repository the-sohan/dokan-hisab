package bd.dokanhisab.tests;

import android.app.Instrumentation;
import android.os.Bundle;
import bd.dokanhisab.app.LedgerStore;

public final class LedgerInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean condition,String label) { checks++; if(!condition)throw new AssertionError(label); }
    private void rejected(Runnable action,String label) { boolean failed=false;try{action.run();}catch(IllegalArgumentException ex){failed=true;}check(failed,label); }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle(); LedgerStore db=null;int resultCode=ActivityResult.OK;
        try {
            getTargetContext().deleteDatabase("verification.db");
            // Instrumentation can run before an Activity creates the app database directory.
            getTargetContext().getDatabasePath("verification.db").getParentFile().mkdirs();
            db=new LedgerStore(getTargetContext(),"verification.db");
            final LedgerStore first=db;
            rejected(()->first.saveCustomer(null," ",""),"Name is required");
            String id=db.saveCustomer(null,"রহিম","০১৭১২৩৪৫৬৭৮");
            check(db.customer(id).phone.equals("01712345678"),"Phone normalization");
            String other=db.saveCustomer(null,"করিম",""); check(db.customer(other)!=null,"Optional phone");
            String credit=db.saveEntry(null,id,"credit",100050,"2026-10-17","চাল");
            String payment=db.saveEntry(null,id,"payment",30025,"2026-10-18","");
            check(db.customer(id).balance()==70025,"Exact credit minus payment");
            rejected(()->first.saveEntry(null,id,"payment",70026,"2026-10-18",""),"Overpayment blocked");
            db.saveEntry(payment,id,"payment",40025,"2026-10-18","সংশোধিত");
            check(db.customer(id).balance()==60025,"Payment edit updates balance");
            db.saveEntry(credit,id,"credit",120050,"2026-10-16","চাল ও ডাল");
            check(db.customer(id).balance()==80025,"Credit edit updates balance");
            rejected(()->first.saveEntry(credit,id,"credit",10000,"2026-10-16",""),"Credit edit cannot create negative balance");
            check(db.customer(id).balance()==80025,"Rejected edit is atomic");
            rejected(()->first.deleteEntry(first.entries(id).get(1)),"Credit deletion cannot create negative balance");
            check(db.entries(id).get(0).id.equals(payment),"Date ordering");
            db.saveCustomer(id,"রহিম উদ্দিন",""); check(db.customer(id).name.equals("রহিম উদ্দিন"),"Customer edit");
            db.close(); db=new LedgerStore(getTargetContext(),"verification.db");
            check(db.customer(id).balance()==80025,"Persistence after database reopen");
            check(db.entries(id).get(1).date.equals("2026-10-16"),"Edited date persists");
            db.deleteEntry(db.entries(id).get(0)); check(db.customer(id).balance()==120050,"Payment deletion recalculates balance");
            db.deleteEntry(db.entries(id).get(0)); check(db.customer(id).balance()==0,"Credit deletion recalculates balance");
            db.saveEntry(null,id,"credit",50000,"2026-10-18",""); db.deleteCustomer(id);
            check(db.customer(id)==null && db.entries(id).isEmpty(),"Customer deletion hides transactions");
            check(db.customer(other).balance()==0,"Other customers unaffected");
            result.putString("stream","PASS: "+checks+" SQLite integration checks\n");
        } catch(Throwable error) { result.putString("stream","FAIL: "+error.toString()+"\n"+android.util.Log.getStackTraceString(error));resultCode=ActivityResult.FAILED; }
        finally {if(db!=null)db.close();getTargetContext().deleteDatabase("verification.db");}
        finish(resultCode,result);
    }
    private static final class ActivityResult {static final int OK=-1,FAILED=0;}
}
