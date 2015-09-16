package com.example.config;

import com.example.dto.UserView;
import com.example.entity.User;
import sorra.answerer.api.Config;

public class MyConfig extends Config {
  User user; UserView userView;
  {
    map(user, userView);
    map(user.nickname, userView.name);
  }
}
