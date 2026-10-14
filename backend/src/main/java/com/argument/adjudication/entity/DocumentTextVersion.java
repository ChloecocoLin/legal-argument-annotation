package com.argument.adjudication.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("document_text_version")
public class DocumentTextVersion {
    @TableId
    private String id;
    private String documentId;
    private Integer versionNumber;
    private String normalizedText;
    private String contentSha256;
    private String status;
    private Integer recordVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
