package org.lareferencia.core.entity.validation.handler;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
public class DocumentValitaionChain {

	@Autowired
	private DocumentValitaionReport report;
	
    @Autowired
    private List<IDocumentValitaionHandler> handlers;

    public boolean validate(Document document) {
    	try {
            for (IDocumentValitaionHandler handler : handlers) {
                boolean isValid = handler.validate(document);
                if (!isValid) {
                	report.setIsValidationStatusOk(Boolean.FALSE);
                    return false;
                }
            }
            report.setIsValidationStatusOk(Boolean.TRUE);
            return true;
    	}catch(Exception e) {
    		report.getReport().append(false);
    		throw new RuntimeException(e.getMessage(),e);
    	}

        
    }
    
    public List<IDocumentValitaionHandler> add(IDocumentValitaionHandler handler) {
    	this.handlers.add(handler);
    	return handlers;
    }
}
