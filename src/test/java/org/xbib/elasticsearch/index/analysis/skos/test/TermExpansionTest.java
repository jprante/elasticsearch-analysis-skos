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
package org.xbib.elasticsearch.index.analysis.skos.test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;

import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexAction;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequestBuilder;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.After;
import org.junit.Test;
import org.xbib.elasticsearch.NodeTestUtils;
import org.xbib.elasticsearch.index.analysis.skos.SKOSAnalyzer;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngine;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngineFactory;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class TermExpansionTest extends NodeTestUtils {

    /*protected IndexSearcher searcher;

    protected IndexWriter writer;

    @After
    public void tearDown() throws Exception {
        if (writer != null) {
            writer.close();
        }
        if (searcher != null && searcher.getIndexReader() != null) {
            searcher.getIndexReader().close();
        }
    }*/

    /**
     * This test indexes a sample metadata record (=lucene document) having a
     * "title", "description", and "subject" field, which contains plain subject
     * terms.
     *
     * A search for "arms" doesn't return that record because the term "arms" is
     * not explicitly contained in the record (document).
     *
     * @throws IOException
     */

    /*public void noExpansion() throws IOException {

        // defining the document to be indexed
        Document doc = new Document();
        doc.add(new Field("title", "Spearhead", TextField.TYPE_STORED));
        doc.add(new Field("description",
                "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft..."
                + "The spear was mainly a thrusting weapon, but could also be thrown. "
                + "It was the principal weapon of the auxiliary soldier... "
                + "(second - fourth century, Arbeia Roman Fort).",
                TextField.TYPE_NOT_STORED));
        doc.add(new Field("subject", "weapons",
                TextField.TYPE_NOT_STORED));

        // setting up a writer with a default (simple) analyzer
        writer = new IndexWriter(new RAMDirectory(), new IndexWriterConfig(new SimpleAnalyzer()));

        // adding the document to the index
        writer.addDocument(doc);

        // defining a query that searches over all fields
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("title", "arms")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("description", "arms")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("subject", "arms")), BooleanClause.Occur.SHOULD);

        // creating a new searcher
        searcher = new IndexSearcher(DirectoryReader.open(writer, false));

        TopDocs results = searcher.search(builder.build(), 10);

        // no results are returned since there is no term match
        assertEquals(0, results.totalHits);
    }*/

    @Test
    public void noExpand() throws Exception {
        logger.info("noExpand start");
        XContentBuilder builder = jsonBuilder();
        builder.startObject()
                .field("title", "Spearhead")
                .field("description",
                        "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft..."
                                + "The spear was mainly a thrusting weapon, but could also be thrown. "
                                + "It was the principal weapon of the auxiliary soldier... "
                                + "(second - fourth century, Arbeia Roman Fort).")
                .field("subject", "weapons")
                .endObject();
        IndexRequestBuilder indexRequestBuilder = new IndexRequestBuilder(client("1"), IndexAction.INSTANCE);
        indexRequestBuilder.setIndex("demo").setType("docs").setId("1")
                .setSource(builder).setRefresh(true).execute().actionGet();
        QueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .should(termQuery("title", "arms"))
                .should(termQuery("description", "arms"))
                .should(termQuery("subject", "arms"));
        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client("1"), SearchAction.INSTANCE);
        SearchResponse searchResponse = searchRequestBuilder.setIndices("demo").setTypes("docs")
                .setQuery(queryBuilder)
                .execute().actionGet();
        assertEquals(0, searchResponse.getHits().getTotalHits());
        logger.info("noExpand successfull");
    }

    /**
     * This test indexes a sample metadata record (=lucene document) having a
     * "title", "description", and "subject" field.
     *
     * A search for "arms" returns that record as a result because "arms" is
     * defined as an alternative label for "weapons", the term which is
     * contained in the subject field.
     *
     * @throws IOException
     */

   /* public void labelBasedTermExpansion() throws IOException {
        // defining the document to be indexed
        Document doc = new Document();
        doc.add(new Field("title", "Spearhead", TextField.TYPE_STORED));
        doc.add(new Field("description",
                "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft..."
                        + "The spear was mainly a thrusting weapon, but could also be thrown. "
                        + "It was the principal weapon of the auxiliary soldier... "
                        + "(second - fourth century, Arbeia Roman Fort).",
                TextField.TYPE_NOT_STORED));
        doc.add(new Field("subject", "weapons", TextField.TYPE_NOT_STORED));

        String skosFile = "src/test/resources/skos_samples/ukat_examples.n3";
        String indexName = "test";

        SKOSEngine skosEngine = SKOSEngineFactory.getSKOSEngine(client("1"), indexName, skosFile);
        Analyzer skosAnalyzer = new SKOSAnalyzer(skosEngine, SKOSAnalyzer.ExpansionType.LABEL);

        // define different analyzers for different fields
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put("subject", skosAnalyzer);
        PerFieldAnalyzerWrapper indexAnalyzer = new PerFieldAnalyzerWrapper(new SimpleAnalyzer(), analyzerPerField);

        // setting up a writer with a default (simple) analyzer
        writer = new IndexWriter(new RAMDirectory(), new IndexWriterConfig(indexAnalyzer));

        // adding the document to the index
        writer.addDocument(doc);

        // defining a query that searches over all fields
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("title", "arms")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("description", "arms")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("subject", "arms")), BooleanClause.Occur.SHOULD);

        // creating a new searcher
        searcher = new IndexSearcher(DirectoryReader.open(writer, false));

        TopDocs results = searcher.search(builder.build(), 10);

        // the document matches because "arms" is among the expanded terms
        assertEquals(1, results.totalHits);

        // defining a query that searches for a broader concept
        Query query = new TermQuery(new Term("subject", "military equipment"));
        results = searcher.search(query, 10);
        // ... also returns the document as result
        assertEquals(1, results.totalHits);
    }*/

    private final static ESLogger logger = ESLoggerFactory.getLogger(TermExpansionTest.class.getName());

    @Test
    public void labelTermExpansion() throws Exception {
        CreateIndexRequestBuilder createIndexRequestBuilder = new CreateIndexRequestBuilder(client("1"), CreateIndexAction.INSTANCE, "demo");
        Settings settings = Settings.builder()
                .put("index.analysis.filter.skosfilter.type", "skos")
                .put("index.analysis.filter.skosfilter.path", "ukat")
                .put("index.analysis.filter.skosfilter.skosFile", "src/test/resources/skos_samples/ukat_examples.n3")
                .put("index.analysis.filter.skosfilter.expansionType", "LABEL")
                .put("index.analysis.analyzer.skos.type", "custom")
                .put("index.analysis.analyzer.skos.tokenizer", "keyword")
                .put("index.analysis.analyzer.skos.filter", "skosfilter")
                .build();

        XContentBuilder mapping = jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("subject")
                .field("type", "string")
                .field("analyzer", "skos")
                .field("search_analyzer", "standard")
                .endObject()
                .endObject()
                .endObject();

        createIndexRequestBuilder.setSettings(settings)
                .addMapping("_default_", mapping)
                .execute().actionGet();

        logger.info("create index done");

        XContentBuilder builder = jsonBuilder();
        builder.startObject()
                .field("title", "Spearhead")
                .field("description",
                        "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft..."
                                + "The spear was mainly a thrusting weapon, but could also be thrown. "
                                + "It was the principal weapon of the auxiliary soldier... "
                                + "(second - fourth century, Arbeia Roman Fort).")
                .field("subject", "weapons")
                .endObject();
        IndexRequestBuilder indexRequestBuilder = new IndexRequestBuilder(client("1"), IndexAction.INSTANCE);
        indexRequestBuilder.setIndex("demo").setType("docs").setId("1")
                .setSource(builder).setRefresh(true).execute().actionGet();

        // search for all docs
        QueryBuilder matchAll = matchAllQuery();
        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client("1"), SearchAction.INSTANCE);
        SearchResponse searchResponse = searchRequestBuilder.setIndices("demo").setTypes("docs")
                .setQuery(matchAll)
                .execute().actionGet();
        assertEquals(1, searchResponse.getHits().getTotalHits());

        // search for "arms"
        QueryBuilder queryBuilder = QueryBuilders.boolQuery()
                .should(termQuery("title", "arms"))
                .should(termQuery("description", "arms"))
                .should(termQuery("subject", "arms"));
        searchRequestBuilder = new SearchRequestBuilder(client("1"), SearchAction.INSTANCE);
        searchResponse = searchRequestBuilder.setIndices("demo").setTypes("docs")
                .setQuery(queryBuilder)
                .execute().actionGet();
        assertEquals(1, searchResponse.getHits().getTotalHits());

        // search for expanded "pref" label term
        queryBuilder = termQuery("subject", "military equipment");
        searchRequestBuilder = new SearchRequestBuilder(client("1"), SearchAction.INSTANCE);
        searchResponse = searchRequestBuilder.setIndices("demo").setTypes("docs")
                .setQuery(queryBuilder)
                .execute().actionGet();
        assertEquals(1, searchResponse.getHits().getTotalHits());
    }

    /**
     * This test indexes a sample metadata record (=lucene document) having a
     * "title", "description", and "subject" field, which is semantically
     * enriched by a URI pointing to a SKOS concept "weapons".
     *
     * A search for "arms" returns that record as a result because "arms" is
     * defined as an alternative label (altLabel) for the concept "weapons".
     *
     * @throws IOException
     */

    /*public void uriBasedTermExpansion() throws IOException {

        // defining the document to be indexed
        Document doc = new Document();
        doc.add(new Field("title", "Spearhead", TextField.TYPE_STORED));
        doc.add(new Field("description",
                "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft..."
                        + "The spear was mainly a thrusting weapon, but could also be thrown. "
                        + "It was the principal weapon of the auxiliary soldier... "
                        + "(second - fourth century, Arbeia Roman Fort).",
                TextField.TYPE_NOT_STORED));
        doc.add(new Field("subject",
                "http://www.ukat.org.uk/thesaurus/concept/859",
                TextField.TYPE_NOT_STORED));

        // setting up the SKOS analyzer
        String skosFile = "src/test/resources/skos_samples/ukat_examples.n3";
        String indexName = "test";

        // ExpansionType.URI->the field to be analyzed (expanded) contains URIs
        SKOSEngine skosEngine = SKOSEngineFactory.getSKOSEngine(client("1"), indexName, skosFile);
        Analyzer skosAnalyzer = new SKOSAnalyzer(skosEngine, SKOSAnalyzer.ExpansionType.URI);

        // Define different analyzers for different fields
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put("subject", skosAnalyzer);
        PerFieldAnalyzerWrapper indexAnalyzer = new PerFieldAnalyzerWrapper(new SimpleAnalyzer(), analyzerPerField);

        // setting up a writer with a default (simple) analyzer
        writer = new IndexWriter(new RAMDirectory(), new IndexWriterConfig(indexAnalyzer));

        // adding the document to the index
        writer.addDocument(doc);

        // defining a query that searches over all fields
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("title", "arms")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("description", "arms")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("subject", "arms")), BooleanClause.Occur.SHOULD);

        // creating a new searcher
        searcher = new IndexSearcher(DirectoryReader.open(writer, false));

        TopDocs results = searcher.search(builder.build(), 10);

        // the document matches because "arms" is among the expanded terms
        assertEquals(1, results.totalHits);

        // defining a query that searches for a broader concept
        Query query = new TermQuery(new Term("subject", "military equipment"));

        results = searcher.search(query, 10);

        // ... also returns the document as result
        assertEquals(1, results.totalHits);

    }*/
}
