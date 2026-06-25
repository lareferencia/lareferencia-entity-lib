/*
 *   Copyright (c) 2013-2026. LA Referencia / Red CLARA and others
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

package org.lareferencia.core.entity.indexing.nested.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Setter;

@XmlRootElement(name = "analyzer")
@Setter
public class AnalyzerConfig {

    private String name;
    private String type;
    private String tokenizer;
    private List<String> filters = new ArrayList<String>();
    private List<String> charFilters = new ArrayList<String>();
    private Map<QName, String> qnameParams = new HashMap<QName, String>();

    @XmlAttribute(name = "name", required = true)
    public String getName() {
        return name;
    }

    @XmlAttribute(name = "type")
    public String getType() {
        return type;
    }

    @XmlAttribute(name = "tokenizer")
    public String getTokenizer() {
        return tokenizer;
    }

    @XmlElement(name = "filter")
    public List<String> getFilters() {
        return filters;
    }

    @XmlElement(name = "char-filter")
    public List<String> getCharFilters() {
        return charFilters;
    }

    @XmlAnyAttribute
    public Map<QName, String> getQnameParams() {
        return qnameParams;
    }

    public Map<String, String> getParams() {
        Map<String, String> params = new HashMap<String, String>();

        for (QName qname : qnameParams.keySet()) {
            params.put(qname.getLocalPart(), qnameParams.get(qname));
        }

        return params;
    }
}
