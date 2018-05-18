package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.data.ArduinoGetResponse;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.regex.Pattern;


@RestController()
@RequestMapping("arduino")
@CrossOrigin()
public class ArduinioController {

    @Autowired
    AppConfig appConfig;

    @Autowired
    ArduinoService arduinoService;


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
    @PutMapping(value="fanMode")
    public void fanMode(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestBody  FanModeCommand fanModeCmd ) throws Exception {

        if ( fanModeCmd.value == null || !fanModeCmd.value.matches("^(ON|OFF|AUTO)$") ){
            throw new Exception("Invalid fan mode: " + fanModeCmd.value );
        }
        execute(respWriter, req, resp, "SET_FAN_MODE," + fanModeCmd.value  + "," + (fanModeCmd.persist?"PERSIST":"TRANSIENT"), null);
    }

    @Data
    static class FanTempCommand {
        public Float value;
        public String device;
        public String fan;
        public String member; // onTemp or offTemp
        public Boolean persist = true;
    }
    @PutMapping(value="device/:componentType")
    public void device(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestBody FanTempCommand fanTempCmd,
                       @PathVariable String componentType ) throws Exception {
        if ( "fanTemp".equalsIgnoreCase(componentType)) {
            //String device = Pattern.quote(fanTempCmd.device);
            //String fan = Pattern.quote(fanTempCmd.fan);
            String strOnTemp = "";  // get latest values from arduino first
            String strOffTemp = "";
            if ("onTemp".equalsIgnoreCase(fanTempCmd.member)) {

            } else if ("onTemp".equalsIgnoreCase(fanTempCmd.member)) {

            } else {
                throw new Exception("TODO - add invalid parameter value");
            }
            String strCmd = "SET_FAN_THRESHOLDS," + fanTempCmd.device + "," + fanTempCmd.fan + "," + strOnTemp + "," + strOffTemp
                    + (fanTempCmd.persist ? "PERSIST" : "TRANSIENT");
            //execute(respWriter, req, resp, strCmd, null);
        } else {
            throw new Exception("TODO - should not get here");
        }
    }


    @PostMapping(value = "execute", produces = "application/json")
    public void execute(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestParam(value = "cmd", required = false) String cmd,
                        @RequestParam(value = "commPortRegEx", required = false) String ttyRegEx ) throws Exception {

        String strClientIp = getClientIp(req);

        if (!strClientIp.matches(appConfig.remoteHostRegEx)) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid client location: " + strClientIp);
            return;
        }

        String strResp = arduinoService.execute(cmd, ttyRegEx, null );

        respWriter.write(strResp);

    }

    @GetMapping(value = "view", produces = "application/json")
    public void view(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestParam(value = "commPortRegEx", required = false) String ttyRegEx,
                        @RequestParam(value = "useCache", required = false, defaultValue = "false") boolean useCached) throws Exception {

        String strClientIp = getClientIp(req);

        if (!strClientIp.matches(appConfig.remoteHostRegEx)) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid client location: " + strClientIp);
            return;
        }

        String strResp = arduinoService.execute("GET", ttyRegEx, useCached );

        respWriter.write("[");
        respWriter.write(strResp);
        respWriter.write("]");
    }

    @GetMapping(value = "data", produces = "application/json")
    @ResponseBody
    public ArduinoGetResponse arduinoData() throws IOException, ClassNotFoundException {

        return arduinoService.arduinoMetrics.getLatestResponse();
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