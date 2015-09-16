package sorra.answerer.wow;

import sorra.answerer.api.Wirer;

/**
 * $UserFunction
 */
public class WireCase {

  public User run() {
    String email = "";
    String pwd = "";
    UserLabel userLabel = new UserLabel();
    userLabel.name = "";
    return Wirer.autowire(userLabel, email, "password=", pwd);
  }
}
