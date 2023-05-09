package org.lareferencia.core.entity.xml.validation;



import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

public abstract class DocumentValitaionChain implements IDocumentValitaionHandler {

	@Autowired(required = false)
	private IDocumentValitaionHandler next;

	public IDocumentValitaionHandler add(IDocumentValitaionHandler next) {
		this.next = next;
		return next;
	}

	public boolean validate(Document document) {
		boolean isValid = doValidation(document);

		if (!isValid) {
			return false;
		}

		if (next != null) {
			return next.validate(document);
		}

		return true;
	}

	protected abstract boolean doValidation(Document document);
}
