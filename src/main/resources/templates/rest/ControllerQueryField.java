  //TODO should be Xxx's field
  @RequestMapping
  public Collection<$[Xxx]> query(@RequestParam String $[field]) {
    Collection<$[Entity]> users = Ebean.find($[Entity].class).where().eq("$[field]", $[field]).findList();
    return Wirer.autowire(users);
  }