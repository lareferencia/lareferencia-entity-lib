package org.lareferencia.core.entity.services;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.beanutils.BeanUtils;
import org.lareferencia.core.entity.services.to.EntityValitaionTO;
import org.lareferencia.core.entity.services.to.SummaryReportTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

@Service
@ManagedResource(objectName = "org.lareferencia:type=EntityLoadingMonitorService")
public class EntityLoadingMonitorService {
	
	
	private static final String JSON = ".json";
	private static final String DD_MM_YYYY_HH_MM_SS = "dd_MM_yyyy_HH_mm_ss";
	private static final String DRY_RUN_REPORT = "entity-report_";
	
	//Loading flag
	@Getter
	@Setter
	@Value("${loadingProcessMonitor.isLoadingProcessInProgress:false}")
    private Boolean isLoadingProcessInProgress;

	@ManagedAttribute(description = "Loading process in progress")
	public boolean isLoadingProcessInProgress() {
		return isLoadingProcessInProgress;
	}

	//Monitor
	@Setter
	private Long loadedEntities;
	@Setter
	private Long duplicatedEntities;
	@Setter
	private Long discardedEntities;
	@Setter
	private Long processedFiles;
	@Setter
	private Long errorFiles;
	
	@Getter
	@Setter
	private List<EntityValitaionTO> loadedEntitiesList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> duplicatedEntitiesaList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> discardedEntitiesList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> processedFilesList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> errorFilesList = new ArrayList<>();
	
	@ManagedAttribute(description = "Monitor: Number of individuals of the loaded entities")
	public Long getLoadedEntities() {
		return loadedEntities;
	}

	@ManagedAttribute(description = "Monitor: Number of individuals of the duplicated entities")
	public Long getDuplicatedEntities() {
		return duplicatedEntities;
	}

	@ManagedAttribute(description = "Monitor: Number of individuals from discarded entities")
	public Long getDiscardedEntities() {
		return discardedEntities;
	}

	@ManagedAttribute(description = "Monitor: Number of successfully processed files")
	public Long getProcessedFiles() {
		return processedFiles;
	}

	@ManagedAttribute(description = "Monitor: Number of files processed with error")
	public Long getErrorFiles() {
		return errorFiles;
	}
	
