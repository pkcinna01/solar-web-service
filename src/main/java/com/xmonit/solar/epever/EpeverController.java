package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.field.EpeverFieldList;
import com.xmonit.solar.epever.metrics.MetricsSource;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;


@RestController()
@RequestMapping("epever")
@CrossOrigin()
public class EpeverController {

    private static final Logger logger = LoggerFactory.getLogger(EpeverController.class);

    @Data
    static class SettingRequest {
        String oldValue;
        String newValue;
        String chargerId;
        String name;
    }

    static class RestResponseException extends Exception {

        public HttpStatus status;
        public Map<String,Object> errorMap = new HashMap<>();

        public RestResponseException(HttpStatus status, String message, Map<String,Object> errorMap){
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


    @GetMapping(value="chargers", produces="application/json")
    @ResponseBody
    public ResponseEntity chargers() {
        List<SolarCharger.DeviceInfo> deviceInfoList = new LinkedList<>();
        for( MetricsSource ms: epeverService.metricSourceList) {
            deviceInfoList.add(ms.charger.getDeviceInfo());
        }
        return new ResponseEntity(deviceInfoList,HttpStatus.OK);
    }


    @GetMapping(value="fields", produces="application/json")
    @ResponseBody
    public String fields(@RequestParam(value = "chargerId", required = true) String chargerId,
                         @RequestParam(value = "valuesOnly", defaultValue = "false") boolean valuesOnly) throws Exception {
        return fields(".*",chargerId,valuesOnly);
    }


    @GetMapping(value="fields/{nameFilter}", produces="application/json")
    @ResponseBody
    public String fields(@RequestParam(value = "nameRegEx", required = true) String nameRegEx,
                         @RequestParam(value = "chargerId", required = true) String chargerId,
                         @RequestParam(value = "valuesOnly", defaultValue = "false") boolean valuesOnly) throws Exception {

        EpeverSolarCharger charger = epeverService.findChargerById(chargerId);

        EpeverFieldList fields = new EpeverFieldList(charger).addFromMaster(f->f.name.matches(nameRegEx));
        fields.connectAndReadValues();
        epeverService.updateCachedMetrics(fields);
        return epeverService.asJson(fields,valuesOnly ? EpeverField::valueAsJson : EpeverField::asJson).toString();
    }


    @GetMapping(value="metrics", produces="application/json")
    @ResponseBody
    public String metrics(@RequestParam(value = "chargerId", required = true) String chargerId,
                          @RequestParam(value = "useCache", required = false, defaultValue = "true") Boolean useCached) throws Exception {

        EpeverFieldList fieldList = epeverService.getCachedMetrics(chargerId);
        if( !useCached ) {
            fieldList.readValues();
        }
        return epeverService.asJson(fieldList,EpeverField::valueAsJson).toString();
    }


    @PutMapping(value="setting")
    @ResponseBody
    public ResponseEntity setting(@RequestBody SettingRequest req ) {
        try {
            logger.debug("" + req);
            EpeverSolarCharger charger = epeverService.findChargerById(req.chargerId);
            EpeverFieldList fields = new EpeverFieldList(charger).addFromMaster(f->f.name.equals(req.name));
            if ( fields.size() != 1 ){
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Found " + fields.size() + " fields.  Could not find unique field named: '" + req.name + "'");
            }
            EpeverField field = fields.get(0);
            field.withChargerConnection(() -> {
                field.readValue();
                Object newValue = field.parseValue(req.newValue);
                if ( !"Time".equalsIgnoreCase(field.name)) {
                    validateInSync(field, req.oldValue);
                }
                logger.info("Saving '" + field.name + "': " + req.newValue + " (converted: " + field.parseValue(req.newValue)+")");
                field.writeValue(newValue);
                field.readValue();
                //TBD - writing some hexcode registers causes checksum issues and device needs to be rebooted but save is successful
            });
            String strJsonResp = field.asJson().toString();
            return ResponseEntity.ok().body(strJsonResp);
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


    private void validateInSync(EpeverField field, String strOldValue) throws RestResponseException{

        double oldValue;
        try {
            oldValue = Double.parseDouble(strOldValue);
        } catch (NumberFormatException ex) {
            String msg = "Could not parse old value from client (expected number): " + strOldValue;
            logger.error(msg,ex);
            throw new RestResponseException(HttpStatus.INTERNAL_SERVER_ERROR,msg);
        }
        if ( field.doubleValue() != oldValue ) {
            HashMap<String,Object> details = new HashMap<>();
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