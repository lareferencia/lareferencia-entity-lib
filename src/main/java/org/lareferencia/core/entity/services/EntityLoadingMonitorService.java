package org.lareferencia.core.entity.services;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import org.lareferencia.core.entity.services.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;


import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

// this class is a singleton because it is a spring service that runs in a single thread for every loading process
@Scope("singleton")
@Service
@ManagedResource(objectName = "org.lareferencia:type=EntityLoadingMonitorService")
public class EntityLoadingMonitorService {

	private static final String JSON = ".json";
	private static final String DD_MM_YYYY_HH_MM_SS = "dd_MM_yyyy_HH_mm_ss";
	private static final String REPORT_NAME = "entity-report_";

	//Loading flag
	@Getter
	@Setter
	@Value("${loadingProcessMonitor.isLoadingProcessInProgress:false}")
    private Boolean isLoadingProcessInProgress;

	@ManagedAttribute(description = "Loading process in progress")
	public boolean isLoadingProcessInProgress() {
		return isLoadingProcessInProgress;
	}
	
	EntityLoadingStats entityLoadingStats = new EntityLoadingStats();

	@ManagedAttribute(description = "Monitor: Entities and relations loaded statistics")
	public EntityLoadingStats getEntityLoadingStatistics() {
		return entityLoadingStats;
	}

	@Setter
	private Long totalProcessedFiles = 0L;

	public void incrementTotalProcessedFiles() {
		this.totalProcessedFiles++;
	}

	@Setter
	private Long totalSuccesfulFiles = 0L;

	public void incrementTotalSuccesfulFiles() {
		this.totalSuccesfulFiles++;
	}

	@Setter
	private Long totalFailedFiles = 0L;

	public void incrementTotalFailedFiles() {
		this.totalFailedFiles++;
	}


	private Map<String, Long>   procesingErrorsCount = new ConcurrentHashMap<String,Long>();
	private Map<String, ArrayList<String>> procesingErrorsFiles = new ConcurrentHashMap<String,ArrayList<String>>();
	private Map<String, String> procesingErrorsDetails = new ConcurrentHashMap<String,String>();


	@ManagedAttribute(description = "Procesed File count")
	public Long getTotalProcessedFiles() {
		return totalProcessedFiles;
	}

	@ManagedAttribute(description = "Succesful File count")
	public Long getTotalSuccesfulFiles() {
		return totalSuccesfulFiles;
	}

	@ManagedAttribute(description = "Error File count")
	public Long getTotalErrorFiles() {
		return totalFailedFiles;
	}

	@ManagedAttribute(description = "Procesing Errors Count")
	public Map<String, Long> getProcesingErrorsCount() {
		return procesingErrorsCount;
	}

	@ManagedAttribute(description = "Files with Procesing Errors by Generic Error" )
	public Map<String, ArrayList<String>> getFilesByError() {
		return procesingErrorsFiles;
	}

	@ManagedAttribute(description = "Procesing Errors Details")
	public Map<String, String> getProcesingErrorsDetails() {
		return procesingErrorsDetails;
	}

	public void reset() {
		
		this.totalProcessedFiles = 0L;
		this.totalSuccesfulFiles = 0L;
		this.totalFailedFiles = 0L;
		this.procesingErrorsCount = new ConcurrentHashMap<String,Long>();
		this.procesingErrorsFiles = new ConcurrentHashMap<String,ArrayList<String>>();
		this.entityLoadingStats = new EntityLoadingStats();

	}	

	public void reportException(EntitiyRelationXMLLoadingException e) {
		
		//TODO: thwo exception if the cause is not a validation exception
		String fileName = e.getFileName();
		String exceptionMessage = e.getMessage();
		String genericMessage = exceptionMessage.substring(0, exceptionMessage.indexOf("::"));
		String exceptionDetails = exceptionMessage.substring(exceptionMessage.indexOf("::")+2);

		// increment procesing error count for generic message
		this.procesingErrorsCount.put(genericMessage, this.procesingErrorsCount.getOrDefault(genericMessage, 0L) + 1);

		// add file name to the map by generic message
		ArrayList<String> files = this.procesingErrorsFiles.getOrDefault(genericMessage, new ArrayList<String>());
		files.add(fileName);
		this.procesingErrorsFiles.put(genericMessage, files);

		// add exception details to procesing error details
		this.procesingErrorsDetails.put(fileName, exceptionMessage);

	}

	/**
	 * Add entity loading stats to the report
	 * @param stats
	 */
	public void reportEntityLoadingStats(EntityLoadingStats stats) {
		this.entityLoadingStats.add(stats);
	}

	public void writeToJSON() {

		ObjectMapper mapper = new ObjectMapper();
		String fileName = REPORT_NAME + new SimpleDateFormat(DD_MM_YYYY_HH_MM_SS).format(new Date()) + JSON;
		// TODO: improve this 
		String filePath =  fileName;
		try {
			mapper.writeValue(new File(filePath), this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	

}
