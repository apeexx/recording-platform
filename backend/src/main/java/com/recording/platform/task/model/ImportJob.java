package com.recording.platform.task.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = "import_jobs")
@CompoundIndexes({
	@CompoundIndex(name = "unique_task_import_operation", def = "{'taskId': 1, 'operationId': 1}", unique = true),
	@CompoundIndex(name = "import_job_status", def = "{'status': 1, 'createdAt': 1}"),
	@CompoundIndex(name = "import_job_recovery", def = "{'status': 1, 'leaseExpiresAt': 1}")
})
public class ImportJob {
	@Id
	private String id;
	@Version
	private Long version;
	private String taskId;
	private String operationId;
	private String actorUserId;
	private String actorUsername;
	private String originalFilename;
	private String fileSha256;
	private long fileSizeBytes;
	private String sourceRelativePath;
	private ImportJobStatus status;
	private long totalRows;
	private long successRows;
	private long failureRows;
	private List<ImportRowError> rowErrors = new ArrayList<>();
	private List<Long> retryRowNumbers = new ArrayList<>();
	private ImportRunMode runMode = ImportRunMode.FULL;
	private String leaseOwner;
	private Instant leaseExpiresAt;
	private Instant heartbeatAt;
	private int attempt;
	private Instant createdAt;
	private Instant startedAt;
	private Instant completedAt;
	private Instant updatedAt;
}
