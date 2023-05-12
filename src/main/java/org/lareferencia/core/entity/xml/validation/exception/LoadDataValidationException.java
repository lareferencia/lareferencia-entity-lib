package org.lareferencia.core.entity.xml.validation.exception;

import org.lareferencia.core.entity.xml.validation.report.DocumentValitaionReportEnum;

public class LoadDataValidationException extends Exception{

	private static final String DEFAULT_MESSAGE = DocumentValitaionReportEnum.GENERIC_ERROR.getDescription();

	
	public LoadDataValidationException() {
		super(DEFAULT_MESSAGE);
	}

	public LoadDataValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public LoadDataValidationException(Throwable cause) {
		super(cause);
	}

	public LoadDataValidationException(String message) {
		super(message);
	}

	private static final long serialVersionUID = 3667116253070316974L;


}
