package commons;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

public class ReadWriteUtil {

  private static final Logger LOGGER = Logger.getLogger(ReadWriteUtil.class.getName());

  /**
   * Read a map <Integer, String> from file and return as a String[]. Non-exist keys will have value
   * null. The purpose is to save storage overhead and accelerate the speed of the get operation.
   *
   * @param filepath
   * @param size should be >= than all integer keys in the map
   * @return
   * @throws Exception
   */
  public static String[] readMapAsArray(String filepath, int size) throws Exception {
    LOGGER.info(String.format("read Label map from %s with size %d", filepath, size));
    BufferedReader reader = new BufferedReader(new FileReader(filepath));
    String line = null;
    String[] map = new String[size];
    Arrays.fill(map, null);
    while ((line = reader.readLine()) != null) {
      String[] strings = line.split(",");
      int graphId = Integer.parseInt(strings[0]);
      if (map[graphId] == null) {
        map[graphId] = strings[1];
      }
    }
    reader.close();
    return map;
  }

  public static void writeEdges(Iterable<Edge> edges, String path, boolean app) throws IOException {
    FileWriter writer = new FileWriter(path, app);
    for (Edge edge : edges) {
      writer.write(edge.toString() + "\n");
    }
    writer.close();
  }

  public static <T> void WriteArray(String filename, List<T> arrayList) {
    FileWriter fileWriter = null;
    try {
      fileWriter = new FileWriter(new File(filename));
      for (T line : arrayList)
        fileWriter.write(line.toString() + "\n");
      fileWriter.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * read integer arraylist
   * 
   * @param path
   * @return
   */
  public static ArrayList<Integer> readIntegerArray(String path) {
    String line = null;
    ArrayList<Integer> arrayList = new ArrayList<Integer>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
      while ((line = reader.readLine()) != null) {
        int x = Integer.parseInt(line);
        arrayList.add(x);
      }
      reader.close();
    } catch (Exception e) {
      // TODO: handle exception
      e.printStackTrace();
    }
    return arrayList;
  }

  /**
   * Read map from file
   * 
   * @param filename
   * @return
   */
  public static HashMap<String, String> ReadMap(String filename) {
    try {
      HashMap<String, String> map = new HashMap<String, String>();
      BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
      String line = null;
      while ((line = reader.readLine()) != null) {
        String[] liStrings = line.split(",");
        map.put(liStrings[0], liStrings[1]);
      }
      reader.close();
      return map;
    } catch (Exception e) {
      // TODO: handle exception
      e.printStackTrace();
    }
    Util.println("nothing in ReadMap(" + filename + ")");
    return null;
  }

  /**
   * Read a map to a long[] array. So the key has to be consecutive in the [0, count-1].
   *
   * @param filename
   * @return
   */
  public static long[] readMapAsArray(String filename) {
    HashMap<String, String> graph_pos_map = ReadMap(filename);
    long[] graph_pos_map_list = new long[graph_pos_map.size()];
    for (String key_str : graph_pos_map.keySet()) {
      int key = Integer.parseInt(key_str);
      int pos_id = Integer.parseInt(graph_pos_map.get(key_str));
      graph_pos_map_list[key] = pos_id;
    }
    return graph_pos_map_list;
  }

  public static List<String> readFileAllLines(String filepath) throws Exception {
    List<String> lines = new LinkedList<>();
    BufferedReader reader = Util.getBufferedReader(filepath);
    String line = null;
    while ((line = reader.readLine()) != null) {
      lines.add(line);
    }
    return lines;
  }

  /**
   * Skip the line starting with {@code skipFlag}.
   *
   * @param filepath
   * @param skipFlag
   * @return
   * @throws Exception
   */
  public static List<String> readFileAllLines(String filepath, String skipFlag) throws Exception {
    List<String> lines = new LinkedList<>();
    BufferedReader reader = Util.getBufferedReader(filepath + ".txt");
    String line = null;
    while ((line = reader.readLine()) != null) {
      if (line.startsWith(skipFlag)) {
        continue;
      }
      lines.add(line);
    }
    return lines;
  }

  /**
   * write map to file
   * 
   * @param filename
   * @param app append or not
   * @param map
   * @throws Exception
   */
  public static <K, V> void WriteMap(String filename, boolean app, Map<K, V> map) throws Exception {
    FileWriter fWriter = new FileWriter(filename, app);
    Set<Entry<K, V>> set = map.entrySet();
    Iterator<Entry<K, V>> iterator = set.iterator();
    while (iterator.hasNext()) {
      Entry<K, V> element = iterator.next();
      fWriter.write(
          String.format("%s,%s\n", element.getKey().toString(), element.getValue().toString()));
    }
    fWriter.close();
  }

  public static void WriteFile(String filename, boolean app, String str) {
    try {
      FileWriter fw = new FileWriter(filename, app);
      fw.write(str);
      fw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void WriteFile(String filename, boolean app, List<String> lines) {
    try {
      FileWriter fw = Util.getFileWriter(filename, app);
      int i = 0;
      while (i < lines.size()) {
        fw.write(String.valueOf(lines.get(i)) + "\n");
        ++i;
      }
      fw.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void WriteFile(String filename, boolean app, Set<String> lines) {
    try {
      FileWriter fw = new FileWriter(filename, app);
      for (String line : lines)
        fw.write(line + "\n");
      fw.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static List<MyRectangle> ReadQueryRectangle(String filepath) {
    ArrayList<MyRectangle> queryrectangles;
    queryrectangles = new ArrayList<MyRectangle>();
    BufferedReader reader = null;
    File file = null;
    try {
      file = new File(filepath);
      reader = new BufferedReader(new FileReader(file));
      String temp = null;
      while ((temp = reader.readLine()) != null) {
        if (temp.contains("%"))
          continue;
        String[] line_list = temp.split("\t");
        MyRectangle rect =
            new MyRectangle(Double.parseDouble(line_list[0]), Double.parseDouble(line_list[1]),
                Double.parseDouble(line_list[2]), Double.parseDouble(line_list[3]));
        queryrectangles.add(rect);
      }
      reader.close();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (reader != null)
        try {
          reader.close();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
    }
    return queryrectangles;
  }

  /**
   * Read the path neighbors map for leaf nodes. Use Map rather than List because leaf nodes count
   * is normally less than 100K. But for spatial nodes PN List is used because it can exceed 1M.
   * 
   * @param path
   * @return
   * @throws Exception
   */
  public static Map<Long, Map<String, int[]>> readLeafNodesPathNeighbors(String path)
      throws Exception {
    Map<Long, Map<String, int[]>> nodesPN = new HashMap<>();

    BufferedReader reader = Util.getBufferedReader(path);
    LOGGER.info("Read leaf nodes path neighbors from " + path);
    String line = null;
    line = reader.readLine();
    long nodeID = Long.parseLong(line);

    while (true) {
      Map<String, int[]> pn = new HashMap<>();
      while ((line = reader.readLine()) != null) {
        if (line.matches("\\d+$") == false) { // path neighbor lines
          String[] lineList = line.split(",", 2);
          String key = lineList[0];

          String content = lineList[1];
          if (content.equals("[]")) {
            pn.put(key, new int[0]);
            continue;
          }
          String[] contentList = content.substring(1, content.length() - 1).split(", ");

          int[] value = new int[contentList.length];
          for (int i = 0; i < contentList.length; i++) {
            value[i] = Integer.parseInt(contentList[i]);
          }
          pn.put(key, value);
        } else {
          break;
        }
      }
      nodesPN.put(nodeID, pn);

      if (line == null) {
        break;
      }
      nodeID = Long.parseLong(line);
    }
    Util.close(reader);
    return nodesPN;
  }

  public static void writeLeafNodesPathNeighbors(Map<Long, Map<String, int[]>> pathNeighbors,
      String outputPath) throws Exception {
    FileWriter writer = Util.getFileWriter(outputPath);
    Iterator<Entry<Long, Map<String, int[]>>> iterator = pathNeighbors.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<Long, Map<String, int[]>> entry = iterator.next();
      long leafNodeId = entry.getKey();
      writer.write(leafNodeId + "\n");
      Iterator<Entry<String, int[]>> nodePnIter = entry.getValue().entrySet().iterator();
      while (nodePnIter.hasNext()) {
        Entry<String, int[]> singlePn = nodePnIter.next();
        String labelPath = singlePn.getKey();
        int[] neighbors = singlePn.getValue();
        writer.write(String.format("%s,%s\n", labelPath, Arrays.toString(neighbors)));
      }
    }
    Util.close(writer);
  }
}
