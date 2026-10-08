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
import android.webkit.JavascriptInterface;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_PERMS=1001, FILE_CHOOSER=1002;
    private WebView webView; private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geoCallback; private String geoOrigin;
    private PermissionRequest pendingWebPermission; private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private String pendingSpeechLocale="en-US";
    private boolean nativeListening=false;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root=new FrameLayout(this); webView=new WebView(this);
        root.addView(webView,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);

        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true); s.setAllowContentAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false); s.setSupportZoom(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true); s.setLoadsImagesAutomatically(true);
        s.setGeolocationEnabled(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        webView.addJavascriptInterface(new JarvisBridge(),"AndroidJARVIS");
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();
                if("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme())) return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(final PermissionRequest r){runOnUiThread(()->{
                boolean cam=false,aud=false;
                for(String x:r.getResources()){
                    if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x)&&!has(Manifest.permission.CAMERA))cam=true;
                    if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)&&!has(Manifest.permission.RECORD_AUDIO))aud=true;
                }
                if(!cam&&!aud){grantWebPermission(r);return;}
                pendingWebPermission=r;
                java.util.ArrayList<String> list=new java.util.ArrayList<>();
                if(cam)list.add(Manifest.permission.CAMERA);
                if(aud)list.add(Manifest.permission.RECORD_AUDIO);
                requestPermissions(list.toArray(new String[0]),REQ_PERMS);
            });}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){
                geoOrigin=origin;geoCallback=cb;
                if(has(Manifest.permission.ACCESS_FINE_LOCATION)||has(Manifest.permission.ACCESS_COARSE_LOCATION))cb.invoke(origin,true,false);
                else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_PERMS);
            }
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null); fileCallback=cb;
                Intent i=params.createIntent(); i.addCategory(Intent.CATEGORY_OPENABLE);
                try{startActivityForResult(i,FILE_CHOOSER);}catch(Exception e){fileCallback=null;return false;}
                return true;
            }
            @Override public void onShowCustomView(View v,CustomViewCallback cb){
                if(customView!=null){cb.onCustomViewHidden();return;}
                customView=v;customViewCallback=cb;setContentView(v);
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            @Override public void onHideCustomView(){hideCustomView();}
        });

        initNativeVoice();
        loadBundledHtml();
    }

    private void initNativeVoice(){
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){
                tts.setLanguage(Locale.getDefault());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){}
                    @Override public void onDone(String id){runOnUiThread(()->js("window.__nativeSpeakDone&&window.__nativeSpeakDone();"));}
                    @Override public void onError(String id){runOnUiThread(()->js("window.__nativeSpeakDone&&window.__nativeSpeakDone();"));}
                });
            }
        });
        if(SpeechRecognizer.isRecognitionAvailable(this)){
            speechRecognizer=SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle p){}
                @Override public void onBeginningOfSpeech(){}
                @Override public void onRmsChanged(float rms){}
                @Override public void onBufferReceived(byte[] b){}
                @Override public void onEndOfSpeech(){runOnUiThread(()->js("window.__nativeSpeechEnded&&window.__nativeSpeechEnded();"));}
                @Override public void onResults(Bundle b){
                    ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(r!=null&&!r.isEmpty())sendSpeech(r.get(0),true);
                }
                @Override public void onPartialResults(Bundle b){
                    ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(r!=null&&!r.isEmpty())sendSpeech(r.get(0),false);
                }
                @Override public void onEvent(int t,Bundle p){}
                @Override public void onError(int e){
                    runOnUiThread(()->{
                        js("window.__nativeSpeechError&&window.__nativeSpeechError("+quote(errorName(e))+");");
                        js("window.__nativeSpeechEnded&&window.__nativeSpeechEnded();");
                    });
                }
            });
        }
    }

    private void sendSpeech(String text,boolean finalResult){js("window.__nativeSpeechResult&&window.__nativeSpeechResult("+quote(text)+","+finalResult+");");}
    private String errorName(int e){
        switch(e){
            case SpeechRecognizer.ERROR_AUDIO:return "audio";
            case SpeechRecognizer.ERROR_CLIENT:return "client";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:return "permission";
            case SpeechRecognizer.ERROR_NETWORK:return "network";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:return "network_timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:return "no_match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:return "busy";
            case SpeechRecognizer.ERROR_SERVER:return "server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:return "timeout";
            default:return "error";
        }
    }
    private String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    private void js(String code){if(webView!=null)webView.post(()->webView.evaluateJavascript("javascript:"+code,null));}

    private class JarvisBridge{
        @JavascriptInterface public void speak(String text,float rate,float pitch,float volume){
            runOnUiThread(()->{
                if(tts==null)return;
                tts.setSpeechRate(Math.max(.1f,Math.min(3f,rate)));
                tts.setPitch(Math.max(.1f,Math.min(3f,pitch)));
                Bundle p=new Bundle();
                p.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,Math.max(0f,Math.min(1f,volume)));
                tts.speak(text,TextToSpeech.QUEUE_FLUSH,p,"jarvis-"+System.nanoTime());
            });
        }
        @JavascriptInterface public void stopSpeaking(){runOnUiThread(()->{if(tts!=null)tts.stop();js("window.__nativeSpeakDone&&window.__nativeSpeakDone();");});}
        @JavascriptInterface public void startListening(String locale){
            runOnUiThread(()->{
                pendingSpeechLocale=(locale==null||locale.isEmpty())?"en-US":locale;
                nativeListening=true;
                if(!has(Manifest.permission.RECORD_AUDIO)){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_PERMS);return;}
                startNativeRecognition();
            });
        }
        @JavascriptInterface public void stopListening(){runOnUiThread(()->{nativeListening=false;try{if(speechRecognizer!=null)speechRecognizer.stopListening();}catch(Exception ignored){}});}
        @JavascriptInterface public boolean isAvailable(){return speechRecognizer!=null;}
    }

    private void startNativeRecognition(){
        if(speechRecognizer==null){js("window.__nativeSpeechError&&window.__nativeSpeechError('unavailable');");return;}
        try{
            speechRecognizer.stopListening();
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,pendingSpeechLocale);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            speechRecognizer.startListening(i);
        }catch(Exception e){js("window.__nativeSpeechError&&window.__nativeSpeechError("+quote(e.getMessage()==null?"start_failed":e.getMessage())+");");}
    }

    private void loadBundledHtml(){
        try(InputStream in=getAssets().open("J.A.R.V.I.S.html")){
            byte[] data=new byte[in.available()];
            int offset=0,n;
            while(offset<data.length&&(n=in.read(data,offset,data.length-offset))>0)offset+=n;
            String html=new String(data,0,offset,StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/assets/",
                html,
                "text/html",
                "UTF-8",
                null
            );
        }catch(Exception e){
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/assets/",
                "<html><body style='background:#000;color:#fff;font-family:sans-serif;padding:24px'><h2>JARVIS failed to load</h2><p>"+escapeHtml(e.toString())+"</p></body></html>",
                "text/html",
                "UTF-8",
                null
            );
        }
    }

    private String escapeHtml(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private boolean has(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}

    private void grantWebPermission(PermissionRequest r){
        java.util.ArrayList<String> a=new java.util.ArrayList<>();
        for(String x:r.getResources()){
            if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x)&&has(Manifest.permission.CAMERA))a.add(x);
            if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x)&&has(Manifest.permission.RECORD_AUDIO))a.add(x);
        }
        if(!a.isEmpty())r.grant(a.toArray(new String[0]));else r.deny();
        pendingWebPermission=null;
    }

    @Override public void onRequestPermissionsResult(int c,@NonNull String[] p,@NonNull int[] g){
        super.onRequestPermissionsResult(c,p,g);
        if(c==REQ_PERMS){
            if(pendingWebPermission!=null)grantWebPermission(pendingWebPermission);
            if(has(Manifest.permission.RECORD_AUDIO)&&nativeListening)startNativeRecognition();
            if(geoCallback!=null&&(has(Manifest.permission.ACCESS_FINE_LOCATION)||has(Manifest.permission.ACCESS_COARSE_LOCATION))){
                geoCallback.invoke(geoOrigin,true,false);geoCallback=null;geoOrigin=null;
            }
        }
    }

    @Override protected void onActivityResult(int c,int r,Intent d){
        super.onActivityResult(c,r,d);
        if(c==FILE_CHOOSER&&fileCallback!=null){
            Uri[] out=null;
            if(r==RESULT_OK&&d!=null){
                if(d.getClipData()!=null){
                    int n=d.getClipData().getItemCount();out=new Uri[n];
                    for(int i=0;i<n;i++)out[i]=d.getClipData().getItemAt(i).getUri();
                }else if(d.getData()!=null)out=new Uri[]{d.getData()};
            }
            fileCallback.onReceiveValue(out);fileCallback=null;
        }
    }

    private void hideCustomView(){
        if(customView==null)return;
        customView=null;
        if(customViewCallback!=null)customViewCallback.onCustomViewHidden();
        customViewCallback=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        FrameLayout root=new FrameLayout(this);
        root.addView(webView,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
    }

    @Override public void onBackPressed(){
        if(customView!=null){hideCustomView();return;}
        if(webView.canGoBack())webView.goBack();else super.onBackPressed();
    }

    @Override protected void onDestroy(){
        nativeListening=false;
        if(speechRecognizer!=null)speechRecognizer.destroy();
        if(tts!=null)tts.shutdown();
        if(webView!=null)webView.destroy();
        super.onDestroy();
    }
}
