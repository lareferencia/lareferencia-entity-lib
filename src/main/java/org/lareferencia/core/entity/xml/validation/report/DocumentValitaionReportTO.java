package org.lareferencia.core.entity.xml.validation.report;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


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

@AllArgsConstructor
@NoArgsConstructor
public class DocumentValitaionReportTO {

	@Getter
	@Setter
	private String filePath;
	
	@Getter
	@Setter
	private DocumentValitaionReportEnum situationStatus;
	
	
	@Getter
	@Setter
	private String moreDetails;
	
}
