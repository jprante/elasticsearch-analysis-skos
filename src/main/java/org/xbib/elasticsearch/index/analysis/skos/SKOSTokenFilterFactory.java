package org.xbib.elasticsearch.index.analysis.skos;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.AnalysisSettingsRequired;

import org.elasticsearch.index.settings.IndexSettingsService;
import org.xbib.elasticsearch.index.analysis.skos.SKOSAnalyzer.ExpansionType;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngine;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngineFactory;
import org.xbib.elasticsearch.index.analysis.skos.SKOSTypeAttribute.SKOSType;

@AnalysisSettingsRequired
public class SKOSTokenFilterFactory extends AbstractTokenFilterFactory {

    private final Injector injector;

    private final Settings settings;

    @Inject
    public SKOSTokenFilterFactory(Index index,
                                  IndexSettingsService indexSettingsService,
                                  @Assisted String name,
                                  @Assisted Settings settings,
                                  Injector injector) {
        super(index, indexSettingsService.indexSettings(), name, settings);
        this.injector = injector;
        this.settings = settings;
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        Client client = injector.getInstance(Client.class);
        ExpansionType expansionType;
        SKOSEngine skosEngine;
        int bufferSize;
        String skosFile = settings.get("skosFile");
        if (skosFile == null) {
            throw new IllegalArgumentException("mandatory parameter 'skosFile' missing");
        }
        String expansionTypeString = settings.get("expansionType");
        if (expansionTypeString == null) {
            throw new IllegalArgumentException("mandatory parameter 'expansionType' missing");
        }
        if (skosFile.endsWith(".n3") || skosFile.endsWith(".rdf") || skosFile.endsWith(".ttl") || skosFile.endsWith(".zip")) {
            try {
                String indexName = settings.get("indexName", "skos");
                String languageString = settings.get("language");
                skosEngine = SKOSEngineFactory.getSKOSEngine(client, indexName, skosFile,
                        languageString != null ? Arrays.asList(languageString.split(" ")) : Collections.<String>emptyList());
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                throw new IllegalArgumentException("could not instantiate SKOS engine", e);
            }
        } else {
            throw new IllegalArgumentException("allowed file suffixes are: .n3 (N3), .rdf (RDF/XML), .ttl (Turtle) and .zip (zip)");
        }
        if (expansionTypeString.equalsIgnoreCase(ExpansionType.LABEL.toString())) {
            expansionType = ExpansionType.LABEL;
        } else {
            expansionType = ExpansionType.URI;
        }
        String bufferSizeString = settings.get("bufferSize");
        if (bufferSizeString != null) {
            bufferSize = Integer.parseInt(bufferSizeString);
            if (bufferSize < 1) {
                throw new IllegalArgumentException("'bufferSize' must be a positive (small) integer");
            }
        } else {
            bufferSize = 4;
        }
        List<SKOSType> types = new LinkedList<>();
        String typeString = settings.get("skosType");
        if (typeString != null) {
            for (String s : typeString.split(" ")) {
                try {
                    SKOSType st = SKOSType.valueOf(s.toUpperCase());
                    if (st != null) {
                        types.add(st);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("'skosType' must be one of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED");
                }
            }
        }
        if (expansionType.equals(ExpansionType.LABEL)) {
            return new SKOSLabelFilter(tokenStream, skosEngine, new StandardAnalyzer(), bufferSize, types);
        } else {
            return new SKOSURIFilter(tokenStream, skosEngine, new StandardAnalyzer(), types);
        }
    }
}
