package com.argument.exporttrial;

import com.argument.version.VersionJson;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component @Profile("(local | test) & !prod")
public class TrialFileStore {
    private final Path root;private final byte[] key;private final SecureRandom random=new SecureRandom();
    public TrialFileStore(@Value("${member-d.files.root:./.local-data/files}") String directory){try{root=Path.of(directory).toAbsolutePath().normalize();Files.createDirectories(root);Path secret=root.resolve(".ticket-key");byte[] candidate=new byte[32];random.nextBytes(candidate);try{Files.write(secret,candidate,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}key=Files.readAllBytes(secret);if(key.length!=32)throw new IllegalStateException("Invalid local ticket encryption key");}catch(Exception e){throw new IllegalStateException("Cannot initialize private file storage",e);}}
    public String publish(String id,byte[] bytes){VersionJson.uuid(id);Path target=root.resolve(id),temp=null;try{temp=Files.createTempFile(root,"pending-",".part");Files.write(temp,bytes);Files.move(temp,target);if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int state){if(state!=STATUS_COMMITTED)try{Files.deleteIfExists(target);}catch(Exception ignored){}}});return id;}catch(Exception e){if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}throw new IllegalStateException("File publication failed",e);}}
    public byte[] read(String key){try{return Files.readAllBytes(root.resolve(VersionJson.uuid(key)));}catch(Exception e){throw new IllegalStateException("File unavailable",e);}}
    public String token(){byte[] n=new byte[32];random.nextBytes(n);return Base64.getUrlEncoder().withoutPadding().encodeToString(n);}
    public String encrypt(String plaintext){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] all=Arrays.copyOf(iv,iv.length+encrypted.length);System.arraycopy(encrypted,0,all,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("Cannot seal response",e);}}
    public String decrypt(String sealed){try{byte[] all=Base64.getDecoder().decode(sealed);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(all,12)));return new String(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Cannot read sealed response",e);}}
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
}
