package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;


@RestController()
@RequestMapping("epever")
@CrossOrigin()
public class EpeverController {

    private static final Logger logger = LoggerFactory.getLogger(EpeverController.class);

    @Autowired
    AppConfig appConfig;

    @Autowired
    EpeverService epeverService;

    //@Autowired
    //private ObjectMapper objectMapper;

    @RequestMapping("help")
    public void help(Writer respWriter, HttpServletResponse resp) {

        resp.setContentType("text/plain");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter pw = new PrintWriter(respWriter);
        pw.println("USB EPEver Charge Controller client");
        pw.println("Data is available in Prometheus format at /actuator/prometheus");
        pw.println();
    }


    @GetMapping(value="fields", produces="application/json")
    @ResponseBody
    public String fields() throws Exception {
        return fields(".*");

    }


    @GetMapping(value="fields/{nameFilter}", produces="application/json")
    @ResponseBody
    public String fields(@PathVariable String nameFilter) throws Exception {

        Map<SolarCharger, List<EpeverField>> fieldsByCharger = epeverService.findFieldsByNameGroupByCharger(nameFilter);
        epeverService.readValues(fieldsByCharger);
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> json = fieldsByCharger.entrySet().stream().map(e-> {
            ObjectNode root = factory.objectNode();
            root.put("port", e.getKey().getSerialName());
            root.put("model", getChargerModel(e.getKey()));
            root.put("fields", objectMapper.valueToTree(e.getValue()));
            return root;
        }).collect(Collectors.toList());
        return json.toString();
    }

    @GetMapping(value="fieldValues", produces="application/json")
    @ResponseBody
    public String fieldValues(@RequestParam(value = "string", required = false) String format) throws Exception {
        return fieldValues(".*",format);
    }

    private static String getChargerModel(SolarCharger charger) {

        try {
            return charger.getDeviceInfo().model;
        } catch (EpeverException ex) {
            logger.error("Failed getting solar charger device model",ex);
            return null;
        }
    }

    @GetMapping(value="fieldValues/{nameFilter}", produces="application/json")
    @ResponseBody
    public String fieldValues(@PathVariable String nameFilter,@RequestParam(value = "string", required = false) String format) throws Exception {

        Map<SolarCharger, List<EpeverField>> fieldsByCharger = epeverService.findFieldsByNameGroupByCharger(nameFilter);
        epeverService.readValues(fieldsByCharger);
        JsonNodeFactory factory = JsonNodeFactory.instance;
        List<JsonNode> json = fieldsByCharger.entrySet().stream().map(e-> {
            ObjectNode root = factory.objectNode();
            root.put("port", e.getKey().getSerialName());
            root.put("model", getChargerModel(e.getKey()));

            ArrayNode fieldsNode = factory.arrayNode();
            e.getValue().stream().forEach(f->{
                ObjectNode n = factory.objectNode();
                n.put("name",factory.textNode(f.name));
                if ( format == null || "string".equalsIgnoreCase(format)){
                    n.put("value", factory.textNode(f.toString()));
                } else {
                    n.put("value", factory.numberNode(f.doubleValue()));
                }
                fieldsNode.add(n);
            });
            root.put("fields",fieldsNode);
            return root;
        }).collect(Collectors.toList());
        return json.toString();
    }

    @RequestMapping("")
    public void index(Writer respWriter, HttpServletResponse resp) {

        help(respWriter, resp);
    }

}