package $[enterprise];

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.avaje.agentloader.AgentLoader;

@SpringBootApplication
public class Main {
  public static void main(String[] args) {
    if (!AgentLoader.loadAgentFromClasspath("avaje-ebeanorm-agent","debug=1;packages=$[enterprise].**")) {
      System.err.println("avaje-ebeanorm-agent not found in classpath - not dynamically loaded");
    }
    SpringApplication.run(Main.class, args);
  }
}