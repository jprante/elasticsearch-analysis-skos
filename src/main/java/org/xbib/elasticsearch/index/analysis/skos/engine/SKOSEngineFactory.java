/**
 * Copyright 2010 Bernhard Haslhofer
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.xbib.elasticsearch.index.analysis.skos.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.client.Client;
import org.xbib.elasticsearch.index.analysis.skos.engine.jena.SKOSEngineImpl;

/**
 * This factory instantiates the various kinds of SKOSEngine implementations
 */
public class SKOSEngineFactory {

    private final static Map<String, SKOSEngine> cache = new HashMap<>();

    /**
     * Sets up a SKOS Engine from a given InputStream. The inputstream must
     * deliver data in a valid RDF serialization format.
     *
     * @param client the Elasticsearch client
     * @param indexName the index name
     * @param inputStream the input stream
     * @param lang the serialization format (N3, RDF/XML, TURTLE)
     * @return a new SKOSEngine instance
     * @throws IOException if SKOS engine can not be instantiated
     */
    public static SKOSEngine getSKOSEngine(Client client, String indexName, InputStream inputStream, String lang) throws IOException {
        if (cache.containsKey(indexName)) {
            return cache.get(indexName);
        }
        SKOSEngine skosEngine = new SKOSEngineImpl(client, indexName, inputStream, lang);
        cache.put(indexName, skosEngine);
        return skosEngine;
    }

    /**
     * Sets up a SKOS Engine from a given rdf file (serialized in any RDF
     * serialization format) and considers only those concept labels that are
     * defined in the language parameter
     *
     * @param client the Elasticsearch client
     * @param indexName the index name
     * @param filenameOrURI the skos file
     * @param languages the languages to be considered
     * @return SKOSEngine
     * @throws IOException if SKOS engine can not be instantiated
     */
    public static SKOSEngine getSKOSEngine(Client client, String indexName, String filenameOrURI, List<String> languages) throws IOException {
        if (cache.containsKey(indexName)) {
            return cache.get(indexName);
        }
        SKOSEngine skosEngine = new SKOSEngineImpl(client, indexName, filenameOrURI, languages);
        cache.put(indexName, skosEngine);
        return skosEngine;
    }


    /**
     * Sets up a SKOS Engine from a given InputStream. The inputstream must
     * deliver data in a valid RDF serialization format.
     *
     * @param client the Elasticsearch client
     * @param inputStream the input stream
     * @param indexName the index name
     * @param format the serialization format (N3, RDF/XML, TURTLE)
     * @param languages the languages to be considered
     * @return a new SKOSEngine instance
     * @throws IOException if SKOS engine can not be instantiated
     */
    public static SKOSEngine getSKOSEngine(Client client, String indexName, InputStream inputStream, String format, List<String> languages) throws IOException {
        if (cache.containsKey(indexName)) {
            return cache.get(indexName);
        }
        SKOSEngine skosEngine = new SKOSEngineImpl(client, indexName, inputStream, format, languages);
        cache.put(indexName, skosEngine);
        return skosEngine;
    }
}