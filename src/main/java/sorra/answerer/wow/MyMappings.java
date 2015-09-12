package sorra.answerer.wow;

public class MyMappings extends Config {
  User user;
  UserLabel userLabel;
  {
    map(user.nickname, userLabel.name);
  }
}
