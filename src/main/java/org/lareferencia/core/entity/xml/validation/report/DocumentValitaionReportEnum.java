package org.lareferencia.core.entity.xml.validation.report;


/**
 * 
 * @author jbjares
 * 
 * ================================================================
	Total arquivos processados: 100
	Total arquivos validos: 80
	Total arquivos invalidos por estrutura: 10
	Total arquivos invalidos por falta de integridade ao modelo: 10
	================================================================
	
	1- <path completo do arquivo>/simple_data_1 - valido.
	2- <path completo do arquivo>/simple_data_1 - invalido por estrutura.
	3- <path completo do arquivo>/simple_data_1 - invalido por falta de integridade ao modelo.
 *
 */
public enum DocumentValitaionReportEnum {
	
	PROCESSING(1, "File Under Processing..."),
	PROCESSED(2, "File Under Processed."),
	VALID(3, "Valid XML File."),
    INVALID_ESTRUCTURAL_ISSUE(4, "[Invalid XML Infrastructure File] "),
    INVALID_MODEL_INTEGRITY_ISSUE(5, "[Invalid XML Model Integrity File] "),
    INVALID_ENTITY_MODEL_INTEGRITY_ISSUE(6, "[Invalid Entity Model Correlation Data] "),
    INVALID_CONTENT_ISSUE(7, "[Invalid Content of the XML Entity Model] "),
    GENERIC_ERROR(8, "[Generic Error on parsing XML File to Entity-Relation] ");

    private final int value;
    private final String description;

    DocumentValitaionReportEnum(int valor, String descricao) {
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
