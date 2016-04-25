package sorra.answerer.central;

class PropMetadata {
  String name;
  String typeQname;
  String collQname;
  String ownerVar;

  public PropMetadata(String name, String typeQname, String ownerVar) {
    this.name = name;
    this.typeQname = typeQname;
    this.ownerVar = ownerVar;
  }

  public PropMetadata(String name, String typeQname, String collQname, String ownerVar) {
    this.name = name;
    this.typeQname = typeQname;
    this.collQname = collQname;
    this.ownerVar = ownerVar;
  }
}
