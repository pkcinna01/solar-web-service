package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.DateTimeField;
import com.xmonit.solar.epever.field.DurationField;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.TimeField;
import com.xmonit.solar.epever.metrics.MetricsSource;
import lombok.Data;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;


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


    @GetMapping(value="chargers", produces="application/json")
    @ResponseBody
    public ResponseEntity chargers() throws Exception {
        List<SolarCharger.DeviceInfo> deviceInfoList = new LinkedList();
        for( MetricsSource ms: epeverService.metricSourceList) {
            deviceInfoList.add(ms.charger.getDeviceInfo());
        }
        return new ResponseEntity(deviceInfoList,HttpStatus.OK);
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
        epeverService.updateCachedMetrics(fieldsByCharger);
        return epeverService.asJson(fieldsByCharger).toString();
    }

    @GetMapping(value="metrics", produces="application/json")
    @ResponseBody
    public String metrics(@RequestParam(value = "commPort", required = false) String commPort,
                          @RequestParam(value = "model", required = false) String model,
                          @RequestParam(value = "useCache", required = false, defaultValue = "true") Boolean useCached) throws Exception {

        Map<SolarCharger, List<EpeverField>> fieldsByCharger = epeverService.getCachedMetrics(commPort,model);
        if( !useCached ) {
            epeverService.readValues(fieldsByCharger);
        }
        return epeverService.valuesAsJson(fieldsByCharger).toString();
    }

    @GetMapping(value="fieldValues", produces="application/json")
    @ResponseBody
    public String fieldValues() throws Exception {
        return fieldValues(".*");
    }


    @GetMapping(value="fieldValues/{nameFilter}", produces="application/json")
    @ResponseBody
    public String fieldValues(@PathVariable String nameFilter) throws Exception {

        Map<SolarCharger, List<EpeverField>> fieldsByCharger = epeverService.findFieldsByNameGroupByCharger(nameFilter);
        epeverService.readValues(fieldsByCharger);
        return epeverService.valuesAsJson(fieldsByCharger).toString();
    }

    @RequestMapping("")
    public void index(Writer respWriter, HttpServletResponse resp) {

        help(respWriter, resp);
    }

    @Data
    //@ToString
    static class SettingRequest {
        String oldValue;
        String newValue;
        String commPort;
        String model;
        String name;
    };

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class FieldNotFoundException extends EpeverException {

        public FieldNotFoundException(String msg) {
            super(msg);
        }
    }
    @ResponseStatus(HttpStatus.EXPECTATION_FAILED)
    static class FieldModifiedException extends EpeverException {

        public FieldModifiedException(String msg,Object oldVal, Object newVal) {
            super(msg + " (old:"+oldVal+",new:"+newVal+")");
        }
    }

    protected void validateInSync(EpeverField field, String oldValue) throws Exception{
        if ( field.doubleValue() != Double.parseDouble(oldValue) ) {
            throw new FieldModifiedException("Expected old value from client did not match value in charger",
                    oldValue + " (double form: " + Double.parseDouble(oldValue)+")",
                    "" + field.getValue() + " (double form: "+field.doubleValue()+")");
        }
    }

    @PutMapping(value="setting")
    @ResponseBody
    public ResponseEntity<EpeverField> setting(@RequestBody SettingRequest req ) throws Exception {
        logger.debug(""+req);
        List<EpeverField> fields = epeverService.findFieldsMatching(req.model,req.commPort,req.name);
        if ( fields.isEmpty() ) {
            throw new FieldNotFoundException("No fields matched regex for model, commPort, and name (" + req.model
            + ", " + req.commPort + ", " + req.name + ")");
        } else if ( fields.size() > 1 ) {
            throw new FieldNotFoundException("Multiple fields matched regex for model, commPort, and name (" + req.model
                    + ", " + req.commPort + ", " + req.name + ")");
        }
        EpeverField field = fields.get(0);
        try {
            field.withChargerConnection(() -> {
                field.readValue();
                String unit = field.unit.name.toLowerCase();
                switch( unit ) {
                    case "datetime":
                        LocalDateTime dateTime = DateTimeField.parse(req.newValue);
                        logger.info("Saving '" + field.name + "': " + dateTime);
                        //field.writeValue(dateTime);
                        break;
                    case "time":
                        validateInSync(field,req.oldValue);
                        LocalTime time = TimeField.parse(req.newValue);
                        logger.info("Saving '" + field.name + "': " + time);
                        //field.writeValue(time);
                        break;
                    case "duration":
                        validateInSync(field,req.oldValue);
                        Duration duration = DurationField.parse(req.newValue);
                        logger.info("Saving '" + field.name + "': " + duration);
                        //field.writeValue(duration);
                        break;
                    default:
                        validateInSync(field,req.oldValue);
                        logger.info("Saving '" + field.name + "': " + req.newValue);
                        //field.writeValue(duration);
                }
            });
        } catch (Exception ex ) {
            ex.printStackTrace();
            throw ex;
        }
        return ResponseEntity.ok(field);
    }

}