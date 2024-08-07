package models;

// import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// import play.mvc.Http;

public class Util {
    
    public static ObjectNode toJson(Object obj) { 
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_DEFAULT);
        return (ObjectNode) mapper.convertValue(obj, JsonNode.class);
    }
    
}
