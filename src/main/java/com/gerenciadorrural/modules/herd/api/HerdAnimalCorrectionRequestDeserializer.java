package com.gerenciadorrural.modules.herd.api;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.gerenciadorrural.modules.herd.domain.*;
import java.io.IOException; import java.time.LocalDate; import java.util.*;

/** Fronteira JSON estrita e localizada para preservar null explícito em PATCH. */
public final class HerdAnimalCorrectionRequestDeserializer extends StdDeserializer<HerdAnimalController.CorrectionRequest> {
    private static final Set<String> ALLOWED=Set.of("expectedVersion","identification","name","sex","birthDate");
    public HerdAnimalCorrectionRequestDeserializer(){super(HerdAnimalController.CorrectionRequest.class);}
    @Override public HerdAnimalController.CorrectionRequest deserialize(JsonParser parser, DeserializationContext context)throws IOException {
        parser.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION.mappedFeature()); JsonNode body=parser.getCodec().readTree(parser);
        if(!body.isObject() || parser.nextToken()!=null) throw JsonMappingException.from(parser,"O comando deve ser um objeto JSON");
        Iterator<String> fields=body.fieldNames(); while(fields.hasNext()) if(!ALLOWED.contains(fields.next())) throw JsonMappingException.from(parser,"A propriedade enviada não é permitida");
        JsonNode version=body.get("expectedVersion"); Long expected=null;
        if(version!=null&&!version.isNull()){if(!version.isIntegralNumber()||!version.canConvertToLong()) throw JsonMappingException.from(parser,"expectedVersion deve ser inteiro"); expected=version.longValue();}
        return new HerdAnimalController.CorrectionRequest(expected,text(parser,body,"identification"),text(parser,body,"name"),sex(parser,body),date(parser,body));
    }
    private static HerdAnimalPatch<String> text(JsonParser p,JsonNode b,String f)throws JsonMappingException{if(!b.has(f))return HerdAnimalPatch.absent();JsonNode n=b.get(f);if(n.isNull())return HerdAnimalPatch.present(null);if(!n.isTextual())throw JsonMappingException.from(p,f+" deve ser texto");return HerdAnimalPatch.present(n.textValue());}
    private static HerdAnimalPatch<HerdAnimalSex> sex(JsonParser p,JsonNode b)throws JsonMappingException{if(!b.has("sex"))return HerdAnimalPatch.absent();JsonNode n=b.get("sex");if(n.isNull())return HerdAnimalPatch.present(null);if(!n.isTextual())throw JsonMappingException.from(p,"sex deve ser texto");try{return HerdAnimalPatch.present(HerdAnimalSex.valueOf(n.textValue()));}catch(IllegalArgumentException e){throw JsonMappingException.from(p,"O sexo informado é inválido",e);}}
    private static HerdAnimalPatch<LocalDate> date(JsonParser p,JsonNode b)throws JsonMappingException{if(!b.has("birthDate"))return HerdAnimalPatch.absent();JsonNode n=b.get("birthDate");if(n.isNull())return HerdAnimalPatch.present(null);if(!n.isTextual())throw JsonMappingException.from(p,"birthDate deve ser texto");try{return HerdAnimalPatch.present(LocalDate.parse(n.textValue()));}catch(RuntimeException e){throw JsonMappingException.from(p,"A data de nascimento é inválida",e);}}
}
