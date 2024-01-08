package graph;

import commons.Config;
import org.junit.Test;

public class treeConstructTest {
    static String dir = "D:/gspatial_test/Riso-Tree";
    static String dataset = "Yelp";
    static String data_dir = dir + "/data/" + dataset;
    static String code_dir = "D:/gspatial_test";

    static String db_path = data_dir + "/neo4j-community-3.4.12/data/databases/graph.db_Gleene_1.0";
    static String graph_path = data_dir + "/graph.txt";
    static String entity_path = data_dir + "/entity.txt";
    static String label_path = data_dir + "/label.txt";

    @Test
    public void testBatchRTreeInsertOneHopAware() {
        LoadDataNoOSM loadDataNoOSM = new LoadDataNoOSM();
        loadDataNoOSM.batchRTreeInsertOneHopAware(db_path, dataset, graph_path, entity_path, label_path);
    }
}
