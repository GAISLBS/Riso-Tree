#!/bin/bash
./package.sh

dataset="Yelp_100"

# server
dir="/hdd/code/yuhansun"
data_dir="${dir}/data/${dataset}"
db_dir="${data_dir}/alpha"
code_dir="${dir}/code"

# local test setup
# dir="/Users/zhouyang/Google_Drive/Projects/tmp/risotree"
# dataset="Yelp"
# data_dir="${dir}/${dataset}"
# code_dir="/Users/zhouyang/Google_Drive/Projects/github_code"

# 407CD setup
# data_dir="D:/Project_Data/wikidata-20180308-truthy-BETA.nt"
# code_dir="D:/Google_Drive/Projects/github_code"
# db_path="D:/Neo4jData/neo4jDatabases/database-0c3f32e4-025a-4a22-a4cd-a9b979a9adf8/installation-3.4.9/data/databases/graph.db"


# server setup
graph_path="${data_dir}/graph.txt"
entity_path="${data_dir}/entity.txt"
label_path="${data_dir}/graph_label.txt"
labelStrMapPath="${data_dir}/entity_string_label.txt"
spatialNodePNPath="${data_dir}/spatialNodesZeroOneHopPN.txt"

jar_path="${code_dir}/Riso-Tree/target/Riso-Tree-0.0.1-SNAPSHOT.jar"

split_mode="Gleenes"
maxPNSize="-1"

for alpha in 0 0.25 0.5 0.55 0.6 0.65 0.7 0.75 0.8 0.85 0.9 0.95 1.0
# for alpha in 0.9999
do
	suffix="${split_mode}_${alpha}_${maxPNSize}_new_version"

	db_path="${db_dir}/neo4j-community-3.4.12_${suffix}/data/databases/graph.db"
	containID_path="${db_dir}/containID_${suffix}.txt"
	PNPathAndPrefix="${db_dir}/PathNeighbors_${suffix}"

	# 0-hop
	java -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHopNoGraphDb \
	-c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	-lp ${label_path} -hop 0 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

	# 1-hop
	java -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHopNoGraphDb \
	-c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	-lp ${label_path} -hop 1 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

	# 2-hop
	java -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHopNoGraphDb \
	-c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	-lp ${label_path} -hop 2 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}



	########## Construct based on the db (hop-1) path neighbor
	# 0-hop
	# java -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHop \
	# -dp ${db_path} -c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	# -lp ${label_path} -hop 0 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

	# java -Xmx100g -jar ${jar_path} -f wikiLoadPN \
	# -dp ${db_path} -c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	# -lp ${label_path} -hop 0 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

	# # 1-hop
	# java -Xmx100g -jar ${jar_path} -f wikiConstructPNTimeSingleHop \
	# -dp ${db_path} -c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	# -lp ${label_path} -hop 1 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}

	# java -Xmx100g -jar ${jar_path} -f wikiLoadPN \
	# -dp ${db_path} -c ${containID_path} -gp ${graph_path} -labelStrMapPath ${labelStrMapPath}\
	# -lp ${label_path} -hop 1 -PNPrefix ${PNPathAndPrefix} -maxPNSize ${maxPNSize}
done