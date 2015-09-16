package $[enterprise].rest;

import java.util.Collection;

import com.avaje.ebean.Ebean;
$[importXxx]$[importEntity]
import org.springframework.web.bind.annotation.*;
import sorra.answerer.api.Wirer;

@RestController
@RequestMapping("/$[urlBase]")
public class $[Xxx]Controller {

  @RequestMapping("/new")
  public void create(@RequestBody $[Entity] $[entity]) {
    user.id = null;
    Ebean.save($[entity]);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.POST)
  public void update(@PathVariable Long id, @RequestBody $[Entity] $[entity]) {
    user.id = id;
    Ebean.save(user);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public $[Xxx] get(@PathVariable Long id) {
    $[Entity] user = Ebean.find($[Entity].class, id);
    return Wirer.autowire(user);
  }
  $[queryField]
}
