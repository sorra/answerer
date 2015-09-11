package sorra.answerer.ast;

import java.util.List;

import org.eclipse.jdt.core.dom.*;

public class AstOps {
  public static void insertPublic(FieldDeclaration fd) {
    List<IExtendedModifier> modifiers = fd.modifiers();
    Modifier pub = fd.getAST().newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD);
    if (modifiers.isEmpty()) {
      modifiers.add(pub);
      return;
    }
    IExtendedModifier lastAnno = null;
    for (IExtendedModifier mod : modifiers) {
      if (mod.isModifier() && ((Modifier)mod).isFinal()) {
        modifiers.add(modifiers.indexOf(mod), pub);
        return;
      }
      if (mod.isAnnotation()) {
        lastAnno = mod;
      }
    }
    int idxLastAnno = lastAnno==null ? 0 : modifiers.indexOf(lastAnno)+1;
    modifiers.add(idxLastAnno, pub);
  }
}
