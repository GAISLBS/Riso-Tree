package CypherMiddleWare;

import org.junit.Test;
import commons.Util;
import cypher.middleware.CypherUtil;

public class CypherUtilTest {

  @Test
  public void removeWhereTest() {
    String query =
        "match (a0:`hill`),(a1:`member state of the United Nations`),(a0)--(a1) where -104.442650 <= a0.lon <= -104.361190 and 27.044770 <= a0.lat <= 27.126230 return id(a0),id(a1)";
    Util.println(CypherUtil.removeWhere(query));
  }

}
