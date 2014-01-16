
package org.xbib.elasticsearch.index.analysis;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.AnalysisSettingsRequired;
import org.elasticsearch.index.settings.IndexSettings;

import org.xbib.elasticsearch.index.analysis.skos.SKOSAnalyzer.ExpansionType;
import org.xbib.elasticsearch.index.analysis.skos.SKOSLabelFilter;
import org.xbib.elasticsearch.index.analysis.skos.SKOSURIFilter;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngine;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngineFactory;
import org.xbib.elasticsearch.index.analysis.skos.tokenattributes.SKOSTypeAttribute.SKOSType;

@AnalysisSettingsRequired
public class SKOSTokenFilterFactory extends AbstractTokenFilterFactory {

    private final ExpansionType expansionType;

    private final SKOSEngine skosEngine;

    private final SKOSType[] type;

    private final int bufferSize;

    @Inject
    public SKOSTokenFilterFactory(Index index, @IndexSettings Settings indexSettings,
            @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);
        String skosFile = settings.get("skosFile");
        String expansionTypeString = settings.get("expansionType");
        String bufferSizeString = settings.get("bufferSize");
        String languageString = settings.get("language");
        String typeString = settings.get("skosType");
        if (skosFile == null) {
            throw new ElasticsearchIllegalArgumentException("Mandatory parameter 'skosFile' missing");
        }
        if (expansionTypeString == null) {
            throw new ElasticsearchIllegalArgumentException("Mandatory parameter 'expansionType' missing");
        }
        if (skosFile.endsWith(".n3") || skosFile.endsWith(".rdf") || skosFile.endsWith(".ttl") || skosFile.endsWith(".zip")) {
            try {
                skosEngine = SKOSEngineFactory.getSKOSEngine(Version.LUCENE_45,
                        settings.get("path", ""), skosFile,
                        languageString != null ? languageString.split(" ") : null);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new ElasticsearchIllegalArgumentException("could not instantiate SKOS engine", e);
            }
        } else {
            throw new ElasticsearchIllegalArgumentException("Allowed file suffixes are: .n3 (N3), .rdf (RDF/XML), .ttl (Turtle) and .zip (zip)");
        }
        if (expansionTypeString.equalsIgnoreCase(ExpansionType.LABEL.toString())) {
            expansionType = ExpansionType.LABEL;
        } else {
            expansionType = ExpansionType.URI;
        }
        if (bufferSizeString != null) {
            bufferSize = Integer.parseInt(bufferSizeString);
            if (bufferSize < 1) {
                throw new ElasticsearchIllegalArgumentException("The property 'bufferSize' must be a positive (small) integer");
            }
        } else {
            bufferSize = 4;
        }
        List<SKOSType> types = new LinkedList<SKOSType>();
        if (typeString != null) {
            for (String s : typeString.split(" ")) {
                try {
                    SKOSType st = SKOSType.valueOf(s.toUpperCase());
                    if (st != null) {
                        types.add(st);
                    }
                } catch (IllegalArgumentException e) {
                    throw new ElasticsearchIllegalArgumentException("The property 'skosType' must be one of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED");
                }
            }
        }
        type = types.toArray(new SKOSType[types.size()]);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        if (expansionType.equals(ExpansionType.LABEL)) {
            return new SKOSLabelFilter(tokenStream, skosEngine, new StandardAnalyzer(Version.LUCENE_45), bufferSize, type);
        } else {
            return new SKOSURIFilter(tokenStream, skosEngine, new StandardAnalyzer(Version.LUCENE_45), type);
        }
    }
}
