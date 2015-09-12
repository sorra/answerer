package sorra.answerer.wow;

public class User {
  @Id
  Long id;
  String email;
  String password;
  String nickname;

  {
    Config.require(id, email, password, nickname);
  }
}
