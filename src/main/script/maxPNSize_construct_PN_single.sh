#!/bin/bash
#./package.sh

#java_cmd="C:/Users/KJY/.jdks/temurin-1.8.0_392/bin/java"
java_cmd="C:/Users/pc/.jdks/azul-1.8.0_402/bin/java"

# for dataset in "Yelp_100" "Gowalla_100" "Patents_100_random_20"
for dataset in "Yelp"
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

	jar_path="${code_dir}/target/Riso-Tree-0.0.1-SNAPSHOT.jar"

	split_mode="Gleenes"
	alpha="1.0"
	containID_suffix="${split_mode}_${alpha}_-1_new_version"
	containID_path="${data_dir}/containID_${containID_suffix}.txt"

	# for maxPNSize in 10 20 40 80 160 320 640 1280 2560 -1
	# for maxPNSize in -1
	for maxPNSize in 10 40 160 640 -1
	do
		suffix="${split_mode}_${alpha}_${maxPNSize}_new_version"
		PNPathAndPrefix="${data_dir}/PathNeighbors_${suffix}"

		# 0-hop
		${java_cmd} -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHopNoGraphDb \
		-c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
		-lp ${label_path} -hop 0 -PNPrefix ${PNPathAndPrefix} -maxPNSize -1

		# 1-hop
		${java_cmd} -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHopNoGraphDb \
		-c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
		-lp ${label_path} -hop 1 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

		# 2-hop
		${java_cmd} -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHopNoGraphDb \
		-c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
		-lp ${label_path} -hop 2 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

	done
done