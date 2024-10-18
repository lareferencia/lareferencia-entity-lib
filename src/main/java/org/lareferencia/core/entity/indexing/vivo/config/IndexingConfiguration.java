/*
 *   Copyright (c) 2013-2022. LA Referencia / Red CLARA and others
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   This file is part of LA Referencia software platform LRHarvester v4.x
 *   For any further information please contact Lautaro Matas <lmatas@gmail.com>
 */

package org.lareferencia.core.entity.indexing.vivo.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.commons.io.IOUtils;

@XmlRootElement(name = "entity-mapping-config")
public class IndexingConfiguration {
	
	private List<OutputConfig> outputs = new ArrayList<OutputConfig>();
	private List<NamespaceConfig> namespaces = new ArrayList<NamespaceConfig>();
	private List<EntityIndexingConfig> sourceEntities = new ArrayList<EntityIndexingConfig>();
	
	public IndexingConfiguration() {
	}
	
	@XmlElementWrapper(name="outputs")
	@XmlElement(name = "output")
	public List<OutputConfig> getOutputs() {
		return outputs;
	}

	public void setOutputs(List<OutputConfig> outputs) {
		this.outputs = new ArrayList<OutputConfig>();
		for (OutputConfig output : outputs) {
			this.outputs.add(output);
		}
	}
	
	@XmlElementWrapper(name="namespaces")
	@XmlElement(name = "namespace")
	public List<NamespaceConfig> getNamespaces() {
		return namespaces;
	}

	public void setNamespaces(List<NamespaceConfig> namespaces) {
		this.namespaces = new ArrayList<NamespaceConfig>();
		for (NamespaceConfig namespace : namespaces) {
			this.namespaces.add(namespace);
		}
	}
	
	@XmlElementWrapper(name="source-entities")
	@XmlElement(name = "source-entity")
	public List<EntityIndexingConfig> getSourceEntities() {
		return sourceEntities;
	}

	public void setSourceEntities(List<EntityIndexingConfig> sourceEntities) {
		this.sourceEntities = new ArrayList<EntityIndexingConfig>();
		for (EntityIndexingConfig entity : sourceEntities) {
			this.sourceEntities.add(entity);
		}
	}

	public static IndexingConfiguration loadFromXml(String filename) throws Exception {
		
		FileInputStream fisTargetFile = new FileInputStream(new File(filename));

		String xml = IOUtils.toString(fisTargetFile, "UTF-8");
		
		IndexingConfiguration config = new IndexingConfiguration();

		JAXBContext context = JAXBContext.newInstance(config.getClass());
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

		StringReader stringReader = new StringReader(xml);
		Unmarshaller unmarshaller = context.createUnmarshaller();

		config = (IndexingConfiguration) unmarshaller.unmarshal(stringReader);		
		return config;
	}
	
}
