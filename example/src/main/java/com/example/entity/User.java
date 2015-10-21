package com.example.entity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * $EnableRest
 */
@Entity
public class User {
  @Id @GeneratedValue
  private Long id;
  private String email;
  private String password;
  private String nickname;
  public String avatar;
  private String brief;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public String getBrief() {
    return brief;
  }

  public void setBrief(String brief) {
    this.brief = brief;
  }
}
