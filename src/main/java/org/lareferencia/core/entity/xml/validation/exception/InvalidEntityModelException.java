package org.lareferencia.core.entity.xml.validation.exception;

import org.lareferencia.core.entity.xml.validation.report.DocumentValitaionReportEnum;

public class InvalidEntityModelException extends Exception{
	
	private static final long serialVersionUID = -4313080671067089014L;
	
	private static final String DEFAULT_MESSAGE = DocumentValitaionReportEnum.INVALID_ENTITY_MODEL_INTEGRITY_ISSUE.getDescription();

	public InvalidEntityModelException() {
		super(DEFAULT_MESSAGE);
	}

	public InvalidEntityModelException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidEntityModelException(Throwable cause) {
		super(cause);
	}

	public InvalidEntityModelException(String message) {
		super(DEFAULT_MESSAGE.concat(message));
	}
	public InvalidEntityModelException(String message,String extraMessage,Throwable cause) {
		super(DEFAULT_MESSAGE.concat(extraMessage), cause);
	}

}
