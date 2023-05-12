package org.lareferencia.core.entity.xml.validation.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@NoArgsConstructor
public class DocumentValitaionReportTO {

	@Getter
	@Setter
	private String file;
	
	@Getter
	@Setter
	private DocumentValitaionReportEnum status;
	
	
	@Getter
	@Setter
	private String details;
	
}
