package sorra.answerer.ast;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.*;
import sorra.answerer.central.Sources;

public class AstCheck {
  public static boolean isSubType(String subQname, String spQname) {
    AbstractTypeDeclaration sub = typeDecl(subQname);
    AbstractTypeDeclaration sp = typeDecl(spQname);
    List<String> supers = new ArrayList<>();
    findSupers(sub, supers);
    return supers.contains(AstFind.qnameOfTopTypeDecl(sp.getName()));
  }

  static void findSupers(AbstractTypeDeclaration atd, List<String> list) {
    if (atd instanceof TypeDeclaration) {
      TypeDeclaration td = (TypeDeclaration) atd;
      List<Type> intfs = td.superInterfaceTypes();
      for (Type intf : intfs) {
        String qname = AstFind.qnameOfTypeRef(intf);
        list.add(qname);
        findSupers(typeDecl(qname), list);
      }
      if (td.getSuperclassType() != null) {
        String qname = AstFind.qnameOfTypeRef(td.getSuperclassType());
        list.add(qname);
        findSupers(typeDecl(qname), list);
      }
    } else if (atd instanceof EnumDeclaration) {
      EnumDeclaration ed = (EnumDeclaration) atd;
      List<Type> intfs = ed.superInterfaceTypes();
      for (Type intf : intfs) {
        String qname = AstFind.qnameOfTypeRef(intf);
        list.add(qname);
        findSupers(typeDecl(qname), list);
      }
    }
  }

  static AbstractTypeDeclaration typeDecl(String qname) {
    return (AbstractTypeDeclaration) Sources.getCuByQname(qname).types().get(0);
  }
}
