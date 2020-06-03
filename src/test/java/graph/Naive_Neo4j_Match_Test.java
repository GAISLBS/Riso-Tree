package graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.Result;
import commons.Config;
import commons.Config.Explain_Or_Profile;
import commons.Config.system;
import commons.MyRectangle;
import commons.OwnMethods;
import commons.Query_Graph;
import commons.Util;

public class Naive_Neo4j_Match_Test {

  static Config config = new Config();
  static String dataset = config.getDatasetName();
  static String version = config.GetNeo4jVersion();
  static system systemName = config.getSystemName();

  static String db_path;
  static String querygraphDir, spaPredicateDir;

  static int nodeCount = 3, query_id = 0, rectID = 2;
  // static int name_suffix = 1280;//Gowalla 0.001
  static int name_suffix = 575;// wikidata_100 0.0001
  // static int name_suffix = 57;//wikidata_100 0.00001
  static String entityPath, querygraph_path, queryrectCenterPath, queryrect_path;

  // query input
  static Query_Graph query_Graph;

  @BeforeClass
  public static void setUpBeforeClass() throws java.lang.Exception {
    switch (systemName) {
      case Ubuntu:
        db_path = String.format(
            "/home/yuhansun/Documents/GeoGraphMatchData/%s_%s/data/databases/graph.db", version,
            dataset);
        entityPath = String.format("/mnt/hgfs/Ubuntu_shared/GeoMinHop/data/%s/entity.txt", dataset);
        querygraphDir =
            String.format("/mnt/hgfs/Google_Drive/Projects/risotree/query/query_graph/%s", dataset);
        spaPredicateDir = String
            .format("/mnt/hgfs/Google_Drive/Projects/risotree/query/spa_predicate/%s", dataset);
        querygraph_path = String.format("%s/%d.txt", querygraphDir, nodeCount);
        queryrectCenterPath = String.format("%s/%s_centerids.txt", spaPredicateDir, dataset);
        queryrect_path = String.format("%s/queryrect_%d.txt", spaPredicateDir, name_suffix);
        break;
      case Windows:
        String dataDirectory = "D:\\Ubuntu_shared\\GeoMinHop\\data";
        db_path = String.format("%s\\%s\\%s_%s\\data\\databases\\graph.db", dataDirectory, dataset,
            version, dataset);
        entityPath = String.format("D:\\Ubuntu_shared\\GeoMinHop\\data\\%s\\entity.txt", dataset);
        querygraphDir =
            String.format("D:\\Google_Drive\\Projects\\risotree\\query\\query_graph\\%s", dataset);
        spaPredicateDir = String
            .format("D:\\Google_Drive\\Projects\\risotree\\query\\spa_predicate\\%s", dataset);
        querygraph_path = String.format("%s\\%d.txt", querygraphDir, nodeCount);
        queryrectCenterPath = String.format("%s\\%s_centerids.txt", spaPredicateDir, dataset);
        queryrect_path = String.format("%s\\queryrect_%d.txt", spaPredicateDir, name_suffix);
      default:
        break;
    }
    iniQueryInput();
  }

  @AfterClass
  public static void tearDownAfterClass() throws java.lang.Exception {}

  public static void iniQueryInput() {
    ArrayList<Query_Graph> queryGraphs = Util.ReadQueryGraph_Spa(querygraph_path, query_id + 1);
    query_Graph = queryGraphs.get(query_id);

    // ArrayList<MyRectangle> queryrect = OwnMethods.ReadQueryRectangle(queryrect_path);
    //
    // ArrayList<Integer> centerIDs = OwnMethods.readIntegerArray(queryrectCenterPath);
    // ArrayList<Entity> entities = OwnMethods.ReadEntity(entityPath);
    // ArrayList<MyRectangle> queryrect = new ArrayList<MyRectangle>();
    // for ( int id : centerIDs)
    // {
    // Entity entity = entities.get(id);
    // queryrect.add(new MyRectangle(entity.lon, entity.lat, entity.lon, entity.lat));
    // }

    // MyRectangle rectangle = queryrect.get(0);
    // int j = 0;
    // for ( ; j < query_Graph.graph.size(); j++)
    // if(query_Graph.Has_Spa_Predicate[j])
    // break;
    // query_Graph.spa_predicate[j] = rectangle;
  }

