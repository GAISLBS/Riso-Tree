#!/bin/bash

# server
# dir="/hdd/code/yuhansun"
# dataset="Yelp"
# data_dir="${dir}/data/${dataset}"
# code_dir="${dir}/code"

# local test setup
dir="D:/gspatial_test/Riso-Tree"
dataset="Yelp"
data_dir="${dir}/data/${dataset}"
code_dir="D:/gspatial_test"

db_path="${data_dir}/neo4j-community-3.4.12/data/databases/graph.db_Gleene_1.0"
graph_path="${data_dir}/graph.txt"
entity_path="${data_dir}/entity.txt"
label_path="${data_dir}/label.txt"

jar_path="${code_dir}/Riso-Tree/target/Riso-Tree-0.0.1-SNAPSHOT.jar"
java_cmd="C:/Users/KJY/.jdks/temurin-1.8.0_392/bin/java"

echo "${java_cmd} -Xmx100g -jar ${jar_path} -h"
${java_cmd} -Xmx100g -jar ${jar_path} -h

echo "${java_cmd} -Xmx100g -jar ${jar_path} -f tree -dp ${db_path} -d ${dataset} -gp ${graph_path} -ep ${entity_path} -lp ${label_path}"
${java_cmd} -Xmx100g -jar ${jar_path} -f tree -dp ${db_path} -d ${dataset} -gp ${graph_path} -ep ${entity_path} -lp ${label_path}