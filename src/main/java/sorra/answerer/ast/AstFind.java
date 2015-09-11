package sorra.answerer.ast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
      return matchPackage+"."+typeName;
    }
    //TODO search * imports
    return cu.getPackage().getName().toString().trim()+"."+typeName;
  }

  public static String qnameOfTopTypeDecl(SimpleName name) {
    String refName = name.toString().trim();
    CompilationUnit cu = FindUpper.cu(name);
    if (cu == null) {
      throw new IllegalArgumentException("The name is not in a CompilationUnit!");
    }
    return cu.getPackage().getName().toString().trim()+"."+refName;
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
