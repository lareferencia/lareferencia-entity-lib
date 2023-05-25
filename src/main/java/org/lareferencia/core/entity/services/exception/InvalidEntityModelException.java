package org.lareferencia.core.entity.services.exception;

import org.lareferencia.core.entity.services.to.EntityValitaionSummaryReportEnum;

public class InvalidEntityModelException extends Exception{
	
	private static final long serialVersionUID = -4313080671067089014L;
	
	private static final String DEFAULT_MESSAGE = EntityValitaionSummaryReportEnum.INVALID_ENTITY_MODEL_INTEGRITY_ISSUE.getDescription();

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
		super(DEFAULT_MESSAGE+message);
	}

}
