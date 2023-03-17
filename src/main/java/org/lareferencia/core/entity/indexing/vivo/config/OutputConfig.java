
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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import lombok.Setter;

@XmlRootElement
@Setter
public class OutputConfig {
	
	String type;
	String path;
	String reset;
	String name;
	String format;
	String url;
	String user;
	String password;
	String graph;
	
	@XmlAttribute(name="type", required = true)
	public String getType() {
		return type;
	}
		
	@XmlAttribute(name="path", required = false)
	public String getPath() {
		return path;
	}
	
	@XmlAttribute(name="name", required = false)
	public String getName() {
		return name;
	}
	
	@XmlAttribute(name="format", required = false)
	public String getFormat() {
		return format;
	}
	
	@XmlAttribute(name="url", required = false)
	public String getUrl() {
		return url;
	}
	
	@XmlAttribute(name="user", required = false)
	public String getUser() {
		return user;
	}
	
	@XmlAttribute(name="password", required = false)
	public String getPassword() {
		return password;
	}
	
	@XmlAttribute(name="graph", required = false)
	public String getGraph() {
		return graph;
	}
	
	@XmlAttribute(name="reset", required = false)
	public String getReset() {
		return reset;
	}
}
