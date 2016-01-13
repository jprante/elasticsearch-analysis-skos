Elasticsearch SKOS analysis plugin
==================================

This is the SKOS support for Lucene, originally written by Bernhard Haslhofer for Lucene and Solr,
ported to Elasticsearch.

For more information about the original version, please see https://github.com/behas/lucene-skos

For a presentation and discussion, see
http://de.slideshare.net/bhaslhofer/using-skos-vocabularies-for-improving-web-search

What is SKOS?
-------------
The Simple Knowledge Organization System  (SKOS) is a model for expressing controlled structured
vocabularies (classification schemes, thesauri, taxonomies, etc.).
As an application of the Resource Description Framework (RDF),
SKOS allows these vocabularies to be published as dereferenceable resources on the Web,
which makes them easy to retrieve and reuse in applications. SKOS plays a major role in the
ongoing Linked Data activities.

SKOS analysis
-------------

SKOS for `Elasticsearch`_ is an analyzer plugin. It takes existing SKOS concepts schemes
and performs term expansion for given documents and/or queries.

Features
--------

The module supports the following use cases:

- Expansion of URI terms to SKOS labels: URI-references to SKOS concepts in given
  documents are expanded by the labels behind those concepts.

- Expansion of text terms to SKOS labels: Labels in given documents, which are
  defined as preferred concept labels in a given SKOS vocabulary, are expanded by
  additional labels defined in that vocabulary.

Installation
------------

(https://travis-ci.org/jprante/elasticsearch-analysis-skos.png)

| Elasticsearch version    | Plugin      | Release date |
| ------------------------ | ----------- | -------------|
| 2.1.1                    | 2.1.1.1     | Jan 13, 2016 |
| 1.4.2                    | 1.4.2.0     | Feb  5, 2015 |

2.x
---

    ./bin/plugin install http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-analysis-skos/2.1.1.0/elasticsearch-analysis-skos-2.1.1.0-plugin.zip

Do not forget to restart the node after installing.

1.x
---

    ./bin/plugin -install analysis-skos -url  http://xbib.org/repository/org/xbib/elasticsearch/plugin/elasticsearch-analysis-skos/1.4.2.0/elasticsearch-analysis-skos-1.4.2.0.zip

Do not forget to restart the node after installing.

Project docs
------------

The Maven project site is available at [Github](http://jprante.github.io/elasticsearch-analysis-skos)

Example
=======

Given the following example SKOS file *ukat_examples.n3* in *$ES\_HOME*

	@prefix skos:   <http://www.w3.org/2004/02/skos/core#> .
	@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
	@prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#> .
	@prefix ukat:   <http://www.ukat.org.uk/thesaurus/concept/> .

	ukat:859 rdf:type skos:Concept ;
	        skos:prefLabel "Weapons" ;
	        skos:altLabel "Armaments" ;
	        skos:altLabel "Arms" ;
	        skos:broader ukat:5060 ;
	        skos:narrower ukat:18874 ;
	        skos:narrower ukat:7630 .

	ukat:5060 rdf:type skos:Concept;
	        skos:prefLabel "Military equipment";
	        skos:altLabel "Defense equipment and supplies";
	        skos:altLabel "Ordnance".

	ukat:18874 rdf:type skos:Concept;
	        skos:prefLabel "Ammunition".

	ukat:7630 rdf:type skos:Concept;
	        skos:prefLabel "Artillery".

you can use URI-based expansion like in this demonstration

	curl -XDELETE 'localhost:9200/test/'

	curl -XPOST 'localhost:9200/test' -d '{
	  "settings" : {
	     "index" : {
	        "analysis" : {
	            "filter": {
	                "skosfilter" : {
	                    "type": "skos",
	                    "indexName" : "ukat",
	                    "skosFile": "ukat_examples.n3", 
	                    "expansionType": "URI"
	                }
	            },
	            "analyzer" : {
	                "skos" : {
	                    "type" : "custom",
	                    "tokenizer" : "keyword",
	                    "filter" : "skosfilter"
	                }
	            }
	        }
	    }
	  },
	  "mappings" : {
	     "_default_" : {
	       "properties" : {
	         "subject" : {
	           "type" : "string",
	           "analyzer" : "skos",
	           "search_analyzer" : "standard"
	         }
	       }
	     }
	  }    
	}'

	# index test document
	curl -XPUT 'localhost:9200/test/subjects/1' -d '{
	    "title" :  "Spearhead",
	    "description": "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft.The spear was mainly a thrusting weapon, but could also be thrown. It was the principal weapon of the auxiliary soldier. (second - fourth century, Arbeia Roman Fort)",
	    "subject" : "http://www.ukat.org.uk/thesaurus/concept/859"
	}'

	curl -XPOST 'localhost:9200/test/_refresh'
	echo

	# should give one hit
	curl -XGET 'localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "term" : {
	                  "subject": "arms"
	             }
	      }
	}'
	echo

	# should give one hit
	curl -XGET 'localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "term" : {
	                  "subject": "weapons"
	             }
	      }
	}'
	echo

    # should give one hit
    curl -XGET 'localhost:9200/test/_search?pretty' -d '{
          "query": {
                "term" : {
                      "subject": "military equipment"
                 }
          }
    }'
    echo

	# should give no hit
	curl -XGET 'localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "term" : {
	                  "subject": "nonsense"
	             }
	      }
	}'
	echo

Parameter overview
------------------

The following settings parameters may be used in a filter of type *skos*

	indexName - a name for SKOS index

	skosFile - the name of the skos file with suffix .n3, .rdf, .ttl, .zip (mandatory)

	expansionType - URI or LABEL (mandatory)

	bufferSize - a buffer size for the number of words that will be checked for expansion

	language - a language for the expansion

	skosType - a string with space-separated terms of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED
	

# License

Elasticsearch SKOS Plugin

Copyright (C) 2013 Jörg Prante

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.