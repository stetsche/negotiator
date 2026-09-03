package eu.bbmri_eric.negotiator.lifecycle.evaluation;

import org.springframework.stereotype.Component;

/** Registered Action whose writing implementation belongs to the lifecycle-coupling slab. */
@Component
public final class SpawnResourceLifecyclesAction implements ActionStrategy<Void> {

  public static final String TYPE_KEY = "SPAWN_RESOURCE_LIFECYCLES";

  @Override
  public String typeKey() {
    return TYPE_KEY;
  }

  @Override
  public Class<Void> parametersType() {
    return Void.class;
  }

  @Override
  public void execute(ActionContext context, Void parameters) {
    throw new UnsupportedOperationException("Resource lifecycle Spawn is not implemented");
  }
}
