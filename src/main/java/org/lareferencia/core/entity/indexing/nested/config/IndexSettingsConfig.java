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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Setter;

@XmlRootElement(name = "index-settings")
@Setter
public class IndexSettingsConfig {

    private Integer numberOfShards;
    private Integer numberOfReplicas;
    private AnalysisConfig analysis;

    @XmlAttribute(name = "number-of-shards")
    public Integer getNumberOfShards() {
        return numberOfShards;
    }

    @XmlAttribute(name = "number-of-replicas")
    public Integer getNumberOfReplicas() {
        return numberOfReplicas;
    }

    @XmlElement(name = "analysis")
    public AnalysisConfig getAnalysis() {
        return analysis;
    }

    public Boolean hasSettings() {
        return numberOfShards != null
                || numberOfReplicas != null
                || (analysis != null && analysis.hasAnalyzers());
    }
}
