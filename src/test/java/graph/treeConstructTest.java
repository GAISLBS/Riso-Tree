package graph;

import commons.Config;
import org.junit.Test;

import static dataprocess.Wikidata.generateZeroOneHopPNForSpatialNodes;
import static dataprocess.Wikidata.loadAllEntities;
import static graph.Construct_RisoTree.wikiConstructPNSingleHopNoGraphDb;
import static graph.Construct_RisoTree.wikiGenerateContainSpatialID;

public class treeConstructTest {
    static String dir = "D:/gspatial_test/Riso-Tree";
    static String dataset = "Yelp";
    static String data_dir = dir + "/data/" + dataset;
    static String db_path = data_dir + "/neo4j-community-3.4.12_Gleenes_1.0_-1_new_version/data/databases/graph.db";
    static String graph_path = data_dir + "/graph.txt";
    static String in_graph_path = data_dir + "/ingoing_graph.txt";
    static String entity_path = data_dir + "/entity.txt";
    static String graph_label_path = data_dir + "/graph_label.txt";
    static String entity_label_path = data_dir + "/entity_string_label.txt";
    static String spatialNodePNPath = data_dir + "/spatialNodesZeroOneHopPN_-1.txt";
    static String containSpatialIDPath = data_dir + "/containID_Gleenes_1.0_-1_new_version.txt";

    /**
     * Construct PathNeighbors for spatial nodes.
     */
    @Test
    public void testGenerateZeroOneHopPNForSpatialNodes() {
        try {
            generateZeroOneHopPNForSpatialNodes(graph_path, graph_label_path, entity_path, entity_label_path, -1, 1, spatialNodePNPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build Neo4j graph database And load all entities into the database.
     */
    @Test
    public void testLoadAllEntities() {
        try {
            loadAllEntities(entity_path, graph_label_path, entity_label_path, db_path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * load all edges into the database by graph file.
     * edges Label set to 'GRAPH_LINK'.
     */
    @Test
    public void testLoadGraphEdgesNoMap() {
        LoadDataNoOSM loadDataNoOSM = new LoadDataNoOSM();
        loadDataNoOSM.loadGraphEdgesNoMap(db_path, graph_path);
    }

    /**
     * make R-Tree for spatial nodes using neo4j spatial plugin.
     */
    @Test
    public void testWikiConstructRTree() {
        LoadDataNoOSM loadDataNoOSM = new LoadDataNoOSM();
        try {
            loadDataNoOSM.wikiConstructRTree(db_path, dataset, entity_path, spatialNodePNPath, 1.0, -1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * make a file that R-Tree Leaf node contains spatial ID.
     */
    @Test
    public void testWikiGenerateContainSpatialID() {
        wikiGenerateContainSpatialID(db_path, dataset, containSpatialIDPath);
    }

    /**
     * make PathNeighbors for R-Tree Leaf Nodes.
     */
    @Test
    public void testWikiConstructPNSingleHopNoGraphDb() {
        String PNPath = data_dir + "/PathNeighbors_" + "Gleenes_1.0_-1_new_version";
        try {
            for (int hop = 0; hop < 3; hop++) {
                wikiConstructPNSingleHopNoGraphDb(containSpatialIDPath, graph_path, in_graph_path, graph_label_path, entity_label_path, hop, PNPath, -1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    @Test
//    public void testBatchRTreeInsertOneHopAware() {
//        LoadDataNoOSM loadDataNoOSM = new LoadDataNoOSM();
//        loadDataNoOSM.batchRTreeInsertOneHopAware(db_path, dataset, graph_path, entity_path, graph_label_path);
//    }
}
