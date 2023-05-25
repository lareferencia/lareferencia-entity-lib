package org.lareferencia.core.entity.services.exception;

import org.lareferencia.core.entity.services.to.EntityValitaionSummaryReportEnum;

public class InvalidModelXMLFileException extends Exception{
	
	private static final long serialVersionUID = -4313080671067089014L;
	
	private static final String DEFAULT_MESSAGE = EntityValitaionSummaryReportEnum.INVALID_MODEL_INTEGRITY_ISSUE.getDescription();

	public InvalidModelXMLFileException() {
		super(DEFAULT_MESSAGE);
	}

	public InvalidModelXMLFileException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidModelXMLFileException(Throwable cause) {
		super(cause);
	}

	public InvalidModelXMLFileException(String message) {
		super(DEFAULT_MESSAGE+message);
	}
}