	public void updateMonitorProcessmentFile() {
		
		Long countAllProcessedFiles = Math.addExact(0, Long.valueOf(this.getGenericErroFilesList().size()));
		countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
		countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidModelFilesList().size()));
		countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidContentDataList().size()));
		this.setProcessedFiles(countAllProcessedFiles);
		
		Long countAllErrorFiles = Math.addExact(0, Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
		countAllErrorFiles = Math.addExact(countAllErrorFiles, this.getInvalidModelFilesList().size());
		countAllErrorFiles = Math.addExact(countAllErrorFiles, Long.valueOf(this.getGenericErroFilesList().size()));
		countAllErrorFiles = Math.addExact(countAllErrorFiles, Long.valueOf(this.getInvalidContentDataList().size()));
		countAllErrorFiles = Math.addExact(countAllErrorFiles, Long.valueOf(this.getErrorFilesList().size()));

		
		this.setLoadedEntities(Long.valueOf(this.getLoadedEntitiesList().size()));
		this.setDuplicatedEntities(Long.valueOf(this.getDuplicatedEntitiesaList().size()));
		this.setDiscardedEntities(Long.valueOf(this.getDiscardedEntitiesList().size()));


	}
	
	//summary
	@Setter
	private Long totalProcessedFiles;
	@Setter
	private Long totalGenericErrorFiles;
	@Setter
	private Long totalValidFiles;
	@Setter
	private Long totalInvalidStructuredXMLFiles;
	@Setter
	private Long totalInvalidModelFiles;
	@Setter
	private Long totalInvalidContentData;
	
	@Getter
	@Setter
	private List<EntityValitaionTO> invalidStructuredXMLFilesList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> invalidContentDataList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> invalidModelFilesList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<EntityValitaionTO> genericErroFilesList = new ArrayList<>();
	

	@ManagedAttribute(description = "Summary: Total of files processed files")
	public Long getTotalProcessedFiles() {
		return totalProcessedFiles;
	}

	@ManagedAttribute(description = "Summary: Total of generic erros files")
	public Long getTotalGenericErrorFiles() {
		return totalGenericErrorFiles;
	}

	@ManagedAttribute(description = "Summary: Total of valid files")
	public Long getTotalValidFiles() {
		return totalValidFiles;
	}

	@ManagedAttribute(description = "Summary: Total of invalid XML structured files")
	public Long getTotalInvalidStructuredXMLFiles() {
		return totalInvalidStructuredXMLFiles;
	}

	@ManagedAttribute(description = "Summary: Total of invalid Model files")
	public Long getTotalInvalidModelFiles() {
		return totalInvalidModelFiles;
	}

	@ManagedAttribute(description = "Summary: Total of invalid Content Data files")
	public Long getTotalInvalidContentData() {
		return totalInvalidContentData;
	}
	
	public void updateSummaryProcessmentFile() {
		Long countAllProcessedFiles = Math.addExact(0, Long.valueOf(this.getGenericErroFilesList().size()));
		countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
		countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidModelFilesList().size()));
		countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidContentDataList().size()));
		this.setTotalProcessedFiles(countAllProcessedFiles);
		
		Long countTotalValidFiles = Math.subtractExact(countAllProcessedFiles, Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
		countTotalValidFiles = Math.subtractExact(countTotalValidFiles, Long.valueOf(this.getInvalidModelFilesList().size()));
		countTotalValidFiles = Math.subtractExact(countTotalValidFiles, Long.valueOf(this.getGenericErroFilesList().size()));
		countTotalValidFiles = Math.subtractExact(countTotalValidFiles, Long.valueOf(this.getInvalidContentDataList().size()));
		
		this.setTotalValidFiles(countTotalValidFiles);
		this.setTotalInvalidStructuredXMLFiles(Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
		this.setTotalInvalidModelFiles(Long.valueOf(this.getInvalidModelFilesList().size()));
		this.setTotalGenericErrorFiles(Long.valueOf(this.getGenericErroFilesList().size()));
		this.setTotalInvalidContentData(Long.valueOf(this.getInvalidContentDataList().size()));
	}

	public void generateSummaryofTotalProcessedFiles(String folderPath) throws JsonGenerationException, JsonMappingException, IOException {
		
		try {
			Long countAllProcessedFiles = Math.addExact(0, Long.valueOf(this.getGenericErroFilesList().size()));
			countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
			countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidModelFilesList().size()));
			countAllProcessedFiles = Math.addExact(countAllProcessedFiles, Long.valueOf(this.getInvalidContentDataList().size()));
			this.setTotalProcessedFiles(countAllProcessedFiles);
			
			Long countTotalValidFiles = Math.subtractExact(countAllProcessedFiles, Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
			countTotalValidFiles = Math.subtractExact(countTotalValidFiles, Long.valueOf(this.getInvalidModelFilesList().size()));
			countTotalValidFiles = Math.subtractExact(countTotalValidFiles, Long.valueOf(this.getGenericErroFilesList().size()));
			countTotalValidFiles = Math.subtractExact(countTotalValidFiles, Long.valueOf(this.getInvalidContentDataList().size()));
			
			this.setTotalValidFiles(countTotalValidFiles);
			this.setTotalInvalidStructuredXMLFiles(Long.valueOf(this.getInvalidStructuredXMLFilesList().size()));
			this.setTotalInvalidModelFiles(Long.valueOf(this.getInvalidModelFilesList().size()));
			this.setTotalGenericErrorFiles(Long.valueOf(this.getGenericErroFilesList().size()));
			this.setTotalInvalidContentData(Long.valueOf(this.getInvalidContentDataList().size()));

			generateSummaryReport(folderPath);
		}catch(Exception e) {
			throw new RuntimeException(e.getMessage(),e);
		}finally {
			updateAllSummaryAndReportData();
		}

	}
	
	
	private void generateSummaryReport(String folderPath) throws JsonGenerationException, JsonMappingException, IOException, IllegalAccessException, InvocationTargetException {
		SummaryReportTO summaryJsonrepresentation = new SummaryReportTO();
		BeanUtils.copyProperties(summaryJsonrepresentation,this );
		ObjectMapper mapper = new ObjectMapper();
		String fileName = DRY_RUN_REPORT + new SimpleDateFormat(DD_MM_YYYY_HH_MM_SS).format(new Date()) + JSON;
		String filePath = folderPath + File.separator + fileName;
		mapper.writeValue(new File(filePath), summaryJsonrepresentation);
		
	}

	public void updateAllSummaryAndReportData() {
		this.updateMonitorProcessmentFile();
		this.updateSummaryProcessmentFile();
	}
	
	public void resetAllMonitorCachedData() {
		this.setInvalidStructuredXMLFilesList(new ArrayList<>());
		this.setInvalidModelFilesList(new ArrayList<>());
		this.setGenericErroFilesList(new ArrayList<>());
		this.setInvalidContentDataList(new ArrayList<>());
	}
	
	public void resetAllSummaryCachedData() {
		this.setLoadedEntitiesList(new ArrayList<>());
		this.setDuplicatedEntitiesaList(new ArrayList<>());
		this.setDiscardedEntitiesList(new ArrayList<>());
		this.setProcessedFilesList(new ArrayList<>());
		this.setErrorFilesList(new ArrayList<>());
	}
	
	public void resetAllCachedData() {
		resetAllMonitorCachedData();
		resetAllSummaryCachedData();
	}

}
