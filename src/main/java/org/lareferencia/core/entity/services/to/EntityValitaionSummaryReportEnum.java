package org.lareferencia.core.entity.services.to;



public enum EntityValitaionSummaryReportEnum {
	
	PROCESSING(1, "Summary Report: File Under Processing..."),
	PROCESSED(2, "Summary Report: File Under Processed."),
	VALID(3, "Summary Report: Valid XML File."),
    INVALID_ESTRUCTURAL_ISSUE(4, "Summary Report: [Invalid XML Infrastructure File] "),
    INVALID_MODEL_INTEGRITY_ISSUE(5, "Summary Report: [Invalid XML Model Integrity File] "),
    INVALID_ENTITY_MODEL_INTEGRITY_ISSUE(6, "Summary: [Invalid Entity Model Correlation Data] "),
    INVALID_CONTENT_ISSUE(7, "Summary Report: [Invalid Content of the XML Entity Model] "),
    GENERIC_ERROR(8, "Summary Report: [Generic Error on parsing XML File to Entity-Relation] ");

    private final int value;
    private final String description;

    EntityValitaionSummaryReportEnum(int valor, String descricao) {
        this.value = valor;
        this.description = descricao;
    }

    public int getValue() {
        return this.value;
    }
    public String getDescription() {
        return this.description;
    }

}
