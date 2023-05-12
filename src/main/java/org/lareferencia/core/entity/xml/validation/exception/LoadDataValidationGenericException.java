package org.lareferencia.core.entity.xml.validation.exception;

import org.lareferencia.core.entity.xml.validation.report.DocumentValitaionReportEnum;

public class LoadDataValidationGenericException extends Exception{

	private static final String DEFAULT_MESSAGE = DocumentValitaionReportEnum.GENERIC_ERROR.getDescription();

	
	public LoadDataValidationGenericException() {
		super(DEFAULT_MESSAGE);
	}

	private static final long serialVersionUID = 3667116253070316974L;


}
