package graph;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.logging.Logger;
import org.neo4j.gis.spatial.rtree.RTreeRelationshipTypes;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.ExecutionPlanDescription;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import commons.Config;
import commons.Enums;
import commons.Enums.QueryStatistic;
import commons.Enums.QueryType;
import commons.Labels;
import commons.Labels.RTreeRel;
import commons.MyPoint;
import commons.MyRectangle;
import commons.Neo4jGraphUtility;
import commons.OwnMethods;
import commons.Query_Graph;
import commons.RTreeUtility;
import commons.Util;
import cypher.middleware.CypherDecoder;
import knn.Element;
import knn.KNNComparator;
import knn.NodeAndRec;

/**
 * Implements SpatialFirst with NL_list. Two versions are implemented, simple version and blocked
 * version. Simple version proves to be same with SpatialFirst class approach. Blocked version is
 * better than SpatialFirst approach. It is used in our experiment.
 * 
 * @author yuhansun
 *
 */
public class SpatialFirst_List {

  public GraphDatabaseService dbservice;
  public String dataset;
  public long[] graph_pos_map_list;

  public static Config config = new Config();
  public static String lon_name = config.GetLongitudePropertyName();
  public static String lat_name = config.GetLatitudePropertyName();
  public static int MAX_HOPNUM = config.getMaxHopNum();
  public static String graphLinkLabelName = Labels.GraphRel.GRAPH_LINK.name();
  public static Enums.system systemName = config.getSystemName();
  public final static String BBoxName = config.BBoxName;

  // query statistics >>
  public long run_time;
  public long get_iterator_time;
  public long iterate_time;
  public long result_count;
  public long page_hit_count;

  // LAGAQ-range query track >>
  public long range_query_time;
  public long overlap_leaf_count;
  public long candidate_count;

  // LAGAQ-knn query track >>
  /**
   * Spatial index search time.
   */
  public long queue_time;
  public int visit_spatial_object_count;

  // LAGAQ-join track >>
  /**
   * The number of id pairs that satisfy the join predicate.
   */
  public long join_result_count;
  /**
   * The time of searching the spatial index.
   */
  public long join_time;


  public ExecutionPlanDescription planDescription;

  public Map<QueryStatistic, Object> queryStatisticMap = new HashMap<>();

  private static final Logger LOGGER = Logger.getLogger(SpatialFirst_List.class.getName());

  /**
   * 
   * @param db_path database location
   * @param p_dataset used for osm and rtree entry
   * @param p_graph_pos_map map from graph id to neo4j pos id
   */
  public SpatialFirst_List(String db_path, String p_dataset, long[] p_graph_pos_map) {
    dbservice = new GraphDatabaseFactory().newEmbeddedDatabase(new File(db_path));
    dataset = p_dataset;
    graph_pos_map_list = p_graph_pos_map;
  }

  public SpatialFirst_List(GraphDatabaseService service, String dataset) {
    this.dbservice = service;
    this.dataset = dataset;
  }

  public void shutdown() {
    dbservice.shutdown();
  }

  /**
   * Set all tracking variables to 0.
   */
  private void clearTrackingVariables() {
    run_time = 0;
    range_query_time = 0;
    get_iterator_time = 0;
    iterate_time = 0;
    overlap_leaf_count = 0;
    candidate_count = 0;
    result_count = 0;
    page_hit_count = 0;
    queue_time = 0;
    visit_spatial_object_count = 0;
    join_result_count = 0;
    join_time = 0;
  }


  public static int[][] Ini_Minhop(Query_Graph query_Graph) {
    int query_node_count = query_Graph.graph.size();
    int[][] minhop_index = new int[query_node_count][];

    for (int i = 0; i < query_node_count; i++) {
      if (query_Graph.spa_predicate[i] == null)
        minhop_index[i] = null;
      else
        minhop_index[i] = new int[query_node_count];
    }

    for (int i = 0; i < query_node_count; i++) {
      if (query_Graph.spa_predicate[i] != null) {
        boolean[] visited = new boolean[query_node_count];
        visited[i] = true;
        minhop_index[i][i] = -1;

        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(i);
        int pre_level_count = 1;
        int cur_level_count = 0;
        int level_index = 1;

        while (queue.isEmpty() == false) {
          for (int j = 0; j < pre_level_count; j++) {
            int node = queue.poll();
            for (int k = 0; k < query_Graph.graph.get(node).size(); k++) {
              int neighbor = query_Graph.graph.get(node).get(k);
              if (visited[neighbor] == false) {
                minhop_index[i][neighbor] = level_index;
                visited[neighbor] = true;
                cur_level_count += 1;
                queue.add(neighbor);
              }
            }
          }
          level_index++;
          pre_level_count = cur_level_count;
          cur_level_count = 0;
        }
      }
    }

    // minhop_index[2][0] = -1; minhop_index[2][3] = -1;
    return minhop_index;
  }

