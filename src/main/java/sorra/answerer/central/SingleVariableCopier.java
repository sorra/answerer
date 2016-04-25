package sorra.answerer.central;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import sorra.answerer.ast.AstFind;
import sorra.answerer.util.PrimitiveUtil;

public class SingleVariableCopier {
  public static Optional<String> getLine(String fromVarName, String toVarName, List<VariableDeclarationFragment> toFields) {
    String[] line = new String[1];
    toFields.stream().anyMatch(vdFrag -> {
      String fieldName = vdFrag.getName().getIdentifier();
      if (fieldName.equals(fromVarName)) {
        Type type = ((FieldDeclaration) vdFrag.getParent()).getType();
        String fieldTypeQname = AstFind.qnameOfTypeRef(type);
        if (PrimitiveUtil.isPrimitive(fieldTypeQname)) {
          line[0] = String.format("%s.%s = Unbox.value(%s);", toVarName, fromVarName, fromVarName);
        } else {
          line[0] = String.format("%s.%s = %s;", toVarName, fieldName, fieldName);
        }
        return true;
      }
      return false;
    });
    return Optional.ofNullable(line[0]);
  }

  /** Amended */
  public static String getLine(String fromVarName, Expression fromExp, String toVarName, List<VariableDeclarationFragment> toFields) {
    String[] line = new String[1];
    String fromExpStr = fromExp.toString().trim();
    toFields.stream().anyMatch(vdFrag -> {
      String fieldName = vdFrag.getName().getIdentifier();
      if (fieldName.equals(fromVarName)) {
        Type type = ((FieldDeclaration) vdFrag.getParent()).getType();
        String fieldTypeQname = AstFind.qnameOfTypeRef(type);
        if (PrimitiveUtil.isPrimitive(fieldTypeQname)) {
          line[0] = String.format("%s.%s = Unbox.value(%s);", toVarName, fromVarName, fromExpStr);
        } else {
          line[0] = String.format("%s.%s = %s;", toVarName, fieldName, fromExpStr);
        }
        return true;
      }
      return false;
    });
    if (line[0] == null) {
      throw new RuntimeException(String.format("Bad var amending '%s' for object '%s'", fromVarName, toVarName));
    }
    return line[0];
  }


}
