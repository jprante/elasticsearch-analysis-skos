package org.xbib.elasticsearch.index.analysis.skos.engine.jena;

import com.hp.hpl.jena.ontology.AnnotationProperty;
import com.hp.hpl.jena.ontology.ObjectProperty;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateRequest;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.RDF;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthAction;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequestBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexAction;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.refresh.RefreshAction;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequestBuilder;
import org.elasticsearch.action.bulk.BulkAction;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexAction;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.indices.IndexAlreadyExistsException;
import org.elasticsearch.search.SearchHit;
import org.xbib.elasticsearch.index.analysis.skos.engine.SKOSEngine;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * SKOSEngine Implementation for Elasticsearch.
 * Each SKOS concept is stored/indexed as a Elasticsearch document.
 * All labels are converted to lowercase.
 */
public class SKOSEngineImpl implements SKOSEngine {

    private final static ESLogger logger = ESLoggerFactory.getLogger(SKOSEngineImpl.class.getSimpleName());

    /*
     * Static fields used in the Lucene Index
     */
    private static final String FIELD_URI = "uri";
    private static final String FIELD_PREF_LABEL = "pref";
    private static final String FIELD_ALT_LABEL = "alt";
    private static final String FIELD_HIDDEN_LABEL = "hidden";
    private static final String FIELD_BROADER = "broader";
    private static final String FIELD_NARROWER = "narrower";
    private static final String FIELD_BROADER_TRANSITIVE = "broaderTransitive";
    private static final String FIELD_NARROWER_TRANSITIVE = "narrowerTransitive";
    private static final String FIELD_RELATED = "related";
    /**
     * The input SKOS model
     */
    private Model skosModel;

    private final String indexName;

    private final Client client;

    /**
     * The languages to be considered when returning labels.
     *
     * If NULL, all languages are supported
     */
    private Set<String> languages;

    /**
     * This constructor loads the SKOS model from a given InputStream using the
     * given serialization language parameter, which must be either N3, RDF/XML,
     * or TURTLE.
     *
     * @param inputStream the input stream
     * @param lang the serialization language
     * @throws IOException if the model cannot be loaded
     */
    public SKOSEngineImpl(Client client, String indexName, InputStream inputStream, String lang) throws IOException {
        if (!("N3".equals(lang) || "RDF/XML".equals(lang) || "TURTLE".equals(lang))) {
            throw new IOException("Invalid RDF serialization format");
        }
        this.client = client;
        this.indexName = indexName;
        this.skosModel = ModelFactory.createDefaultModel();
        skosModel.read(inputStream, null, lang);
        entailSKOSModel();
        indexSKOSModel();
    }

    /**
     * This constructor loads the SKOS model from a given filename or URI,
     * starts the indexing process and sets up the index searcher.
     *
     * @param client the Elasticsearch client
     * @param indexName index name
     * @param filenameOrURI file name or URI
     * @param languages the languages to be considered
     * @throws IOException if indexing SKOS model fails
     */
    public SKOSEngineImpl(Client client, String indexName, String filenameOrURI, List<String> languages) throws IOException {
        this.client = client;
        String langSig = "";
        if (languages != null ) {
            this.languages = new TreeSet<>(languages);
            if (!this.languages.isEmpty()) {
                langSig = "-" + join(this.languages.iterator(), '-');
            }
        }
        this.indexName = indexName + langSig;
        if (filenameOrURI != null) {
            FileManager fileManager = new FileManager();
            fileManager.addLocatorFile();
            fileManager.addLocatorURL();
            fileManager.addLocatorClassLoader(SKOSEngineImpl.class.getClassLoader());
            if (getExtension(filenameOrURI).equals("zip")) {
                fileManager.addLocatorZip(filenameOrURI);
                filenameOrURI = getBaseName(filenameOrURI);
            }
            skosModel = fileManager.loadModel(filenameOrURI);
            entailSKOSModel();
            indexSKOSModel();
        }
    }

