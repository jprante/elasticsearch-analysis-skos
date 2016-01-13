package org.xbib.elasticsearch.index.analysis.skos;

import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.settings.IndexSettingsService;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngine;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngineFactory;
import org.xbib.elasticsearch.index.analysis.skos.SKOSTypeAttribute.SKOSType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SKOSAnalyzerProvider extends AbstractIndexAnalyzerProvider<SKOSAnalyzer> {

    private final Injector injector;

    private final Settings settings;

    @Inject
    public SKOSAnalyzerProvider(Index index,
                                IndexSettingsService indexSettingsService,
                                @Assisted String name,
                                @Assisted Settings settings,
                                Injector injector) {
        super(index, indexSettingsService.indexSettings(), name, settings);
        this.injector = injector;
        this.settings = settings;
    }

    @Override
    public SKOSAnalyzer get() {
        CharArraySet stopwords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
        Client client = injector.getInstance(Client.class);
        String skosFile = settings.get("skosFile");
        //if (skosFile == null) {
        //    throw new IllegalArgumentException("Mandatory parameter 'skosFile' missing");
        //}
        String expansionTypeString = settings.get("expansionType");
        //if (expansionTypeString == null) {
        //    throw new IllegalArgumentException("Mandatory parameter 'expansionType' missing");
        //}
        String typeString = settings.get("skosType");
        SKOSAnalyzer.ExpansionType expansionType;
        if (SKOSAnalyzer.ExpansionType.LABEL.toString().equalsIgnoreCase(expansionTypeString)) {
            expansionType = SKOSAnalyzer.ExpansionType.LABEL;
        } else {
            expansionType = SKOSAnalyzer.ExpansionType.URI;
        }

        String bufferSizeString = settings.get("bufferSize");
        int bufferSize;
        if (bufferSizeString != null) {
            bufferSize = Integer.parseInt(bufferSizeString);
            if (bufferSize < 1) {
                throw new IllegalArgumentException("The property 'bufferSize' must be a positive (small) integer");
            }
        } else {
            bufferSize = 4;
        }
        List<SKOSType> types = new LinkedList<>();
        if (typeString != null) {
            for (String s : typeString.split(" ")) {
                try {
                    SKOSType st = SKOSType.valueOf(s.toUpperCase());
                    if (st != null) {
                        types.add(st);
                    }
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("The property 'skosType' must be one of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED");
                }
            }
        }
        String languageString = settings.get("language");
        SKOSEngine skosEngine = null;
        if (skosFile != null) {
            if (skosFile.endsWith(".n3") || skosFile.endsWith(".rdf") || skosFile.endsWith(".ttl") || skosFile.endsWith(".zip")) {
                try {
                    skosEngine = SKOSEngineFactory.getSKOSEngine(client, settings.get("path", ""), skosFile,
                            languageString != null ? Arrays.asList(languageString.split(" ")) : Collections.<String>emptyList());
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new IllegalArgumentException("could not instantiate SKOS engine", e);
                }
            } else {
                throw new IllegalArgumentException("Allowed file suffixes are: .n3 (N3), .rdf (RDF/XML), .ttl (Turtle) and .zip (zip)");
            }
        }
        return new SKOSAnalyzer(stopwords, skosEngine, expansionType, bufferSize, types);
    }

}
