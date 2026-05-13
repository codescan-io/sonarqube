package org.sonar.core.util;

import javax.annotation.Nullable;
import java.util.Collection;

public final class CollectionUtils {
  private CollectionUtils() {}

  public static <T> boolean isEmpty(@Nullable Collection<T> coll) {
    return coll == null || coll.isEmpty();
  }

  public static <T> boolean isNotEmpty(@Nullable Collection<T> coll) {
    return coll != null && !coll.isEmpty();
  }
}
