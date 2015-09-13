package sorra.answerer.central;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstFind;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

public class ObjectPropsCopier {
//  private static Map<Pair<String, String>, ObjectPropsCopier> copiers = new ConcurrentHashMap<>();

  private List<String> lines;

  public List<String> getLines() {
    return lines;
  }

  public static ObjectPropsCopier get(String fromVarName, String fromQname, String toVarName, String toQname, List<VariableDeclarationFragment> toFields) {
//    ObjectPropsCopier cached = copiers.get(Pair.of(fromQname, toQname));
//    if (cached != null) {
//      return cached;
//    }

    ObjectPropsCopier copier = new ObjectPropsCopier();
    List<String> lines = codegen(fromVarName, fromQname, toVarName, toQname, toFields);
    copier.lines = Collections.unmodifiableList(lines);
//    copiers.put(Pair.of(fromQname, toQname), copier);
    return copier;
  }

  //TODO pre-parse-toFieldTypeQnames
  private static List<String> codegen(String fromVarName, String fromQname, String toVarName, String toQname, List<VariableDeclarationFragment> toFields) {
    List<String> lines = new ArrayList<>();

    if (!fromQname.contains(".")) {
      throw new RuntimeException("Unsupported fromQname: " + fromQname);
    }
    //TODO props mapping
    //TODO field types auto-mapping
    TypeDeclaration fromTd = (TypeDeclaration) Sources.getCuByQname(fromQname).types().get(0);
    Set<String> fromFieldNames = AstFind.fieldNameSet(fromTd);
    boolean isTypePairMapped = PropsMapper.isTypePairMapped(fromQname, toQname);

    toFields.forEach(vdFrag -> {
      String fieldName = vdFrag.getName().getIdentifier();
      String fromFieldName = null;
      if (isTypePairMapped) {
        fromFieldName = PropsMapper.findMappedProp(toQname, fieldName, fromQname);
      }
      if (fromFieldName == null) {
        if (fromFieldNames.contains(fieldName)) fromFieldName = fieldName;
        else return;
      }
      Type type = ((FieldDeclaration) vdFrag.getParent()).getType();
      String fieldTypeQname = AstFind.qnameOfTypeRef(type);
      if (PrimitiveUtil.isPrimitive(fieldTypeQname)) {
        lines.add(String.format("%s.%s = Unbox.value(%s.%s);", toVarName, fieldName, fromVarName, fromFieldName));
      } else {
        lines.add(String.format("%s.%s = %s.%s;", toVarName, fieldName, fromVarName, fromFieldName));
      }
    });
    return lines;
  }
}
