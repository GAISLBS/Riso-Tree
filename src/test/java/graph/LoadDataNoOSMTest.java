package graph;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import commons.Config;
import commons.ReadWriteUtil;
import commons.Enums;
import commons.Enums.system;
import commons.Util;

public class LoadDataNoOSMTest {

  static Config config = new Config();
  static String dataset = config.getDatasetName();
  static String version = config.GetNeo4jVersion();
  static Enums.system systemName = config.getSystemName();

  static String db_path, graph_pos_map_path;
  static String querygraphDir, spaPredicateDir;
  static GraphDatabaseService databaseService;

  static int nodeCount = 7, query_id = 0;
  // static int name_suffix = 7;
  // static int name_suffix = 75;
  // static int name_suffix = 7549;//0.1
  static int name_suffix = 75495;// 0.1
  static String queryrect_path = null, querygraph_path = null;

  String homeDir = null;
  String dataDir = null;
  String entityPath = null;

  @Before
  public void setUp() throws Exception {
    // switch (systemName) {
    // case Ubuntu:
    // db_path = String.format(
    // "/home/yuhansun/Documents/GeoGraphMatchData/%s_%s/data/databases/graph.db", version,
    // dataset);
    // graph_pos_map_path =
    // "/mnt/hgfs/Ubuntu_shared/GeoMinHop/data/" + dataset + "/node_map_RTree.txt";
    // querygraphDir =
    // String.format("/mnt/hgfs/Google_Drive/Projects/risotree/query/query_graph/%s", dataset);
    // spaPredicateDir = String
    // .format("/mnt/hgfs/Google_Drive/Projects/risotree/query/spa_predicate/%s", dataset);
    // querygraph_path = String.format("%s/%d.txt", querygraphDir, nodeCount);
    // queryrect_path = String.format("%s/queryrect_%d.txt", spaPredicateDir, name_suffix);
    // break;
    // case Windows:
    // String dataDirectory = "D:\\Ubuntu_shared\\GeoMinHop\\data";
    // db_path = String.format("%s\\%s\\%s_%s\\data\\databases\\graph.db", dataDirectory, dataset,
    // version, dataset);
    // graph_pos_map_path =
    // "D:\\Ubuntu_shared\\GeoMinHop\\data\\" + dataset + "\\node_map_RTree.txt";
    // querygraphDir =
    // String.format("D:\\Google_Drive\\Projects\\risotree\\query\\query_graph\\%s", dataset);
    // spaPredicateDir = String
    // .format("D:\\Google_Drive\\Projects\\risotree\\query\\spa_predicate\\%s", dataset);
    // querygraph_path = String.format("%s\\%d.txt", querygraphDir, nodeCount);
    // queryrect_path = String.format("%s\\queryrect_%d.txt", spaPredicateDir, name_suffix);
    // default:
    // break;
    // }

    ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource("").getFile());
    homeDir = file.getAbsolutePath();
    dataDir = homeDir + "/data/Yelp";
    db_path = dataDir + "/test.db";
    entityPath = dataDir + "/entity.txt";

    // databaseService = new GraphDatabaseFactory().newEmbeddedDatabase(new File(db_path));
  }

  @After
  public void tearDown() throws Exception {
    // databaseService.shutdown();
  }

  @Test
  public void constructRTreeWikidataTest() throws Exception {
    LoadDataNoOSM loadDataNoOSM = new LoadDataNoOSM(new Config(), true);
    loadDataNoOSM.loadSpatialEntity(db_path, entityPath);
    loadDataNoOSM.wikiConstructRTree(db_path, dataset, entityPath);
  }

  @Test
  public void nodeMapTest() {
    Transaction tx = databaseService.beginTx();
    Node node = databaseService.getNodeById(78589);
    Util.println(node);
    Util.println(node.getAllProperties());

    node = databaseService.getNodeById(10353);
    Util.println(node);
    Util.println(node.getAllProperties());

    tx.success();
    tx.close();
  }

  @Test
  public void readSpatialNodesPNTest() throws Exception {
    String dir = "D:\\Project_Data\\wikidata-20180308-truthy-BETA.nt";
    String path = dir + "\\spatialNodesZeroOneHopPN.txt";
    List<Map<String, int[]>> spatialNodesPN = LoadDataNoOSM.readSpatialNodesPN(path);
    int index = 0;
    int id = -1;
    for (Map<String, int[]> pn : spatialNodesPN) {
      id++;
      if (pn == null) {
        continue;
      }
      ReadWriteUtil.WriteFile(dir + "\\temp.txt", true, id + "\n");
      ReadWriteUtil.WriteFile(dir + "\\temp.txt", true, pn.toString() + "\n");
      index++;
      if (index == 10) {
        return;
      }
    }
  }

}
