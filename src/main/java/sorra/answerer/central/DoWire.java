package sorra.answerer.central;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.ast.*;
import sorra.answerer.io.FileUtil;
import sorra.answerer.io.FileWalker;
import sorra.answerer.util.EventSeq;
import sorra.answerer.util.StringUtil;


public class DoWire {
  public static void run(String javaFolder) {
    Supplier<Collection<File>> findAll = () -> FileWalker.findAll(new Path[]{Paths.get(javaFolder)},
        path -> {
          String p = path.toString();
          return p.endsWith(".java") && !p.equals("package-info.java");
        });

    FileWalker.walkAll(findAll.get(), fileAction(ctx -> {
      ConfigReader.read(ctx.cu);
      AopWeaving.collect(ctx);
    }), 1);

    FileWalker.walkAll(findAll.get(), fileAction(DoWire::processEnableRest), 1);
    FileWalker.walkAll(findAll.get(), fileAction(DoWire::processUserFunction), 1);
    Autowire.writeWirers(javaFolder);
  }

  private static Consumer<File> fileAction(Consumer<AstContext> consumer) {
    return file -> {
      try {
        String source = FileUtil.readFile(file.getPath());
        CompilationUnit cu = Parser.parse(source);
        consumer.accept(new AstContext(file, source, cu));
      } catch (Throwable e) {
        System.err.println("Error at: " + file.getPath());
        e.printStackTrace();
      }
    };
  }

  static void processUserFunction(AstContext ctx) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) ctx.cu.types().get(0);
    if (atd.getJavadoc() != null && atd.getJavadoc().toString().contains("$UserFunction")) {
      EventSeq eventSeq = new EventSeq(ctx.source);
      ImportDeclaration wirerImport = AstFind.findImport("sorra.answerer.api.Wirer", ctx.cu.imports());
      if (wirerImport != null) {
        eventSeq.add(new EventSeq.Deletion(wirerImport, '\n', ctx.source));
      }

      String pkgName = ctx.cu.getPackage().getName().getFullyQualifiedName();
      List<AutowireMethod> wireMethods = Autowire.getMethods(pkgName);

      atd.bodyDeclarations().stream().forEach(bodyDecl -> {
        if (bodyDecl instanceof MethodDeclaration) {
          ((MethodDeclaration) bodyDecl).getBody().accept(Autowire.visitor(ctx, eventSeq, wireMethods));
        }
      });
      atd.accept(AopWeaving.weaver(ctx, eventSeq));

      if (ctx.modified) {
        try {
          FileUtils.write(ctx.file, eventSeq.run(), StandardCharsets.UTF_8);
          System.out.println("* Modified file: " + ctx.file.getPath());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }

  static void processEnableRest(AstContext ctx) {
    AbstractTypeDeclaration atd = (AbstractTypeDeclaration) ctx.cu.types().get(0);
    if (atd.getJavadoc() != null && atd.getJavadoc().toString().contains("$EnableRest")) {
      if (atd instanceof TypeDeclaration == false) {
        throw new RuntimeException("EnableRest only supports 'class'!");
      }
      boolean isEntity = atd.modifiers().stream().anyMatch(mod -> {
        if (mod instanceof Annotation) {
          Annotation anno = (Annotation) mod;
          String annoQname = AstFind.qnameOfTypeRef(anno.getTypeName().getFullyQualifiedName(), ctx.cu);
          return annoQname.equals("javax.persistence.Entity");
        } else return false;
      });

      String xxxQname = AstFind.qnameOfTopTypeDecl(atd.getName());
      String entityQname = isEntity ? xxxQname : Relations.findEntity(xxxQname);
      if (entityQname == null) {
        throw new RuntimeException("Entity Qname not found for: " + xxxQname);
      }
      ProjectGenerator.newController(entityQname, xxxQname, StringUtil.asVarName(xxxQname));
    }
  }
}
