package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.data.ArduinoGetResponse;
//import com.xmonit.solar.arduino.data.Fan;
//import com.xmonit.solar.arduino.data.Voltmeter;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;


@RestController()
@RequestMapping("arduino")
@CrossOrigin()
public class ArduinioController {

    @Autowired
    AppConfig appConfig;

    @Autowired
    ArduinoService arduinoService;

    private static final Logger logger = LoggerFactory.getLogger(ArduinioController.class);

    private static String getClientIp(HttpServletRequest request) {

        String remoteAddr = "";

        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }

        return remoteAddr;
    }

    @Data
    static class FanModeCommand {
        public String value;
        public Boolean persist = true;
    }

    @PutMapping(value = "fanMode")
    public void fanMode(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestBody FanModeCommand fanModeCmd) throws Exception {

        if (fanModeCmd.value == null || !fanModeCmd.value.matches("^(ON|OFF|AUTO)$")) {
            throw new Exception("Invalid fan mode: " + fanModeCmd.value);
        }
        //execute(respWriter, req, resp, "SET_FAN_MODE," + fanModeCmd.value + "," + (fanModeCmd.persist ? "PERSIST" : "TRANSIENT"), null);
    }

    @Data
    static class FanCommand {
        public String member; // onTemp or offTemp
        public double oldValue;
        public double newValue;
        public String deviceName;
        //public Fan fan;
        public Boolean persist = true;
    }

    @PutMapping(value = "device/{componentType}")
    public synchronized void device(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                                    @RequestBody FanCommand fanCmd,
                                    @PathVariable String componentType) throws IOException {
        try {
            if ("fanTemp".equalsIgnoreCase(componentType)) {
                logger.info("Fan request (client): " + fanCmd.toString());

                /*Fan clientFan = fanCmd.fan;

                String strResp = arduinoService.execute("GET", null, null);
                ObjectMapper mapper = new ObjectMapper();
                ArduinoGetResponse getResp = mapper.readValue(strResp, ArduinoGetResponse.class);
                Fan serverFan = getResp.getDevices().stream().filter(d -> d.getName().equalsIgnoreCase(fanCmd.deviceName)).findAny().map(d -> d.getFans()).orElseGet(LinkedList::new)
                        .stream().filter(f -> f.getName().equalsIgnoreCase(fanCmd.fan.getName())).findAny().get();

                if (serverFan != null) {

                    logger.info("Fan (server): " + fanCmd.toString());

                    boolean offTempInSync = Math.abs(clientFan.offTemp - serverFan.offTemp) < 0.001;
                    boolean onTempInSync = Math.abs(clientFan.onTemp - serverFan.onTemp) < 0.001;
                    if (!offTempInSync) {
                        throw new Exception("Client and Server fan OFF data out of sync before updating " + fanCmd.member + " (client=" + clientFan.offTemp + ",server=" + serverFan.offTemp + ").  Refresh client/browser and try again.");
                    } else if (!onTempInSync) {
                        throw new Exception("Client and Server fan ON data out of sync before updating " + fanCmd.member + " (client=" + clientFan.onTemp + ",server=" + serverFan.onTemp + ").  Refresh client/browser and try again.");
                    }
                    if ("onTemp".equalsIgnoreCase(fanCmd.member)) {
                        if (Math.abs(fanCmd.oldValue - serverFan.onTemp) < 0.001) {
                            clientFan.setOnTemp(fanCmd.newValue);
                        } else {
                            throw new Exception("Client and server data out of sync.  Fan ON threshold temperatures do not match: client=" + fanCmd.oldValue + " server=" + serverFan.onTemp);
                        }
                    } else if ("offTemp".equalsIgnoreCase(fanCmd.member)) {
                        if (Math.abs(fanCmd.oldValue - serverFan.offTemp) < 0.001) {
                            clientFan.setOffTemp(fanCmd.newValue);
                        } else {
                            throw new Exception("Client and server data out of sync.  Fan OFF threshold temperatures do not match: client=" + fanCmd.oldValue + " server=" + serverFan.onTemp);
                        }
                    } else {
                        throw new Exception("TODO - add invalid parameter value");
                    }
                    String strCmd = "SET_FAN_THRESHOLDS," + fanCmd.deviceName + "," + clientFan.getName() + "," + clientFan.onTemp + "," + clientFan.offTemp
                            + "," + (fanCmd.persist ? "PERSIST" : "TRANSIENT");
                    //logger.info(strCmd);
                    execute(respWriter, req, resp, strCmd, null);
                }
                */
            } else {
                throw new Exception("Unsupported device component type: " + componentType);
            }
        } catch (Exception ex) {
            logger.error("Failed saving FAN " + fanCmd.member, ex);
            JsonNodeFactory nf = JsonNodeFactory.instance;
            ObjectNode respNode = nf.objectNode();
            respNode.put("respCode", -1);
            respNode.put("respMsg", ex.getMessage());
            respWriter.write(respNode.toString());
        }
    }


    @Data
    static class VoltmeterCommand {
        public String member; // onTemp or offTemp
        public double oldValue;
        public double newValue;
        public String powerMeterName;
        //public Voltmeter voltage;
        public Boolean persist = true;
    }

    @PutMapping(value = "powerMeter/{componentType}")
    public synchronized void powerMeter(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                           @RequestBody VoltmeterCommand voltmeterCmd,
                           @PathVariable String componentType) throws IOException {
        try {
            /*
            if ("voltmeter".equalsIgnoreCase(componentType)) {
                String strResp = arduinoService.execute("GET", null, null);
                ObjectMapper mapper = new ObjectMapper();
                ArduinoGetResponse getResp = mapper.readValue(strResp, ArduinoGetResponse.class);
                Voltmeter serverVoltmeter = getResp.getPowerMeters().stream().filter(pm -> pm.getName().equalsIgnoreCase(voltmeterCmd.powerMeterName))
                        .findAny().map(pm -> pm.getVoltage()).get();

                if (serverVoltmeter != null) {

                    Voltmeter clientVoltMeter = voltmeterCmd.voltage;
                    Boolean vccInSync = Math.abs(clientVoltMeter.assignedVcc - serverVoltmeter.assignedVcc) < 0.001;
                    Boolean r1InSync = Math.abs(clientVoltMeter.assignedR1 - serverVoltmeter.assignedR1) < 0.001;
                    Boolean r2InSync = Math.abs(clientVoltMeter.assignedR2 - serverVoltmeter.assignedR2) < 0.001;
                    for (Pair<Boolean, String> p : Arrays.asList(Pair.of(vccInSync, "VCC"), Pair.of(r1InSync, "R1"), Pair.of(r2InSync, "R2"))) {
                        if (!p.getKey()) {
                            throw new Exception("Client and Server voltage meter " + p.getValue() + " out of sync before updating " + voltmeterCmd.member
                                    + ".  Refresh client/browser and try again.");
                        }
                    }

                    BiConsumer<Voltmeter, Double> setFn;
                    double serverVal;
                    String serialCmdArg;
                    switch (voltmeterCmd.member) {
                        case "assignedVcc":
                            setFn = Voltmeter::setAssignedVcc;
                            serverVal = serverVoltmeter.assignedVcc;
                            serialCmdArg = "VCC";
                            break;
                        case "assignedR1":
                            setFn = Voltmeter::setAssignedR1;
                            serverVal = serverVoltmeter.assignedR1;
                            serialCmdArg = "R1";
                            break;
                        case "assignedR2":x
                            setFn = Voltmeter::setAssignedR2;
                            serverVal = serverVoltmeter.assignedR2;
                            serialCmdArg = "R2";
                            break;
                        default:
                            throw new Exception("");
                    }
                    if (Math.abs(voltmeterCmd.oldValue - serverVal) < 0.001) {
                        setFn.accept(clientVoltMeter, voltmeterCmd.newValue);
                        //TBD - any need to send this back to client?
                    } else {
                        throw new Exception("Client and server data out of sync.  Voltmeter " + voltmeterCmd.member + " values do not match: client=" + voltmeterCmd.oldValue + " server=" + serverVal);
                    }
                    String strCmd = "SET_POWER_METER," + serialCmdArg + "," + voltmeterCmd.powerMeterName + "," + voltmeterCmd.newValue
                            + "," + (voltmeterCmd.persist ? "PERSIST" : "TRANSIENT");
                    logger.info(strCmd);
                    execute(respWriter, req, resp, strCmd, null);
                }
            } else {
                throw new Exception("Unsupported power meter component type: " + componentType);
            }
            */
        } catch (Exception ex) {
            logger.error("Failed saving voltmeter " + voltmeterCmd.member, ex);
            JsonNodeFactory nf = JsonNodeFactory.instance;
            ObjectNode respNode = nf.objectNode();
            respNode.put("respCode", -1);
            respNode.put("respMsg", ex.getMessage());
            respWriter.write(respNode.toString());
        }
    }


    CachedCmdResp cachedInfoResp = new CachedCmdResp("info");

    @GetMapping(value = "info", produces = "application/json")
    public void info(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                     @RequestParam(value = "commPortRegEx", required = false) String ttyRegEx,
                     @RequestParam(value = "useCache", required = false, defaultValue = "true") boolean useCache) throws Exception {

        String strTty = arduinoService.serialPort.getPortName();

        String strResp = null;
        if (useCache ) {
            strResp = cachedInfoResp.getLatest(strTty);
        }
        if ( strResp == null ){
            strResp = arduinoService.execute("GET,ENV,SETUP", ttyRegEx, useCache, true);
            final ObjectMapper mapper = new ObjectMapper();

            JsonNode respNode = mapper.readTree(strResp);
            JsonNodeFactory factory = JsonNodeFactory.instance;
            ArrayNode root = factory.arrayNode();
            ObjectNode objNode = factory.objectNode();
            root.add(objNode);
            objNode.put("commPort", strTty);
            objNode.put("env", respNode.get("env"));
            objNode.put("eeprom", respNode.get("eeprom"));

            strResp = root.toString();
            cachedInfoResp.update(strTty,strResp);
        }
        logger.debug("/arduino/info response: " + strResp);
        respWriter.write(strResp);
    }


    @PostMapping(value = "execute", produces = "application/json")
    public synchronized void execute(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestParam(value = "cmd", required = false) String cmd,
                        @RequestParam(value = "validate", required = false, defaultValue="true") boolean validate,
                        @RequestParam(value = "commPortRegEx", required = false) String ttyRegEx) throws Exception {

        String strResp = arduinoService.execute(cmd, ttyRegEx, null, validate);

        respWriter.write(strResp);

    }


    @GetMapping(value = "data", produces = "application/json")
    public void data(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                     @RequestParam(value = "commPortRegEx", required = false) String ttyRegEx,
                     @RequestParam(value = "verbose", required = false, defaultValue = "false") boolean verbose,
                     @RequestParam(value = "elements", required = false, defaultValue = "SENSORS" /*,DEVICES,OUTPUT_FORMAT,TIME,ENV,SETUP"*/) String elements,
                     @RequestParam(value = "useCache", required = false, defaultValue = "false") boolean useCache) throws Exception {

        String strResp = arduinoService.execute( (verbose?"VERBOSE,":"")+"GET,"+elements, ttyRegEx, useCache, true);

        respWriter.write(strResp);
    }


    @RequestMapping("help")
    public void help(Writer respWriter, HttpServletResponse resp) {

        resp.setContentType("text/plain");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter pw = new PrintWriter(respWriter);
        pw.println("USB Serial client for Arduino app that manages cooling fans, etc...");
        pw.println("Data is available in Prometheus format at /actuator/prometheus");
        pw.println("Example: curl -X POST http://localhost:9202/execute?cmd=GET");
        pw.println();

        new ArduinoAbout().printHelp(pw);
    }


    @RequestMapping("")
    public void index(Writer respWriter, HttpServletResponse resp) {
        help(respWriter, resp);
    }

}