package org.lareferencia.core.entity.xml.validation;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
@Qualifier("syntaxValidationHandler")
public class SyntaxValidationHandler extends DocumentValitaionHandler{

    protected boolean doValidation(Document document){
    	
    	try {
    		String xsdPath = "entity_data_folder/entity-relation-metamodel.xsd";
    		validateXMLSyntax(document,xsdPath);
            System.out.println("Validation successful!");
            
            boolean isValid = true;
            if (!isValid) {
                return false;
            }
            return true;
    	}catch(Exception e) {
    		throw new RuntimeException(e.getMessage(),e);
    	}

    }
    
    private void validateXMLSyntax(Document document, String xsdPath) throws Exception {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Source schemaSource = new StreamSource(SyntaxValidationHandler.class.getResourceAsStream(xsdPath));
        Schema schema = schemaFactory.newSchema(schemaSource);
        Validator validator = schema.newValidator();
        validator.validate(new DOMSource(document));
    }
	
}
