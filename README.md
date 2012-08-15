# SKOS analysis plugin for Elasticsearch

This is the SKOS support for Lucene, originally written by Bernhard Haslhofer for Lucene and Solr, ported to Elasticsearch by Jörg Prante.

For more information about the original version, please see https://github.com/behas/lucene-skos

## What is SKOS?

The [Simple Knowledge Organization System (SKOS)][skos] is a model for expressing controlled structured vocabularies (classification schemes, thesauri, taxonomies, etc.). As an application of the [Resource Description Framework (RDF)][rdf], SKOS allows these vocabularies to be published as dereferenceable resources on the Web, which makes them easy to retrieve and reuse in applications. SKOS plays a major role in the ongoing [Linked Data][ld] activities.

## SKOS analysis

SKOS for [Elasticsearch][elasticsearch] is an analyzer plugin. It takes existing SKOS concepts schemes and performs term expansion for given documents and/or queries.

## Features

The module supports the following use cases:

 * Expansion of URI terms to SKOS labels: URI-references to SKOS concepts in given documents are expanded by the labels behind those concepts.

 * Expansion of text terms to SKOS labels: Labels in given documents, which are defined as preferred concept labels in a given SKOS vocabulary, are expanded by additional labels defined in that vocabulary.

## Installation

The SKOS Analyzer Module can be installed like every other plugin for Elasticsearch.

Please ensure you have at least Elasticsearch version **0.19.8** installed.

In order to install the plugin, simply run

	bin/plugin -install jprante/elasticsearch-analysis-skos/1.0.1


### Using the Elasticsearch SKOS plugin

Given the following example SKOS file **ukat_examples.n3** in **$ES\_HOME**

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

you can use URI-based expansion like in this demonstration:

	curl -XDELETE 'http://localhost:9200/test/'

	curl -XPOST 'http://localhost:9200/test' -d '{
	  "settings" : {
	     "index" : {
	        "analysis" : {
	            "filter": {
	                "skosfilter" : {
	                    "type": "skos",
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
	           "index_analyzer" : "skos",
	           "search_analyzer" : "standard"
	         }
	       }
	     }
	  }    
	}'

	# index test document
	curl -XPUT 'http://localhost:9200/test/subjects/1' -d '{
	    "title" :  "Spearhead",
	    "description": "Roman iron spearhead. The spearhead was attached to one end of a wooden shaft.The spear was mainly a thrusting weapon, but could also be thrown. It was the principal weapon of the auxiliary soldier. (second - fourth century, Arbeia Roman Fort)",
	    "subject" : "http://www.ukat.org.uk/thesaurus/concept/859"
	}'

	curl -XPOST 'http://localhost:9200/test/_refresh'
	echo

	# should give one hit
	curl -XGET 'http://localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "text" : {
	                  "subject": "arms"
	             }
	      }
	}'
	echo

	# should give one hit
	curl -XGET 'http://localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "text" : {
	                  "subject": "weapons"
	             }
	      }
	}'
	echo

	# should give no hit
	curl -XGET 'http://localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "text" : {
	                  "subject": "nonsense"
	             }
	      }
	}'
	echo

Parameter overview
------------------

The following settings parameters may be used in a filter of type **skos**

	path - a path for SKOS resource locations
	skosFile - the name of the skos file with suffix .n3, .rdf, .ttl, .zip (mandatory)
	expansionType - wither URI or LABEL (mandatory)
	bufferSize - a buffer size for the number of words that will be checked for expansion
	language - a language for the expansion
	skosType - a string with space-separated terms of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED
	

[skos]: http://www.w3.org/TR/skos-primer/ "SKOS Primer"
[rdf]: http://www.w3.org/TR/rdf-primer/ "RDF Primer"
[ld]: http://www.w3.org/standards/semanticweb/data "Linked Data"
[lucene]: http://lucene.apache.org/core/ "Apache Lucene"
[solr]: http://lucene.apache.org/solr/ "Apache Solr"
[elasticsearch]: http://elasticsearch.org "Elasticsearch"
[jena]: http://jena.apache.org/ "Apache Jena"