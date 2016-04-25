package sorra.answerer.util;

import java.io.File;
import java.util.Map;

import sorra.answerer.io.FileUtil;

public class TemplateEngine {
  public static CharSequence render(File template, Map<String, ?> variables) {
    String tmplStr = FileUtil.read(template);
    return SemiTemplate.transform(tmplStr,
        (tmpl, range) -> {
          int idxStart = tmpl.indexOf("$[", range.begin);
          if (idxStart < 0 ) return null;
          int idxEnd = tmpl.indexOf("]", idxStart);
          if (idxEnd < 0) throw new RuntimeException("'$[' needs a matching ']'!");
          String name = tmpl.substring(idxStart + 2, idxEnd);
          return SemiTemplate.Section.f(name, idxStart, idxEnd+1);
        },
        section -> {
          String name = section.data;
          if (name.contains("\n")) throw new RuntimeException("expression shouldn't have line ending!");
          Object o = variables.get(name);
          if (o == null) throw new RuntimeException(name + " is not given a value!");
          return o.toString();
        });
  }
}
