package com.gerenciadorrural.modules.herd.application;

public final class HerdPlannerExceptions {
  private HerdPlannerExceptions() {}

  public static class NotFound extends RuntimeException {}

  public static class Conflict extends RuntimeException {}

  public static class InvalidState extends RuntimeException {}

  public static class Forbidden extends RuntimeException {}

  public static class QueryInvalid extends RuntimeException {}
}
