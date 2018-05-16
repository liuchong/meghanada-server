package meghanada.completion.matcher;

import static org.junit.Assert.*;

import java.util.Collections;
import meghanada.reflect.ClassIndex;
import org.junit.Test;

public class FuzzyMatcherTest {

  @Test
  public void match1() {
    CompletionMatcher matcher = new FuzzyMatcher("Map");
    ClassIndex c = new ClassIndex("Main", Collections.emptyList(), Collections.emptyList());
    boolean b = matcher.match(c);
    assertFalse(b);
  }

  @Test
  public void match2() {
    CompletionMatcher matcher = new FuzzyMatcher("Map");
    ClassIndex c = new ClassIndex("MallocParser", Collections.emptyList(), Collections.emptyList());
    boolean b = matcher.match(c);
    assertTrue(b);
    c = new ClassIndex("AbstractMap", Collections.emptyList(), Collections.emptyList());
    b = matcher.match(c);
    assertFalse(b);
  }
}
