package sorra.answerer.central;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstFind;
import sorra.answerer.util.PrimitiveUtil;

import static java.lang.String.format;

public class ObjectPropsCopier {
//  private static Map<Pair<String, String>, ObjectPropsCopier> copiers = new ConcurrentHashMap<>();

  private List<String> lines;

  public List<String> getLines() {
    return lines;
  }

  public static ObjectPropsCopier get(String fromVarName, String fromQname, String toVarName, String toQname,
                                      List<VariableDeclarationFragment> toFields, List<DoWire.AutowireMethod> wireMethods) {
//    ObjectPropsCopier cached = copiers.get(Pair.of(fromQname, toQname));
//    if (cached != null) {
//      return cached;
//    }

    ObjectPropsCopier copier = new ObjectPropsCopier();
    List<String> lines = codegen(fromVarName, fromQname, toVarName, toQname, toFields, wireMethods);
    copier.lines = Collections.unmodifiableList(lines);
//    copiers.put(Pair.of(fromQname, toQname), copier);
    return copier;
  }

  //TODO pre-parse-toFieldTypeQnames
  private static List<String> codegen(String fromVarName, String fromQname, String toVarName, String toQname,
                                      List<VariableDeclarationFragment> toFields, List<DoWire.AutowireMethod> wireMethods) {
    List<String> lines = new ArrayList<>();

    if (!fromQname.contains(".")) {
      throw new RuntimeException("Unsupported fromQname: " + fromQname);
    }
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
      String fromFieldTypeQname = AstFind.qnameOfTypeRef(findFieldTypeByName(fromTd, fromFieldName));
      Type toFieldType = ((FieldDeclaration) vdFrag.getParent()).getType();
      String toFieldTypeQname = AstFind.qnameOfTypeRef(toFieldType);
      if (PrimitiveUtil.isPrimitive(toFieldTypeQname)) {
        lines.add(format("%s.%s = Unbox.value(%s.%s);", toVarName, fieldName, fromVarName, fromFieldName));
      } else {
        if (!toFieldTypeQname.equals(fromFieldTypeQname)) {
          //TODO nested autowire
        }
        lines.add(format("%s.%s = %s.%s;", toVarName, fieldName, fromVarName, fromFieldName));
      }
    });
    return lines;
  }

  private static Type findFieldTypeByName(TypeDeclaration td, String name) {
    for (FieldDeclaration field : td.getFields()) {
      for (Object obj : field.fragments()) {
        VariableDeclarationFragment frag = (VariableDeclarationFragment) obj;
        if (frag.getName().getIdentifier().equals(name)) {
          return field.getType();
        }
      }
    }
    throw new RuntimeException(format("Field %s is not found in class %s", name, td.getName().getIdentifier()));
  }
}
