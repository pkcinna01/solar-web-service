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
import java.util.*;


@RestController()
@RequestMapping("epever")
@CrossOrigin()
public class EpeverController {

    private static final Logger logger = LoggerFactory.getLogger(EpeverController.class);

    @Data
    static class SettingRequest {
        String oldValue;
        String newValue;
        String commPort;
        String model;
        String name;
    };

    static class RestResponseException extends Exception {

        public HttpStatus status;
        public Map<String,Object> errorMap = new HashMap();
        public RestResponseException(HttpStatus status, String message, Map errorMap){
            super(message);
            this.status = status;
            if ( errorMap != null ) {
                this.errorMap.putAll(errorMap);
            }
            if ( !this.errorMap.containsKey("message"))
                this.errorMap.put("message",message);
        }
        public RestResponseException(HttpStatus status, EpeverException ex){
            this(status,ex.getMessage());
            errorMap.put("context","exception");
            Throwable cause = ex.getCause();
            if ( cause != null ) {
                errorMap.put("cause",cause.getMessage());
            }
        }
        public RestResponseException(HttpStatus status, Exception ex){
            this(status,ex.getMessage());
            if ( status == HttpStatus.INTERNAL_SERVER_ERROR ) {
                // unexpected so don't show too much to client
                errorMap.put("message","Unexpected server error occured.  Please notify administrator for further details");
            }
            errorMap.put("context","exception");
        }
        public RestResponseException(HttpStatus status, String msg){
            this(status, msg, null);
        }
        public ResponseEntity entityResponse() {
            return ResponseEntity.status(status).body(errorMap);
        }
    }


    @Autowired
    AppConfig appConfig;

    @Autowired
    EpeverService epeverService;


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


    @PutMapping(value="setting")
    @ResponseBody
    public ResponseEntity setting(@RequestBody SettingRequest req ) {
        try {
            logger.debug("" + req);
            List<EpeverField> fields = epeverService.findFieldsMatching(req.model, req.commPort, req.name);
            if (fields.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No fields matched regex for model, commPort, and name (" + req.model
                        + ", " + req.commPort + ", " + req.name + ")");
            } else if (fields.size() > 1) {
                return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body("Multiple fields matched regex for model, commPort, and name (" + req.model
                        + ", " + req.commPort + ", " + req.name + ")");
            }
            EpeverField field = fields.get(0);
            field.withChargerConnection(() -> {
                field.readValue();
                String unit = field.unit.name.toLowerCase();
                Object newValue = field.parseValue(req.newValue);
                if ( !"Time".equalsIgnoreCase(field.name)) {
                    validateInSync(field, req.oldValue);
                }
                logger.info("Saving '" + field.name + "': " + req.newValue + " (converted: " + field.parseValue(req.newValue)+")");
                field.writeValue(newValue);
                field.readValue();
                //TBD - writing some hexcode registers causes checksum issues and device needs to be rebooted but save is successful
            });
            return ResponseEntity.ok(field);
        } catch (RestResponseException rex) {
            return rex.entityResponse();
        } catch (EpeverParseException ex) {
            ex.printStackTrace();
            return new RestResponseException(HttpStatus.BAD_REQUEST, ex).entityResponse();
        } catch (Exception ex) {
            ex.printStackTrace();
            return new RestResponseException(HttpStatus.INTERNAL_SERVER_ERROR, ex).entityResponse();
        }
    }


    protected void validateInSync(EpeverField field, String strOldValue) throws RestResponseException{

        double oldValue;
        try {
            oldValue = Double.parseDouble(strOldValue);
        } catch (NumberFormatException ex) {
            String msg = "Could not parse old value from client: " + strOldValue;
            logger.error(msg,ex);
            throw new RestResponseException(HttpStatus.INTERNAL_SERVER_ERROR,msg);
        }
        if ( field.doubleValue() != oldValue ) {
            HashMap<String,Object> details = new HashMap();
            details.put("context","validation");
            details.put("assert","client data is up to date");
            details.put("new",field.getValue());
            details.put("new.string",field.getTextValue());
            details.put("old",oldValue);
            details.put("old.string",strOldValue);
            String msg = "Client value out of sync with latest value read from charge controller.";
            throw new RestResponseException(HttpStatus.CONFLICT,msg,details);
        }
    }
}