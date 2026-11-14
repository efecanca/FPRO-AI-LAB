package tr.fpro.ailab;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  final int bg=0xff0b1422, fg=0xffeaf2ff;
  LinearLayout root, content;
  SharedPreferences pref;
  JSONArray history;
  EditText input;
  Spinner mode;
  Handler ui=new Handler(Looper.getMainLooper());
  boolean busy=false;
  @Override public void onCreate(Bundle b) { super.onCreate(b); pref=getSharedPreferences("lab",0); try{history=new JSONArray(pref.getString("history","[]"));}catch(Exception e){history=new JSONArray();}page("Sohbet");}
  TextView label(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextColor(fg);v.setTextSize(size);v.setPadding(12,12,12,12);return v;}
  void button(LinearLayout parent,String title,Runnable run){Button b=new Button(this);b.setText(title);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->run.run());}
  EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(fg);e.setHintTextColor(0xff9baabd);return e;}
  void page(String name){
    root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);setContentView(root);
    root.addView(label("FPRO AI LAB",24));
    LinearLayout tabs=new LinearLayout(this);root.addView(tabs);
    for(String n:new String[]{"Sohbet","Projeler","Bulgular","Ayarlar"}){TextView t=label(n,12);tabs.addView(t,new LinearLayout.LayoutParams(0,-2,1));t.setOnClickListener(v->page(n));}
    ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);
    if(name.equals("Sohbet"))chat();else if(name.equals("Ayarlar"))settings();else notes(name);
  }
  void notes(String section){String key=section.equals("Projeler")?"projects":"findings";EditText e=field(section,pref.getString(key,""));e.setMinLines(12);content.addView(e);button(content,"Kaydet",()->{pref.edit().putString(key,e.getText().toString()).apply();Toast.makeText(this,"Kaydedildi",0).show();});}
  void settings(){
    content.addView(label("API anahtarları yalnızca bu cihazda saklanır. Bu prototipte şifreli depolama henüz yoktur.",13));
    setting("OpenAI API anahtarı","openai",true);setting("Anthropic API anahtarı","anthropic",true);
    setting("OpenAI model kimliği","openai_model",false);setting("Claude model kimliği","claude_model",false);
    content.addView(label("Aylık 20 $ sınırı yalnızca uygulama içi tahmini kontroldür; sağlayıcı hesabında ayrıca limit koy.",13));
  }
  void setting(String title,String key,boolean secret){content.addView(label(title,14));EditText e=field(title,pref.getString(key,""));if(secret)e.setInputType(129);content.addView(e);button(content,"Kaydet: "+title,()->{pref.edit().putString(key,e.getText().toString().trim()).apply();Toast.makeText(this,"Kaydedildi",0).show();});}
  void chat(){
    content.addView(label("Ortak sohbet · GPT + Claude",17));
    content.addView(label("Tahmini kullanım: $"+pref.getFloat("spent",0)+" / $20",13));
    for(int i=0;i<history.length();i++){JSONObject o=history.optJSONObject(i);if(o!=null)content.addView(label(o.optString("who")+": "+o.optString("text"),14));}
    LinearLayout compose=new LinearLayout(this);compose.setOrientation(1);root.addView(compose);
    mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Claude","GPT","İkisi sırayla"}));compose.addView(mode);
    input=field("Mesaj yaz...","");compose.addView(input);button(compose,"Gönder",()->send());
  }
  synchronized void add(String who,String text){try{history.put(new JSONObject().put("who",who).put("text",text));pref.edit().putString("history",history.toString()).apply();}catch(Exception ignored){}}
  void send(){if(busy)return;String q=input.getText().toString().trim();if(q.isEmpty())return;if(pref.getFloat("spent",0)>=20){Toast.makeText(this,"Uygulama bütçe sınırına ulaşıldı",1).show();return;}String selected=mode.getSelectedItem().toString();add("Sen",q);busy=true;page("Sohbet");new Thread(()->{if(selected.equals("İkisi sırayla")){request("Claude");if(pref.getFloat("spent",0)<20)request("GPT");}else request(selected);busy=false;ui.post(()->page("Sohbet"));}).start();}
  void request(String who){
    try{
      boolean gpt=who.equals("GPT");String key=pref.getString(gpt?"openai":"anthropic","");String model=pref.getString(gpt?"openai_model":"claude_model","");
      if(key.isEmpty()||model.isEmpty()){add("Sistem",who+" için API anahtarı ve model kimliği gir.");return;}
      StringBuilder context=new StringBuilder("Türkçe yanıt ver. Ortak araştırma geçmişi:\n");
      synchronized(this){for(int i=Math.max(0,history.length()-12);i<history.length();i++){JSONObject o=history.optJSONObject(i);if(o!=null)context.append(o.optString("who")).append(": ").append(o.optString("text")).append("\n");}}
      JSONArray messages=new JSONArray().put(new JSONObject().put("role","user").put("content",context.toString()));
      JSONObject body=new JSONObject().put("model",model).put("messages",messages);
      if(gpt)body.put("max_completion_tokens",1000);else body.put("max_tokens",1000);
      HttpURLConnection conn=(HttpURLConnection)new URL(gpt?"https://api.openai.com/v1/chat/completions":"https://api.anthropic.com/v1/messages").openConnection();
      conn.setRequestMethod("POST");conn.setConnectTimeout(20000);conn.setReadTimeout(90000);conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");
      if(gpt)conn.setRequestProperty("Authorization","Bearer "+key);else{conn.setRequestProperty("x-api-key",key);conn.setRequestProperty("anthropic-version","2023-06-01");}
      try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
      int status=conn.getResponseCode();InputStream stream=status<400?conn.getInputStream():conn.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1)bytes.write(buf,0,n);
      JSONObject result=new JSONObject(bytes.toString("UTF-8"));if(status>=400){add("Sistem",who+" HTTP "+status+": "+result.optString("error").substring(0,Math.min(200,result.optString("error").length())));return;}
      String answer=gpt?result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content"):result.getJSONArray("content").getJSONObject(0).optString("text");
      add(who,answer);
      JSONObject usage=result.optJSONObject("usage");if(usage!=null){int in=usage.optInt(gpt?"prompt_tokens":"input_tokens"),out=usage.optInt(gpt?"completion_tokens":"output_tokens");float estimate=(float)((in*(gpt?2:4)+out*(gpt?10:20))/1000000.0);pref.edit().putFloat("spent",pref.getFloat("spent",0)+estimate).apply();}
    }catch(Exception ex){add("Sistem",who+" bağlantı hatası: "+ex.getMessage());}
  }
}