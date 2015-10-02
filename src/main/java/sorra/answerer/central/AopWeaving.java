package sorra.answerer.central;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.AstContext;
import sorra.answerer.ast.AstFind;
import sorra.answerer.ast.FindUpper;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.StringUtil;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class AopWeaving {
  private static final Map<String, AopMetadata> aopMetadatas = new ConcurrentHashMap<>();

  static void collect(AstContext ctx) {
    ctx.cu.accept(new ASTVisitor() {
      @Override
      public boolean visit(AnnotationTypeDeclaration antd) {
        for (Object mod : antd.modifiers()) {
          if (mod instanceof SingleMemberAnnotation) {
            SingleMemberAnnotation anno = (SingleMemberAnnotation) mod;
            String annoName = anno.getTypeName().getFullyQualifiedName();
            if (StringUtil.simpleName(annoName).equals("Aop")
                && AstFind.qnameOfTypeRef(annoName, FindUpper.cu(antd)).equals("sorra.answerer.api.Aop")) {
              collect(antd, anno);
              break;
            }
          }
        }
        return false;
      }

      void collect(AnnotationTypeDeclaration antd, SingleMemberAnnotation anno) {
        TypeLiteral interceptorClass = (TypeLiteral) anno.getValue();
        String interceptorQname = AstFind.qnameOfTypeRef(interceptorClass.getType());

        Map<String, String> properties = new HashMap<>();
        for (Object bd : antd.bodyDeclarations()) {
          if (bd instanceof AnnotationTypeMemberDeclaration) {
            AnnotationTypeMemberDeclaration md = (AnnotationTypeMemberDeclaration) bd;
            if (AstFind.hasModifierKeyword(md.modifiers(), Modifier.ModifierKeyword.STATIC_KEYWORD)) {
              continue;
            }
            String defaultVal = null;
            if (md.getDefault() != null) {
              defaultVal = md.getDefault().toString().trim();
            }
            properties.put(md.getName().getIdentifier(), defaultVal);
          }
        }
        aopMetadatas.put(AstFind.qnameOfTopTypeDecl(antd.getName()),
            new AopMetadata(interceptorQname, Collections.unmodifiableMap(properties)));
      }
    });
  }

  static ASTVisitor weaver(AstContext ctx, EventSeq eventSeq) {
    return new ASTVisitor() {
//      @Override
//      public boolean visit(TypeDeclaration td) {
//      }
//
//      @Override
//      public boolean visit(EnumDeclaration ed) {
//      }

      @Override
      public boolean visit(MethodDeclaration md) {
        if (AstFind.hasModifierKeyword(md.modifiers(), Modifier.ModifierKeyword.STATIC_KEYWORD)
            || AstFind.hasModifierKeyword(md.modifiers(), Modifier.ModifierKeyword.PRIVATE_KEYWORD)) {
          return true;
        }
        List<AopInstance> aopInstances = new ArrayList<>();
        List<Annotation> annos = new ArrayList<>();
        md.modifiers().stream().filter(mod -> mod instanceof Annotation).forEach(mod -> {
          Annotation anno = (Annotation) mod;
          String annoQname = AstFind.qnameOfTypeRef(anno.getTypeName().getFullyQualifiedName(), FindUpper.cu(md));
          Map<String, String> params = new HashMap<>();
          if (anno instanceof SingleMemberAnnotation) {
            params.put("value", ((SingleMemberAnnotation) anno).getValue().toString().trim());
          } else if (anno instanceof NormalAnnotation) {
            List<MemberValuePair> values = ((NormalAnnotation) anno).values();
            values.forEach(pair -> params.put(pair.getName().getIdentifier(), pair.getValue().toString().trim()));
          }
          AopMetadata aopMetadata = aopMetadatas.get(annoQname);
          if (aopMetadata != null) {
            aopInstances.add(new AopInstance(aopMetadata, params));
            annos.add(anno);
          }
        });
        if (aopInstances.size() > 0) weave(md, aopInstances, annos);
        return true;
      }

      private void weave(MethodDeclaration md, List<AopInstance> aopInstances, List<Annotation> annos) {
        ctx.modified = true;
        String maskedMethName = "$" + md.getName().getIdentifier();
        List<SingleVariableDeclaration> methParameters = md.parameters();

        List<Type> throwns = md.thrownExceptionTypes();
        List<String> throwsNames = throwns.stream().map(type -> type.toString().trim()).collect(toList());
        String throwsDecl = throwns.isEmpty() ? "" : "throws " + StringUtils.join(throwsNames, ", ") + " ";

        String $header = format("  private %s %s(%s) %s",
            md.getReturnType2(), maskedMethName, StringUtils.join(methParameters, ", "), throwsDecl);
        String bodyCode = ctx.source.substring(md.getBody().getStartPosition(), md.getBody().getStartPosition() + md.getBody().getLength());

        AopInstance firstInst = aopInstances.get(0);
        StringBuilder invocBuilder = new StringBuilder(
            format("  return new %s(%s)", firstInst.aopMetadata.interceptorQname, ctArgStr(firstInst)));
        aopInstances.stream().skip(1).forEach(inst -> invocBuilder.append(
            format(".setInner(new %s(%s))", inst.aopMetadata.interceptorQname, ctArgStr(inst))));
        String methArgs = StringUtils.join(methParameters.stream().map(p -> p.getName().getIdentifier()).iterator(), ", ");
        invocBuilder.append(format(".invoke(() -> %s(%s));", maskedMethName, methArgs));

        PartWriter bodyWriter = new PartWriter();
        bodyWriter.writeLine("{");
        bodyWriter.setIndent(2);
        bodyWriter.writeLine("try {");
        bodyWriter.writeLine(invocBuilder.toString());
        bodyWriter.writeLine("} catch (Exception e) {");
        bodyWriter.writeLine("  if (e instanceof RuntimeException) throw (RuntimeException)e;");
        throwsNames.forEach(ex -> bodyWriter.writeLine(format("  if(e instanceof %s) throw (%s)e;", ex, ex)));
        bodyWriter.writeLine("  throw new sorra.answerer.api.ImpossibleException(e);");
        bodyWriter.writeLine("}");
        bodyWriter.setIndent(1);
        bodyWriter.writeLine("}");

        eventSeq.add(new EventSeq.Deletion(md.getBody()));
        eventSeq.add(new EventSeq.Insertion(bodyWriter.getWhole(), md.getBody().getStartPosition()));
        int nextLF = ctx.source.indexOf('\n', md.getStartPosition() + md.getLength() - 1);
        eventSeq.add(new EventSeq.Insertion("\n" + $header + bodyCode + "\n\n", nextLF+1));

        annos.forEach(anno -> {
          eventSeq.add(new EventSeq.Insertion("/* ", anno.getStartPosition()));
          eventSeq.add(new EventSeq.Insertion(" */", anno.getStartPosition()+anno.getLength()));
        });
      }
    };
  }

  private static String ctArgStr(AopInstance firstInst) {
    AbstractTypeDeclaration icAtd = (AbstractTypeDeclaration) Sources.getCuByQname(firstInst.aopMetadata.interceptorQname).types().get(0);
    List<?> bds = icAtd.bodyDeclarations();
    return bds.stream().filter(bd -> bd instanceof MethodDeclaration)
        .map(bd -> {
          MethodDeclaration med = (MethodDeclaration) bd;
          List<String> ctArgs = null;
          if (med.isConstructor()) {
            List<SingleVariableDeclaration> ctParams = med.parameters();
            ctArgs = ctParams.stream().map(p -> {
              String key = p.getName().getIdentifier();
              String value = firstInst.params.get(key);
              if (value == null) value = firstInst.aopMetadata.properties.get(key);
              if (value == null) System.err.println("Missing interceptor argument: " + key);
              return value;
            }).collect(toList());
            if (ctArgs.contains(null)) {
              throw new RuntimeException("Missing argument, cannot construct interceptor " + firstInst.aopMetadata.interceptorQname);
            }
          }
          return ctArgs;
        }).filter(l -> l != null).map(l -> String.join(", ", l)).findFirst().orElse("");
  }

  static class AopMetadata {
    String interceptorQname;
    Map<String, String> properties;

    AopMetadata(String interceptorQname, Map<String, String> properties) {
      this.interceptorQname = interceptorQname;
      this.properties = properties;
    }
  }

  static class AopInstance {
    AopMetadata aopMetadata;
    Map<String, String> params;

    public AopInstance(AopMetadata aopMetadata, Map<String, String> params) {
      this.aopMetadata = aopMetadata;
      this.params = params;
    }
  }
}
