package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.epever.field.EpeverField;
import com.xmonit.solar.epever.metrics.MetricsSource;
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

}