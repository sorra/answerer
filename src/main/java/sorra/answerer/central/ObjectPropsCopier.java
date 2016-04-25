package sorra.answerer.central;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstCheck;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.FindUpper;
import sorra.answerer.ast.VariableTypeResolver;
import sorra.answerer.central.Autowire.WiringParams;
import sorra.answerer.constant.JavaCollections;
import sorra.answerer.util.PrimitiveUtil;
import sorra.answerer.util.StringUtil;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

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

    List<String> lines = new ArrayList<>();
    toFields.forEach(vdFrag -> {
      String toFieldName = vdFrag.getName().getIdentifier();
      String toProp;
      if (hasMethodByName(toTd, setterName(toFieldName))) {
        toProp = "." + setterName(toFieldName);
      } else {
        toProp = toFieldName;
      }

      String _fromFieldName = null;
      if (isTypePairMapped) {
        _fromFieldName = PropsMapper.findMappedProp(toQname, toFieldName, fromQname);
      }
      if (_fromFieldName == null) {
        if (fromFieldNames.contains(toFieldName)) _fromFieldName = toFieldName;
        else return;
      }
      String fromFieldName = _fromFieldName;

      String fromProp;
      if (hasMethodByName(fromTd, getterName(fromFieldName))) {
        fromProp = "." + getterName(fromFieldName);
      } else {
        fromProp = fromFieldName;
      }

      String toFieldCollTypeQname = null;
      Type _fromFieldType = findFieldTypeByName(fromTd, fromFieldName);
      String fromFieldTypeQname = AstFind.qnameOfTypeRef(_fromFieldType);

      Type _toFieldType = ((FieldDeclaration) vdFrag.getParent()).getType();
      String toFieldTypeQname = AstFind.qnameOfTypeRef(_toFieldType);

      if (_toFieldType.isParameterizedType()) {
        // Detect collection mode
        if (JavaCollections.containsIntf(toFieldTypeQname) && JavaCollections.containsIntf(fromFieldTypeQname)) {
          toFieldCollTypeQname = toFieldTypeQname;
        }
      }

      if (PrimitiveUtil.isPrimitive(toFieldTypeQname)) {
        lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, "Unbox.value").toString());
      } else {
        if (toFieldCollTypeQname != null) { // Collection mode!
          String fromEtalQname = extractElemTypeQname(_fromFieldType);
          String toEtalQname = extractElemTypeQname(_toFieldType);
          if (typesIncompatible(fromEtalQname, toEtalQname)) {
            String toCollName = JavaCollections.collName(toFieldCollTypeQname);

            AutowireMethod am = wireMethods.stream()
                .filter(method -> method.retType.equals(toCollName))
                .findFirst().orElseGet(() -> {
                  WiringParams wp = new WiringParams(fromEtalQname, toEtalQname, toCollName, fromFieldName,
                      singletonList(fromFieldTypeQname + "<" + fromEtalQname + "> " + fromFieldName),
                      singletonList(fromFieldTypeQname + "<" + fromEtalQname + ">"));
                  return Autowire.genAutowireMethod(wp, a -> {}, wireMethods, singletonList(
                      writer -> ObjectPropsCopier.get("each", fromEtalQname, "$r", toEtalQname, AstFind.fields(toEtalQname), wireMethods)
                          .getLines().forEach(writer::writeLine)));
                });
            lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, am.name).toString());
          } else {
            if (!toFieldCollTypeQname.equals(fromFieldTypeQname)) {
              String copyConstruction = "new java.util." + JavaCollections.get(toFieldCollTypeQname) + "<>";
              lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, copyConstruction).toString());
            } else {
              lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, null).toString());
            }
          }
        } else if (typesIncompatible(toFieldTypeQname, fromFieldTypeQname)) {
          // toType<-fromType conversion via wirer
          AutowireMethod autowireMethod = wireMethods.stream()
              .filter(method -> method.retType.equals(toFieldTypeQname)
                  && method.paramTypes.size() == 1 && method.paramTypes.get(0).equals(fromFieldTypeQname))
              .findFirst().orElseGet(() -> {
                String toFieldAsVar = StringUtil.asVarName(toFieldTypeQname);
                String fromFieldAsVar = StringUtil.asVarName(fromFieldTypeQname);

                Consumer<PartWriter> midWriting = methodWriter -> {
                  methodWriter.writeLine(format("%s %s = new %s();", toFieldTypeQname, toFieldAsVar, toFieldTypeQname));
                  ObjectPropsCopier.get(fromFieldAsVar, fromFieldTypeQname, toFieldAsVar, toFieldTypeQname,
                      AstFind.fields(toFieldTypeQname), wireMethods)
                      .getLines().forEach(methodWriter::writeLine);
                };
                // Generate autowire method before fullfiling its body, to avoid cyclic autowiring
                PartWriter methodWriter = new PartWriter();
                Pair<AutowireMethod, Boolean> autowirer = Autowire.genAutowireMethodHeader(toFieldTypeQname, toFieldAsVar,
                    singletonList(fromFieldTypeQname), singletonList(fromFieldTypeQname + " " + fromFieldAsVar),
                    methodWriter, wireMethods);
                if (!autowirer.getRight()) {
                  return autowirer.getLeft();
                }
                methodWriter.setIndent(1);
                methodWriter.writeLine(format("static %s %s(%s) {",
                    toFieldTypeQname, autowirer.getLeft().name, String.join(", ", autowirer.getLeft().params)));
                methodWriter.setIndent(2);
                methodWriter.writeLine(format("if (%s == null) return null;\n", fromFieldAsVar));

                midWriting.accept(methodWriter);

                methodWriter.writeLine(format("return %s;", toFieldAsVar));
                methodWriter.setIndent(1);
                methodWriter.writeLine("}\n");
                return autowirer.getLeft();
              });
          lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, autowireMethod.name).toString());
        } else {
          // Direct assignment
          lines.add(new PropCopy(toVarName, toProp, fromVarName, fromProp, null).toString());
        }
      }
    });
    return lines;
  }

  private static String extractElemTypeQname(Type _toFieldType) {
    Object toTArg = ((ParameterizedType) _toFieldType).typeArguments().get(0);
    return AstFind.qnameOfTypeRef(toTArg.toString(), FindUpper.cu(_toFieldType));
  }

  private static boolean typesIncompatible(String leftType, String rightType) {
    return !leftType.equals(rightType)
        && (Sources.containsQname(rightType) && Sources.containsQname(leftType)
        && !AstCheck.isSubType(rightType, leftType));
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
    String rconv; //conversion method

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
