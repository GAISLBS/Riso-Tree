#!/bin/bash
./package.sh

dir="/hdd/code/yuhansun"
kValue="1,5,25,125"

# dataset="Yelp_100"
# selectivity_list="0.0001 0.001 0.01 0.1"
# selectivity=0.0001

dataset="Gowalla_100"
#dataset="foursquare_100"
# selectivity_list="0.000001 0.00001 0.0001 0.001"
selectivity=0.000001

MAX_HOPNUM=2

#dataset="wikidata"
#MAX_HOPNUM=1
#selectivity_list="0.000001 0.00001 0.0001 0.001"
selectivity=0.000001

run_spatial=1
run_risotree=1
run_naive=1

split_mode="Gleenes"
maxPNSize="-1"
alpha=1.0

query_size=3
query_count=50

password="syhSYH.19910205"
data_dir="${dir}/data/${dataset}"
db_dir="${data_dir}/selectivity"
code_dir="${dir}/code"
result_dir="${dir}/result/Riso-Tree/KCount/${dataset}"
query_dir="${dir}/result/query/${dataset}"
mkdir -p $result_dir

query_path="${query_dir}/${query_size}_${selectivity}"

output_path="${result_dir}"
log_path="${result_dir}/${dataset}_log.txt"

jar_path="${code_dir}/Riso-Tree/target/Riso-Tree-0.0.1-SNAPSHOT.jar"
suffix="${split_mode}_${alpha}_${maxPNSize}_new_version"
db_path="${db_dir}/neo4j-community-3.4.12_${suffix}/data/databases/graph.db"

source ./utility.sh
time=$(get_time)

##### NAIVE ######
log_path="${result_dir}/${dataset}_naive_log_${time}.txt"
if [ $run_naive == 1 ];	then
	# java -Xmx100g -jar ${jar_path} \
	# 	-f selectivityExperimentSingleMethod \
	# 	-method "NAIVE"	\
	# 	-dp ${db_path} \
	# 	-d ${dataset} \
	# 	-MAX_HOPNUM ${MAX_HOPNUM} \
	# 	-queryPath ${query_path} \
	# 	-queryCount ${query_count} \
	# 	-password ${password}	\
	# 	-clearCache "true"	\
	# 	-clearCacheMethod "DOUBLE" \
	# 	-outputPath ${output_path} \
	# 	>> ${log_path}

	java -Xmx100g -jar ${jar_path} \
		-f KCountExperimentSingleMethod \
		-method "NAIVE"	\
		-dp ${db_path} \
		-d ${dataset} \
		-MAX_HOPNUM ${MAX_HOPNUM} \
		-kValue ${kValue}	\
		-queryPath ${query_path} \
		-queryCount ${query_count} \
		-password ${password}	\
		-clearCache "false"	\
		-clearCacheMethod "NULL" \
		-outputPath ${output_path} \
		>> ${log_path}
fi


##### SPATIAL_FIRST ######
log_path="${result_dir}/${dataset}_spatialfirst_log_${time}.txt"
if [ $run_spatial == 1 ];	then
# java -Xmx100g -jar ${jar_path} \
# 	-f selectivityExperimentSingleMethod \
# 	-method "SPATIAL_FIRST"	\
# 	-dp ${db_path} \
# 	-d ${dataset} \
# 	-MAX_HOPNUM ${MAX_HOPNUM} \
# 	-queryPath ${query_path} \
# 	-queryCount ${query_count} \
# 	-password ${password}	\
# 	-clearCache "true"	\
# 	-clearCacheMethod "DOUBLE" \
# 	-outputPath ${output_path} \
# 	>> ${log_path}

java -Xmx100g -jar ${jar_path} \
	-f KCountExperimentSingleMethod \
	-method "SPATIAL_FIRST"	\
	-dp ${db_path} \
	-d ${dataset} \
	-MAX_HOPNUM ${MAX_HOPNUM} \
	-kValue ${kValue}	\
	-queryPath ${query_path} \
	-queryCount ${query_count} \
	-password ${password}	\
	-clearCache "false"	\
	-clearCacheMethod "NULL" \
	-outputPath ${output_path} \
	>> ${log_path}
fi

##### RISOTREE ######
log_path="${result_dir}/${dataset}_risotree_log_${time}.txt"
if [ $run_risotree == 1 ];	then
	# java -Xmx100g -jar ${jar_path} \
	# 	-f selectivityExperimentSingleMethod \
	# 	-method "RISOTREE"	\
	# 	-dp ${db_path} \
	# 	-d ${dataset} \
	# 	-MAX_HOPNUM ${MAX_HOPNUM} \
	# 	-queryPath ${query_path} \
	# 	-queryCount ${query_count} \
	# 	-password ${password}	\
	# 	-clearCache "true"	\
	# 	-clearCacheMethod "DOUBLE" \
	# 	-outputPath ${output_path} \
	# 	>> ${log_path}

	java -Xmx100g -jar ${jar_path} \
		-f KCountExperimentSingleMethod \
		-method "RISOTREE"	\
		-dp ${db_path} \
		-d ${dataset} \
		-MAX_HOPNUM ${MAX_HOPNUM} \
		-kValue ${kValue}	\
		-queryPath ${query_path} \
		-queryCount ${query_count} \
		-password ${password}	\
		-clearCache "false"	\
		-clearCacheMethod "NULL" \
		-outputPath ${output_path} \
		>> ${log_path}
fi
