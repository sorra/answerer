package sorra.answerer.central;

import java.util.*;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstCheck;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.FindUpper;
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
    if (toFields.isEmpty()) {
      return Collections.emptyList();
    }
    TypeDeclaration toTd = FindUpper.typeScope(toFields.get(0));
    TypeDeclaration fromTd = (TypeDeclaration) Sources.getCuByQname(fromQname).types().get(0);
    Set<String> fromFieldNames = AstFind.fieldNameSet(fromTd);
    boolean isTypePairMapped = PropsMapper.isTypePairMapped(fromQname, toQname);

    toFields.forEach(vdFrag -> {
      String toFieldName = vdFrag.getName().getIdentifier();
      String toProp;
      if (hasMethodByName(toTd, setterName(toFieldName))) {
        toProp = "."+setterName(toFieldName);
      } else {
        toProp = toFieldName;
      }

      String fromFieldName = null;
      if (isTypePairMapped) {
        fromFieldName = PropsMapper.findMappedProp(toQname, toFieldName, fromQname);
      }
      if (fromFieldName == null) {
        if (fromFieldNames.contains(toFieldName)) fromFieldName = toFieldName;
        else return;
      }
      String fromProp;
      if (hasMethodByName(fromTd, getterName(fromFieldName))) {
        fromProp = "."+getterName(fromFieldName);
      } else {
        fromProp = fromFieldName;
      }

      String fromFieldTypeQname = AstFind.qnameOfTypeRef(findFieldTypeByName(fromTd, fromFieldName));
      Type toFieldType = ((FieldDeclaration) vdFrag.getParent()).getType();
      String toFieldTypeQname = AstFind.qnameOfTypeRef(toFieldType);

      if (PrimitiveUtil.isPrimitive(toFieldTypeQname)) {
        lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, "Unbox.value").toString());
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
          lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, autowireMethod.name).toString());
        } else {
          lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, null).toString());
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

  private static String getterName(String fieldName) {
    return "get" + StringUtils.capitalize(fieldName);
  }
  private static String setterName(String fieldName) {
    return "set" + StringUtils.capitalize(fieldName);
  }

  private static boolean hasMethodByName(TypeDeclaration td, String name) {
    return Stream.of(td.getMethods()).anyMatch(m -> m.getName().getIdentifier().equals(name));
  }

  private static class PropCopy {
    String lcaller;
    String lprop;
    String rcaller;
    String rprop;
    String rconv;

    public PropCopy(String lcaller, String lprop, String rcaller, String rprop, String rconv) {
      this.lcaller = lcaller;
      this.lprop = lprop;
      this.rcaller = rcaller;
      this.rprop = rprop;
      this.rconv = rconv;
    }

    @Override
    public String toString() {
      String rexp;
      if (rprop.startsWith(".")) rexp = rcaller+rprop+"()";
      else rexp = rcaller+"."+rprop;

      if (rconv != null) rexp = rconv+"("+rexp+")";

      if (lprop.startsWith(".")) return format("%s%s(%s);", lcaller, lprop, rexp);
      else return format("%s.%s = %s;", lcaller, lprop, rexp);
    }
  }
}
