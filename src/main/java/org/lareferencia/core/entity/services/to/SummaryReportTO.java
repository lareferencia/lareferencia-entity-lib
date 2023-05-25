package org.lareferencia.core.entity.services.to;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SummaryReportTO {

	private Long totalProcessedFiles;
	private Long totalGenericErrorFiles;
	private Long totalValidFiles;
	private Long totalInvalidStructuredXMLFiles;
	private Long totalInvalidModelFiles;
	private Long totalInvalidContentData;
	private List<EntityValitaionTO> invalidStructuredXMLFilesList = new ArrayList<>();
	private List<EntityValitaionTO> invalidContentDataList = new ArrayList<>();
	private List<EntityValitaionTO> invalidModelFilesList = new ArrayList<>();
	private List<EntityValitaionTO> genericErroFilesList = new ArrayList<>();

}
