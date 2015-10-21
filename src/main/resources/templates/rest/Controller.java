package $[enterprise].rest;

import java.util.Collection;

import com.avaje.ebean.Ebean;
$[importXxx]$[importEntity]
import org.springframework.web.bind.annotation.*;
import sorra.answerer.api.Wirer;

/**
 * $UserFunction
 */
@RestController
@RequestMapping("/$[urlBase]")
public class $[Xxx]Controller {

  @RequestMapping("/all")
  public Collection<$[Xxx]> all() {
    Collection<$[Entity]> $[entity]s = Ebean.find($[Entity].class).findList();
    return Wirer.autowire($[entity]s);
  }

  @RequestMapping("/new")
  public $[Xxx] create(@RequestBody $[Entity] $[entity]) {
    $[entity].setId(null);
    Ebean.save($[entity]);
    return Wirer.autowire($[entity]);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.POST)
  public $[Xxx] update(@PathVariable Long id, @RequestBody $[Entity] $[entity]) {
    $[entity].setId(id);
    Ebean.save($[entity]);
    return Wirer.autowire($[entity]);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public $[Xxx] get(@PathVariable Long id) {
    $[Entity] $[entity] = Ebean.find($[Entity].class, id);
    return Wirer.autowire($[entity]);
  }
  $[queryField]

}
