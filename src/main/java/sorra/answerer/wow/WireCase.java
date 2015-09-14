package sorra.answerer.wow;

/**
 * User Function
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
