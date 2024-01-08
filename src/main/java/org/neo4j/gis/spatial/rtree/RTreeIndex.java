/**
 * Copyright (c) 2002-2013 "Neo Technology," Network Engine for Objects in Lund AB
 * [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gis.spatial.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.neo4j.gis.spatial.rtree.filter.SearchFilter;
import org.neo4j.gis.spatial.rtree.filter.SearchResults;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import commons.ReadWriteUtil;
import commons.Util;

/**
 *
 */
public class RTreeIndex implements SpatialIndexWriter {

  public enum NodeLayer {
    LEAF, OBJECT, OTHER,
  }

  public static final String INDEX_PROP_BBOX = "bbox";

  public static final String KEY_SPLIT = "splitMode";
  public static final String QUADRATIC_SPLIT = "quadratic";
  public static final String GREENES_SPLIT = "greene";

  public static final String KEY_MAX_NODE_REFERENCES = "maxNodeReferences";
  public static final String KEY_SHOULD_MERGE_TREES = "shouldMergeTrees";
  public static final long MIN_MAX_NODE_REFERENCES = 10;
  public static final long MAX_MAX_NODE_REFERENCES = 1000000;

  private TreeMonitor monitor;

  // Constructor
  public RTreeIndex(GraphDatabaseService database, Node rootNode, EnvelopeDecoder envelopeEncoder) {
    this(database, rootNode, envelopeEncoder, 100);
  }

  public void addMonitor(TreeMonitor monitor) {
    this.monitor = monitor;
  }

  public RTreeIndex(GraphDatabaseService database, Node rootNode, EnvelopeDecoder envelopeDecoder,
      int maxNodeReferences) {
    this.database = database;
    this.rootNode = rootNode;
    this.envelopeDecoder = envelopeDecoder;
    this.maxNodeReferences = maxNodeReferences;

    LOGGER.info("Set minNodeReference");
    this.minNodeReferences = this.maxNodeReferences / 2;
    monitor = new EmptyMonitor();
    if (envelopeDecoder == null) {
      throw new NullPointerException("envelopeDecoder is NULL");
    }

    initIndexRoot();
    initIndexMetadata();
  }

  // Public methods
  @Override
  public EnvelopeDecoder getEnvelopeDecoder() {
    return this.envelopeDecoder;
  }

  public void configure(Map<String, Object> config) {
    for (String key : config.keySet()) {
      switch (key) {
        case KEY_SPLIT:
          String value = config.get(key).toString();
          switch (value) {
            case QUADRATIC_SPLIT:
            case GREENES_SPLIT:
              splitMode = value;
              break;
            default:
              throw new IllegalArgumentException(
                  "No such RTreeIndex value for '" + key + "': " + value);
          }
          break;
        case KEY_MAX_NODE_REFERENCES:
          int intValue = Integer.parseInt(config.get(key).toString());
          if (intValue < MIN_MAX_NODE_REFERENCES) {
            throw new IllegalArgumentException(
                "RTreeIndex does not allow " + key + " less than " + MIN_MAX_NODE_REFERENCES);
          }
          if (intValue > MAX_MAX_NODE_REFERENCES) {
            throw new IllegalArgumentException(
                "RTreeIndex does not allow " + key + " greater than " + MAX_MAX_NODE_REFERENCES);
          }
          this.maxNodeReferences = intValue;
          break;
        case KEY_SHOULD_MERGE_TREES:
          this.shouldMergeTrees = Boolean.parseBoolean(config.get(key).toString());
          break;
        default:
          throw new IllegalArgumentException("No such RTreeIndex configuration key: " + key);
      }
    }
  }

  public void add(Node geomNode, Map<String, int[]> pathNeighbors) {
    // initialize the search with root
    Node parent = getIndexRoot();

    addBelow(parent, geomNode, pathNeighbors);

    countSaved = false;
    totalGeometryCount++;
  }

  @Override
  public void add(Node geomNode) {
    // initialize the search with root
    Node parent = getIndexRoot();

    addBelow(parent, geomNode);

    countSaved = false;
    totalGeometryCount++;
  }

  private void addBelow(Node parent, Node geomNode, Map<String, int[]> pathNeighbors) {
    // choose a path down to a leaf
    long start = System.currentTimeMillis();
    while (!nodeIsLeaf(parent)) {
      if (spatialOnly) {
        parent = chooseSubTree(parent, geomNode);
      } else {
        // parent = chooseSubTree(parent, geomNode, pathNeighbors);
        parent = chooseSubTreeSmallestGSD(parent, geomNode, pathNeighbors);
      }
    }
    // LOGGER.info("parent node: " + parent);
    chooseSubTreeTime += System.currentTimeMillis() - start;

    start = System.currentTimeMillis();
    if (countChildren(parent, RTreeRelationshipTypes.RTREE_REFERENCE) >= maxNodeReferences) {
      // LOGGER.info(String.format("insertInLeaf(%s,%s,%s)", parent, geomNode, pathNeighbors));
      insertInLeaf(parent, geomNode, pathNeighbors);
      // LOGGER.info(String.format("splitAndAdjustPathBoundingBox(%s)", parent));
      splitAndAdjustPathBoundingBox(parent);
    } else { // no split case, done for RisoTree.
      // LOGGER.info(String.format("insertInLeaf(%s, %s, %s)", parent, geomNode, pathNeighbors));
      if (insertInLeaf(parent, geomNode, pathNeighbors)) {
        // bbox enlargement needed
        adjustPathBoundingBox(parent);
      }
      if (!spatialOnly) {
        adjustGraphLoc(parent, pathNeighbors);// yuhan
      }
    }
    insertAndAdjustTime += System.currentTimeMillis() - start;
  }

  /**
   * This method will add the node somewhere below the parent.
   */
  private void addBelow(Node parent, Node geomNode) {
    // choose a path down to a leaf
    long start = System.currentTimeMillis();
    while (!nodeIsLeaf(parent)) {
      parent = chooseSubTree(parent, geomNode);
    }
    chooseSubTreeTime += System.currentTimeMillis() - start;
    if (countChildren(parent, RTreeRelationshipTypes.RTREE_REFERENCE) >= maxNodeReferences) {
      insertInLeaf(parent, geomNode);
      splitAndAdjustPathBoundingBox(parent);
    } else { // no split case, done for RisoTree.
      if (insertInLeaf(parent, geomNode)) {
        // bbox enlargement needed
        adjustPathBoundingBox(parent);
      }
      if (!spatialOnly) {
        adjustGraphLoc(parent, geomNode);// yuhan
      }
    }
  }

  /**
   * Adjust the parent PN by expanding based on {@code pathNeighbors}. Only called when
   * {@code parent} is leaf node.
   *
   * @param parent
   * @param childLoc Path neighbors of the spatial object
   */
  private void adjustGraphLoc(Node parent, Map<String, int[]> childLoc) {
    long start = System.currentTimeMillis();
    HashMap<String, int[]> parentLoc = getLocInGraph(parent);
    long startWrite = System.currentTimeMillis();
    adjustGraphLoc(parentLoc, childLoc);
    adjustWriteTime += System.currentTimeMillis() - startWrite;
    adjustGraphLocTime += System.currentTimeMillis() - start;
  }

  private void adjustGraphLoc(Map<String, int[]> basePNs, Map<String, int[]> otherPNs) {
    for (String key : otherPNs.keySet()) {
      int[] childPN = otherPNs.get(key);
      if (childPN.length == 0) {
        // childPN is the ignored PN, so directly make parent PN ignored.
        basePNs.put(key, childPN);
        continue;
      }

      int[] basePN = basePNs.get(key);
      if (basePN == null) {
        basePNs.put(key, childPN);
        continue;
      }

      if (basePN.length == 0) {
        continue;
      }

      // both PNs are not ignored
      int[] expandPN = Util.sortedArrayMerge(childPN, basePN);
      if (expandPN.length > basePN.length) { // if PN is really expanded
        if (expandPN.length > MaxPNSize) {
          expandPN = new int[] {};
        }
        basePNs.put(key, expandPN);
      }
    }
  }

  /**
   * Adjust the PNs of the parent node on the leaf level. Currently no label paths is stored on
   * non-leaf nodes. So the function is not called recursively.
   *
   * @author yuhan
   * @param parent
   * @param geomNode
   */
  private void adjustGraphLoc(Node parent, Node geomNode) {
    long start = System.currentTimeMillis();
    HashMap<String, int[]> parentLoc = getLocInGraph(parent);
    Map<String, int[]> childLoc = spatialNodesPathNeighbors.get((int) geomNode.getId());
    for (String key : childLoc.keySet()) {
      int[] childPN = childLoc.get(key);

      if (childPN.length == 0) {
        // childPN is the ignored PN, so directly make parent PN ignored.
        parent.setProperty(key, childPN);
        continue;
      }

      int[] parentPN = parentLoc.get(key);
      if (parentPN == null) {
        parent.setProperty(key, childPN);
        continue;
      }

      if (parentPN.length == 0) {
        continue;
      }

      // both PNs are not ignored
      int[] expandPN = Util.sortedArrayMerge(childPN, parentPN);
      if (expandPN.length > parentPN.length) { // if PN is really expanded
        if (expandPN.length > MaxPNSize) {
          expandPN = new int[] {};
        }
        parent.setProperty(key, expandPN);
      }
    }
    adjustGraphLocTime += System.currentTimeMillis() - start;
  }


  /**
   * Use this method if you want to insert an index node as a child of a given index node. This will
   * recursively update the bounding boxes above the parent to keep the tree consistent.
   */
  private void insertIndexNodeOnParent(Node parent, Node child) {
    int numChildren = countChildren(parent, RTreeRelationshipTypes.RTREE_CHILD);
    boolean needExpansion = addChild(parent, RTreeRelationshipTypes.RTREE_CHILD, child);
    if (numChildren < maxNodeReferences) {
      if (needExpansion) {
        adjustPathBoundingBox(parent);
      }
    } else {
      splitAndAdjustPathBoundingBox(parent);
    }
  }

  public void printTimeTrack() {
    Util.println("chooseSubTree time: " + chooseSubTreeTime);
    Util.println("insertAndAdjust time: " + insertAndAdjustTime);

    Util.println("getLocInGraph time: " + getLocInGraphTime);
    Util.println("getGDTime time: " + getGDTime);
    Util.println("adjustWrite time: " + adjustWriteTime);
    Util.println("adjustGraphLoc time: " + adjustGraphLocTime);
    Util.println("Total time: " + totalTime);
  }

  public void printFunctionCallTrack() {
    Util.println("In the chooseSubtree(), chooseIndexnodeWithSmallestGD is called "
        + chooseSmallestGDCount + " times");
    Util.println(differentTimes + " are different");

    Util.println(String.format("getGD() is called %d times", getGDCount));

    Util.println("noContainCount happens " + noContainCount + " times");
    Util.println(String.format("%d are the same while %d are different.", noContainSame,
        noContainDifferent));
    Util.println(String.format("tieBreakFailCount: %d", tieBreakFailCount));
  }

