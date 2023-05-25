package org.lareferencia.core.entity.services.exception;

import org.lareferencia.core.entity.services.to.EntityValitaionSummaryReportEnum;

public class InvalidStructureXMLFileException extends Exception{
	

	private static final String DEFAULT_MESSAGE = EntityValitaionSummaryReportEnum.INVALID_ESTRUCTURAL_ISSUE.getDescription();
	
	
	public InvalidStructureXMLFileException() {
		super(DEFAULT_MESSAGE);
	}

	public InvalidStructureXMLFileException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidStructureXMLFileException(Throwable cause) {
		super(cause);
	}

	public InvalidStructureXMLFileException(String message) {
		super(DEFAULT_MESSAGE+message);
	}
	
	
	private static final long serialVersionUID = 3667116253070316974L;
	

}
