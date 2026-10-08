package com.akarsh.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final int REQ_PERMS=1001, FILE_CHOOSER=1002;
    private WebView webView; private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback; private String geoOrigin;
    private PermissionRequest pendingWebPermission; private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root=new FrameLayout(this); webView=new WebView(this);
        root.addView(webView,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        WebViewAssetLoader assetLoader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        WebSettings s=webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setMediaPlaybackRequiresUserGesture(false); s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false); s.setSupportZoom(false); s.setJavaScriptCanOpenWindowsAutomatically(true); s.setLoadsImagesAutomatically(true);
        s.setGeolocationEnabled(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.setWebViewClient(new WebViewClient(){
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return assetLoader.shouldInterceptRequest(r.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){Uri u=r.getUrl(); if("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme())) return false; try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){} return true;}
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(final PermissionRequest r){runOnUiThread(()->{
                boolean cam=false,aud=false; for(String x:r.getResources()){if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x)&&!has(Manifest.permission.CAMERA))cam=true;if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)&&!has(Manifest.permission.RECORD_AUDIO))aud=true;}
                if(!cam&&!aud){grantWebPermission(r);return;} pendingWebPermission=r; java.util.ArrayList<String> list=new java.util.ArrayList<>(); if(cam)list.add(Manifest.permission.CAMERA);if(aud)list.add(Manifest.permission.RECORD_AUDIO);requestPermissions(list.toArray(new String[0]),REQ_PERMS);
            });}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){geoOrigin=origin;geoCallback=cb;if(has(Manifest.permission.ACCESS_FINE_LOCATION)||has(Manifest.permission.ACCESS_COARSE_LOCATION))cb.invoke(origin,true,false);else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_PERMS);}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;Intent i=params.createIntent();i.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,FILE_CHOOSER);}catch(Exception e){fileCallback=null;return false;}return true;}
            @Override public void onShowCustomView(View v,CustomViewCallback cb){if(customView!=null){cb.onCustomViewHidden();return;}customView=v;customViewCallback=cb;setContentView(v);getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);}
            @Override public void onHideCustomView(){hideCustomView();}
        });
        webView.loadUrl("https://appassets.androidplatform.net/assets/J.A.R.V.I.S.html");
    }
    private boolean has(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private void grantWebPermission(PermissionRequest r){java.util.ArrayList<String> a=new java.util.ArrayList<>();for(String x:r.getResources()){if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x)&&has(Manifest.permission.CAMERA))a.add(x);if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)&&has(Manifest.permission.RECORD_AUDIO))a.add(x);}if(!a.isEmpty())r.grant(a.toArray(new String[0]));else r.deny();pendingWebPermission=null;}
    @Override public void onRequestPermissionsResult(int c,@NonNull String[] p,@NonNull int[] g){super.onRequestPermissionsResult(c,p,g);if(c==REQ_PERMS){if(pendingWebPermission!=null)grantWebPermission(pendingWebPermission);if(geoCallback!=null&&(has(Manifest.permission.ACCESS_FINE_LOCATION)||has(Manifest.permission.ACCESS_COARSE_LOCATION))){geoCallback.invoke(geoOrigin,true,false);geoCallback=null;geoOrigin=null;}}}
    @Override protected void onActivityResult(int c,int r,Intent d){super.onActivityResult(c,r,d);if(c==FILE_CHOOSER&&fileCallback!=null){Uri[] out=null;if(r==RESULT_OK&&d!=null){if(d.getClipData()!=null){int n=d.getClipData().getItemCount();out=new Uri[n];for(int i=0;i<n;i++)out[i]=d.getClipData().getItemAt(i).getUri();}else if(d.getData()!=null)out=new Uri[]{d.getData()};}fileCallback.onReceiveValue(out);fileCallback=null;}}
    private void hideCustomView(){if(customView==null)return;customView=null;if(customViewCallback!=null)customViewCallback.onCustomViewHidden();customViewCallback=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);FrameLayout root=new FrameLayout(this);root.addView(webView,new FrameLayout.LayoutParams(-1,-1));setContentView(root);}
    @Override public void onBackPressed(){if(customView!=null){hideCustomView();return;}if(webView.canGoBack())webView.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(webView!=null)webView.destroy();super.onDestroy();}
}
