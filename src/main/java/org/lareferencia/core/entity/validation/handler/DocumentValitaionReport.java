package org.lareferencia.core.entity.validation.handler;

import org.springframework.stereotype.Component;

@Component
public class DocumentValitaionReport {

	private StringBuilder report = new StringBuilder();
	
	private Boolean isValidationStatusOk = Boolean.TRUE;

	public StringBuilder getReport() {
		return report;
	}

	public void setReport(StringBuilder report) {
		this.report = report;
	}

	public Boolean getIsValidationStatusOk() {
		return isValidationStatusOk;
	}

	public void setIsValidationStatusOk(Boolean isValidationStatusOk) {
		this.isValidationStatusOk = isValidationStatusOk;
	}

	
	
}
