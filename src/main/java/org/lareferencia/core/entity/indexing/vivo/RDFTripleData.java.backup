package org.lareferencia.core.entity.indexing.vivo;

import java.util.Objects;

/**
 * Clase simple para representar una triple RDF sin usar objetos Jena.
 * Se usa durante el procesamiento y solo se convierte a Statement al momento de persistir.
 */
public class RDFTripleData {
    
    public enum ObjectType {
        URI_RESOURCE,
        LITERAL
    }
    
    private final String subjectUri;
    private final String predicateUri;
    private final String objectValue;
    private final ObjectType objectType;
    private final String objectDatatype; // Para literales con tipo específico
    private final String objectLanguage; // Para literales con idioma
    private final String tripleHash; // Hash precalculado para deduplicación eficiente
    
    // Constructor privado principal
    private RDFTripleData(String subjectUri, String predicateUri, String objectValue, 
                         ObjectType objectType, String datatype, String language) {
        this.subjectUri = subjectUri;
        this.predicateUri = predicateUri;
        this.objectValue = objectValue;
        this.objectType = objectType;
        this.objectDatatype = datatype;
        this.objectLanguage = language;
        this.tripleHash = calculateHash();
    }
    
    // Factory method para object properties (objeto es URI)
    public static RDFTripleData createObjectProperty(String subjectUri, String predicateUri, String objectUri) {
        return new RDFTripleData(subjectUri, predicateUri, objectUri, ObjectType.URI_RESOURCE, null, null);
    }
    
    // Factory method para data properties con tipo y lenguaje específicos
    public static RDFTripleData createDataProperty(String subjectUri, String predicateUri, String literalValue, 
                                                  String datatype, String language) {
        return new RDFTripleData(subjectUri, predicateUri, literalValue, ObjectType.LITERAL, datatype, language);
    }
    
    // Factory method simplificado para literales sin tipo ni idioma
    public static RDFTripleData createLiteral(String subjectUri, String predicateUri, String literalValue) {
        return new RDFTripleData(subjectUri, predicateUri, literalValue, ObjectType.LITERAL, null, null);
    }
    
    private String calculateHash() {
        // Validar que los campos requeridos no sean null
        if (subjectUri == null || predicateUri == null || objectValue == null) {
            throw new IllegalArgumentException("Subject URI, Predicate URI, and Object Value cannot be null");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(subjectUri).append("|");
        sb.append(predicateUri).append("|");
        
        if (objectType == ObjectType.URI_RESOURCE) {
            sb.append("URI:").append(objectValue);
        } else {
            sb.append("LITERAL:").append(objectValue);
            if (objectDatatype != null) {
                sb.append("^^").append(objectDatatype);
            }
            if (objectLanguage != null && !objectLanguage.isEmpty()) {
                sb.append("@").append(objectLanguage);
            }
        }
        
        return sb.toString();
    }
    
    // Getters
    public String getSubjectUri() { return subjectUri; }
    public String getPredicateUri() { return predicateUri; }
    public String getObjectValue() { return objectValue; }
    public ObjectType getObjectType() { return objectType; }
    public String getObjectDatatype() { return objectDatatype; }
    public String getObjectLanguage() { return objectLanguage; }
    public String getTripleHash() { return tripleHash; }
    
    public boolean isObjectProperty() {
        return objectType == ObjectType.URI_RESOURCE;
    }
    
    public boolean isDataProperty() {
        return objectType == ObjectType.LITERAL;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RDFTripleData)) return false;
        RDFTripleData that = (RDFTripleData) o;
        return Objects.equals(tripleHash, that.tripleHash);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(tripleHash);
    }
    
    @Override
    public String toString() {
        return String.format("RDFTripleData{%s %s %s%s}", 
                subjectUri, 
                predicateUri, 
                objectValue,
                objectType == ObjectType.LITERAL && objectDatatype != null ? 
                    "^^" + objectDatatype : "");
    }
}
           