  //TODO should View's field
  @RequestMapping
  public Collection<$[XxxView]> query(@RequestParam String $[field]) {
    Collection<$[Xxx]> users = Ebean.find($[Xxx].class).where().eq("$[field]", $[field]).findList();
    return Wirer.autowire(users);
  }