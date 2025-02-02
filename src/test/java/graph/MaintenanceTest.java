package graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import commons.Util;

public class MaintenanceTest {

  // private String dbPath =
  // "D:\\Ubuntu_shared\\GeoMinHop\\data\\Yelp_100\\neo4j-community-3.1.1_Yelp_100_withPN\\data\\databases\\graph.db";
  String dbPath =
      "D:\\Project_Data\\wikidata-20180308-truthy-BETA.nt\\neo4j-community-3.4.12_Gleenes_1.0_40_new_version\\data\\databases\\graph.db";

  private GraphDatabaseService dbservice = null;

  @Before
  public void setUp() throws Exception {
    // dbservice = Neo4jGraphUtility.getDatabaseService(dbPath);
  }

  @After
  public void tearDown() throws Exception {
    if (dbservice != null) {
      dbservice.shutdown();
    }
  }

  @Test
  public void concatenatePnNamesTest() {
    assertEquals(MaintenanceUtil.concatenatePnNames("PN_a", "PN_b_c"), "PN_a_b_c");
  }

  @Test
  public void getReversePnNameTest() {
    assertEquals(MaintenanceUtil.getReversePnName("PN_1_10"), "PN_10_1");
    assertEquals(MaintenanceUtil.getReversePnName("PN_1_10_3"), "PN_3_10_1");
    assertEquals(MaintenanceUtil.getReversePnName("PN_a_b_c_d"), "PN_d_c_b_a");
    assertEquals(MaintenanceUtil.getReversePnName("PN_1"), "PN_1");

  }

  @Test
  public void getPNGeneralTest() {
    int nodeCount = 1000000;
    Transaction tx = dbservice.beginTx();
    int bound = 1;
    Random random = new Random();
    long id = random.nextInt(nodeCount);
    Util.println("id: " + id);
    Node source = dbservice.getNodeById(id);
    Util.println(source.getLabels());
    Map<String, Set<Node>> pns = MaintenanceUtil.getPNGeneral(dbservice, source, bound);
    Util.println(pns);

    id = random.nextInt(nodeCount);
    Util.println("id: " + id);
    source = dbservice.getNodeById(id);
    Util.println(source.getLabels());
    pns = MaintenanceUtil.getPNGeneral(dbservice, source, 2);
    Util.println(pns);

    tx.success();
    tx.close();
  }

  @Test
  public void getPNTest() {
    Transaction tx = dbservice.beginTx();
    ResourceIterator<Node> testNodes = dbservice.findNodes(Label.label("GRAPH_2"));
    Node testNode = null;
    while (testNodes.hasNext()) {
      testNode = testNodes.next();
      break;
    }
    Util.println(testNode);
    HashMap<String, HashSet<Node>> PNMap = MaintenanceUtil.getPN(dbservice, testNode, 2);
    Util.println(PNMap);
    Util.println(PNMap.remove("PN"));
    for (String propertyName : PNMap.keySet()) {
      HashSet<Node> nodes = PNMap.get(propertyName);
      HashSet<Long> idsInMap = new HashSet<>();
      for (Node node : nodes) {
        idsInMap.add(node.getId());
      }
      String labelsString = propertyName.split("PN_")[1];
      String[] labelList = labelsString.split("_");
      String query = "match (n)";
      int i = 0;
      for (String label : labelList) {
        query += String.format("-[:GRAPH_LINK]-(a%d:GRAPH_%s)", i, label);
        i++;
      }
      query += " where id(n) = " + testNode.getId();
      query += String.format(" return id(a%d) as res", i - 1);
      Util.println(query);
      Result result = dbservice.execute(query);
      HashSet<Long> idsFromQuery = new HashSet<>();
      while (result.hasNext()) {
        long id = (Long) (result.next().get("res"));
        idsFromQuery.add(id);
      }
      if (idsInMap.size() != idsFromQuery.size()) {
        Util.println("size different");
      }
      Set<Long> diff = Util.setDiff(idsInMap, idsFromQuery);
      Util.println("diff = " + diff);
    }
    tx.success();
    tx.close();
  }

  @Test
  public void getNodeLabelIdTest() {
    Transaction tx = dbservice.beginTx();
    ResourceIterator<Node> testNodes = dbservice.findNodes(Label.label("GRAPH_2"));
    Node testNode = null;
    while (testNodes.hasNext()) {
      testNode = testNodes.next();
      break;
    }
    Util.println(testNode);
    int labelId = MaintenanceUtil.getNodeLabelId(testNode);
    tx.success();
    tx.close();
    assertEquals(2, labelId);
  }

  @Test
  public void getReversePropertyNameTest() {
    String[] labelStrings = new String[] {"0", "10", "1"};
    assertTrue(MaintenanceUtil.getReversePropertyName(labelStrings).equals("PN_1_10_0"));
  }

}
