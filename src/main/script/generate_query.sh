#!/bin/bash
./package.sh

function get_selectivity_list() {
	if [ $1 == "Yelp_100" ];	then
		echo "0.0001,0.001,0.01,0.1"
	else
		echo "0.000001,0.00001,0.0001,0.001,0.01,0.1"
	fi
}

java_cmd="C:/Users/KJY/.jdks/temurin-1.8.0_392/bin/java"

# for dataset in "Yelp_100" "foursquare_100" "Gowalla_100" "wikidata"
for dataset in "smallGraph"
do
	# server
	dir="D:/gspatial_test/Riso-Tree"
	data_dir="${dir}/data/${dataset}"
	code_dir="${dir}"

	# server setup
	graph_path="${data_dir}/graph.txt"
	entity_path="${data_dir}/entity.txt"
	label_path="${data_dir}/graph_label.txt"
	labelStrMapPath="${data_dir}/entity_string_label.txt"
	spatialNodePNPath="${data_dir}/spatialNodesZeroOneHopPN.txt"

	jar_path="${code_dir}/target/Riso-Tree-0.0.1-SNAPSHOT.jar"
	selectivitiesStr=$(get_selectivity_list ${dataset})
	# selectivitiesStr="0.00001,0.0001,0.001,0.01,0.1"
	queryCount=100
	outputDir="${dir}/data/result/query/${dataset}"

	# for nodeCount in 2 3 4 5
	for nodeCount in 6 7
	do
		${java_cmd} -Xmx100g -jar ${jar_path} \
			-f generateQuery \
			-gp ${graph_path} \
			-ep ${entity_path} \
			-lp ${label_path} \
			-labelStrMapPath ${labelStrMapPath} \
			-selectivitiesStr ${selectivitiesStr} \
			-nodeCount ${nodeCount} \
			-queryCount ${queryCount} \
			-outputPath ${outputDir}
	done
done