package com.xmonit.solar.arduino;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.data.ArduinoGetResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;


@RestController()
@RequestMapping("arduino")
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


    @PostMapping(value = "execute", produces = "application/json")
    public void execute(Writer respWriter, HttpServletRequest req, HttpServletResponse resp,
                        @RequestParam(value = "cmd", required = false) String cmd,
                        @RequestParam(value = "commPortRegEx", required = false) String ttyRegEx,
                        @RequestParam(value = "validate", required = false) Boolean validate ) throws Exception {

        String strClientIp = getClientIp(req);

        if (!strClientIp.matches(appConfig.remoteHostRegEx)) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid client location: " + strClientIp);
            return;
        }

        boolean bValidate = validate != null ? validate : true;
        Integer reqId = bValidate ? ArduinoSerialBus.AUTO_GENERATE_REQ_ID : ArduinoSerialBus.NO_VALIDATION_REQ_ID;
        String strResp = arduinoService.execute(cmd, ttyRegEx, reqId );

        respWriter.write(strResp);

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