package graph;

import commons.Enums;
import org.junit.Test;

import static experiment.MaxPNSize.maxPNSizeRisoTreeQueryMultiple;
import static experiment.Prepare.generateExperimentCypherQuery;

public class queryTest {
    static String dir = "D:/gspatial_test/Riso-Tree";
    static String dataset = "Yelp";
    static String data_dir = dir + "/data/" + dataset;
    static String graph_path = data_dir + "/graph.txt";
    static String in_graph_path = data_dir + "/ingoing_graph.txt";
    static String entity_path = data_dir + "/entity.txt";
    static String graph_label_path = data_dir + "/graph_label.txt";
    static String entity_label_path = data_dir + "/entity_string_label.txt";
    static String password = "0000";
    static String db_path_format = data_dir + "/neo4j-community-3.4.12_Gleenes_1.0_%s_new_version/data/databases/graph.db";
//    static String db_path = String.format(db_path_format, "10") + "," + String.format(db_path_format, "40") + "," + String.format(db_path_format, "160") + "," + String.format(db_path_format, "640") + "," + String.format(db_path_format, "-1");
    static String db_path = String.format(db_path_format, "-1");
    static String query_dir = dir + "/data/result/query/" + dataset + "/";
    static String node_count = "6";
    static String selectivity = "0.001";
    static String selectivity_str_list = "0.0001,0.001,0.01,0.1";
    static String query_path = query_dir + node_count + "_" + selectivity;
    static int query_count = 50;
    static boolean clear_cache = false;
    static Enums.ClearCacheMethod clear_cache_method = Enums.ClearCacheMethod.DOUBLE;
    static String output_path = dir + "/data/result/maxPNSizeRisoTreeQuery/" + dataset + "/";
    @Test
    public void testGenerateQuery(){
        try {
            generateExperimentCypherQuery(graph_path, entity_path, graph_label_path, entity_label_path, selectivity_str_list, query_count, Integer.parseInt(node_count), query_dir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testBatchRTreeInsertOneHopAware() throws Exception {
        maxPNSizeRisoTreeQueryMultiple(db_path, dataset, 2, query_path, query_count, password, clear_cache, clear_cache_method, output_path);
    }
}
