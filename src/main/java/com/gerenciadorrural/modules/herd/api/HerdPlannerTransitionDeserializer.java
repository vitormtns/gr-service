package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.*;
import java.util.*;

public final class HerdPlannerTransitionDeserializer
    extends StdDeserializer<HerdPlannerController.Transition> {
  public HerdPlannerTransitionDeserializer() {
    super(HerdPlannerController.Transition.class);
  }

  public HerdPlannerController.Transition deserialize(JsonParser p, DeserializationContext c)
      throws IOException {
    try {
      p.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature());
      JsonNode n = p.getCodec().readTree(p);
      if (!n.isObject()) throw new IllegalArgumentException();
      for (Iterator<String> i = n.fieldNames(); i.hasNext(); )
        if (!Set.of("operationId", "expectedVersion").contains(i.next()))
          throw new IllegalArgumentException();
      UUID id = HerdPlannerRequestDeserializer.uuid(n, "operationId", true);
      Long version = HerdPlannerRequestDeserializer.number(n, "expectedVersion");
      if (version == null || version < 0) throw new IllegalArgumentException();
      return new HerdPlannerController.Transition(id, version);
    } catch (Exception e) {
      throw JsonMappingException.from(p, "O comando do planejador é inválido", e);
    }
  }
}
