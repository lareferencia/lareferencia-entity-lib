package org.lareferencia.core.entity.services.exception;

import org.lareferencia.core.entity.services.to.EntityValitaionSummaryReportEnum;

public class LoadDataValidationGenericException extends Exception{

	private static final String DEFAULT_MESSAGE = EntityValitaionSummaryReportEnum.GENERIC_ERROR.getDescription();

	
	public LoadDataValidationGenericException() {
		super(DEFAULT_MESSAGE);
	}

	private static final long serialVersionUID = 3667116253070316974L;


}
