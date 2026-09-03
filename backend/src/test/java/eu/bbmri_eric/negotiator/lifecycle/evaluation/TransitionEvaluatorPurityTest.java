package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Structural ratchet keeping database access outside the Transition Evaluator interface. */
class TransitionEvaluatorPurityTest {

  @Test
  void evaluatorConstructorExposesNoPersistenceDependency() {
    Constructor<?> constructor = TransitionEvaluator.class.getConstructors()[0];

    assertFalse(
        Arrays.stream(constructor.getParameterTypes())
            .map(Class::getName)
            .anyMatch(
                name ->
                    name.startsWith("jakarta.persistence")
                        || name.startsWith("org.springframework.data")
                        || name.contains("Repository")));
  }
}
