package graph;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.HashSet;
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
import commons.Neo4jGraphUtility;
import commons.Utility;

public class MaintenanceTest {

  private String dbPath =
      "D:\\Ubuntu_shared\\GeoMinHop\\data\\Yelp_100\\neo4j-community-3.1.1_Yelp_100_withPN\\data\\databases\\graph.db";
  private GraphDatabaseService dbservice = null;

  @Before
  public void setUp() throws Exception {
    dbservice = Neo4jGraphUtility.getDatabaseService(dbPath);
  }

  @After
  public void tearDown() throws Exception {
    dbservice.shutdown();
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
    Utility.print(testNode);
    HashMap<String, HashSet<Node>> PNMap = Maintenance.getPN(dbservice, testNode, 2);
    Utility.print(PNMap);
    Utility.print(PNMap.remove("PN"));
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
      Utility.print(query);
      Result result = dbservice.execute(query);
      HashSet<Long> idsFromQuery = new HashSet<>();
      while (result.hasNext()) {
        long id = (Long) (result.next().get("res"));
        idsFromQuery.add(id);
      }
      if (idsInMap.size() != idsFromQuery.size()) {
        Utility.print("size different");
      }
      Set<Long> diff = Utility.setDiff(idsInMap, idsFromQuery);
      Utility.print("diff = " + diff);
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
    Utility.print(testNode);
    int labelId = Maintenance.getNodeLabelId(testNode);
    tx.success();
    tx.close();
    assertEquals(2, labelId);
  }

  @Test
  public void getReversePropertyNameTest() {
    String[] labelStrings = new String[] {"0", "10", "1"};
    String res = Maintenance.getReversePropertyName(labelStrings);
    Utility.print(res);
  }

}