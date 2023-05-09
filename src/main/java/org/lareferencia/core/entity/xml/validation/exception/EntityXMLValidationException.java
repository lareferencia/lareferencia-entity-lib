package org.lareferencia.core.entity.xml.validation.exception;

import org.lareferencia.core.entity.xml.validation.report.DocumentValitaionReportEnum;

public class EntityXMLValidationException extends Exception{

	private static final String DEFAULT_MESSAGE = DocumentValitaionReportEnum.GENERIC_ERROR.getDescription();

	
	public EntityXMLValidationException() {
		super(DEFAULT_MESSAGE);
	}

	public EntityXMLValidationException(String message, Throwable cause) {
		super(message, cause);
	}

	public EntityXMLValidationException(Throwable cause) {
		super(cause);
	}

	public EntityXMLValidationException(String message) {
		super(DEFAULT_MESSAGE.concat(message));
	}
	public EntityXMLValidationException(String message,String extraMessage,Throwable cause) {
		super(DEFAULT_MESSAGE.concat(extraMessage), cause);
	}
	private static final long serialVersionUID = 3667116253070316974L;


}
