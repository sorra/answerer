package sorra.answerer.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class CommandProperties {
  private Properties properties = new Properties();

  public CommandProperties(String filename) throws IOException {
    properties.load(new FileInputStream(filename));
  }

  public String getProperty(String key) {
    String sysProp = System.getProperty(key);
    if (sysProp != null) {
      return sysProp;
    } else {
      String fileProp = properties.getProperty(key);
      if (fileProp != null) {
        return fileProp;
      } else {
        throw new IllegalArgumentException("Require property: " + key);
      }
    }
  }
}