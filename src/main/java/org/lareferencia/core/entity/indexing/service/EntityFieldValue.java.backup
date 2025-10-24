
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

package org.lareferencia.core.entity.indexing.service;

import org.lareferencia.core.entity.domain.EntityRelationException;
import org.lareferencia.core.entity.domain.FieldOccurrence;

/**
 * Field Value contained in a group of fields of an iEntity
 * 
 * @author pgraca
 *
 */
public class EntityFieldValue {

    private String value;
    private LangFieldType lang = LangFieldType.UND;

    public enum LangFieldType {

        POR("pt", "por"), ENG("en", "eng"), SPA("es", "spa"), FRA("fr", "fra"), UND("un", "und");

        public final String lang_639_1;
        public final String lang_639_3;

        private LangFieldType(String lang_639_1, String lang_639_3) {
            this.lang_639_1 = lang_639_1;
            this.lang_639_3 = lang_639_3;
        }

        public static LangFieldType valueOfLang(String lang) {

            for (LangFieldType e : values()) {
                if (e.lang_639_1.equals(lang) || e.lang_639_3.equals(lang)) {
                    return e;
                }
            }
            return UND;
        }

    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setLanguage(LangFieldType lang) {
        this.lang = lang;
    }

    public LangFieldType getLanguage() {
        return lang;
    }

    /**
     * Builder class for an entityfieldvalue from occurences
     * 
     * @author pgraca
     *
     */
    public static class Builder {
        EntityFieldValue entityFieldvalue;

        /**
         * Build FieldValue from a String single value
         * 
         * @param value
         * @return
         */
        public Builder fromValue(String value) {
            entityFieldvalue = new EntityFieldValue();
            entityFieldvalue.setValue(value);
            return this;
        }

        /**
         * Build FieldValue from a String single value and a language
         * 
         * @param value
         * @param lang
         * @return
         */
        public Builder fromValueLang(String value, LangFieldType lang) {
            entityFieldvalue = new EntityFieldValue();
            entityFieldvalue.setValue(value);
            entityFieldvalue.setLanguage(lang);
            return this;
        }

        /**
         * Build FieldValue from a field occurrences
         * 
         * @param occur
         * @return
         * @throws EntityRelationException
         */
        public Builder fromFieldOccurrence(FieldOccurrence occur) throws EntityRelationException {
            entityFieldvalue = new EntityFieldValue();
            // support language mapping like pt_PT -> por, etc..
            entityFieldvalue.setLanguage(parseLang(occur.getLang()));
            entityFieldvalue.setValue(occur.getValue());

            return this;
        }

        /**
         * Build FieldValue from a complex field occurrences (with multiple field
         * levels)
         * 
         * @param occur
         * @param subField
         * @return
         * @throws EntityRelationException
         */
        public Builder fromComplexFieldOccurrence(FieldOccurrence occur, String subField)
                throws EntityRelationException {
            entityFieldvalue = new EntityFieldValue();
            // support language mapping like pt_PT -> por, etc..
            entityFieldvalue.setLanguage(parseLang(occur.getLang()));

            entityFieldvalue.setValue(occur.getValue(subField));

            return this;
        }

        public EntityFieldValue build() {
            return this.entityFieldvalue;
        }

        private static LangFieldType parseLang(String lang) {

            if (lang == null) {
                return LangFieldType.UND;
            }
            // get the first part of pt_PT or pt-PT splitting by - or _
            return LangFieldType.valueOfLang(lang.toLowerCase().split("_|-")[0]);
        }
    }

}
