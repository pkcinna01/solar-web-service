package com.xmonit.solar.epever;

import com.xmonit.solar.AppConfig;
import com.xmonit.solar.arduino.ArduinoService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.Writer;


@RestController()
@Timed
public class EpeverController {

    @Autowired
    AppConfig appConfig;

    @Autowired
    ArduinoService epeverService;


    @RequestMapping("/help")
    public void help(Writer respWriter, HttpServletResponse resp) {

        resp.setContentType("text/plain");
        resp.setCharacterEncoding("UTF-8");

        PrintWriter pw = new PrintWriter(respWriter);
        pw.println("USB EPEver Charge Controller client");
        pw.println("Data is available in Prometheus format at /actuator/prometheus");
        pw.println();

        //new ArduinoAbout().printHelp(pw);
    }


    @RequestMapping("/")
    public void index(Writer respWriter, HttpServletResponse resp) {
        help(respWriter, resp);
    }

}