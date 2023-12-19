package org.lareferencia.core.entity.domain;

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor
public class ProvenanceId implements Serializable {

    @Getter
	private String source;
	
	@Getter
	private String record;

    // default constructor

    public ProvenanceId(String source, String record) {
        this.source = source;
        this.record = record;
    }

} 
    
