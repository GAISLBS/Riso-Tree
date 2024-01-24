#!/bin/bash
./package.sh

dir="D:/gspatial_test/Riso-Tree"
result_dir="${dir}/data/result"
maxPNSize_result_dir="${result_dir}/maxPNSizeRisoTreeQuery"
code_dir="${dir}"
password="0000"

clear_cache="false"
clear_cache_method="DOUBLE"

#java_cmd="C:/Users/KJY/.jdks/temurin-1.8.0_392/bin/java"
java_cmd="C:/Users/pc/.jdks/azul-1.8.0_402/bin/java"

source ./utility.sh

# for dataset in "Yelp_100" "Gowalla_100" "foursquare_100" "wikidata"
for dataset in "Yelp"
do
	data_dir="${dir}/data/${dataset}"
	output_dir="${maxPNSize_result_dir}/${dataset}"
	mkdir -p ${output_dir}
	time=$(get_time)
	log_path="${output_dir}/${dataset}_log_${time}.txt"
	avg_path="${output_dir}/RISOTREE_avg.tsv"
	detail_path="${output_dir}/RISOTREE_detail.txt"
	echo "${time}" >> ${avg_path}
	echo "global clear cache" >> ${avg_path}
	echo "${time}" >> ${detail_path}
	echo "global clear cache" >> ${detail_path}

	# alpha=1.0
	# split_mode="Gleenes"
	# suffix="${split_mode}_${alpha}_${maxPNSize}_new_version"
	db_path="${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_10_new_version/data/databases/graph.db"
	db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_40_new_version/data/databases/graph.db"
	db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_160_new_version/data/databases/graph.db"
	db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_640_new_version/data/databases/graph.db"
	db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_-1_new_version/data/databases/graph.db"

	# graph_path="${data_dir}/graph.txt"
	# entity_path="${data_dir}/entity.txt"
	# label_path="${data_dir}/graph_label.txt"
	# labelStrMapPath="${data_dir}/entity_string_label.txt"

	query_dir="${dir}/data/result/query/${dataset}"
	node_count=6
	query_count=50

	if [ $dataset = "wikidata" ];	then
  		selectivity_list_str="0.00001 0.0001"
  else
  		selectivity_list_str="0.1 0.01 0.001"
  fi

	# for selectivity in 0.000001 0.00001 0.0001 0.001 0.01
	# for selectivity in 0.01 0.001 0.0001 0.00001 0.000001
	for selectivity in $selectivity_list_str
	do
		query_path="${query_dir}/${node_count}_${selectivity}"
		jar_path="${code_dir}/target/Riso-Tree-0.0.1-SNAPSHOT.jar"

		# split_mode="Gleenes"
		# alpha="1.0"
		# containID_suffix="${split_mode}_${alpha}_new_version"
		# containID_path="${data_dir}/containID.txt"

		${java_cmd} -Xmx100g -jar ${jar_path} \
			-f maxPNSizeRisoTreeQueryMultiple \
			-dp ${db_path} \
			-d ${dataset} \
			-MAX_HOPNUM 2 \
			-queryPath ${query_path} \
			-queryCount ${query_count} \
			-password ${password} \
			-clearCache ${clear_cache} \
			-clearCacheMethod ${clear_cache_method} \
			-outputPath ${output_dir} \
			>> ${log_path}
	done
done