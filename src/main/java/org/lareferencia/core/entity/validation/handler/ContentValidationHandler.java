package org.lareferencia.core.entity.validation.handler;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
@Qualifier("contentValidationHandler")
public class ContentValidationHandler extends DocumentValitaionHandler{

    protected boolean doValidation(Document document) {
        boolean isValid = true;
        if (!isValid) {
            return false;
        }
        return true;
    }
	
}