    /**
     * This constructor loads the SKOS model from a given InputStream using the
     * given serialization language parameter, which must be either N3, RDF/XML,
     * or TURTLE.
     *
     * @param inputStream the input stream
     * @param format the serialization language
     * @param languages the languages
     * @throws IOException if the model cannot be loaded
     */
    public SKOSEngineImpl(Client client, String indexName, InputStream inputStream, String format, List<String> languages)
            throws IOException {
        if (!("N3".equals(format) || "RDF/XML".equals(format) || "TURTLE".equals(format))) {
            throw new IOException("Invalid RDF serialization format");
        }
        this.client = client;
        this.indexName = indexName;
        if (languages != null) {
            this.languages = new TreeSet<>(languages);
        }
        skosModel = ModelFactory.createDefaultModel();
        skosModel.read(inputStream, null, format);
        entailSKOSModel();
        indexSKOSModel();
    }

    private void entailSKOSModel() {
        GraphStore graphStore = GraphStoreFactory.create(skosModel);
        String sparqlQuery =
                "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n"
                + "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
                + "INSERT { ?subject rdf:type skos:Concept }\n"
                + "WHERE {\n"
                + "{ ?subject skos:prefLabel ?text } UNION\n"
                + "{ ?subject skos:altLabel ?text } UNION\n"
                + "{ ?subject skos:hiddenLabel ?text }\n"
                + "}";
        UpdateRequest request = UpdateFactory.create(sparqlQuery);
        UpdateAction.execute(request, graphStore);
    }


