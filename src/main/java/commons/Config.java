package commons;

public class Config {
  public Config() {

  }

  private String SERVER_ROOT_URI = "http://localhost:7474/db/data";

  public static String longitude_property_name = "longitude";
  public static String latitude_property_name = "latitude";
  public static final String password = "0000";

  // attention here, these settings change a lot
  private String neo4j_version = "neo4j-community-3.4.12_Gleenes_1.0_-1_new_version";
  private Enums.system operatingSystem = Enums.system.Windows;
  private String dataset = Enums.Datasets.Yelp.name();

  private int MAX_HOPNUM = 2;
  private int MAX_HMBR_HOPNUM = 3;
  private int nonspatial_label_count = 100;

  private String Rect_minx_name = "minx";
  private String Rect_miny_name = "miny";
  private String Rect_maxx_name = "maxx";
  private String Rect_maxy_name = "maxy";

  public final static String PNPrefix = "PN";
  public final static String PNSizePrefix = "PNSize";
  public final static String PNSeparator = "_";
  public final static String BBoxName = "bbox";
  public final static int graphNodeCount = 47116657;
  public final static int logInterval = 5000000;

  /**
   * Used in LAGAQ-Join experiment. Convert the single spatial predicate query graph into join
   * predicate with two spatial query vertex pair. The query vertex whose label size is less than
   * this value will be skipped until two satisfying spatial query vertexes are found.
   */
  public final static int labelSizeFilterValue = 1000;

  public final static String SKIPFLAG = "//";

  public void setDatasetName(String pName) {
    this.dataset = pName;
  }

  public void setMAXHOPNUM(int pMAXHOPNUM) {
    this.MAX_HOPNUM = pMAXHOPNUM;
  }

  public String GetServerRoot() {
    return SERVER_ROOT_URI;
  }

  public String GetLongitudePropertyName() {
    return longitude_property_name;
  }

  public String GetLatitudePropertyName() {
    return latitude_property_name;
  }

  public String[] GetRectCornerName() {
    String[] rect_corner_name = new String[4];
    rect_corner_name[0] = this.Rect_minx_name;
    rect_corner_name[1] = this.Rect_miny_name;
    rect_corner_name[2] = this.Rect_maxx_name;
    rect_corner_name[3] = this.Rect_maxy_name;
    return rect_corner_name;
  }

  public String GetNeo4jVersion() {
    return neo4j_version;
  }

  public int getMaxHopNum() {
    return MAX_HOPNUM;
  }

  public int getMaxHMBRHopNum() {
    return MAX_HMBR_HOPNUM;
  }

  public Enums.system getSystemName() {
    return operatingSystem;
  }

  public String getDatasetName() {
    return dataset;
  }

  public String getPassword() {
    return password;
  }

  public int getNonSpatialLabelCount() {
    return nonspatial_label_count;
  }
}
