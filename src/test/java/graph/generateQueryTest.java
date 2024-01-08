package graph;

import org.junit.Test;

import static experiment.Prepare.generateExperimentCypherQuery;

public class generateQueryTest {
    static String dataset="smallGraph";
    static String dir="D:/gspatial_test/Riso-Tree";
    static String data_dir= dir + "/data/" + dataset;
    static String code_dir= dir;

    static String graph_path= data_dir + "/graph.txt";
    static String entity_path= data_dir + "/entity.txt";
    static String label_path= data_dir + "/graph_label.txt";
    static String labelStrMapPath= data_dir + "/entity_string_label.txt";
    static String spatialNodePNPath= data_dir + "/spatialNodesZeroOneHopPN.txt";
    static String jar_path= code_dir + "/target/Riso-Tree-0.0.1-SNAPSHOT.jar";
    static String selectivitiesStr="0.1";
    static int queryCount=100;
    static int nodeCount=6;
    static String outputDir= dir + "/data/result/query/" + dataset;

    @Test
    public void testGenerateExperimentCypherQuery() {
        try {
            generateExperimentCypherQuery(graph_path, entity_path, label_path, labelStrMapPath, selectivitiesStr, nodeCount, queryCount, outputDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