  public void add(List<Node> geomNodes, List<Map<String, int[]>> spatialNodesPathNeighbors,
      int graphNodeCount, double alpha, int maxPNSize) throws Exception {
    List<NodeWithEnvelope> outliers = bulkInsertion(getIndexRoot(), getHeight(getIndexRoot(), 0),
        decodeGeometryNodeEnvelopes(geomNodes), 0.7);
    countSaved = false;
    totalGeometryCount = totalGeometryCount + (geomNodes.size() - outliers.size());
    int index = 0;

    // initialize the map for leaf nodes path neighbors
    initializeLeafNodesPathNeighbors();
    this.spatialNodesPathNeighbors = spatialNodesPathNeighbors;
    this.graphNodeCount = graphNodeCount;
    this.alpha = alpha;
    this.spatialOnly = Math.abs(alpha - 1) < onlyDecisionThreshold ? true : false;
    this.graphOnly = Math.abs(alpha - 0) < onlyDecisionThreshold ? true : false;
    this.MaxPNSize = maxPNSize == -1 ? Integer.MAX_VALUE : maxPNSize;

    long start = System.currentTimeMillis();
    for (NodeWithEnvelope n : outliers) {
      index++;
      // LOGGER.info("" + index);
      if (index % 10000 == 0) {
        LOGGER.info("" + index);
      }
      Map<String, int[]> pathNeighbors = spatialNodesPathNeighbors.get((int) n.node.getId());
      add(n.node, pathNeighbors);
    }
    totalTime += System.currentTimeMillis() - start;

    printTimeTrack();
    printFunctionCallTrack();
    if (outputLeafNodesPathNeighors) {
      outputLeafNodesPathNeighors();
    }
  }

  private void outputLeafNodesPathNeighors() throws Exception {
    String dir = RTreeIndex.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    String outputPath = dir + alpha;
    ReadWriteUtil.writeLeafNodesPathNeighbors(leafNodesPathNeighbors, outputPath);
  }

  private void initializeLeafNodesPathNeighbors() throws Exception {
    leafNodesPathNeighbors = new HashMap<>();
    Node root = getRootNode();
    if (root == null) {
      throw new Exception("Root node is null");
    }
    Node rootMBRNode = root
        .getSingleRelationship(RTreeRelationshipTypes.RTREE_ROOT, Direction.OUTGOING).getEndNode();
    long id = rootMBRNode.getId();
    leafNodesPathNeighbors.put(id, new HashMap<>());
  }

  /**
   * Depending on the size of the incumbent tree, this will either attempt to rebuild the entire
   * index from scratch (strategy used if the insert larger than 40% of the current tree size - may
   * give heap out of memory errors for large inserts as has O(n) space complexity in the total tree
   * size. It has n*log(n) time complexity. See function partition for more details.) or it will
   * insert using the method of seeded clustering, where you attempt to use the existing tree
   * structure to partition your data.
   * <p>
   * This is based on the Paper "Bulk Insertion for R-trees by seeded clustering" by T.Lee, S.Lee &
   * B Moon. Repeated use of this strategy will lead to degraded query performance, especially if
   * used for many relatively small insertions compared to tree size. Though not worse than one by
   * one insertion. In practice, it should be fine for most uses.
   */
  @Override
  public void add(List<Node> geomNodes) {

    // If the insertion is large relative to the size of the tree, simply rebuild the whole tree.
    // yuhan
    // if (geomNodes.size() > totalGeometryCount * 0.4) {
    // List<Node> nodesToAdd = new ArrayList<>(geomNodes.size() + totalGeometryCount);
    // for (Node n : getAllIndexedNodes()) {
    // nodesToAdd.add(n);
    // }
    // nodesToAdd.addAll(geomNodes);
    // for (Node n : getAllIndexInternalNodes()) {
    // if (!n.equals(getIndexRoot())) {
    // deleteNode(n);
    // }
    // }
    // buildRtreeFromScratch(getIndexRoot(), decodeGeometryNodeEnvelopes(nodesToAdd), 0.7);
    // countSaved = false;
    // totalGeometryCount = nodesToAdd.size();
    // monitor.addNbrRebuilt(this);
    // } else {
    List<NodeWithEnvelope> outliers = bulkInsertion(getIndexRoot(), getHeight(getIndexRoot(), 0),
        decodeGeometryNodeEnvelopes(geomNodes), 0.7);
    countSaved = false;
    totalGeometryCount = totalGeometryCount + (geomNodes.size() - outliers.size());
    int index = 0;
    for (NodeWithEnvelope n : outliers) {
      index++;
      LOGGER.info("" + index);
      // if (index % 10000 == 0) {
      // LOGGER.info("" + index);
      // }
      long start = System.currentTimeMillis();
      add(n.node);
      totalTime += System.currentTimeMillis() - start;
    }

    printTimeTrack();
    printFunctionCallTrack();
    // }

  }

  private List<NodeWithEnvelope> decodeGeometryNodeEnvelopes(List<Node> nodes) {
    return nodes.stream().map(GeometryNodeWithEnvelope::new).collect(Collectors.toList());
  }

  public static class NodeWithEnvelope {
    public Envelope envelope;
    Node node;

    public NodeWithEnvelope(Node node, Envelope envelope) {
      this.node = node;
      this.envelope = envelope;
    }
  }

  public class GeometryNodeWithEnvelope extends NodeWithEnvelope {
    GeometryNodeWithEnvelope(Node node) {
      super(node, envelopeDecoder.decodeEnvelope(node));
    }
  }

  /**
   * Returns the height of the tree, starting with the rootNode and adding one for each subsequent
   * level. Relies on the balanced property of the RTree that all leaves are on the same level and
   * no index nodes are empty. In the convention the index is level 0, so if there is just the index
   * and the leaf nodes, the leaf nodes are level one and the height is one. Thus the lowest level
   * is 1.
   */
  int getHeight(Node rootNode, int height) {
    Iterator<Relationship> rels = rootNode
        .getRelationships(Direction.OUTGOING, RTreeRelationshipTypes.RTREE_CHILD).iterator();
    if (rels.hasNext()) {
      return getHeight(rels.next().getEndNode(), height + 1);
    } else {
      // Add one to account for the step to leaf nodes.
      return height + 1; // todo should this really be +1 ?
    }
  }

  List<NodeWithEnvelope> getIndexChildren(Node rootNode) {
    List<NodeWithEnvelope> result = new ArrayList<>();
    for (Relationship r : rootNode.getRelationships(Direction.OUTGOING,
        RTreeRelationshipTypes.RTREE_CHILD)) {
      Node child = r.getEndNode();
      result.add(new NodeWithEnvelope(child, getIndexNodeEnvelope(child)));
    }
    return result;
  }

  private List<NodeWithEnvelope> getIndexChildren(Node rootNode, int depth) {
    if (depth < 1) {
      throw new IllegalArgumentException("Depths must be at least one");
    }

    List<NodeWithEnvelope> rootChildren = getIndexChildren(rootNode);
    if (depth == 1) {
      return rootChildren;
    } else {
      List<NodeWithEnvelope> result = new ArrayList<>(rootChildren.size() * 5);
      for (NodeWithEnvelope child : rootChildren) {
        result.addAll(getIndexChildren(child.node, depth - 1));
      }
      return result;
    }
  }

  private List<NodeWithEnvelope> bulkInsertion(Node rootNode, int rootNodeHeight,
      final List<NodeWithEnvelope> geomNodes, final double loadingFactor) {
    List<NodeWithEnvelope> children = getIndexChildren(rootNode);
    if (children.isEmpty()) {
      return geomNodes;
    }
    children.sort(new IndexNodeAreaComparator());

    Map<NodeWithEnvelope, List<NodeWithEnvelope>> map = new HashMap<>(children.size());
    int nodesPerRootSubTree = Math.max(16, geomNodes.size() / children.size());
    for (NodeWithEnvelope n : children) {
      map.put(n, new ArrayList<>(nodesPerRootSubTree));
    }

    // The outliers are those nodes which do not fit into the existing tree hierarchy.
    List<NodeWithEnvelope> outliers = new ArrayList<>(geomNodes.size() / 10); // 10% outliers
    for (NodeWithEnvelope n : geomNodes) {
      Envelope env = n.envelope;
      boolean flag = true;

      // exploits that the iterator returns the list inorder, which is sorted by size, as above.
      // Thus child
      // is always added to the smallest existing envelope which contains it.
      for (NodeWithEnvelope c : children) {
        if (c.envelope.contains(env)) {
          map.get(c).add(n); // add to smallest area envelope which contains the child;
          flag = false;
          break;
        }
      }
      // else add to outliers.
      if (flag) {
        outliers.add(n);
      }
    }
    for (NodeWithEnvelope child : children) {
      List<NodeWithEnvelope> cluster = map.get(child);

      if (cluster.isEmpty())
        continue;

      // todo move each branch into a named method
      int expectedHeight = expectedHeight(loadingFactor, cluster.size());

      // In an rtree is this height it will add as a single child to the current child node.
      int currentRTreeHeight = rootNodeHeight - 2;
      // if(expectedHeight-currentRTreeHeight > 1 ){
      // throw new RuntimeException("Due to h_i-l_t > 1");
      // }
      if (expectedHeight < currentRTreeHeight) {
        monitor.addCase("h_i < l_t ");
        // if the height is smaller than that recursively sort and split.
        outliers.addAll(bulkInsertion(child.node, rootNodeHeight - 1, cluster, loadingFactor));
      } // if constructed tree is the correct size insert it here.
      else if (expectedHeight == currentRTreeHeight) {

        // Do not create underfull nodes, instead use the add logic, except we know the root not to
        // add them too.
        // this handles the case where the number of nodes in a cluster is small.

        if (cluster.size() < maxNodeReferences * loadingFactor / 2) {
          monitor.addCase("h_i == l_t && small cluster");
          // getParent because addition might cause a split. This strategy not ideal,
          // but does tend to limit overlap more than adding to the child exclusively.

          for (NodeWithEnvelope n : cluster) {
            addBelow(rootNode, n.node);
          }
        } else {
          monitor.addCase("h_i == l_t && big cluster");
          Node newRootNode = database.createNode();
          buildRtreeFromScratch(newRootNode, cluster, loadingFactor);
          if (shouldMergeTrees) {
            NodeWithEnvelope nodeWithEnvelope =
                new NodeWithEnvelope(newRootNode, getIndexNodeEnvelope(newRootNode));
            List<NodeWithEnvelope> insert =
                new ArrayList<>(Arrays.asList(new NodeWithEnvelope[] {nodeWithEnvelope}));
            monitor.beforeMergeTree(child.node, insert);
            mergeTwoSubtrees(child, insert);
            monitor.afterMergeTree(child.node);
          } else {
            insertIndexNodeOnParent(child.node, newRootNode);
          }
        }

      } else {
        Node newRootNode = database.createNode();
        buildRtreeFromScratch(newRootNode, cluster, loadingFactor);
        int newHeight = getHeight(newRootNode, 0);
        if (newHeight == 1) {
          monitor.addCase("h_i > l_t (d==1)");
          for (Relationship geom : newRootNode
              .getRelationships(RTreeRelationshipTypes.RTREE_REFERENCE)) {
            addBelow(child.node, geom.getEndNode());
            geom.delete();
          }
        } else {
          monitor.addCase("h_i > l_t (d>1)");
          int insertDepth = newHeight - (currentRTreeHeight);
          List<NodeWithEnvelope> childrenToBeInserted = getIndexChildren(newRootNode, insertDepth);
          for (NodeWithEnvelope n : childrenToBeInserted) {
            Relationship relationship = n.node
                .getSingleRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.INCOMING);
            relationship.delete();
            if (!shouldMergeTrees) {
              insertIndexNodeOnParent(child.node, n.node);
            }
          }
          if (shouldMergeTrees) {
            monitor.beforeMergeTree(child.node, childrenToBeInserted);
            mergeTwoSubtrees(child, childrenToBeInserted);
            monitor.afterMergeTree(child.node);
          }
        }
        // todo wouldn't it be better for this temporary tree to only live in memory?
        deleteRecursivelySubtree(newRootNode, null); // remove the buffer tree remnants
      }
    }
    monitor.addSplit(rootNode); // for debugging via images

