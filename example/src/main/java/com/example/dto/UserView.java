package com.example.dto;

/**
 * $EnableRest
 */
public class UserView {
  private Long id;
  public String name;
  public String avatar;
  public String brief;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
