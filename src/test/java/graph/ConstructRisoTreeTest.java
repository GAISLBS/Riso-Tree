package graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import commons.Config;
import commons.Enums;
import commons.Labels;
import commons.Labels.RTreeRel;
import commons.Util;

public class ConstructRisoTreeTest {

  static Config config = new Config();
  static String dataset = config.getDatasetName();
  static String version = config.GetNeo4jVersion();
  static Enums.system systemName = config.getSystemName();

  static String db_path;
  static String containIDPath;
  static GraphDatabaseService databaseService;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    switch (systemName) {
      case Ubuntu:
        db_path = String.format(
            "/home/yuhansun/Documents/GeoGraphMatchData/%s_%s/data/databases/graph.db", version,
            dataset);
        containIDPath =
            String.format("/mnt/hgfs/Ubuntu_shared/GeoMinHop/data/%s/containID.txt", dataset);
        break;
      case Windows:
        String dataDirectory = "D:\\gspatial_test\\Riso-Tree\\data";
        db_path = String.format("%s\\%s\\%s_%s\\data\\databases\\graph.db", dataDirectory, dataset,
            version, dataset);
        containIDPath =
            String.format("D:\\gspatial_test\\Riso-Tree\\data\\%s\\containID.txt", dataset);
      default:
        break;
    }

    databaseService = new GraphDatabaseFactory().newEmbeddedDatabase(new File(db_path));
    Util.println("dbpath:" + db_path);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    databaseService.shutdown();
  }

  @Test
  public void constructPNTimeTest() throws Exception {
    ArrayList<Long> constructTime = Construct_RisoTree.constructPNTime();
    Util.println(constructTime);
  }

  /**
   * Use the written file to prove correctness
   * 
   * @throws IOException
   */
  @Test
  public void constructPNTimeContentCorrectnessTest() throws IOException {
    int countTest = 100;
    String filePath1 = "D:\\gspatial_test\\Riso-Tree\\data\\Gowalla_50\\PathNeighbors_2";
    String filePath2 = "D:\\gspatial_test\\Riso-Tree\\data\\Gowalla_50\\PathNeighbors_2_backup";

    BufferedReader reader1 = new BufferedReader(new FileReader(new File(filePath1)));
    BufferedReader reader2 = new BufferedReader(new FileReader(new File(filePath2)));

    String line1 = reader1.readLine();
    String line2 = reader2.readLine();

    assert (line1.equals(line2));

    long nodeID1 = Long.parseLong(line1);
    while (true) {
      Util.println(countTest);
      countTest--;
      if (countTest == 0)
        break;
      HashMap<String, String> PN1 = new HashMap<String, String>();
      HashMap<String, String> PN2 = new HashMap<String, String>();
      while ((line1 = reader1.readLine()) != null) {
        line2 = reader2.readLine();
        if (line1.matches("\\d+$") == false) {
          String[] lineList1 = line1.split(",", 2);
          String key1 = lineList1[0];
          String content1 = lineList1[1];
          PN1.put(key1, content1);

          String[] lineList2 = line1.split(",", 2);
          String key2 = lineList2[0];
          String content2 = lineList2[1];
          PN2.put(key2, content2);
        } else
          break;
      }

      for (String key : PN1.keySet())
        assert (PN1.get(key).equals(PN2.get(key)));

      // break;

      // if ( line1 == null)
      // break;
      // nodeID1 = Long.parseLong(line1);
      // OwnMethods.Print(nodeID1);
      //
      // assert ( line1.equals(line2));

    }

    reader1.close();
    reader2.close();

  }

  @Test
  public void nonLeafContentTest() {
    Transaction tx = databaseService.beginTx();
    ResourceIterator<Node> nodes = databaseService.findNodes(Labels.GraphLabel.GRAPH_1);
    while (nodes.hasNext()) {
      Node node = nodes.next();
      Util.println("node:" + node.getAllProperties());
      Node parent =
          node.getSingleRelationship(RTreeRel.RTREE_REFERENCE, Direction.INCOMING).getStartNode();
      Util.println(parent.getAllProperties());

      // Iterable<Relationship> rels = node.getRelationships();
      // Iterator<Relationship> iterator = rels.iterator();
      // while (iterator.hasNext())
      // {
      // Relationship relationship = iterator.next();
      // OwnMethods.Print(relationship);
      // OwnMethods.Print(relationship.getAllProperties());
      // }

      break;
    }
    tx.success();
    tx.close();
  }

  @Test
  public void readContainIDMapTest() throws Exception {
    HashMap<Long, ArrayList<Integer>> containIDMap =
        Construct_RisoTree.readContainIDMap(containIDPath);
    for (long key : containIDMap.keySet()) {
      Util.println(String.format("%d:%s", key, containIDMap.get(key).toString()));
      break;
    }
  }

  @Test
  public void constructPNTest() {
    long nodeID = 754959;
    Transaction tx = databaseService.beginTx();
    Node node = databaseService.getNodeById(nodeID);
    Map<String, Object> properties = node.getAllProperties();
    for (String key : properties.keySet()) {
      if (key.equals("count") || key.equals("bbox"))
        Util.println(key);

      // if ( key.contains("PN"))
      // {
      // String line = String.format("%s:", key);
      // int[] property = (int[]) properties.get(key);
      // line += "[";
      // for ( int id : property)
      // line += String.format("%d, ", id);
      // line = line.substring(0, line.length() - 2);
      // line += "]";
      // OwnMethods.Print(line);
      // }
    }

    tx.success();
    tx.close();
    databaseService.shutdown();
  }

  @Test
  public void constructNLTest() {
    long nodeID = 1280979;
    Transaction tx = databaseService.beginTx();

    Node node = databaseService.getNodeById(nodeID);
    Map<String, Object> properties = node.getAllProperties();

    // Set<Node> nodes = OSM_Utility.getRTreeNonleafDeepestLevelNodes(databaseService, dataset);
    // Node node = nodes.iterator().next();
    // Map<String, Object> properties = node.getAllProperties();

    int count = 0;
    Util.println(node.getAllProperties());
    // for ( String key : properties.keySet())
    // {
    // if ( key.contains("list"))
    // {
    // count++;
    // OwnMethods.Print(count);
    // String line = String.format("%s:", key);
    // int[] property = (int[]) properties.get(key);
    // line += "[";
    // for ( int id : property)
    // line += String.format("%d, ", id);
    // line = line.substring(0, line.length() - 2);
    // line += "]";
    // OwnMethods.Print(line);
    // }
    // OwnMethods.Print(key);
    // }
    // OwnMethods.Print(properties);


    tx.success();
    tx.close();
    databaseService.shutdown();
  }
}
