package org.lareferencia.core.entity.services.to;

public enum EntityValitaionMonitorEnum {
	
	LOADED_ENTITIES(1, "Monitor: File Under Processing..."),
	DUPLICATED_ENTITIES(2, "Monitor: File Under Processed."),
	DISCARD_ENTITIES(3, "Monitor: Valid XML File."),
    PROCESSED_FILES(4, "Monitor: [Invalid XML Infrastructure File] "),
    ERROR_FILES(5, "Monitor: [Invalid XML Model Integrity File] ");


    private final int value;
    private final String description;

    EntityValitaionMonitorEnum(int valor, String descricao) {
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