    @Override
    public List<String> getAltLabels(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_ALT_LABEL);
    }

    @Override
    public List<String> getAltTerms(String label) throws IOException {
        List<String> result = new LinkedList<>();
        // convert the query to lower-case
        String queryString = label.toLowerCase();
        try {
            List<String> conceptURIs = getConcepts(queryString);
            if (conceptURIs != null) {
                for (String conceptURI : conceptURIs) {
                    List<String> altLabels = getAltLabels(conceptURI);
                    if (altLabels != null) {
                        result.addAll(altLabels);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public List<String> getHiddenLabels(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_HIDDEN_LABEL);
    }

    @Override
    public List<String> getBroaderConcepts(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_BROADER);
    }

    @Override
    public List<String> getBroaderLabels(String conceptURI) throws IOException {
        return getLabels(conceptURI, FIELD_BROADER);
    }

    @Override
    public List<String> getBroaderTransitiveConcepts(String conceptURI)
            throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_BROADER_TRANSITIVE);
    }

    @Override
    public List<String> getBroaderTransitiveLabels(String conceptURI)
            throws IOException {
        return getLabels(conceptURI, FIELD_BROADER_TRANSITIVE);
    }

    @Override
    public List<String> getConcepts(String label) throws IOException {
        List<String> concepts = new ArrayList<>();
        // convert the query to lower-case
        String queryString = label.toLowerCase();
        QueryBuilder queryBuilder = QueryBuilders.disMaxQuery()
                .tieBreaker(0.0f)
                .add(termQuery(FIELD_PREF_LABEL, queryString))
                .add(termQuery(FIELD_ALT_LABEL, queryString))
                .add(termQuery(FIELD_HIDDEN_LABEL, queryString));
        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client, SearchAction.INSTANCE);
        SearchResponse searchResponse = searchRequestBuilder.setIndices(indexName)
                .setQuery(queryBuilder)
                .setSize(100) // is 100 ok? 10?
                .execute().actionGet();
        if (searchResponse.getHits() != null) {
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                String conceptURI = (String) hit.getSource().get(FIELD_URI);
                concepts.add(conceptURI);
            }
        }
        return concepts;
    }

    private List<String> getLabels(String conceptURI, String field)
            throws IOException {
        List<String> labels = new LinkedList<>();
        List<String> concepts = readConceptFieldValues(conceptURI, field);
        if (concepts != null) {
            for (String aConceptURI : concepts) {
                labels.addAll(getPrefLabels(aConceptURI));
                labels.addAll(getAltLabels(aConceptURI));
            }
        }
        return labels;
    }

    @Override
    public List<String> getNarrowerConcepts(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_NARROWER);
    }

    @Override
    public List<String> getNarrowerLabels(String conceptURI) throws IOException {
        return getLabels(conceptURI, FIELD_NARROWER);
    }

    @Override
    public List<String> getNarrowerTransitiveConcepts(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_NARROWER_TRANSITIVE);
    }

    @Override
    public List<String>getNarrowerTransitiveLabels(String conceptURI) throws IOException {
        return getLabels(conceptURI, FIELD_NARROWER_TRANSITIVE);
    }

    @Override
    public List<String> getPrefLabels(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_PREF_LABEL);
    }

    @Override
    public List<String> getRelatedConcepts(String conceptURI) throws IOException {
        return readConceptFieldValues(conceptURI, FIELD_RELATED);
    }

    @Override
    public List<String> getRelatedLabels(String conceptURI) throws IOException {
        return getLabels(conceptURI, FIELD_RELATED);
    }

    @SuppressWarnings("unchecked")
    private List<String> readConceptFieldValues(String conceptURI, String field)
            throws IOException {
        TermQueryBuilder termQueryBuilder = termQuery(FIELD_URI, conceptURI);
        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client, SearchAction.INSTANCE);
        SearchResponse searchResponse = searchRequestBuilder.setIndices(indexName)
                .setQuery(termQueryBuilder)
                .setSize(1)
                .execute().actionGet();
        if (searchResponse.getHits().totalHits() != 1) {
            logger.warn("unknown concept " + conceptURI);
            return Collections.emptyList();
        }
        SearchHit hit = searchResponse.getHits().getAt(0);
        Object object = hit.getSource().get(field);
        if (!(object instanceof List)) {
            object = Collections.singletonList(object);
        }
        return (List<String>) object;
    }

    /**
     * Creates the synonym index
     *
     * @throws IOException
     */
    private void indexSKOSModel() throws IOException {

        ClusterHealthRequestBuilder clusterHealthRequestBuilder = new ClusterHealthRequestBuilder(client, ClusterHealthAction.INSTANCE);
        ClusterHealthResponse clusterIndexHealthResponse = clusterHealthRequestBuilder
                .setIndices(new String[]{}) // we need this, or ES will bark with an NPE
                .setWaitForYellowStatus()
                .setTimeout("15s").execute().actionGet();
        if (clusterIndexHealthResponse.isTimedOut()) {
            throw new IOException("cluster health is not yellow: " + clusterIndexHealthResponse.getStatus().name());
        }

        CreateIndexRequestBuilder createIndexRequestBuilder = new CreateIndexRequestBuilder(client, CreateIndexAction.INSTANCE, indexName);
        Settings settings = Settings.builder()
                .put("index.analysis.analyzer.default.type", "keyword")
                .build();
        try {
            createIndexRequestBuilder.setSettings(settings).execute().actionGet();
        } catch (IndexAlreadyExistsException e) {
            return;
        }

        BulkRequestBuilder bulkRequestBuilder = new BulkRequestBuilder(client, BulkAction.INSTANCE);

        ResIterator it = skosModel.listResourcesWithProperty(RDF.type, SKOS.Concept);
        while (it.hasNext()) {
            Resource skos_concept = it.next();
            XContentBuilder builder = jsonBuilder();
            builder.startObject()
                    .field(FIELD_URI, skos_concept.getURI());
            buildAnnotation(builder, skos_concept, SKOS.prefLabel, FIELD_PREF_LABEL);
            buildAnnotation(builder, skos_concept, SKOS.altLabel, FIELD_ALT_LABEL);
            buildAnnotation(builder, skos_concept, SKOS.hiddenLabel, FIELD_HIDDEN_LABEL);
            buildObject(builder, skos_concept, SKOS.broader, FIELD_BROADER);
            buildObject(builder, skos_concept, SKOS.broaderTransitive, FIELD_BROADER_TRANSITIVE);
            buildObject(builder, skos_concept, SKOS.narrower, FIELD_NARROWER);
            buildObject(builder, skos_concept, SKOS.narrowerTransitive, FIELD_NARROWER_TRANSITIVE);
            buildObject(builder, skos_concept, SKOS.related, FIELD_RELATED);
            builder.endObject();
            IndexRequestBuilder indexRequestBuilder = new IndexRequestBuilder(client, IndexAction.INSTANCE);
            indexRequestBuilder.setIndex(indexName).setType("skos")
                    .setSource(builder);
            bulkRequestBuilder.add(indexRequestBuilder);
            if (bulkRequestBuilder.numberOfActions() == 1000) {
                BulkResponse bulkResponse = bulkRequestBuilder.execute().actionGet();
                if (bulkResponse.hasFailures()) {
                    throw new IOException("can't index SKOS: " + bulkResponse.buildFailureMessage());
                }
                bulkRequestBuilder = new BulkRequestBuilder(client, BulkAction.INSTANCE);
            }
        }
        if (bulkRequestBuilder.numberOfActions() > 0) {
            BulkResponse bulkResponse = bulkRequestBuilder.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                throw new IOException("can't index SKOS: " + bulkResponse.buildFailureMessage());
            }
        }
        RefreshRequestBuilder refreshRequestBuilder = new RefreshRequestBuilder(client, RefreshAction.INSTANCE);
        refreshRequestBuilder.setIndices(indexName).execute().actionGet();
    }

    private void buildAnnotation(XContentBuilder builder, Resource skos_concept,
                                 AnnotationProperty property, String field) throws IOException {
        List<String> values = new LinkedList<>();
        StmtIterator stmt_iter = skos_concept.listProperties(property);
        while (stmt_iter.hasNext()) {
            Literal labelLiteral = stmt_iter.nextStatement().getObject().as(Literal.class);
            String label = labelLiteral.getLexicalForm();
            String labelLang = labelLiteral.getLanguage();
            if (this.languages != null && !this.languages.isEmpty() && !this.languages.contains(labelLang)) {
                continue;
            }
            values.add(label.toLowerCase());
        }
        builder.field(field, values.toArray(new String[values.size()]));
    }

    private void buildObject(XContentBuilder builder, Resource skos_concept,
                             ObjectProperty property, String field) throws IOException {
        List<String> values = new LinkedList<>();
        StmtIterator stmt_iter = skos_concept.listProperties(property);
        while (stmt_iter.hasNext()) {
            RDFNode concept = stmt_iter.nextStatement().getObject();
            if (!concept.canAs(Resource.class)) {
                logger.warn("error when indexing relationship of concept " + skos_concept.getURI() + " .");
                continue;
            }
            Resource resource = concept.as(Resource.class);
            values.add(resource.getURI());
        }
        builder.field(field, values.toArray(new String[values.size()]));
    }

    private String join(Iterator iterator, char separator) {
        // handle null, zero and one elements before building a buffer
        if (iterator == null) {
            return null;
        }
        if (!iterator.hasNext()) {
            return "";
        }
        Object first = iterator.next();
        if (!iterator.hasNext()) {
            return first == null ? "" : first.toString();
        }
        // two or more elements
        StringBuilder buf = new StringBuilder();
        if (first != null) {
            buf.append(first);
        }
        while (iterator.hasNext()) {
            buf.append(separator);
            Object obj = iterator.next();
            if (obj != null) {
                buf.append(obj);
            }
        }
        return buf.toString();
    }

    private static final char UNIX_SEPARATOR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';

    private String getName(String filename) {
        if (filename == null) {
            return null;
        }
        int index = indexOfLastSeparator(filename);
        return filename.substring(index + 1);
    }

    private int indexOfLastSeparator(String filename) {
        if (filename == null) {
            return -1;
        }
        int lastUnixPos = filename.lastIndexOf(UNIX_SEPARATOR);
        int lastWindowsPos = filename.lastIndexOf(WINDOWS_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int index = indexOfExtension(filename);
        if (index == -1) {
            return "";
        } else {
            return filename.substring(index + 1);
        }
    }

    private int indexOfExtension(String filename) {
        if (filename == null) {
            return -1;
        }
        int extensionPos = filename.lastIndexOf(EXTENSION_SEPARATOR);
        int lastSeparator = indexOfLastSeparator(filename);
        return (lastSeparator > extensionPos ? -1 : extensionPos);
    }
    public static final char EXTENSION_SEPARATOR = '.';

    private String getBaseName(String filename) {
        return removeExtension(getName(filename));
    }

    private String removeExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int index = indexOfExtension(filename);
        if (index == -1) {
            return filename;
        } else {
            return filename.substring(0, index);
        }
    }    
}
