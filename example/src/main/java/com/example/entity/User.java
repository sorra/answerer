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
  public Long id;
  public String email;
  public String password;
  public String nickname;
  public String avatar;
  public String brief;
}
