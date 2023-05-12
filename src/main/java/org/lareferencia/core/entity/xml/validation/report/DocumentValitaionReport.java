package org.lareferencia.core.entity.xml.validation.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
public class DocumentValitaionReport {
	
	@Getter
	@Setter
	private Long totalProcessedFiles;
	
	@Getter
	@Setter
	private Long totalGenericErrorFiles;
	
	@Getter
	@Setter
	private Long totalValidFiles;
	
	@Getter
	@Setter
	private Long totalInvalidStructuredXMLFiles;
	
	@Getter
	@Setter
	private Long totalInvalidModelFiles;
	
	@Getter
	@Setter
	private Long totalInvalidContentData;

	
	@Getter
	@Setter
	private List<DocumentValitaionReportTO> invalidStructuredXMLFilesList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<DocumentValitaionReportTO> invalidContentDataList = new ArrayList<>();
	
	@Getter
	@Setter
	private List<DocumentValitaionReportTO> invalidModelFilesList = new ArrayList<>();
	
	
	@Getter
	@Setter
	private List<DocumentValitaionReportTO> genericErroFilesList = new ArrayList<>();
}
