package sorra.answerer.central;

import java.util.*;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstCheck;
import sorra.answerer.ast.AstFind;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

import static java.lang.String.format;

public class ObjectPropsCopier {

  private List<String> lines;

  public List<String> getLines() {
    return lines;
  }

  public static ObjectPropsCopier get(String fromVarName, String fromQname, String toVarName, String toQname,
                                      List<VariableDeclarationFragment> toFields, List<AutowireMethod> wireMethods) {

    ObjectPropsCopier copier = new ObjectPropsCopier();
    List<String> lines = codegen(fromVarName, fromQname, toVarName, toQname, toFields, wireMethods);
    copier.lines = Collections.unmodifiableList(lines);
    return copier;
  }

  //TODO pre-parse-toFieldTypeQnames
  private static List<String> codegen(String fromVarName, String fromQname, String toVarName, String toQname,
                                      List<VariableDeclarationFragment> toFields, List<AutowireMethod> wireMethods) {
    List<String> lines = new ArrayList<>();

    if (!fromQname.contains(".")) {
      throw new RuntimeException("Unsupported fromQname: " + fromQname);
    }
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
        if (!toFieldTypeQname.equals(fromFieldTypeQname)
            && (Sources.containsQname(fromFieldTypeQname) && Sources.containsQname(toFieldTypeQname)
                && !AstCheck.isSubType(fromFieldTypeQname, toFieldTypeQname))) {
          AutowireMethod autowireMethod = wireMethods.stream()
              .filter(method -> method.retType.equals(toFieldTypeQname)
                  && method.paramTypes.size() == 1 && method.paramTypes.get(0).equals(fromFieldTypeQname))
              .findFirst().orElseGet(() -> {
                String toFieldAsVar = StringUtil.asVarName(toFieldTypeQname);
                String fromFieldAsVar = StringUtil.asVarName(fromFieldTypeQname);
                // Generate autowire method before fullfiling its body, to avoid cyclic autowiring
                PartWriter methodWriter = new PartWriter();
                Pair<AutowireMethod, Boolean> autowirer = Autowire.genAutowireMethod(toFieldTypeQname, toFieldAsVar,
                    Collections.singletonList(fromFieldTypeQname), Collections.singletonList(fromFieldTypeQname + " " + fromFieldAsVar),
                    methodWriter, wireMethods);
                if (!autowirer.getRight()) {
                  return autowirer.getLeft();
                }
                methodWriter.setIndent(1);
                methodWriter.writeLine(format("static %s %s(%s) {",
                    toFieldTypeQname, autowirer.getLeft().name, String.join(", ", autowirer.getLeft().params)));
                methodWriter.setIndent(2);
                methodWriter.writeLine(format("if (%s == null) return null;\n", fromFieldAsVar));

                methodWriter.writeLine(format("%s %s = new %s();", toFieldTypeQname, toFieldAsVar, toFieldTypeQname));
                TypeDeclaration toTypeDecl = (TypeDeclaration) Sources.getCuByQname(toFieldTypeQname).types().get(0);
                List<VariableDeclarationFragment> toFieldFields = AstFind.fields(toTypeDecl);
                ObjectPropsCopier.get(fromFieldAsVar, fromFieldTypeQname, toFieldAsVar, toFieldTypeQname,
                    toFieldFields, wireMethods)
                    .getLines().forEach(methodWriter::writeLine);

                methodWriter.writeLine(format("return %s;", toFieldAsVar));
                methodWriter.setIndent(1);
                methodWriter.writeLine("}\n");
                return autowirer.getLeft();
              });
          lines.add(format("%s.%s = %s(%s.%s);", toVarName, fieldName, autowireMethod.name, fromVarName, fromFieldName));
        } else {
          lines.add(format("%s.%s = %s.%s;", toVarName, fieldName, fromVarName, fromFieldName));
        }
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
