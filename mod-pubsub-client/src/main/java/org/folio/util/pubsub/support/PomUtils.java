package org.folio.util.pubsub.support;

import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class PomUtils {
  public static String getModuleVersion() {
    return getVersionRecursively("pom.xml");
  }

  public static String getVersionRecursively(String pomPath) {
    try {
      Model model = new MavenXpp3Reader().read(new FileReader(pomPath));
      String version = model.getVersion();
      if (version == null || version.isEmpty()) {
        Parent parent = model.getParent();
        if (parent == null) {
          throw new RuntimeException("Failed to find version in POM file");
        }

        return getVersionRecursively(parent.getRelativePath());
      }
      else {
        return version.replaceAll("-.*", "");
      }
    }
    catch (IOException | XmlPullParserException e) {
      throw new RuntimeException("Failed to parse " + pomPath);
    }
  }
}
