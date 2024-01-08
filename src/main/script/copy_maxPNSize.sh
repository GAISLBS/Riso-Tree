#!/bin/bash
#dataset="wikidata"
#dataset="smallGraph"
dataset="Yelp"

# server
dir="D:/gspatial_test/Riso-Tree"
data_dir="${dir}/data/${dataset}"

split_mode="Gleenes"
alpha="1.0"

for maxPNSize in 10 40 160 640
do
	suffix="${split_mode}_${alpha}_${maxPNSize}_new_version"
	echo ${suffix}

	# source_path="${data_dir}/neo4j-community-3.4.12_node_edges/"
	source_path="${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_-1_new_version"
	target_path="${data_dir}/neo4j-community-3.4.12_${suffix}"

	echo ${source_path}
	echo ${target_path}
	cp -a ${source_path} ${target_path}
done