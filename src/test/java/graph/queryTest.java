package graph;

import commons.Enums;
import org.junit.Test;

import static experiment.MaxPNSize.maxPNSizeRisoTreeQueryMultiple;

public class queryTest {
    static String dir = "D:/gspatial_test/Riso-Tree";
    static String dataset = "Yelp";
    static String data_dir = dir + "/data/" + dataset;
    static String password = "0000";
    static String db_path_format = data_dir + "/neo4j-community-3.4.12_Gleenes_1.0_%s_new_version/data/databases/graph.db";
    static String db_path = String.format(db_path_format, "10") + "," + String.format(db_path_format, "40") + "," + String.format(db_path_format, "160") + "," + String.format(db_path_format, "640") + "," + String.format(db_path_format, "-1");
    static String query_dir = dir + "/data/result/query/" + dataset + "/";
    static String node_count = "6";
    static String selectivity = "0.001";
    static String query_path = query_dir + node_count + "_" + selectivity;
    static int query_count = 50;
    static boolean clear_cache = false;
    static Enums.ClearCacheMethod clear_cache_method = Enums.ClearCacheMethod.DOUBLE;
    static String output_path = dir + "/data/result/maxPNSizeRisoTreeQuery/" + dataset + "/";
    @Test
    public void testBatchRTreeInsertOneHopAware() throws Exception {
        maxPNSizeRisoTreeQueryMultiple(db_path, dataset, 2, query_path, query_count, password, clear_cache, clear_cache_method, output_path);
    }
}
