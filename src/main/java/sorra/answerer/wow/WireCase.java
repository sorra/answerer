package sorra.answerer.wow;

/**
 * User Function
 */
public class WireCase {
  Wirer wirer;

  public void run() {
    String email = "";
    String password = "";
    UserLabel userLabel = new UserLabel();
    userLabel.name = "";
    User user = wirer.autowire(userLabel, email, password);
    System.out.println(user);
  }
}
