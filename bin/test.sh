#!/usr/bin/env bash

# start ES 2.1.1 with ./bin/elasticsearch

# copy N3 resource to the "logs" directory #ES_HOME/logs e.g.
# cp src/test/resources/ukat_exampl.ns $ES_HOME/logs

# Note: the "logs" directory is permitted to read/write by ES security manager, other dirs might not work.

# Note: messages like
#
# [2016-01-13 20:48:07,817][WARN ][com.hp.hpl.jena.util.LocatorFile] Security problem testing for file
# java.security.AccessControlException: access denied ("java.io.FilePermission" "ont-policy.rdf" "read")
#
# in ES logs are harmless and can be ignored. If you care to suppress them, you must disable security with
# ./bin/elasticsearch -Dsecurity.manager.enabled=false

curl -XDELETE 'localhost:9200/test/'

curl -XPOST 'localhost:9200/test' -d '{
  "settings" : {
     "index" : {
        "analysis" : {
            "filter": {
                "skosfilter" : {
                    "type": "skos",
                    "indexName" : "ukat",
                    "skosFile": "logs/ukat_examples.n3",
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
            "match" : {
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