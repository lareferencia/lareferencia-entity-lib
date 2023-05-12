package org.lareferencia.core.entity.xml.validation.exception;

import org.lareferencia.core.entity.xml.validation.report.DocumentValitaionReportEnum;

public class InvalidEntityModelContentException extends Exception{
	
	private static final long serialVersionUID = -4313080671067089014L;
	
	private static final String DEFAULT_MESSAGE = DocumentValitaionReportEnum.INVALID_CONTENT_ISSUE.getDescription();

	public InvalidEntityModelContentException() {
		super(DEFAULT_MESSAGE);
	}

	public InvalidEntityModelContentException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidEntityModelContentException(Throwable cause) {
		super(cause);
	}

	public InvalidEntityModelContentException(String message) {
		super(DEFAULT_MESSAGE+message);
	}


}