  @Test
  public void queryTest() {
    String queryrect_path = null, querygraph_path = null;
    switch (systemName) {
      case Ubuntu:
        querygraph_path = String.format("%s/%d.txt", querygraphDir, nodeCount);
        queryrect_path = String.format("%s/queryrect_%d.txt", spaPredicateDir, name_suffix);
        break;
      case Windows:
        querygraph_path = String.format("%s\\%d.txt", querygraphDir, nodeCount);
        queryrect_path = String.format("%s\\queryrect_%d.txt", spaPredicateDir, name_suffix);
        break;
    }

    ArrayList<Query_Graph> queryGraphs = Util.ReadQueryGraph_Spa(querygraph_path, query_id + 1);
    Query_Graph query_Graph = queryGraphs.get(query_id);

    ArrayList<MyRectangle> queryrect = OwnMethods.ReadQueryRectangle(queryrect_path);
    MyRectangle rectangle = queryrect.get(rectID);
    int j = 0;
    for (; j < query_Graph.graph.size(); j++)
      if (query_Graph.Has_Spa_Predicate[j])
        break;
    query_Graph.spa_predicate[j] = rectangle;

    Util.println(query_Graph);
    Util.println(query_Graph.spa_predicate);
    Util.println("database path: " + db_path);
    if (!OwnMethods.pathExist(db_path)) {
      Util.println(db_path + " does not exist!");
      System.exit(-1);
    }
    Naive_Neo4j_Match naive_Neo4j_Match = new Naive_Neo4j_Match(db_path);
    Result result = naive_Neo4j_Match.SubgraphMatch_Spa_API(query_Graph, -1);
    int count = 0;
    while (result.hasNext()) {
      count++;
      Map<String, Object> row = result.next();
      // OwnMethods.Print(result.next());
    }
    Util.println(count);
  }

  @Test
  public void graphOnlyQueryTest() {
    int nodeCount = 5, query_id = 0;

    String querygraph_path =
        "/mnt/hgfs/Google_Drive/Projects/risotree/query/query_graph/labelCount/Gowalla_25/5.txt";

    ArrayList<Query_Graph> queryGraphs = Util.ReadQueryGraph_Spa(querygraph_path, query_id + 1);
    Query_Graph query_Graph = queryGraphs.get(query_id);

    int j = 0;
    for (; j < query_Graph.graph.size(); j++)
      if (query_Graph.Has_Spa_Predicate[j])
        break;
    query_Graph.spa_predicate[j] = null;
    query_Graph.Has_Spa_Predicate[j] = false;

    Naive_Neo4j_Match naive_Neo4j_Match = new Naive_Neo4j_Match(db_path);
    Result result = naive_Neo4j_Match.SubgraphMatch_Spa_API(query_Graph, 100);
    while (result.hasNext())
      Util.println(result.next());
    Util.println(result.getExecutionPlanDescription());
  }

  @Test
  public void formQueryJoinTest() {
    double distance = 0.1;
    Naive_Neo4j_Match naive_Neo4j_Match = new Naive_Neo4j_Match(db_path);
    OwnMethods.convertQueryGraphForJoinRandom(query_Graph);
    Util.println(query_Graph);
    ArrayList<Integer> pos = new ArrayList<>();
    for (int i = 0; i < query_Graph.Has_Spa_Predicate.length; i++)
      if (query_Graph.Has_Spa_Predicate[i])
        pos.add(i);
    String query =
        naive_Neo4j_Match.formQueryJoin(query_Graph, pos, distance, Explain_Or_Profile.Profile);
    Util.println(query);
  }

  @Test
  public void LAGAQ_JoinTest() {
    double distance = 0.00001;
    Naive_Neo4j_Match naive_Neo4j_Match = new Naive_Neo4j_Match(db_path);
    OwnMethods.convertQueryGraphForJoinRandom(query_Graph);
    Util.println(query_Graph);
    long start = System.currentTimeMillis();
    List<long[]> res = naive_Neo4j_Match.LAGAQ_Join(query_Graph, distance);
    long time = System.currentTimeMillis() - start;
    Util.println("distance: " + distance);
    Util.println("total time: " + time);
    Util.println("get iterator time: " + naive_Neo4j_Match.get_iterator_time);
    Util.println("iterate time: " + naive_Neo4j_Match.iterate_time);
    Util.println("result count: " + naive_Neo4j_Match.result_count);
    Util.println("result count: " + res.size());
    naive_Neo4j_Match.neo4j_API.ShutDown();
  }
}
