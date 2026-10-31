package com.esssal.accounting;

import android.app.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.Scope;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN=4101, RC_AUTH=4102;
    private static final String DRIVE_SCOPE="https://www.googleapis.com/auth/drive.appdata";
    private GoogleSignInClient signInClient;
    private GoogleSignInAccount account;
    private WebView web;
    private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(this); bar.setPadding(8,8,8,8); bar.setBackgroundColor(Color.rgb(18,59,93));
        Button login=makeButton("Gmail Login"), backup=makeButton("Backup"), restore=makeButton("Restore");
        bar.addView(login,new LinearLayout.LayoutParams(0,52,1)); bar.addView(backup,new LinearLayout.LayoutParams(0,52,1)); bar.addView(restore,new LinearLayout.LayoutParams(0,52,1));
        status=new TextView(this); status.setTextColor(Color.DKGRAY); status.setTextSize(12); status.setPadding(12,5,12,5); status.setText("Offline accounting ready. Sign in to use Drive backup.");
        web=new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true); web.getSettings().setDatabaseEnabled(true);
        web.setWebViewClient(new WebViewClient()); web.addJavascriptInterface(new Object(){
            @JavascriptInterface public void message(String s){runOnUiThread(()->status.setText(s));}
        },"NativeStatus");
        root.addView(bar); root.addView(status); root.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        web.loadUrl("file:///android_asset/index.html");
        GoogleSignInOptions opts=new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
          .requestEmail().requestScopes(new Scope(DRIVE_SCOPE)).build();
        signInClient=GoogleSignIn.getClient(this,opts);
        account=GoogleSignIn.getLastSignedInAccount(this);
        if(account!=null) status.setText("Signed in: "+account.getEmail());
        login.setOnClickListener(v->startActivityForResult(signInClient.getSignInIntent(),RC_SIGN_IN));
        backup.setOnClickListener(v->backupNow());
        restore.setOnClickListener(v->restoreNow());
    }
    private Button makeButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(23,105,170)));return b;}
    @Override protected void onActivityResult(int r,int c,android.content.Intent d){super.onActivityResult(r,c,d);
        if(r==RC_SIGN_IN){try{account=GoogleSignIn.getSignedInAccountFromIntent(d).getResult(com.google.android.gms.common.api.ApiException.class);status.setText("Signed in: "+account.getEmail());}
        catch(Exception e){status.setText("Google sign-in failed: "+e.getMessage());}}
        else if(r==RC_AUTH){status.setText("Google Drive permission returned. Please try Backup or Restore again.");}
    }
    private void backupNow(){
        if(account==null){status.setText("Please sign in with Gmail first.");return;}
        status.setText("Preparing local database backup…");
        web.evaluateJavascript("window.exportAppData ? window.exportAppData() : ''", value->{
            try{
                Object parsed=new JSONTokener(value).nextValue();
                if(!(parsed instanceof String)||((String)parsed).isEmpty()) throw new Exception("App data export is unavailable");
                String json=(String)parsed;
                worker.execute(()->{try{String token=getToken();String id=uploadBackup(token,json);runOnUiThread(()->status.setText("Backup saved to your Google Drive app data: "+id));}
                    catch(UserRecoverableAuthException e){runOnUiThread(()->startActivityForResult(e.getIntent(),RC_AUTH));}
                    catch(Exception e){runOnUiThread(()->status.setText("Backup failed: "+e.getMessage()));}});
            }catch(Exception e){status.setText("Could not read local accounting data: "+e.getMessage());}
        });
    }
    private void restoreNow(){
        if(account==null){status.setText("Please sign in with Gmail first.");return;}
        new AlertDialog.Builder(this).setTitle("Restore accounting backup?")
          .setMessage("Restore will replace the current local accounts and vouchers with the latest Google Drive backup. Continue?")
          .setNegativeButton("Cancel",null).setPositiveButton("Restore", (di,w)->{
            status.setText("Looking for Google Drive backup…");
            worker.execute(()->{try{String token=getToken();String json=downloadBackup(token);runOnUiThread(()->{
                String arg=JSONObject.quote(json);
                web.evaluateJavascript("window.importAppData && window.importAppData("+arg+")",v->{status.setText("Restore completed from Google Drive.");});
            });}catch(UserRecoverableAuthException e){runOnUiThread(()->startActivityForResult(e.getIntent(),RC_AUTH));}
            catch(Exception e){runOnUiThread(()->status.setText("Restore failed: "+e.getMessage()));}});
          }).show();
    }
    private String getToken() throws Exception{
        return GoogleAuthUtil.getToken(getApplicationContext(),account.getAccount(),"oauth2:"+DRIVE_SCOPE);
    }
    private String uploadBackup(String token,String json)throws Exception{
        String boundary="esssal_"+System.currentTimeMillis();
        URL u=new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","multipart/related; boundary="+boundary);
        try(OutputStream o=c.getOutputStream()){
            write(o,"--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"EssSal_Accounting_Backup.json\",\"parents\":[\"appDataFolder\"]}\r\n");
            write(o,"--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"+json+"\r\n--"+boundary+"--");
        }
        String body=readResponse(c);JSONObject obj=new JSONObject(body);return obj.optString("id","uploaded");
    }
    private String downloadBackup(String token)throws Exception{
        URL list=new URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name%3D%27EssSal_Accounting_Backup.json%27&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name)");
        HttpURLConnection c=(HttpURLConnection)list.openConnection();c.setRequestProperty("Authorization","Bearer "+token);
        JSONObject result=new JSONObject(readResponse(c));JSONArray files=result.optJSONArray("files");
        if(files==null||files.length()==0)throw new Exception("No EssSal backup found in this Google account.");
        String id=files.getJSONObject(0).getString("id");
        URL get=new URL("https://www.googleapis.com/drive/v3/files/"+URLEncoder.encode(id,"UTF-8")+"?alt=media");
        HttpURLConnection d=(HttpURLConnection)get.openConnection();d.setRequestProperty("Authorization","Bearer "+token);return readResponse(d);
    }
    private void write(OutputStream o,String s)throws Exception{o.write(s.getBytes(StandardCharsets.UTF_8));}
    private String readResponse(HttpURLConnection c)throws Exception{
        int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);
        String s=b.toString("UTF-8");if(code<200||code>=300)throw new Exception("Google API "+code+": "+s);return s;
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}