  /**
   * Only returns the rtree leaf nodes. Require one more level filtering.
   * 
   * @param root_node
   * @param query_rectangle
   * @return
   */
  public LinkedList<Node> rangeQuery(Node root_node, MyRectangle query_rectangle) {
    try {
      LinkedList<Node> cur_list = new LinkedList<Node>();
      cur_list.add(root_node);

      int level_index = 0;
      while (cur_list.isEmpty() == false) {
        long start = System.currentTimeMillis();
        // OwnMethods.Print(String.format("level %d", level_index));
        LinkedList<Node> next_list = new LinkedList<Node>();
        LinkedList<Node> overlap_MBR_list = new LinkedList<Node>();

        for (Node node : cur_list) {
          if (node.hasProperty(BBoxName)) {
            double[] bbox = (double[]) node.getProperty(BBoxName);
            MyRectangle MBR = new MyRectangle(bbox[0], bbox[1], bbox[2], bbox[3]);
            if (query_rectangle.intersect(MBR) != null) {
              overlap_MBR_list.add(node);

              Iterable<Relationship> rels =
                  node.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
              for (Relationship relationship : rels)
                next_list.add(relationship.getEndNode());
            }
          } else
            throw new Exception(String.format("node %d does not has \"bbox\" property", node));
        }

        // OwnMethods.Print(String.format("level %d time: %d", level_index,
        // System.currentTimeMillis() - start));

        if (next_list.isEmpty())
          return overlap_MBR_list;

        cur_list = next_list;
        level_index++;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * form the cypher query
   * 
   * @param query_Graph
   * @param limit -1 is no limit
   * @param explain_Or_Profile
   * @param spa_predicates spatial predicates except for the min_pos spatial predicate
   * @param pos query graph node id with the trigger spatial predicate
   * @param id corresponding spatial graph node id (neo4j pos id)
   * @param NL_hopnum shrunk query node <query_graph_id, hop_num>
   * @param node the rtree node stores NL_list information
   * @return
   */
  public String formSubgraphQuery(Query_Graph query_Graph, int limit,
      Enums.Explain_Or_Profile explain_Or_Profile, HashMap<Integer, MyRectangle> spa_predicates,
      int pos, long id, HashMap<Integer, Integer> NL_hopnum, Node node) {
    String query = "";
    switch (explain_Or_Profile) {
      case Profile:
        query += "profile match ";
        break;
      case Explain:
        query += "explain match ";
        break;
      case Nothing:
        query += "match ";
        break;
    }

    // label
    query += String.format("(a0:GRAPH_%d)", query_Graph.label_list[0]);
    for (int i = 1; i < query_Graph.graph.size(); i++) {
      query += String.format(",(a%d:GRAPH_%d)", i, query_Graph.label_list[i]);
    }

    // edge
    for (int i = 0; i < query_Graph.graph.size(); i++) {
      for (int j = 0; j < query_Graph.graph.get(i).size(); j++) {
        int neighbor = query_Graph.graph.get(i).get(j);
        if (neighbor > i)
          query += String.format(",(a%d)-[:%s]-(a%d)", i, graphLinkLabelName, neighbor);
      }
    }

    query += " \n where ";

    // spatial predicate
    for (int key : spa_predicates.keySet()) {
      MyRectangle qRect = spa_predicates.get(key);
      query += String.format(" %f <= a%d.%s <= %f ", qRect.min_x, key, lon_name, qRect.max_x);
      query += String.format("and %f <= a%d.%s <= %f and", qRect.min_y, key, lat_name, qRect.max_y);
    }

    // id
    // query += String.format(" id(a%d) in [%d]", pos, id);
    query += String.format(" \nid(a%d) = %d\n", pos, id);

    // NL_id_list
    for (int key : NL_hopnum.keySet()) {
      String id_list_property_name =
          String.format("NL_%d_%d_list", NL_hopnum.get(key), query_Graph.label_list[key]);

      if (node.hasProperty(id_list_property_name) == false) {
        query = "match (n) where false return n";
        return query;
      }

      int[] graph_id_list = (int[]) node.getProperty(id_list_property_name);
      ArrayList<Long> pos_id_list = new ArrayList<Long>(graph_id_list.length);
      for (int i = 0; i < graph_id_list.length; i++)
        pos_id_list.add(graph_pos_map_list[graph_id_list[i]]);

      query += String.format(" and ( id(a%d) = %d", key, pos_id_list.get(0));
      for (int i = 1; i < pos_id_list.size(); i++)
        query += String.format(" or id(a%d) = %d", key, pos_id_list.get(i));
      query += " )\n";
      // query += String.format(" and id(a%d) in %s", key, pos_id_list.toString());
    }

    // return
    query += " return id(a0)";
    for (int i = 1; i < query_Graph.graph.size(); i++)
      query += String.format(",id(a%d)", i);

    if (limit != -1)
      query += String.format(" limit %d", limit);

    return query;
  }

  /**
   * For each spatial vertex, run a cypher query using NL_list. (Not used)
   * 
   * @param query_Graph
   * @param limit
   */
  public void query(Query_Graph query_Graph, int limit) {
    try {
      clearTrackingVariables();

      long start = System.currentTimeMillis();
      Transaction tx = dbservice.beginTx();

      int[][] min_hop = Ini_Minhop(query_Graph);

      // <spa_id, rectangle>
      HashMap<Integer, MyRectangle> spa_predicates = new HashMap<Integer, MyRectangle>();
      for (int i = 0; i < query_Graph.Has_Spa_Predicate.length; i++)
        if (query_Graph.Has_Spa_Predicate[i])
          spa_predicates.put(i, query_Graph.spa_predicate[i]);

      int min_pos = 0;
      MyRectangle min_queryRectangle = null;
      for (int key : spa_predicates.keySet())
        if (min_queryRectangle == null) {
          min_pos = key;
          min_queryRectangle = spa_predicates.get(key);
        } else {
          if (spa_predicates.get(key).area() < min_queryRectangle.area()) {
            min_pos = key;
            min_queryRectangle = spa_predicates.get(key);
          }
        }
      spa_predicates.remove(min_pos, min_queryRectangle);

      // query vertex to be shrunk <id, hop_num>
      // calculated from the min_pos
      HashMap<Integer, Integer> NL_hopnum = new HashMap<Integer, Integer>();
      for (int i = 0; i < query_Graph.graph.size(); i++)
        if (min_hop[min_pos][i] <= MAX_HOPNUM && min_hop[min_pos][i] > 0)
          NL_hopnum.put(i, min_hop[min_pos][i]);

      long start_1 = System.currentTimeMillis();
      Node rootNode = RTreeUtility.getRTreeRoot(dbservice, dataset);
      LinkedList<Node> rangeQueryResult = this.rangeQuery(rootNode, min_queryRectangle);
      range_query_time = System.currentTimeMillis() - start_1;

      int located_in_count = 0;
      for (Node rtree_node : rangeQueryResult) {
        start_1 = System.currentTimeMillis();
        Iterable<Relationship> rels =
            rtree_node.getRelationships(Direction.OUTGOING, RTreeRelationshipTypes.RTREE_REFERENCE);
        range_query_time += System.currentTimeMillis() - start_1;

        for (Relationship relationship : rels) {
          start_1 = System.currentTimeMillis();
          Node geom = relationship.getEndNode();
          double[] bbox = (double[]) geom.getProperty(BBoxName);
          MyRectangle bbox_rect = new MyRectangle(bbox);
          if (min_queryRectangle.intersect(bbox_rect) != null) {
            located_in_count++;
            // Node node = geom.getSingleRelationship(OSMRelation.GEOM,
            // Direction.INCOMING).getStartNode();
            // long id = node.getId();
            long id = geom.getId();
            range_query_time += System.currentTimeMillis() - start_1;

            start_1 = System.currentTimeMillis();
            String query = formSubgraphQuery(query_Graph, limit, Enums.Explain_Or_Profile.Profile,
                spa_predicates, min_pos, id, NL_hopnum, rtree_node);

            Result result = dbservice.execute(query);
            get_iterator_time += System.currentTimeMillis() - start_1;

            start_1 = System.currentTimeMillis();
            int cur_count = 0;
            while (result.hasNext()) {
              cur_count++;
              result.next();
              // Map<String, Object> row = result.next();
              // String str = row.toString();
              // OwnMethods.Print(row.toString());
            }
            iterate_time += System.currentTimeMillis() - start_1;

            if (cur_count != 0) {
              ExecutionPlanDescription planDescription = result.getExecutionPlanDescription();
              ExecutionPlanDescription.ProfilerStatistics profile =
                  planDescription.getProfilerStatistics();
              result_count += profile.getRows();
              page_hit_count += OwnMethods.GetTotalDBHits(planDescription);
              // OwnMethods.Print(planDescription);
              Util.println(String.format("%d, %d", id, cur_count));
            }
          } else {
            range_query_time += System.currentTimeMillis() - start_1;
          }
        }
      }

      tx.success();
      tx.close();
      Util.println(String.format("locate in count:%d", located_in_count));
      // OwnMethods.Print(String.format("result size: %d", result_count));
      // OwnMethods.Print(String.format("time: %d", System.currentTimeMillis() - start));
      // return result;
    } catch (Exception e) {
      e.printStackTrace();
    }
    // return null;
  }

  /**
   * form the cypher query for MBR block
   * 
   * @param query_Graph
   * @param limit -1 is no limit
   * @param explain_Or_Profile -1 is Explain; 1 is Profile; the rest is nothing
   * @param spa_predicates spatial predicates except the min_pos spatial predicate
   * @param pos query graph node id with the trigger spatial predicate
   * @param ids corresponding spatial graph node ids that in this MBR (neo4j pos id)
   * @return
   */
  public String formSubgraphQuery_Block_New(Query_Graph query_Graph, int limit,
      Enums.Explain_Or_Profile explain_Or_Profile, HashMap<Integer, MyRectangle> spa_predicates,
      int pos, ArrayList<Long> ids) {
    String query = "";
    switch (explain_Or_Profile) {
      case Profile:
        query += "profile match ";
        break;
      case Explain:
        query += "explain match ";
        break;
      case Nothing:
        query += "match ";
        break;
    }

    // label
    for (int i = 0; i < query_Graph.graph.size(); i++) {
      if (i == 0) {
        query += String.format("(a%d:`%s`)", i, query_Graph.label_list_string[i]);
      } else {
        query += String.format(",(a%d:`%s`)", i, query_Graph.label_list_string[i]);
      }
    }

    // edge
    for (int i = 0; i < query_Graph.graph.size(); i++) {
      for (int j = 0; j < query_Graph.graph.get(i).size(); j++) {
        int neighbor = query_Graph.graph.get(i).get(j);
        if (neighbor > i) {
          query += String.format(",(a%d)--(a%d)", i, neighbor);
        }
      }
    }

    query += " where\n";

    // spatial predicate
    for (int key : spa_predicates.keySet()) {
      if (key == pos) {
        continue;
      }
      MyRectangle qRect = spa_predicates.get(key);
      query += String.format(" %f <= a%d.%s <= %f ", qRect.min_x, key, lon_name, qRect.max_x);
      query += String.format("and %f <= a%d.%s <= %f and", qRect.min_y, key, lat_name, qRect.max_y);
    }

    query += "\n";

    // id
    query += String.format(" (id(a%d)=%d", pos, ids.get(0));
    if (ids.size() > 1)
      for (int i = 1; i < ids.size(); i++)
        query += String.format(" or id(a%d)=%d", pos, ids.get(i));
    // use id(n) = n1 or id(n) = n2... because it provides a better selectivity estimation
    // id(n) in [] will always return the same cost estimation no matter how many ids in [].
    // query += String.format(" id(a%d) in %s\n", pos, ids.toString());

    query += ")\n";
    // Util.println(String.format("spa_ids size: %d", ids.size()));

    // return
    query += " return id(a0)";
    for (int i = 1; i < query_Graph.graph.size(); i++)
      query += String.format(",id(a%d)", i);

    if (limit != -1)
      query += String.format(" limit %d", limit);

    return query;
  }

  /**
   * form the cypher query for MBR block
   * 
   * @param query_Graph
   * @param limit -1 is no limit
   * @param explain_Or_Profile -1 is Explain; 1 is Profile; the rest is nothing
   * @param spa_predicates spatial predicates except the min_pos spatial predicate
   * @param pos query graph node id with the trigger spatial predicate
   * @param ids corresponding spatial graph node ids that in this MBR (neo4j pos id)
   * @param NL_hopnum shrunk query node <query_graph_id, hop_num>
   * @param node the rtree node stores NL_list information
   * @return
   */
  public String formSubgraphQuery_Block(Query_Graph query_Graph, int limit,
      Enums.Explain_Or_Profile explain_Or_Profile, HashMap<Integer, MyRectangle> spa_predicates,
      int pos, ArrayList<Long> ids, HashMap<Integer, Integer> NL_hopnum, Node node) {
    String query = "";
    switch (explain_Or_Profile) {
      case Profile:
        query += "profile match ";
        break;
      case Explain:
        query += "explain match ";
        break;
      case Nothing:
        query += "match ";
        break;
    }

    // label
    if (pos == 0 || NL_hopnum.containsKey(0))
      query += "(a0)";
    else
      query += String.format("(a0:GRAPH_%d)", query_Graph.label_list[0]);
    for (int i = 1; i < query_Graph.graph.size(); i++) {
      if (pos == i || NL_hopnum.containsKey(i))
        query += String.format(",(a%d)", i);
      else
        query += String.format(",(a%d:GRAPH_%d)", i, query_Graph.label_list[i]);
    }

    // edge
    for (int i = 0; i < query_Graph.graph.size(); i++) {
      for (int j = 0; j < query_Graph.graph.get(i).size(); j++) {
        int neighbor = query_Graph.graph.get(i).get(j);
        if (neighbor > i)
          query += String.format(",(a%d)-[:%s]-(a%d)", i, graphLinkLabelName, neighbor);
      }
    }

    query += " where\n";

    // spatial predicate
    for (int key : spa_predicates.keySet()) {
      MyRectangle qRect = spa_predicates.get(key);
      query += String.format(" %f <= a%d.%s <= %f ", qRect.min_x, key, lon_name, qRect.max_x);
      query += String.format("and %f <= a%d.%s <= %f and", qRect.min_y, key, lat_name, qRect.max_y);
    }

    query += "\n";

    // id
    query += String.format(" (id(a%d)=%d", pos, ids.get(0));
    if (ids.size() > 1)
      for (int i = 1; i < ids.size(); i++)
        query += String.format(" or id(a%d)=%d", pos, ids.get(i));
    // query += String.format(" id(a%d) in %s\n", pos, ids.toString());

    query += ")\n";
    Util.println(String.format("spa_ids size: %d", ids.size()));

    // NL_id_list
    // for ( int key : NL_hopnum.keySet())
    // {
    // String id_list_size_property_name = String.format("NL_%d_%d_size", NL_hopnum.get(key),
    // query_Graph.label_list[key]);
    // int id_list_size = (Integer) node.getProperty(id_list_size_property_name);
    // if( id_list_size > 0) //whether to use the shrunk label
    // query = query.replaceFirst(String.format("a%d", key), String.format("a%d:GRAPH_%d", key,
    // query_Graph.label_list[key]));
    // else {
    // String id_list_property_name = String.format("NL_%d_%d_list", NL_hopnum.get(key),
    // query_Graph.label_list[key]);
    //
    // if ( node.hasProperty(id_list_property_name) == false)
    // return "match (n) where false return n";
    //
    // int[] graph_id_list = (int[]) node.getProperty(id_list_property_name);
    // ArrayList<Long> pos_id_list = new ArrayList<Long>(graph_id_list.length);
    // for ( int i = 0; i < graph_id_list.length; i++)
    // pos_id_list.add(graph_pos_map_list[graph_id_list[i]]);
    //
    // query += String.format(" and ( id(a%d) = %d", key, pos_id_list.get(0));
    // for ( int i = 1; i < pos_id_list.size(); i++)
    // query += String.format(" or id(a%d) = %d", key, pos_id_list.get(i));
    // query += " )\n";
    //// query += String.format(" and id(a%d) in %s\n", key, pos_id_list.toString());
    // OwnMethods.Print(String.format("%s size is %d", id_list_property_name, pos_id_list.size()));
    // }
    // }

    // return
    query += " return id(a0)";
    for (int i = 1; i < query_Graph.graph.size(); i++)
      query += String.format(",id(a%d)", i);

    if (limit != -1)
      query += String.format(" limit %d", limit);

    return query;
  }

  public void query_Block(String query) throws Exception {
    Query_Graph query_Graph = CypherDecoder.getQueryGraph(query, dbservice);
    query_Block(query_Graph, -1);
  }

  /**
   * for spatial vertices in the same MBR run a cypher query using NL_list the one used in
   * experiment
   * 
   * @param query_Graph
   * @param limit
   */
  public void query_Block(Query_Graph query_Graph, int limit) {
    try {
      clearTrackingVariables();
      long totalStart = System.currentTimeMillis();
      Transaction tx = dbservice.beginTx();

      int[][] min_hop = Ini_Minhop(query_Graph);

      // <spa_id, rectangle>
      HashMap<Integer, MyRectangle> spa_predicates = new HashMap<Integer, MyRectangle>();
      for (int i = 0; i < query_Graph.Has_Spa_Predicate.length; i++)
        if (query_Graph.Has_Spa_Predicate[i])
          spa_predicates.put(i, query_Graph.spa_predicate[i]);

      int min_pos = 0;
      MyRectangle min_queryRectangle = null;
      for (int key : spa_predicates.keySet())
        if (min_queryRectangle == null) {
          min_pos = key;
          min_queryRectangle = spa_predicates.get(key);
        } else {
          if (spa_predicates.get(key).area() < min_queryRectangle.area()) {
            min_pos = key;
            min_queryRectangle = spa_predicates.get(key);
          }
        }
      spa_predicates.remove(min_pos, min_queryRectangle);

      // query vertex to be shrunk <id, hop_num>
      // calculated from the min_pos
      HashMap<Integer, Integer> NL_hopnum = new HashMap<Integer, Integer>();
      for (int i = 0; i < query_Graph.graph.size(); i++)
        if (min_hop[min_pos][i] <= MAX_HOPNUM && min_hop[min_pos][i] > 0)
          NL_hopnum.put(i, min_hop[min_pos][i]);

      long start_1 = System.currentTimeMillis();
      Node rootNode = RTreeUtility.getRTreeRoot(dbservice, dataset);
      Util.println("query range: " + min_queryRectangle);
      LinkedList<Node> rangeQueryResult = this.rangeQuery(rootNode, min_queryRectangle);
      range_query_time = System.currentTimeMillis() - start_1;
      overlap_leaf_count += rangeQueryResult.size();

      int located_in_count = 0;
      for (Node rtree_node : rangeQueryResult) {
        start_1 = System.currentTimeMillis();
        Iterable<Relationship> rels =
            rtree_node.getRelationships(Direction.OUTGOING, RTreeRelationshipTypes.RTREE_REFERENCE);

        ArrayList<Long> ids = new ArrayList<Long>();
        for (Relationship relationship : rels) {
          Node geom = relationship.getEndNode();
          double[] bbox = (double[]) geom.getProperty(BBoxName);
          MyRectangle bbox_rect = new MyRectangle(bbox);
          if (min_queryRectangle.intersect(bbox_rect) != null) {
            located_in_count++;
            long id = geom.getId();
            ids.add(id);
          }
        }
        range_query_time += System.currentTimeMillis() - start_1;

        if (ids.size() > 0) {
          start_1 = System.currentTimeMillis();
          // String query = formSubgraphQuery_Block(query_Graph, limit, Explain_Or_Profile.Profile,
          // spa_predicates, min_pos, ids, NL_hopnum, rtree_node);
          String query = formSubgraphQuery_Block_New(query_Graph, limit,
              Enums.Explain_Or_Profile.Profile, spa_predicates, min_pos, ids);
          Util.println(query);

          Result result = dbservice.execute(query);
          get_iterator_time += System.currentTimeMillis() - start_1;

          start_1 = System.currentTimeMillis();
          int cur_count = 0;
          while (result.hasNext()) {
            cur_count++;
            result.next();
          }
          iterate_time += System.currentTimeMillis() - start_1;

          planDescription = result.getExecutionPlanDescription();
          result_count += cur_count;
          page_hit_count += OwnMethods.GetTotalDBHits(planDescription);
        }
      }
      Util.println("located in spatial objects count: " + located_in_count);
      candidate_count += located_in_count;

      tx.success();
      tx.close();
      run_time += System.currentTimeMillis() - totalStart;
      setQueryStatistics(QueryType.LAGAQ_RANGE);
    } catch (Exception e) {
      e.printStackTrace();
    }
    // return null;
  }

  /**
   * Query function with KNN predicate.
   * 
   * @param query_Graph
   * @param K
   */
  public ArrayList<Long> LAGAQ_KNN(Query_Graph query_Graph, int K) {
    clearTrackingVariables();
    long totalStart = System.currentTimeMillis();
    try {
      ArrayList<Long> resultIDs = new ArrayList<Long>();
      Map<Integer, MyRectangle> spatialPredicatesMap = query_Graph.getSpatialPredicates();
      if (spatialPredicatesMap.size() != 1) {
        throw new Exception(String.format("query graph has %d spatial predicates rather than 1!",
            spatialPredicatesMap.size()));
      }
      MyPoint queryLoc = null;
      int querySpatialVertexID = spatialPredicatesMap.keySet().iterator().next();
      MyRectangle queryRectangle = spatialPredicatesMap.get(querySpatialVertexID);
      queryLoc = new MyPoint(queryRectangle.min_x, queryRectangle.min_y);
      Label kNNLabel = Label.label(query_Graph.getLabel(querySpatialVertexID));
      Util.println("kNNLabel: " + kNNLabel.toString());

      long start = System.currentTimeMillis();
      Transaction tx = dbservice.beginTx();
      Node root_node = RTreeUtility.getRTreeRoot(dbservice, dataset);

      PriorityQueue<Element> queue = new PriorityQueue<Element>(100, new KNNComparator());
      queue.add(new Element(root_node, 0));

      while (resultIDs.size() < K && queue.isEmpty() == false) {
        Element element = queue.poll();
        Node node = element.node;
        // Tree non-leaf node
        if (node.hasRelationship(Labels.RTreeRel.RTREE_CHILD, Direction.OUTGOING)) {
          Iterable<Relationship> rels =
              node.getRelationships(Labels.RTreeRel.RTREE_CHILD, Direction.OUTGOING);
          for (Relationship relationship : rels) {
            Node child = relationship.getEndNode();
            if (!child.hasProperty(BBoxName)) {
              throw new Exception(String.format("Node %s does not have %s", child, BBoxName));
            }
            double[] bbox = (double[]) child.getProperty(BBoxName);
            MyRectangle MBR = new MyRectangle(bbox[0], bbox[1], bbox[2], bbox[3]);
            queue.add(new Element(child, Util.distance(queryLoc, MBR)));
          }
        }
        // tree leaf node
        else if (node.hasRelationship(Labels.RTreeRel.RTREE_REFERENCE, Direction.OUTGOING)) {
          Iterable<Relationship> rels =
              node.getRelationships(Labels.RTreeRel.RTREE_REFERENCE, Direction.OUTGOING);
          for (Relationship relationship : rels) {
            Node geom = relationship.getEndNode();
            if (!geom.hasProperty(lon_name)) {
              throw new Exception(String.format("Node %d does not have %s", geom, lon_name));
            }
            // if (!geom.hasLabel(kNNLabel)) {
            // LOGGER.info("Node %s does not have label %s");
            // Iterable<Label> labels = geom.getLabels();
            // for (Label label : labels) {
            // LOGGER.info(label.toString());
            // }
            // continue;
            // }
            double lon = (Double) geom.getProperty(lon_name);
            double lat = (Double) geom.getProperty(lat_name);
            queue.add(new Element(geom, Util.distance(queryLoc, new MyPoint(lon, lat))));
          }
        }
        // spatial object
        else if (Neo4jGraphUtility.isNodeSpatial(node)) {
          visit_spatial_object_count++;
          long id = node.getId();

          queue_time += System.currentTimeMillis() - start;

          start = System.currentTimeMillis();
          String query = RisoTreeQueryPN.formQuery_KNN(query_Graph, 1,
              Enums.Explain_Or_Profile.Profile, querySpatialVertexID, id);
          Result result = dbservice.execute(query);
          get_iterator_time += System.currentTimeMillis() - start;

          start = System.currentTimeMillis();
          if (result.hasNext()) {
            result.next();
            resultIDs.add(id);
            // OwnMethods.Print(String.format("%d, %f", id, element.distance));
          }
          iterate_time += System.currentTimeMillis() - start;
          start = System.currentTimeMillis();

          this.planDescription = result.getExecutionPlanDescription();
          page_hit_count += OwnMethods.GetTotalDBHits(planDescription);
        } else
          throw new Exception(
              String.format("Node %d does not affiliate to any type!", node.getId()));
      }

      queue_time += System.currentTimeMillis() - start;
      tx.success();
      tx.close();
      run_time = System.currentTimeMillis() - totalStart;
      result_count = resultIDs.size();
      setQueryStatistics(QueryType.LAGAQ_KNN);
      return resultIDs;

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    return null;
  }

  public List<Long[]> spatialJoinRTree(double distance, ArrayList<Label> targetLabels) {
    Label leftLabel = targetLabels.get(0);
    Label rightLabel = targetLabels.get(1);
    List<Long[]> result = new LinkedList<Long[]>();
    Queue<NodeAndRec[]> queue = new LinkedList<NodeAndRec[]>();
    Transaction tx = dbservice.beginTx();
    Node root = RTreeUtility.getRTreeRoot(dbservice, dataset);
    MyRectangle rootMBR = RTreeUtility.getNodeMBR(root);

    NodeAndRec[] pair = new NodeAndRec[2];
    pair[0] = new NodeAndRec(root, rootMBR);
    pair[1] = new NodeAndRec(root, rootMBR);
    queue.add(pair);
    while (!queue.isEmpty()) {
      NodeAndRec[] element = queue.poll();
      NodeAndRec left = element[0];
      NodeAndRec right = element[1];

      LinkedList<NodeAndRec> leftChildren = new LinkedList<NodeAndRec>();
      LinkedList<NodeAndRec> rightChildern = new LinkedList<NodeAndRec>();

      Iterable<Relationship> rels = left.node.getRelationships(Direction.OUTGOING);
      for (Relationship relationship : rels) {
        Node child = relationship.getEndNode();
        MyRectangle mbr = RTreeUtility.getNodeMBR(child);
        if (Util.distance(mbr, right.rectangle) <= distance)
          leftChildren.add(new NodeAndRec(child, mbr));
      }

      rels = right.node.getRelationships(Direction.OUTGOING);
      for (Relationship relationship : rels) {
        Node child = relationship.getEndNode();
        MyRectangle mbr = RTreeUtility.getNodeMBR(child);
        if (Util.distance(mbr, left.rectangle) <= distance)
          rightChildern.add(new NodeAndRec(child, mbr));
      }

      if (left.node.hasRelationship(RTreeRel.RTREE_REFERENCE, Direction.OUTGOING)) {
        for (NodeAndRec leftChild : leftChildren) {
          if (!leftChild.node.hasLabel(leftLabel)) {
            continue;
          }
          for (NodeAndRec rightChild : rightChildern) {
            if (!rightChild.node.hasLabel(rightLabel)) {
              continue;
            }
            if (Util.distance(leftChild.rectangle, rightChild.rectangle) <= distance) {
              long id1 = leftChild.node.getId();
              long id2 = rightChild.node.getId();
              if (id1 != id2) {
                result.add(new Long[] {id1, id2});
              }
            }
          }
        }
      } else {
        for (NodeAndRec leftChild : leftChildren) {
          for (NodeAndRec rightChild : rightChildern) {
            if (Util.distance(leftChild.rectangle, rightChild.rectangle) <= distance) {
              NodeAndRec[] nodeAndRecs = new NodeAndRec[2];
              nodeAndRecs[0] = new NodeAndRec(leftChild.node, leftChild.rectangle);
              nodeAndRecs[1] = new NodeAndRec(rightChild.node, rightChild.rectangle);
              queue.add(nodeAndRecs);
            }
          }
        }
      }
    }
    tx.success();
    tx.close();
    return result;
  }

  public List<Long[]> LAGAQ_Join(Query_Graph query_Graph, double distance) {
    try {
      clearTrackingVariables();
      long totalStart = System.currentTimeMillis();

      List<Long[]> resultPairs = new LinkedList<Long[]>();

      int count = 0;
      ArrayList<Integer> pos = new ArrayList<Integer>(2);
      ArrayList<Label> targetLabels = new ArrayList<>(2);
      for (int i = 0; i < query_Graph.Has_Spa_Predicate.length; i++) {
        if (query_Graph.Has_Spa_Predicate[i] == true) {
          pos.add(i);
          targetLabels.add(Label.label(query_Graph.getLabel(i)));
          count++;
        }
      }
      if (count != 2) {
        throw new Exception(String
            .format("Number of query graph spatial predicate is " + "%d, it should be 2!", count));
      }

      long start = System.currentTimeMillis();
      List<Long[]> idPairs = this.spatialJoinRTree(distance, targetLabels);
      join_time = System.currentTimeMillis() - start;
      join_result_count = idPairs.size();

      Transaction tx = dbservice.beginTx();
      for (Long[] idPair : idPairs) {
        start = System.currentTimeMillis();
        String query = RisoTreeQueryPN.formQueryLAGAQ_Join(query_Graph, pos, idPair, 1,
            Enums.Explain_Or_Profile.Profile);
        Result result = dbservice.execute(query);
        get_iterator_time += System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        if (result.hasNext()) {
          result.next();
          resultPairs.add(idPair);
          // OwnMethods.Print(String.format("%d, %f", id, element.distance));
        }
        iterate_time += System.currentTimeMillis() - start;
        start = System.currentTimeMillis();

        planDescription = result.getExecutionPlanDescription();
        page_hit_count += OwnMethods.GetTotalDBHits(planDescription);
      }
      tx.success();
      tx.close();
      run_time = System.currentTimeMillis() - totalStart;
      result_count = resultPairs.size();
      setQueryStatistics(QueryType.LAGAQ_JOIN);
      return resultPairs;
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    return null;
  }

  public Map<QueryStatistic, Object> getQueryStatisticMap() {
    return queryStatisticMap;
  }

  private void setQueryStatistics(QueryType queryType) throws Exception {
    queryStatisticMap = new HashMap<>();
    queryStatisticMap.put(QueryStatistic.run_time, run_time);
    queryStatisticMap.put(QueryStatistic.page_hit_count, page_hit_count);
    queryStatisticMap.put(QueryStatistic.get_iterator_time, get_iterator_time);
    queryStatisticMap.put(QueryStatistic.iterate_time, iterate_time);
    queryStatisticMap.put(QueryStatistic.graph_time, get_iterator_time + iterate_time);
    queryStatisticMap.put(QueryStatistic.result_count, result_count);

    switch (queryType) {
      case LAGAQ_RANGE:
        queryStatisticMap.put(QueryStatistic.spatial_time, range_query_time);
        queryStatisticMap.put(QueryStatistic.overlap_leaf_node_count, overlap_leaf_count);
        queryStatisticMap.put(QueryStatistic.candidate_count, candidate_count);
        break;
      case LAGAQ_JOIN:
        queryStatisticMap.put(QueryStatistic.spatial_time, join_time);
        queryStatisticMap.put(QueryStatistic.candidate_count, join_result_count);
        break;
      case LAGAQ_KNN:
        queryStatisticMap.put(QueryStatistic.spatial_time, queue_time);
        queryStatisticMap.put(QueryStatistic.candidate_count, visit_spatial_object_count);
        break;
      default:
        throw new Exception(queryType + " does not exist!");
    }
  }
}
