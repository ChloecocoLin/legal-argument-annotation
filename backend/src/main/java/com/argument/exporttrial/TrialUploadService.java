package com.argument.exporttrial;
import com.argument.common.exception.*;
import com.argument.version.VersionJson;
import com.argument.version.port.VersionIdentity.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.argument.adjudication.AdjudicationService.require;

/** Unattached admin uploads stay private; creator submissions require A's pending review workflow. */
@Service @Profile("(local | test) & !prod")
public class TrialUploadService {
    private final ExportTrialRepository store;private final TrialFileStore files;private final ObjectMapper mapper;
    public TrialUploadService(ExportTrialRepository store,TrialFileStore files,ObjectMapper mapper){this.store=store;this.files=files;this.mapper=mapper;}
    @Transactional public ObjectNode upload(Actor actor,String key,ObjectNode metadata,byte[] bytes,String requestId){
        require(actor.role("ADMIN")&&(actor.permission("DOCUMENT_MANAGE")||actor.permission("GUIDE_MANAGE")),ErrorCode.FORBIDDEN,"本批普通原件仅供管理员私有上传；创建者待审提交需先接 A 的审核模型");VersionJson.uuid(key);store.lockActor(actor.id());var fields=new HashSet<String>();metadata.fieldNames().forEachRemaining(fields::add);require(fields.equals(Set.of("fileName","mimeType"))&&metadata.path("fileName").isTextual()&&metadata.path("mimeType").isTextual(),ErrorCode.VALIDATION_ERROR,"上传元数据需为 fileName/mimeType");String name=metadata.path("fileName").asText(),mime=metadata.path("mimeType").asText();require(!name.isBlank()&&name.length()<=255&&name.indexOf('/')<0&&name.indexOf('\\')<0&&name.chars().noneMatch(c->c<32),ErrorCode.FILE_INVALID,"文件名无效");require(bytes.length>0&&bytes.length<=20_000_000,ErrorCode.FILE_INVALID,"上传文件需在 1–20000000 字节之间");String hash=VersionJson.sha256(metadata.toString()+TrialFileStore.sha256(bytes));String cached=store.receipt(actor.id(),"F01",key,hash);if(cached!=null)return VersionJson.parse(mapper,files.decrypt(cached));validate(bytes,mime);
        var file=mapper.createObjectNode();String now=VersionJson.now();VersionJson.stamp(file,actor.id(),now);file.put("fileName",name).put("mimeType",mime).put("sizeBytes",bytes.length).put("sha256",TrialFileStore.sha256(bytes)).put("storageKey",files.publish(file.path("id").asText(),bytes)).put("status","AVAILABLE");store.file(file,null,null);var event=mapper.createObjectNode();VersionJson.stamp(event,actor.id(),now);event.put("objectType","FileAsset").put("objectId",file.path("id").asText()).put("operationCode","F01").put("actorUserId",actor.id()).put("requestId",requestId).put("occurredAt",now).put("status","AVAILABLE").put("sourceDigest",file.path("sha256").asText());event.putArray("finalVersionIds");event.putArray("sourceManifest");store.event(event);var safe=file.deepCopy();safe.remove("storageKey");store.receipt(actor.id(),"F01",key,hash,files.encrypt(safe.toString()));return safe;
    }
    private void validate(byte[] bytes,String mime){try{switch(mime){case "text/plain"->StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));case "application/pdf"->require(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"),ErrorCode.FILE_INVALID,"PDF 文件头不匹配");case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"->{boolean document=false,types=false;long expanded=0;try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){byte[] buffer=new byte[8192];for(ZipEntry e;(e=zip.getNextEntry())!=null;){if(e.getName().equals("word/document.xml"))document=true;if(e.getName().equals("[Content_Types].xml"))types=true;require(!e.getName().toLowerCase().contains("vbaproject"),ErrorCode.FILE_INVALID,"不接受包含宏的文档");for(int n;(n=zip.read(buffer))!=-1;){expanded+=n;require(expanded<=50_000_000,ErrorCode.FILE_INVALID,"文档解压内容过大");}}}require(document&&types,ErrorCode.FILE_INVALID,"不是有效的 DOCX 文档");}default->throw new BusinessException(ErrorCode.FILE_INVALID,"首批上传支持 UTF-8 TXT、PDF、DOCX");}}catch(IOException e){throw new BusinessException(ErrorCode.FILE_INVALID,"文件编码或容器无效");}}
}
