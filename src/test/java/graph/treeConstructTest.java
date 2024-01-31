package graph;

import commons.Config;
import org.junit.Test;

import static dataprocess.Wikidata.generateZeroOneHopPNForSpatialNodes;

public class treeConstructTest {
    static String dir = "D:/gspatial_test/Riso-Tree";
    static String dataset = "Yelp";
    static String data_dir = dir + "/data/" + dataset;
    static String code_dir = "D:/gspatial_test";

    static String db_path = data_dir + "/neo4j-community-3.4.12/data/databases/graph.db_Gleene_1.0";
    static String graph_path = data_dir + "/graph.txt";
    static String entity_path = data_dir + "/entity.txt";
    static String label_path = data_dir + "/graph_label.txt";
    static String entity_label_path = data_dir + "/entity_string_label.txt";
    static String spatialNodePNPath = data_dir + "/spatialNodesZeroOneHopPN_-1.txt";

    @Test
    public void testGenerateZeroOneHopPNForSpatialNodes() {
        try {
            generateZeroOneHopPNForSpatialNodes(graph_path, label_path, entity_path, entity_label_path, -1, 1, spatialNodePNPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testBatchRTreeInsertOneHopAware() {
        LoadDataNoOSM loadDataNoOSM = new LoadDataNoOSM();
        loadDataNoOSM.batchRTreeInsertOneHopAware(db_path, dataset, graph_path, entity_path, label_path);
    }
}
