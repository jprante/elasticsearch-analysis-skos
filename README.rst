Elasticsearch SKOS analysis plugin
==================================

This is the SKOS support for Lucene, originally written by Bernhard Haslhofer for Lucene and Solr,
ported to Elasticsearch.

For more information about the original version, please see https://github.com/behas/lucene-skos

What is SKOS?
-------------
The `Simple Knowledge Organization System (SKOS)`__ is a model for expressing controlled structured
vocabularies (classification schemes, thesauri, taxonomies, etc.).
As an application of the `Resource Description Framework (RDF)`_,
SKOS allows these vocabularies to be published as dereferenceable resources on the Web,
which makes them easy to retrieve and reuse in applications. SKOS plays a major role in the
ongoing `Linked Data`_ activities.

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

.. image:: https://travis-ci.org/jprante/elasticsearch-analysis-skos.png

Prerequisites::

  Elasticsearch 0.90.7+

=============  =========  =================  =============================================================
ES version     Plugin     Release date       Command
-------------  ---------  -----------------  -------------------------------------------------------------
0.90.7         **1.1.0**  Nov 18, 2013       ./bin/plugin -install analysis-skos -url http://bit.ly/I1rAZt
=============  =========  =================  =============================================================

Do not forget to restart the node after installing.

Project docs
------------

The Maven project site is available at `Github <http://jprante.github.io/elasticsearch-analysis-skos>`_

Binaries
--------

Binaries are available at `Bintray <https://bintray.com/pkg/show/general/jprante/elasticsearch-plugins/elasticsearch-analysis-skos>`_


Example
=======

Given the following example SKOS file **ukat_examples.n3** in **$ES\_HOME**::

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

you can use URI-based expansion like in this demonstration::

	curl -XDELETE 'localhost:9200/test/'

	curl -XPOST 'localhost:9200/test' -d '{
	  "settings" : {
	     "index" : {
	        "analysis" : {
	            "filter": {
	                "skosfilter" : {
	                    "type": "skos",
	                    "path" : "/tmp/indexdir",
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
	            "text" : {
	                  "subject": "arms"
	             }
	      }
	}'
	echo

	# should give one hit
	curl -XGET 'localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "text" : {
	                  "subject": "weapons"
	             }
	      }
	}'
	echo

	# should give no hit
	curl -XGET 'localhost:9200/test/_search?pretty' -d '{
	      "query": {
	            "text" : {
	                  "subject": "nonsense"
	             }
	      }
	}'
	echo

Parameter overview
------------------

The following settings parameters may be used in a filter of type **skos**::

	`path` - a path for SKOS index directory
	`skosFile` - the name of the skos file with suffix .n3, .rdf, .ttl, .zip (mandatory)
	`expansionType` - wither URI or LABEL (mandatory)
	`bufferSize` - a buffer size for the number of words that will be checked for expansion
	`language` - a language for the expansion
	`skosType` - a string with space-separated terms of PREF, ALT, HIDDEN, BROADER, NARROWER, BROADERTRANSITIVE, NARROWERTRANSITIVE, RELATED
	

.. _Simple Knowledge Organization System (SKOS): http://www.w3.org/TR/skos-primer/
.. _Resource Description Framework (RDF):: http://www.w3.org/TR/rdf-primer/
.. _Linked Data: http://www.w3.org/standards/semanticweb/data
.. _Apache Lucene: http://lucene.apache.org/core/
.. _Apache Solr: http://lucene.apache.org/solr/
.. _Elasticsearch: http://elasticsearch.org/
.. _Apache Jena: http://jena.apache.org/
