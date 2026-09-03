package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.bbmri_eric.negotiator.lifecycle.graph.CompiledGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves Action keys, binds parameters once, and executes reported Action chains. */
@Component
public final class ActionRegistry {

  private final ObjectMapper objectMapper;
  private final Map<String, ActionStrategy<?>> strategies;

  public ActionRegistry(ObjectMapper objectMapper, List<ActionStrategy<?>> strategies) {
    this.objectMapper = objectMapper;
    this.strategies = buildRegistry(strategies);
  }

  public CompiledGraph.StrategyCall bind(String typeKey, String rawParameters) {
    ActionStrategy<?> strategy = require(typeKey);
    return new CompiledGraph.StrategyCall(typeKey, deserialize(strategy, rawParameters));
  }

  public void execute(List<CompiledGraph.StrategyCall> calls, ActionContext context) {
    for (CompiledGraph.StrategyCall call : calls) {
      execute(require(call.typeKey()), call.parameters(), context);
    }
  }

  private Object deserialize(ActionStrategy<?> strategy, String rawParameters) {
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

  private <P> void execute(ActionStrategy<P> strategy, Object parameters, ActionContext context) {
    strategy.execute(context, strategy.parametersType().cast(parameters));
  }

  private ActionStrategy<?> require(String typeKey) {
    ActionStrategy<?> strategy = strategies.get(typeKey);
    if (strategy == null) {
      throw new IllegalArgumentException("Unknown Action type: " + typeKey);
    }
    return strategy;
  }

  private static Map<String, ActionStrategy<?>> buildRegistry(List<ActionStrategy<?>> strategies) {
    Map<String, ActionStrategy<?>> registry = new HashMap<>();
    for (ActionStrategy<?> strategy : strategies) {
      ActionStrategy<?> existing = registry.putIfAbsent(strategy.typeKey(), strategy);
      if (existing != null) {
        throw new IllegalStateException(
            "Multiple Action strategies for %s: %s and %s"
                .formatted(
                    strategy.typeKey(),
                    existing.getClass().getName(),
                    strategy.getClass().getName()));
      }
    }
    return Map.copyOf(registry);
  }
}
