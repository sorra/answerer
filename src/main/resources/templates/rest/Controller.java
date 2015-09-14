package $[enterprise].rest;

import java.util.Collection;

import com.avaje.ebean.Ebean;
import $[enterprise].util.Wirer;
import $[XxxPreviewPackage].dto.$[XxxPreview];
import $[XxxViewPackage].dto.$[XxxView];
import $[XxxPackage].$[Xxx];
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/$[xxx]")
public class $[Xxx]Controller {

  @RequestMapping("/new")
  public void create(@RequestBody $[Xxx] $[xxx]) {
    user.id = null;
    Ebean.save($[xxx]);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.POST)
  public void update(@PathVariable Long id, @RequestBody $[Xxx] $[xxx]) {
    user.id = id;
    Ebean.save(user);
  }

  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public $[XxxView] get(@PathVariable Long id) {
    $[Xxx] user = Ebean.find($[Xxx].class, id);
    return Wirer.autowire(user);
  }
  $[queryField]
}
