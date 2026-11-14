package tr.fpro.ailab;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
public final class SecretStore {
 private static final String ALIAS="fpro_ai_lab_key_v1";
 private final SharedPreferences prefs;
 public SecretStore(Context context){prefs=context.getSharedPreferences("secrets",Context.MODE_PRIVATE);}
 private SecretKey key() throws Exception {
   KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
   if(!store.containsAlias(ALIAS)){
     KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
     gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
       .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
       .setKeySize(256).build());gen.generateKey();
   }
   return (SecretKey)store.getKey(ALIAS,null);
 }
 public void put(String name,String value) throws Exception {
   Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
   byte[] raw=c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   prefs.edit().putString(name,Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(raw,Base64.NO_WRAP)).apply();
 }
 public String get(String name) throws Exception {
   String data=prefs.getString(name,"");if(data.isEmpty())return "";
   String[] parts=data.split(":",2);if(parts.length!=2)throw new IllegalStateException("Şifreli veri okunamadı");
   Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
   c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
   return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
 }
 public boolean has(String name){return prefs.contains(name);}
}
