package sorra.answerer.central;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.VariableTypeResolver;
import sorra.answerer.api.Config;

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
          if (pair1.getRight() == null && pair2.getRight() == null) {
            String qname1 = pair1.getLeft();
            String qname2 = pair2.getLeft();
            boolean isEntity1 = isEntity(Sources.getCuByQname(qname1));
            boolean isEntity2 = isEntity(Sources.getCuByQname(qname2));
            if (isEntity1 && isEntity2) {
              throw new RuntimeException("Cannot map two Entities!");
            } else if (isEntity1) {
              Relations.add(qname2, qname1);
            } else if (isEntity2) {
              Relations.add(qname1, qname2);
            } else {
              throw new RuntimeException("Cannot map two DTOs!");
            }
          } else {
            PropsMapper.bidirectMapProp(pair1.getLeft(), pair1.getRight(), pair2.getLeft(), pair2.getRight());
          }
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

  private static boolean isEntity(CompilationUnit cu) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) cu.types().get(0);
    return atd.modifiers().stream().anyMatch(mod -> {
      if (mod instanceof Annotation) {
        Annotation anno = (Annotation) mod;
        String annoQname = AstFind.qnameOfTypeRef(anno.getTypeName().getFullyQualifiedName(), cu);
        return annoQname.equals("javax.persistence.Entity");
      } else return false;
    });
  }
}
