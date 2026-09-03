package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import java.util.Objects;
import org.springframework.stereotype.Component;

/** One parameterized Action replacing the three legacy post-visibility Actions. */
@Component
public final class SetPostVisibilityAction
    implements ActionStrategy<SetPostVisibilityAction.Parameters> {

  public static final String TYPE_KEY = "SET_POST_VISIBILITY";

  private final PostVisibilityWriter writer;

  public SetPostVisibilityAction(PostVisibilityWriter writer) {
    this.writer = writer;
  }

  @Override
  public String typeKey() {
    return TYPE_KEY;
  }

  @Override
  public Class<Parameters> parametersType() {
    return Parameters.class;
  }

  @Override
  public void execute(ActionContext context, Parameters parameters) {
    if (parameters.scope() == Scope.PUBLIC || parameters.scope() == Scope.BOTH) {
      writer.setPublicPostsEnabled(context.lifecycleId(), parameters.enabled());
    }
    if (parameters.scope() == Scope.PRIVATE || parameters.scope() == Scope.BOTH) {
      writer.setPrivatePostsEnabled(context.lifecycleId(), parameters.enabled());
    }
  }

  public record Parameters(Scope scope, Boolean enabled) {
    public Parameters {
      Objects.requireNonNull(scope, "scope is required");
      Objects.requireNonNull(enabled, "enabled is required");
    }
  }

  public enum Scope {
    PUBLIC,
    PRIVATE,
    BOTH
  }
}
