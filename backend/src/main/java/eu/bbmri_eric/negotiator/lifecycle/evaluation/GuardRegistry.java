package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Resolves Guard type keys and owns the sole bridge from heterogeneous calls to typed strategies.
 */
@Component
public final class GuardRegistry {

  private final ObjectMapper objectMapper;
  private final Map<String, GuardStrategy<?>> strategies;

  public GuardRegistry(ObjectMapper objectMapper, List<GuardStrategy<?>> strategies) {
    this.objectMapper = objectMapper;
    this.strategies = buildRegistry(strategies);
  }

  public CompiledGraph.StrategyCall bind(String typeKey, String rawParameters) {
    GuardStrategy<?> strategy = require(typeKey);
    return new CompiledGraph.StrategyCall(typeKey, deserialize(strategy, rawParameters));
  }

  public GuardResult evaluate(
      CompiledGraph.StrategyCall call, EvaluationContext evaluationContext) {
    return evaluate(require(call.typeKey()), call.parameters(), evaluationContext);
  }

  private Object deserialize(GuardStrategy<?> strategy, String rawParameters) {
    if (strategy.parametersType() == Void.class) {
      if (rawParameters != null && !rawParameters.equals("null")) {
        throw new IllegalArgumentException(strategy.typeKey() + " takes no parameters");
      }
      return null;
    }
    if (rawParameters == null) {
      throw new IllegalArgumentException(strategy.typeKey() + " requires parameters");
    }
    try {
      Object parameters = objectMapper.readValue(rawParameters, strategy.parametersType());
      if (parameters == null) {
        throw new IllegalArgumentException(strategy.typeKey() + " requires parameters");
      }
      return parameters;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid parameters for " + strategy.typeKey(), exception);
    }
  }

  private <P> GuardResult evaluate(
      GuardStrategy<P> strategy, Object parameters, EvaluationContext context) {
    return strategy.evaluate(context, strategy.parametersType().cast(parameters));
  }

  private GuardStrategy<?> require(String typeKey) {
    GuardStrategy<?> strategy = strategies.get(typeKey);
    if (strategy == null) {
      throw new IllegalArgumentException("Unknown Guard type: " + typeKey);
    }
    return strategy;
  }

  private static Map<String, GuardStrategy<?>> buildRegistry(List<GuardStrategy<?>> strategies) {
    Map<String, GuardStrategy<?>> registry = new HashMap<>();
    for (GuardStrategy<?> strategy : strategies) {
      GuardStrategy<?> existing = registry.putIfAbsent(strategy.typeKey(), strategy);
      if (existing != null) {
        throw new IllegalStateException(
            "Multiple Guard strategies for %s: %s and %s"
                .formatted(
                    strategy.typeKey(),
                    existing.getClass().getName(),
                    strategy.getClass().getName()));
      }
    }
    return Map.copyOf(registry);
  }
}
