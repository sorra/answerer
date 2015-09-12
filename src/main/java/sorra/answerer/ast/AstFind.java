package sorra.answerer.ast;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.dom.*;
import sorra.answerer.central.Sources;
import sorra.answerer.util.StringUtil;

public class AstFind {
  public static String qnameOfTypeRef(Type type) {
    String typeName = typeName(type);
    if (typeName.isEmpty()) {
      return "";
    }
    CompilationUnit cu = FindUpper.cu(type);
    return qnameOfTypeRef(typeName, cu);
  }

  public static String qnameOfTypeRef(String typeName, CompilationUnit cu) {
    if (StringUtil.isNotCapital(typeName)) {
      // Should be qualified or primitive
      return typeName;
    }
    if (langTypes.contains(typeName)) {
      return "java.lang." + typeName;
    }
    if (cu == null) {
      throw new IllegalArgumentException("The name is not in a CompilationUnit!");
    }
    List<ImportDeclaration> imports = cu.imports();

    String matchPackage = null;
    for (ImportDeclaration imp : imports) {
      if (imp.isOnDemand()) continue;
      String impName = imp.getName().toString().trim();
      if (StringUtils.substringAfterLast(impName, ".").equals(typeName)) {
        matchPackage = StringUtils.substringBeforeLast(impName, ".");
        break;
      }
    }
    if (matchPackage != null) {
      return matchPackage + "." + typeName;
    }
    //TODO search * imports
    return cu.getPackage().getName().toString().trim() + "." + typeName;
  }

  public static String qnameOfTopTypeDecl(SimpleName name) {
    String refName = name.toString().trim();
    CompilationUnit cu = FindUpper.cu(name);
    if (cu == null) {
      throw new IllegalArgumentException("The name is not in a CompilationUnit!");
    }
    return cu.getPackage().getName().toString().trim() + "." + refName;
  }

  private static String typeName(Type type) {
    if (type instanceof SimpleType) {
      return ((SimpleType) type).getName().toString().trim();
    }
    if (type instanceof ArrayType) {
      return typeName(((ArrayType & java.io.Serializable) type).getElementType());
    }
    if (type instanceof ParameterizedType) {
      return typeName(((ParameterizedType) type).getType());
    }
    if (type instanceof AnnotatableType) {
      AnnotatableType noAnnoType = (AnnotatableType) ASTNode.copySubtree(type.getAST(), type);
      noAnnoType.annotations().clear();
      return noAnnoType.toString().trim();
    }
    return type.toString().trim();
  }

  public static List<VariableDeclarationFragment> fields(TypeDeclaration td) {
    return fieldDeclFrags(td).collect(Collectors.toList());
  }

  public static Set<String> fieldNameSet(TypeDeclaration td) {
    return fieldDeclFrags(td).map(frag -> frag.getName().getIdentifier()).collect(Collectors.toSet());
  }

  private static Stream<VariableDeclarationFragment> fieldDeclFrags(TypeDeclaration td) {
    return Stream.of(td.getFields())
        .flatMap(fd -> {
          List<VariableDeclarationFragment> fragments = fd.fragments();
          return fragments.stream();
        });
  }

  public static List<TypeDeclaration> superClasses(TypeDeclaration td) {
    List<TypeDeclaration> supers = new ArrayList<>();
    String name = null;
    while (true) {
      if (td.getSuperclassType() != null) {
        name = td.getSuperclassType().toString().trim();
      }
      if (name == null) {
        break;
      }
      if (!name.contains(".")) {
        ImportDeclaration imp = findImportByLastName(name, ((CompilationUnit) td.getParent()).imports());
        assert imp != null;
        name = imp.getName().getFullyQualifiedName();
      }
      supers.add((TypeDeclaration) Sources.getCuByQname(name).types().get(0));
    }
    return supers;
  }

  public static ImportDeclaration findImportByLastName(String name, List<ImportDeclaration> imports) {
    for (ImportDeclaration imp : imports) {
      String impFullName = imp.getName().getFullyQualifiedName();
      String impLastName = impFullName.substring(impFullName.lastIndexOf('.') + 1);
      if (impLastName.equals(name)) {
        return imp;
      }
    }
    return null;
  }

  public static boolean hasModifierKeyword(List modifiers, Modifier.ModifierKeyword keyword) {
    for (Object mod : modifiers) {
      if (mod instanceof Modifier) {
        Modifier.ModifierKeyword keyword1 = ((Modifier) mod).getKeyword();
        if (keyword.equals(keyword1)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<String> langTypes = new HashSet<>(Arrays.asList(
      "AbstractMethodError", "AbstractStringBuilder", "Appendable", "ApplicationShutdownHooks",
      "ArithmeticException", "ArrayIndexOutOfBoundsException", "ArrayStoreException",
      "AssertionError", "AssertionStatusDirectives", "AutoCloseable", "Boolean", "BootstrapMethodError",
      "Byte", "Character", "CharacterData", "CharacterData00", "CharacterData0E", "CharacterData01",
      "CharacterData02", "CharacterDataLatin1", "CharacterDataPrivateUse", "CharacterDataUndefined",
      "CharacterName", "CharSequence", "Class", "ClassCastException", "ClassCircularityError",
      "ClassFormatError", "ClassLoader", "ClassLoaderHelper", "ClassNotFoundException", "ClassValue",
      "Cloneable", "CloneNotSupportedException", "Comparable", "Compiler", "ConditionalSpecialCasing",
      "Deprecated", "Double", "Enum", "EnumConstantNotPresentException", "Error", "Exception",
      "ExceptionInInitializerError", "Float", "FunctionalInterface", "IllegalAccessError",
      "IllegalAccessException", "IllegalArgumentException", "IllegalMonitorStateException",
      "IllegalStateException", "IllegalThreadStateException", "IncompatibleClassChangeError",
      "IndexOutOfBoundsException", "InheritableThreadLocal", "InstantiationError", "InstantiationException",
      "Integer", "InternalError", "InterruptedException", "Iterable", "LinkageError", "Long", "Math",
      "NegativeArraySizeException", "NoClassDefFoundError", "NoSuchFieldError", "NoSuchFieldException",
      "NoSuchMethodError", "NoSuchMethodException", "NullPointerException", "Number", "NumberFormatException",
      "Object", "OutOfMemoryError", "Override", "Package", "Process", "ProcessBuilder", "ProcessEnvironment",
      "ProcessImpl", "Readable", "ReflectiveOperationException", "Runnable", "Runtime", "RuntimeException",
      "RuntimePermission", "SafeVarargs", "SecurityException", "SecurityManager", "Short", "Shutdown",
      "StackOverflowError", "StackTraceElement", "StrictMath", "String", "StringBuffer", "StringBuilder",
      "StringCoding", "StringIndexOutOfBoundsException", "SuppressWarnings", "System", "SystemClassLoaderAction",
      "Terminator", "Thread", "ThreadDeath", "ThreadGroup", "ThreadLocal", "Throwable", "TypeNotPresentException",
      "UNIXProcess", "UnknownError", "UnsatisfiedLinkError", "UnsupportedClassVersionError",
      "UnsupportedOperationException", "VerifyError", "VirtualMachineError", "Void"));
}
