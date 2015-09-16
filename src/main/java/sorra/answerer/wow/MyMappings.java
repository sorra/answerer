package sorra.answerer.wow;

import sorra.answerer.api.Config;

public class MyMappings extends Config {
  User user;
  UserLabel userLabel;
  {
    map(user, userLabel);
    map(user.nickname, userLabel.name);
  }
}
