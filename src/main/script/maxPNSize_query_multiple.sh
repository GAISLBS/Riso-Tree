#!/bin/bash
./package.sh

dir="/hdd/code/yuhansun"

dataset="wikidata"

data_dir="${dir}/data/wikidata_risotree"
code_dir="${dir}/code"
result_dir="${dir}/result"

# alpha=1.0
# split_mode="Gleenes"
# suffix="${split_mode}_${alpha}_${maxPNSize}_new_version"
db_path="${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_10_new_version/data/databases/graph.db"
db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_40_new_version/data/databases/graph.db"
db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_160_new_version/data/databases/graph.db"
db_path+=",${data_dir}/neo4j-community-3.4.12_Gleenes_1.0_640_new_version/data/databases/graph.db"

# graph_path="${data_dir}/graph.txt"
# entity_path="${data_dir}/entity.txt"
# label_path="${data_dir}/graph_label.txt"
# labelStrMapPath="${data_dir}/entity_string_label.txt"

query_dir="${dir}/result/query/${dataset}"
node_count=3
query_count=20
for selectivity in 0.000001 0.00001 0.0001 0.001
do
	query_path="${query_dir}/${node_count}_${selectivity}"

	output_path="${result_dir}/Riso-Tree/maxPNSizeRisoTreeQuery"

	jar_path="${code_dir}/Riso-Tree/target/Riso-Tree-0.0.1-SNAPSHOT.jar"

	# split_mode="Gleenes"
	# alpha="1.0"
	# containID_suffix="${split_mode}_${alpha}_new_version"
	# containID_path="${data_dir}/containID.txt"

	java -Xmx100g -jar ${jar_path} \
		-f maxPNSizeRisoTreeQueryMultiple \
		-dp ${db_path} \
		-d ${dataset} \
		-MAX_HOPNUM 1 \
		-queryPath ${query_path} \
		-queryId ${query_count} \
		-outputPath ${output_path}
done