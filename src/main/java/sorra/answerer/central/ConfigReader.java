package sorra.answerer.central;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.VariableTypeResolver;
import sorra.answerer.wow.Config;

public class ConfigReader {
  private static String CONFIG_CLASS = Config.class.getName();

  public static void read(CompilationUnit cu) {
    Object topType = cu.types().get(0);
    if (topType instanceof TypeDeclaration) {
      TypeDeclaration td = (TypeDeclaration) topType;
      Type superclassType = td.getSuperclassType();
      if (superclassType != null && superclassType.toString().contains("Config")) {
        String superQname = AstFind.qnameOfTypeRef(superclassType);
        if (superQname.equals(CONFIG_CLASS)) {
          doRead(td);
        }
      }
    }
  }

  private static void doRead(TypeDeclaration td) {
    td.accept(new ASTVisitor() {
      @Override
      public boolean visit(MethodInvocation mi) {
        if (mi.getName().getIdentifier().equals("map")
            && (mi.getExpression() == null || mi.getExpression() instanceof ThisExpression)) {
          List<Expression> args = mi.arguments();
          if (args.size() != 2) throw new RuntimeException("map() takes 2 arguments! actual=" + args.size());
          Pair<String, String> pair1 = qnameAndFieldName(args.get(0));
          Pair<String, String> pair2 = qnameAndFieldName(args.get(1));
          PropsMapper.bidirectMapProp(pair1.getLeft(), pair1.getRight(), pair2.getLeft(), pair2.getRight());
        }
        return true;
      }
    });
  }

  private static Pair<String, String> qnameAndFieldName(Expression arg) {
    String argStr = arg.toString().trim();
    String typeInstance = StringUtils.substringBeforeLast(argStr, ".");
    String fieldName = StringUtils.substringAfterLast(argStr, ".");
    if (fieldName.contains(".")) {
      throw new RuntimeException("Dot is forbidden in field name " + fieldName);
    }
    return Pair.of(new VariableTypeResolver(typeInstance, arg).resolveTypeQname(), fieldName);
  }
}
