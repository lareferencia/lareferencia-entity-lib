package org.lareferencia.core.entity.xml.validation.exception;

import org.lareferencia.core.entity.xml.validation.report.DocumentValitaionReportEnum;

public class InvalidModelXMLFileException extends EntityXMLValidationException {
	
	private static final long serialVersionUID = -4313080671067089014L;
	
	private static final String DEFAULT_MESSAGE = DocumentValitaionReportEnum.INVALID_MODEL_INTEGRITY_ISSUE.getDescription();

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
		super(DEFAULT_MESSAGE.concat(message));
	}
	public InvalidModelXMLFileException(String message,String extraMessage,Throwable cause) {
		super(DEFAULT_MESSAGE.concat(extraMessage), cause);
	}

}
