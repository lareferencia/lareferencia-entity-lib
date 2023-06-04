package org.lareferencia.core.entity.services;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


import net.minidev.json.annotate.JsonIgnore;
import org.lareferencia.core.entity.services.exception.*;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;


import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Setter;

// this class is a singleton because it is a spring service that runs in a single thread for every loading process
@Service
@ManagedResource(objectName = "org.lareferencia:type=EntityMonitorService")
public class EntityLoadingMonitorService {

	private static final String JSON = ".json";
	private static final String DD_MM_YYYY_HH_MM_SS = "dd_MM_yyyy_HH_mm_ss";
	private static final String REPORT_NAME = "_report_";

	//Loading flag
	@Setter
    private Boolean loadingProcessInProgress = false;

	@ManagedAttribute(description = "Loading process in progress")
	@JsonIgnore
	public Boolean loadingProcessInProgress() {
		return loadingProcessInProgress;
	}

	EntityLoadingStats entityLoadingStats = new EntityLoadingStats();

	@ManagedAttribute(description = "Source Entities Loaded")
	public Long getSourceEntitiesLoaded() {
		return entityLoadingStats.getSourceEntitiesLoaded();
	}

	@ManagedAttribute(description = "Entities Created")
	public Long getEntitiesCreated() {
		return entityLoadingStats.getEntitiesCreated();
	}

	@ManagedAttribute(description = "Entities Duplicated")
	public Long getEntitiesDuplicated() {
		return entityLoadingStats.getEntitiesDuplicated();
	}

	@ManagedAttribute(description = "Source Relations Loaded")
	public Long getSourceRelationsLoaded() {
		return entityLoadingStats.getSourceRelationsLoaded();
	}

	@ManagedAttribute(description = "Relations Created")
	public Long getRelationsCreated() {
		return entityLoadingStats.getRelationsCreated();
	}

	@Setter
	private Long totalProcessedFiles = 0L;

	public void incrementTotalProcessedFiles() {
		this.totalProcessedFiles++;
	}

	@Setter
	private Long totalSuccessfulFiles = 0L;

	public void incrementTotalSuccessfulFiles() {
		this.totalSuccessfulFiles++;
	}

	@Setter
	private Long totalFailedFiles = 0L;

	public void incrementTotalFailedFiles() {
		this.totalFailedFiles++;
	}


	private Map<String, Long> processingErrorsCount = new ConcurrentHashMap<String,Long>();
	private Map<String, ArrayList<String>> processingErrorsFiles = new ConcurrentHashMap<String,ArrayList<String>>();
	private Map<String, String> processingErrorsDetails = new ConcurrentHashMap<String,String>();


	@ManagedAttribute(description = "Processed File count")
	public Long getTotalProcessedFiles() {
		return totalProcessedFiles;
	}

	@ManagedAttribute(description = "Successful File count")
	public Long getTotalSuccessfulFiles() {
		return totalSuccessfulFiles;
	}

	@ManagedAttribute(description = "Error File count")
	public Long getTotalErrorFiles() {
		return totalFailedFiles;
	}

	@ManagedAttribute(description = "Processing Errors Count")
	public Map<String, Long> getProcessingErrorsCount() {
		return processingErrorsCount;
	}

	@ManagedAttribute(description = "Files with Processing Errors by Generic Error" )
	public Map<String, ArrayList<String>> getFilesByError() {
		return processingErrorsFiles;
	}

	@ManagedAttribute(description = "Processing Errors Details")
	public Map<String, String> getProcessingErrorsDetails() {
		return processingErrorsDetails;
	}

	public void reset() {
		
		this.totalProcessedFiles = 0L;
		this.totalSuccessfulFiles = 0L;
		this.totalFailedFiles = 0L;

		this.processingErrorsCount.clear();
		this.processingErrorsFiles.clear();

		this.entityLoadingStats.reset();
		this.indexingStats.reset();

	}	

	public void reportException(EntitiyRelationXMLLoadingException e) {
		
		//TODO: thwo exception if the cause is not a validation exception
		String fileName = e.getFileName();
		String exceptionMessage = e.getMessage();
		String genericMessage = exceptionMessage.substring(0, exceptionMessage.indexOf("::"));
		String exceptionDetails = exceptionMessage.substring(exceptionMessage.indexOf("::")+2);

		// increment procesing error count for generic message
		this.processingErrorsCount.put(genericMessage, this.processingErrorsCount.getOrDefault(genericMessage, 0L) + 1);

		// add file name to the map by generic message
		ArrayList<String> files = this.processingErrorsFiles.getOrDefault(genericMessage, new ArrayList<String>());
		files.add(fileName);
		this.processingErrorsFiles.put(genericMessage, files);

		// add exception details to procesing error details
		this.processingErrorsDetails.put(fileName, exceptionMessage);

	}

	/**
	 * Add entity loading stats to the report
	 * @param stats
	 */
	public void reportEntityLoadingStats(EntityLoadingStats stats) {
		this.entityLoadingStats.add(stats);
	}

	public void writeToJSON(String originalFileName) {

		ObjectMapper mapper = new ObjectMapper();
		String reportFilePath = originalFileName + REPORT_NAME + new SimpleDateFormat(DD_MM_YYYY_HH_MM_SS).format(new Date()) + JSON;

		try {
			//mapper.writeValue(new File(reportFilePath), this);
			mapper.writerWithDefaultPrettyPrinter().writeValue(new File(reportFilePath), this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*** Indexing Stats ***/

	private EntityIndexingStats indexingStats = new EntityIndexingStats();

	@ManagedAttribute(description = "Indexing::All Entities Sent to Indexing")
	public Integer getAllEntitiesSentToIndexCount() {
		return indexingStats.getAllSentEntitiesCount();
	}

	@ManagedAttribute(description = "Indexing::Unique Entities Sent to Indexing")
	public Integer getUniqueEntitiesSentToIndexCount() {
		return indexingStats.getUniqueSentEntitiesCount();
	}

	public void addEntitySentToIndex(UUID entityId, Long type) {
		this.indexingStats.addEntitySentToIndex(entityId, type);
	};

	public void reportEntityIndexingError(UUID entityId, String message) {
		this.indexingStats.registerErrorStat(entityId, message);
	};

	public String loadingReport() {
		return  "\n " + entityLoadingStats
				+ "\n totalProcessedFiles=" + totalProcessedFiles
				+ "\n totalSuccessfulFiles=" + totalSuccessfulFiles
				+ "\n totalFailedFiles=" + totalFailedFiles;
	}

	public String indexingReport() {
		return "\n Indexing Report  " + indexingStats;
	}
}