    return outliers;
  }

  class NodeTuple {
    private final double overlap;
    NodeWithEnvelope left;
    NodeWithEnvelope right;

    NodeTuple(NodeWithEnvelope left, NodeWithEnvelope right) {
      this.left = left;
      this.right = right;
      this.overlap = left.envelope.overlap(right.envelope);
    }

    boolean contains(NodeWithEnvelope entry) {
      return left.node.equals(entry.node) || right.node.equals(entry.node);
    }
  }

  protected void mergeTwoSubtrees(NodeWithEnvelope parent, List<NodeWithEnvelope> right) {
    ArrayList<NodeTuple> pairs = new ArrayList<>();
    HashSet<NodeWithEnvelope> disconnectedChildren = new HashSet<>();
    List<NodeWithEnvelope> left = getIndexChildren(parent.node);
    for (NodeWithEnvelope leftNode : left) {
      for (NodeWithEnvelope rightNode : right) {
        NodeTuple pair = new NodeTuple(leftNode, rightNode);
        if (pair.overlap > 0.1) {
          pairs.add(pair);
        }
      }
    }
    pairs.sort((o1, o2) -> Double.compare(o1.overlap, o2.overlap));
    while (!pairs.isEmpty()) {
      NodeTuple pair = pairs.remove(pairs.size() - 1);
      Envelope merged = new Envelope(pair.left.envelope);
      merged.expandToInclude(pair.right.envelope);
      NodeWithEnvelope newNode = new NodeWithEnvelope(pair.left.node, merged);
      setIndexNodeEnvelope(newNode.node, newNode.envelope);
      List<NodeWithEnvelope> rightChildren = getIndexChildren(pair.right.node);
      pairs.removeIf(t -> t.contains(pair.left) || t.contains(pair.right));
      for (Relationship rel : pair.right.node.getRelationships()) {
        rel.delete();
      }
      disconnectedChildren.add(pair.right);
      mergeTwoSubtrees(newNode, rightChildren);
    }

    right.removeIf(t -> disconnectedChildren.contains(t));
    disconnectedChildren.forEach(t -> t.node.delete());

    for (NodeWithEnvelope n : right) {
      n.node.getSingleRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.INCOMING);
      parent.node.createRelationshipTo(n.node, RTreeRelationshipTypes.RTREE_CHILD);
      parent.envelope.expandToInclude(n.envelope);
    }
    setIndexNodeEnvelope(parent.node, parent.envelope);
    if (countChildren(parent.node, RTreeRelationshipTypes.RTREE_CHILD) > maxNodeReferences) {
      splitAndAdjustPathBoundingBox(parent.node);
    } else {
      adjustPathBoundingBox(parent.node);
    }
  }

  private int expectedHeight(double loadingFactor, int size) {
    if (size == 1) {
      return 1;
    } else {
      final int targetLoading = (int) Math.floor(maxNodeReferences * loadingFactor);
      return (int) Math.ceil(Math.log(size) / Math.log(targetLoading)); // exploit change of base
                                                                        // formula
    }

  }

  /**
   * This algorithm is based on Overlap Minimizing Top-down Bulk Loading Algorithm for R-tree by T
   * Lee and S Lee. This is effectively a wrapper function around the function Partition which will
   * attempt to parallelise the task. This can work better or worse since the top level may have as
   * few as two nodes, in which case it fails is not optimal. The loadingFactor must be between 0.1
   * and 1, this is how full each node will be, approximately. Use 1 for static trees (will not be
   * added to after build built), lower numbers if there are to be many subsequent updates. //TODO -
   * Better parallelisation strategy.
   */
  private void buildRtreeFromScratch(Node rootNode, final List<NodeWithEnvelope> geomNodes,
      double loadingFactor) {
    partition(rootNode, geomNodes, 0, loadingFactor);
  }

  /**
   * This will partition a collection of nodes under the specified index node. The nodes are
   * clustered into one or more groups based on the loading factor, and the tree is expanded if
   * necessary. If the nodes all fit into the parent, they are added directly, otherwise the depth
   * is increased and partition called for each cluster at the deeper depth based on a new root node
   * for each cluster.
   */
  private void partition(Node indexNode, List<NodeWithEnvelope> nodes, int depth,
      final double loadingFactor) {

    // We want to split by the longest dimension to avoid degrading into extremely thin envelopes
    int longestDimension = findLongestDimension(nodes);

    // Sort the entries by the longest dimension and then create envelopes around left and right
    // halves
    nodes.sort(new SingleDimensionNodeEnvelopeComparator(longestDimension));

    // work out the number of times to partition it:
    final int targetLoading = (int) Math.round(maxNodeReferences * loadingFactor);
    int nodeCount = nodes.size();

    if (nodeCount <= targetLoading) {
      // We have few enough nodes to add them directly to the current index node
      boolean expandRootNodeBoundingBox = false;
      for (NodeWithEnvelope n : nodes) {
        expandRootNodeBoundingBox |= insertInLeaf(indexNode, n.node);
      }
      if (expandRootNodeBoundingBox) {
        adjustPathBoundingBox(indexNode);
      }
    } else {
      // We have more geometries than can fit in the current index node - create clusters and index
      // them
      final int height = expectedHeight(loadingFactor, nodeCount); // exploit change of base formula
      final int subTreeSize = (int) Math.round(Math.pow(targetLoading, height - 1));
      final int numberOfPartitions = (int) Math.ceil((double) nodeCount / (double) subTreeSize);
      // - TODO change this to use the sort function above
      List<List<NodeWithEnvelope>> partitions = partitionList(nodes, numberOfPartitions);

      // recurse on each partition
      for (List<NodeWithEnvelope> partition : partitions) {
        Node newIndexNode = database.createNode();
        if (partition.size() > 1) {
          partition(newIndexNode, partition, depth + 1, loadingFactor);
        } else {
          addBelow(newIndexNode, partition.get(0).node);
        }
        insertIndexNodeOnParent(indexNode, newIndexNode);
      }
      monitor.addSplit(indexNode);
    }
  }

  // quick dirty way to partition a set into equal sized disjoint subsets
  // - TODO why not use list.sublist() without copying ?

  private List<List<NodeWithEnvelope>> partitionList(List<NodeWithEnvelope> nodes,
      int numberOfPartitions) {
    int nodeCount = nodes.size();
    List<List<NodeWithEnvelope>> partitions = new ArrayList<>(numberOfPartitions);

    int partitionSize = nodeCount / numberOfPartitions; // it is critical that partitionSize is
                                                        // always less than the target loading.
    if (nodeCount % numberOfPartitions > 0) {
      partitionSize++;
    }
    for (int i = 0; i < numberOfPartitions; i++) {
      partitions
          .add(nodes.subList(i * partitionSize, Math.min((i + 1) * partitionSize, nodeCount)));
    }
    return partitions;
  }

  @Override
  public void remove(long geomNodeId, boolean deleteGeomNode) {
    remove(geomNodeId, deleteGeomNode, true);
  }

  public void remove(long geomNodeId, boolean deleteGeomNode, boolean throwExceptionIfNotFound) {

    Node geomNode = null;
    // getNodeById throws NotFoundException if node is already removed
    try {
      geomNode = database.getNodeById(geomNodeId);

    } catch (NotFoundException nfe) {

      // propagate exception only if flag is set
      if (throwExceptionIfNotFound) {
        throw nfe;
      }
    }
    if (geomNode != null && isGeometryNodeIndexed(geomNode)) {

      Node indexNode = findLeafContainingGeometryNode(geomNode);

      // be sure geomNode is inside this RTree
      if (isIndexNodeInThisIndex(indexNode)) {

        // remove the entry
        final Relationship geometryRtreeReference = geomNode
            .getSingleRelationship(RTreeRelationshipTypes.RTREE_REFERENCE, Direction.INCOMING);
        if (geometryRtreeReference != null) {
          geometryRtreeReference.delete();
        }
        if (deleteGeomNode) {
          deleteNode(geomNode);
        }

        // reorganize the tree if needed
        if (countChildren(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE) == 0) {
          indexNode = deleteEmptyTreeNodes(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE);
          adjustParentBoundingBox(indexNode, RTreeRelationshipTypes.RTREE_CHILD);
        } else {
          adjustParentBoundingBox(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE);
        }

        adjustPathBoundingBox(indexNode);

        countSaved = false;
        totalGeometryCount--;
      } else if (throwExceptionIfNotFound) {
        throw new RuntimeException("GeometryNode not indexed in this RTree: " + geomNodeId);
      }
    } else if (throwExceptionIfNotFound) {
      throw new RuntimeException("GeometryNode not indexed with an RTree: " + geomNodeId);
    }
  }

  private Node deleteEmptyTreeNodes(Node indexNode, RelationshipType relType) {
    if (countChildren(indexNode, relType) == 0) {
      Node parent = getIndexNodeParent(indexNode);
      if (parent != null) {
        indexNode.getSingleRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.INCOMING)
            .delete();

        indexNode.delete();
        return deleteEmptyTreeNodes(parent, RTreeRelationshipTypes.RTREE_CHILD);
      } else {
        // root
        return indexNode;
      }
    } else {
      return indexNode;
    }
  }

  @Override
  public void removeAll(final boolean deleteGeomNodes, final Listener monitor) {
    Node indexRoot = getIndexRoot();

    monitor.begin(count());
    try {
      // delete all geometry nodes
      visitInTx(new SpatialIndexVisitor() {
        public boolean needsToVisit(Envelope indexNodeEnvelope) {
          return true;
        }

        public void onIndexReference(Node geomNode) {
          geomNode.getSingleRelationship(RTreeRelationshipTypes.RTREE_REFERENCE, Direction.INCOMING)
              .delete();
          if (deleteGeomNodes) {
            deleteNode(geomNode);
          }

          monitor.worked(1);
        }
      }, indexRoot.getId());
    } finally {
      monitor.done();
    }

    try (Transaction tx = database.beginTx()) {
      // delete index root relationship
      indexRoot.getSingleRelationship(RTreeRelationshipTypes.RTREE_ROOT, Direction.INCOMING)
          .delete();

      // delete tree
      deleteRecursivelySubtree(indexRoot, null);

      // delete tree metadata
      Relationship metadataNodeRelationship = getRootNode()
          .getSingleRelationship(RTreeRelationshipTypes.RTREE_METADATA, Direction.OUTGOING);
      Node metadataNode = metadataNodeRelationship.getEndNode();
      metadataNodeRelationship.delete();
      metadataNode.delete();

      tx.success();
    }

    countSaved = false;
    totalGeometryCount = 0;
  }

  @Override
  public void clear(final Listener monitor) {
    try (Transaction tx = database.beginTx()) {
      removeAll(false, new NullListener());
      initIndexRoot();
      initIndexMetadata();
      tx.success();
    }
  }

  @Override
  public Envelope getBoundingBox() {
    try (Transaction tx = database.beginTx()) {
      Envelope result = getIndexNodeEnvelope(getIndexRoot());
      tx.success();
      return result;
    }
  }

  @Override
  public int count() {
    saveCount();
    return totalGeometryCount;
  }

  @Override
  public boolean isEmpty() {
    Node indexRoot = getIndexRoot();
    return !indexRoot.hasProperty(INDEX_PROP_BBOX);
  }

  @Override
  public boolean isNodeIndexed(Long geomNodeId) {
    Node geomNode = database.getNodeById(geomNodeId);
    // be sure geomNode is inside this RTree
    return geomNode != null && isGeometryNodeIndexed(geomNode)
        && isIndexNodeInThisIndex(findLeafContainingGeometryNode(geomNode));
  }

  public void warmUp() {
    visit(new WarmUpVisitor(), getIndexRoot());
  }

  public Iterable<Node> getAllIndexInternalNodes() {
    TraversalDescription td = database.traversalDescription().breadthFirst()
        .relationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING)
        .evaluator(Evaluators.all());
    return td.traverse(getIndexRoot()).nodes();
  }

  @Override
  public Iterable<Node> getAllIndexedNodes() {
    return new IndexNodeToGeometryNodeIterable(getAllIndexInternalNodes());
  }

  private class SearchEvaluator implements Evaluator {
    private SearchFilter filter;

    public SearchEvaluator(SearchFilter filter) {
      this.filter = filter;
    }

    @Override
    public Evaluation evaluate(Path path) {
      Relationship rel = path.lastRelationship();
      Node node = path.endNode();
      if (rel == null) {
        return Evaluation.EXCLUDE_AND_CONTINUE;
      } else if (rel.isType(RTreeRelationshipTypes.RTREE_CHILD)) {
        boolean shouldContinue = filter.needsToVisit(getIndexNodeEnvelope(node));
        if (shouldContinue)
          monitor.matchedTreeNode(path.length(), node);
        monitor.addCase(shouldContinue ? "Index Matches" : "Index Does NOT Match");
        return shouldContinue ? Evaluation.EXCLUDE_AND_CONTINUE : Evaluation.EXCLUDE_AND_PRUNE;
      } else if (rel.isType(RTreeRelationshipTypes.RTREE_REFERENCE)) {
        boolean found = filter.geometryMatches(node);
        monitor.addCase(found ? "Geometry Matches" : "Geometry Does NOT Match");
        if (found)
          monitor.setHeight(path.length());
        return found ? Evaluation.INCLUDE_AND_PRUNE : Evaluation.EXCLUDE_AND_PRUNE;
      }
      return null;
    }
  }

  public SearchResults searchIndex(SearchFilter filter) {
    try (Transaction tx = database.beginTx()) {
      SearchEvaluator searchEvaluator = new SearchEvaluator(filter);
      TraversalDescription td = database.traversalDescription().depthFirst()
          .relationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING)
          .relationships(RTreeRelationshipTypes.RTREE_REFERENCE, Direction.OUTGOING)
          .evaluator(searchEvaluator);
      Traverser traverser = td.traverse(getIndexRoot());
      SearchResults results = new SearchResults(traverser.nodes());
      tx.success();
      return results;
    }
  }

  public void visit(SpatialIndexVisitor visitor, Node indexNode) {
    if (!visitor.needsToVisit(getIndexNodeEnvelope(indexNode))) {
      return;
    }

    try (Transaction tx = database.beginTx()) {
      if (indexNode.hasRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING)) {
        // Node is not a leaf
        for (Relationship rel : indexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD,
            Direction.OUTGOING)) {
          Node child = rel.getEndNode();
          // collect children results
          visit(visitor, child);
        }
      } else if (indexNode.hasRelationship(RTreeRelationshipTypes.RTREE_REFERENCE,
          Direction.OUTGOING)) {
        // Node is a leaf
        for (Relationship rel : indexNode.getRelationships(RTreeRelationshipTypes.RTREE_REFERENCE,
            Direction.OUTGOING)) {
          visitor.onIndexReference(rel.getEndNode());
        }
      }
      tx.success();
    }
  }

  /**
   * Get the root node of RTree. It is the highest node with bbox.
   *
   * @return
   */
  public Node getIndexRoot() {
    try (Transaction tx = database.beginTx()) {
      Node indexRoot =
          getRootNode().getSingleRelationship(RTreeRelationshipTypes.RTREE_ROOT, Direction.OUTGOING)
              .getEndNode();
      tx.success();
      return indexRoot;
    }
  }

  // Private methods

  /***
   * This will get the envelope of the child. The relationshipType acts as as flag to allow the
   * function to know whether the child is a leaf or an index node.
   */
  private Envelope getChildNodeEnvelope(Node child, RelationshipType relType) {
    if (relType.name().equals(RTreeRelationshipTypes.RTREE_REFERENCE.name())) {
      return getLeafNodeEnvelope(child);
    } else {
      return getIndexNodeEnvelope(child);
    }
  }

  /**
   * The leaf nodes belong to the domain model, and as such need to use the layers domain-specific
   * GeometryEncoder for decoding the envelope.
   */
  public Envelope getLeafNodeEnvelope(Node geomNode) {
    return envelopeDecoder.decodeEnvelope(geomNode);
  }

  /**
   * The index nodes do NOT belong to the domain model, and as such need to use the indexes internal
   * knowledge of the index tree and node structure for decoding the envelope.
   */
  public Envelope getIndexNodeEnvelope(Node indexNode) {
    if (indexNode == null) {
      indexNode = getIndexRoot();
    }
    try (Transaction tx = database.beginTx()) {
      if (!indexNode.hasProperty(INDEX_PROP_BBOX)) {
        // this is ok after an index node split
        tx.success();
        return null;
      }

      double[] bbox = (double[]) indexNode.getProperty(INDEX_PROP_BBOX);
      tx.success();
      // Envelope parameters: xmin, xmax, ymin, ymax
      return new Envelope(bbox[0], bbox[2], bbox[1], bbox[3]);
    }
  }

  private void visitInTx(SpatialIndexVisitor visitor, Long indexNodeId) {
    Node indexNode = database.getNodeById(indexNodeId);
    if (!visitor.needsToVisit(getIndexNodeEnvelope(indexNode))) {
      return;
    }

    if (indexNode.hasRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING)) {
      // Node is not a leaf

      // collect children
      List<Long> children = new ArrayList<>();
      for (Relationship rel : indexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD,
          Direction.OUTGOING)) {
        children.add(rel.getEndNode().getId());
      }


      // visit children
      for (Long child : children) {
        visitInTx(visitor, child);
      }
    } else if (indexNode.hasRelationship(RTreeRelationshipTypes.RTREE_REFERENCE,
        Direction.OUTGOING)) {
      // Node is a leaf
      try (Transaction tx = database.beginTx()) {
        for (Relationship rel : indexNode.getRelationships(RTreeRelationshipTypes.RTREE_REFERENCE,
            Direction.OUTGOING)) {
          visitor.onIndexReference(rel.getEndNode());
        }

        tx.success();
      }
    }
  }

  private void initIndexMetadata() {
    Node layerNode = getRootNode();
    if (layerNode.hasRelationship(RTreeRelationshipTypes.RTREE_METADATA, Direction.OUTGOING)) {
      // metadata already present
      metadataNode =
          layerNode.getSingleRelationship(RTreeRelationshipTypes.RTREE_METADATA, Direction.OUTGOING)
              .getEndNode();

      maxNodeReferences = (Integer) metadataNode.getProperty("maxNodeReferences");
    } else {
      // metadata initialization
      metadataNode = database.createNode();
      layerNode.createRelationshipTo(metadataNode, RTreeRelationshipTypes.RTREE_METADATA);

      metadataNode.setProperty("maxNodeReferences", maxNodeReferences);
    }

    saveCount();
  }

  private void initIndexRoot() {
    Node layerNode = getRootNode();
    if (!layerNode.hasRelationship(RTreeRelationshipTypes.RTREE_ROOT, Direction.OUTGOING)) {
      // index initialization
      Node root = database.createNode();
      layerNode.createRelationshipTo(root, RTreeRelationshipTypes.RTREE_ROOT);
    }
  }

  private Node getMetadataNode() {
    if (metadataNode == null) {
      metadataNode = getRootNode()
          .getSingleRelationship(RTreeRelationshipTypes.RTREE_METADATA, Direction.OUTGOING)
          .getEndNode();
    }

    return metadataNode;
  }

  /**
   * Save the geometry count to the database if it has not been saved yet. However, if the count is
   * zero, first do an exhaustive search of the tree and count everything before saving it.
   */
  private void saveCount() {
    if (totalGeometryCount == 0) {
      SpatialIndexRecordCounter counter = new SpatialIndexRecordCounter();
      visit(counter, getIndexRoot());
      totalGeometryCount = counter.getResult();

      int savedGeometryCount = (int) getMetadataNode().getProperty("totalGeometryCount", 0);
      countSaved = savedGeometryCount == totalGeometryCount;
    }

    if (!countSaved) {
      try (Transaction tx = database.beginTx()) {
        getMetadataNode().setProperty("totalGeometryCount", totalGeometryCount);
        countSaved = true;
        tx.success();
      }
    }
  }

  private boolean nodeIsLeaf(Node node) {
    return !node.hasRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
  }

  /**
   * General function for {@code indexNodes} are non-leaf and leaf nodes.
   *
   * @param parentIndexNode
   * @param geomRootNode
   * @param pathNeighbors
   * @return
   */
  private Node chooseSubTreeSmallestGSD(Node parentIndexNode, Node geomRootNode,
      Map<String, int[]> pathNeighbors) {
    // Get all the children through RTREE_CHILD
    List<Node> indexNodes = new ArrayList<>();
    Iterable<Relationship> relationships =
        parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
    for (Relationship relation : relationships) {
      Node indexNode = relation.getEndNode();
      indexNodes.add(indexNode);
    }
    boolean isLeaf = nodeIsLeaf(indexNodes.get(0));
    Map<String, int[]> locInGraph = null;
    if (!spatialOnly && isLeaf) {
      locInGraph = pathNeighbors;
    }

    List<Node> nodesWithSmallestGSD = new ArrayList<>();
    // pick the child that needs the minimum enlargement to include the new geometry
    double minimumEnlargement = Double.POSITIVE_INFINITY;

    // for evaluation comparison purpose, yuhan
    double minimumEnlargementSpatial = Double.POSITIVE_INFINITY;
    List<Node> minimumNodesSpatial = new LinkedList<>();

    for (Node indexNode : indexNodes) {
      double enlargementNeeded;
      if (getIndexNodeEnvelope(indexNode).contains(getLeafNodeEnvelope(geomRootNode))) {
        enlargementNeeded = 0;
      } else {
        enlargementNeeded = getAreaEnlargement(indexNode, geomRootNode);
      }

      // Util.println("before: " + enlargementNeeded);

      // for comparison, yuhan
      if (enlargementNeeded < minimumEnlargementSpatial) {
        minimumNodesSpatial.clear();
        minimumNodesSpatial.add(indexNode);
        minimumEnlargementSpatial = enlargementNeeded;
      } else if (enlargementNeeded == minimumEnlargementSpatial) {
        minimumNodesSpatial.add(indexNode);
      }

      if (enlargementNeeded > minimumEnlargement) {
        continue;
      }

      // yuhan
      // if graph influence is not zero
      if (!spatialOnly && isLeaf) {
        int GD = getExpandGD(indexNode, locInGraph);

        // double normSD = enlargementNeeded / spatialNorm;
        // double normGD = (double) GD / graphNodeCount;
        // String logLine = String.format("normSD: %s, normGD: %s", Double.toString(normSD),
        // Double.toString(normGD));
        // Util.println(logLine);
        enlargementNeeded = getGSDGeneral(GD, enlargementNeeded);
      }

      // Util.println("after: " + enlargementNeeded);
      if (enlargementNeeded < minimumEnlargement) {
        nodesWithSmallestGSD.clear();
        nodesWithSmallestGSD.add(indexNode);
        minimumEnlargement = enlargementNeeded;
      } else if (enlargementNeeded == minimumEnlargement) {
        // Util.println("equal here");
        nodesWithSmallestGSD.add(indexNode);
      }
    }
    if (nodesWithSmallestGSD.size() > 1) {
      // This happens very rarely because it requires two enlargement to be exactly the same. But it
      // often happen when alpha = 0. Because only graphDist is considered in that case.
      tieBreakFailCount++;
      return chooseIndexNodeWithSmallestArea(nodesWithSmallestGSD);
    } else if (nodesWithSmallestGSD.size() == 1) {
      // for comparison, yuhan
      if (nodesWithSmallestGSD.get(0).equals(minimumNodesSpatial.get(0))) {
        noContainSame++;
      } else {
        noContainDifferent++;
      }
      return nodesWithSmallestGSD.get(0);
    } else {
      // this shouldn't happen
      throw new RuntimeException("No IndexNode found for new geometry");
    }
  }

  /**
   * First compute the node that contain the {@code geomRootNode}. If |overlap| > 1,
   * {@code chooseIndexnodeWithSmallestGD}.
   * 
   * @param parentIndexNode
   * @param geomRootNode
   * @param pathNeighbors
   * @return
   */
  private Node chooseSubTree(Node parentIndexNode, Node geomRootNode,
      Map<String, int[]> pathNeighbors) {
    // children that can contain the new geometry
    List<Node> indexNodes = new ArrayList<>();

    // pick the child that contains the new geometry bounding box
    Iterable<Relationship> relationships =
        parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
    for (Relationship relation : relationships) {
      Node indexNode = relation.getEndNode();
      // yuhan
      if (getIndexNodeEnvelope(indexNode).contains(getLeafNodeEnvelope(geomRootNode))) {
        indexNodes.add(indexNode);
      }
    }

    // more than one leaf nodes contains geomRootNode
    if (indexNodes.size() > 1) {
      // return chooseIndexNodeWithSmallestArea(indexNodes);
      // yuhan
      Node node = chooseIndexNodeWithSmallestArea(indexNodes);
      // if (!spatialOnly)
      if (nodeIsLeaf(indexNodes.get(0))) {
        chooseSmallestGDCount++;
        Node nodeWithSmallestGD = chooseIndexnodeWithSmallestGD(indexNodes, pathNeighbors);
        if (node.equals(nodeWithSmallestGD) == false) {
          differentTimes++;
        }
        node = nodeWithSmallestGD;
      }
      return node;
    } else if (indexNodes.size() == 1) {
      return indexNodes.get(0);
    }

    // No leaf node contains geomRootNode
    Map<String, int[]> locInGraph = null;
    if (!spatialOnly) {
      locInGraph = pathNeighbors;
    }
    // pick the child that needs the minimum enlargement to include the new geometry
    double minimumEnlargement = Double.POSITIVE_INFINITY;

    // for evaluation comparison purpose, yuhan
    noContainCount++;
    double minimumEnlargementSpatial = Double.POSITIVE_INFINITY;
    List<Node> minimumNodesSpatial = new LinkedList<>();

    relationships =
        parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
    for (Relationship relation : relationships) {
      Node indexNode = relation.getEndNode();
      double enlargementNeeded = getAreaEnlargement(indexNode, geomRootNode);

      // for comparison, yuhan
      if (enlargementNeeded < minimumEnlargementSpatial) {
        minimumNodesSpatial.clear();
        minimumNodesSpatial.add(indexNode);
        minimumEnlargementSpatial = enlargementNeeded;
      } else if (enlargementNeeded == minimumEnlargementSpatial) {
        minimumNodesSpatial.add(indexNode);
      }

      // yuhan
      // if graph influence is not zero
      if (!spatialOnly && nodeIsLeaf(indexNode)) {
        int GD = getExpandGD(indexNode, locInGraph);
        enlargementNeeded = getGSDGeneral(GD, enlargementNeeded);
      }

      if (enlargementNeeded < minimumEnlargement) {
        indexNodes.clear();
        indexNodes.add(indexNode);
        minimumEnlargement = enlargementNeeded;
      } else if (enlargementNeeded == minimumEnlargement) {
        indexNodes.add(indexNode);
      }
    }

    if (indexNodes.size() > 1) {
      // This happens very rarely because it requires two enlargement to be exactly the same. But it
      // often happen when alpha = 0. Because only graphDist is considered in that case.
      tieBreakFailCount++;
      return chooseIndexNodeWithSmallestArea(indexNodes);
    } else if (indexNodes.size() == 1) {
      // for comparison, yuhan
      if (indexNodes.get(0).equals(minimumNodesSpatial.get(0))) {
        noContainSame++;
      } else {
        noContainDifferent++;
      }

      return indexNodes.get(0);
    } else {
      // this shouldn't happen
      throw new RuntimeException("No IndexNode found for new geometry");
    }
  }

  private Node chooseSubTree(Node parentIndexNode, Node geomRootNode) {
    // children that can contain the new geometry
    List<Node> indexNodes = new ArrayList<>();

    // pick the child that contains the new geometry bounding box
    Iterable<Relationship> relationships =
        parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
    for (Relationship relation : relationships) {
      Node indexNode = relation.getEndNode();
      if (getIndexNodeEnvelope(indexNode).contains(getLeafNodeEnvelope(geomRootNode))) {
        indexNodes.add(indexNode);
      }
    }

    if (indexNodes.size() > 1) {
      return chooseIndexNodeWithSmallestArea(indexNodes);
    } else if (indexNodes.size() == 1) {
      return indexNodes.get(0);
    }

    // pick the child that needs the minimum enlargement to include the new geometry
    double minimumEnlargement = Double.POSITIVE_INFINITY;
    relationships =
        parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
    for (Relationship relation : relationships) {
      Node indexNode = relation.getEndNode();
      double enlargementNeeded = getAreaEnlargement(indexNode, geomRootNode);

      if (enlargementNeeded < minimumEnlargement) {
        indexNodes.clear();
        indexNodes.add(indexNode);
        minimumEnlargement = enlargementNeeded;
      } else if (enlargementNeeded == minimumEnlargement) {
        indexNodes.add(indexNode);
      }
    }

    if (indexNodes.size() > 1) {
      return chooseIndexNodeWithSmallestArea(indexNodes);
    } else if (indexNodes.size() == 1) {
      return indexNodes.get(0);
    } else {
      // this shouldn't happen
      throw new RuntimeException("No IndexNode found for new geometry");
    }
  }

  // private Node chooseSubTree(Node parentIndexNode, Node geomRootNode) {
  // // children that can contain the new geometry
  // List<Node> indexNodes = new ArrayList<>();
  //
  // // pick the child that contains the new geometry bounding box
  // Iterable<Relationship> relationships =
  // parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
  // for (Relationship relation : relationships) {
  // Node indexNode = relation.getEndNode();
  // if (getIndexNodeEnvelope(indexNode).contains(getLeafNodeEnvelope(geomRootNode))) {
  // indexNodes.add(indexNode);
  // }
  // }
  //
  // // more than one leaf nodes contains geomRootNode
  // if (indexNodes.size() > 1) {
  // // return chooseIndexNodeWithSmallestArea(indexNodes);
  // // yuhan
  // Node node = chooseIndexNodeWithSmallestArea(indexNodes);
  // if (!spatialOnly && nodeIsLeaf(node)) {
  // chooseSmallestGDCount++;
  // Node nodeWithSmallestGD = chooseIndexnodeWithSmallestGD(indexNodes, geomRootNode);
  // if (node.equals(nodeWithSmallestGD) == false) {
  // differentTimes++;
  // }
  // node = nodeWithSmallestGD;
  // }
  // return node;
  // } else if (indexNodes.size() == 1) {
  // return indexNodes.get(0);
  // }
  //
  // // No leaf node contains geomRootNode
  // HashMap<String, int[]> locInGraph = null;
  // if (!spatialOnly) {
  // locInGraph = getLocInGraph(geomRootNode);
  // }
  // // pick the child that needs the minimum enlargement to include the new geometry
  // double minimumEnlargement = Double.POSITIVE_INFINITY;
  //
  // // for evaluation comparison purpose, yuhan
  // noContainCount++;
  // double minimumEnlargementSpatial = Double.POSITIVE_INFINITY;
  // List<Node> minimumNodesSpatial = new LinkedList<>();
  //
  // relationships =
  // parentIndexNode.getRelationships(RTreeRelationshipTypes.RTREE_CHILD, Direction.OUTGOING);
  //
  // // check whether current level is leaf to decide whether to consider the graph distance.
  // boolean isLeaf = false;
  // for (Relationship relationship : relationships) {
  // Node firstNode = relationship.getEndNode();
  // if (nodeIsLeaf(firstNode)) {
  // isLeaf = true;
  // }
  // break;
  // }
  //
  // for (Relationship relation : relationships) {
  // Node indexNode = relation.getEndNode();
  // double enlargementNeeded = getAreaEnlargement(indexNode, geomRootNode);
  //
  // // for comparison, yuhan
  // if (enlargementNeeded < minimumEnlargementSpatial) {
  // minimumNodesSpatial.clear();
  // minimumNodesSpatial.add(indexNode);
  // minimumEnlargementSpatial = enlargementNeeded;
  // } else if (enlargementNeeded == minimumEnlargementSpatial) {
  // minimumNodesSpatial.add(indexNode);
  // }
  //
  // // yuhan
  // if (!spatialOnly && isLeaf) {
  // int GD = getGD(indexNode, locInGraph);
  // enlargementNeeded = alpha * enlargementNeeded / (360 * 180)
  // + (1 - alpha) * (double) GD / Config.graphNodeCount;
  // }
  //
  // if (enlargementNeeded < minimumEnlargement) {
  // indexNodes.clear();
  // indexNodes.add(indexNode);
  // minimumEnlargement = enlargementNeeded;
  // } else if (enlargementNeeded == minimumEnlargement) {
  // indexNodes.add(indexNode);
  // }
  // }
  //
  // if (indexNodes.size() > 1) {
  // // This happens very rarely because it requires two enlargement to be exactly the same.
  // return chooseIndexNodeWithSmallestArea(indexNodes);
  // } else if (indexNodes.size() == 1) {
  // // for comparison, yuhan
  // if (indexNodes.get(0).equals(minimumNodesSpatial.get(0))) {
  // noContainSame++;
  // } else {
  // noContainDifferent++;
  // }
  //
  // return indexNodes.get(0);
  // } else {
  // // this shouldn't happen
  // throw new RuntimeException("No IndexNode found for new geometry");
  // }
  // }

  /**
   * Choose the node with the smallest GD. Since GD has many same values, use the area as a tie
   * breaker.
   *
   * @param indexNodes
   * @param pathNeighbors
   * @return
   */
  private Node chooseIndexnodeWithSmallestGD(List<Node> indexNodes,
      Map<String, int[]> pathNeighbors) {
    Node result = null;
    double smallestSGD = Double.MAX_VALUE;
    // Util.println("count: " + indexNodes.size());
    for (Node indexNode : indexNodes) {
      int GD = getExpandGD(indexNode, pathNeighbors);
      double area = getArea(getIndexNodeEnvelope(indexNode));
      double SGD = adjustThreshold * area + GD; // Solve tie breaker using the smallest area.
      // Util.println(String.format("%s: %d, %s", indexNode, GD, String.valueOf(SGD)));
      if (result == null || SGD < smallestSGD) {
        result = indexNode;
        smallestSGD = SGD;
      }
    }
    return result;
  }

  /**
   * Compute the GD by considering area of the indexNode. The reason is that GDs are often the same
   * for all indexNodes. To handle the equality problem, we consider the area of indexNode.
   * Coefficient of area is a very small value (0.000000001), so it only works when GDs are the
   * same. Make sure that the coefficient * max(area) < 1, it will work as wanted. The reason of
   * doing this is it can happen very often that no indexNodes share a common path neighbor with
   * geom. So use the area as the tiebreaker.
   *
   * @author yuhan
   * @param indexNodes
   * @param geomRootNode
   * @return
   */
  private Node chooseIndexnodeWithSmallestGD(List<Node> indexNodes, Node geomRootNode) {
    Node result = null;
    double smallestSGD = Double.MAX_VALUE;
    // Util.println("count: " + indexNodes.size());
    HashMap<String, int[]> pathNeighbors = getLocInGraph(geomRootNode);
    for (Node indexNode : indexNodes) {
      int GD = getExpandGD(indexNode, pathNeighbors);
      double area = getArea(getIndexNodeEnvelope(indexNode));
      double SGD = adjustThreshold * area + GD;
      // Util.println(String.format("%s: %d, %s", indexNode, GD, String.valueOf(SGD)));
      if (result == null || SGD < smallestSGD) {
        result = indexNode;
        smallestSGD = SGD;
      }
    }
    return result;
  }

  /**
   * Get the graph location of a node. It is currently represented by a histogram of 1-hop
   * neighbors.
   *
   * @param node
   * @return
   */
  private HashMap<String, int[]> getLocInGraph(Node node) {
    long start = System.currentTimeMillis();
    HashMap<String, int[]> pathNeighbors =
        (HashMap<String, int[]>) leafNodesPathNeighbors.get(node.getId());
    getLocInGraphTime += System.currentTimeMillis() - start;
    return pathNeighbors;
  }

  /**
   * Compute the PN expansion if insert the geom into the indexNode. Consider the ignored PN.
   * 
   * @param indexNode
   * @param pathNeighbors
   * @return
   */
  private int getExpandGD(Node indexNode, Map<String, int[]> pathNeighbors) {
    long start = System.currentTimeMillis();
    getGDCount++;
    long indexNodeId = indexNode.getId();
    Map<String, int[]> indexNodePathNeighbors = leafNodesPathNeighbors.get(indexNodeId);
    int GD = getExpandGD(indexNodePathNeighbors, pathNeighbors);
    getGDTime += System.currentTimeMillis() - start;
    return GD;
  }

  /**
   * Compute the expand if insert @{@code otherPN} into {@code basePN}. (e.g., {@code otherPNs} -
   * {@code basePNs}).
   *
   * @param basePNs
   * @param otherPNs
   * @return
   */
  private int getExpandGD(Map<String, int[]> basePNs, Map<String, int[]> otherPNs) {
    int expandCount = 0;
    Iterator<Entry<String, int[]>> iterator = otherPNs.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<String, int[]> entry = iterator.next();
      String key = entry.getKey();
      int[] basePN = basePNs.get(key);
      int[] otherPN = entry.getValue();
      if (basePN == null) {
        if (otherPN.length == 0) { // otherPN ignored
          expandCount += MaxPNSize;
        } else {
          expandCount += otherPNs.get(key).length;
        }
        continue;
      }

      if (basePN.length == 0) { // indexNode is ignored, GD keeps.
        continue;
      }

      // indexNodePN exists and not ignored.
      if (otherPN.length == 0) {
        expandCount += MaxPNSize - basePN.length;
      } else {
        // mark here, current is fine, may be improved. If the |difference+indexNodePN| > maxPNSize,
        // then this GD is too much.
        expandCount += Util.sortedArraysDifferenceCount(otherPNs.get(key), basePN);
      }
    }
    return expandCount;
  }

  private double getAreaEnlargement(Node indexNode, Node geomRootNode) {
    Envelope before = getIndexNodeEnvelope(indexNode);

    Envelope after = getLeafNodeEnvelope(geomRootNode);
    after.expandToInclude(before);

    return getArea(after) - getArea(before);
  }

  private Node chooseIndexNodeWithSmallestArea(List<Node> indexNodes) {
    Node result = null;
    double smallestArea = -1;

    for (Node indexNode : indexNodes) {
      double area = getArea(getIndexNodeEnvelope(indexNode));
      if (result == null || area < smallestArea) {
        result = indexNode;
        smallestArea = area;
      }
    }

    return result;
  }

  private int countChildren(Node indexNode, RelationshipType relationshipType) {
    int counter = 0;
    for (Relationship ignored : indexNode.getRelationships(relationshipType, Direction.OUTGOING)) {
      counter++;
    }
    return counter;
  }

  /**
   * Insert a object into a leaf node. Will adjust MBR and graphloc if necessary in addChild()
   * function.
   *
   * @param indexNode the leaf node
   * @param geomRootNode the spatial object
   * @param pathNeighbors path neighbor of the <code>geomRootNode</code>
   * @return
   */
  private boolean insertInLeaf(Node indexNode, Node geomRootNode,
      Map<String, int[]> pathNeighbors) {
    return addChild(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE, geomRootNode, pathNeighbors);
  }

  /**
   * Insert a geomRootNode to the indexNode. Will adjust MBR and graphloc if necessary in addChild()
   * function.
   *
   * @param indexNode a leaf node
   * @param geomRootNode the object to be inserted
   * @return is enlargement needed?
   */
  private boolean insertInLeaf(Node indexNode, Node geomRootNode) {
    return addChild(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE, geomRootNode);
  }

  /**
   * Currently split using the original algorithm, but adjust PN accordingly.
   *
   * @param indexNode
   */
  private void splitAndAdjustPathBoundingBox(Node indexNode) {
    // create a new node and distribute the entries.
    // entries are distributed evenly into indexNode and newIndexNode respectively.
    // LOGGER.info("greenesSplit");
    // PN is adjusted if indexNode is leaf nodes.
    Node newIndexNode =
        splitMode.equals(GREENES_SPLIT) ? greenesSplit(indexNode) : quadraticSplitRiso(indexNode);
    Node parent = getIndexNodeParent(indexNode);
    // System.out.println("spitIndex " + newIndexNode.getId());
    // System.out.println("parent " + parent.getId());
    if (parent == null) {
      // if indexNode is the root, create a new root, maintain the RTree structure and MBR.
      // LOGGER.info("createNewRoot");
      // MBR is adjusted here and no PN adjustment is needed.
      createNewRoot(indexNode, newIndexNode);
    } else {
      expandParentBoundingBoxAfterNewChild(parent,
          (double[]) indexNode.getProperty(INDEX_PROP_BBOX));

      addChild(parent, RTreeRelationshipTypes.RTREE_CHILD, newIndexNode);

      if (countChildren(parent, RTreeRelationshipTypes.RTREE_CHILD) > maxNodeReferences) {
        splitAndAdjustPathBoundingBox(parent);
      } else {
        adjustPathBoundingBox(parent);
      }
    }
    monitor.addSplit(newIndexNode);
  }

  private Node quadraticSplit(Node indexNode) {
    if (nodeIsLeaf(indexNode)) {
      return quadraticSplit(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE);
    } else {
      return quadraticSplit(indexNode, RTreeRelationshipTypes.RTREE_CHILD);
    }
  }

  private Node quadraticSplitRiso(Node indexNode) {
    if (nodeIsLeaf(indexNode)) {
      return quadraticSplitRiso(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE);
    } else {
      return quadraticSplitRiso(indexNode, RTreeRelationshipTypes.RTREE_CHILD);
    }
  }

  private Node greenesSplit(Node indexNode) {
    if (nodeIsLeaf(indexNode)) {
      // LOGGER.info("nodeIsLeaf case");
      return greenesSplit(indexNode, RTreeRelationshipTypes.RTREE_REFERENCE);
    } else {
      // LOGGER.info("node Is not leaf case");
      return greenesSplit(indexNode, RTreeRelationshipTypes.RTREE_CHILD);
    }
  }

  private NodeWithEnvelope[] mostDistantByDeadSpace(List<NodeWithEnvelope> entries) {
    NodeWithEnvelope seed1 = entries.get(0);
    NodeWithEnvelope seed2 = entries.get(0);
    double worst = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < entries.size(); ++i) {
      NodeWithEnvelope e = entries.get(i);
      for (int j = i + 1; j < entries.size(); ++j) {
        NodeWithEnvelope e1 = entries.get(j);
        double deadSpace = e.envelope.separation(e1.envelope);
        if (deadSpace > worst) {
          worst = deadSpace;
          seed1 = e;
          seed2 = e1;
        }
      }
    }
    return new NodeWithEnvelope[] {seed1, seed2};
  }

  /**
   * Consider both dead space and graph distance.
   *
   * @param entries
   * @return
   */
  private NodeWithEnvelope[] mostDistantByDeadSpaceRiso(List<NodeWithEnvelope> entries,
      Map<Node, Map<String, int[]>> childNodePNs) {
    NodeWithEnvelope seed1 = entries.get(0);
    NodeWithEnvelope seed2 = entries.get(0);
    double worst = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < entries.size(); ++i) {
      NodeWithEnvelope e = entries.get(i);
      Map<String, int[]> basePNs = childNodePNs.get(e.node);
      for (int j = i + 1; j < entries.size(); ++j) {
        NodeWithEnvelope e1 = entries.get(j);
        double deadSpace = e.envelope.separation(e1.envelope);
        if (!spatialOnly) {
          Map<String, int[]> otherPNs = childNodePNs.get(e1.node);
          int graphDist = getExpandGD(basePNs, otherPNs);
          graphDist += getExpandGD(otherPNs, basePNs);
          deadSpace = getGSD(graphDist, deadSpace);
        }
        if (deadSpace > worst) {
          worst = deadSpace;
          seed1 = e;
          seed2 = e1;
        }
      }
    }
    return new NodeWithEnvelope[] {seed1, seed2};
  }

  private int findLongestDimension(List<NodeWithEnvelope> entries) {
    Envelope env = new Envelope();
    for (NodeWithEnvelope entry : entries) {
      env.expandToInclude(entry.envelope);
    }
    int longestDimension = 0;
    double maxWidth = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < env.getDimension(); i++) {
      double width = env.getWidth(i);
      if (width > maxWidth) {
        maxWidth = width;
        longestDimension = i;
      }
    }
    return longestDimension;
  }

  private List<NodeWithEnvelope> extractChildNodesWithEnvelopes(Node indexNode,
      RelationshipType relationshipType) {
    List<NodeWithEnvelope> entries = new ArrayList<>();

    Iterable<Relationship> relationships =
        indexNode.getRelationships(relationshipType, Direction.OUTGOING);
    for (Relationship relationship : relationships) {
      Node node = relationship.getEndNode();
      entries.add(new NodeWithEnvelope(node, getChildNodeEnvelope(node, relationshipType)));
      relationship.delete();
    }
    return entries;
  }

  /**
   * Get a map of <childNode, PNs>.
   *
   * @param indexNode
   * @param relationshipType
   * @return {@code null} if child nodes do not have PN.
   */
  private Map<Node, Map<String, int[]>> extractChildNodesWithPNs(Node indexNode,
      RelationshipType relationshipType) {
    Map<Node, Map<String, int[]>> childNodesPNs = new HashMap<>();
    Iterable<Relationship> relationships =
        indexNode.getRelationships(relationshipType, Direction.OUTGOING);
    // if indexNode is leaf node
    if (relationshipType.equals(RTreeRelationshipTypes.RTREE_REFERENCE)) {
      for (Relationship relationship : relationships) {
        Node childNode = relationship.getEndNode();
        long id = childNode.getId();
        childNodesPNs.put(childNode, spatialNodesPathNeighbors.get((int) id));
      }
      return childNodesPNs;
    }

    Node firstChild = relationships.iterator().next().getEndNode();
    // if child is leaf layer
    if (nodeIsLeaf(firstChild)) {
      for (Relationship relationship : relationships) {
        Node childnode = relationship.getEndNode();
        long id = childnode.getId();
        childNodesPNs.put(childnode, leafNodesPathNeighbors.get(id));
      }
      return childNodesPNs;
    }

    // children do not have PN.
    return null;
  }

  /**
   *
   * @param indexNode can be leaf or non-leaf node
   * @param relationshipType can be {@code RTREE_CHILD} or {@code RTREE_REFERENCE}
   * @return a new IndexNode that contains the second part of children
   */
  private Node greenesSplit(Node indexNode, RelationshipType relationshipType) {
    // Disconnect all current children from the index and return them with their envelopes
    List<NodeWithEnvelope> entries = extractChildNodesWithEnvelopes(indexNode, relationshipType);

    // We want to split by the longest dimension to avoid degrading into extremely thin envelopes
    int longestDimension = findLongestDimension(entries);

    // Sort the entries by the longest dimension and then create envelopes around left and right
    // halves
    entries.sort(new SingleDimensionNodeEnvelopeComparator(longestDimension));
    int splitAt = entries.size() / 2;
    List<NodeWithEnvelope> left = entries.subList(0, splitAt);
    List<NodeWithEnvelope> right = entries.subList(splitAt, entries.size());

    // LOGGER.info("reconnectTwoChildGroups");
    return reconnectTwoChildGroups(indexNode, left, right, relationshipType);
  }

  private static class SingleDimensionNodeEnvelopeComparator
      implements Comparator<NodeWithEnvelope> {
    private final int dimension;

    public SingleDimensionNodeEnvelopeComparator(int dimension) {
      this.dimension = dimension;
    }

    @Override
    public int compare(NodeWithEnvelope o1, NodeWithEnvelope o2) {
      double length = o2.envelope.centre(dimension) - o1.envelope.centre(dimension);
      if (length < 0.0)
        return -1;
      else if (length > 0.0)
        return 1;
      else
        return 0;
    }
  }

  private Node quadraticSplit(Node indexNode, RelationshipType relationshipType) {
    // Disconnect all current children from the index and return them with their envelopes
    List<NodeWithEnvelope> entries = extractChildNodesWithEnvelopes(indexNode, relationshipType);

    // pick two seed entries such that the dead space is maximal
    NodeWithEnvelope[] seeds = mostDistantByDeadSpace(entries);

    List<NodeWithEnvelope> group1 = new ArrayList<>();
    group1.add(seeds[0]);
    Envelope group1envelope = seeds[0].envelope;

    List<NodeWithEnvelope> group2 = new ArrayList<>();
    group2.add(seeds[1]);
    Envelope group2envelope = seeds[1].envelope;

    entries.remove(seeds[0]);
    entries.remove(seeds[1]);
    while (entries.size() > 0) {
      // compute the cost of inserting each entry
      List<NodeWithEnvelope> bestGroup = null;
      Envelope bestGroupEnvelope = null;
      NodeWithEnvelope bestEntry = null;
      double expansionMin = Double.POSITIVE_INFINITY;
      for (NodeWithEnvelope e : entries) {
        double expansion1 =
            getArea(createEnvelope(e.envelope, group1envelope)) - getArea(group1envelope);
        double expansion2 =
            getArea(createEnvelope(e.envelope, group2envelope)) - getArea(group2envelope);

        if (expansion1 < expansion2 && expansion1 < expansionMin) {
          bestGroup = group1;
          bestGroupEnvelope = group1envelope;
          bestEntry = e;
          expansionMin = expansion1;
        } else if (expansion2 < expansion1 && expansion2 < expansionMin) {
          bestGroup = group2;
          bestGroupEnvelope = group2envelope;
          bestEntry = e;
          expansionMin = expansion2;
        } else if (expansion1 == expansion2 && expansion1 < expansionMin) {
          // in case of equality choose the group with the smallest area
          if (getArea(group1envelope) < getArea(group2envelope)) {
            bestGroup = group1;
            bestGroupEnvelope = group1envelope;
          } else {
            bestGroup = group2;
            bestGroupEnvelope = group2envelope;
          }
          bestEntry = e;
          expansionMin = expansion1;
        }
      }

      if (bestEntry == null) {
        throw new RuntimeException(
            "Should not be possible to fail to find a best entry during quadratic split");
      } else {
        // insert the best candidate entry in the best group
        bestGroup.add(bestEntry);
        bestGroupEnvelope.expandToInclude(bestEntry.envelope);

        entries.remove(bestEntry);
      }
    }

    return reconnectTwoChildGroups(indexNode, group1, group2, relationshipType);
  }

  private Node quadraticSplitRiso(Node indexNode, RelationshipType relationshipType) {
    Map<Node, Map<String, int[]>> childNodePNs =
        extractChildNodesWithPNs(indexNode, relationshipType);

    // Disconnect all current children from the index and return them with their envelopes
    List<NodeWithEnvelope> entries = extractChildNodesWithEnvelopes(indexNode, relationshipType);

    // pick two seed entries such that the dead space (considering graph distance) is maximal
    NodeWithEnvelope[] seeds = null;
    if (childNodePNs == null) {
      seeds = mostDistantByDeadSpace(entries);
    } else {
      seeds = mostDistantByDeadSpaceRiso(entries, childNodePNs);
    }

    List<NodeWithEnvelope> group1 = new ArrayList<>();
    group1.add(seeds[0]);
    Envelope group1envelope = seeds[0].envelope;

    List<NodeWithEnvelope> group2 = new ArrayList<>();
    group2.add(seeds[1]);
    Envelope group2envelope = seeds[1].envelope;

    Map<String, int[]> group1PN = null;
    Map<String, int[]> group2PN = null;
    if (childNodePNs != null) {
      group1PN = new HashMap<>(childNodePNs.get(seeds[0].node));
      group2PN = new HashMap<>(childNodePNs.get(seeds[1].node));
    }

    entries.remove(seeds[0]);
    entries.remove(seeds[1]);
    while (entries.size() > 0) {
      // compute the cost of inserting each entry
      List<NodeWithEnvelope> bestGroup = null;
      Envelope bestGroupEnvelope = null;
      NodeWithEnvelope bestEntry = null;

      if (group1.size() >= maxNodeReferences - minNodeReferences) {
        bestGroup = group2;
        bestEntry = entries.get(0);
        bestGroupEnvelope = bestEntry.envelope;
      } else if (group2.size() > maxNodeReferences - minNodeReferences) {
        bestGroup = group1;
        bestEntry = entries.get(0);
        bestGroupEnvelope = bestEntry.envelope;
      } else {
        double expansionMin = Double.POSITIVE_INFINITY;
        for (NodeWithEnvelope e : entries) {
          double expansion1 =
              getArea(createEnvelope(e.envelope, group1envelope)) - getArea(group1envelope);
          if (!spatialOnly && childNodePNs != null) {
            int expandPN = getExpandGD(group1PN, childNodePNs.get(e.node));
            expansion1 = getGSDGeneral(expandPN, expansion1);
          }
          double expansion2 =
              getArea(createEnvelope(e.envelope, group2envelope)) - getArea(group2envelope);
          if (!spatialOnly && childNodePNs != null) {
            int expandPN = getExpandGD(group2PN, childNodePNs.get(e.node));
            expansion2 = getGSDGeneral(expandPN, expansion2);
          }

          if (expansion1 < expansion2 && expansion1 < expansionMin) {
            bestGroup = group1;
            bestGroupEnvelope = group1envelope;
            bestEntry = e;
            expansionMin = expansion1;
          } else if (expansion2 < expansion1 && expansion2 < expansionMin) {
            bestGroup = group2;
            bestGroupEnvelope = group2envelope;
            bestEntry = e;
            expansionMin = expansion2;
          } else if (expansion1 == expansion2 && expansion1 < expansionMin) {
            // in case of equality choose the group with the smallest area
            if (getArea(group1envelope) < getArea(group2envelope)) {
              bestGroup = group1;
              bestGroupEnvelope = group1envelope;
            } else {
              bestGroup = group2;
              bestGroupEnvelope = group2envelope;
            }
            bestEntry = e;
            expansionMin = expansion1;
          }
        }
      }

      if (bestEntry == null) {
        throw new RuntimeException(
            "Should not be possible to fail to find a best entry during quadratic split");
      } else {
        // insert the best candidate entry in the best group
        bestGroup.add(bestEntry);
        bestGroupEnvelope.expandToInclude(bestEntry.envelope);
        if (childNodePNs != null) {
          if (bestGroup == group1) {
            adjustGraphLoc(group1PN, childNodePNs.get(bestEntry.node));
          } else if (bestGroup == group2) {
            adjustGraphLoc(group2PN, childNodePNs.get(bestEntry.node));
          } else {
            throw new RuntimeException("best group is either 1 or 2!");
          }
        }
        entries.remove(bestEntry);
      }
    }

    return reconnectTwoChildGroups(indexNode, group1, group2, relationshipType);
  }

  private double getGSDGeneral(int expandPN, double spatialExpansion) {
    if (graphOnly) {
      return getGSDGraphOnly(expandPN, spatialExpansion);
    } else {
      return getGSD(expandPN, spatialExpansion);
    }
  }

  private double getGSD(int expandPN, double spatialExpansion) {
    return alpha * spatialExpansion / spatialNorm
        + (1 - alpha) * ((double) expandPN) / ((double) graphNodeCount);
  }

  private double getGSDGraphOnly(int expandPN, double spatialExpansion) {
    return adjustThreshold * spatialExpansion / spatialNorm
        + (double) expandPN / (double) graphNodeCount;
  }

  /**
   * Add group1 into indexNode and group2 into newIndexNode, including remove PN if indexNode is a
   * leaf node.
   *
   * @param indexNode
   * @param group1
   * @param group2
   * @param relationshipType
   * @return newIndexNode
   */
  private Node reconnectTwoChildGroups(Node indexNode, List<NodeWithEnvelope> group1,
      List<NodeWithEnvelope> group2, RelationshipType relationshipType) {
    // yuhan
    // remove the PN property and reconstruct in addChild() function
    if (!spatialOnly && relationshipType.equals(RTreeRelationshipTypes.RTREE_REFERENCE)) {
      leafNodesPathNeighbors.remove(indexNode.getId());
      leafNodesPathNeighbors.put(indexNode.getId(), new HashMap<>());
    }

    // LOGGER.info("add group1 into indexNode");
    // reset bounding box and add new children
    indexNode.removeProperty(INDEX_PROP_BBOX);
    for (NodeWithEnvelope entry : group1) {
      // LOGGER.info(String.format("addChild(%s, %s, %s)", indexNode, relationshipType,
      // entry.node));
      addChild(indexNode, relationshipType, entry.node);
    }

    // create new node from split
    Node newIndexNode = database.createNode();
    leafNodesPathNeighbors.put(newIndexNode.getId(), new HashMap<>());
    // LOGGER.info("add group2 into newIndexNode");
    for (NodeWithEnvelope entry : group2) {
      // LOGGER
      // .info(String.format("addChild(%s, %s, %s)", newIndexNode, relationshipType, entry.node));
      addChild(newIndexNode, relationshipType, entry.node);
    }

    return newIndexNode;
  }

  /**
   * Create a new node as the new root. Add oldRoot and newIndexNode as children of such new node.
   * Keep the schema of LayerNode-[:RTREE_ROOT]-RootNode correct. Remove the original relationship
   * to oldRoot and add a new relationship to newRoot. MBR is maintained and adjusted in addChild
   * function. Here we use addChild() no adjust version without considering PN because newRoot is
   * not a leaf node. This function does not need to adjust PN. It can be used for both versions. PN
   * exists on oldRoot and newIndexNode and should have been adjusted before going into this
   * function.
   *
   * @param oldRoot
   * @param newIndexNode
   */
  private void createNewRoot(Node oldRoot, Node newIndexNode) {
    // Create a new root and add oldRoot and newIndexNode as children.
    Node newRoot = database.createNode();
    addChild(newRoot, RTreeRelationshipTypes.RTREE_CHILD, oldRoot);
    addChild(newRoot, RTreeRelationshipTypes.RTREE_CHILD, newIndexNode);

    Node layerNode = getRootNode();
    // move the RTREE_ROOT relationship from the oldRoot to new Root.
    layerNode.getSingleRelationship(RTreeRelationshipTypes.RTREE_ROOT, Direction.OUTGOING).delete();
    layerNode.createRelationshipTo(newRoot, RTreeRelationshipTypes.RTREE_ROOT);
  }

  /**
   * Refer to the same-name function. The return only considers the MBR, not the PN because PN is
   * not recursely adjusted.
   *
   * @param parent
   * @param type
   * @param newChild
   * @param pathNeighbors
   * @return {@code parent} MBR changed or not
   */
  private boolean addChild(Node parent, RelationshipType type, Node newChild,
      Map<String, int[]> pathNeighbors) {
    // yuhan
    // only adustGraphLoc when the parent is a leaf node.
    if (spatialOnly == false && type.equals(RTreeRelationshipTypes.RTREE_REFERENCE)) {
      adjustGraphLoc(parent, pathNeighbors);
    }

    Envelope childEnvelope = getChildNodeEnvelope(newChild, type);
    double[] childBBox = new double[] {childEnvelope.getMinX(), childEnvelope.getMinY(),
        childEnvelope.getMaxX(), childEnvelope.getMaxY()};
    parent.createRelationshipTo(newChild, type);
    return expandParentBoundingBoxAfterNewChild(parent, childBBox);
  }

  /**
   * Add the child to a parent node by creating a relationship. Update the mbr accordingly. For
   * RisoTree (spatialOnly == false) update PNs. Only the leaf node will be adjusted for now. No
   * non-leaf node has PN.
   * 
   * @param parent
   * @param type
   * @param newChild
   * @return is the mbr of parent changed?
   */
  private boolean addChild(Node parent, RelationshipType type, Node newChild) {
    // yuhan
    // only adustGraphLoc when the parent is a leaf node.
    if (spatialOnly == false && type.equals(RTreeRelationshipTypes.RTREE_REFERENCE)) {
      adjustGraphLoc(parent, spatialNodesPathNeighbors.get((int) newChild.getId()));
    }

    Envelope childEnvelope = getChildNodeEnvelope(newChild, type);
    double[] childBBox = new double[] {childEnvelope.getMinX(), childEnvelope.getMinY(),
        childEnvelope.getMaxX(), childEnvelope.getMaxY()};
    parent.createRelationshipTo(newChild, type);
    return expandParentBoundingBoxAfterNewChild(parent, childBBox);
  }

  /**
   * Adjust the parent of {@code node}.
   *
   * @param node
   */
  private void adjustPathBoundingBox(Node node) {
    Node parent = getIndexNodeParent(node);
    if (parent != null) {
      if (adjustParentBoundingBox(parent, RTreeRelationshipTypes.RTREE_CHILD)) {
        // entry has been modified: adjust the path for the parent
        adjustPathBoundingBox(parent);
      }
    }
  }

  /**
   * Fix an IndexNode bounding box after a child has been added or removed removed. Return true if
   * something was changed so that parents can also be adjusted.
   */
  private boolean adjustParentBoundingBox(Node indexNode, RelationshipType relationshipType) {
    double[] old = null;
    if (indexNode.hasProperty(INDEX_PROP_BBOX)) {
      old = (double[]) indexNode.getProperty(INDEX_PROP_BBOX);
    }

    Envelope bbox = null;

    for (Relationship relationship : indexNode.getRelationships(relationshipType,
        Direction.OUTGOING)) {
      Node childNode = relationship.getEndNode();

      if (bbox == null) {
        bbox = new Envelope(getChildNodeEnvelope(childNode, relationshipType));
      } else {
        bbox.expandToInclude(getChildNodeEnvelope(childNode, relationshipType));
      }
    }

    if (bbox == null) {
      // this could happen in an empty tree
      bbox = new Envelope(0, 0, 0, 0);
    }

    if (old == null || old.length != 4 || bbox.getMinX() != old[0] || bbox.getMinY() != old[1]
        || bbox.getMaxX() != old[2] || bbox.getMaxY() != old[3]) {
      setIndexNodeEnvelope(indexNode, bbox);
      return true;
    } else {
      return false;
    }
  }

  protected void setIndexNodeEnvelope(Node indexNode, Envelope bbox) {
    indexNode.setProperty(INDEX_PROP_BBOX,
        new double[] {bbox.getMinX(), bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY()});
  }

  /**
   * Adjust IndexNode bounding box according to the new child inserted
   *
   * @param parent IndexNode
   * @param childBBox geomNode inserted
   * @return is bbox changed or not
   */
  protected boolean expandParentBoundingBoxAfterNewChild(Node parent, double[] childBBox) {
    if (!parent.hasProperty(INDEX_PROP_BBOX)) {
      parent.setProperty(INDEX_PROP_BBOX,
          new double[] {childBBox[0], childBBox[1], childBBox[2], childBBox[3]});
      return true;
    }

    double[] parentBBox = (double[]) parent.getProperty(INDEX_PROP_BBOX);

    boolean valueChanged = setMin(parentBBox, childBBox, 0);
    valueChanged = setMin(parentBBox, childBBox, 1) || valueChanged;
    valueChanged = setMax(parentBBox, childBBox, 2) || valueChanged;
    valueChanged = setMax(parentBBox, childBBox, 3) || valueChanged;

    if (valueChanged) {
      parent.setProperty(INDEX_PROP_BBOX, parentBBox);
    }

    return valueChanged;
  }

  private boolean setMin(double[] parent, double[] child, int index) {
    if (parent[index] > child[index]) {
      parent[index] = child[index];
      return true;
    } else {
      return false;
    }
  }

  private boolean setMax(double[] parent, double[] child, int index) {
    if (parent[index] < child[index]) {
      parent[index] = child[index];
      return true;
    } else {
      return false;
    }
  }

  private Node getIndexNodeParent(Node indexNode) {
    Relationship relationship =
        indexNode.getSingleRelationship(RTreeRelationshipTypes.RTREE_CHILD, Direction.INCOMING);
    if (relationship == null) {
      return null;
    } else {
      return relationship.getStartNode();
    }
  }

  private double getArea(Envelope e) {
    return e.getWidth() * e.getHeight();
    // TODO why not e.getArea(); ?
  }

  private void deleteRecursivelySubtree(Node node, Relationship incoming) {
    for (Relationship relationship : node.getRelationships(RTreeRelationshipTypes.RTREE_CHILD,
        Direction.OUTGOING)) {
      deleteRecursivelySubtree(relationship.getEndNode(), relationship);
    }
    if (incoming != null) {
      incoming.delete();
    }
    for (Relationship rel : node.getRelationships()) {
      System.out.println("Unexpected relationship found on " + node + ": " + rel.toString());
      rel.delete();
    }
    node.delete();
  }

  protected boolean isGeometryNodeIndexed(Node geomNode) {
    return geomNode.hasRelationship(RTreeRelationshipTypes.RTREE_REFERENCE, Direction.INCOMING);
  }

  protected Node findLeafContainingGeometryNode(Node geomNode) {
    return geomNode
        .getSingleRelationship(RTreeRelationshipTypes.RTREE_REFERENCE, Direction.INCOMING)
        .getStartNode();
  }

  protected boolean isIndexNodeInThisIndex(Node indexNode) {
    Node child = indexNode;
    Node root = null;
    while (root == null) {
      Node parent = getIndexNodeParent(child);
      if (parent == null) {
        root = child;
      } else {
        child = parent;
      }
    }
    return root.getId() == getIndexRoot().getId();
  }

  private void deleteNode(Node node) {
    for (Relationship r : node.getRelationships()) {
      r.delete();
    }
    node.delete();
  }

  /**
   * Get the layer node (one above the rootNode).
   *
   * @return
   */
  private Node getRootNode() {
    return rootNode;
  }

  /**
   * Create a bounding box encompassing the two bounding boxes passed in.
   */
  private static Envelope createEnvelope(Envelope e, Envelope e1) {
    Envelope result = new Envelope(e);
    result.expandToInclude(e1);
    return result;
  }

  // Attributes
  public GraphDatabaseService getDatabase() {
    return database;
  }

  private GraphDatabaseService database;

  private Node rootNode; // actually it is the layerNode.
  private EnvelopeDecoder envelopeDecoder;
  private int maxNodeReferences;
  private int minNodeReferences;
  // private String splitMode = GREENES_SPLIT;
  private String splitMode = QUADRATIC_SPLIT;
  private boolean shouldMergeTrees = false;

  private Node metadataNode;
  private int totalGeometryCount = 0;
  private boolean countSaved = false;

  private final static Logger LOGGER = Logger.getLogger(RTreeIndex.class.getName());

  // ########### RisoTree ###########
  /**
   * The value for spatial coefficient. Set to 1.0 if do not want to consider graph distance in the
   * noContain case.
   */
  private Double alpha = null;

  private Integer graphNodeCount = null;
  private Integer MaxPNSize = null;

  /*
   * Control whether the PN comes into effect. It is set along with alpha. If alpha = 1.0, this
   * should be true. Otherwise, false.
   */
  private Boolean spatialOnly = false;
  /**
   * If alpha = 0.0.
   */
  private Boolean graphOnly = null;

  static double onlyDecisionThreshold = 0.00000001;
  /**
   * If graph only, many SGD will be the same. So adjust the alpha to this value. It can ensure that
   * GD take
   */
  static double adjustThreshold = 0.000000000001;
  static final boolean outputLeafNodesPathNeighors = false;
  static double spatialNorm = 64800.0;

  private int chooseSmallestGDCount = 0;
  private int getGDCount = 0;

  /**
   * how many times GraphDist works when there are more than one nodes contain the geom object.
   */
  private int differentTimes = 0;

  /**
   * how many times the chooseSubTree() function goes into the no contain case.
   */
  private int noContainCount = 0;
  private int noContainSame = 0; // Spatial only and SGD takes the same subtree.
  private int noContainDifferent = 0; // Spatial only and SGD takes different subtrees.
  private int tieBreakFailCount = 0;

  public final static String PN_PROP_PREFFIX = "PN_";

  public Map<Long, Map<String, int[]>> leafNodesPathNeighbors = null;
  public List<Map<String, int[]>> spatialNodesPathNeighbors = null;

  // ******** tracking time *********/
  public long chooseSubTreeTime = 0;
  public long insertAndAdjustTime = 0;
  public long getLocInGraphTime = 0;
  public long getGDTime = 0;
  public long adjustGraphLocTime = 0;
  public long totalTime = 0;
  public long adjustWriteTime = 0;

  // Private classes
  private class WarmUpVisitor implements SpatialIndexVisitor {

    public boolean needsToVisit(Envelope indexNodeEnvelope) {
      return true;
    }

    public void onIndexReference(Node geomNode) {}
  }

  /**
   * In order to wrap one iterable or iterator in another that converts the objects from one type to
   * another without loading all into memory, we need to use this ugly java-magic. Man, I miss Ruby
   * right now!
   *
   * @author Craig
   */
  private class IndexNodeToGeometryNodeIterable implements Iterable<Node> {

    private Iterator<Node> allIndexNodeIterator;

    private class GeometryNodeIterator implements Iterator<Node> {

      Iterator<Node> geometryNodeIterator = null;

      public boolean hasNext() {
        checkGeometryNodeIterator();
        return geometryNodeIterator != null && geometryNodeIterator.hasNext();
      }

      public Node next() {
        checkGeometryNodeIterator();
        return geometryNodeIterator == null ? null : geometryNodeIterator.next();
      }

      private void checkGeometryNodeIterator() {
        TraversalDescription td = database.traversalDescription().depthFirst()
            .relationships(RTreeRelationshipTypes.RTREE_REFERENCE, Direction.OUTGOING)
            .evaluator(Evaluators.excludeStartPosition()).evaluator(Evaluators.toDepth(1));
        while ((geometryNodeIterator == null || !geometryNodeIterator.hasNext())
            && allIndexNodeIterator.hasNext()) {
          geometryNodeIterator = td.traverse(allIndexNodeIterator.next()).nodes().iterator();
        }
      }

      public void remove() {}
    }

    public IndexNodeToGeometryNodeIterable(Iterable<Node> allIndexNodes) {
      this.allIndexNodeIterator = allIndexNodes.iterator();
    }

    public Iterator<Node> iterator() {
      return new GeometryNodeIterator();
    }
  }

  private class IndexNodeAreaComparator implements Comparator<NodeWithEnvelope> {

    @Override
    public int compare(NodeWithEnvelope o1, NodeWithEnvelope o2) {
      return Double.compare(o1.envelope.getArea(), o2.envelope.getArea());
    }
  }
}
