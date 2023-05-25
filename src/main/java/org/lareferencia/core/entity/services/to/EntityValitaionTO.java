package org.lareferencia.core.entity.services.to;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EntityValitaionTO {

	private String file;
	private String status;
	private String details;
	
}
