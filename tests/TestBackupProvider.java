package bd.dokanhisab.tests;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Test APK only. Simulates writable and unavailable document providers. */
public final class TestBackupProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        if(!"/backup.json".equals(uri.getPath()))throw new FileNotFoundException("Simulated unavailable provider");
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),"provider-backup.json"),ParcelFileDescriptor.parseMode(mode));
    }
    @Override public String getType(Uri u){return "application/json";}
    @Override public Cursor query(Uri u,String[]p,String s,String[]a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){return null;}
    @Override public int update(Uri u,ContentValues v,String s,String[]a){return 0;}
    @Override public int delete(Uri u,String s,String[]a){return 0;}
}
