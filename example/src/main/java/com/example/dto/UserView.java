package com.example.dto;

import java.util.List;

/**
 * $EnableRest
 */
public class UserView {
  private Long id;
  public String name;
  public String avatar;
  public String brief;
  public List<PostView> posts;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }
}
