/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.analysis;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;
import org.elasticsearch.ElasticSearchIllegalArgumentException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.skos.SKOSAnalyzer.ExpansionType;
import org.elasticsearch.index.analysis.skos.SKOSLabelFilter;
import org.elasticsearch.index.analysis.skos.SKOSURIFilter;
import org.elasticsearch.index.analysis.skos.engine.SKOSEngine;
import org.elasticsearch.index.analysis.skos.engine.SKOSEngineFactory;
import org.elasticsearch.index.analysis.skos.tokenattributes.SKOSTypeAttribute.SKOSType;
import org.elasticsearch.index.settings.IndexSettings;

@AnalysisSettingsRequired
public class SKOSTokenFilterFactory extends AbstractTokenFilterFactory {

    private final ExpansionType expansionType;
    private final SKOSEngine skosEngine;
    private final SKOSType[] type;
    private final int bufferSize;
    private final String skosFile;
    private final String expansionTypeString;
    private final String bufferSizeString;
    private final String languageString;
    private final String typeString;

    @Inject
    public SKOSTokenFilterFactory(Index index, @IndexSettings Settings indexSettings,
            @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);
        skosFile = settings.get("skosFile");
        expansionTypeString = settings.get("expansionType");
        bufferSizeString = settings.get("bufferSize");
        languageString = settings.get("language");
        typeString = settings.get("skosType");
        if (skosFile == null) {
            throw new ElasticSearchIllegalArgumentException("Mandatory parameter 'skosFile' missing");
        }
        if (expansionTypeString == null) {
            throw new ElasticSearchIllegalArgumentException("Mandatory parameter 'expansionType' missing");
        }
        if (skosFile.endsWith(".n3") || skosFile.endsWith(".rdf") || skosFile.endsWith(".ttl") || skosFile.endsWith(".zip")) {
            try {
                skosEngine = SKOSEngineFactory.getSKOSEngine(Version.LUCENE_36,
                        settings.get("path", ""), skosFile,
                        languageString != null ? languageString.split(" ") : null);
            } catch (IOException e) {
                throw new ElasticSearchIllegalArgumentException("could not instantiate SKOS engine", e);
            }
        } else {
            throw new ElasticSearchIllegalArgumentException("Allowed file suffixes are: .n3 (N3), .rdf (RDF/XML), .ttl (Turtle) and .zip (zip)");
        }
        if (expansionTypeString.equalsIgnoreCase(ExpansionType.LABEL.toString())) {
            expansionType = ExpansionType.LABEL;
        } else {
            expansionType = ExpansionType.URI;
        }
        if (bufferSizeString != null) {
            bufferSize = Integer.parseInt(bufferSizeString);
            if (bufferSize < 1) {
                throw new ElasticSearchIllegalArgumentException("The property 'bufferSize' must be a positive (small) integer");
            }
        } else {
            bufferSize = 4;
        }
        List<SKOSType> types = new LinkedList();
        if (typeString != null) {
            for (String s : typeString.split(" ")) {
                try {
                    SKOSType st = SKOSType.valueOf(s.toUpperCase());
                    if (st != null) {
                        types.add(st);
                    }
                } catch (IllegalArgumentException e) {
                    throw new ElasticSearchIllegalArgumentException("The property 'skosType' must be one of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED");
                }
            }
        }
        type = types.toArray(new SKOSType[types.size()]);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        if (expansionType.equals(ExpansionType.LABEL)) {
            return new SKOSLabelFilter(tokenStream, skosEngine, new StandardAnalyzer(Version.LUCENE_36), bufferSize, type);
        } else {
            return new SKOSURIFilter(tokenStream, skosEngine, new StandardAnalyzer(Version.LUCENE_36), type);
        }
    }
}
